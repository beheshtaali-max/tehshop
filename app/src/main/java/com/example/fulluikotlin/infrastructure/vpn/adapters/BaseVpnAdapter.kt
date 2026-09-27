package com.example.fulluikotlin.infrastructure.vpn.adapters

import android.app.Activity
import android.content.Context
import android.net.VpnService
import android.util.Log
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.`interface`.VpnController
import com.example.fulluikotlin.domain.model.ConnectionInfo
import com.example.fulluikotlin.domain.model.ConnectionState
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.domain.model.TrafficSpeed
import com.example.fulluikotlin.infrastructure.vpn.splittunnel.SplitTunnelPackageResolver
import com.example.fulluikotlin.ui.utils.Crypto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface
import java.util.Collections

abstract class BaseVpnAdapter(
    protected val appContext: Context,
    protected val userDataStore: UserDataStore,
    protected val splitTunnelDataStore: SplitTunnelDataStore
) : VpnController {

    protected val adapterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _trafficSpeed = MutableStateFlow(TrafficSpeed())
    override val trafficSpeed: StateFlow<TrafficSpeed> = _trafficSpeed.asStateFlow()

    private val _connectionInfo = MutableStateFlow<ConnectionInfo?>(null)
    override val connectionInfo: StateFlow<ConnectionInfo?> = _connectionInfo.asStateFlow()

    private var connectedAtMillis: Long = 0L

    protected fun markConnecting() {
        _connectionState.value = ConnectionState.Connecting
    }

    protected fun markConnected(server: Server? = null) {
        if (connectedAtMillis == 0L) connectedAtMillis = System.currentTimeMillis()
        _connectionState.value = ConnectionState.Connected
        server?.let {
            _connectionInfo.value = ConnectionInfo(
                serverName = it.servername,
                virtualIp = null,
                durationSeconds = ((System.currentTimeMillis() - connectedAtMillis) / 1000).coerceAtLeast(0)
            )
        }
    }

    protected fun markDisconnected() {
        connectedAtMillis = 0L
        _connectionState.value = ConnectionState.Disconnected
        _trafficSpeed.value = TrafficSpeed()
        _connectionInfo.value = null
    }

    protected fun markError(message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(javaClass.simpleName, message, throwable)
        _connectionState.value = ConnectionState.Error(message)
        _trafficSpeed.value = TrafficSpeed()
    }

    protected fun setTraffic(downloadBps: Long, uploadBps: Long) {
        _trafficSpeed.value = TrafficSpeed(downloadBps = downloadBps, uploadBps = uploadBps)
    }

    protected fun prepareVpnPermission(activity: Activity?): Boolean {
        val act = activity ?: throw IllegalStateException("This VPN protocol requires an Activity")
        val intent = VpnService.prepare(appContext)
        return if (intent != null) {
            act.startActivityForResult(intent, VPN_PERMISSION_REQUEST_CODE)
            false
        } else {
            true
        }
    }

    protected fun decryptConfig(server: Server): String = Crypto.decryptThis(server.config)

    protected suspend fun resolveCredentials(server: Server): Pair<String, String> {
        return if (server.serverusername == SHARED_USER_SENTINEL) {
            val (username, password) = userDataStore.getSavedCredentials()
            (username ?: "") to (password ?: "")
        } else {
            Crypto.decryptThis(server.serverusername) to Crypto.decryptThis(server.serverpassword)
        }
    }

    protected suspend fun splitTunnelPackages(): ArrayList<String> {
        val disallowedPackages = SplitTunnelPackageResolver.resolveDisallowedPackages(
            context = appContext,
            splitTunnelDataStore = splitTunnelDataStore
        )
        Log.d(javaClass.simpleName, "splitTunnel disallowedPackages=${disallowedPackages.size}")
        return disallowedPackages
    }

    protected fun isVpnInterfaceUp(): Boolean {
        return try {
            Collections.list(NetworkInterface.getNetworkInterfaces()).any { networkInterface ->
                networkInterface.isUp && listOf("tun", "ppp", "pptp", "wg").any { token ->
                    networkInterface.name.contains(token, ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun release() {
        adapterScope.cancel()
    }

    companion object {
        const val VPN_PERMISSION_REQUEST_CODE = 1001
        const val SHARED_USER_SENTINEL = "526c7864343242487055556a2b755957756f6b4732413d3d"
    }
}
