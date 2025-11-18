package com.archko.reader.viewer.dialog

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.archko.reader.pdf.entity.DavResourceItem
import com.archko.reader.pdf.viewmodel.BackupViewModel
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * @author: archko 2025/11/17 :15:17
 */
@Composable
fun WebdavConfigDialog(
    viewModel: BackupViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val toaster = rememberToasterState()

    // 视图状态：true = 显示列表，false = 显示配置表单
    var showListView by remember { mutableStateOf(viewModel.checkAndLoadUser()) }

    var username by remember { mutableStateOf(viewModel.webdavUser?.name ?: "") }
    var password by remember { mutableStateOf(viewModel.webdavUser?.pass ?: "") }
    var host by remember {
        mutableStateOf(
            viewModel.webdavUser?.host ?: ""
        )
    }
    var path by remember { mutableStateOf(viewModel.webdavUser?.path ?: "") }
    var isConfiguring by remember { mutableStateOf(false) }

    val rootPath = remember(viewModel.webdavUser) { viewModel.webdavUser?.path ?: "" }

    var currentPath by remember { mutableStateOf(rootPath) }

    val davResources by viewModel.uiDavResourceModel.collectAsState()

    LaunchedEffect(showListView) {
        if (showListView && viewModel.webdavUser != null) {
            currentPath = viewModel.webdavUser!!.path
        }
    }

    LaunchedEffect(showListView, currentPath) {
        if (showListView && viewModel.webdavUser != null) {
            scope.launch {
                viewModel.loadFileList(currentPath)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 800.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
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
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.webdav_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = {
                        if (showListView) {
                            showListView = false
                        } else {
                            isConfiguring = true
                            scope.launch {
                                viewModel.saveWebdavUser(username, password, host, path)
                                    .flowOn(Dispatchers.IO)
                                    .collectLatest { success ->
                                        isConfiguring = false
                                        if (success) {
                                            showListView = true
                                            toaster.show(
                                                message = getString(Res.string.webdav_config_success),
                                                type = ToastType.Success,
                                            )
                                        } else {
                                            toaster.show(
                                                message = getString(Res.string.webdav_config_failed),
                                                type = ToastType.Success,
                                            )
                                        }
                                    }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    enabled = if (showListView) {
                        true
                    } else {
                        username.isNotBlank()
                                && password.isNotBlank()
                                && host.isNotBlank()
                                && !isConfiguring
                    }
                ) {
                    if (isConfiguring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.webdav_config_doing))
                    } else {
                        Text(
                            if (showListView) stringResource(Res.string.webdav_config_btn_do)
                            else stringResource(Res.string.webdav_config_btn_save)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (showListView) {
                    val canNavigateUp = currentPath.length > rootPath.length &&
                            currentPath.startsWith(rootPath) &&
                            currentPath != rootPath

                    FileListView(
                        currentPath = currentPath,
                        davResourceItems = davResources,
                        onNavigateUp = if (canNavigateUp) {
                            {
                                val parentPath = currentPath.substringBeforeLast('/', "")
                                if (parentPath.length >= rootPath.length && parentPath.isNotEmpty()) {
                                    currentPath = parentPath
                                }
                            }
                        } else null,
                        onDirectoryClick = { item ->
                            currentPath = item.resource.location.encodedPath.trimEnd('/')
                        },
                        onFileClick = { item ->
                            scope.launch {
                                val filePath = item.resource.location.encodedPath.trimEnd('/')
                                val fileName = filePath.substringAfterLast('/')
                                viewModel.restoreFromWebdav(filePath)
                                    .flowOn(Dispatchers.IO)
                                    .collectLatest { success ->
                                        if (success) {
                                            println("Restore successful: $fileName")
                                            toaster.show(
                                                message = getString(Res.string.webdav_upload_success),
                                                type = ToastType.Success,
                                            )
                                        } else {
                                            println("Restore failed: $fileName")
                                            toaster.show(
                                                message = getString(Res.string.webdav_upload_failed),
                                                type = ToastType.Error,
                                            )
                                        }
                                    }
                            }
                        }
                    )
                } else {
                    ConfigFormView(
                        username = username,
                        password = password,
                        host = host,
                        path = path,
                        isConfiguring = isConfiguring,
                        onUsernameChange = { username = it },
                        onPasswordChange = { password = it },
                        onHostChange = { host = it },
                        onPathChange = { path = it }
                    )
                }
            }
        }
    }

    Toaster(
        state = toaster,
        maxVisibleToasts = 1,
        alignment = Alignment.Center,
    )
}

@Composable
private fun FileListView(
    currentPath: String,
    davResourceItems: List<DavResourceItem>?,
    onNavigateUp: (() -> Unit)?,
    onDirectoryClick: (DavResourceItem) -> Unit,
    onFileClick: (DavResourceItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onNavigateUp?.invoke() },
                enabled = onNavigateUp != null
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_back),
                    contentDescription = "返回上级",
                    tint = if (onNavigateUp != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
            Text(
                text = currentPath.ifEmpty { "/" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (davResourceItems == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (davResourceItems.isEmpty()) {
                Text(
                    text = stringResource(Res.string.webdav_config_no_files),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(davResourceItems) { item ->
                        FileListItem(
                            item = item,
                            onDirectoryClick = onDirectoryClick,
                            onFileClick = onFileClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileListItem(
    item: DavResourceItem,
    onDirectoryClick: (DavResourceItem) -> Unit,
    onFileClick: (DavResourceItem) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val displayName = item.resource.location.encodedPath
        .trimEnd('/')
        .substringAfterLast('/')

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = {
                    if (item.isDirectory) {
                        onDirectoryClick(item)
                    } else {
                        showMenu = true
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (item.isDirectory) "📁" else "📄",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.resource.location.encodedPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.webdav_restore)) },
                onClick = {
                    showMenu = false
                    onFileClick(item)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.webdav_upload)) },
                onClick = {
                    showMenu = false
                    // TODO: 上传备份功能
                }
            )
        }
    }
}

@Composable
private fun ConfigFormView(
    username: String,
    password: String,
    host: String,
    path: String,
    isConfiguring: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPathChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(Res.string.webdav_config_name)) },
            placeholder = { Text(stringResource(Res.string.webdav_config_input_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConfiguring
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(Res.string.webdav_config_pass)) },
            placeholder = { Text(stringResource(Res.string.webdav_config_input_pass)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !isConfiguring
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text(stringResource(Res.string.webdav_config_host)) },
            placeholder = { Text("eg: https://example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !isConfiguring
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = path,
            onValueChange = onPathChange,
            label = { Text(stringResource(Res.string.webdav_config_path)) },
            placeholder = { Text(stringResource(Res.string.webdav_config_path_eg)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConfiguring
        )
    }
}