package com.example.fulluikotlin.infrastructure.vpn.traffic

/**
 * One normalized broadcast contract for every VPN core.
 *
 * Protocol services must send byte counters with this action instead of forcing
 * the app layer to understand each library's private broadcast shape.
 */
object VpnTrafficConstants {
    const val ACTION_PROTOCOL_TRAFFIC = "pw.fullvpn.android.action.PROTOCOL_TRAFFIC"

    const val EXTRA_PROTOCOL = "protocol"
    const val EXTRA_STATE = "state"
    const val EXTRA_DOWNLOAD_TOTAL = "download_total_bytes"
    const val EXTRA_UPLOAD_TOTAL = "upload_total_bytes"
    const val EXTRA_DOWNLOAD_SPEED = "download_speed_bps"
    const val EXTRA_UPLOAD_SPEED = "upload_speed_bps"
    const val EXTRA_DURATION_SECONDS = "duration_seconds"
    const val EXTRA_TIMESTAMP = "timestamp_ms"

    const val PROTOCOL_V2RAY = "V2RAY"
    const val PROTOCOL_OPENVPN = "OPENVPN"
    const val PROTOCOL_SSH = "SSH"

    const val STATE_CONNECTING = "CONNECTING"
    const val STATE_CONNECTED = "CONNECTED"
    const val STATE_DISCONNECTED = "DISCONNECTED"
    const val STATE_ERROR = "ERROR"
}
