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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.book_info
import kreader.composeapp.generated.resources.book_name
import kreader.composeapp.generated.resources.book_path
import kreader.composeapp.generated.resources.book_progress
import kreader.composeapp.generated.resources.book_read
import kreader.composeapp.generated.resources.book_read_count
import kreader.composeapp.generated.resources.book_size
import kreader.composeapp.generated.resources.cancel
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
fun BookInfoDialog(
    recent: Recent,
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
                modifier = Modifier.padding(vertical = 8.dp)
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