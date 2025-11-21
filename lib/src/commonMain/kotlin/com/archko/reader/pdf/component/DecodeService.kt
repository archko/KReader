package com.archko.reader.pdf.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val pageTaskQueue = mutableListOf<DecodeTask>()
    private val nodeTaskQueue = mutableListOf<DecodeTask>()
    private val cropTaskQueue = mutableListOf<DecodeTask>()

    private val queueMutex = Mutex()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var processingJob: Job? = null

    private var isShutdown = false

    // 任务通知channel - 只用于通知有新任务，不传递任务内容
    private val taskNotificationChannel = Channel<Unit>(Channel.UNLIMITED)

    init {
        startProcessing()
    }

    private fun startProcessing() {
        processingJob = serviceScope.launch {
            taskProcessorLoop()
        }
    }

    private suspend fun taskProcessorLoop() {
        while (serviceScope.isActive && !isShutdown) {
            // 1. 按优先级选择任务执行
            val task = selectNextTask()
            if (task != null) {
                executeTask(task)
                continue
            }

            // 2. 没有任务时，等待新任务通知
            taskNotificationChannel.receive() // 阻塞等待通知
        }
    }

    private suspend fun selectNextTask(): DecodeTask? {
        return queueMutex.withLock {
            // 清理不可见的任务
            //cleanupInvisibleTasks()

            when {
                pageTaskQueue.isNotEmpty() -> pageTaskQueue.removeAt(0)
                nodeTaskQueue.isNotEmpty() -> nodeTaskQueue.removeAt(0)
                cropTaskQueue.isNotEmpty() -> cropTaskQueue.removeAt(0)
                else -> null
            }
        }
    }

    private fun cleanupInvisibleTasks() {
        // 清理页面任务中不可见的任务
        pageTaskQueue.removeAll { task ->
            val shouldRender = task.callback?.shouldRender(task.pageIndex, true) ?: true
            if (!shouldRender) {
                println("DecodeService.cleanupInvisibleTasks: 清理不可见页面任务 - page: ${task.pageIndex}")
            }
            !shouldRender
        }

        // 清理节点任务中不可见的任务
        nodeTaskQueue.removeAll { task ->
            val shouldRender = task.callback?.shouldRender(task.pageIndex, false) ?: true
            if (!shouldRender) {
                println("DecodeService.cleanupInvisibleTasks: 清理不可见节点任务 - page: ${task.pageIndex}")
            }
            !shouldRender
        }
    }

    private suspend fun addTaskToQueue(task: DecodeTask) {
        queueMutex.withLock {
            when (task.type) {
                DecodeTask.TaskType.PAGE -> {
                    // 移除相同key的旧任务，避免重复解码
                    pageTaskQueue.removeAll { it.decodeKey == task.decodeKey }
                    pageTaskQueue.add(task)
                }

                DecodeTask.TaskType.NODE -> {
                    nodeTaskQueue.removeAll { it.decodeKey == task.decodeKey }
                    nodeTaskQueue.add(task)
                }

                DecodeTask.TaskType.CROP -> {
                    cropTaskQueue.removeAll { it.decodeKey == task.decodeKey }
                    cropTaskQueue.add(task)
                }
            }
        }

        // 通知处理循环有新任务（非阻塞）
        taskNotificationChannel.trySend(Unit)
    }

    private suspend fun executeTask(task: DecodeTask) {
        if (isShutdown) return

        // 执行前检查任务是否仍然需要渲染
        val shouldRender =
            task.callback?.shouldRender(task.pageIndex, task.type == DecodeTask.TaskType.PAGE)
                ?: true
        if (!shouldRender) {
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
                    // crop任务通常没有callback，结果直接更新到APage
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

    public fun submitTask(task: DecodeTask) {
        if (isShutdown) return
        serviceScope.launch {
            addTaskToQueue(task)
        }
    }

    public fun submitCropTasks(tasks: List<DecodeTask>) {
        if (isShutdown) return
        serviceScope.launch {
            queueMutex.withLock {
                // 清除旧的切边任务，添加新的
                cropTaskQueue.clear()
                cropTaskQueue.addAll(tasks)
            }
            // 通知有新任务
            taskNotificationChannel.trySend(Unit)
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
    }
}