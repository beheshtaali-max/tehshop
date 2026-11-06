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
    WARP;

    companion object {
        /**
         * Converts API/cache protocol names to the app enum without making the API
         * responsible for Kotlin enum casing or separator style.
         */
        fun fromExternalName(value: String?): ProtocolType? {
            val raw = value?.trim().orEmpty()
            if (raw.isBlank()) return null

            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.let { return it }

            return when (raw.uppercase().replace(Regex("[^A-Z0-9]"), "")) {
                "V2RAY", "V2RAYNG", "VMESS", "VLESS", "TROJAN" -> V2RAY
                "OPENVPN", "OVPN" -> OPENVPN
                "WIREGUARD", "WG" -> WIREGUARD
                "CISCO", "ANYCONNECT" -> CISCO
                "STTP" -> STTP
                "SSTP" -> SSTP
                "SSH" -> SSH
                "WRAP" -> WRAP
                "WARP" -> WARP
                "IKEV2", "IKE2" -> IKEV2
                else -> null
            }
        }
    }
}
