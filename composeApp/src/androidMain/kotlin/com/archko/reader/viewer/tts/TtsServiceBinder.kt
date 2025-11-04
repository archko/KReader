package com.archko.reader.viewer.tts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.tts.TtsTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TTS服务绑定器，用于在Compose中管理TTS服务连接
 */
class TtsServiceBinder(private val context: Context) {

    private var service: AndroidTtsForegroundService? = null
    private var isBound = false

    // 创建协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isSpeakingFlow = MutableStateFlow(false)
    val isSpeakingFlow: StateFlow<Boolean> = _isSpeakingFlow.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            println("TtsServiceBinder: 服务连接成功")
            val serviceBinder = binder as? AndroidTtsForegroundService.TtsServiceBinder
            service = serviceBinder?.getService()
            isBound = true
            _isConnected.value = true

            // 等待服务初始化完成后再监听Flow
            scope.launch {
                // 轮询等待服务初始化完成
                while (service?.isSpeakingFlow == null && service?.isServiceInitialized() != true) {
                    kotlinx.coroutines.delay(100)
                }

                service?.isSpeakingFlow?.let { serviceFlow ->
                    println("TtsServiceBinder: 服务初始化完成，开始监听朗读状态Flow")
                    serviceFlow.collect { isSpeaking ->
                        println("TtsServiceBinder: 朗读状态变化: $isSpeaking")
                        _isSpeakingFlow.value = isSpeaking
                    }
                } ?: run {
                    println("TtsServiceBinder: 服务初始化超时，isSpeakingFlow仍为null")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            println("TtsServiceBinder: 服务连接断开")
            service = null
            isBound = false
            _isConnected.value = false
            _isSpeakingFlow.value = false
        }
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
        service?.stop()
        if (isBound) {
            println("TtsServiceBinder: 解绑服务")
            context.unbindService(serviceConnection)
            isBound = false
            _isConnected.value = false
            _isSpeakingFlow.value = false
        }
        // 取消协程作用域
        scope.cancel()
    }

    /**
     * 开始朗读ReflowBean
     */
    fun speak(reflowBean: ReflowBean) {
        println("TtsServiceBinder: speak called")
        service?.speak(reflowBean)
    }

    /**
     * 添加ReflowBean到朗读队列
     */
    fun addToQueue(reflowBean: ReflowBean) {
        //println("TtsServiceBinder: addToQueue called")
        service?.addToQueue(reflowBean)
    }

    /**
     * 暂停朗读
     */
    fun pause() {
        println("TtsServiceBinder: pause called")
        service?.pause()
    }

    /**
     * 停止朗读
     */
    fun stop() {
        println("TtsServiceBinder: stop called")
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

    fun getQueue(): List<TtsTask>? {
        return service?.getQueue()
    }

    /**
     * 获取当前朗读的ReflowBean
     */
    fun getCurrentReflowBean(): ReflowBean? {
        return service?.getCurrentReflowBean()
    }

    /**
     * 服务是否已初始化
     */
    fun isServiceInitialized(): Boolean {
        return service?.isServiceInitialized() ?: false
    }
    
    /**
     * 设置睡眠定时器
     */
    fun setSleepTimer(minutes: Int) {
        println("TtsServiceBinder: setSleepTimer called with $minutes minutes")
        service?.setSleepTimer(minutes)
    }
    
    /**
     * 取消睡眠定时器
     */
    fun cancelSleepTimer() {
        println("TtsServiceBinder: cancelSleepTimer called")
        service?.cancelSleepTimer()
    }
    
    /**
     * 获取剩余睡眠时间
     */
    fun getSleepTimerMinutes(): Int {
        return service?.getSleepTimerMinutes() ?: 0
    }
    
    /**
     * 是否设置了睡眠定时器
     */
    fun hasSleepTimer(): Boolean {
        return service?.hasSleepTimer() ?: false
    }
}