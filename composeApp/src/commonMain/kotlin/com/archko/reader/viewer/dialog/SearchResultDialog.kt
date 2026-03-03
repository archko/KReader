package com.archko.reader.viewer.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.component.SearchResult
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.ic_close
import kreader.composeapp.generated.resources.search_results_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 搜索结果列表弹窗
 * @author: archko 2026/3/3
 */
@Composable
fun SearchResultDialog(
    query: String,
    results: List<SearchResult>,
    currentIndex: Int,
    onResultClick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.search_results_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "\"$query\"",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Divider(color = Color.Gray.copy(alpha = 0.4f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    itemsIndexed(results) { index, result ->
                        SearchResultItem(
                            result = result,
                            query = query,
                            isSelected = index == currentIndex,
                            onClick = { onResultClick(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: SearchResult,
    query: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "${result.pageIndex + 1} 页",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
        // 上下文预览，高亮关键字
        Text(
            text = buildAnnotatedString {
                val context = result.context.trim()
                val lowerContext = context.lowercase()
                val lowerQuery = query.lowercase()
                
                var lastIndex = 0
                var startIndex = lowerContext.indexOf(lowerQuery, lastIndex)
                
                while (startIndex != -1) {
                    // 添加关键字之前的文本
                    if (startIndex > lastIndex) {
                        append(context.substring(lastIndex, startIndex))
                    }
                    
                    // 添加高亮的关键字 - 使用背景色高亮
                    withStyle(
                        style = SpanStyle(
                            background = Color(0xFFFFEB3B),
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(context.substring(startIndex, startIndex + query.length))
                    }
                    
                    lastIndex = startIndex + query.length
                    startIndex = lowerContext.indexOf(lowerQuery, lastIndex)
                }
                
                // 添加剩余文本
                if (lastIndex < context.length) {
                    append(context.substring(lastIndex))
                }
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp,
            maxLines = 3
        )
    }
    
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    )
}
