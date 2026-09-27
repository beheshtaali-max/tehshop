/*
package com.example.fulluikotlin.infrastructure.vpn.adapters

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import de.blinkt.openvpn.VpnProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sp.openconnect.VpnProfile
import sp.openconnect.api.GrantPermissionsActivity
import sp.openconnect.core.OpenConnectManagementThread
import sp.openconnect.core.OpenVpnService
import sp.openconnect.core.ProfileManager
import sp.openconnect.core.VPNConnector
import sp.openconnect.fragments.FeedbackFragment
import sp.openconnect.remote.Static

class CiscoAdapter(
    appContext: Context,
    userDataStore: UserDataStore,
    splitTunnelDataStore: SplitTunnelDataStore
) : BaseVpnAdapter(appContext, userDataStore, splitTunnelDataStore) {

    private var activeServer: Server? = null
    private var profile: VpnProfile? = null
    private var connector: VPNConnector? = null

    override suspend fun connect(server: Server, protocol: ProtocolType, activity: Activity?) {
        val act = activity ?: throw IllegalStateException("Cisco/OpenConnect requires an Activity")
        if (!prepareVpnPermission(act)) return
        activeServer = server
        markConnecting()

        val blockedApps = splitTunnelPackages()
        val (username, password) = resolveCredentials(server)
        val hostName = decryptConfig(server).replace("\\s".toRegex(), "")

        Static.CiscoUserName = username
        Static.CiscoPassword = password
        ProfileManager.init(appContext)
        ProfileManager.setBlockedApplications(blockedApps)
        FeedbackFragment.recordProfileAdd(appContext)
        profile = ProfileManager.create(hostName)

        connector = object : VPNConnector(appContext, false) {
            override fun onUpdate(service: OpenVpnService) {
                when (service.getConnectionState()) {
                    OpenConnectManagementThread.STATE_CONNECTED -> markConnected(activeServer)
                    OpenConnectManagementThread.STATE_DISCONNECTED -> markDisconnected()
                    else -> markConnecting()
                }
                if (statsValid) {
                    setTraffic(
                        downloadBps = 0,
//                        downloadBps = deltaStats.rxBytes,
                        uploadBps = 0
//                        uploadBps = deltaStats.txBytes
                    )
                }
            }
        }

        val intent = Intent(appContext, GrantPermissionsActivity::class.java).apply {
            putExtra(appContext.packageName + GrantPermissionsActivity.EXTRA_UUID, profile?.getUUID()?.toString())
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        act.startActivity(intent)
        startCiscoStateFallbackPolling()
    }

    private fun startCiscoStateFallbackPolling() {
        adapterScope.launch {
            while (isActive) {
                val service = connector?.service
                if (service == null) {
                    if (isVpnInterfaceUp()) markConnected(activeServer)
                } else {
                    when (service.getConnectionState()) {
                        OpenConnectManagementThread.STATE_CONNECTED -> markConnected(activeServer)
                        OpenConnectManagementThread.STATE_DISCONNECTED -> markDisconnected()
                        else -> markConnecting()
                    }
                }
                delay(1000)
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            runCatching { connector?.service?.stopVPN() }
        }
        runCatching { connector?.unbind() }
        connector = null
        profile = null
        markDisconnected()
    }

    override fun release() {
        runCatching { connector?.unbind() }
        connector = null
        super.release()
    }
}
*/
