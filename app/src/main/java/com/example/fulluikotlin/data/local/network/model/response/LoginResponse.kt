package com.example.fulluikotlin.data.local.network.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    @SerialName("STATUS") val status: String,
    val user_id: String? = null,
    val creation_date: String? = null,
    val first_login_date: String? = null,
    val exp_date: String? = null,
    val total_traffic: String? = null,
    val remain_traffic: String? = null,
    val group_name: String? = null,
    val remain_days: Float? = null,
    val remain_device: String? = null,
    val isExpired: Boolean? = null,
    @SerialName("server") val servers: List<ServerNetwork>? = null,
    val ERRORTEXT: String? = null,
    val code: String? = null,
    val devices: String? = null
)

@Serializable
data class ServerNetwork(
    val serverid: String,
    val servername: String,
    val servergroup: String,
    val serverimagename: String,
    val serversignal: String,
    val serverusername: String,
    val serverpassword: String,
    val config: String,
    val extra: String,
    val protocol: String
)