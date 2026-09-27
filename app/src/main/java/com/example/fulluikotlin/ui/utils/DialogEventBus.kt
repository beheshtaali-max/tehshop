package com.example.fulluikotlin.ui.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.fulluikotlin.domain.model.ExpiryType

object DialogEventBus {
    private val _expiryEvent = MutableSharedFlow<ExpiryType>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val expiryEvent = _expiryEvent.asSharedFlow()

    suspend fun emitExpiry(type: ExpiryType) {
        _expiryEvent.emit(type)
    }
}