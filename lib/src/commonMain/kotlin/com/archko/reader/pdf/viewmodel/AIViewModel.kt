package com.archko.reader.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.cache.AppDatabase
import com.archko.reader.pdf.entity.AIPageConversation
import com.archko.reader.pdf.entity.AIProvider
import com.archko.reader.pdf.service.AIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public class AIViewModel : ViewModel() {

    public var database: AppDatabase? = null

    public var aiService: AIService = AIService()

    private val _providers = MutableStateFlow<List<AIProvider>>(emptyList())
    public val providers: StateFlow<List<AIProvider>> = _providers

    private val _defaultProvider = MutableStateFlow<AIProvider?>(null)
    public val defaultProvider: StateFlow<AIProvider?> = _defaultProvider

    private val _conversations = MutableStateFlow<List<AIPageConversation>>(emptyList())
    public val conversations: StateFlow<List<AIPageConversation>> = _conversations

    private val _isLoading = MutableStateFlow(false)
    public val isLoading: StateFlow<Boolean> = _isLoading

    init {
        initializeDefaultProviders()
        initializeAIService()
    }

    public fun getAIPromptConfig(): AIService.AIPromptConfig {
        return AIService.AIPromptConfig(
            systemPrompt = "你是一个专业的文档阅读助手。用户会提供文档页面的内容，并基于这些内容提问。请根据页面内容准确回答问题。",
            userPromptFormat = "页面内容：\n%s\n\n问题：%s",
            unsupportedProvider = "不支持的 AI 提供商: %s",
            apiRequestFailed = "API 请求失败: %d %s",
            apiEmptyResponse = "API 返回空响应体",
            emptyResponse = "AI 返回空响应",
            apiCallFailed = "API 调用失败: %s"
        )
    }

    private fun initializeAIService() {
        try {
            val config = getAIPromptConfig()
            aiService.setPromptConfig(config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    public fun initializeDefaultProviders() {
        viewModelScope.launch {
            val existing = database?.aiProviderDao()?.getAllProviders() ?: emptyList()
            val existingIds = existing.map { it.id }.toSet()

            val allDefaults = listOf(
                AIProvider(
                    id = "deepseek",
                    name = "DeepSeek",
                    apiKey = "",
                    baseUrl = "https://api.deepseek.com",
                    model = "deepseek-v4-flash",
                    maxTokens = 100000,
                    temperature = 0.7f,
                    isDefault = false
                ),
                AIProvider(
                    id = "qwen",
                    name = "通义千问",
                    apiKey = "",
                    baseUrl = "https://dashscope.aliyuncs.com",
                    model = "qwen-turbo",
                    maxTokens = 100000,
                    temperature = 0.7f,
                    isDefault = false
                ),
                AIProvider(
                    id = "glm",
                    name = "智谱清言",
                    apiKey = "",
                    baseUrl = "https://open.bigmodel.cn",
                    model = "glm-4-flash",
                    maxTokens = 100000,
                    temperature = 0.7f,
                    isDefault = false
                ),
                AIProvider(
                    id = "openai",
                    name = "OpenAI GPT",
                    apiKey = "",
                    baseUrl = "https://api.openai.com",
                    model = "gpt-4o-mini",
                    maxTokens = 100000,
                    temperature = 0.7f,
                    isDefault = false
                ),
                AIProvider(
                    id = "gemini",
                    name = "Google Gemini",
                    apiKey = "",
                    baseUrl = "https://generativelanguage.googleapis.com",
                    model = "gemini-2.0-flash",
                    maxTokens = 100000,
                    temperature = 0.7f,
                    isDefault = false
                )
            )

            val toInsert = allDefaults.filter { it.id !in existingIds }
            if (toInsert.isNotEmpty()) {
                database?.aiProviderDao()?.insertAllProviders(toInsert)
            }

            existing.forEach { provider ->
                val defaultProvider = allDefaults.find { it.id == provider.id }
                if (defaultProvider != null) {
                    val updated = provider.apply {
                        name = defaultProvider.name
                        baseUrl = defaultProvider.baseUrl
                        model = defaultProvider.model
                        maxTokens = defaultProvider.maxTokens
                        temperature = defaultProvider.temperature
                        updatedAt = System.currentTimeMillis()
                    }
                    database?.aiProviderDao()?.updateProvider(updated)
                }
            }

            loadProviders()
        }
    }

    public fun loadProviders() {
        viewModelScope.launch {
            val providers = database?.aiProviderDao()?.getAllProviders() ?: emptyList()
            _providers.value = providers
            _defaultProvider.value = providers.find { it.isDefault }
        }
    }

    public fun updateProvider(provider: AIProvider) {
        viewModelScope.launch {
            provider.updatedAt = System.currentTimeMillis()
            database?.aiProviderDao()?.updateProvider(provider)
            loadProviders()
        }
    }

    public fun setDefaultProvider(id: String) {
        viewModelScope.launch {
            database?.aiProviderDao()?.clearAllDefaults()
            database?.aiProviderDao()?.setDefault(id)
            loadProviders()
        }
    }

    public suspend fun getCurrentProvider(): AIProvider? {
        return database?.aiProviderDao()?.getDefaultProvider()
    }

    public fun loadConversations(path: String, pageIndex: Int) {
        viewModelScope.launch {
            val list = database?.aiPageConversationDao()?.getConversationsByPage(path, pageIndex)
                ?: emptyList()
            _conversations.value = list
        }
    }

    public fun saveConversation(
        documentPath: String,
        documentName: String,
        pageIndex: Int,
        question: String,
        answer: String,
        pageContent: String
    ) {
        viewModelScope.launch {
            val conversation = AIPageConversation(
                documentPath = documentPath,
                documentName = documentName,
                pageIndex = pageIndex,
                question = question,
                answer = answer,
                pageContent = pageContent
            )
            database?.aiPageConversationDao()?.insertConversation(conversation)
            loadConversations(documentPath, pageIndex)
        }
    }

    public fun deleteConversation(conversation: AIPageConversation) {
        viewModelScope.launch {
            database?.aiPageConversationDao()?.deleteConversation(conversation)
            loadConversations(conversation.documentPath, conversation.pageIndex)
        }
    }

    public fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    public fun askQuestion(
        documentPath: String,
        documentName: String,
        pageIndex: Int,
        question: String,
        pageContent: String,
        onSuccess: (answer: String, promptTokens: Int, completionTokens: Int, totalTokens: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val provider = getCurrentProvider()
                if (provider == null) {
                    onError("请先配置 AI 提供商")
                    _isLoading.value = false
                    return@launch
                }

                if (provider.apiKey.isBlank()) {
                    onError("请先配置 ${provider.name} 的 API Key")
                    _isLoading.value = false
                    return@launch
                }

                val result = aiService.chat(provider, question, pageContent)

                result.fold(
                    onSuccess = { aiResponse ->
                        saveConversation(
                            documentPath = documentPath,
                            documentName = documentName,
                            pageIndex = pageIndex,
                            question = question,
                            answer = aiResponse.answer,
                            pageContent = pageContent
                        )
                        onSuccess(
                            aiResponse.answer,
                            aiResponse.promptTokens,
                            aiResponse.completionTokens,
                            aiResponse.totalTokens
                        )
                    },
                    onFailure = { error ->
                        onError(error.message ?: "AI 调用失败")
                    }
                )
            } catch (e: Exception) {
                onError("发生错误: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    public fun loadAllConversations(documentPath: String) {
        viewModelScope.launch {
            val allConversations = database?.aiPageConversationDao()
                ?.getConversationsByDocument(documentPath) ?: emptyList()
            _conversations.value = allConversations
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
