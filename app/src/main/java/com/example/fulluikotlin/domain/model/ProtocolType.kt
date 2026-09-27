package com.example.fulluikotlin.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProtocolType {
    V2RAY,
    OPENVPN,
    WIREGUARD,
    CISCO,
    STTP,
    SSH,
    WRAP,
    IKEV2,
    SSTP,
    WARP
}