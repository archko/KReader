package com.archko.reader.viewer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class TtsQueueService : SpeechService {
    private var currentProcess: Process? = null
    private val isSpeakingFlag = AtomicBoolean(false)
    private val isPausedFlag = AtomicBoolean(false)
    private var rate: Float = 0.20f
    private var volume: Float = 0.8f
    private var selectedVoice: String = "Meijia"

    // Flow for selected voice
    private val _selectedVoiceFlow = MutableStateFlow<Voice?>(null)
    val selectedVoiceFlow: StateFlow<Voice?> = _selectedVoiceFlow.asStateFlow()

    private val taskQueue = mutableListOf<TtsTask>()
    private val queueMutex = Mutex()
    private val currentText = AtomicReference<String?>(null)

    // 协程作用域和任务通知
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val taskNotificationChannel = Channel<Unit>(Channel.UNLIMITED)
    private var processingJob: Job? = null

    // 平台检测
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")
    private var isShutdown = false

    init {
        startProcessing()
        // 初始化语音设置
        serviceScope.launch {
            initializeVoiceSetting()
        }
        // 添加 JVM 关闭钩子，确保应用退出时清理 TTS 进程
        Runtime.getRuntime().addShutdownHook(Thread {
            println("TTS: JVM shutdown hook triggered, cleaning up...")
            forceKillAllTtsProcesses()
        })
    }

    private suspend fun initializeVoiceSetting() {
        try {
            // 尝试加载保存的语音设置
            val savedVoice = getVoiceSetting()
            if (savedVoice != null) {
                selectedVoice = savedVoice.name
                rate = 0.25f // 使用当前设置，不从文件读取
                volume = 0.8f // 使用当前设置，不从文件读取
                _selectedVoiceFlow.value = savedVoice
                println("TTS: Loaded saved voice: ${savedVoice.name}")
            } else {
                // 没有保存的设置，使用默认语音
                val defaultVoice = getDefaultVoice()
                selectedVoice = defaultVoice.name
                _selectedVoiceFlow.value = defaultVoice
                println("TTS: Using default voice: ${defaultVoice.name}")
            }
        } catch (e: Exception) {
            println("TTS: Failed to initialize voice setting: ${e.message}")
        }
    }

    private fun startProcessing() {
        processingJob = serviceScope.launch {
            taskProcessorLoop()
        }
    }

    private suspend fun taskProcessorLoop() {
        while (serviceScope.isActive && !isShutdown) {
            if (isPausedFlag.get()) {
                // 暂停状态，等待恢复
                delay(100)
                continue
            }

            val task = selectNextTask()
            if (task != null) {
                currentText.set(task.text)
                executeTask(task)
                // 等待当前任务完成
                while (isSpeakingFlag.get() && serviceScope.isActive && !isPausedFlag.get()) {
                    delay(100)
                }
                currentText.set(null)
                continue
            }

            // 没有任务时，等待新任务通知
            try {
                taskNotificationChannel.receive()
            } catch (e: Exception) {
                break
            }
        }
    }

    private suspend fun selectNextTask(): TtsTask? {
        return queueMutex.withLock {
            if (taskQueue.isNotEmpty()) {
                // 按优先级排序，优先级高的先执行
                taskQueue.sortByDescending { it.priority }
                taskQueue.removeAt(0)
            } else {
                null
            }
        }
    }

    private suspend fun addTaskToQueue(task: TtsTask) {
        queueMutex.withLock {
            taskQueue.add(task)
        }
        // 通知处理循环有新任务
        taskNotificationChannel.trySend(Unit)
    }

    private fun detectLanguageAndSelectVoice(text: String): String {
        return selectedVoice
    }

    private suspend fun executeTask(task: TtsTask) {
        if (isShutdown || isPausedFlag.get()) return

        try {
            val voiceToUse = detectLanguageAndSelectVoice(task.text)
            println("TTS: Using voice '$voiceToUse' for text: ${task.text.take(50)}...")

            val command = if (isWindows) {
                createWindowsCommand(task.text, voiceToUse)
            } else {
                createMacCommand(task.text, voiceToUse)
            }

            isSpeakingFlag.set(true)
            currentProcess = withContext(Dispatchers.IO) {
                createManagedProcess(command)
            }

            // 使用协程等待进程结束
            withContext(Dispatchers.IO) {
                try {
                    currentProcess?.waitFor()
                } catch (e: InterruptedException) {
                    // 进程被中断
                } finally {
                    isSpeakingFlag.set(false)
                    currentProcess = null
                }
            }

        } catch (e: Exception) {
            println("TTS Error: ${e.message}")
            isSpeakingFlag.set(false)
        }
    }

    private fun createManagedProcess(command: Array<String>): Process {
        val processBuilder = ProcessBuilder(*command)

        if (isWindows) {
            // Windows: 创建新的进程组，当父进程退出时子进程也会退出
            processBuilder.environment()["CREATE_NEW_PROCESS_GROUP"] = "true"
        } else {
            // macOS/Linux: 设置进程组，使子进程在父进程退出时收到 SIGHUP
            // 这里我们通过 shell 包装来确保进程能被正确清理
            val wrappedCommand = arrayOf(
                "sh", "-c",
                "trap 'kill 0' TERM; ${command.joinToString(" ")} & wait"
            )
            return ProcessBuilder(*wrappedCommand).start()
        }

        return processBuilder.start()
    }

    private fun createWindowsCommand(text: String, voice: String): Array<String> {
        val rateValue = (rate * 10).toInt() // Windows rate range 0-10
        val volumeValue = (volume * 100).toInt() // Windows volume 0-100

        // 转义文本中的特殊字符
        val escapedText = text.replace("'", "''").replace("\"", "`\"")

        // 使用 PowerShell 调用 Windows Speech API，添加进程管理
        return arrayOf(
            "powershell",
            "-Command",
            """
            Add-Type -AssemblyName System.Speech;
            ${'$'}synth = New-Object System.Speech.Synthesis.SpeechSynthesizer;
            try {
                ${'$'}synth.SelectVoice('$voice');
                ${'$'}synth.Rate = $rateValue;
                ${'$'}synth.Volume = $volumeValue;
                ${'$'}synth.Speak('$escapedText');
            } finally {
                ${'$'}synth.Dispose();
            }
            """.trimIndent()
        )
    }

    private fun createMacCommand(text: String, voice: String): Array<String> {
        val rateValue = (rate * 400 + 100).toInt() // Mac rate range 100-500

        // 为了更好的进程管理，我们给 say 命令添加一个标识符
        val processId = "KReader_TTS_${System.currentTimeMillis()}"

        return arrayOf(
            "say",
            "-v", voice,
            "-r", rateValue.toString(),
            text
        )
    }

    override fun speak(text: String) {
        if (isShutdown) return
        serviceScope.launch {
            clearQueue()
            addTaskToQueue(TtsTask(text, priority = 1)) // 高优先级
        }
    }

    override fun addToQueue(text: String) {
        if (isShutdown || text.isBlank()) return
        serviceScope.launch {
            addTaskToQueue(TtsTask(text, priority = 0)) // 普通优先级
            println("TTS: Added to queue: ${text.take(50)}... (Queue size: ${getQueueSize()})")
        }
    }

    override fun clearQueue() {
        if (isShutdown) return
        serviceScope.launch {
            queueMutex.withLock {
                taskQueue.clear()
            }
            println("TTS: Queue cleared")
        }
    }

    override fun stop() {
        clearQueue()
        currentProcess?.let { process ->
            try {
                process.destroyForcibly()
                process.waitFor()
            } catch (e: Exception) {
                println("Stop TTS Error: ${e.message}")
            }
        }
        isSpeakingFlag.set(false)
        isPausedFlag.set(false)
        currentProcess = null
        currentText.set(null)
    }

    override fun pause() {
        isPausedFlag.set(true)
        currentProcess?.destroyForcibly()
        isSpeakingFlag.set(false)
        println("TTS: Paused")
    }

    override fun resume() {
        isPausedFlag.set(false)
        println("TTS: Resumed")
    }

    override fun setRate(rate: Float) {
        this.rate = rate.coerceIn(0.1f, 1.0f)
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0.0f, 1.0f)
    }

    override fun setVoice(voiceId: String) {
        this.selectedVoice = voiceId
        // 更新 Flow
        serviceScope.launch {
            val availableVoices = getAvailableVoices()
            val voice = availableVoices.find { it.name == voiceId }
            _selectedVoiceFlow.value = voice
        }
    }

    override fun getAvailableVoices(): List<Voice> {
        return try {
            val allVoices = if (isWindows) {
                getWindowsVoices()
            } else {
                getMacVoices()
            }
            // 只保留中文和英文语音
            filterChineseAndEnglishVoices(allVoices)
        } catch (e: Exception) {
            println("Get voices error: ${e.message}")
            emptyList()
        }
    }

    private fun filterChineseAndEnglishVoices(voices: List<Voice>): List<Voice> {
        return voices.filter { voice ->
            val countryCode = voice.countryCode.lowercase()
            // 中文：zh-cn, zh-tw, zh-hk, zh-sg 等
            // 英文：en-us, en-gb, en-au, en-ca 等
            countryCode.startsWith("zh") || countryCode.startsWith("en")
        }
    }

    private fun getWindowsVoices(): List<Voice> {
        return try {
            val process = ProcessBuilder(
                "powershell",
                "-Command",
                """
                Add-Type -AssemblyName System.Speech;
                (New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices() | ForEach-Object {
                    ${'$'}voice = ${'$'}_.VoiceInfo;
                    "${'$'}(${'$'}voice.Name)|${'$'}(${'$'}voice.Culture.Name)|${'$'}(${'$'}voice.Description)"
                }
                """.trimIndent()
            ).start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            output.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.trim().split("|")
                    if (parts.size >= 3) {
                        Voice(
                            name = parts[0],
                            countryCode = parts[1],
                            description = parts[2]
                        )
                    } else null
                }
        } catch (e: Exception) {
            println("Failed to get Windows voices: ${e.message}")
            getDefaultWindowsVoices()
        }
    }

    private fun getMacVoices(): List<Voice> {
        return try {
            val process = ProcessBuilder("say", "-v", "?").start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            output.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    parseMacVoiceLine(line.trim())
                }
        } catch (e: Exception) {
            println("Failed to get Mac voices: ${e.message}")
            getDefaultMacVoices()
        }
    }

    private fun parseMacVoiceLine(line: String): Voice? {
        // macOS say -v ? 输出格式通常是: "VoiceName    language_code    # description"
        // 例如: "Alex                 en_US    # Most people recognize me by my voice."
        // 或者: "Ting-Ting            zh_CN    # 普通话（中国大陆）- 女声"

        return try {
            val parts = line.split("#", limit = 2)
            val voiceInfo = parts[0].trim()
            val description = if (parts.size > 1) parts[1].trim() else ""

            // 分离语音名称和语言代码
            val voiceParts = voiceInfo.split("\\s+".toRegex())
            if (voiceParts.size >= 2) {
                val name = voiceParts[0]
                val countryCode = voiceParts[voiceParts.size - 1]

                Voice(
                    name = name,
                    countryCode = countryCode,
                    description = description.ifEmpty { "System voice" }
                )
            } else {
                // 如果解析失败，至少返回语音名称
                Voice(
                    name = voiceParts[0],
                    countryCode = "unknown",
                    description = description.ifEmpty { "System voice" }
                )
            }
        } catch (e: Exception) {
            println("Failed to parse voice line: $line, error: ${e.message}")
            null
        }
    }

    private fun getDefaultWindowsVoices(): List<Voice> {
        return listOf(
            Voice("Microsoft David Desktop", "en-US", "English (United States) - Male"),
            Voice("Microsoft Zira Desktop", "en-US", "English (United States) - Female"),
            Voice("Microsoft Mark Desktop", "en-US", "English (United States) - Male"),
            Voice("Microsoft Huihui Desktop", "zh-CN", "Chinese (Simplified) - Female"),
            Voice("Microsoft Yaoyao Desktop", "zh-CN", "Chinese (Simplified) - Female"),
            Voice("Microsoft Kangkang Desktop", "zh-CN", "Chinese (Simplified) - Male")
        )
    }

    private fun getDefaultMacVoices(): List<Voice> {
        return listOf(
            Voice("Alex", "en-US", "English (United States) - Male"),
            Voice("Samantha", "en-US", "English (United States) - Female"),
            Voice("Victoria", "en-US", "English (United States) - Female"),
            Voice("Daniel", "en-GB", "English (United Kingdom) - Male"),
            Voice("Karen", "en-AU", "English (Australia) - Female"),
            Voice("Moira", "en-IE", "English (Ireland) - Female"),
            Voice("Tessa", "en-ZA", "English (South Africa) - Female"),
            Voice("Ting-Ting", "zh-CN", "Chinese (Simplified) - Female"),
            Voice("Sin-ji", "zh-HK", "Chinese (Hong Kong) - Female"),
            Voice("Mei-Jia", "zh-TW", "Chinese (Traditional) - Female"),
            Voice("Li-mu", "zh-CN", "Chinese (Simplified) - Male"),
            Voice("Yu-shu", "zh-CN", "Chinese (Simplified) - Female")
        )
    }

    override fun isSpeaking(): Boolean {
        return isSpeakingFlag.get()
    }

    override fun isPaused(): Boolean {
        return isPausedFlag.get()
    }

    override fun getQueueSize(): Int {
        return runBlocking {
            queueMutex.withLock {
                taskQueue.size
            }
        }
    }

    override fun getCurrentText(): String? {
        return currentText.get()
    }

    override fun getDefaultVoice(): Voice {
        val availableVoices = getAvailableVoices()

        var voice: Voice? = null
        if (isMacOS) {
            // 对于 macOS，优先查找 Mei-Jia 或 Meijia
            voice = availableVoices.find { voice ->
                voice.name.equals("Mei-Jia", ignoreCase = true) ||
                        voice.name.equals("Meijia", ignoreCase = true)
            }
        }

        if (null == voice && availableVoices.isNotEmpty()) {
            voice = availableVoices.first()
        }
        if (null == voice) {
            voice = Voice(selectedVoice, "zh_CN", "中文")
        }
        return voice
    }

    fun destroy() {
        isShutdown = true
        stop()
        processingJob?.cancel()
        taskNotificationChannel.close()
        serviceScope.cancel()

        // 强制终止所有可能的 TTS 进程
        forceKillAllTtsProcesses()
    }

    private fun forceKillAllTtsProcesses() {
        try {
            if (isWindows) {
                // Windows: 更精确地终止 TTS 相关的 PowerShell 进程
                ProcessBuilder(
                    "powershell", "-Command",
                    "Get-Process | Where-Object {${'$'}_.ProcessName -eq 'powershell' -and ${'$'}_.CommandLine -like '*Speech*'} | Stop-Process -Force"
                ).start().waitFor()

                // 备用方案：终止所有 PowerShell 进程（可能影响其他应用）
                // ProcessBuilder("taskkill", "/F", "/IM", "powershell.exe").start()
            } else {
                // macOS: 终止所有 say 进程
                ProcessBuilder("pkill", "say").start().waitFor()

                // 如果 pkill 不工作，尝试 killall
                ProcessBuilder("killall", "say").start().waitFor()
            }
            println("TTS: Force killed all TTS processes")
        } catch (e: Exception) {
            println("TTS: Failed to force kill TTS processes: ${e.message}")
        }
    }

    private fun getConfigFilePath(): String {
        val userHome = System.getProperty("user.home")
        return if (isWindows) {
            val appData = System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming"
            "$appData\\KReader\\tts_voice_setting.json"
        } else {
            // macOS
            "$userHome/Library/Application Support/KReader/tts_voice_setting.json"
        }
    }

    override suspend fun saveVoiceSetting(voice: Voice) = withContext(Dispatchers.IO) {
        try {

            val configFile = File(getConfigFilePath())
            configFile.parentFile?.mkdirs()

            val json = Json { prettyPrint = true }
            val jsonString = json.encodeToString(voice)
            configFile.writeText(jsonString)

            println("TTS: Voice setting saved to ${configFile.absolutePath}")
        } catch (e: Exception) {
            println("TTS: Failed to save voice setting: ${e.message}")
        }
    }

    override suspend fun getVoiceSetting(): Voice? = withContext(Dispatchers.IO) {
        try {
            val configFile = File(getConfigFilePath())
            if (!configFile.exists()) {
                println("TTS: No voice setting file found")
                return@withContext null
            }

            val jsonString = configFile.readText()
            val json = Json { ignoreUnknownKeys = true }
            val voice = json.decodeFromString<Voice>(jsonString)

            println("TTS: Voice setting loaded from ${configFile.absolutePath}")
            voice
        } catch (e: Exception) {
            println("TTS: Failed to load voice setting: ${e.message}")
            null
        }
    }
}