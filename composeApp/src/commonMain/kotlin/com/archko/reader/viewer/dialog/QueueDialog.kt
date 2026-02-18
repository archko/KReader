package com.archko.reader.viewer.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.entity.ReflowBean
import com.archko.reader.pdf.entity.ReflowCacheBean
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.ic_back
import kreader.composeapp.generated.resources.tts_empty_queue
import kreader.composeapp.generated.resources.tts_page_item
import kreader.composeapp.generated.resources.tts_queue_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * @author: archko 2025/11/4 :09:09
 */
@Composable
fun QueueDialog(
    cacheBean: ReflowCacheBean?,
    onDismiss: () -> Unit,
    onItemClick: ((ReflowBean) -> Unit)? = null,
    count: Int = 14,
    currentSpeakingPage: String? = null, // 当前朗读的页面
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            val width = maxWidth * 0.9f
            val height = maxHeight * 0.9f
            Surface(
                modifier = Modifier.size(width, height),
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
                            text = stringResource(Res.string.tts_queue_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (cacheBean != null) {
                        val listState = rememberLazyListState()

                        // 自动滚动到当前朗读的项目
                        LaunchedEffect(currentSpeakingPage) {
                            currentSpeakingPage?.let { speakingPage ->
                                val index =
                                    cacheBean.reflow.indexOfFirst { it.page == speakingPage }
                                if (index >= 0) {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(
                                horizontal = 8.dp,
                                vertical = 8.dp
                            )
                        ) {
                            itemsIndexed(
                                cacheBean.reflow,
                                key = { index, item -> index }) { index, item ->
                                val isCurrentSpeaking = item.page == currentSpeakingPage
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrentSpeaking) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    ),
                                    onClick = {
                                        onItemClick?.invoke(item)
                                    }
                                ) {
                                    val pageNumber = (item.page?.toIntOrNull() ?: 0) + 1
                                    Text(
                                        text = stringResource(Res.string.tts_page_item)
                                            .format(
                                                pageNumber,
                                                item.data?.take(count)
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.tts_empty_queue),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}