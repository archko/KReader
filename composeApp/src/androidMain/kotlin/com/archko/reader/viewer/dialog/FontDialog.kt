package com.archko.reader.viewer.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.back
import kreader.composeapp.generated.resources.font_no
import kreader.composeapp.generated.resources.font_scaning
import kreader.composeapp.generated.resources.font_select
import kreader.composeapp.generated.resources.font_storage
import kreader.composeapp.generated.resources.ic_back
import kreader.composeapp.generated.resources.ic_font
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

/**
 * 字体选择对话框
 * @author: archko 2025/11/4 :15:04
 */
@Composable
fun FontDialog(
    onDismiss: () -> Unit,
    onFontSelected: (String) -> Unit
) {
    var fontFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 扫描字体文件
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val fontsDir = File("/sdcard/fonts")
            val files = if (fontsDir.exists() && fontsDir.isDirectory) {
                fontsDir.listFiles { file ->
                    file.extension.equals("ttf", ignoreCase = true) ||
                            file.extension.equals("otf", ignoreCase = true)
                }?.toList() ?: emptyList()
            } else {
                emptyList()
            }
            fontFiles = files
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.wrapContentSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back),
                            contentDescription = stringResource(Res.string.back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.font_select),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.wrapContentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(Res.string.font_scaning),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (fontFiles.isEmpty()) {
                    Box(
                        modifier = Modifier.wrapContentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(Res.string.font_no),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(Res.string.font_storage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .wrapContentSize()
                            .heightIn(max = 800.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(fontFiles.size) { index ->
                            val fontFile = fontFiles[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onFontSelected(fontFile.absolutePath)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.5f
                                    )
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_font),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = fontFile.nameWithoutExtension,
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontFamily = FontFamily(
                                                Font(
                                                    fontFile,
                                                    FontWeight.Normal,
                                                    FontStyle.Normal
                                                )
                                            )
                                        )
                                        Text(
                                            text = "${fontFile.extension.uppercase()} • ${
                                                String.format(
                                                    "%.1f KB",
                                                    fontFile.length() / 1024.0
                                                )
                                            }",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = FontFamily(
                                                Font(
                                                    fontFile,
                                                    FontWeight.Normal,
                                                    FontStyle.Normal
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}