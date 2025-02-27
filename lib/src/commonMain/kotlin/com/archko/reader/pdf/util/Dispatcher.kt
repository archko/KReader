package com.archko.reader.pdf.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * @author: archko 2025/1/5 :08:51
 */
public object Dispatcher {

    @JvmStatic
    public val DECODE: CoroutineDispatcher = Executors.newSingleThreadExecutor()
        .asCoroutineDispatcher()
}