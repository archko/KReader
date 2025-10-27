package com.archko.reader.viewer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class TtsTask(
    val text: String,
    val priority: Int = 0 // 0 = normal, 1 = high priority
)

class TtsQueueService : SpeechService {
    private var currentProcess: Process? = null
    private val isSpeakingFlag = AtomicBoolean(false)
    private val isPausedFlag = AtomicBoolean(false)
    private var rate: Float = 0.30f
    private var volume: Float = 0.8f
    private var selectedVoice: String = "Mei-Jia"
    
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
        // 添加 JVM 关闭钩子，确保应用退出时清理 TTS 进程
        Runtime.getRuntime().addShutdownHook(Thread {
            println("TTS: JVM shutdown hook triggered, cleaning up...")
            forceKillAllTtsProcesses()
        })
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
    
    // 中文语音映射
    private val chineseVoices = if (isWindows) {
        listOf("Microsoft Huihui Desktop", "Microsoft Yaoyao Desktop", "Microsoft Kangkang Desktop")
    } else {
        listOf("Mei-Jia", "Ting-Ting", "Sin-ji", "Li-mu", "Yu-shu")
    }
    
    private val englishVoices = if (isWindows) {
        listOf("Microsoft David Desktop", "Microsoft Zira Desktop", "Microsoft Mark Desktop")
    } else {
        listOf("Alex", "Samantha", "Victoria", "Daniel", "Karen", "Moira", "Tessa")
    }
    
    private fun detectLanguageAndSelectVoice(text: String): String {
        val chineseCharCount = text.count { it.toString().matches(Regex("[\\u4e00-\\u9fff]")) }
        val totalChars = text.length
        
        return if (chineseCharCount > totalChars * 0.3) {
            val availableVoices = getAvailableVoices()
            chineseVoices.firstOrNull { it in availableVoices } 
                ?: if (isWindows) "Microsoft Huihui Desktop" else "Ting-Ting"
        } else {
            selectedVoice
        }
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
    }

    override fun getAvailableVoices(): List<String> {
        return try {
            if (isWindows) {
                getWindowsVoices()
            } else {
                getMacVoices()
            }
        } catch (e: Exception) {
            println("Get voices error: ${e.message}")
            if (isWindows) {
                listOf("Microsoft David Desktop", "Microsoft Zira Desktop", "Microsoft Huihui Desktop")
            } else {
                listOf("Alex", "Samantha", "Ting-Ting", "Sin-ji", "Mei-Jia")
            }
        }
    }
    
    private fun getWindowsVoices(): List<String> {
        return try {
            val process = ProcessBuilder(
                "powershell",
                "-Command",
                "Add-Type -AssemblyName System.Speech; (New-Object System.Speech.Synthesis.SpeechSynthesizer).GetInstalledVoices() | ForEach-Object { \$_.VoiceInfo.Name }"
            ).start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            output.lines()
                .filter { it.isNotBlank() }
                .map { it.trim() }
        } catch (e: Exception) {
            println("Failed to get Windows voices: ${e.message}")
            listOf("Microsoft David Desktop", "Microsoft Zira Desktop", "Microsoft Huihui Desktop")
        }
    }
    
    private fun getMacVoices(): List<String> {
        return try {
            val process = ProcessBuilder("say", "-v", "?").start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            
            output.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.isNotEmpty()) parts[0] else null
                }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            println("Failed to get Mac voices: ${e.message}")
            listOf("Alex", "Samantha", "Ting-Ting", "Sin-ji", "Mei-Jia")
        }
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
                ProcessBuilder("powershell", "-Command", 
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
}