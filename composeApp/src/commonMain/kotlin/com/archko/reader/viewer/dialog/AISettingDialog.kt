package com.archko.reader.viewer.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.entity.AIProvider
import com.archko.reader.pdf.viewmodel.AIViewModel
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.ai_api_key_label
import kreader.composeapp.generated.resources.ai_api_url_label
import kreader.composeapp.generated.resources.ai_edit_provider
import kreader.composeapp.generated.resources.ai_model_name_label
import kreader.composeapp.generated.resources.ai_setting_title
import kreader.composeapp.generated.resources.cancel
import kreader.composeapp.generated.resources.ic_edit
import kreader.composeapp.generated.resources.save
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AISettingDialog(
    viewModel: AIViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val providers by viewModel.providers.collectAsState()
    val defaultProvider by viewModel.defaultProvider.collectAsState()
    
    var showEditDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<AIProvider?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initializeDefaultProviders()
    }

    if (showEditDialog && editingProvider != null) {
        AIProviderEditDialog(
            provider = editingProvider!!,
            onDismiss = { showEditDialog = false },
            onSave = { updatedProvider ->
                viewModel.updateProvider(updatedProvider)
                showEditDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.ai_setting_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(providers) { provider ->
                    AIProviderItem(
                        provider = provider,
                        isDefault = provider.id == defaultProvider?.id,
                        onSetDefault = {
                            viewModel.setDefaultProvider(provider.id)
                        },
                        onEdit = {
                            editingProvider = provider
                            showEditDialog = true
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave()
                onDismiss()
            }) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}


@Composable
private fun AIProviderItem(
    provider: AIProvider,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDefault) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetDefault() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 默认选择
            RadioButton(
                selected = isDefault,
                onClick = onSetDefault
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 提供商信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = provider.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (provider.apiKey.isNotEmpty()) {
                    Text(
                        text = "API Key: ${provider.apiKey.take(8)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 编辑按钮
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(Res.drawable.ic_edit),
                    contentDescription = "Edit"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIProviderEditDialog(
    provider: AIProvider,
    onDismiss: () -> Unit,
    onSave: (AIProvider) -> Unit
) {
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var model by remember { mutableStateOf(provider.model) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.ai_edit_provider, provider.name)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(Res.string.ai_api_key_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(Res.string.ai_api_url_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(Res.string.ai_model_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = AIProvider(
                        id = provider.id,
                        name = provider.name,
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                        maxTokens = provider.maxTokens,
                        temperature = provider.temperature,
                        enabled = provider.enabled,
                        isDefault = provider.isDefault
                    ).apply {
                        this.id = provider.id
                        this.createdAt = provider.createdAt
                    }
                    onSave(updated)
                }
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}