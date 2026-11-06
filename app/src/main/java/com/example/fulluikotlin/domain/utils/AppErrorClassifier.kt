package com.example.fulluikotlin.domain.utils

import com.example.fulluikotlin.data.local.network.ApiHttpException
import com.example.fulluikotlin.data.local.network.ApiResponseException
import kotlinx.serialization.SerializationException

object AppErrorClassifier {

    fun classify(error: Throwable): AppErrorCode {
        val causes = causes(error)
        val names = causes.map { it::class.java.simpleName }
        val messages = causes.mapNotNull { it.message?.lowercase() }

        val httpError = causes.filterIsInstance<ApiHttpException>().firstOrNull()
        if (httpError != null) {
            return if (httpError.statusCode >= 500 || httpError.statusCode == 408 || httpError.statusCode == 429) {
                AppErrorCode.SERVER_UNAVAILABLE
            } else {
                AppErrorCode.RESPONSE_INVALID
            }
        }

        if (causes.any { it is ApiResponseException || it is SerializationException }) {
            return AppErrorCode.RESPONSE_INVALID
        }

        if (isTimeout(names, messages)) {
            return AppErrorCode.NETWORK_TIMEOUT
        }

        if (isOffline(names, messages)) {
            return AppErrorCode.NETWORK_OFFLINE
        }

        return AppErrorCode.UNKNOWN
    }

    fun supportCode(error: Throwable, errorCode: AppErrorCode = classify(error)): String {
        val causes = causes(error)
        val names = causes.map { it::class.java.simpleName }
        val messages = causes.mapNotNull { it.message?.lowercase() }

        causes.filterIsInstance<ApiHttpException>().firstOrNull()?.let { httpError ->
            return "${errorCode.code}-H${httpError.statusCode}"
        }

        if (causes.any { it is ApiResponseException }) {
            return "${errorCode.code}-RSP"
        }
        if (causes.any { it is SerializationException } || names.any { it.contains("ContentConvert", true) }) {
            return "${errorCode.code}-JSN"
        }
        if (isTimeout(names, messages)) {
            return "${errorCode.code}-TMO"
        }
        if (names.any { it.contains("UnresolvedAddress", true) || it.contains("UnknownHost", true) }) {
            return "${errorCode.code}-DNS"
        }
        if (isOffline(names, messages)) {
            return "${errorCode.code}-CON"
        }

        val rootName = causes.lastOrNull()?.javaClass?.simpleName.orEmpty().ifBlank { "Unknown" }
        val suffix = Integer.toUnsignedString(rootName.hashCode(), 36)
            .uppercase()
            .padStart(4, '0')
            .takeLast(4)
        return "${errorCode.code}-$suffix"
    }

    private fun causes(error: Throwable): List<Throwable> =
        generateSequence(error) { it.cause }.toList()

    private fun isTimeout(names: List<String>, messages: List<String>): Boolean {
        return names.any { name ->
            name.contains("Timeout", ignoreCase = true) ||
                name.contains("SocketTimeout", ignoreCase = true)
        } || messages.any { it.contains("timed out") || it.contains("timeout") }
    }

    private fun isOffline(names: List<String>, messages: List<String>): Boolean {
        return names.any { name ->
            name.contains("UnresolvedAddress", ignoreCase = true) ||
                name.contains("UnknownHost", ignoreCase = true) ||
                name.contains("ConnectException", ignoreCase = true) ||
                name.contains("NoRouteToHost", ignoreCase = true)
        } || messages.any {
            it.contains("unable to resolve host") ||
                it.contains("network is unreachable") ||
                it.contains("failed to connect")
        }
    }
}
