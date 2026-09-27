package com.example.fulluikotlin.ui.screens.home

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fulluikotlin.data.local.datastore.ServerDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.ConnectionState
import com.example.fulluikotlin.domain.model.ExpiryType
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.domain.model.User
import com.example.fulluikotlin.domain.usecase.auth.LoginUseCase
import com.example.fulluikotlin.domain.usecase.servers.ServersUseCase
import com.example.fulluikotlin.infrastructure.service.UserInfoUpdateService
import com.example.fulluikotlin.infrastructure.permission.NotificationPermissionHelper
import com.example.fulluikotlin.infrastructure.vpn.VpnOrchestrator
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.ui.utils.DialogEventBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class HomeViewModel(
    private val app: Application,
    private val orchestrator: VpnOrchestrator,
    private val serverDataStore: ServerDataStore,
    private val serversUseCase: ServersUseCase,
    private val userDataStore: UserDataStore,
    private val loginUseCase: LoginUseCase,
) : AndroidViewModel(app) {

    val connectionState = orchestrator.connectionState
    val connectionInfo = orchestrator.connectionInfo
    val totalDownloadBytes = orchestrator.totalDownloadBytes
    val totalUploadBytes = orchestrator.totalUploadBytes

    private val _serverSelected = mutableStateOf<Server?>(null)
    val serverSelected: Server? get() = _serverSelected.value

    private val _elapsedTime = mutableStateOf(0L)
    val elapsedTime: Long get() = _elapsedTime.value

    private val _downloadedTotal = mutableStateOf("0.0 B")
    val downloadedTotal: String get() = _downloadedTotal.value

    private val _uploadedTotal = mutableStateOf("0.0 B")
    val uploadedTotal: String get() = _uploadedTotal.value

    private val _publicIp = MutableStateFlow<String?>(null)
    val publicIp = _publicIp.asStateFlow()

    private val _isIpLoading = MutableStateFlow(false)
    val isIpLoading = _isIpLoading.asStateFlow()

    private var timerJob: Job? = null
    private var ipFetchJob: Job? = null

    init {
        viewModelScope.launch {
            serverDataStore.selectedServerFlow.collect { server ->
                _serverSelected.value = server
            }
        }
        viewModelScope.launch {
            orchestrator.totalDownloadBytes.collect { bytes ->
                _downloadedTotal.value = formatBytes(bytes)
            }
        }
        viewModelScope.launch {
            orchestrator.totalUploadBytes.collect { bytes ->
                _uploadedTotal.value = formatBytes(bytes)
            }
        }

        viewModelScope.launch {
            connectionState.collectLatest { state ->
                when (state) {
                    ConnectionState.Connected -> {
                        startTimer(orchestrator.connectionStartedAtMillis.value)
                        ipFetchJob?.cancel()
                        _publicIp.value = null
                        _isIpLoading.value = false
                    }

                    ConnectionState.Connecting -> {
                        ipFetchJob?.cancel()
                        _publicIp.value = null
                        _isIpLoading.value = false
                    }

                    ConnectionState.Disconnected -> {
                        stopTimer()
                        ipFetchJob?.cancel()
                        _publicIp.value = null
                        _isIpLoading.value = false
                    }

                    is ConnectionState.Error -> {
                        stopTimer()
                        ipFetchJob?.cancel()
                        _publicIp.value = null
                        _isIpLoading.value = false
                    }
                }
            }
        }
    }

    /**
     * Kept for HomeScreen compatibility. Connection statistics are now collected through
     * VpnOrchestrator and per-protocol adapters instead of directly from the V2Ray ViewModel.
     */
    fun registerStatsReceiver(context: Context) = Unit

    /** See [registerStatsReceiver]. */
    fun unregisterStatsReceiver(context: Context) = Unit

    fun connect(activity: Activity, server: Server?) {
        val targetServer = server ?: serverSelected
        if (targetServer == null) {
            Log.w("HomeViewModel", "connect skipped: no selected server")
            return
        }
        connect(targetServer, targetServer.protocol, activity)
    }

    fun connect(server: Server, protocol: ProtocolType, activity: Activity) {
        Log.d("HomeViewModel", "connect called: server=${server.servername}, protocol=$protocol")
        viewModelScope.launch {
            val currentState = connectionState.value
            if (currentState is ConnectionState.Connected || currentState is ConnectionState.Connecting) return@launch

            if (!NotificationPermissionHelper.ensurePermission(activity)) {
                Toast.makeText(
                    app.applicationContext,
                    "برای اتصال VPN باید اجازه نمایش نوتیفیکیشن را فعال کنید.",
                    Toast.LENGTH_SHORT
                ).show()
                Log.d("HomeViewModel", "connect paused: notification permission is required")
                return@launch
            }

            _downloadedTotal.value = "0.0 B"
            _uploadedTotal.value = "0.0 B"

            val preparedSessionId = orchestrator.prepareAuthConnectionState(server, protocol, activity)
                ?: return@launch

            val authResult = authenticateBeforeConnect(preparedSessionId)
            if (!orchestrator.isConnectionSessionActive(preparedSessionId)) {
                Log.d("HomeViewModel", "AUTH result ignored because connection attempt was cancelled")
                return@launch
            }

            when (authResult) {
                is Resource.Success -> {
                    val canConnect = validateUserCanConnect()
                    if (!canConnect) {
                        orchestrator.abortPreparedConnection(
                            preparedSessionId,
                            "اکانت شما امکان اتصال ندارد"
                        )
                        return@launch
                    }

                    val latestServer = resolveLatestServerAfterAuth(server)
                    val latestProtocol = latestServer.protocol

                    runCatching {
                        serversUseCase(
                            protocol = latestProtocol.name,
                            server = latestServer.serverid
                        )
                    }.onFailure {
                        Log.e("HomeViewModel", "server usage telemetry failed", it)
                    }

                    orchestrator.selectAndConnect(
                        server = latestServer,
                        protocol = latestProtocol,
                        activity = activity,
                        preparedSessionId = preparedSessionId
                    )
                }

                is Resource.Error -> {
                    abortAuthConnection(preparedSessionId, authResult.message)
                }

                is Resource.MultiLoginError -> {
                    val message = "ظرفیت کاربری اکانت شما پر شده است"
                    abortAuthConnection(preparedSessionId, message)
                }

                is Resource.Exception -> {
                    val message = authResult.throwable.message ?: "عدم ارتباط با سرور"
                    abortAuthConnection(preparedSessionId, message)
                }

                is Resource.Loading -> Unit
            }
        }
    }

    private suspend fun authenticateBeforeConnect(sessionId: Long): Resource<User> {
        val user = userDataStore.userFlow.first()
        val (savedUsername, savedPassword) = userDataStore.getSavedCredentials()
        val (multiLoginUsername, multiLoginPassword, multiLoginDevice) = userDataStore.getMultiLoginCredentials()

        val username = when {
            !multiLoginUsername.isNullOrBlank() -> multiLoginUsername
            !savedUsername.isNullOrBlank() -> savedUsername
            !user?.username.isNullOrBlank() -> user?.username
            else -> null
        }

        val password = when {
            !multiLoginPassword.isNullOrBlank() -> multiLoginPassword
            !savedPassword.isNullOrBlank() -> savedPassword
            else -> null
        }

        val device = when {
            !multiLoginDevice.isNullOrBlank() -> multiLoginDevice
            !user?.device.isNullOrBlank() -> user?.device
            else -> null
        }

        if (username.isNullOrBlank() || password.isNullOrBlank() || device.isNullOrBlank()) {
            val message = "اطلاعات ورود برای احراز هویت پیدا نشد. لطفاً دوباره وارد شوید."
            Log.w("HomeViewModel", "AUTH before connect skipped: missing credentials session=$sessionId")
            return Resource.Error(message)
        }

        Log.d("HomeViewModel", "AUTH before connect started: session=$sessionId device=$device")
        val result = loginUseCase(username, password, "android", device)

        if (result is Resource.MultiLoginError) {
            userDataStore.saveMultiLoginDevices(result.devices)
            userDataStore.saveMultiLoginCredentials(username, password, device)
        }

        when (result) {
            is Resource.Success -> Log.d("HomeViewModel", "AUTH before connect success: session=$sessionId")
            is Resource.Error -> Log.w("HomeViewModel", "AUTH before connect error: session=$sessionId message=${result.message}")
            is Resource.MultiLoginError -> Log.w("HomeViewModel", "AUTH before connect multi-login: session=$sessionId devices=${result.devices.size}")
            is Resource.Exception -> Log.e("HomeViewModel", "AUTH before connect exception: session=$sessionId", result.throwable)
            is Resource.Loading -> Unit
        }

        return result
    }

    private suspend fun resolveLatestServerAfterAuth(requestedServer: Server): Server {
        val selectedServer = serverDataStore.getSelectedServer()
        if (selectedServer?.serverid == requestedServer.serverid) return selectedServer

        val updatedServer = serverDataStore.serversFlow.first()
            .firstOrNull { it.serverid == requestedServer.serverid }

        return updatedServer ?: selectedServer ?: requestedServer
    }

    private suspend fun abortAuthConnection(sessionId: Long, message: String) {
        orchestrator.abortPreparedConnection(sessionId, message)
        Toast.makeText(app.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    fun disconnect(context: Context? = null) {
        viewModelScope.launch {
            orchestrator.disconnect()
            UserInfoUpdateService.stop(context ?: app.applicationContext)
        }
    }

    private suspend fun validateUserCanConnect(): Boolean {
        val user = userDataStore.userFlow.first() ?: return false
        val remainTraffic = user.remainTraffic.toDoubleOrNull() ?: 0.0

        val expiryType = when {
            remainTraffic <= 0 -> ExpiryType.VOLUME_EXPIRED
            user.isExpired || user.remainDays <= 0 -> ExpiryType.DATE_EXPIRED
            else -> null
        }

        if (expiryType != null) {
            DialogEventBus.emitExpiry(expiryType)
            return false
        }

        return true
    }

    private fun startTimer(startedAtMillis: Long = 0L) {
        val stableStartTime = startedAtMillis
            .takeIf { it > 0L }
            ?: System.currentTimeMillis()

        if (timerJob?.isActive == true) {
            _elapsedTime.value = (System.currentTimeMillis() - stableStartTime).coerceAtLeast(0L)
            return
        }

        _elapsedTime.value = (System.currentTimeMillis() - stableStartTime).coerceAtLeast(0L)
        timerJob = viewModelScope.launch {
            while (connectionState.value is ConnectionState.Connected) {
                _elapsedTime.value = (System.currentTimeMillis() - stableStartTime).coerceAtLeast(0L)
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _elapsedTime.value = 0L
    }

    fun formatElapsedTime(): String {
        val seconds = elapsedTime / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, secs)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0.0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }

    override fun onCleared() {
        super.onCleared()
        ipFetchJob?.cancel()
        timerJob?.cancel()
    }
}
