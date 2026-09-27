package com.example.fulluikotlin.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Server(
    val serverid: String,
    val servername: String,
    val servergroup: String,
    val serverimagename: String,
    val serversignal: String,
    val serverusername: String,
    val serverpassword: String,
    val config: String,
    val extra: String,
    val protocol: ProtocolType,
    val ip: String,
    val country: String,
    val ping: String,
    val isActive: Boolean
)
