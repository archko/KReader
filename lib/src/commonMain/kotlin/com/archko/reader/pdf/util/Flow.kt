package com.archko.reader.pdf.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public fun <T> Flow<T>.throttle(wait: Long): Flow<T> = channelFlow {
    val channel = Channel<T>(capacity = Channel.CONFLATED)
    coroutineScope {
        launch {
            collect {
                channel.send(it)
            }
        }
        launch {
            for (e in channel) {
                send(e)
                delay(wait)
            }
        }
    }
}

public fun <T, M> StateFlow<T>.map(
    coroutineScope : CoroutineScope,
    mapper : (value : T) -> M
) : StateFlow<M> = map { mapper(it) }.stateIn(
    coroutineScope,
    SharingStarted.Eagerly,
    mapper(value)
)

@OptIn(FlowPreview::class)
public fun <T> CoroutineScope.debounce(
    timeoutMillis: Long,
    block: suspend (T) -> Unit
): SendChannel<T> {
    val channel = Channel<T>(capacity = Channel.CONFLATED)
    val flow = channel.receiveAsFlow().debounce(timeoutMillis)
    launch {
        flow.collect {
            block(it)
        }
    }

    return channel
}

public fun CoroutineScope.throttle(wait: Long, block: suspend () -> Unit): SendChannel<Unit> {
    val channel = Channel<Unit>(capacity = Channel.CONFLATED)
    val flow = channel.receiveAsFlow()

    launch {
        flow.collect {
            block()
            delay(wait)
        }
    }
    return channel
}