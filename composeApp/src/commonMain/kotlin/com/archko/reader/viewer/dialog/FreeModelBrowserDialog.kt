package com.archko.reader.viewer.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.ai_api_key_label
import kreader.composeapp.generated.resources.ai_free_confirm_selection
import kreader.composeapp.generated.resources.ai_free_fetch_failed
import kreader.composeapp.generated.resources.ai_free_select_model_title
import kreader.composeapp.generated.resources.ai_free_total_models
import kreader.composeapp.generated.resources.cancel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
fun FreeModelBrowserDialog(
    baseUrl: String,
    currentModel: String,
    onDismiss: () -> Unit,
    onModelSelected: (apiKey: String, model: String) -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedModel by remember { mutableStateOf(currentModel) }
    var hasFetched by remember { mutableStateOf(false) }

    LaunchedEffect(apiKey, baseUrl) {
        if (apiKey.isBlank()) return@LaunchedEffect
        isLoading = true
        errorMsg = null
        hasFetched = true
        try {
            models = withContext(Dispatchers.Default) {
                val openAI = OpenAI(
                    token = apiKey,
                    host = OpenAIHost(baseUrl)
                )
                openAI.models()
                    .map { it.id.id }
                    .filter { it.contains("free", ignoreCase = true) }
                    .sorted()
            }
        } catch (e: Exception) {
            println("OpenAI:$e.message")
            errorMsg = getString(Res.string.ai_free_fetch_failed).format(e.message ?: "")
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.ai_free_select_model_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(Res.string.ai_api_key_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (apiKey.isBlank() && !hasFetched) {
                    Text(
                        text = "请输入 API Key 以浏览模型",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (models.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.ai_free_total_models).format(models.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(models) { model ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedModel = model },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (model == selectedModel) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = model == selectedModel,
                                        onClick = { selectedModel = model }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = model,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onModelSelected(apiKey, selectedModel) },
                enabled = selectedModel.isNotEmpty() && !isLoading && errorMsg == null && models.isNotEmpty()
            ) {
                Text(stringResource(Res.string.ai_free_confirm_selection))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
