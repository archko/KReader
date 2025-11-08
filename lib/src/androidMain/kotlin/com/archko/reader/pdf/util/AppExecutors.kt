package com.archko.reader.pdf.util

import android.os.Handler
import android.os.Looper

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Global executor pools for the whole application.
 *
 * Grouping tasks like this avoids the effects of task starvation (e.g. disk reads don't wait behind
 * webservice requests).
 */
public open class AppExecutors(
    private val diskIO: ExecutorService,
    private val networkIO: ExecutorService,
    private val mainThread: Executor
) {
    private object Holder {
        val INSTANCE = AppExecutors()
    }

    public companion object {
        public val instance: AppExecutors by lazy { Holder.INSTANCE }
    }

    public constructor() : this(
        Executors.newSingleThreadExecutor(),
        Executors.newFixedThreadPool(3),
        MainThreadExecutor()
    )

    public fun diskIO(): ExecutorService {
        return diskIO
    }

    public fun networkIO(): ExecutorService {
        return networkIO
    }

    public fun mainThread(): Executor {
        return mainThread
    }

    private class MainThreadExecutor : Executor {
        private val mainThreadHandler = Handler(Looper.getMainLooper())
        override fun execute(command: Runnable) {
            mainThreadHandler.post(command)
        }
    }
}
