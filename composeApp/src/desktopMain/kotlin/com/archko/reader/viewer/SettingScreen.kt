package com.archko.reader.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.viewmodel.AIViewModel
import com.archko.reader.pdf.viewmodel.BackupViewModel
import com.archko.reader.viewer.dialog.AISettingDialog
import com.archko.reader.viewer.dialog.AboutDialog
import com.archko.reader.viewer.dialog.ConvertToEpubDialog
import com.archko.reader.viewer.dialog.PdfCreateDialog
import com.archko.reader.viewer.dialog.PdfEncryptDialog
import com.archko.reader.viewer.dialog.PdfExportDialog
import com.archko.reader.viewer.dialog.PdfMergeDialog
import com.archko.reader.viewer.dialog.PdfSplitDialog
import com.archko.reader.viewer.dialog.TtsDialog
import com.archko.reader.viewer.dialog.WebdavConfigDialog
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingScreen(
    viewModel: BackupViewModel,
    aiViewModel: AIViewModel,
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
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
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "KReader",
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(Res.string.app_author),
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 15.sp
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Features()

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingCategory(viewModel, aiViewModel)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SettingCategory(viewModel: BackupViewModel, aiViewModel: AIViewModel) {
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPdfCreateDialog by remember { mutableStateOf(false) }
    var showPdfExportDialog by remember { mutableStateOf(false) }
    var showPdfEncryptDialog by remember { mutableStateOf(false) }
    var showPdfSplitDialog by remember { mutableStateOf(false) }
    var showPdfMergeDialog by remember { mutableStateOf(false) }
    var showPdfConvertDialog by remember { mutableStateOf(false) }
    var showWebdavDialog by remember { mutableStateOf(false) }
    var showTtsDialog by remember { mutableStateOf(false) }
    var showAISettingDialog by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(8.dp))

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SettingItem(
                title = stringResource(Res.string.create_pdf),
                onClick = { showPdfCreateDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_create),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }

        item {
            SettingItem(
                title = stringResource(Res.string.export_pdf),
                onClick = { showPdfExportDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_export),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }

        item {
            SettingItem(
                title = stringResource(Res.string.encrypt_decrypt_title),
                onClick = { showPdfEncryptDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_encrypt_decrypt),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }

        item {
            SettingItem(
                title = stringResource(Res.string.split_title),
                onClick = { showPdfSplitDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_split),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }

        item {
            SettingItem(
                title = stringResource(Res.string.merge_title),
                onClick = { showPdfMergeDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_merge),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }

        item {
            SettingItem(
                title = stringResource(Res.string.convert_title),
                onClick = { showPdfConvertDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_convert),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }

        item {
            SettingItem(
                title = stringResource(Res.string.webdav_title),
                onClick = { showWebdavDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_cloud),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }

        item {
            SettingItem(
                title = stringResource(Res.string.tts_setting_title),
                onClick = { showTtsDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_tts),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }

        item {
            SettingItem(
                title = stringResource(Res.string.ai_setting),
                onClick = { showAISettingDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_android),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }

        item {
            SettingItem(
                title = stringResource(Res.string.about),
                onClick = { showAboutDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.ic_information),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            )
        }
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
        PdfSplitDialog(
            onDismiss = { showPdfSplitDialog = false }
        )
    }

    // PDF合并 Dialog
    if (showPdfMergeDialog) {
        PdfMergeDialog(
            onDismiss = { showPdfMergeDialog = false }
        )
    }

    // convert epub Dialog
    if (showPdfConvertDialog) {
        ConvertToEpubDialog(
            onDismiss = { showPdfConvertDialog = false }
        )
    }

    // Webdav Dialog
    if (showWebdavDialog) {
        WebdavConfigDialog(
            viewModel = viewModel,
            onDismiss = { showWebdavDialog = false }
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

    // AI 设置Dialog
    if (showAISettingDialog) {
        AISettingDialog(
            viewModel = aiViewModel,
            onDismiss = { showAISettingDialog = false },
            onSave = { }
        )
    }
}