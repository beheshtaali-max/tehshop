package com.example.fulluikotlin.data.local.network

import com.example.fulluikotlin.ui.utils.NetworkRetryEventBus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

object NetworkRequestExecutor {
    const val MAX_RETRIES = 3
    const val TIMEOUT_MS = 10_000L
    private const val RETRY_DELAY_MS = 1_000L
    private const val TOTAL_ATTEMPTS = MAX_RETRIES + 1

    suspend fun <T> execute(block: suspend () -> T): T {
        var lastError: Exception? = null

        repeat(TOTAL_ATTEMPTS) { attemptIndex ->
            try {
                return block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                val retryNumber = attemptIndex + 1
                if (retryNumber <= MAX_RETRIES) {
                    NetworkRetryEventBus.emitRetry(retryNumber, MAX_RETRIES)
                    delay(RETRY_DELAY_MS)
                }
            }
        }

        throw lastError ?: IllegalStateException("Request failed after $TOTAL_ATTEMPTS attempts")
    }
}
