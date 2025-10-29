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

@OptIn(DelicateCoroutinesApi::class)
class TtsQueueService : SpeechService {
    private var rate: Float = 0.20f
    private var volume: Float = 0.8f
    private var selectedVoice: String = "Tingting"

    // Flow for selected voice
    private val _selectedVoiceFlow = MutableStateFlow<Voice?>(null)
    val selectedVoiceFlow: StateFlow<Voice?> = _selectedVoiceFlow.asStateFlow()

    // 平台检测
    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    private val isMacOS = System.getProperty("os.name").lowercase().contains("mac")

    // 外部公用队列 - 线程安全
    private val taskQueue = mutableListOf<TtsTask>()
    private val queueMutex = Mutex()
    private val isSpeaking = AtomicBoolean(false)
    private val currentText = AtomicReference<String?>(null)

    // TTS 工作器 - 只负责朗读，不管理队列
    @Volatile
    private var ttsWorker: TtsWorker? = null
    private val workerMutex = Mutex()

    /**
     * TTS 工作器 - 只负责朗读，从外部队列获取任务
     */
    private inner class TtsWorker(
        private val isWindows: Boolean,
        private val voice: String,
        private val rate: Float,
        private val volume: Float
    ) {
        @Volatile
        private var currentProcess: Process? = null
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        private val taskChannel = Channel<Unit>(Channel.UNLIMITED)
        
        init {
            scope.launch {
                processLoop()
            }
        }
        
        private suspend fun processLoop() {
            while (scope.isActive) {
                // 1. 从外部队列获取任务
                val task = selectNextTask()
                if (task != null) {
                    currentText.set(task.text)
                    executeTask(task)
                    currentText.set(null)
                    
                    // 检查队列是否为空，如果为空则重置状态
                    val queueSize = queueMutex.withLock { taskQueue.size }
                    if (queueSize == 0) {
                        isSpeaking.set(false)
                    }
                    continue
                }
                
                // 2. 等待新任务通知
                try {
                    taskChannel.receive()
                } catch (_: Exception) {
                    break
                }
            }
            
            // 循环结束时重置状态
            isSpeaking.set(false)
            currentText.set(null)
        }
        
        private suspend fun selectNextTask(): TtsTask? {
            return queueMutex.withLock {
                if (taskQueue.isNotEmpty()) {
                    taskQueue.sortByDescending { it.priority }
                    taskQueue.removeAt(0)
                } else {
                    null
                }
            }
        }
        
        private suspend fun executeTask(task: TtsTask) {
            try {
                isSpeaking.set(true)
                
                val textVariants = listOf(
                    TtsUtils.cleanTextForTts(task.text),
                    task.text.replace(Regex("-{2,}"), "").replace(Regex("[^\\p{L}\\p{N}\\s，。！？:/.\\-]"), ""),
                    task.text.replace(Regex("[^\\u4e00-\\u9fff\\w\\s:/.\\-]"), ""),
                    TtsUtils.extractMeaningfulText(task.text),
                    "跳过无法朗读的内容"
                )
                
                for ((index, text) in textVariants.withIndex()) {
                    if (!scope.isActive) return
                    if (text.isBlank()) continue
                    
                    try {
                        println("TTS: Attempt ${index + 1} - text: ${text.take(50)}...")
                        
                        val success = attemptSpeak(text)
                        if (success) {
                            println("TTS: Successfully spoke text on attempt ${index + 1}")
                            return
                        }
                    } catch (e: Exception) {
                        println("TTS: Attempt.error ${index + 1} error: ${e.message}")
                    }
                }
                
                println("TTS: All attempts failed")
            } finally {
                isSpeaking.set(false)
                currentProcess = null
            }
        }
        
        private suspend fun attemptSpeak(text: String): Boolean {
            return try {
                val command = if (isWindows) {
                    TtsUtils.createWindowsCommand(text, voice, rate, volume)
                } else {
                    TtsUtils.createMacCommand(text, voice, rate)
                }
                
                currentProcess = TtsUtils.createManagedProcess(isWindows, command)
                val timeoutSeconds = (text.length / 10).coerceIn(10, 60)
                
                val exitCode = withTimeoutOrNull(timeoutSeconds * 1000L) {
                    while (currentProcess?.isAlive == true && scope.isActive) {
                        delay(100)
                    }
                    currentProcess?.exitValue() ?: -1
                }
                
                if (exitCode == null) {
                    currentProcess?.destroyForcibly()
                    false
                } else {
                    exitCode == 0
                }
            } catch (e: Exception) {
                println("TTS: attemptSpeak error: ${e.message}")
                currentProcess?.destroyForcibly()
                false
            }
        }
        
        // 通知工作器有新任务
        fun notifyNewTask() {
            taskChannel.trySend(Unit)
        }
        
        fun destroy() {
            println("TTS: Destroying worker...")
            
            // 强制终止当前进程
            currentProcess?.destroyForcibly()
            
            // 取消协程作用域
            scope.cancel()
            
            // 强制终止所有TTS进程
            TtsUtils.forceKillAllTtsProcesses(isWindows)
        }
    }

    init {
        // 初始化语音设置
        GlobalScope.launch {
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
            rate = savedVoice.rate
            volume = savedVoice.volume
            _selectedVoiceFlow.value = savedVoice
            println("TTS: Loaded saved voice: ${savedVoice.name} with rate=${savedVoice.rate}, volume=${savedVoice.volume}")
        } catch (e: Exception) {
            println("TTS: Failed to initialize voice setting: ${e.message}")
        }
    }

    /**
     * 确保工作器存在，如果不存在则创建新的
     */
    private suspend fun ensureWorker(): TtsWorker {
        return workerMutex.withLock {
            if (ttsWorker == null) {
                println("TTS: Creating new worker...")
                ttsWorker = TtsWorker(isWindows, selectedVoice, rate, volume)
            }
            ttsWorker!!
        }
    }

    /**
     * 销毁当前工作器
     */
    private suspend fun destroyWorker() {
        workerMutex.withLock {
            ttsWorker?.destroy()
            ttsWorker = null
        }
    }

    override fun speak(text: String) {
        println("TTS: Speak requested: ${text.take(50)}...")
        
        GlobalScope.launch {
            // 完全重启：先销毁旧的工作器，再创建新的
            destroyWorker()
            
            // 清空队列并添加新任务
            clearQueueSync()
            addToQueue(text, priority = 1)
            
            // 启动工作器
            startWorker()
        }
    }

    override fun addToQueue(text: String) {
        addToQueue(text, priority = 0)
    }
    
    /**
     * 添加任务到队列 - 线程安全，快速执行
     */
    private fun addToQueue(text: String, priority: Int = 0) {
        if (text.isBlank()) return
        
        // 使用 runBlocking 确保同步执行，避免并发问题
        runBlocking {
            var queueSize = 0
            queueMutex.withLock {
                taskQueue.add(TtsTask(text, priority))
                queueSize = taskQueue.size
            }
            
            // 通知工作器有新任务（如果工作器存在）
            ttsWorker?.notifyNewTask()
            
            println("TTS: Added to queue: ${text.take(50)}... (Queue size: $queueSize)")
        }
    }

    override fun clearQueue() {
        GlobalScope.launch {
            clearQueueSync()
        }
    }
    
    /**
     * 同步清空队列
     */
    private suspend fun clearQueueSync() {
        queueMutex.withLock {
            taskQueue.clear()
        }
        println("TTS: Queue cleared")
    }
    
    /**
     * 启动工作器开始朗读
     */
    fun startWorker() {
        GlobalScope.launch {
            ensureWorker()
        }
    }

    override fun stop() {
        GlobalScope.launch {
            // 完全销毁工作器，这会立即停止所有朗读
            destroyWorker()
            
            // 重置状态标志
            isSpeaking.set(false)
            currentText.set(null)
            
            // 清空队列
            clearQueueSync()
            
            println("TTS: Stopped")
        }
    }

    override fun pause() {
        GlobalScope.launch {
            // 暂停也是完全销毁工作器
            destroyWorker()
            
            // 重置状态标志
            isSpeaking.set(false)
            currentText.set(null)
            
            println("TTS: Paused")
        }
    }

    override fun resume() {
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
        GlobalScope.launch {
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
        return isSpeaking.get()
    }

    override fun isPaused(): Boolean {
        return ttsWorker == null
    }

    override fun getQueueSize(): Int {
        return runBlocking {
            queueMutex.withLock { taskQueue.size }
        }
    }

    override fun getCurrentText(): String? {
        return currentText.get()
    }

    override fun getDefaultVoice(): Voice {
        val availableVoices = getAvailableVoices()

        var voice: Voice? = null
        if (isMacOS) {
            // 对于 macOS，优先查找 Ting-ting 或 Tingting
            voice = availableVoices.find { voice ->
                voice.name.equals("Ting-ting", ignoreCase = true) ||
                        voice.name.equals("Tingting", ignoreCase = true)
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
        println("TTS: Service destroy requested")
        
        runBlocking {
            destroyWorker()
        }
        
        // 强制终止所有可能的 TTS 进程
        TtsUtils.forceKillAllTtsProcesses(isWindows)
        
        println("TTS: Service destroyed")
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
            return@withContext voice
        } catch (e: Exception) {
            println("TTS: Failed to load voice setting: ${e.message}")
        }
        return@withContext getDefaultVoice()
    }
}