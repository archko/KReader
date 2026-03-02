package com.archko.reader.viewer.dialog

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.archko.reader.pdf.decoder.internal.ImageDecoder
import com.archko.reader.pdf.entity.AIPageConversation
import com.archko.reader.pdf.viewmodel.AIViewModel
import kreader.composeapp.generated.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
    
    // 在 Composable 上下文中获取字符串资源
    val unableToGetTextMsg = stringResource(Res.string.unable_to_get_page_text)
    val unableToGetTextErrorMsg = stringResource(Res.string.unable_to_get_page_text_error, "")
    
    // 加载页面文本
    LaunchedEffect(pageIndex) {
        isLoadingText = true
        try {
            val reflowBean = decoder.decodeReflowSinglePage(pageIndex)
            pageText = reflowBean?.data ?: unableToGetTextMsg
        } catch (e: Exception) {
            pageText = unableToGetTextErrorMsg.replace("%s", e.message ?: "")
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
                        text = stringResource(Res.string.ai_assistant_page, pageIndex + 1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_close),
                            contentDescription = stringResource(Res.string.close),
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
                                text = stringResource(Res.string.page_content),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = pageText ?: stringResource(Res.string.no_content),
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
                            text = stringResource(Res.string.conversation_history),
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
                                    text = stringResource(Res.string.no_conversation_records),
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
                        placeholder = { Text(stringResource(Res.string.ask_based_on_content)) },
                        enabled = !isLoading && !isLoadingText,
                        singleLine = false,
                        maxLines = 3
                    )
                    
                    Button(
                        onClick = {
                            if (question.isNotBlank() && pageText != null) {
                                val currentQuestion = question
                                question = ""
                                
                                aiViewModel.askQuestion(
                                    documentPath = currentPath,
                                    documentName = currentPath.substringAfterLast('/'),
                                    pageIndex = pageIndex,
                                    question = currentQuestion,
                                    pageContent = pageText ?: "",
                                    onSuccess = { answer ->
                                        // 对话已保存，会自动刷新列表
                                    },
                                    onError = { error ->
                                        onShowToast?.invoke(error)
                                    }
                                )
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
                            Text(stringResource(Res.string.send))
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
