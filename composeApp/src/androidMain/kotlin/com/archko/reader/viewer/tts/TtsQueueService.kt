package com.archko.reader.viewer.tts

import com.archko.reader.pdf.tts.SpeechService
import com.archko.reader.pdf.tts.TtsTask
import com.archko.reader.pdf.tts.Voice
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

@OptIn(DelicateCoroutinesApi::class)
class TtsQueueService : SpeechService {
    private var rate: Float = 0.20f
    private var volume: Float = 0.8f
    private var selectedVoice: String = "Tingting"

    // Flow for selected voice
    private val _selectedVoiceFlow = MutableStateFlow<Voice?>(null)
    val selectedVoiceFlow: StateFlow<Voice?> = _selectedVoiceFlow.asStateFlow()

    // Flow for speaking state
    private val _isSpeakingFlow = MutableStateFlow(false)
    override val isSpeakingFlow: StateFlow<Boolean> = _isSpeakingFlow.asStateFlow()

    private val taskQueue = mutableListOf<TtsTask>()
    private val queueMutex = Mutex()
    private val currentText = AtomicReference<String?>(null)

    @Volatile
    private var ttsWorker: TtsWorker? = null
    private val workerMutex = Mutex()

    private inner class TtsWorker(
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
                        _isSpeakingFlow.value = false
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
            _isSpeakingFlow.value = false
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
                _isSpeakingFlow.value = true

                val textVariants = listOf(
                    task.text
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
                _isSpeakingFlow.value = false
                currentProcess = null
            }
        }

        private suspend fun attemptSpeak(text: String): Boolean {
            return false
        }

        // 通知工作器有新任务
        fun notifyNewTask() {
            taskChannel.trySend(Unit)
        }

        fun destroy() {
            println("TTS: Destroying worker...")

            scope.cancel()
        }
    }

    init {
        // 初始化语音设置
        GlobalScope.launch {
            initializeVoiceSetting()
        }
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

    private suspend fun ensureWorker(): TtsWorker {
        return workerMutex.withLock {
            if (ttsWorker == null) {
                println("TTS: Creating new worker...")
                ttsWorker = TtsWorker(selectedVoice, rate, volume)
            }
            ttsWorker!!
        }
    }

    private suspend fun destroyWorker() {
        workerMutex.withLock {
            ttsWorker?.destroy()
            ttsWorker = null
        }
    }

    override fun speak(text: String) {
        println("TTS: Speak requested: ${text.take(50)}...")

        GlobalScope.launch {
            // 完全重启：先销毁旧的工作器
            destroyWorker()

            // 清空队列并添加新任务
            clearQueueSync()
            queueMutex.withLock {
                taskQueue.add(TtsTask(text, priority = 1))
            }

            // 创建并启动新工作器
            val worker = ensureWorker()
            worker.notifyNewTask()

            println("TTS: Speak task added and worker started")
        }
    }

    override fun addToQueue(text: String) {
        if (text.isBlank()) return

        GlobalScope.launch {
            var queueSize = 0
            queueMutex.withLock {
                taskQueue.add(TtsTask(text, priority = 0))
                queueSize = taskQueue.size
            }

            // 确保工作器存在并通知有新任务
            val worker = ensureWorker()
            worker.notifyNewTask()

            println("TTS: Added to queue: ${text.take(50)}... (Queue size: $queueSize)")
        }
    }

    override fun clearQueue() {
        GlobalScope.launch {
            clearQueueSync()
        }
    }

    private suspend fun clearQueueSync() {
        queueMutex.withLock {
            taskQueue.clear()
        }
        println("TTS: Queue cleared")
    }

    override fun stop() {
        GlobalScope.launch {
            // 完全销毁工作器，这会立即停止所有朗读
            destroyWorker()

            // 重置状态标志
            _isSpeakingFlow.value = false
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
            _isSpeakingFlow.value = false
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
        return emptyList()
    }

    override fun isSpeaking(): Boolean {
        return _isSpeakingFlow.value
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

        if (availableVoices.isNotEmpty()) {
            voice = availableVoices.first()
        }
        if (null == voice) {
            voice = Voice(selectedVoice, "zh_CN", "中文", rate, volume)
        }
        return voice
    }

    fun destroy() {
        runBlocking {
            destroyWorker()
        }

        println("TTS: Service destroyed")
    }

    override suspend fun saveVoiceSetting(voice: Voice) = withContext(Dispatchers.IO) {
    }

    override suspend fun getVoiceSetting(): Voice = withContext(Dispatchers.IO) {
        return@withContext getDefaultVoice()
    }
}