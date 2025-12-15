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
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.archko.reader.pdf.entity.ReflowBean
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

// TTS监听器接口
interface TtsProgressListener {
    fun onStart(bean: ReflowBean)
    fun onDone(bean: ReflowBean)
    fun onFinish()
}

class AndroidTtsForegroundService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tts_service_channel"
        const val ACTION_STOP = "action_stop"
    }

    private val binder = TtsServiceBinder()
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    // TTS监听器
    private var progressListener: TtsProgressListener? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 睡眠定时相关
    private var sleepTimerJob: Job? = null
    private var sleepTimerMinutes: Int = 0

    private val beanList = mutableListOf<ReflowBean>()
    private var currentBean: ReflowBean? = null
    private var currentIndex = 0

    // Flow for speaking state
    private val _isSpeakingFlow = MutableStateFlow(false)
    val isSpeakingFlow: StateFlow<Boolean> = _isSpeakingFlow.asStateFlow()

    // 前台服务状态
    private var isForegroundStarted = false
    private var wakeLock: PowerManager.WakeLock? = null

    // 文档路径，用于保存临时进度
    var documentPath: String? = null

    inner class TtsServiceBinder : Binder() {
        fun getService(): AndroidTtsForegroundService = this@AndroidTtsForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initWakeLock()
        initializeTts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        shutdown()
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
            this, 1, stopIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
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
        if (isForegroundStarted) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun startForegroundIfNeeded() {
        if (!isForegroundStarted) {
            startForeground(NOTIFICATION_ID, createNotification())
            isForegroundStarted = true
            wakeLock?.acquire()
        }
    }

    private fun stopForegroundIfNeeded() {
        if (isForegroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            isForegroundStarted = false
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun initWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TtsForegroundService:TTS")
    }

    private fun initializeTts() {
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
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
                println(String.format("onStart:%s, index:%s, size:%s", utteranceId, currentIndex, beanList.size))
                currentBean?.let { progressListener?.onStart(it) }
                _isSpeakingFlow.value = true
                startForegroundIfNeeded()
            }

            override fun onDone(utteranceId: String?) {
                println(String.format("onDone:%s, index:%s, size:%s", utteranceId, currentIndex, beanList.size))
                currentBean?.let { progressListener?.onDone(it) }
                playNext()
            }

            override fun onError(utteranceId: String?) {
                println("TTS: Error occurred for utterance: $utteranceId, stopping TTS")
                stop()
            }
        })
    }

    private fun playNext() {
        currentBean?.let { bean ->
            TtsTempProgressHelper.saveTempProgress(bean.page, documentPath, this)
        }
        if (currentIndex < beanList.size) {
            currentBean = beanList[currentIndex]
            val text = currentBean?.data
            if (!text.isNullOrBlank()) {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null, currentBean?.page)
                currentIndex++
            } else {
                currentIndex++
                playNext() // 跳过空内容
            }
        } else {
            // 所有项目播放完成
            stopForegroundIfNeeded()
            wakeLock?.takeIf { it.isHeld }?.release()
            progressListener?.onFinish()
        }
    }

    fun setProgressListener(listener: TtsProgressListener) {
        progressListener = listener
    }

    fun speak(reflowBean: ReflowBean) {
        if (isInitialized) {
            reset()
            addToQueue(reflowBean)
            playNext()
        }
    }

    fun addToQueue(reflowBean: ReflowBean) {
        val data = reflowBean.data
        val segmentCount = if (data.isNullOrBlank() || data.length <= 300) 1 else (data.length / 300.0).toInt() + 1
        if (data.isNullOrBlank() || data.length <= 300) {
            beanList.add(reflowBean)
        } else {
            var index = 0
            for (i in data.indices step 300) {
                val sub = data.substring(i, (i + 300).coerceAtMost(data.length))
                beanList.add(ReflowBean(sub, reflowBean.type, reflowBean.page + "-$index"))
                index++
            }
        }
        if (!isSpeaking() && isInitialized && beanList.size == segmentCount) {
            currentIndex = 0
            playNext()
        }
    }

    fun addToQueue(beans: List<ReflowBean>) {
        println("addToQueue beans size: ${beans.size}")
        val allSegments = mutableListOf<ReflowBean>()
        for (bean in beans) {
            val data = bean.data
            if (data != null && data.length > 300) {
                var index = 0
                for (i in data.indices step 300) {
                    val sub = data.substring(i, (i + 300).coerceAtMost(data.length))
                    allSegments.add(ReflowBean(sub, bean.type, bean.page + "-$index"))
                    index++
                }
            } else {
                allSegments.add(bean)
            }
        }
        beanList.addAll(allSegments)
        if (!isSpeaking() && isInitialized) {
            currentIndex = 0
            playNext()
        }
    }

    fun stop() {
        textToSpeech?.stop()
        reset()
        stopForegroundIfNeeded()
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    fun stopAndClear() {
        val lastPage = currentBean?.page
        textToSpeech?.stop()
        reset()
        progressListener?.onFinish()
        stopForegroundIfNeeded()
        wakeLock?.takeIf { it.isHeld }?.release()
        _isSpeakingFlow.value = false
        cancelSleepTimer()
    }

    fun pause() {
        textToSpeech?.stop()
        _isSpeakingFlow.value = false
        stopForegroundIfNeeded()
        wakeLock?.takeIf { it.isHeld }?.release()
    }

    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    fun reset() {
        beanList.clear()
        currentIndex = 0
        currentBean = null
    }

    fun clearQueue() {
        beanList.clear()
        currentIndex = 0
    }

    fun getQueueSize(): Int = beanList.size

    fun getQueue(): List<ReflowBean> {
        return beanList.toList()
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

    fun shutdown() {
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        reset()
        stopForegroundIfNeeded()
        wakeLock?.takeIf { it.isHeld }?.release()
        serviceScope.cancel()
    }

    fun isServiceInitialized(): Boolean = isInitialized
}
