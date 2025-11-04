package com.archko.reader.viewer.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.archko.reader.pdf.tts.SpeechService
import com.archko.reader.pdf.tts.TtsTask
import com.archko.reader.pdf.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Android版本的TTS队列服务，使用Android TextToSpeech引擎
 */
@OptIn(DelicateCoroutinesApi::class)
class TtsQueueService(
    private val textToSpeech: TextToSpeech
) : SpeechService {

    private var rate: Float = 0.8f
    private var volume: Float = 0.8f
    private var selectedVoice: String = "default"

    // Flow for speaking state
    private val _isSpeakingFlow = MutableStateFlow(false)
    override val isSpeakingFlow: StateFlow<Boolean> = _isSpeakingFlow.asStateFlow()

    private val taskQueue = mutableListOf<TtsTask>()
    private val queueMutex = Mutex()
    private val currentReflowBean = AtomicReference<com.archko.reader.pdf.entity.ReflowBean?>(null)

    @Volatile
    private var ttsWorker: TtsWorker? = null
    private val workerMutex = Mutex()

    @Volatile
    private var isCurrentlySpeaking = false

    init {
        setupTtsListener()
        textToSpeech.setSpeechRate(rate)
    }

    private fun setupTtsListener() {
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isCurrentlySpeaking = true
                _isSpeakingFlow.value = true
            }

            override fun onDone(utteranceId: String?) {
                isCurrentlySpeaking = false
                // 通知worker继续处理下一个任务
                ttsWorker?.onTtsComplete()
            }

            override fun onError(utteranceId: String?) {
                isCurrentlySpeaking = false
                println("TTS: Error occurred for utterance: $utteranceId")
                // 通知worker继续处理下一个任务
                ttsWorker?.onTtsComplete()
            }
        })
    }

    private inner class TtsWorker {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        private val taskChannel = Channel<Unit>(Channel.UNLIMITED)
        private val completeChannel = Channel<Unit>(Channel.UNLIMITED)

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
                    currentReflowBean.set(task.reflowBean)
                    executeTask(task)

                    // 等待TTS完成
                    completeChannel.receive()

                    currentReflowBean.set(null)

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
            currentReflowBean.set(null)
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
                if (!scope.isActive) return
                val text = task.reflowBean.data ?: ""
                if (text.isBlank()) return

                println("TTS: Speaking text: ${text.take(50)}...")

                withContext(Dispatchers.Main) {
                    val params = hashMapOf<String, String>().apply {
                        put(
                            TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                            "utterance_${System.currentTimeMillis()}"
                        )
                    }
                    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params)
                }
            } catch (e: Exception) {
                println("TTS: Error executing task: ${e.message}")
                _isSpeakingFlow.value = false
            }
        }

        // 通知工作器有新任务
        fun notifyNewTask() {
            taskChannel.trySend(Unit)
        }

        // TTS完成回调
        fun onTtsComplete() {
            completeChannel.trySend(Unit)
        }

        fun destroy() {
            println("TTS: Destroying worker...")
            scope.cancel()
        }
    }

    private suspend fun ensureWorker(): TtsWorker {
        return workerMutex.withLock {
            if (ttsWorker == null) {
                println("TTS: Creating new worker...")
                ttsWorker = TtsWorker()
            }
            ttsWorker!!
        }
    }

    private suspend fun destroyWorker() {
        workerMutex.withLock {
            ttsWorker?.destroy()
            ttsWorker = null
        }
        // 停止当前的TTS
        withContext(Dispatchers.Main) {
            textToSpeech.stop()
        }
        isCurrentlySpeaking = false
    }

    override fun speak(reflowBean: com.archko.reader.pdf.entity.ReflowBean) {
        val text = reflowBean.data ?: ""
        println("TTS: Speak requested: ${text.take(50)}...")

        GlobalScope.launch {
            // 完全重启：先销毁旧的工作器
            destroyWorker()

            // 清空队列并添加新任务
            clearQueueSync()
            queueMutex.withLock {
                taskQueue.add(TtsTask(reflowBean, priority = 1))
            }

            // 创建并启动新工作器
            val worker = ensureWorker()
            worker.notifyNewTask()

            println("TTS: Speak task added and worker started")
        }
    }

    override fun addToQueue(reflowBean: com.archko.reader.pdf.entity.ReflowBean) {
        val text = reflowBean.data ?: ""
        if (text.isBlank()) return

        GlobalScope.launch {
            var queueSize = 0
            queueMutex.withLock {
                taskQueue.add(TtsTask(reflowBean, priority = 0))
                queueSize = taskQueue.size
            }

            // 确保工作器存在并通知有新任务
            val worker = ensureWorker()
            worker.notifyNewTask()

            //println("TTS: Added to queue: ${text.take(50)}... (Queue size: $queueSize)")
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
            currentReflowBean.set(null)

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
            currentReflowBean.set(null)

            println("TTS: Paused")
        }
    }

    override fun resume() {
        // Android TTS没有直接的resume功能，需要重新开始队列
        GlobalScope.launch {
            if (taskQueue.isNotEmpty()) {
                val worker = ensureWorker()
                worker.notifyNewTask()
            }
        }
    }

    override fun setRate(rate: Float) {
        this.rate = rate.coerceIn(0.1f, 2.0f)
        textToSpeech.setSpeechRate(this.rate)
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0.0f, 1.0f)
    }

    override fun setVoice(voiceId: String) {
        this.selectedVoice = voiceId
    }

    override fun getAvailableVoices(): List<Voice> {
        return listOf(getDefaultVoice())
    }

    override fun isSpeaking(): Boolean {
        return _isSpeakingFlow.value || isCurrentlySpeaking
    }

    override fun isPaused(): Boolean {
        return ttsWorker == null && taskQueue.isNotEmpty()
    }

    override fun getQueueSize(): Int {
        return runBlocking {
            queueMutex.withLock { taskQueue.size }
        }
    }

    override fun getQueue(): List<TtsTask> {
        return runBlocking {
            queueMutex.withLock { taskQueue }
        }
    }

    override fun getCurrentReflowBean(): com.archko.reader.pdf.entity.ReflowBean? {
        return currentReflowBean.get()
    }

    override fun getDefaultVoice(): Voice {
        return Voice(selectedVoice, "zh_CN", "中文", rate, volume)
    }

    fun destroy() {
        runBlocking {
            destroyWorker()
        }
        println("TTS: Service destroyed")
    }

    override suspend fun saveVoiceSetting(voice: Voice) = withContext(Dispatchers.IO) {
        // 可以在这里保存设置到SharedPreferences
    }

    override suspend fun getVoiceSetting(): Voice = withContext(Dispatchers.IO) {
        return@withContext getDefaultVoice()
    }
}