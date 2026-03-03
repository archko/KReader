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
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.component.AnnotationPath
import com.archko.reader.pdf.entity.AIPageConversation
import com.archko.reader.pdf.entity.Bookmark
import com.archko.reader.pdf.entity.Item
import com.archko.reader.pdf.state.AnnotationManager
import com.archko.reader.pdf.viewmodel.AIViewModel
import com.archko.reader.pdf.viewmodel.BookmarkViewModel
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * @author: archko 2025/11/4 :15:17
 */
@Composable
fun OutlineDialog(
    currentPage: Int,
    currentPath: String,
    outlineList: List<Item>,
    annotationManager: AnnotationManager?,
    bookmarkViewModel: BookmarkViewModel?,
    aiViewModel: AIViewModel?,
    onOutlineClick: (Item) -> Unit,
    onAnnotationClick: (Int) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onEditBookmark: (Bookmark) -> Unit,
    onAIConversationClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var selectedTab by remember { mutableIntStateOf(0) }

        val hasOutline = outlineList.isNotEmpty()

        val annotations = annotationManager?.annotationsFlow?.collectAsState()?.value
            ?: emptyMap()

        val bookmarks = bookmarkViewModel?.currentPathBookmarks?.collectAsState()?.value
            ?: emptyList()

        val aiConversations = aiViewModel?.conversations?.collectAsState()?.value
            ?: emptyList()

        // 加载 AI 对话记录
        LaunchedEffect(currentPath) {
            aiViewModel?.loadAllConversations(currentPath)
        }

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
                SecondaryTabRow(
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
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text(stringResource(Res.string.bookmark_tab)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text(stringResource(Res.string.ai_chat_tab)) }
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

                    2 -> BookmarkTabContent(
                        bookmarks = bookmarks,
                        onBookmarkClick = onBookmarkClick,
                        onEditBookmark = onEditBookmark,
                        onDeleteBookmark = { bookmark ->
                            bookmarkViewModel?.deleteBookmark(bookmark)
                        }
                    )

                    3 -> AIConversationTabContent(
                        conversations = aiConversations,
                        onConversationClick = onAIConversationClick
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

@Composable
private fun BookmarkTabContent(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
    onEditBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.bookmark_empty),
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
            itemsIndexed(
                bookmarks,
                key = { _, bookmark -> bookmark.id }
            ) { _, bookmark ->
                BookmarkListItem(
                    bookmark = bookmark,
                    onClick = { onBookmarkClick(bookmark) },
                    onEdit = { onEditBookmark(bookmark) },
                    onDelete = { onDeleteBookmark(bookmark) }
                )
            }
        }
    }
}

@Composable
private fun BookmarkListItem(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val bookmarkColor = bookmark.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val createTime = dateFormat.format(Date(bookmark.createAt))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 颜色标记
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .background(bookmarkColor, RoundedCornerShape(2.dp))
                .padding(horizontal = 3.dp, vertical = 16.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.page_label).format(bookmark.pageIndex + 1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!bookmark.title.isNullOrBlank()) {
                    Text(
                        text = " - ${bookmark.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!bookmark.note.isNullOrBlank()) {
                Text(
                    text = bookmark.note!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = createTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // 编辑按钮
        IconButton(
            onClick = onEdit,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_edit),
                contentDescription = stringResource(Res.string.edit_bookmark),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // 删除按钮
        IconButton(
            onClick = onDelete,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_delete),
                contentDescription = stringResource(Res.string.delete_bookmark),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AIConversationTabContent(
    conversations: List<AIPageConversation>,
    onConversationClick: (Int) -> Unit
) {
    if (conversations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.ai_chat_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        // 按页面分组
        val conversationsByPage = conversations.groupBy { it.pageIndex }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 8.dp
            )
        ) {
            conversationsByPage.forEach { (pageIndex, pageConversations) ->
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onConversationClick(pageIndex) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(Res.string.page_label).format(pageIndex + 1),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(Res.string.ai_conversations_count)
                                    .format(pageConversations.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            // 显示最后一条对话的问题预览
                            pageConversations.firstOrNull()?.let { lastConv ->
                                Text(
                                    text = lastConv.question,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Icon(
                            painter = painterResource(Res.drawable.ic_ai),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
