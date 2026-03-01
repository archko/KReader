package com.archko.reader.viewer.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.archko.reader.pdf.entity.CustomImageData
import com.archko.reader.pdf.entity.Recent
import com.archko.reader.pdf.util.getAbsolutePath
import com.archko.reader.pdf.util.inferName
import com.archko.reader.pdf.viewmodel.ReadingStatsViewModel
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.annotation_count_stats
import kreader.composeapp.generated.resources.average_session_time
import kreader.composeapp.generated.resources.bookmark_count_stats
import kreader.composeapp.generated.resources.book_info
import kreader.composeapp.generated.resources.book_name
import kreader.composeapp.generated.resources.book_path
import kreader.composeapp.generated.resources.book_progress
import kreader.composeapp.generated.resources.book_read
import kreader.composeapp.generated.resources.book_read_count
import kreader.composeapp.generated.resources.book_size
import kreader.composeapp.generated.resources.cancel
import kreader.composeapp.generated.resources.completed_pages
import kreader.composeapp.generated.resources.consecutive_days
import kreader.composeapp.generated.resources.first_read_at
import kreader.composeapp.generated.resources.last_read_at
import kreader.composeapp.generated.resources.reading_stats
import kreader.composeapp.generated.resources.session_count
import kreader.composeapp.generated.resources.total_reading_time
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookInfoDialog(
    recent: Recent,
    readingStatsViewModel: ReadingStatsViewModel?,
    bookmarkViewModel: com.archko.reader.pdf.viewmodel.BookmarkViewModel? = null,
    onDismiss: () -> Unit,
    onRead: (Recent) -> Unit
) {
    val file = recent.path?.let { File(getAbsolutePath(it)) }
    val fileSize = file?.let { getFileSize(it.length()) } ?: ""
    val readCount = recent.readTimes ?: 0
    val progress = if (recent.pageCount != null && recent.pageCount!! > 0) {
        (recent.page?.toDouble() ?: 0.0) / recent.pageCount!! * 100
    } else {
        0.0
    }
    
    // 加载阅读统计
    val stats by readingStatsViewModel?.currentStats?.collectAsState() ?: remember { mutableStateOf(null) }
    
    // 加载书签数量
    val bookmarks by bookmarkViewModel?.currentPathBookmarks?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val bookmarkCount = bookmarks.size
    
    LaunchedEffect(recent.path) {
        recent.path?.let { path ->
            val absolutePath = getAbsolutePath(path)
            // 先加载统计数据
            readingStatsViewModel?.loadStats(absolutePath)
            // 加载书签
            bookmarkViewModel?.loadBookmarks(absolutePath)
        }
    }
    
    // 如果stats为null，显示提示信息
    val displayStats = stats
    val hasAnyData = displayStats != null || bookmarkCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.book_info),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .align(alignment = Alignment.CenterHorizontally)
                        .width(200.dp)
                        .height(260.dp)
                        .padding(bottom = 16.dp)
                ) {
                    AsyncImage(
                        model = recent.path?.let {
                            CustomImageData(
                                getAbsolutePath(it),
                                200,
                                260
                            )
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.book_name)
                            .format(recent.path?.inferName() ?: ""),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = stringResource(Res.string.book_path)
                            .format(recent.path ?: ""),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(Res.string.book_size)
                                .format(fileSize),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = stringResource(Res.string.book_read_count)
                                .format(readCount),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Text(
                        text = stringResource(Res.string.book_progress)
                            .format(progress),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // 阅读统计部分 - 如果有统计数据或书签就显示
                    if (hasAnyData) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        Text(
                            text = stringResource(Res.string.reading_stats),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        displayStats?.let { readingStats ->
                            Text(
                                text = stringResource(Res.string.total_reading_time)
                                    .format(formatDuration(readingStats.totalReadingTime)),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(Res.string.session_count)
                                        .format(readingStats.sessionCount),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = stringResource(Res.string.average_session_time)
                                        .format(formatDuration(readingStats.averageSessionTime)),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(Res.string.completed_pages)
                                        .format(readingStats.completedPages, readingStats.totalPages),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = stringResource(Res.string.consecutive_days)
                                        .format(readingStats.consecutiveDays),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(Res.string.annotation_count_stats)
                                        .format(readingStats.annotationCount),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Text(
                                    text = stringResource(Res.string.bookmark_count_stats)
                                        .format(bookmarkCount),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            
                            Text(
                                text = stringResource(Res.string.first_read_at)
                                    .format(dateFormat.format(Date(readingStats.firstReadAt))),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            Text(
                                text = stringResource(Res.string.last_read_at)
                                    .format(dateFormat.format(Date(readingStats.lastReadAt))),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        } ?: run {
                            // 如果没有阅读统计但有书签，只显示书签数量
                            if (bookmarkCount > 0) {
                                Text(
                                    text = stringResource(Res.string.bookmark_count_stats)
                                        .format(bookmarkCount),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
                Button(
                    onClick = {
                        onRead(recent)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(Res.string.book_read))
                }
            }
        }
    )
}

fun getFileSize(size: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var index = 0
    var fileSize = size.toDouble()
    while (fileSize >= 1024 && index < units.size - 1) {
        fileSize /= 1024
        index++
    }
    return String.format("%.2f %s", fileSize, units[index])
}


/**
 * 格式化时长显示
 */
fun formatDuration(seconds: Long): String {
    if (seconds < 60) {
        return "${seconds}秒"
    }
    val minutes = seconds / 60
    if (minutes < 60) {
        val remainingSeconds = seconds % 60
        return if (remainingSeconds > 0) {
            "${minutes}分${remainingSeconds}秒"
        } else {
            "${minutes}分钟"
        }
    }
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (remainingMinutes > 0) {
        "${hours}小时${remainingMinutes}分钟"
    } else {
        "${hours}小时"
    }
}
