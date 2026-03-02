package com.archko.reader.viewer.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.AIPageConversation
import com.archko.reader.pdf.viewmodel.AIViewModel
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.io.File

/**
 * AI页面对话框
 * @author: archko 2026/3/2
 */
@Composable
fun AIPageDialog(
    currentPath: String,
    pageIndex: Int,
    decoder: ImageDecoder,
    aiViewModel: AIViewModel,
    onDismiss: () -> Unit,
    onShowToast: ((String) -> Unit)? = null
) {
    val conversations by aiViewModel.conversations.collectAsState()
    val isLoading by aiViewModel.isLoading.collectAsState()
    
    var pageText by remember { mutableStateOf<String?>(null) }
    var isLoadingText by remember { mutableStateOf(true) }
    var question by remember { mutableStateOf("") }
    
    // 加载页面文本
    LaunchedEffect(pageIndex) {
        isLoadingText = true
        try {
            val reflowBean = decoder.decodeReflowSinglePage(pageIndex)
            pageText = reflowBean?.data ?: "无法获取页面文本"
        } catch (e: Exception) {
            pageText = "无法获取页面文本: ${e.message}"
        } finally {
            isLoadingText = false
        }
        
        // 加载对话历史
        aiViewModel.loadConversations(currentPath, pageIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI 助手 - 第 ${pageIndex + 1} 页",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 页面文本区域 - 占一半高度
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (isLoadingText) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "页面内容",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = pageText ?: "无内容",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                // 对话历史区域 - 占一半高度
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "对话历史",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (conversations.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无对话记录",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(conversations) { conv ->
                                    ConversationItem(conv)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 输入区域
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = question,
                        onValueChange = { question = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("基于此内容提问...") },
                        enabled = !isLoading && !isLoadingText,
                        singleLine = false,
                        maxLines = 3
                    )
                    
                    Button(
                        onClick = {
                            if (question.isNotBlank() && pageText != null) {
                                // TODO: 调用AI接口
                                // 暂时显示提示
                                aiViewModel.setLoading(true)
                                // 这里需要检查AI配置并调用
                                // 如果没有配置，显示Toast
                                onShowToast?.invoke("AI功能尚未配置，请先在设置中配置AI服务")
                                question = ""
                                aiViewModel.setLoading(false)
                            }
                        },
                        enabled = !isLoading && !isLoadingText && question.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("发送")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(conversation: AIPageConversation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 问题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_ai),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = conversation.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 回答
            Text(
                text = conversation.answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp)
            )
        }
    }
}
