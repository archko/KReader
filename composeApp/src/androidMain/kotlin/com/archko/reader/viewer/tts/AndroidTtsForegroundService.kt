package com.archko.reader.viewer.tts

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.archko.reader.pdf.tts.TtsTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class AndroidTtsForegroundService : Service(), TextToSpeech.OnInitListener {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "tts_service_channel"
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_STOP = "action_stop"
    }

    private val binder = TtsServiceBinder()
    private var textToSpeech: TextToSpeech? = null
    private var ttsQueueService: TtsQueueService? = null
    private var isInitialized = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 睡眠定时相关
    private var sleepTimerJob: Job? = null
    private var sleepTimerMinutes: Int = 0

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
            ACTION_PLAY_PAUSE -> {
                if (isSpeaking()) {
                    pause()
                } else {
                    // 这里需要恢复播放，但由于没有保存状态，暂时不处理
                }
            }

            ACTION_STOP -> {
                stop()
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        ttsQueueService?.destroy()
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
        val playPauseIntent = Intent(this, AndroidTtsForegroundService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val playPausePendingIntent = PendingIntent.getService(
            this,
            0,
            playPauseIntent,
            flags
        )

        val stopIntent = Intent(this, AndroidTtsForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("文档朗读")
            .setContentText(if (isSpeaking()) "正在朗读..." else "已暂停")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(
                if (isSpeaking()) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isSpeaking()) "暂停" else "播放",
                playPausePendingIntent
            )
            .addAction(android.R.drawable.ic_delete, "停止", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun initializeTts() {
        textToSpeech = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.CHINA
            ttsQueueService = TtsQueueService(textToSpeech!!)
            isInitialized = true

            // 监听朗读状态变化，更新通知
            serviceScope.launch {
                ttsQueueService?.isSpeakingFlow?.collect { isSpeaking ->
                    updateNotification()
                    if (!isSpeaking && getQueueSize() == 0) {
                        // 如果没有在朗读且队列为空，可以考虑停止服务
                        // 这里暂时不自动停止，让用户手动控制
                    }
                }
            }
        }
    }

    fun speak(reflowBean: com.archko.reader.pdf.entity.ReflowBean) {
        if (isInitialized) {
            ttsQueueService?.speak(reflowBean)
        }
    }

    fun addToQueue(reflowBean: com.archko.reader.pdf.entity.ReflowBean) {
        if (isInitialized) {
            ttsQueueService?.addToQueue(reflowBean)
        }
    }

    fun pause() {
        ttsQueueService?.pause()
        updateNotification()
    }

    fun stop() {
        ttsQueueService?.stop()
        cancelSleepTimer()
        updateNotification()
    }

    fun clearQueue() {
        ttsQueueService?.clearQueue()
    }

    fun isSpeaking(): Boolean {
        return ttsQueueService?.isSpeaking() ?: false
    }

    fun getQueueSize(): Int {
        return ttsQueueService?.getQueueSize() ?: 0
    }

    fun getQueue(): List<TtsTask>? {
        return ttsQueueService?.getQueue()
    }

    fun getCurrentReflowBean(): com.archko.reader.pdf.entity.ReflowBean? {
        return ttsQueueService?.getCurrentReflowBean()
    }

    /**
     * 设置睡眠定时器
     * @param minutes 定时分钟数
     */
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerMinutes = minutes

        if (minutes > 0) {
            sleepTimerJob = serviceScope.launch {
                println("TTS: 设置睡眠定时器 $minutes 分钟")
                delay(minutes * 60 * 1000L) // 转换为毫秒

                println("TTS: 睡眠定时器到期，停止朗读")
                stop()
                sleepTimerMinutes = 0
            }
        }
    }

    /**
     * 取消睡眠定时器
     */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerMinutes = 0
        println("TTS: 取消睡眠定时器")
    }

    /**
     * 获取剩余睡眠时间（分钟）
     */
    fun getSleepTimerMinutes(): Int {
        return sleepTimerMinutes
    }

    /**
     * 是否设置了睡眠定时器
     */
    fun hasSleepTimer(): Boolean {
        return sleepTimerJob?.isActive == true
    }

    val isSpeakingFlow: StateFlow<Boolean>?
        get() = ttsQueueService?.isSpeakingFlow

    fun isServiceInitialized(): Boolean = isInitialized
}