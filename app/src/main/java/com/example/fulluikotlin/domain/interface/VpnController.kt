package com.example.fulluikotlin.domain.`interface`

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow
import com.example.fulluikotlin.domain.model.ConnectionInfo
import com.example.fulluikotlin.domain.model.ConnectionState
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.domain.model.TrafficSpeed

interface VpnController {
    val connectionState: StateFlow<ConnectionState>
    val trafficSpeed: StateFlow<TrafficSpeed>
    val connectionInfo: StateFlow<ConnectionInfo?>

    suspend fun connect(server: Server, protocol: ProtocolType, activity: Activity? = null)
    suspend fun disconnect()
    fun release()   // آزادسازی منابع (مثلا unregister receiver)
}