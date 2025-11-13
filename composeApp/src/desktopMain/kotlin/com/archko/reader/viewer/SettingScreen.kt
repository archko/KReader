package com.archko.reader.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.archko.reader.viewer.dialog.PdfCreateDialog
import com.archko.reader.viewer.dialog.PdfEncryptDialog
import com.archko.reader.viewer.dialog.PdfExportDialog
import com.archko.reader.viewer.dialog.PdfMergeDialog
import com.archko.reader.viewer.dialog.PdfSplitDialog
import com.archko.reader.viewer.dialog.TtsDialog
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.about
import kreader.composeapp.generated.resources.about_content
import kreader.composeapp.generated.resources.about_kreader
import kreader.composeapp.generated.resources.app_author
import kreader.composeapp.generated.resources.create_pdf
import kreader.composeapp.generated.resources.encrypt_decrypt_title
import kreader.composeapp.generated.resources.export_pdf
import kreader.composeapp.generated.resources.ic_back
import kreader.composeapp.generated.resources.merge_title
import kreader.composeapp.generated.resources.split_title
import kreader.composeapp.generated.resources.support_format
import kreader.composeapp.generated.resources.tts_setting_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Theme {
            Surface(
                modifier = modifier
                    .fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_back),
                                    contentDescription = "返回",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.about_kreader),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Logo()
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "KReader",
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(Res.string.app_author),
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        SettingCategory()
                    }
                }
            }
        }
    }
}

@Composable
fun SettingCategory() {
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPdfCreateDialog by remember { mutableStateOf(false) }
    var showPdfExportDialog by remember { mutableStateOf(false) }
    var showPdfEncryptDialog by remember { mutableStateOf(false) }
    var showPdfSplitDialog by remember { mutableStateOf(false) }
    var showPdfMergeDialog by remember { mutableStateOf(false) }
    var showTtsDialog by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(8.dp))

    Column {
        SettingItem(
            title = stringResource(Res.string.create_pdf),
            onClick = { showPdfCreateDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = stringResource(Res.string.export_pdf),
            onClick = { showPdfExportDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = stringResource(Res.string.encrypt_decrypt_title),
            onClick = { showPdfEncryptDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = stringResource(Res.string.split_title),
            onClick = { showPdfSplitDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = stringResource(Res.string.merge_title),
            onClick = { showPdfMergeDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = stringResource(Res.string.tts_setting_title),
            onClick = { showTtsDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = stringResource(Res.string.about),
            onClick = { showAboutDialog = true }
        )
    }

    // PDF创建 Dialog
    if (showPdfCreateDialog) {
        PdfCreateDialog(
            onDismiss = { showPdfCreateDialog = false }
        )
    }

    // PDF导出 Dialog
    if (showPdfExportDialog) {
        PdfExportDialog(
            onDismiss = { showPdfExportDialog = false }
        )
    }

    // PDF加密/解密 Dialog
    if (showPdfEncryptDialog) {
        PdfEncryptDialog(
            onDismiss = { showPdfEncryptDialog = false }
        )
    }

    // PDF拆分 Dialog
    if (showPdfSplitDialog) {
        PdfSplitDialog (
            onDismiss = { showPdfSplitDialog = false }
        )
    }

    // PDF合并 Dialog
    if (showPdfMergeDialog) {
        PdfMergeDialog (
            onDismiss = { showPdfMergeDialog = false }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }

    // tts 设置Dialog
    if (showTtsDialog) {
        TtsDialog(
            onDismiss = { showTtsDialog = false }
        )
    }
}

@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
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
                        .wrapContentHeight()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_back),
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.about_kreader),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.support_format),
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 20.sp
                        ),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(Res.string.about_content),
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 20.sp
                        ),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}