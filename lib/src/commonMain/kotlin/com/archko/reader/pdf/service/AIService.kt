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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

public class AIService {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    public data class AIPromptConfig(
        val systemPrompt: String,
        val userPromptFormat: String,
        val unsupportedProvider: String,
        val apiRequestFailed: String,
        val apiEmptyResponse: String,
        val emptyResponse: String,
        val apiCallFailed: String
    )

    public data class AIResponse(
        val answer: String,
        val promptTokens: Int = 0,
        val completionTokens: Int = 0,
        val totalTokens: Int = 0
    )

    private var promptConfig: AIPromptConfig? = null

    public fun setPromptConfig(config: AIPromptConfig) {
        promptConfig = config
    }

    public suspend fun chat(
        provider: AIProvider,
        question: String,
        pageContent: String
    ): Result<AIResponse> {
        val config = promptConfig
        return try {
            when (provider.id) {
                "deepseek" -> chatWithDeepSeek(provider, question, pageContent, config)
                "qwen" -> chatWithQwen(provider, question, pageContent, config)
                "glm" -> chatWithGLM(provider, question, pageContent, config)
                "openai" -> chatWithOpenAI(provider, question, pageContent, config)
                "gemini" -> chatWithGemini(provider, question, pageContent, config)
                else -> Result.failure(
                    Exception((config?.unsupportedProvider ?: "不支持的 AI 提供商: %s").format(provider.id))
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun chatWithDeepSeek(
        provider: AIProvider,
        question: String,
        pageContent: String,
        config: AIPromptConfig?
    ): Result<AIResponse> {
        val url = "${provider.baseUrl}/v1/chat/completions"
        return makeOpenAIRequest(url, provider, question, pageContent, config)
    }

    private suspend fun chatWithQwen(
        provider: AIProvider,
        question: String,
        pageContent: String,
        config: AIPromptConfig?
    ): Result<AIResponse> {
        val url = "${provider.baseUrl}/compatible-mode/v1/chat/completions"
        return makeOpenAIRequest(url, provider, question, pageContent, config)
    }

    private suspend fun chatWithGLM(
        provider: AIProvider,
        question: String,
        pageContent: String,
        config: AIPromptConfig?
    ): Result<AIResponse> {
        val url = "${provider.baseUrl}/api/paas/v4/chat/completions"
        return makeOpenAIRequest(url, provider, question, pageContent, config)
    }

    private suspend fun chatWithOpenAI(
        provider: AIProvider,
        question: String,
        pageContent: String,
        config: AIPromptConfig?
    ): Result<AIResponse> {
        val url = "${provider.baseUrl}/v1/chat/completions"
        return makeOpenAIRequest(url, provider, question, pageContent, config)
    }

    private suspend fun chatWithGemini(
        provider: AIProvider,
        question: String,
        pageContent: String,
        config: AIPromptConfig?
    ): Result<AIResponse> {
        val userPrompt = config?.userPromptFormat?.format(pageContent, question)
            ?: "页面内容：\n$pageContent\n\n问题：$question"

        return try {
            val url = "${provider.baseUrl}/v1beta/models/${provider.model}:generateContent?key=${provider.apiKey}"

            val requestBody = buildJsonObject {
                putJsonArray("contents") {
                    add(buildJsonObject {
                        putJsonArray("parts") {
                            add(buildJsonObject {
                                put("text", JsonPrimitive(userPrompt))
                            })
                        }
                    })
                }
                putJsonObject("generationConfig") {
                    put("maxOutputTokens", JsonPrimitive(provider.maxTokens))
                    put("temperature", JsonPrimitive(provider.temperature.toDouble()))
                }
            }

            val responseString: String = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }.body()

            val responseJson = Json.parseToJsonElement(responseString).jsonObject
            val candidates = responseJson["candidates"]?.jsonArray
            val firstCandidate = candidates?.get(0)?.jsonObject
            val content = firstCandidate?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            val firstPart = parts?.get(0)?.jsonObject
            val answer = firstPart?.get("text")?.jsonPrimitive?.content
                ?: return Result.failure(Exception(config?.emptyResponse ?: "AI 返回空响应"))

            Result.success(
                AIResponse(
                    answer = answer,
                    promptTokens = 0,
                    completionTokens = 0,
                    totalTokens = 0
                )
            )
        } catch (e: Exception) {
            Result.failure(
                Exception((config?.apiCallFailed ?: "API 调用失败: %s").format(e.message), e)
            )
        }
    }

    private suspend fun makeOpenAIRequest(
        url: String,
        provider: AIProvider,
        question: String,
        pageContent: String,
        config: AIPromptConfig?
    ): Result<AIResponse> {
        val systemPrompt = config?.systemPrompt
            ?: "你是一个专业的文档阅读助手。用户会提供文档页面的内容，并基于这些内容提问。请根据页面内容准确回答问题。"
        val userPrompt = config?.userPromptFormat?.format(pageContent, question)
            ?: "页面内容：\n$pageContent\n\n问题：$question"

        val requestBody = OpenAIRequest(
            model = provider.model,
            messages = listOf(
                Message(
                    role = "system",
                    content = systemPrompt
                ),
                Message(
                    role = "user",
                    content = userPrompt
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
                ?: return Result.failure(Exception(config?.emptyResponse ?: "AI 返回空响应"))

            Result.success(
                AIResponse(
                    answer = answer,
                    promptTokens = response.usage?.prompt_tokens ?: 0,
                    completionTokens = response.usage?.completion_tokens ?: 0,
                    totalTokens = response.usage?.total_tokens ?: 0
                )
            )
        } catch (e: Exception) {
            Result.failure(
                Exception((config?.apiCallFailed ?: "API 调用失败: %s").format(e.message), e)
            )
        }
    }

    public fun close() {
        client.close()
    }
}

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
