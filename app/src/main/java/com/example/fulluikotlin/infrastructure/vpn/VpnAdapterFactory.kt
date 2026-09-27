package com.example.fulluikotlin.infrastructure.vpn

import com.example.fulluikotlin.domain.`interface`.VpnController
import com.example.fulluikotlin.domain.model.ProtocolType
//import com.example.fulluikotlin.infrastructure.vpn.adapters.CiscoAdapter
//import com.example.fulluikotlin.infrastructure.vpn.adapters.Ikev2Adapter
import com.example.fulluikotlin.infrastructure.vpn.adapters.SSHAdapter
//import com.example.fulluikotlin.infrastructure.vpn.adapters.SstpAdapter
import com.example.fulluikotlin.infrastructure.vpn.adapters.V2rayAdapter
//import com.example.fulluikotlin.infrastructure.vpn.adapters.WireGuardAdapter
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class VpnAdapterFactory : KoinComponent {
    fun create(protocol: ProtocolType): VpnController {
        return when (protocol.normalized()) {
            ProtocolType.V2RAY -> get<V2rayAdapter>()
            ProtocolType.SSH -> get<SSHAdapter>()
//            ProtocolType.WIREGUARD -> get<WireGuardAdapter>()
//            ProtocolType.CISCO -> get<CiscoAdapter>() //           ProtocolType.IKEV2 -> get<Ikev2Adapter>()
//            ProtocolType.SSTP -> get<SstpAdapter>()
//            ProtocolType.STTP -> get<SstpAdapter>()
            else -> throw IllegalArgumentException("Protocol $protocol is not supported by this build")
        }
    }

    private fun ProtocolType.normalized(): ProtocolType {
        return when (this) {
            ProtocolType.STTP -> ProtocolType.SSTP
            ProtocolType.WRAP -> ProtocolType.WARP
            else -> this
        }
    }
}