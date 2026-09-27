package com.example.fulluikotlin.data.local.repository

import android.util.Log
import com.example.fulluikotlin.data.local.network.api.ServerApi
import com.example.fulluikotlin.domain.repository.ServersRepository

class ServersRepositoryImpl(
    private val telemetryApi: ServerApi
) : ServersRepository {
    override suspend fun reportUsedProtocol(protocol: String?, server: String?) {
        try {
            val response = telemetryApi.reportUsedProtocol(protocol, server)
            if (response.status.equals("SUCCESSFULL", ignoreCase = true)) {
                Log.d("Telemetry", "UsedProtocol reported successfully")
            } else {
                Log.w("Telemetry", "UsedProtocol reported with status: ${response.status}")
            }
        } catch (e: Exception) {
            Log.e("Telemetry", "Failed to report used protocol", e)
        }
    }


}