package com.example.fulluikotlin.domain.model

sealed class DialogState {
    object None : DialogState()
    data class Update(val data: UpdateDetails) : DialogState()
    data class UpdateDownloading(
        val data: UpdateDetails,
        val progressPercent: Int?,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DialogState()
    data class UpdateReady(
        val data: UpdateDetails,
        val apkPath: String
    ) : DialogState()
    data class UpdateFailed(
        val data: UpdateDetails,
        val message: String
    ) : DialogState()
    data class CustomInfo(val data: CustomDialogStatus) : DialogState()
    data class PopupAds(val data: PopupAd) : DialogState()
    data class Expiry(val type: ExpiryType, val username: String? = null) : DialogState()
}
