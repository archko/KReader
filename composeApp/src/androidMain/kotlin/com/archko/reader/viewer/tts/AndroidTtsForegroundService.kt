package com.archko.reader.viewer.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.tts.TtsTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class AndroidTtsForegroundService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tts_service_channel"
        const val ACTION_STOP = "action_stop"
    }

    private val binder = TtsServiceBinder()
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 睡眠定时相关
    private var sleepTimerJob: Job? = null
    private var sleepTimerMinutes: Int = 0

    // 朗读完成回调
    private var onSpeechCompleteCallback: ((String?) -> Unit)? = null

    private val beanList = mutableListOf<ReflowBean>()
    private var currentBean: ReflowBean? = null
    private var currentIndex = 0

    // Flow for speaking state
    private val _isSpeakingFlow = MutableStateFlow(false)
    val isSpeakingFlow: StateFlow<Boolean> = _isSpeakingFlow.asStateFlow()

    // 前台服务状态
    private var isForegroundServiceStarted = false

    inner class TtsServiceBinder : Binder() {
        fun getService(): AndroidTtsForegroundService = this@AndroidTtsForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initializeTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stop()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        textToSpeech?.shutdown()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TTS朗读服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "文档朗读服务"
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val stopIntent = Intent(this, AndroidTtsForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("文档朗读")
            .setContentText(
                when {
                    !isInitialized -> "正在初始化..."
                    isSpeaking() -> "正在朗读..."
                    else -> "已暂停"
                }
            )
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_delete, "停止", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        if (isForegroundServiceStarted) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun initializeTts() {
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.CHINESE
            setupTtsListener()
            isInitialized = true

            serviceScope.launch {
                isSpeakingFlow.collect { isSpeaking ->
                    updateNotification()
                }
            }
        }
    }

    private fun setupTtsListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeakingFlow.value = true
            }

            override fun onDone(utteranceId: String?) {
                playNext()
            }

            override fun onError(utteranceId: String?) {
                println("TTS: Error occurred for utterance: $utteranceId")
                playNext()
            }
        })
    }

    private fun playNext() {
        currentIndex++
        if (currentIndex < beanList.size) {
            val nextBean = beanList[currentIndex]
            currentBean = nextBean
            speakText(nextBean.data ?: "")
        } else {
            _isSpeakingFlow.value = false
            currentBean = null

            // 所有朗读完成时停止前台服务
            if (isForegroundServiceStarted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                isForegroundServiceStarted = false
            }
        }
        println("TTS: playNext:$currentBean")
        currentBean?.let { bean ->
            onSpeechCompleteCallback?.invoke(bean.page)
        }
    }

    private fun speakText(text: String) {
        if (text.isNotBlank()) {
            // 确保前台服务已启动
            if (!isForegroundServiceStarted) {
                startForeground(NOTIFICATION_ID, createNotification())
                isForegroundServiceStarted = true
            }

            val params = hashMapOf<String, String>().apply {
                put(
                    TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                    "utterance_${System.currentTimeMillis()}"
                )
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
        }
    }

    fun speak(reflowBean: ReflowBean) {
        if (isInitialized) {
            beanList.clear()
            beanList.add(reflowBean)
            currentIndex = 0
            currentBean = reflowBean
            speakText(reflowBean.data ?: "")
        }
    }

    fun addToQueue(reflowBean: ReflowBean) {
        if (isInitialized) {
            println("TTS: addToQueue:$reflowBean")
            beanList.add(reflowBean)

            // 如果当前没有在朗读，开始朗读
            if (!isSpeaking() && beanList.size == 1) {
                currentIndex = 0
                currentBean = reflowBean
                speakText(reflowBean.data ?: "")
            }
        }
    }

    fun pause() {
        textToSpeech?.stop()
        _isSpeakingFlow.value = false

        // 暂停时也停止前台服务，移除通知
        if (isForegroundServiceStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            isForegroundServiceStarted = false
        }
    }

    fun stop() {
        textToSpeech?.stop()
        beanList.clear()
        currentBean = null
        currentIndex = 0
        _isSpeakingFlow.value = false
        cancelSleepTimer()

        // 停止前台服务，移除通知
        if (isForegroundServiceStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            isForegroundServiceStarted = false
        }
    }

    fun clearQueue() {
        beanList.clear()
        currentIndex = 0
    }

    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    fun getQueueSize(): Int {
        return beanList.size
    }

    fun getQueue(): List<TtsTask> {
        return beanList.map { TtsTask(it, priority = 0) }
    }

    fun getCurrentReflowBean(): ReflowBean? {
        return currentBean
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerMinutes = minutes

        if (minutes > 0) {
            sleepTimerJob = serviceScope.launch {
                println("TTS: 设置睡眠定时器, $minutes 分钟")
                delay(minutes * 60 * 1000L) // 转换为毫秒

                println("TTS: 睡眠定时器到期，停止朗读")
                stop()
                sleepTimerMinutes = 0
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerMinutes = 0
        println("TTS: 取消睡眠定时器")
    }

    fun getSleepTimerMinutes(): Int {
        return sleepTimerMinutes
    }

    fun hasSleepTimer(): Boolean {
        return sleepTimerJob?.isActive == true
    }

    fun isServiceInitialized(): Boolean = isInitialized

    fun setOnSpeechCompleteCallback(callback: ((String?) -> Unit)?) {
        onSpeechCompleteCallback = callback
    }
}