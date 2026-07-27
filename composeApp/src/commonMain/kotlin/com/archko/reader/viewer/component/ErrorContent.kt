package com.archko.reader.viewer.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.close
import kreader.composeapp.generated.resources.document_open_failed
import kreader.composeapp.generated.resources.document_open_permission
import kreader.composeapp.generated.resources.loading
import kreader.composeapp.generated.resources.support_format
import org.jetbrains.compose.resources.stringResource

/**
 * @author: archko 2026/2/3 :08:12
 */

/**
 * 错误和加载状态内容组件
 */
@Composable
fun ErrorContent(
    loadingError: String?,
    currentPath: String,
    onCloseDocument: (() -> Unit)?
) {
    when {
        loadingError != null -> {
            // 显示错误信息
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = when (loadingError) {
                        "document_open_failed" -> stringResource(Res.string.document_open_failed)
                        else -> stringResource(Res.string.document_open_failed)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.document_open_permission),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.support_format),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onCloseDocument?.invoke() }
                ) {
                    Text(stringResource(Res.string.close))
                }
            }
        }

        else -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.loading),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}