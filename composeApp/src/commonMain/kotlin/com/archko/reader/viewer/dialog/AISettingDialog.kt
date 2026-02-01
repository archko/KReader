package com.archko.reader.viewer.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archko.reader.viewer.utils.Utils
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import com.archko.reader.generated.Res
import com.archko.reader.generated.Res.string

@Serializable
data class AIModel(
    val modelName: String,
    val token: String
)

@Composable
fun AISettingDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit
) {
    var models by remember { mutableStateOf<List<AIModel>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var selectedModelIndex by remember { mutableIntStateOf(-1) }
    var tokenInput by remember { mutableStateOf("") }
    
    // Load models from Utils
    LaunchedEffect(Unit) {
        loadModelsFromUtils()?.let { loadedModels ->
            models = loadedModels
            if (loadedModels.isNotEmpty() && selectedModelIndex == -1) {
                selectedModelIndex = 0
                tokenInput = loadedModels[0].token
            }
        }
    }
    
    // Update token when selected model changes
    LaunchedEffect(selectedModelIndex) {
        if (selectedModelIndex >= 0 && selectedModelIndex < models.size) {
            tokenInput = models[selectedModelIndex].token
        } else {
            tokenInput = ""
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(Res.string.ai_setting)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Model selection dropdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.select_model),
                        modifier = Modifier.weight(1f)
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = if (selectedModelIndex >= 0 && selectedModelIndex < models.size) {
                                models[selectedModelIndex].modelName
                            } else {
                                ""
                            },
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(0.6f),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            models.forEachIndexed { index, model ->
                                DropdownMenuItem(
                                    text = { Text(model.modelName) },
                                    onClick = {
                                        selectedModelIndex = index
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Token input field
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text(stringResource(Res.string.input_token)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Save the updated model with its token
                    if (selectedModelIndex >= 0 && selectedModelIndex < models.size) {
                        val updatedModels = models.toMutableList()
                        updatedModels[selectedModelIndex] = updatedModels[selectedModelIndex].copy(token = tokenInput)
                        saveModelsToUtils(updatedModels)
                        onSave()
                        onDismissRequest()
                    } else {
                        // If no model is selected, don't save
                        onDismissRequest()
                    }
                }
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}

private fun loadModelsFromUtils(): List<AIModel>? {
    val jsonStr = Utils.getString("ai_models", "")
    return if (jsonStr.isNotEmpty()) {
        try {
            Json.decodeFromString<List<AIModel>>(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            listOf()
        }
    } else {
        listOf()
    }
}

private fun saveModelsToUtils(models: List<AIModel>) {
    val jsonStr = Json.encodeToString(models)
    Utils.saveString("ai_models", jsonStr)
}