package com.archko.reader.viewer.utils

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlin.time.Duration.Companion.seconds

/**
 * @author: archko 2025/7/2 :17:20
 */
object OKHttpHelper {

    val client = HttpClient {
        install(ContentNegotiation)
        // 设置超时时间
        install(HttpTimeout) {
            requestTimeoutMillis = 30000 // 30秒
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }

        // 添加全局 header
        defaultRequest {
            header("Content-Type", "application/json; charset=utf-8")
        }

        //install(ContentNegotiation) {
        //    json(Json {
        //        prettyPrint = true
        //        isLenient = true
        //        ignoreUnknownKeys = true
        //    })
        //}

        //install(Logging)
    }

    suspend fun post(url: String, json: String?, headers: Map<String, String>?): String? {
        try {
            val response: HttpResponse = client.post(url) {
                headers?.forEach { (key, value) ->
                    header(key, value)
                }
                if (!json.isNullOrEmpty()) {
                    setBody(json)
                }
            }

            val responseBody = response.body<String>()
            println("Response status: ${response.status}")
            println("Response body: $responseBody")
            return responseBody
        } catch (e: Exception) {
            println("An error occurred: ${e.message}")
        } finally {
            //client.close()
        }

        return null
    }

    suspend fun get(url: String, headers: Map<String, String>?): String? {
        try {
            val response: HttpResponse = client.get(url) {
                headers?.forEach { (key, value) ->
                    header(key, value)
                }
            }

            val responseBody = response.body<String>()
            println("Response status: ${response.status}")
            println("Response body: $responseBody")
            return responseBody
        } catch (e: Exception) {
            println("An error occurred: ${e.message}")
        } finally {
            //client.close()
        }

        return null
    }

    suspend fun streamSSE(
        url: String,
        json: String,
        authToken: String,
        onData: (String) -> Unit,
        onError: (Exception) -> Unit,
        onComplete: () -> Unit
    ) {
        try {
            val client = HttpClient {
                install(ContentNegotiation)
                // 设置超时时间
                install(HttpTimeout) {
                    requestTimeoutMillis = 160000
                    connectTimeoutMillis = 30000
                    socketTimeoutMillis = 160000
                }
                install(SSE) {
                    maxReconnectionAttempts = 2
                    reconnectionTime = 2.seconds
                }
            }
            client.sse(
                url,
                {
                    contentType(ContentType.Application.Json)
                    method = HttpMethod.Post
                    setBody(json)
                    header("Authorization", "Bearer $authToken")
                },
            ) {
                incoming.collect { event ->
                    val line = event.data
                    if (null != line) {
                        val data = line.trim()
                        if (data == "[DONE]") {
                            onComplete()
                        } else {
                            onData(data)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            onError(e)
        }
    }
}