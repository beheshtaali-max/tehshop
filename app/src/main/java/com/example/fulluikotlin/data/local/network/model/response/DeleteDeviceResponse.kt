package com.example.fulluikotlin.data.local.network.model.response

import kotlinx.serialization.Serializable

@Serializable
data class DeleteDeviceResponse(
    val STATUS: String,
    val ERRORTEXT: String? = null
)