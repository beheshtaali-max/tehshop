package com.example.fulluikotlin.infrastructure.vpn.adapters

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.infrastructure.vpn.traffic.VpnTrafficConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.openconnect_vpn.android.AuthFormHandler
import net.openconnect_vpn.android.api.GrantPermissionsActivity
import net.openconnect_vpn.android.core.OpenConnectManagementThread
import net.openconnect_vpn.android.core.OpenVpnService
import net.openconnect_vpn.android.core.ProfileManager
import net.openconnect_vpn.android.core.VPNConnector
import org.infradead.libopenconnect.LibOpenConnect.VPNStats

/**
 * Cisco AnyConnect adapter backed by the upstream OpenConnect Android engine.
 * Credentials are passed from the same IBSng credential store as other protocols.
 */
class CiscoAdapter(
    appContext: Context,
    userDataStore: UserDataStore,
    splitTunnelDataStore: SplitTunnelDataStore
) : BaseVpnAdapter(appContext, userDataStore, splitTunnelDataStore) {

    private var activeServer: Server? = null
    private var profileUuid: String? = null
    private var connector: VPNConnector? = null
    @Volatile private var everConnected = false

    override suspend fun connect(server: Server, protocol: ProtocolType, activity: Activity?) {
        val host = decryptConfig(server).trim()
        require(host.isNotBlank()) { "Cisco server address is empty" }
        val normalizedHost = if (host.startsWith("https://", true) || host.startsWith("http://", true)) {
            host
        } else {
            "https://$host"
        }
        val (username, password) = resolveCredentials(server)
        require(username.isNotBlank()) { "IBSng username is empty" }
        require(password.isNotBlank()) { "IBSng password is empty" }

        val act = activity ?: throw IllegalStateException("Cisco/OpenConnect requires an Activity")
        activeServer = server
        everConnected = false
        markConnecting()
        AuthFormHandler.setTehCredentials(username, password)
        ProfileManager.init(appContext)
        val profile = ProfileManager.create(normalizedHost)
        profileUuid = profile.getUUIDString()
        GrantPermissionsActivity.activateProfile(profile.getUUIDString())

        // Avoid leaving an old profile or service behind if a previous attempt was interrupted.
        runCatching { ProfileManager.setConnectedVpnProfileDisconnected() }

        connector = object : VPNConnector(appContext, false) {
            override fun onUpdate(service: OpenVpnService) {
                Log.d("CiscoAdapter", "onUpdate state=${service.getConnectionState()} statsValid=$statsValid")
                when (service.getConnectionState()) {
                    OpenConnectManagementThread.STATE_CONNECTED -> {
                        Log.d("CiscoAdapter", "markConnected from onUpdate")
                        everConnected = true
                        markConnected(activeServer)
                    }
                    OpenConnectManagementThread.STATE_DISCONNECTED -> {
                        // Binding creates the service before its VPN thread starts. Its initial
                        // DISCONNECTED state is normal and must not be treated as a failure.
                        if (everConnected) markDisconnected()
                    }
                    else -> markConnecting()
                }
                if (statsValid) {
                    val connectionStatus = service.getConnectionState()
                    val trafficState = when {
                        connectionStatus == OpenConnectManagementThread.STATE_CONNECTED -> VpnTrafficConstants.STATE_CONNECTED
                        connectionStatus == OpenConnectManagementThread.STATE_DISCONNECTED && everConnected -> VpnTrafficConstants.STATE_DISCONNECTED
                        else -> null
                    }
                    if (trafficState != null) {
                        appContext.sendBroadcast(Intent(VpnTrafficConstants.ACTION_PROTOCOL_TRAFFIC).apply {
                            setPackage(appContext.packageName)
                            putExtra(VpnTrafficConstants.EXTRA_PROTOCOL, VpnTrafficConstants.PROTOCOL_CISCO)
                            putExtra(VpnTrafficConstants.EXTRA_STATE, trafficState)
                            putExtra(VpnTrafficConstants.EXTRA_DOWNLOAD_TOTAL, newStats.rxBytes.coerceAtLeast(0L))
                            putExtra(VpnTrafficConstants.EXTRA_UPLOAD_TOTAL, newStats.txBytes.coerceAtLeast(0L))
                            putExtra(VpnTrafficConstants.EXTRA_DOWNLOAD_SPEED, deltaStats.rxBytes.coerceAtLeast(0L))
                            putExtra(VpnTrafficConstants.EXTRA_UPLOAD_SPEED, deltaStats.txBytes.coerceAtLeast(0L))
                            putExtra(VpnTrafficConstants.EXTRA_DURATION_SECONDS, 0L)
                            putExtra(VpnTrafficConstants.EXTRA_TIMESTAMP, System.currentTimeMillis())
                        })
                        if (trafficState == VpnTrafficConstants.STATE_DISCONNECTED) everConnected = false
                    }
                }
            }
        }

        val intent = Intent(appContext, GrantPermissionsActivity::class.java).apply {
            putExtra(appContext.packageName + GrantPermissionsActivity.EXTRA_UUID, profile.getUUIDString())
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            act.startActivity(intent)
        } catch (t: Throwable) {
            runCatching { connector?.unbind() }
            connector = null
            cleanupProfile()
            AuthFormHandler.setTehCredentials("", "")
            throw IllegalStateException("Unable to start Cisco VPN service: ${t.message}", t)
        }

        adapterScope.launch {
            // Poll only as a fallback; the OpenConnect service broadcasts state changes too.
            while (isActive && connectionState.value is com.example.fulluikotlin.domain.model.ConnectionState.Connecting) {
                val service = connector?.service
                if (service != null) {
                    when (service.getConnectionState()) {
                        OpenConnectManagementThread.STATE_CONNECTED -> {
                            everConnected = true
                            markConnected(activeServer)
                            Log.d("CiscoAdapter", "markConnected from polling")
                        }
                        OpenConnectManagementThread.STATE_DISCONNECTED -> {
                            if (everConnected) markDisconnected()
                        }
                    }
                }
                delay(500)
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            val service = connector?.service
            runCatching { service?.stopVPN() }
            // Let OpenConnect's worker release its TUN descriptor before deleting its profile.
            if (service != null) {
                withTimeoutOrNull(3_000L) {
                    while (service.getConnectionState() != OpenConnectManagementThread.STATE_DISCONNECTED) {
                        delay(100)
                    }
                }
            }
            runCatching { connector?.unbind() }
            connector = null
            cleanupProfile()
            AuthFormHandler.setTehCredentials("", "")
            everConnected = false
            activeServer = null
            markDisconnected()
        }
    }

    private fun cleanupProfile() {
        val uuid = profileUuid
        if (uuid != null) GrantPermissionsActivity.deactivateProfile(uuid)
        profileUuid = null
        if (uuid != null) {
            runCatching { ProfileManager.setConnectedVpnProfileDisconnected() }
            runCatching { ProfileManager.delete(uuid) }
        }
    }

    override fun release() {
        runCatching { connector?.service?.stopVPN() }
        runCatching { connector?.unbind() }
        connector = null
        cleanupProfile()
        AuthFormHandler.setTehCredentials("", "")
        everConnected = false
        super.release()
    }
}
