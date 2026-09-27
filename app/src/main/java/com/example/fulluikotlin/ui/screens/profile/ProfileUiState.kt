package com.example.fulluikotlin.ui.screens.profile

import com.example.fulluikotlin.domain.model.User

data class ProfileUiState(
    val user: User? = null,
    val isDarkMode: Boolean = false,
    val isNotifAllowed: Boolean = false,
    val isKillSwitchEnabled: Boolean = false,
    val isLoggedIn: Boolean = true,
    val remainingPercent: Float = 0f,
    val errorMessage: String? = null
)
