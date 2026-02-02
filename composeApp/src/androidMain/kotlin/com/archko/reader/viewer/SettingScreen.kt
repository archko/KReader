package com.archko.reader.viewer

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.viewmodel.BackupViewModel
import com.archko.reader.viewer.dialog.AISettingDialog
import com.archko.reader.viewer.dialog.ConvertToEpubDialog
import com.archko.reader.viewer.dialog.PdfCreateDialog
import com.archko.reader.viewer.dialog.PdfEncryptDialog
import com.archko.reader.viewer.dialog.PdfExportDialog
import com.archko.reader.viewer.dialog.PdfMergeDialog
import com.archko.reader.viewer.dialog.PdfSplitDialog
import com.archko.reader.viewer.dialog.WebdavConfigDialog
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingScreen(
    viewModel: BackupViewModel,
    modifier: Modifier = Modifier,
) {
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
                Spacer(modifier = Modifier.height(16.dp))
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

                Spacer(modifier = Modifier.height(8.dp))

                Features()

                Spacer(modifier = Modifier.height(16.dp))

                SettingCategory(viewModel)

                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}

@Composable
fun Features() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 8.dp, horizontal = 20.dp)
    ) {
        Text(
            text = stringResource(Res.string.features),
            style = TextStyle(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // 第一行
        Row(modifier = Modifier.fillMaxWidth()) {
            FeatureItem(
                text = stringResource(Res.string.auto_crop_edge),
                modifier = Modifier.weight(1f)
            )
            FeatureItem(
                text = stringResource(Res.string.multi_format_support),
                modifier = Modifier.weight(1f)
            )
        }

        // 第二行
        Row(modifier = Modifier.fillMaxWidth()) {
            FeatureItem(
                text = stringResource(Res.string.ocr_text_recognition),
                modifier = Modifier.weight(1f)
            )
            FeatureItem(
                text = stringResource(Res.string.image_to_pdf),
                modifier = Modifier.weight(1f)
            )
        }

        // 第三行
        Row(modifier = Modifier.fillMaxWidth()) {
            FeatureItem(
                text = stringResource(Res.string.mobi_azw3_to_epub),
                modifier = Modifier.weight(1f)
            )
            FeatureItem(
                text = stringResource(Res.string.pdf_encrypt_decrypt),
                modifier = Modifier.weight(1f)
            )
        }

        // 第四行
        Row(modifier = Modifier.fillMaxWidth()) {
            FeatureItem(
                text = stringResource(Res.string.webdav_backup),
                modifier = Modifier.weight(1f)
            )
            FeatureItem(
                text = stringResource(Res.string.tts_read_aloud),
                modifier = Modifier.weight(1f)
            )
        }

        // 第五行
        Row(modifier = Modifier.fillMaxWidth()) {
            FeatureItem(
                text = stringResource(Res.string.export_images),
                modifier = Modifier.weight(1f)
            )
            FeatureItem(
                text = stringResource(Res.string.split_merge_pdf),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun FeatureItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(36.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            ),
            maxLines = 1,
        )
    }
}

@Composable
fun SettingCategory(viewModel: BackupViewModel) {
    val context = LocalContext.current
    var showAISettingDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPdfCreateDialog by remember { mutableStateOf(false) }
    var showPdfExportDialog by remember { mutableStateOf(false) }
    var showPdfEncryptDialog by remember { mutableStateOf(false) }
    var showPdfSplitDialog by remember { mutableStateOf(false) }
    var showPdfMergeDialog by remember { mutableStateOf(false) }
    var showPdfConvertDialog by remember { mutableStateOf(false) }
    var showWebdavDialog by remember { mutableStateOf(false) }

    val version by remember {
        var packageInfo: PackageInfo? = null
        try {
            packageInfo =
                context.let { context.packageManager?.getPackageInfo(it.packageName, 0) }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        if (packageInfo != null) {
            mutableStateOf(packageInfo.versionName)
        } else {
            mutableStateOf("")
        }
    }

    Column {
        SettingItem(
            title = stringResource(Res.string.version),
            subtitle = version
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = stringResource(Res.string.ai_setting),
            onClick = { showAISettingDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

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
            title = stringResource(Res.string.convert_title),
            onClick = { showPdfConvertDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = stringResource(Res.string.webdav_title),
            onClick = { showWebdavDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingItem(
            title = stringResource(Res.string.about),
            onClick = { showAboutDialog = true }
        )
    }

    // AI设置 Dialog
    if (showAISettingDialog) {
        AISettingDialog(
            onDismiss = { showAISettingDialog = false },
            onSave = {}
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
