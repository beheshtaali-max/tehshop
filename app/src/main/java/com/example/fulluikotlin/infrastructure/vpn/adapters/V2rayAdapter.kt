package com.example.fulluikotlin.infrastructure.vpn.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.ConnectionInfo
import com.example.fulluikotlin.domain.model.ConnectionState
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.domain.model.TrafficSpeed
import com.example.fulluikotlin.infrastructure.vpn.traffic.VpnTrafficConstants
import dev.dev7.lib.v2ray.V2rayController
import dev.dev7.lib.v2ray.utils.V2rayConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class V2rayAdapter(
    appContext: Context,
    userDataStore: UserDataStore,
    splitTunnelDataStore: SplitTunnelDataStore
) : BaseVpnAdapter(appContext, userDataStore, splitTunnelDataStore) {

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _trafficSpeed = MutableStateFlow(TrafficSpeed())
    override val trafficSpeed: StateFlow<TrafficSpeed> = _trafficSpeed.asStateFlow()

    private val _connectionInfo = MutableStateFlow<ConnectionInfo?>(null)
    override val connectionInfo: StateFlow<ConnectionInfo?> = _connectionInfo.asStateFlow()

    private var statsReceiver: BroadcastReceiver? = null
    private var activeServer: Server? = null
    private var connectedAtMillis: Long = 0L
    private val pollingJob = adapterScope.launch { pollConnectionState() }

    private suspend fun pollConnectionState() {
        while (true) {
            val newState = V2rayController.getConnectionState().toConnectionState()
            updateConnectionState(newState)
            delay(500)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStatsReceiver(context: Context) {
        if (statsReceiver != null) return

        statsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != VpnTrafficConstants.ACTION_PROTOCOL_TRAFFIC) return
                if (intent.getStringExtra(VpnTrafficConstants.EXTRA_PROTOCOL) != VpnTrafficConstants.PROTOCOL_V2RAY) return

                updateConnectionState(intent.getStringExtra(VpnTrafficConstants.EXTRA_STATE).toConnectionState())
                _trafficSpeed.value = TrafficSpeed(
                    downloadBps = intent.getLongExtraCompat(VpnTrafficConstants.EXTRA_DOWNLOAD_SPEED),
                    uploadBps = intent.getLongExtraCompat(VpnTrafficConstants.EXTRA_UPLOAD_SPEED)
                )
            }
        }

        val filter = IntentFilter(VpnTrafficConstants.ACTION_PROTOCOL_TRAFFIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(statsReceiver, filter)
        }
    }

    private fun unregisterStatsReceiver(context: Context) {
        statsReceiver?.let { receiver ->
            runCatching { context.unregisterReceiver(receiver) }
        }
        statsReceiver = null
    }

    private fun V2rayConstants.CONNECTION_STATES.toConnectionState(): ConnectionState {
        return when (this) {
            V2rayConstants.CONNECTION_STATES.CONNECTED -> ConnectionState.Connected
            V2rayConstants.CONNECTION_STATES.CONNECTING -> ConnectionState.Connecting
            V2rayConstants.CONNECTION_STATES.DISCONNECTED -> ConnectionState.Disconnected
        }
    }

    private fun String?.toConnectionState(): ConnectionState {
        return when (this?.uppercase(Locale.US)) {
            VpnTrafficConstants.STATE_CONNECTED -> ConnectionState.Connected
            VpnTrafficConstants.STATE_CONNECTING -> ConnectionState.Connecting
            VpnTrafficConstants.STATE_ERROR -> ConnectionState.Error("V2ray protocol error")
            else -> ConnectionState.Disconnected
        }
    }

    private fun updateConnectionState(newState: ConnectionState) {
        if (_connectionState.value == newState) return

        if (newState is ConnectionState.Connected && connectedAtMillis == 0L) {
            connectedAtMillis = System.currentTimeMillis()
        }

        if (newState is ConnectionState.Disconnected) {
            connectedAtMillis = 0L
            _trafficSpeed.value = TrafficSpeed()
            _connectionInfo.value = null
        } else if (newState is ConnectionState.Connected) {
            activeServer?.let { server ->
                _connectionInfo.value = ConnectionInfo(
                    serverName = server.servername,
                    virtualIp = null,
                    durationSeconds = ((System.currentTimeMillis() - connectedAtMillis) / 1000)
                        .coerceAtLeast(0)
                )
            }
        }

        _connectionState.value = newState
    }

    override suspend fun connect(server: Server, protocol: ProtocolType, activity: Activity?) {
        val act = activity ?: throw IllegalStateException("V2ray requires an Activity for connection")
        activeServer = server
        connectedAtMillis = 0L
        _trafficSpeed.value = TrafficSpeed()
        _connectionInfo.value = null
        updateConnectionState(ConnectionState.Connecting)
        registerStatsReceiver(appContext)

        val blockedApps = splitTunnelPackages()
        withContext(Dispatchers.IO) {
            V2rayController.startV2ray(
                act,
                server.servername,
                decryptConfig(server),
                blockedApps
            )
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            V2rayController.stopV2ray(appContext)
        }
        updateConnectionState(ConnectionState.Disconnected)
    }

    override fun release() {
        unregisterStatsReceiver(appContext)
        pollingJob.cancel()
        super.release()
    }

    private fun Intent.getLongExtraCompat(name: String): Long {
        val value = extras?.get(name) ?: return 0L
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }.coerceAtLeast(0L)
    }
}
