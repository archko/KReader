package com.archko.reader.viewer

import com.archko.reader.viewer.utils.TtsUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
            TtsUtils.forceKillAllTtsProcesses(isWindows)
        })
    }

    private suspend fun initializeVoiceSetting() {
        try {
            // 尝试加载保存的语音设置
            val savedVoice = getVoiceSetting()
            selectedVoice = savedVoice.name
            rate = savedVoice.rate // 从文件读取保存的 rate
            volume = savedVoice.volume // 从文件读取保存的 volume
            _selectedVoiceFlow.value = savedVoice
            println("TTS: Loaded saved voice: ${savedVoice.name} with rate=${savedVoice.rate}, volume=${savedVoice.volume}")
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

        val voiceToUse = detectLanguageAndSelectVoice(task.text)

        // 尝试多种文本处理方式
        val textVariants = listOf(
            TtsUtils.cleanTextForTts(task.text),           // 清理后的文本
            task.text
                .replace(Regex("-{2,}"), "")      // 先移除连续破折号
                .replace(Regex("[^\\p{L}\\p{N}\\s，。！？:/.\\-]"), ""), // 只保留字母、数字、空格、基本标点和URL字符
            task.text.replace(Regex("[^\\u4e00-\\u9fff\\w\\s:/.\\-]"), ""),   // 只保留中文、英文字母、数字、空格和URL字符
            TtsUtils.extractMeaningfulText(task.text),     // 提取有意义的文本
            "跳过无法朗读的内容"                      // 最后的备用文本
        )

        for ((index, text) in textVariants.withIndex()) {
            if (isShutdown || isPausedFlag.get()) return

            if (text.isBlank()) continue

            try {
                println("TTS: Attempt ${index + 1} - Using voice '$voiceToUse' for text: ${text.take(50)}...")

                val success = attemptSpeak(text, voiceToUse)
                if (success) {
                    println("TTS: Successfully spoke text on attempt ${index + 1}")
                    return
                } else {
                    println("TTS: Attempt ${index + 1} failed, trying next variant...")
                }

            } catch (e: Exception) {
                println("TTS: Attempt ${index + 1} error: ${e.message}")
                continue
            }
        }

        println("TTS: All attempts failed, skipping this text")
        isSpeakingFlag.set(false)
    }

    /**
     * 尝试朗读文本，返回是否成功
     */
    private suspend fun attemptSpeak(text: String, voice: String): Boolean {
        return try {
            val command = if (isWindows) {
                TtsUtils.createWindowsCommand(text, voice, rate, volume)
            } else {
                TtsUtils.createMacCommand(text, voice, rate)
            }

            isSpeakingFlag.set(true)
            currentProcess = withContext(Dispatchers.IO) {
                TtsUtils.createManagedProcess(isWindows, command)
            }

            // 设置超时时间：根据文本长度计算，最少10秒，最多60秒
            val timeoutSeconds = (text.length / 10).coerceIn(10, 60)

            // 等待进程结束并检查退出码，带超时
            val exitCode = withTimeoutOrNull(timeoutSeconds * 1000L) {
                withContext(Dispatchers.IO) {
                    try {
                        currentProcess?.waitFor() ?: -1
                    } catch (e: InterruptedException) {
                        -1
                    }
                }
            }

            // 清理状态
            isSpeakingFlag.set(false)
            if (exitCode == null) {
                // 超时了，强制终止进程
                println("TTS: Timeout after ${timeoutSeconds}s, killing process")
                currentProcess?.destroyForcibly()
                currentProcess = null
                false
            } else {
                currentProcess = null
                // 检查是否成功（退出码为0表示成功）
                exitCode == 0
            }

        } catch (e: Exception) {
            println("TTS: attemptSpeak error: ${e.message}")
            isSpeakingFlag.set(false)
            currentProcess?.destroyForcibly()
            currentProcess = null
            false
        }
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
                TtsUtils.getWindowsVoices()
            } else {
                TtsUtils.getMacVoices()
            }
            // 只保留中文和英文语音
            TtsUtils.filterChineseAndEnglishVoices(allVoices)
        } catch (e: Exception) {
            println("Get voices error: ${e.message}")
            emptyList()
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
            voice = Voice(selectedVoice, "zh_CN", "中文", rate, volume)
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
        TtsUtils.forceKillAllTtsProcesses(isWindows)
    }

    override suspend fun saveVoiceSetting(voice: Voice) = withContext(Dispatchers.IO) {
        TtsUtils.saveVoiceSetting(voice, isWindows)
    }

    override suspend fun getVoiceSetting(): Voice = withContext(Dispatchers.IO) {
        try {
            val configFile = File(TtsUtils.getConfigFilePath(isWindows))
            if (!configFile.exists()) {
                println("TTS: No voice setting file found")
                return@withContext getDefaultVoice()
            }

            val jsonString = configFile.readText()
            val json = Json { ignoreUnknownKeys = true }
            val voice = json.decodeFromString<Voice>(jsonString)

            println("TTS: Voice setting loaded from ${configFile.absolutePath}")
            voice
        } catch (e: Exception) {
            println("TTS: Failed to load voice setting: ${e.message}")
        }
        return@withContext getDefaultVoice()
    }
}