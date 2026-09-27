package com.example.fulluikotlin.data.local.network.model.response


import kotlinx.serialization.Serializable

@Serializable
data class PopupAdsResponse(
    val popupshow: Boolean = true,
    val popupshowtype: String? = null,
    val popupdatails: List<PopupDetailResponse>? = null
)

@Serializable
data class PopupDetailResponse(
    val popuptype: String? = null,
    val popupurl: String? = null,
    val popupstarttime: String? = null,
    val popupexptime: String? = null,
    val popupid: String? = null
)
