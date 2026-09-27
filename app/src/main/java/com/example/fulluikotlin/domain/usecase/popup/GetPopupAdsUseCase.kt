package com.example.fulluikotlin.domain.usecase.popup

import com.example.fulluikotlin.domain.model.PopupAdsConfig
import com.example.fulluikotlin.domain.repository.PopupRepository
import com.example.fulluikotlin.domain.utils.Resource

class GetPopupAdsUseCase(
    private val popupRepository: PopupRepository
) {
    suspend operator fun invoke(): Resource<PopupAdsConfig> {
        return popupRepository.getPopupAds()
    }
}