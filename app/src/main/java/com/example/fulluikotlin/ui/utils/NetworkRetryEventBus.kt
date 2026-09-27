package com.example.fulluikotlin.ui.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NetworkRetryEventBus {
    private val _retryEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val retryEvents = _retryEvents.asSharedFlow()

    fun emitRetry(retryNumber: Int, maxRetries: Int) {
        _retryEvents.tryEmit("در حال تلاش مجدد... ($retryNumber از $maxRetries)")
    }
}
