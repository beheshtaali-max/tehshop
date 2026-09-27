package com.example.fulluikotlin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.CustomDialogStatus
import com.example.fulluikotlin.domain.model.DialogState
import com.example.fulluikotlin.domain.model.ExpiryType
import com.example.fulluikotlin.domain.model.PopupAdsConfig
import com.example.fulluikotlin.domain.model.UpdateDetails
import com.example.fulluikotlin.domain.usecase.auth.DeleteDeviceUseCase
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.ui.utils.DialogEventBus
import com.example.fulluikotlin.ui.utils.LinkOpener
import pw.fullvpn.android.BuildConfig
import com.example.fulluikotlin.ui.utils.ApkUpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.io.File

class MainViewModel(
    private val appContext: Context,
    private val appDetailsDataStore: AppDetailsDataStore,
    private val userDataStore: UserDataStore,
    private val deleteDeviceUseCase: DeleteDeviceUseCase
) : ViewModel() {

    private var volumeExpiryLink: String = ""
    private var dateExpiryLink: String = ""

    suspend fun loadExpiryLinks() {
        volumeExpiryLink = appDetailsDataStore.rechargeAccLinkFlow.first() ?: ""
        dateExpiryLink = appDetailsDataStore.buyAccLinkFlow.first() ?: ""
    }

    private val _currentDialog = MutableStateFlow<DialogState>(DialogState.None)
    val currentDialog: StateFlow<DialogState> = _currentDialog.asStateFlow()

    private val dialogQueue = mutableListOf<DialogState>()
    private var isWaitingForForeground = false
    private var updateDownloadJob: Job? = null
    private var activeUpdateDetails: UpdateDetails? = null
    private var downloadedUpdateFile: File? = null

    init {
        viewModelScope.launch {
            DialogEventBus.expiryEvent.collect { expiryType ->
                addExpiryDialog(expiryType)
            }
        }
    }

    fun prepareDialogsFromResponse(
        updateDetails: UpdateDetails?,
        customDialog: CustomDialogStatus?,
        adsDialog: PopupAdsConfig?
    ) {
        dialogQueue.clear()
        updateDetails?.let { details ->
            val currentVersion = BuildConfig.VERSION_NAME
            if (details.version != currentVersion) {
                dialogQueue.add(DialogState.Update(details))
            }
        }
        customDialog?.let { dialogQueue.add(DialogState.CustomInfo(it)) }

        if (adsDialog?.showPopup == true) {
            adsDialog.ads.forEach { ad ->
                dialogQueue.add(DialogState.PopupAds(ad))
            }
        }

        showNextDialog()
    }

    fun addExpiryDialog(type: ExpiryType, username: String? = null) {
        dialogQueue.add(DialogState.Expiry(type, username))

        if (_currentDialog.value is DialogState.None) {
            showNextDialog()
        }
    }

    fun onAppForeground() {
        if (isWaitingForForeground) {
            isWaitingForForeground = false
            showNextDialog()
        }
    }

    private fun showNextDialog() {
        if (isWaitingForForeground) return

        val next = dialogQueue.firstOrNull()
        _currentDialog.value = next ?: DialogState.None
        if (next != null) dialogQueue.removeAt(0)
    }

    fun dismissCurrentDialog(waitForForeground: Boolean = false) {
        if (waitForForeground) {
            // بررسی می‌کنیم که دیالوگ بعدی در صف چیست
            val nextDialog = dialogQueue.firstOrNull()
            if (nextDialog is DialogState.Expiry) {
                // اگر دیالوگ بعدی از نوع انقضا است، نیازی به انتظار برای foreground نیست
                _currentDialog.value = DialogState.None
                // در صورت فعال بودن flag، آن را غیرفعال می‌کنیم
                if (isWaitingForForeground) isWaitingForForeground = false
                showNextDialog()
            } else {
                _currentDialog.value = DialogState.None
                isWaitingForForeground = true
            }
        } else {
            showNextDialog()
        }
    }

    fun onUpdateClicked(url: String) {
        val updateDialog = _currentDialog.value as? DialogState.Update
        val details = updateDialog?.data ?: UpdateDetails(
            version = BuildConfig.VERSION_NAME,
            title = "آپدیت برنامه",
            description = "",
            size = "",
            link = url,
            forcedUpdate = false
        )
        startUpdateDownload(details)
    }

    fun hideUpdateDownloadDialog() {
        if (_currentDialog.value is DialogState.UpdateDownloading) {
            _currentDialog.value = DialogState.None
        }
    }

    fun retryUpdateDownload() {
        val details = when (val dialog = _currentDialog.value) {
            is DialogState.UpdateFailed -> dialog.data
            is DialogState.Update -> dialog.data
            else -> activeUpdateDetails ?: return
        }
        startUpdateDownload(details, forceRestart = true)
    }

    fun installDownloadedUpdate() {
        val readyDialog = _currentDialog.value as? DialogState.UpdateReady
        val apkPath = readyDialog?.apkPath ?: downloadedUpdateFile?.absolutePath ?: return
        val installStarted = ApkUpdateManager.installApk(appContext, apkPath)
        if (installStarted) {
            dismissCurrentDialog(waitForForeground = false)
        }
    }

    private fun startUpdateDownload(details: UpdateDetails, forceRestart: Boolean = false) {
        activeUpdateDetails = details
        downloadedUpdateFile = null

        if (forceRestart) {
            updateDownloadJob?.cancel()
            updateDownloadJob = null
        }

        if (updateDownloadJob?.isActive == true) {
            _currentDialog.value = DialogState.UpdateDownloading(
                data = details,
                progressPercent = null,
                downloadedBytes = 0L,
                totalBytes = -1L
            )
            return
        }

        _currentDialog.value = DialogState.UpdateDownloading(
            data = details,
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = -1L
        )

        updateDownloadJob = viewModelScope.launch {
            try {
                val apkFile = ApkUpdateManager.downloadApk(appContext, details) { progress ->
                    _currentDialog.value = DialogState.UpdateDownloading(
                        data = details,
                        progressPercent = progress.percent,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes
                    )
                }
                downloadedUpdateFile = apkFile
                _currentDialog.value = DialogState.UpdateReady(
                    data = details,
                    apkPath = apkFile.absolutePath
                )
            } catch (error: Exception) {
                _currentDialog.value = DialogState.UpdateFailed(
                    data = details,
                    message = error.message ?: "دانلود آپدیت انجام نشد. لطفاً دوباره تلاش کنید."
                )
            } finally {
                updateDownloadJob = null
            }
        }
    }

    fun onCustomInfoClicked(link: String) {
        LinkOpener.openUrl(appContext, link); dismissCurrentDialog(waitForForeground = false)
    }

    fun onPopupAdsClicked(link: String) {
        LinkOpener.openUrl(appContext, link)
        dismissCurrentDialog(waitForForeground = true)
    }

    fun onExpiryPrimaryClick() {
        val currentDialogState = _currentDialog.value
        if (currentDialogState is DialogState.Expiry) {
            val url = when (currentDialogState.type) {
                ExpiryType.VOLUME_EXPIRED -> volumeExpiryLink
                ExpiryType.DATE_EXPIRED -> dateExpiryLink
                ExpiryType.DATE_EXPIRING_SOON -> volumeExpiryLink
            }
            if (url.isNotEmpty()) {
                LinkOpener.openUrl(appContext, url)
            }
        }
        dismissCurrentDialog(waitForForeground = false)
    }

    fun onExpirySecondary(onLogout: () -> Unit) {
        val currentDialogState = _currentDialog.value
        if (currentDialogState is DialogState.Expiry && currentDialogState.type == ExpiryType.DATE_EXPIRING_SOON) {
            dismissCurrentDialog(waitForForeground = false)
            return
        }

        viewModelScope.launch {
            val username = userDataStore.userFlow.first()?.username ?: return@launch
            val device = userDataStore.userFlow.first()?.device ?: return@launch
            val result = deleteDeviceUseCase(username, device)
            if (result is Resource.Success) {
                userDataStore.logout()
                onLogout()
                dismissCurrentDialog()
            } else {
                //_logoutState.value = Resource.Error((result as? Resource.Error)?.message ?: "خطا در حذف دستگاه")
            }
        }
    }

}