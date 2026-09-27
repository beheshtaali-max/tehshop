package com.example.fulluikotlin.ui.screens.devicesactive

sealed class DeleteState {
    data class Loading(val deviceId: String) : DeleteState()
    data class Success(val deviceId: String) : DeleteState()
    data class Error(val deviceId: String, val message: String) : DeleteState()
}