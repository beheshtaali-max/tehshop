package com.example.fulluikotlin.data.local.network.api

import com.example.fulluikotlin.data.local.network.NetworkRequestExecutor
import com.example.fulluikotlin.data.local.network.interceptor.AuthInterceptor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.client.call.body
import io.ktor.client.request.request
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

@PublishedApi
internal val apiResponseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

@PublishedApi
internal fun decodePossiblyGzippedBody(bytes: ByteArray): String {
    val decodedBytes = if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    } else {
        bytes
    }
    return decodedBytes.toString(Charsets.UTF_8)
}

abstract class BaseApiService(
    protected val client: HttpClient,
    protected val authInterceptor: AuthInterceptor
) {
    protected suspend inline fun <reified T> apiRequest(
        crossinline isSuccessful: (T) -> Boolean = { true },
        crossinline block: HttpRequestBuilder.() -> Unit
    ): T {
        return NetworkRequestExecutor.execute {
            val response: HttpResponse = client.request {
                authInterceptor.addApiKey(this)
                block()
            }

            if (!response.status.isSuccess()) {
                throw IllegalStateException("HTTP ${response.status.value}")
            }

            val responseBody: T = response.body()
            if (!isSuccessful(responseBody)) {
                throw IllegalStateException("Server returned an unsuccessful response")
            }

            responseBody
        }
    }
}