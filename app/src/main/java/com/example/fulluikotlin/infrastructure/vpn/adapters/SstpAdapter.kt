//package com.example.fulluikotlin.infrastructure.vpn.adapters
//
//import android.app.Activity
//import android.content.Context
//import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
//import com.example.fulluikotlin.data.local.datastore.UserDataStore
//import com.example.fulluikotlin.domain.model.ProtocolType
//import com.example.fulluikotlin.domain.model.Server
//import kittoku.osc.SstpController
//import kittoku.osc.interfaces.VpnStatusListener
//import kittoku.osc.utils.VpnBus
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//
//class SstpAdapter(
//    appContext: Context,
//    userDataStore: UserDataStore,
//    splitTunnelDataStore: SplitTunnelDataStore
//) : BaseVpnAdapter(appContext, userDataStore, splitTunnelDataStore) {
//
//    private var activeServer: Server? = null
//    private var listenerRegistered = false
//
//    private val listener = VpnStatusListener { state, message ->
//        when (mapSstpStatus(state, message)) {
//            SstpMappedState.CONNECTING -> markConnecting()
//            SstpMappedState.CONNECTED -> markConnected(activeServer)
//            SstpMappedState.DISCONNECTED -> markDisconnected()
//            SstpMappedState.ERROR -> markError("SSTP connection failed: ${message.orEmpty()}")
//            null -> Unit
//        }
//    }
//
//    override suspend fun connect(server: Server, protocol: ProtocolType, activity: Activity?) {
//        if (!prepareVpnPermission(activity)) return
//        activeServer = server
//        registerListener()
//        markConnecting()
//
//        withContext(Dispatchers.IO) {
//            val config = decryptConfig(server).trim()
//            val host = config.substringBeforeLast(":").trim()
//            val port = config.substringAfterLast(":", "443").trim().toIntOrNull() ?: 443
//            val (username, password) = resolveCredentials(server)
//            SstpController.connect(appContext, host, port, username, password, 1080)
//        }
//    }
//
//    override suspend fun disconnect() {
//        withContext(Dispatchers.IO) {
//            runCatching { SstpController.disconnect(appContext.applicationContext) }
//        }
//        unregisterListener()
//        markDisconnected()
//    }
//
//    private fun registerListener() {
//        if (listenerRegistered) return
//        VpnBus.add(listener)
//        listenerRegistered = true
//    }
//
//    private fun unregisterListener() {
//        if (!listenerRegistered) return
//        VpnBus.remove(listener)
//        listenerRegistered = false
//    }
//
//    private fun mapSstpStatus(state: String?, message: String?): SstpMappedState? {
//        val text = listOfNotNull(state, message).joinToString(" ").lowercase()
//        return when {
//            text.contains("connected") || text.contains("ready") -> SstpMappedState.CONNECTED
//            text.contains("error") || text.contains("fail") || text.contains("timeout") || text.contains("err_") -> SstpMappedState.ERROR
//            text.contains("disconnect") || text.contains("stopped") || text.contains("close") -> SstpMappedState.DISCONNECTED
//            text.contains("starting sstp") || text.contains("ssl") || text.contains("handshaking") -> SstpMappedState.CONNECTING
//            text.contains("authenticating") || text.contains("auth protocol") || text.contains("pap") -> SstpMappedState.CONNECTING
//            text.contains("chap") || text.contains("eap") || text.contains("configuring network") -> SstpMappedState.CONNECTING
//            text.contains("ip terminal") || text.contains("ipcp") || text.contains("ipv6cp") -> SstpMappedState.CONNECTING
//            else -> null
//        }
//    }
//
//    override fun release() {
//        unregisterListener()
//        super.release()
//    }
//
//    private enum class SstpMappedState {
//        CONNECTING,
//        CONNECTED,
//        DISCONNECTED,
//        ERROR
//    }
//}
