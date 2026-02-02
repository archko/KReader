package com.archko.reader.viewer.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.archko.reader.viewer.utils.Utils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kreader.composeapp.generated.resources.Res
import kreader.composeapp.generated.resources.ai_setting
import kreader.composeapp.generated.resources.cancel
import kreader.composeapp.generated.resources.input_token
import kreader.composeapp.generated.resources.save
import kreader.composeapp.generated.resources.select_model
import org.jetbrains.compose.resources.stringResource

@Serializable
data class AIModel(
    val modelName: String,
    val token: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingDialog(
    onDismiss: () -> Unit,
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
        tokenInput = if (selectedModelIndex >= 0 && selectedModelIndex < models.size) {
            models[selectedModelIndex].token
        } else {
            ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            modifier = Modifier.menuAnchor(
                                ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                false
                            ).fillMaxWidth(0.6f),
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
                        updatedModels[selectedModelIndex] =
                            updatedModels[selectedModelIndex].copy(token = tokenInput)
                        saveModelsToUtils(updatedModels)
                        onSave()
                        onDismiss()
                    } else {
                        // If no model is selected, don't save
                        onDismiss()
                    }
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