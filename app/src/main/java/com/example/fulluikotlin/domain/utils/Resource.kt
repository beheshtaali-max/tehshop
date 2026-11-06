package com.example.fulluikotlin.domain.utils

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()

    /**
     * [message] is always safe to show to the user.
     * [supportCode] is an opaque code that lets support identify the exact branch.
     * [serverCode] and [serverMessage] are internal-only diagnostic values and must not be shown.
     */
    data class Error(
        val message: String,
        val supportCode: String? = null,
        val serverCode: String? = null,
        val serverMessage: String? = null
    ) : Resource<Nothing>()

    data class Loading(val isLoading: Boolean = true) : Resource<Nothing>()
    data class Exception(val throwable: Throwable) : Resource<Nothing>()
    data class MultiLoginError(val devices: List<String>) : Resource<Nothing>()
}
