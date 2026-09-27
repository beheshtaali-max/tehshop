package com.example.fulluikotlin.domain.model

data class  User(
    val id: String,
    val username: String,
    val device: String,
    val devices: String?,
    val creationDate: String,
    val expDate: String,
    val totalTraffic: String,
    val remainTraffic: String,
    val groupName: String,
    val remainDays: Float,
    val remainDevice: String,
    val isExpired: Boolean
)

