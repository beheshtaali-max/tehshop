package com.example.fulluikotlin.infrastructure.vpn.traffic

data class ProtocolTrafficEvent(
    val protocol: String,
    val state: String,
    val downloadTotalBytes: Long,
    val uploadTotalBytes: Long,
    val downloadSpeedBps: Long,
    val uploadSpeedBps: Long,
    val durationSeconds: Long,
    val timestampMs: Long
)
