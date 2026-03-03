package com.archko.reader.pdf.service

import com.archko.reader.pdf.entity.AIProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AI 服务 - 处理与 AI 提供商的通信
 * @author: archko 2026/3/3
 */
public class AIService {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * AI 响应结果，包含回答和 token 使用信息
     */
    public data class AIResponse(
        val answer: String,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val totalTokens: Int = 0
    )

    /**
     * 调用 AI 接口进行问答
     */
    public suspend fun chat(
        provider: AIProvider,
        question: String,
        pageContent: String
    ): Result<AIResponse> {
        return try {
            when (provider.id) {
                "deepseek" -> chatWithDeepSeek(provider, question, pageContent)
                "qwen" -> chatWithQwen(provider, question, pageContent)
                "glm" -> chatWithGLM(provider, question, pageContent)
                else -> Result.failure(Exception("不支持的 AI 提供商: ${provider.id}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * DeepSeek API 调用
     */
    private suspend fun chatWithDeepSeek(
        provider: AIProvider,
        question: String,
        pageContent: String
    ): Result<AIResponse> {
        val url = "${provider.baseUrl}/v1/chat/completions"

        val requestBody = OpenAIRequest(
            model = provider.model,
            messages = listOf(
                Message(
                    role = "system",
                    content = "你是一个专业的文档阅读助手。用户会提供文档页面的内容，并基于这些内容提问。请根据页面内容准确回答问题。"
                ),
                Message(
                    role = "user",
                    content = "页面内容：\n$pageContent\n\n问题：$question"
                )
            ),
            max_tokens = provider.maxTokens,
            temperature = provider.temperature
        )

        return try {
            val response: OpenAIResponse = client.post(url) {
                header("Authorization", "Bearer ${provider.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val answer = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("AI 返回空响应"))

            Result.success(
                AIResponse(
                    answer = answer,
                    promptTokens = response.usage?.prompt_tokens ?: 0,
                    completionTokens = response.usage?.completion_tokens ?: 0,
                    totalTokens = response.usage?.total_tokens ?: 0
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("DeepSeek API 调用失败: ${e.message}", e))
        }
    }

    /**
     * 通义千问 API 调用
     */
    private suspend fun chatWithQwen(
        provider: AIProvider,
        question: String,
        pageContent: String
    ): Result<AIResponse> {
        val url = "${provider.baseUrl}/compatible-mode/v1/chat/completions"

        val requestBody = OpenAIRequest(
            model = provider.model,
            messages = listOf(
                Message(
                    role = "system",
                    content = "你是一个专业的文档阅读助手。用户会提供文档页面的内容，并基于这些内容提问。请根据页面内容准确回答问题。"
                ),
                Message(
                    role = "user",
                    content = "页面内容：\n$pageContent\n\n问题：$question"
                )
            ),
            max_tokens = provider.maxTokens,
            temperature = provider.temperature
        )

        return try {
            val response: OpenAIResponse = client.post(url) {
                header("Authorization", "Bearer ${provider.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val answer = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("AI 返回空响应"))

            Result.success(
                AIResponse(
                    answer = answer,
                    promptTokens = response.usage?.prompt_tokens ?: 0,
                    completionTokens = response.usage?.completion_tokens ?: 0,
                    totalTokens = response.usage?.total_tokens ?: 0
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("通义千问 API 调用失败: ${e.message}", e))
        }
    }

    /**
     * 智谱清言 API 调用
     */
    private suspend fun chatWithGLM(
        provider: AIProvider,
        question: String,
        pageContent: String
    ): Result<AIResponse> {
        val url = "${provider.baseUrl}/api/paas/v4/chat/completions"

        val requestBody = OpenAIRequest(
            model = provider.model,
            messages = listOf(
                Message(
                    role = "system",
                    content = "你是一个专业的文档阅读助手。用户会提供文档页面的内容，并基于这些内容提问。请根据页面内容准确回答问题。"
                ),
                Message(
                    role = "user",
                    content = "页面内容：\n$pageContent\n\n问题：$question"
                )
            ),
            max_tokens = provider.maxTokens,
            temperature = provider.temperature
        )

        return try {
            val response: OpenAIResponse = client.post(url) {
                header("Authorization", "Bearer ${provider.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.body()

            val answer = response.choices.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("AI 返回空响应"))

            Result.success(
                AIResponse(
                    answer = answer,
                    promptTokens = response.usage?.prompt_tokens ?: 0,
                    completionTokens = response.usage?.completion_tokens ?: 0,
                    totalTokens = response.usage?.total_tokens ?: 0
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("智谱清言 API 调用失败: ${e.message}", e))
        }
    }

    public fun close() {
        client.close()
    }
}

// OpenAI 兼容格式的请求和响应数据类
@Serializable
private data class OpenAIRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int,
    val temperature: Float
)

@Serializable
private data class Message(
    val role: String,
    val content: String
)

@Serializable
private data class OpenAIResponse(
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
private data class Choice(
    val message: Message
)

@Serializable
private data class Usage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)
