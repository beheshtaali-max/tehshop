package com.example.fulluikotlin.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore
import com.example.fulluikotlin.data.local.datastore.SettingsDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.usecase.auth.DeleteDeviceUseCase
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.infrastructure.vpn.VpnOrchestrator
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged


class ProfileViewModel(
    private val userDataStore: UserDataStore,
    private val settingsDataStore: SettingsDataStore,
    private val deleteDeviceUseCase: DeleteDeviceUseCase,
    private val appDetailsDataStore: AppDetailsDataStore,
    private val vpnOrchestrator: VpnOrchestrator


) : ViewModel() {

    private val _profileUiState = MutableStateFlow(ProfileUiState())
    val profileUiState: StateFlow<ProfileUiState> = _profileUiState.asStateFlow()

    private val _logoutState = MutableStateFlow<Resource<Unit>?>(null)
    val logoutState: StateFlow<Resource<Unit>?> = _logoutState

    val telegramSupportIdFlow: Flow<String?> = appDetailsDataStore.telegramSupportIdFlow
    val rechargeAccLinkFlow: Flow<String?> = appDetailsDataStore.rechargeAccLinkFlow

    val devices: StateFlow<List<String>> = userDataStore.multiLoginDevicesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onIsDarkModeChange(value: Boolean) {
        viewModelScope.launch {
            _profileUiState.value = _profileUiState.value.copy(isDarkMode = value)
            settingsDataStore.setDarkMode(value)
        }
    }

    fun onIsNotifAllowedChange(value: Boolean) {
        viewModelScope.launch {
            _profileUiState.value = _profileUiState.value.copy(isNotifAllowed = value)
            settingsDataStore.setNotifAllowed(value)
        }
    }

    fun onKillSwitchChange(value: Boolean) {
        viewModelScope.launch {
            _profileUiState.value = _profileUiState.value.copy(isKillSwitchEnabled = value)
            settingsDataStore.setKillSwitch(value)
        }
    }

    init {
        observeUserAndSettings()
    }

    private fun observeUserAndSettings() {
        viewModelScope.launch {
            combine(
                userDataStore.userFlow.distinctUntilChanged(),
                settingsDataStore.isDarkMode.distinctUntilChanged(),
                settingsDataStore.isNotifAllowed.distinctUntilChanged(),
                settingsDataStore.isKillSwitchEnabled.distinctUntilChanged()

            ) { user, isDarkMode, isNotifAllowed,isKillSwitchEnabled ->
                val remainingPercent = if (user != null) {
                    computeRemainingPercent(
                        user.totalTraffic ?: "0",
                        user.remainTraffic ?: "0"
                    )
                } else 0f

                ProfileUiState(
                    user = user,
                    isDarkMode = isDarkMode,
                    isNotifAllowed = isNotifAllowed,
                    isKillSwitchEnabled = isKillSwitchEnabled,
                    remainingPercent = remainingPercent,
                    isLoggedIn = user != null,
                    errorMessage = null
                )
            }.collect { newState ->
                _profileUiState.value = newState
            }
        }
    }

    fun logout(deviceId: String) {
        viewModelScope.launch {
            _logoutState.value = Resource.Loading(true)
            vpnOrchestrator.disconnect(flushUsage = true)
            val username = userDataStore.userFlow.first()?.username ?: return@launch
            val result = deleteDeviceUseCase(username, deviceId)
            if (result is Resource.Success) {
                _logoutState.value = Resource.Success(Unit)
            } else {
                _logoutState.value =
                    Resource.Error((result as? Resource.Error)?.message ?: "خطا در حذف دستگاه")
            }
        }
    }

    fun resetLogoutState() {
        viewModelScope.launch {
            vpnOrchestrator.disconnect(flushUsage = true)
            userDataStore.logout()
            _logoutState.value = null
        }
    }

    fun computeRemainingPercent(totalStr: String, remainStr: String): Float {
        val total = totalStr.toDoubleOrNull() ?: 0.0
        val remain = remainStr.toDoubleOrNull() ?: 0.0
        if (total <= 0) return 0f
        return (remain / total).toFloat().coerceIn(0f, 1f)
    }

}