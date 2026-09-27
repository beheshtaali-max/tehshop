package com.example.fulluikotlin.ui.screens.splash

data class SplashUiState(
    val isLoading: Boolean = true,
    val isLoggedIn: Boolean? = null,
    val errorMessage: String? = null,
    val showRetry: Boolean = false
)

