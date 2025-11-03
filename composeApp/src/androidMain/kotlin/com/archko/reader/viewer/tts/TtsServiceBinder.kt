package com.archko.reader.viewer.tts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
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
    
    private val _isSpeakingFlow = MutableStateFlow(false)
    val isSpeakingFlow: StateFlow<Boolean> = _isSpeakingFlow.asStateFlow()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val serviceBinder = binder as? AndroidTtsForegroundService.TtsServiceBinder
            service = serviceBinder?.getService()
            isBound = true
            _isConnected.value = true
            
            // 开始监听服务的朗读状态
            service?.isSpeakingFlow?.let { flow ->
                // 这里需要在协程中收集，但为了简化，我们先用轮询方式
                startMonitoringSpeakingState()
            }
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            _isConnected.value = false
            _isSpeakingFlow.value = false
        }
    }
    
    private fun startMonitoringSpeakingState() {
        // 简化版本：定期检查朗读状态
        // 在实际项目中，应该使用协程来收集Flow
        android.os.Handler(android.os.Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                if (isBound && service != null) {
                    _isSpeakingFlow.value = service?.isSpeaking() ?: false
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 500)
                }
            }
        })
    }
    
    /**
     * 绑定TTS服务
     */
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
    
    /**
     * 解绑TTS服务
     */
    fun unbindService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            _isConnected.value = false
            _isSpeakingFlow.value = false
        }
    }
    
    /**
     * 开始朗读文本
     */
    fun speak(text: String) {
        service?.speak(text)
    }
    
    /**
     * 添加文本到朗读队列
     */
    fun addToQueue(text: String) {
        service?.addToQueue(text)
    }
    
    /**
     * 暂停朗读
     */
    fun pause() {
        service?.pause()
    }
    
    /**
     * 停止朗读
     */
    fun stop() {
        service?.stop()
    }
    
    /**
     * 清空朗读队列
     */
    fun clearQueue() {
        service?.clearQueue()
    }
    
    /**
     * 是否正在朗读
     */
    fun isSpeaking(): Boolean {
        return service?.isSpeaking() ?: false
    }
    
    /**
     * 获取队列大小
     */
    fun getQueueSize(): Int {
        return service?.getQueueSize() ?: 0
    }
    
    /**
     * 获取当前朗读的文本
     */
    fun getCurrentText(): String? {
        return service?.getCurrentText()
    }
    
    /**
     * 服务是否已初始化
     */
    fun isServiceInitialized(): Boolean {
        return service?.isServiceInitialized() ?: false
    }
}