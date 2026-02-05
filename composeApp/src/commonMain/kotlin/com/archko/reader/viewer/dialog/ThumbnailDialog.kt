package com.archko.reader.viewer.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.cache.ImageCache
import com.archko.reader.pdf.component.DecoderAdapter
import com.archko.reader.pdf.component.ImageDecoder
import com.archko.reader.pdf.entity.APage
import com.archko.reader.pdf.state.AnnotationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.snapshotFlow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * @author: archko 2026/2/6 :6:17
 */
@Composable
fun ThumbnailDialog(
    currentPage: Int,
    list: List<APage>,
    decoder: ImageDecoder,
    onPageClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val pageCount = list.size
    val lazyListState = rememberLazyListState()
    
    // 缩略图固定宽度
    val thumbnailWidth = 200
    
    // 创建单线程的CoroutineScope
    val singleThreadScope = remember {
        CoroutineScope(Dispatchers.IO + SupervisorJob())
    }

    // Dialog关闭时取消所有任务
    DisposableEffect(Unit) {
        onDispose {
            singleThreadScope.cancel()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(), // 充满整个屏幕高度
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back),
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "缩略图列表",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(), // 充满整个可用空间
                    contentPadding = PaddingValues(
                        horizontal = 8.dp,
                        vertical = 8.dp
                    )
                ) {
                    itemsIndexed(
                        pageCount,
                        key = { index, _ -> "thumbnail-$index" }
                    ) { index, _ ->
                        val isCurrentPage = index == currentPage
                        val aPage = list[index]
                        
                        ThumbnailItem(
                            index = index,
                            aPage = aPage,
                            width = thumbnailWidth,
                            decoder = decoder,
                            isSelected = isCurrentPage,
                            onClick = {
                                onPageClick(index)
                            },
                            scope = singleThreadScope
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailItem(
    index: Int,
    aPage: APage,
    width: Int,
    decoder: ImageDecoder,
    isSelected: Boolean,
    onClick: () -> Unit,
    scope: CoroutineScope
) {
    // 根据原始页面尺寸计算缩略图高度
    val (thumbWidth, thumbHeight) = DecoderAdapter.calculateThumbnailSize(
        aPage.width,
        aPage.height
    )
    
    val cacheKey = "thumb-${index}-${width}x${thumbHeight}"
    val imageState = remember { mutableStateOf<Painter?>(null) }
    val isLoading = remember { mutableStateOf(true) }
    
    // 预先设置固定高度，避免LazyColumn布局问题
    val itemModifier = Modifier
        .fillMaxWidth()
        .height(thumbHeight.dp)
        .padding(vertical = 2.dp)
        .clickable(onClick = onClick)

    if (isSelected) {
        itemModifier.background(Color(0x332196F3)) // 半透明蓝色背景
    }

    Box(
        modifier = itemModifier,
        contentAlignment = Alignment.Center
    ) {
        if (imageState.value != null) {
            androidx.compose.foundation.Image(
                painter = imageState.value!!,
                contentDescription = "页面 ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(width.dp, thumbHeight.dp)
                    .background(Color.White)
            )
        } else {
            // 显示加载指示器
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 页面编号
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(4.dp)
        )
    }

    // 启动缩略图加载 - 使用ImageCache和单线程加载
    androidx.compose.runtime.DisposableEffect(index, width, thumbHeight) {
        // 先尝试从缓存获取
        val cachedState = ImageCache.acquirePage(cacheKey)
        if (cachedState != null) {
            imageState.value = cachedState.bitmapPainter
            isLoading.value = false
        } else {
            // 缓存中没有，启动解码任务
            scope.launch {
                if (isActive) {
                    val bitmap = decoder.renderPage(
                        aPage,
                        width,
                        thumbHeight,
                        false // 不启用切边
                    )
                    if (bitmap != null) {
                        // 将解码结果存入缓存
                        val newState = ImageCache.putPage(cacheKey, bitmap)
                        imageState.value = newState.bitmapPainter
                        isLoading.value = false
                    }
                }
            }
        }
        
        onDispose {
            // 释放缓存资源
            cachedState?.let { ImageCache.releasePage(it) }
        }
    }
}
