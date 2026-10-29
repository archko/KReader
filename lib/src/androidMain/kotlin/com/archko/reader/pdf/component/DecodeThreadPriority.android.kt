package com.archko.reader.pdf.component

import android.os.Process

internal actual fun setDecodeThreadPriority() {
    Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY)
}
