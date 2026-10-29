package com.archko.reader.pdf.component

internal actual fun setDecodeThreadPriority() {
    Thread.currentThread().priority = Thread.NORM_PRIORITY + 1
}
