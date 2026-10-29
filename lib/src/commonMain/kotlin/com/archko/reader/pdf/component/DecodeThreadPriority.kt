package com.archko.reader.pdf.component

/**
 * 提升解码线程的调度优先级，降低缩略图出现延迟。
 */
internal expect fun setDecodeThreadPriority()
