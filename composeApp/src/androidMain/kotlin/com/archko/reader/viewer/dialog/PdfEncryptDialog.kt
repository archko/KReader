package com.archko.reader.viewer.dialog

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.PdfApp
import com.archko.reader.pdf.util.IntentFile
import com.archko.reader.viewer.utils.PDFCreaterHelper
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PdfEncryptDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var pdfFilePath by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                        text = stringResource(Res.string.encrypt_decrypt_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    val pickerLauncher = rememberFilePickerLauncher(
                        type = FilePickerFileType.Pdf,
                        selectionMode = FilePickerSelectionMode.Single
                    ) { files ->
                        scope.launch {
                            if (files.isNotEmpty()) {
                                val file = files.first()
                                val path = IntentFile.getPath(PdfApp.app!!, file.uri)
                                    ?: file.uri.toString()
                                pdfFilePath = path
                            }
                        }
                    }

                    Button(
                        onClick = { pickerLauncher.launch() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.encrypt_decrypt_add))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        if (pdfFilePath.isNotEmpty()) {
                            val fileObj = File(pdfFilePath)
                            val fileName = fileObj.name
                            val fileSize = if (fileObj.exists()) {
                                val sizeInBytes = fileObj.length()
                                val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
                                String.format("%.1fMB", sizeInMB)
                            } else {
                                "0MB"
                            }
                            val lastModified = SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.getDefault()
                            )
                                .format(Date(fileObj.lastModified()))

                            Text(
                                text = stringResource(Res.string.encrypt_decrypt_file_name).format(
                                    fileName
                                ) + "\n" +
                                        stringResource(Res.string.encrypt_decrypt_file_size).format(
                                            fileSize
                                        ) + "\n" +
                                        stringResource(Res.string.encrypt_decrypt_file_path).format(
                                            pdfFilePath
                                        ) + "\n" +
                                        stringResource(Res.string.encrypt_decrypt_modification_time).format(
                                            lastModified
                                        ),
                                modifier = Modifier.padding(all = 8.dp),
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(Res.string.encrypt_decrypt_encrypt)) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(Res.string.encrypt_decrypt_decrypt)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val passwordLabel = stringResource(Res.string.encrypt_decrypt_input_pwd)

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(passwordLabel) },
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible }
                            ) {
                                Icon(
                                    painter = if (isPasswordVisible) {
                                        painterResource(Res.drawable.ic_visibility_off)
                                    } else {
                                        painterResource(Res.drawable.ic_visibility)
                                    },
                                    contentDescription = if (isPasswordVisible) "隐藏密码" else "显示密码",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        if (selectedTab == 0) {
                            Button(
                                onClick = {
                                    if (pdfFilePath.isNotEmpty() && (password.isNotEmpty())) {
                                        isLoading = true
                                        scope.launch {
                                            try {
                                                val outputPath =
                                                    pdfFilePath.replace(".pdf", "_encrypted.pdf")
                                                val success = PDFCreaterHelper.encryptPDF(
                                                    pdfFilePath,
                                                    outputPath,
                                                    password,
                                                    password
                                                )

                                                if (success) {
                                                    Toast.makeText(
                                                        context,
                                                        getString(Res.string.encrypt_decrypt_encrypt_success)
                                                            .format(
                                                                outputPath
                                                            ),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        getString(Res.string.encrypt_decrypt_encrypt_failed),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    getString(Res.string.encrypt_decrypt_encrypt_failed),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            Toast.makeText(
                                                context,
                                                getString(Res.string.encrypt_decrypt_input_pwd),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = pdfFilePath.isNotEmpty() && password.isNotEmpty()
                            ) {
                                Text(stringResource(Res.string.encrypt_decrypt_encrypt))
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (pdfFilePath.isNotEmpty() && password.isNotEmpty()) {
                                        isLoading = true
                                        scope.launch {
                                            try {
                                                val outputPath =
                                                    pdfFilePath.replace(".pdf", "_decrypted.pdf")
                                                val success = PDFCreaterHelper.decryptPDF(
                                                    pdfFilePath,
                                                    outputPath,
                                                    password
                                                )

                                                if (success) {
                                                    Toast.makeText(
                                                        context,
                                                        getString(Res.string.encrypt_decrypt_decrypt_success)
                                                            .format(
                                                                outputPath
                                                            ),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        getString(Res.string.encrypt_decrypt_decrypt_failed),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    getString(Res.string.encrypt_decrypt_decrypt_failed),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            Toast.makeText(
                                                context,
                                                getString(Res.string.encrypt_decrypt_input_pwd),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = pdfFilePath.isNotEmpty() && password.isNotEmpty()
                            ) {
                                Text(stringResource(Res.string.encrypt_decrypt_decrypt))
                            }
                        }
                    }
                }
            }
        }
    }
}