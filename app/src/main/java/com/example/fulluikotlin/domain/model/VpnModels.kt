package com.example.fulluikotlin.domain.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class TrafficSpeed(
    val downloadBps: Long = 0,
    val uploadBps: Long = 0
)

data class ConnectionInfo(
    val serverName: String,
    val virtualIp: String?,
    val durationSeconds: Long
)