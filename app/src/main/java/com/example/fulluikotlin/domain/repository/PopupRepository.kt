package com.example.fulluikotlin.domain.repository

import com.example.fulluikotlin.domain.model.PopupAdsConfig
import com.example.fulluikotlin.domain.utils.Resource

interface PopupRepository {
    suspend fun getPopupAds(): Resource<PopupAdsConfig>
}