//package com.example.fulluikotlin.infrastructure.vpn.adapters
//
//import android.app.Activity
//import android.content.Context
//import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
//import com.example.fulluikotlin.data.local.datastore.UserDataStore
//import com.example.fulluikotlin.domain.model.ProtocolType
//import com.example.fulluikotlin.domain.model.Server
//import com.example.fulluikotlin.infrastructure.vpn.wireguard.SimpleTunnel
//import com.wireguard.android.backend.GoBackend
//import com.wireguard.android.backend.Tunnel
//import com.wireguard.config.Config
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import java.io.ByteArrayInputStream
//import java.nio.charset.StandardCharsets
//
//class WireGuardAdapter(
//    appContext: Context,
//    userDataStore: UserDataStore,
//    splitTunnelDataStore: SplitTunnelDataStore
//) : BaseVpnAdapter(appContext, userDataStore, splitTunnelDataStore) {
//
//    private val backend by lazy { GoBackend(appContext.applicationContext) }
//    private val tunnel = SimpleTunnel()
//    private var activeServer: Server? = null
//
//    override suspend fun connect(server: Server, protocol: ProtocolType, activity: Activity?) {
//        if (!prepareVpnPermission(activity)) return
//        activeServer = server
//        markConnecting()
//        withContext(Dispatchers.IO) {
//            try {
//                val configText = decryptConfig(server)
//                val config = ByteArrayInputStream(configText.toByteArray(StandardCharsets.UTF_8)).use {
//                    Config.parse(it)
//                }
//                backend.setState(tunnel, Tunnel.State.UP, config)
//                markConnected(server)
//            } catch (e: Exception) {
//                markError("WireGuard connection failed", e)
//            }
//        }
//    }
//
//    override suspend fun disconnect() {
//        withContext(Dispatchers.IO) {
//            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
//        }
//        markDisconnected()
//    }
//}
