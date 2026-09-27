/*
package com.example.fulluikotlin.infrastructure.vpn.adapters

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.strongswan.android.data.VpnProfile
import org.strongswan.android.data.VpnProfileDataSource
import org.strongswan.android.data.VpnType
import org.strongswan.android.logic.CharonVpnService
import org.strongswan.android.logic.VpnStateService
import org.strongswan.android.ui.VpnProfileControlActivity
import java.util.TreeSet

class Ikev2Adapter(
    appContext: Context,
    userDataStore: UserDataStore,
    splitTunnelDataStore: SplitTunnelDataStore
) : BaseVpnAdapter(appContext, userDataStore, splitTunnelDataStore), VpnStateService.VpnStateListener {

    private var service: VpnStateService? = null
    private var bound = false
    private var activeServer: Server? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as? VpnStateService.LocalBinder)?.getService()
            service?.registerListener(this@Ikev2Adapter)
            updateStateFromService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service?.unregisterListener(this@Ikev2Adapter)
            service = null
            bound = false
            markDisconnected()
        }
    }

    override fun stateChanged() {
        updateStateFromService()
    }

    private fun updateStateFromService() {
        when (service?.state ?: VpnStateService.getConnectionState()) {
            VpnStateService.State.CONNECTED -> markConnected(activeServer)
            VpnStateService.State.CONNECTING,
            VpnStateService.State.DISCONNECTING -> markConnecting()
            VpnStateService.State.DISABLED -> markDisconnected()
            else -> markDisconnected()
        }
    }

    override suspend fun connect(server: Server, protocol: ProtocolType, activity: Activity?) {
        val act = activity ?: throw IllegalStateException("IKEv2 requires an Activity")
        if (!prepareVpnPermission(act)) return
        activeServer = server
        markConnecting()

        val profile = withContext(Dispatchers.IO) { createProfile(server) }
        bindStateService()

        val intent = Intent(appContext, VpnProfileControlActivity::class.java).apply {
            action = VpnProfileControlActivity.START_PROFILE
            putExtra(VpnProfileControlActivity.EXTRA_VPN_PROFILE_ID, profile.getUUID().toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        act.startActivity(intent)
    }

    private suspend fun createProfile(server: Server): VpnProfile {
        val (username, password) = resolveCredentials(server)
        val profile = VpnProfile().apply {
            setName(server.servername)
            setGateway(decryptConfig(server))
            setUsername(username)
            setPassword(password)
            setVpnType(VpnType.IKEV2_EAP)
            setFlags(0)
            setSelectedAppsHandling(VpnProfile.SelectedAppsHandling.SELECTED_APPS_DISABLE)
            setSelectedApps(TreeSet(splitTunnelPackages()))
        }

        VpnProfileDataSource(appContext).useDataSource { dataSource ->
            dataSource.insertProfile(profile)
        }
        return profile
    }

    private fun bindStateService() {
        if (bound) return
        bound = appContext.bindService(
            Intent(appContext, VpnStateService::class.java),
            serviceConnection,
            Service.BIND_AUTO_CREATE
        )
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            runCatching {
                VpnStateService.resetRetryTimer()
                val intent = Intent(appContext, CharonVpnService::class.java).apply {
                    action = CharonVpnService.DISCONNECT_ACTION
                }
                appContext.startService(intent)
            }
        }
        unbindStateService()
        markDisconnected()
    }

    private fun unbindStateService() {
        runCatching { service?.unregisterListener(this) }
        if (bound) {
            runCatching { appContext.unbindService(serviceConnection) }
        }
        bound = false
        service = null
    }

    override fun release() {
        unbindStateService()
        super.release()
    }
}

private inline fun <T> VpnProfileDataSource.useDataSource(block: (VpnProfileDataSource) -> T): T {
    open()
    return try {
        block(this)
    } finally {
        close()
    }
}
*/
