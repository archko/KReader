package com.archko.reader.pdf.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.archko.reader.pdf.cache.ImageCache

/**
 * @author: archko 2026/2/3 :08:34
 */
/**
 * 文本操作工具栏
 */
@Composable
public fun TextActionToolbar(
    selectedPage: Page?,
    textSelector: TextSelector?, // 添加textSelector参数
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val selection = selectedPage?.currentSelection
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    // 检查选中文本是否为空
    val selectedText = selection?.text ?: ""
    println("TextActionToolbar:$selectedText")
    var resultText by remember { mutableStateOf(selectedText) }
    var isOcring by remember { mutableStateOf(false) }

    // 如果选中文本是空的，尝试从缓存获取图片并进行OCR识别
    /*if (selectedText.isEmpty() && selection != null && textSelector != null) {
        // 使用selection.quads获取坐标区域
        selection.quads.forEach { quad ->
            // 计算quad的边界框
            val left = minOf(quad.ul_x, quad.ll_x, quad.ur_x, quad.lr_x)
            val top = minOf(quad.ul_y, quad.ll_y, quad.ur_y, quad.lr_y)
            val right = maxOf(quad.ul_x, quad.ll_x, quad.ur_x, quad.lr_x)
            val bottom = maxOf(quad.ul_y, quad.ll_y, quad.ur_y, quad.lr_y)

            // 转换为屏幕坐标
            val screenRect = Rect(left, top, right, bottom)
        }
        val cacheKey = selectedPage.getThumbnailCacheKey()
        if (cacheKey != null) {
            val cachedState = ImageCache.acquirePage(cacheKey)
            if (cachedState != null) {
                isOcring = true
                // 使用缓存的缩略图进行OCR识别
                val thumbnailBitmap = cachedState.bitmap
                val extractedText = textSelector.extractTextFromImage(thumbnailBitmap)

                LaunchedEffect(extractedText) {
                    isOcring = false
                    resultText = extractedText
                    if (extractedText.isNotEmpty()) {
                        //onCopy(extractedText)
                    }
                }
            }
        }
    }*/

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 80.dp, horizontal = 40.dp),
        color = Color.Black.copy(alpha = 0.8f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Selected Text",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        text = if (resultText.isEmpty() && isOcring) {
                            "Processing image..."
                        } else {
                            resultText
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(resultText))
                        onCopy(resultText)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Copy")
                }

                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
