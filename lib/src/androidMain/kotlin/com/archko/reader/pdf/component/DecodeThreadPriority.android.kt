package com.archko.reader.pdf.component

import android.os.Process

internal actual fun setDecodeThreadPriority() {
    // 解码线程不要和 RenderThread/UI 抢调度：DISPLAY 优先级会让大倍率下的
    // tile 光栅化直接拖慢渲染线程，导致滑动掉帧。
    Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
}
