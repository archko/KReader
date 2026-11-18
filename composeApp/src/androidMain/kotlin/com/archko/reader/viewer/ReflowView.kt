package com.archko.reader.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.util.FileTypeUtils

/**
 * Reflow view — displays a document page-by-page using k2pdfopt.
 *
 * All decode work is delegated to [ReflowDecodeService]; this composable
 * only owns the lifecycle and scroll state.
 *
 * Supported decoders: PdfDecoder, DjvuDecoder (any [ImageDecoder] whose
 * [renderPage] returns a valid bitmap — image-only types are excluded via
 * [FileTypeUtils.canReflow]).
 */
@Composable
fun ReflowView(
    decoder: ImageDecoder,
    pageCount: Int,
    currentPath: String,
    onPageChanged: ((page: Int) -> Unit)? = null,
    jumpToPage: Int? = null,
    onSaveDocument: ((page: Int, pageCount: Int, zoom: Double, scrollX: Long, scrollY: Long, scrollOri: Long, reflow: Long, crop: Long, pageOffsetX: Long, pageOffsetY: Long) -> Unit)? = null,
    onCloseDocument: (() -> Unit)? = null,
    onDoubleTapToolbar: (() -> Unit)? = null,
    onTapNonPageArea: ((pageIndex: Int) -> Unit)? = null,
    initialScrollX: Long = 0L,
    initialScrollY: Long = 0L,
    initialZoom: Double = 1.0,
    initialOrientation: Int,
    reflow: Long = 0,
    wordSpacing: Float = 0.3f,
    fontSize: Float = 1.0f,
) {
    val context = LocalContext.current

    // ── Decode service: one instance per document open, recreated when
    //   screen metrics change (handled by remember key below). ────────────────
    val dm = context.resources.displayMetrics
    val decodeService = remember(decoder, currentPath) {
        ReflowDecodeService(
            context = context,
            decoder = decoder,
            documentPath = currentPath,
            screenWidth = dm.widthPixels,
            screenHeight = dm.heightPixels,
            densityDpi = dm.densityDpi,
            orientation = initialOrientation,
        )
    }

    DisposableEffect(decodeService) {
        onDispose { decodeService.shutdown() }
    }

    // ── Scroll state ──────────────────────────────────────────────────────────
    val lazyListState = rememberLazyListState()
    var currentPage by remember { mutableIntStateOf(0) }
    var toPage by remember { mutableIntStateOf(-1) }
    var isJumping by remember { mutableStateOf(false) }

    LaunchedEffect(jumpToPage) {
        isJumping = true
        if (jumpToPage != null && toPage != jumpToPage) {
            toPage = jumpToPage
            if (toPage in 0 until pageCount) {
                // 直接滚动到目标页面，不使用动画
                lazyListState.scrollToItem(toPage)
            }
        }
        isJumping = false
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { newPage ->
                if (!isJumping && newPage != currentPage) {
                    currentPage = newPage
                    onPageChanged?.invoke(newPage)
                }
            }
    }

    // ── Progress persistence ──────────────────────────────────────────────────
    fun saveState() {
        onSaveDocument?.invoke(
            currentPage, pageCount,
            initialZoom, initialScrollX, initialScrollY,
            initialOrientation.toLong(), reflow, 1L, 0L, 0L
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) saveState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            saveState()
            // 只在真正关闭文档（离开视图）时调用 onCloseDocument
            // 如果只是切换模式，不应该调用关闭回调
            // onCloseDocument?.invoke()
        }
    }

    // ── List ──────────────────────────────────────────────────────────────────
    val density = LocalDensity.current
    val tapPageKeepPx = remember {
        with(density) { 16.dp.toPx() }
    }
    
    // 存储待执行的滚动请求
    var scrollRequest by remember { mutableStateOf<Float?>(null) }
    
    // 在协程中执行滚动
    LaunchedEffect(scrollRequest) {
        scrollRequest?.let { amount ->
            lazyListState.scroll {
                scrollBy(amount)
            }
            scrollRequest = null
        }
    }
    
    LazyColumn(
        state = lazyListState,
        flingBehavior = ScrollableDefaults.flingBehavior(),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val h = size.height.toFloat()
                        when {
                            // 顶部 1/4 区域：上翻一屏（向上滚动屏幕高度 - tapPageKeepPx）
                            offset.y < h / 4 -> {
                                scrollRequest = -(h - tapPageKeepPx)
                            }
                            // 底部 3/4 区域：下翻一屏（向下滚动屏幕高度 - tapPageKeepPx）
                            offset.y > h * 3 / 4 -> {
                                scrollRequest = h - tapPageKeepPx
                            }
                            // 中间区域：点击非页面区域回调
                            else -> {
                                onTapNonPageArea?.invoke(currentPage.coerceIn(0, pageCount - 1))
                            }
                        }
                    },
                    onDoubleTap = { offset ->
                        val h = size.height.toFloat()
                        if (offset.y in h / 4..h * 3 / 4) {
                            onDoubleTapToolbar?.invoke()
                        }
                    }
                )
            },
    ) {
        itemsIndexed((0 until pageCount).toList()) { _, pageIndex ->
            ReflowPageItem(
                pageIndex = pageIndex,
                decodeService = decodeService,
                wordSpacing = wordSpacing,
                fontSize = fontSize,
            )
        }
    }
}

// ── Page item ─────────────────────────────────────────────────────────────────

@Composable
private fun ReflowPageItem(
    pageIndex: Int,
    decodeService: ReflowDecodeService,
    wordSpacing: Float,
    fontSize: Float,
) {
    var tiles by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val screenHeight = remember {
        context.resources.displayMetrics.heightPixels.toFloat()
    }

    // Re-request whenever page index or style params change
    LaunchedEffect(pageIndex, wordSpacing, fontSize) {
        isLoading = true
        tiles = emptyList()
        tiles = decodeService.requestPage(pageIndex, fontSize, wordSpacing)
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp)
    ) {
        when {
            isLoading && tiles.isEmpty() -> LoadingPlaceholder(screenHeight = screenHeight)
            tiles.isEmpty() -> EmptyPagePlaceholder(pageIndex)
            else -> tiles.forEach { tile ->
                Image(
                    bitmap = tile.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }

        // Page separator - divider only, no page number, 1/3 width from left
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth(fraction = 1f)
                .padding(horizontal = 0.dp, vertical = 0.dp),
            thickness = 1.dp,
            color = Color(0xFF555555)
        )
    }
}

@Composable
private fun LoadingPlaceholder(screenHeight: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((screenHeight).dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFF888888),
            strokeWidth = 2.dp,
            modifier = Modifier.height(32.dp)
        )
    }
}

@Composable
private fun EmptyPagePlaceholder(pageIndex: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color(0x11888888)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Page ${pageIndex + 1}", fontSize = 14.sp, color = Color.Gray)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns true for decoder types that support k2pdfopt reflow. */
fun canReflow(path: String): Boolean =
    FileTypeUtils.isDocumentFile(path) || FileTypeUtils.isDjvuFile(path)
