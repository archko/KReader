package com.archko.reader.viewer.tts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.tts.TtsTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * TTS服务绑定器，用于在Compose中管理TTS服务连接
 */
class TtsServiceBinder(private val context: Context) {

    private var service: AndroidTtsForegroundService? = null
    private var isBound = false

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    val isSpeakingFlow: StateFlow<Boolean>?
        get() = service?.isSpeakingFlow

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            println("TtsServiceBinder: 服务连接成功")
            val serviceBinder = binder as? AndroidTtsForegroundService.TtsServiceBinder
            service = serviceBinder?.getService()
            isBound = true
            _isConnected.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            println("TtsServiceBinder: 服务连接断开")
            service = null
            isBound = false
            _isConnected.value = false
        }
    }

    fun bindService() {
        if (!isBound) {
            val intent = Intent(context, AndroidTtsForegroundService::class.java)
            // 处理API 30+的前台服务限制
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService() {
        service?.stop()
        if (isBound) {
            println("TtsServiceBinder: 解绑服务")
            context.unbindService(serviceConnection)
            isBound = false
            _isConnected.value = false
        }
    }

    fun speak(reflowBean: ReflowBean) {
        println("TtsServiceBinder: speak called")
        service?.speak(reflowBean)
    }

    fun addToQueue(reflowBean: ReflowBean) {
        //println("TtsServiceBinder: addToQueue called")
        service?.addToQueue(reflowBean)
    }

    fun pause() {
        println("TtsServiceBinder: pause called")
        service?.pause()
    }

    fun stop() {
        println("TtsServiceBinder: stop called")
        service?.stop()
    }

    fun clearQueue() {
        service?.clearQueue()
    }

    fun isSpeaking(): Boolean {
        return service?.isSpeaking() ?: false
    }

    fun getQueueSize(): Int {
        return service?.getQueueSize() ?: 0
    }

    fun getQueue(): List<TtsTask>? {
        return service?.getQueue()
    }

    fun getCurrentReflowBean(): ReflowBean? {
        return service?.getCurrentReflowBean()
    }

    fun isServiceInitialized(): Boolean {
        return service?.isServiceInitialized() ?: false
    }

    fun setSleepTimer(minutes: Int) {
        println("TtsServiceBinder: setSleepTimer called with $minutes minutes")
        service?.setSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        println("TtsServiceBinder: cancelSleepTimer called")
        service?.cancelSleepTimer()
    }

    fun getSleepTimerMinutes(): Int {
        return service?.getSleepTimerMinutes() ?: 0
    }

    fun hasSleepTimer(): Boolean {
        return service?.hasSleepTimer() ?: false
    }

    fun getCurrentSpeakingPage(): String? {
        return service?.getCurrentReflowBean()?.page
    }

    fun setOnSpeechCompleteCallback(callback: ((String?) -> Unit)?) {
        service?.setOnSpeechCompleteCallback(callback)
    }
}