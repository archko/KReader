package com.archko.reader.viewer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(), // easier to see the PDF on dark theme,
        content = content
    )
}