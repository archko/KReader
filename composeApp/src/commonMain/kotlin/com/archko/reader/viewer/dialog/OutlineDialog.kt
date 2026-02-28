package com.archko.reader.viewer.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.component.AnnotationPath
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.state.AnnotationManager
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.annotation_empty
import kreader.composeapp.generated.resources.annotation_tab
import kreader.composeapp.generated.resources.annotations_count
import kreader.composeapp.generated.resources.delete_annotation
import kreader.composeapp.generated.resources.document_outline
import kreader.composeapp.generated.resources.ic_back
import kreader.composeapp.generated.resources.ic_delete
import kreader.composeapp.generated.resources.no_outline
import kreader.composeapp.generated.resources.outline_tab
import kreader.composeapp.generated.resources.page_label
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * @author: archko 2025/11/4 :15:17
 */
@Composable
fun OutlineDialog(
    currentPage: Int,
    outlineList: List<Item>,
    annotationManager: AnnotationManager?,
    onOutlineClick: (Item) -> Unit,
    onAnnotationClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var selectedTab by remember { mutableIntStateOf(0) }

        val hasOutline = outlineList.isNotEmpty()

        // 使用 Flow 来监听批注数据的变化
        val annotations = annotationManager?.annotationsFlow?.collectAsState()?.value
            ?: emptyMap()

        // 根据当前页码找到最接近的大纲项位置
        val initialOutlineIndex = outlineList.indexOfFirst { it.page >= currentPage }
            .takeIf { it != -1 } ?: outlineList.indexOfLast { it.page <= currentPage }
            .takeIf { it != -1 } ?: 0
        val lazyListState = rememberLazyListState(
            initialFirstVisibleItemIndex = initialOutlineIndex.coerceAtLeast(0)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f),
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
                        text = stringResource(Res.string.document_outline),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Tab 标签页
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(Res.string.outline_tab)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(Res.string.annotation_tab)) }
                    )
                }

                when (selectedTab) {
                    0 -> OutlineTabContent(
                        hasOutline = hasOutline,
                        outlineList = outlineList,
                        initialOutlineIndex = initialOutlineIndex,
                        lazyListState = lazyListState,
                        onOutlineClick = onOutlineClick
                    )

                    1 -> AnnotationTabContent(
                        annotations = annotations,
                        annotationManager = annotationManager,
                        onAnnotationClick = onAnnotationClick
                    )
                }
            }
        }
    }
}

@Composable
private fun OutlineTabContent(
    hasOutline: Boolean,
    outlineList: List<Item>,
    initialOutlineIndex: Int,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    onOutlineClick: (Item) -> Unit
) {
    if (!hasOutline) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.no_outline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            state = lazyListState
        ) {
            itemsIndexed(
                outlineList,
                key = { index, item -> index }) { index, item ->
                val isSelected = index == initialOutlineIndex
                val backgroundColor = if (isSelected) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .padding(start = (item.level * 8).dp)
                        .background(color = backgroundColor, shape = RoundedCornerShape(4.dp))
                        .clickable { onOutlineClick(item) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${item.page + 1}",
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnotationTabContent(
    annotations: Map<Int, List<AnnotationPath>>,
    annotationManager: AnnotationManager?,
    onAnnotationClick: (Int) -> Unit
) {
    if (annotations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.annotation_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 8.dp
            )
        ) {
            annotations.forEach { (pageIndex, paths) ->
                if (paths.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onAnnotationClick(pageIndex) }
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.page_label)
                                    .format(pageIndex + 1),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(Res.string.annotations_count)
                                    .format(paths.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    annotationManager?.deletePaths(pageIndex)
                                },
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_delete),
                                    contentDescription = stringResource(Res.string.delete_annotation),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
