package com.example.fulluikotlin.infrastructure.vpn.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
import java.net.URI
import java.util.Locale

/**
 * SSH adapter backed by the existing sing-box/V2ray module.
 *
 * The panel data is mapped like the old Java implementation:
 * - server.config          -> encrypted SSH host or host:port
 * - server.serverusername  -> encrypted SSH username, or shared-user sentinel
 * - server.serverpassword  -> encrypted SSH password, or saved user password when sentinel is used
 */
class SSHAdapter(
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

    override suspend fun connect(server: Server, protocol: ProtocolType, activity: Activity?) {
        val act = activity ?: throw IllegalStateException("SSH requires an Activity for connection")

        if (protocol != ProtocolType.SSH || server.protocol != ProtocolType.SSH) {
            val message = "برای استفاده از این پروتکل سرور مخصوص SSH رو انتخاب کنید."
            showToast(message)
            markSshError(message)
            return
        }

        activeServer = server
        connectedAtMillis = 0L
        _trafficSpeed.value = TrafficSpeed()
        _connectionInfo.value = null
        updateConnectionState(ConnectionState.Connecting)
        registerStatsReceiver(appContext)

        try {
            val endpoint = parseSshEndpoint(decryptConfig(server))
            val (sshUsername, sshPassword) = resolveCredentials(server)

            if (endpoint.host.isBlank()) {
                val message = "آدرس سرور SSH معتبر نیست."
                showToast(message)
                markSshError(message)
                disconnect()
                return
            }

            if (sshUsername.isBlank() || sshUsername == "null") {
                val message = "نام کاربری SSH معتبر نیست."
                showToast(message)
                markSshError(message)
                disconnect()
                return
            }

            if (sshPassword.isBlank() || sshPassword == "null") {
                val message = "رمز عبور SSH معتبر نیست."
                showToast(message)
                markSshError(message)
                disconnect()
                return
            }

            val blockedApps = splitTunnelPackages()
            Log.d(TAG, "SSH connect host=${endpoint.host} port=${endpoint.port} username=$sshUsername")

            withContext(Dispatchers.IO) {
                V2rayController.startSSH(
                    act,
                    server.servername,
                    endpoint.host,
                    endpoint.port,
                    sshUsername,
                    sshPassword,
                    blockedApps
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "connectToSSH error", t)
            markSshError(t.message ?: "SSH connection failed")
            disconnect()
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerStatsReceiver(context: Context) {
        if (statsReceiver != null) return

        statsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != VpnTrafficConstants.ACTION_PROTOCOL_TRAFFIC) return
                if (intent.getStringExtra(VpnTrafficConstants.EXTRA_PROTOCOL) != VpnTrafficConstants.PROTOCOL_SSH) return

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
            VpnTrafficConstants.STATE_ERROR -> ConnectionState.Error("SSH protocol error")
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

    private fun markSshError(message: String) {
        _connectionState.value = ConnectionState.Error(message)
        _trafficSpeed.value = TrafficSpeed()
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseSshEndpoint(rawAddress: String): SshEndpoint {
        val value = rawAddress.trim()
        if (value.isEmpty()) return SshEndpoint("", DEFAULT_SSH_PORT)

        runCatching {
            if (value.startsWith("ssh://", ignoreCase = true)) {
                val uri = URI(value)
                val host = uri.host.orEmpty().trim()
                val port = if (uri.port > 0) uri.port else DEFAULT_SSH_PORT
                return SshEndpoint(host, port)
            }
        }

        val withoutScheme = value.removePrefix("SSH://").removePrefix("ssh://").trim()

        // IPv6 with brackets: [2001:db8::1]:2222
        if (withoutScheme.startsWith("[")) {
            val end = withoutScheme.indexOf(']')
            if (end > 0) {
                val host = withoutScheme.substring(1, end).trim()
                val portText = withoutScheme.substring(end + 1).removePrefix(":").trim()
                val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: DEFAULT_SSH_PORT
                return SshEndpoint(host, port)
            }
        }

        val separator = withoutScheme.lastIndexOf(':')
        return if (separator > 0 && withoutScheme.indexOf(':') == separator) {
            val host = withoutScheme.substring(0, separator).trim()
            val port = withoutScheme.substring(separator + 1).trim()
                .toIntOrNull()
                ?.takeIf { it in 1..65535 }
                ?: DEFAULT_SSH_PORT
            SshEndpoint(host, port)
        } else {
            SshEndpoint(withoutScheme, DEFAULT_SSH_PORT)
        }
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

    private data class SshEndpoint(val host: String, val port: Int)

    companion object {
        private const val TAG = "SSHAdapter"
        private const val DEFAULT_SSH_PORT = 22
    }
}
