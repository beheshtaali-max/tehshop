package com.example.fulluikotlin.domain.utils

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
    data class Loading(val isLoading: Boolean = true) : Resource<Nothing>()
    data class Exception(val throwable: Throwable) : Resource<Nothing>()
    data class MultiLoginError(val devices: List<String>) : Resource<Nothing>()
}