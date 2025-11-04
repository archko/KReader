package com.archko.reader.viewer.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.entity.Item
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.back
import kreader.composeapp.generated.resources.document_outline
import kreader.composeapp.generated.resources.ic_back
import kreader.composeapp.generated.resources.no_outline
import kreader.composeapp.generated.resources.page_number
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * @author: archko 2025/11/4 :15:17
 */
@Composable
fun OutlineDialog(
    currentPage: Int,
    outlineList: List<Item>,
    onClick: (Item) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val hasOutline = outlineList.isNotEmpty()
        // 根据当前页码找到最接近的大纲项位置
        val initialOutlineIndex = outlineList.indexOfFirst { it.page >= currentPage }
            .takeIf { it != -1 } ?: outlineList.indexOfLast { it.page <= currentPage }
            .takeIf { it != -1 } ?: 0
        val lazyListState = rememberLazyListState(
            initialFirstVisibleItemIndex = initialOutlineIndex.coerceAtLeast(
                0
            )
        )
        Surface(
            modifier = if (hasOutline) Modifier.fillMaxSize() else Modifier.wrapContentSize(),
            color = Color.White.copy(alpha = 0.8f),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = if (hasOutline) Modifier.fillMaxSize() else Modifier.wrapContentSize()
            ) {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_back),
                                contentDescription = stringResource(Res.string.back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Text(
                        stringResource(Res.string.document_outline),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (!hasOutline) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(Res.string.no_outline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = lazyListState
                    ) {
                        itemsIndexed(
                            outlineList,
                            key = { index, item -> index }) { index, item ->
                            val isSelected = index == initialOutlineIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onClick(item)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title ?: "",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(Res.string.page_number).format(
                                        item.page + 1
                                    ),
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
        }
    }
}