//package com.example.fulluikotlin.infrastructure.vpn.wireguard
//
//import com.wireguard.android.backend.Tunnel
//
//class SimpleTunnel(private val tunnelName: String = "wg-embedded") : Tunnel {
//    private var tunnelState: Tunnel.State = Tunnel.State.DOWN
//
//    override fun getName(): String = tunnelName
//
//    override fun onStateChange(newState: Tunnel.State) {
//        tunnelState = newState
//    }
//
//    fun state(): Tunnel.State = tunnelState
//}
