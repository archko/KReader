package com.archko.reader.viewer.component

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun CropModeMenuItem(
    mode: Int,
    currentMode: Int,
    iconRes: DrawableResource,
    label: String,
    onClick: () -> Unit,
) {
    val selected = mode == currentMode
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    DropdownMenuItem(
        text = { Text(text = label, color = color) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                tint = color
            )
        },
        trailingIcon = if (selected) {
            { Text(text = "✓", color = color) }
        } else {
            null
        },
        onClick = onClick
    )
}
