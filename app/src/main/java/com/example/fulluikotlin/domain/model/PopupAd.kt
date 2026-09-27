package com.example.fulluikotlin.domain.model

data class PopupAd(
    val type: String,
    val url: String,
    val startTime: Long,
    val expTime: Long,
    val id: String
)

data class PopupAdsConfig(
    val showPopup: Boolean = true,
    val showType: String,
    val ads: List<PopupAd>
)
