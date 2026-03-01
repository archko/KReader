package com.archko.reader.pdf.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archko.reader.pdf.cache.AppDatabase
import com.archko.reader.pdf.entity.AIProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * AI 功能 ViewModel
 * @author: archko 2026/3/1
 */
public class AIViewModel : ViewModel() {

    public var database: AppDatabase? = null

    private val _providers = MutableStateFlow<List<AIProvider>>(emptyList())
    public val providers: StateFlow<List<AIProvider>> = _providers

    private val _defaultProvider = MutableStateFlow<AIProvider?>(null)
    public val defaultProvider: StateFlow<AIProvider?> = _defaultProvider

    /**
     * 初始化默认提供商
     */
    public fun initializeDefaultProviders() {
        viewModelScope.launch {
            val existing = database?.aiProviderDao()?.getAllProviders() ?: emptyList()
            if (existing.isEmpty()) {
                val defaults = listOf(
                    AIProvider(
                        id = "deepseek",
                        name = "DeepSeek",
                        apiKey = "",
                        baseUrl = "https://api.deepseek.com",
                        model = "deepseek-chat",
                        maxTokens = 2000,
                        temperature = 0.7f,
                        enabled = true,
                        isDefault = true
                    ),
                    AIProvider(
                        id = "qwen",
                        name = "通义千问",
                        apiKey = "",
                        baseUrl = "https://dashscope.aliyuncs.com",
                        model = "qwen-turbo",
                        maxTokens = 2000,
                        temperature = 0.7f,
                        enabled = false,
                        isDefault = false
                    ),
                    AIProvider(
                        id = "glm",
                        name = "智谱清言",
                        apiKey = "",
                        baseUrl = "https://open.bigmodel.cn",
                        model = "glm-4-flash",
                        maxTokens = 2000,
                        temperature = 0.7f,
                        enabled = false,
                        isDefault = false
                    )
                )
                database?.aiProviderDao()?.insertAllProviders(defaults)
            }
            loadProviders()
        }
    }

    /**
     * 加载所有提供商
     */
    public fun loadProviders() {
        viewModelScope.launch {
            val providers = database?.aiProviderDao()?.getAllProviders() ?: emptyList()
            _providers.value = providers
            _defaultProvider.value = providers.find { it.isDefault && it.enabled }
        }
    }

    /**
     * 更新提供商
     */
    public fun updateProvider(provider: AIProvider) {
        viewModelScope.launch {
            provider.updatedAt = System.currentTimeMillis()
            database?.aiProviderDao()?.updateProvider(provider)
            loadProviders()
        }
    }

    /**
     * 设置默认提供商
     */
    public fun setDefaultProvider(id: String) {
        viewModelScope.launch {
            database?.aiProviderDao()?.clearAllDefaults()
            database?.aiProviderDao()?.setDefault(id)
            loadProviders()
        }
    }

    /**
     * 切换启用状态
     */
    public fun toggleEnabled(provider: AIProvider) {
        viewModelScope.launch {
            val updated = provider.apply {
                enabled = !enabled
                updatedAt = System.currentTimeMillis()
            }
            database?.aiProviderDao()?.updateProvider(updated)
            loadProviders()
        }
    }

    /**
     * 获取当前可用的提供商
     */
    public suspend fun getCurrentProvider(): AIProvider? {
        return database?.aiProviderDao()?.getDefaultProvider()
    }
}
