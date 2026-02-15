package com.archko.reader.pdf.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 基于三队列优先级的解码服务
 * 参考Android DecodeServiceBase的设计，使用协程实现
 *
 * 优先级顺序：pageTask -> nodeTask -> cropTask
 * 只有当前优先级队列为空时，才处理下一优先级队列
 *
 * @author: archko 2025/1/10
 */
public class DecodeService(
    private val decoder: Decoder
) {

    // 全局单线程解码作用域
    public val dispatchScope: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Page-Dispatcher").apply { isDaemon = true }
    }

    // 使用线程安全的原子队列代替普通 List + Mutex
    private val pageTaskQueue = ConcurrentLinkedQueue<DecodeTask>()
    private val nodeTaskQueue = ConcurrentLinkedQueue<DecodeTask>()
    private val cropTaskQueue = ConcurrentLinkedQueue<DecodeTask>()

    private val decodeDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val serviceScope = CoroutineScope(SupervisorJob() + decodeDispatcher)

    private var processingJob: Job? = null
    private var isShutdown = false

    // 任务通知channel - 仅用于唤醒解码循环，不携带数据
    private val taskNotificationChannel = Channel<Unit>(Channel.UNLIMITED)

    init {
        startProcessing()
    }

    public fun submit(func: Runnable): Future<*> {
        return dispatchScope.submit(func)
    }

    private fun startProcessing() {
        processingJob = serviceScope.launch {
            taskProcessorLoop()
        }
    }

    private suspend fun taskProcessorLoop() {
        while (serviceScope.isActive && !isShutdown) {
            // 按照优先级获取下一个任务
            val task = selectNextTask()

            if (task != null) {
                executeTask(task)
            } else {
                // 队列全空时挂起，等待新信号
                taskNotificationChannel.receive()
            }
        }
    }

    /**
     * 优先级策略实现：Page -> Node -> Crop
     * 由于是单线程消费，直接 poll() 是线程安全的
     */
    private fun selectNextTask(): DecodeTask? {
        return pageTaskQueue.poll()
            ?: nodeTaskQueue.poll()
            ?: cropTaskQueue.poll()
    }

    public fun submitTask(task: DecodeTask) {
        if (isShutdown) return

        when (task.type) {
            DecodeTask.TaskType.PAGE -> pageTaskQueue.add(task)
            DecodeTask.TaskType.NODE -> nodeTaskQueue.add(task)
            DecodeTask.TaskType.CROP -> cropTaskQueue.add(task)
        }

        // 发出信号唤醒 taskProcessorLoop。trySend 是非阻塞原子操作。
        taskNotificationChannel.trySend(Unit)
    }

    /**
     * 批量提交切边任务
     */
    public fun submitCropTasks(tasks: List<DecodeTask>) {
        if (isShutdown) return

        // 清除旧任务并添加新任务。
        // 虽然多线程下 clear+addAll 不是绝对原子的，但在 PDF 切边场景下足够安全
        cropTaskQueue.clear()
        cropTaskQueue.addAll(tasks)

        taskNotificationChannel.trySend(Unit)
    }

    private suspend fun executeTask(task: DecodeTask) {
        if (isShutdown) return

        // 执行前检查任务是否仍然需要渲染
        val shouldRender =
            task.callback?.shouldRender(task.pageIndex, task.type == DecodeTask.TaskType.PAGE)
                ?: true
        if (!shouldRender) {
            // 保证node的状态可以恢复
            task.callback.onFinish(task.pageIndex)
            //println("DecodeService.executeTask: 跳过不可见任务 - page: ${task.pageIndex}, type: ${task.type}")
            return
        }

        try {
            when (task.type) {
                DecodeTask.TaskType.PAGE -> {
                    val bitmap = decoder.decodePage(task)
                    task.callback?.onDecodeComplete(bitmap, true, null)
                }

                DecodeTask.TaskType.NODE -> {
                    val bitmap = decoder.decodeNode(task)
                    task.callback?.onDecodeComplete(bitmap, false, null)
                }

                DecodeTask.TaskType.CROP -> {
                    val result = decoder.processCrop(task)
                    result?.let {
                        task.aPage.cropBounds = it.cropBounds
                    }
                }
            }
        } catch (e: Exception) {
            println("DecodeService.executeTask error: ${e.message}")
            task.callback?.onDecodeComplete(null, false, e)
        }
    }

    public fun shutdown() {
        isShutdown = true
        processingJob?.cancel()
        taskNotificationChannel.close()
        pageTaskQueue.clear()
        nodeTaskQueue.clear()
        cropTaskQueue.clear()
        serviceScope.cancel()
        dispatchScope.shutdownNow()
    }
}