package com.example.fulluikotlin.data.local.repository

import com.example.fulluikotlin.domain.repository.IpInfoRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

class IpInfoRepositoryImpl : IpInfoRepository {
    override suspend fun getPublicIp(): Result<String> = try {
        HttpClient(CIO) {
            install(ContentEncoding) {
                gzip()
            }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }
        }.use { client ->
            val ip = client.get(IPIFY_ENDPOINT)
                .bodyAsText()
                .trim()
                .toValidPublicIpOrNull()

            if (ip.isNullOrBlank()) {
                Result.failure(IllegalStateException("Public IP response is empty or invalid"))
            } else {
                Result.success(ip)
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun String.toValidPublicIpOrNull(): String? {
        val value = trim().removeSurrounding("\"").trim()
        return value.takeIf {
            it.length in 7..45 && PUBLIC_IP_PATTERN.matches(it)
        }
    }

    companion object {
        private const val IPIFY_ENDPOINT = "https://api.ipify.org"
        private const val CONNECT_TIMEOUT_MS = 3_000L
        private const val REQUEST_TIMEOUT_MS = 5_000L
        private const val SOCKET_TIMEOUT_MS = 5_000L
        private val PUBLIC_IP_PATTERN = Regex("^[0-9A-Fa-f:.]+$")
    }
}
