package com.example.fulluikotlin.data.local.repository

import android.util.Log
import com.example.fulluikotlin.data.local.network.api.PopupApi
import com.example.fulluikotlin.domain.model.PopupAd
import com.example.fulluikotlin.domain.model.PopupAdsConfig
import com.example.fulluikotlin.domain.repository.PopupRepository
import com.example.fulluikotlin.domain.utils.Resource

class PopupRepositoryImpl(
    private val popupApi: PopupApi
) : PopupRepository {

    override suspend fun getPopupAds(): Resource<PopupAdsConfig> {
        return try {
            val response = popupApi.getPopupAds()
            Log.e("Response11", response.popupdatails?.get(0)?.popupurl ?: "no")

            if (response.popupdatails != null) {
                val ads = response.popupdatails.mapNotNull { detail ->
                    val start = detail.popupstarttime?.toLongOrNull() ?: 0L
                    val exp = detail.popupexptime?.toLongOrNull() ?: 0L
                    if (detail.popuptype != null && detail.popupurl != null && detail.popupid != null) {
                        PopupAd(
                            type = detail.popuptype,
                            url = detail.popupurl,
                            startTime = start,
                            expTime = exp,
                            id = detail.popupid
                        )
                    } else null
                }
                val config = PopupAdsConfig(
                    showPopup = response.popupshow,
                    showType = response.popupshowtype ?: "once",
                    ads = ads
                )
                Resource.Success(config)
            } else {
                Resource.Success(
                    PopupAdsConfig(
                        showPopup = response.popupshow,
                        showType = "once",
                        ads = emptyList()
                    )
                )
            }
        } catch (e: Exception) {
            Resource.Exception(e)
        }
    }
}