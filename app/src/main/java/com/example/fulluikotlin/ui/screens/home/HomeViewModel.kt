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
import com.example.fulluikotlin.domain.utils.AppErrorClassifier
import com.example.fulluikotlin.domain.utils.AppErrorCode
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.infrastructure.permission.NotificationPermissionHelper
import com.example.fulluikotlin.infrastructure.reporting.FirebaseErrorReporter
import com.example.fulluikotlin.infrastructure.service.UserInfoUpdateService
import com.example.fulluikotlin.infrastructure.vpn.VpnOrchestrator
import com.example.fulluikotlin.ui.utils.Crypto
import com.example.fulluikotlin.ui.utils.DialogEventBus
import dev.dev7.lib.v2ray.V2rayController
import dev.dev7.lib.v2ray.interfaces.RealDelayTestListener
import dev.dev7.lib.v2ray.model.RealDelayTestConfig
import dev.dev7.lib.v2ray.model.RealDelayTestHandle
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SmartServerSearchState {
    data object Idle : SmartServerSearchState

    data class Searching(
        val completed: Int,
        val total: Int,
        val bestServerName: String? = null,
        val bestDelayMs: Long = -1L,
        val isCancelling: Boolean = false,
        val connectingToCurrentBest: Boolean = false
    ) : SmartServerSearchState

    data class Error(val message: String) : SmartServerSearchState
}

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

    private val _smartResolvedServer = mutableStateOf<Server?>(null)
    val smartResolvedServer: Server? get() = _smartResolvedServer.value

    private val _isSmartServerSelection = MutableStateFlow(false)
    val isSmartServerSelection = _isSmartServerSelection.asStateFlow()

    private val _selectedProtocol = MutableStateFlow<ProtocolType?>(null)
    val selectedProtocol = _selectedProtocol.asStateFlow()

    private val _smartSearchState = MutableStateFlow<SmartServerSearchState>(SmartServerSearchState.Idle)
    val smartSearchState = _smartSearchState.asStateFlow()

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
    private var smartDelayHandle: RealDelayTestHandle? = null
    private var smartCancelFallbackJob: Job? = null
    private var smartBestServer: Server? = null
    private var pendingSmartServerConnection: Server? = null
    private var smartScanGeneration = 0L

    init {
        viewModelScope.launch {
            serverDataStore.selectedServerFlow.collect { server ->
                _serverSelected.value = server
            }
        }
        viewModelScope.launch {
            serverDataStore.selectedProtocolFlow.collect { protocol ->
                _selectedProtocol.value = protocol
            }
        }
        viewModelScope.launch {
            combine(
                serverDataStore.smartServerSelectionFlow,
                serverDataStore.smartResolvedServerFlow
            ) { enabled, resolvedServer -> enabled to resolvedServer }
                .collect { (enabled, resolvedServer) ->
                    _isSmartServerSelection.value = enabled
                    _smartResolvedServer.value = resolvedServer.takeIf { enabled }
                    if (!enabled) {
                        cancelSmartSearchInternal(clearDialog = true)
                    }
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

    fun registerStatsReceiver(context: Context) = Unit

    fun unregisterStatsReceiver(context: Context) = Unit

    fun connect(activity: Activity, server: Server?) {
        viewModelScope.launch {
            val currentState = connectionState.value
            if (currentState is ConnectionState.Connected || currentState is ConnectionState.Connecting) {
                return@launch
            }

            if (serverDataStore.isSmartServerSelection()) {
                val protocol = serverDataStore.getSelectedProtocol()
                if (protocol == null) {
                    showUserMessage("پروتکل اتصال مشخص نیست. دوباره سرور را انتخاب کنید.")
                    return@launch
                }

                if (protocol == ProtocolType.V2RAY) {
                    startV2raySmartSearch(activity)
                } else {
                    connectRandomSmartServer(activity, protocol)
                }
                return@launch
            }

            val targetServer = server ?: serverSelected
            if (targetServer == null) {
                Log.w(TAG, "connect skipped: no selected server")
                showUserMessage("ابتدا یک سرور انتخاب کنید.")
                return@launch
            }
            connectResolvedServer(targetServer, targetServer.protocol, activity)
        }
    }

    fun connect(server: Server, protocol: ProtocolType, activity: Activity) {
        viewModelScope.launch {
            connectResolvedServer(server, protocol, activity)
        }
    }

    private suspend fun connectRandomSmartServer(activity: Activity, protocol: ProtocolType) {
        val candidates = serverDataStore.getServers().filter { it.protocol == protocol }
        if (candidates.isEmpty()) {
            showUserMessage("برای این پروتکل سروری در دسترس نیست.")
            return
        }

        val selected = candidates.random()
        _smartResolvedServer.value = selected
        connectResolvedServer(selected, protocol, activity)
    }

    private suspend fun startV2raySmartSearch(activity: Activity) {
        if (_smartSearchState.value is SmartServerSearchState.Searching) return

        if (!NotificationPermissionHelper.ensurePermission(activity)) {
            showUserMessage("برای اتصال VPN باید اجازه نمایش نوتیفیکیشن را فعال کنید.")
            return
        }

        val candidates = serverDataStore.getServers()
            .filter { it.protocol == ProtocolType.V2RAY && it.config.isNotBlank() }

        if (candidates.isEmpty()) {
            _smartSearchState.value = SmartServerSearchState.Error(
                "در حال حاضر سرور V2Ray قابل بررسی وجود ندارد."
            )
            return
        }

        cancelSmartSearchInternal(clearDialog = false)
        val generation = ++smartScanGeneration
        smartBestServer = null
        pendingSmartServerConnection = null
        _smartResolvedServer.value = null

        val serverById = candidates.associateBy { it.serverid }
        var invalidConfigCount = 0
        val testConfigs = candidates.mapNotNull { server ->
            val decrypted = runCatching { Crypto.decryptThis(server.config).trim() }
                .onFailure {
                    invalidConfigCount++
                    Log.w(TAG, "smart scan config decode failed serverId=${server.serverid}", it)
                }
                .getOrNull()

            decrypted?.takeIf { it.isNotBlank() }?.let {
                RealDelayTestConfig(server.serverid, it)
            } ?: run {
                if (decrypted != null) invalidConfigCount++
                null
            }
        }

        if (testConfigs.isEmpty()) {
            reportSmartScanFailure(
                stage = "config_prepare",
                message = "No valid V2Ray configs",
                totalServers = candidates.size,
                invalidConfigs = invalidConfigCount
            )
            _smartSearchState.value = SmartServerSearchState.Error(
                AppErrorCode.SMART_SERVER_SCAN_FAILED.displayMessage()
            )
            return
        }

        _smartSearchState.value = SmartServerSearchState.Searching(
            completed = 0,
            total = testConfigs.size
        )

        smartDelayHandle = runCatching {
            V2rayController.startRealDelayTest(
            app.applicationContext,
            testConfigs,
            SMART_DELAY_MAX_CONCURRENCY,
            object : RealDelayTestListener {
                override fun onProgress(
                    completed: Int,
                    total: Int,
                    testedServerId: String,
                    testedDelayMs: Long,
                    bestServerId: String,
                    bestDelayMs: Long
                ) {
                    viewModelScope.launch {
                        if (generation != smartScanGeneration) return@launch
                        val best = serverById[bestServerId]
                        if (best != null) smartBestServer = best
                        val previous = _smartSearchState.value as? SmartServerSearchState.Searching
                        _smartSearchState.value = SmartServerSearchState.Searching(
                            completed = completed.coerceAtMost(total),
                            total = total,
                            bestServerName = best?.servername,
                            bestDelayMs = bestDelayMs,
                            isCancelling = previous?.isCancelling == true,
                            connectingToCurrentBest = previous?.connectingToCurrentBest == true
                        )
                    }
                }

                override fun onCompleted(bestServerId: String, bestDelayMs: Long) {
                    viewModelScope.launch {
                        if (generation != smartScanGeneration) return@launch
                        smartCancelFallbackJob?.cancel()
                        smartCancelFallbackJob = null
                        smartDelayHandle = null
                        val best = serverById[bestServerId] ?: smartBestServer
                        smartBestServer = best
                        if (best == null) {
                            reportSmartScanFailure(
                                stage = "result_mapping",
                                message = "Best server id was not found in the current list",
                                totalServers = testConfigs.size,
                                invalidConfigs = invalidConfigCount
                            )
                            _smartSearchState.value = SmartServerSearchState.Error(
                                AppErrorCode.SMART_SERVER_SCAN_FAILED.displayMessage()
                            )
                            return@launch
                        }

                        _smartResolvedServer.value = best
                        _smartSearchState.value = SmartServerSearchState.Idle
                        connectResolvedServer(best, ProtocolType.V2RAY, activity)
                    }
                }

                override fun onCancelled() {
                    viewModelScope.launch {
                        if (generation != smartScanGeneration) return@launch
                        smartCancelFallbackJob?.cancel()
                        smartCancelFallbackJob = null
                        smartDelayHandle = null
                        val serverToConnect = pendingSmartServerConnection
                        pendingSmartServerConnection = null
                        _smartSearchState.value = SmartServerSearchState.Idle
                        if (serverToConnect != null) {
                            _smartResolvedServer.value = serverToConnect
                            connectResolvedServer(serverToConnect, ProtocolType.V2RAY, activity)
                        }
                    }
                }

                override fun onError(message: String) {
                    viewModelScope.launch {
                        if (generation != smartScanGeneration) return@launch
                        smartCancelFallbackJob?.cancel()
                        smartCancelFallbackJob = null
                        smartDelayHandle = null
                        pendingSmartServerConnection = null
                        Log.e(TAG, "smart server real-delay test failed: $message")
                        reportSmartScanFailure(
                            stage = "core_test",
                            message = message,
                            totalServers = testConfigs.size,
                            invalidConfigs = invalidConfigCount
                        )
                        _smartSearchState.value = SmartServerSearchState.Error(
                            AppErrorCode.SMART_SERVER_SCAN_FAILED.displayMessage()
                        )
                    }
                }
            }
            )
        }.onFailure { throwable ->
            Log.e(TAG, "Unable to start smart server scan", throwable)
            reportSmartScanFailure(
                stage = "start",
                message = throwable.message,
                throwable = throwable,
                totalServers = testConfigs.size,
                invalidConfigs = invalidConfigCount
            )
        }.getOrNull()

        if (smartDelayHandle == null && _smartSearchState.value is SmartServerSearchState.Searching) {
            _smartSearchState.value = SmartServerSearchState.Error(
                AppErrorCode.SMART_SERVER_SCAN_FAILED.displayMessage()
            )
        }
    }

    fun cancelSmartSearch() {
        pendingSmartServerConnection = null
        val current = _smartSearchState.value as? SmartServerSearchState.Searching
        if (current != null) {
            _smartSearchState.value = current.copy(
                isCancelling = true,
                connectingToCurrentBest = false
            )
        }

        val handle = smartDelayHandle
        if (handle == null) {
            _smartSearchState.value = SmartServerSearchState.Idle
            return
        }
        handle.cancel()
        scheduleSmartCancelFallback(activity = null, serverToConnect = null)
    }

    fun connectToCurrentBest(activity: Activity) {
        val best = smartBestServer ?: return
        pendingSmartServerConnection = best
        val current = _smartSearchState.value as? SmartServerSearchState.Searching
        if (current != null) {
            _smartSearchState.value = current.copy(
                isCancelling = true,
                connectingToCurrentBest = true
            )
        }

        val handle = smartDelayHandle
        if (handle == null) {
            pendingSmartServerConnection = null
            _smartResolvedServer.value = best
            _smartSearchState.value = SmartServerSearchState.Idle
            viewModelScope.launch {
                connectResolvedServer(best, ProtocolType.V2RAY, activity)
            }
            return
        }
        handle.cancel()
        scheduleSmartCancelFallback(activity = activity, serverToConnect = best)
    }

    private fun scheduleSmartCancelFallback(activity: Activity?, serverToConnect: Server?) {
        val generation = smartScanGeneration
        smartCancelFallbackJob?.cancel()
        smartCancelFallbackJob = viewModelScope.launch {
            delay(SMART_CANCEL_FALLBACK_MS)
            val state = _smartSearchState.value as? SmartServerSearchState.Searching
                ?: return@launch
            if (!state.isCancelling || generation != smartScanGeneration) return@launch

            // The in-process cancellation flag has already stopped new work. This watchdog only
            // prevents the UI from remaining blocked if Android drops the terminal broadcast.
            smartScanGeneration++
            smartDelayHandle?.release()
            smartDelayHandle = null
            pendingSmartServerConnection = null
            _smartSearchState.value = SmartServerSearchState.Idle

            if (serverToConnect != null && activity != null && !activity.isFinishing && !activity.isDestroyed) {
                _smartResolvedServer.value = serverToConnect
                connectResolvedServer(serverToConnect, ProtocolType.V2RAY, activity)
            }
        }
    }

    fun retrySmartSearch(activity: Activity) {
        viewModelScope.launch {
            _smartSearchState.value = SmartServerSearchState.Idle
            startV2raySmartSearch(activity)
        }
    }

    fun dismissSmartSearchError() {
        if (_smartSearchState.value is SmartServerSearchState.Error) {
            _smartSearchState.value = SmartServerSearchState.Idle
        }
    }

    private fun cancelSmartSearchInternal(clearDialog: Boolean) {
        smartScanGeneration++
        smartCancelFallbackJob?.cancel()
        smartCancelFallbackJob = null
        pendingSmartServerConnection = null
        smartBestServer = null
        smartDelayHandle?.cancel()
        smartDelayHandle?.release()
        smartDelayHandle = null
        if (clearDialog) _smartSearchState.value = SmartServerSearchState.Idle
    }

    private suspend fun connectResolvedServer(
        server: Server,
        protocol: ProtocolType,
        activity: Activity
    ) {
        Log.d(TAG, "connect resolved: server=${server.servername}, protocol=$protocol")
        val currentState = connectionState.value
        if (currentState is ConnectionState.Connected || currentState is ConnectionState.Connecting) return

        if (!NotificationPermissionHelper.ensurePermission(activity)) {
            showUserMessage("برای اتصال VPN باید اجازه نمایش نوتیفیکیشن را فعال کنید.")
            Log.d(TAG, "connect paused: notification permission is required")
            return
        }

        _downloadedTotal.value = "0.0 B"
        _uploadedTotal.value = "0.0 B"

        val preparedSessionId = orchestrator.prepareAuthConnectionState(server, protocol, activity)
            ?: return

        val authResult = authenticateBeforeConnect(preparedSessionId)
        if (!orchestrator.isConnectionSessionActive(preparedSessionId)) {
            Log.d(TAG, "AUTH result ignored because connection attempt was cancelled")
            return
        }

        when (authResult) {
            is Resource.Success -> {
                val canConnect = validateUserCanConnect()
                if (!canConnect) {
                    orchestrator.abortPreparedConnection(
                        preparedSessionId,
                        "اکانت شما امکان اتصال ندارد"
                    )
                    return
                }

                val latestServer = resolveLatestServerAfterAuth(server)
                val latestProtocol = latestServer.protocol
                val wasAutomaticSelection = serverDataStore.isSmartServerSelection()
                if (wasAutomaticSelection) {
                    // Automatic selection is a one-time resolver. The resolved server now becomes
                    // the application's real selected server, so reconnect and process recreation
                    // use this exact server instead of starting another scan/random selection.
                    serverDataStore.promoteSmartServerToSelected(latestServer)
                    _serverSelected.value = latestServer.copy(isActive = true)
                    _isSmartServerSelection.value = false
                    _smartResolvedServer.value = null
                }

                runCatching {
                    serversUseCase(
                        protocol = latestProtocol.name,
                        server = latestServer.serverid
                    )
                }.onFailure {
                    Log.e(TAG, "server usage telemetry failed", it)
                }

                orchestrator.selectAndConnect(
                    server = latestServer,
                    protocol = latestProtocol,
                    activity = activity,
                    preparedSessionId = preparedSessionId
                )
            }

            is Resource.Error -> abortAuthConnection(preparedSessionId, authResult.message)

            is Resource.MultiLoginError -> abortAuthConnection(
                preparedSessionId,
                AppErrorCode.AUTH_DEVICE_LIMIT.displayMessage()
            )

            is Resource.Exception -> {
                val errorCode = AppErrorClassifier.classify(authResult.throwable)
                val supportCode = AppErrorClassifier.supportCode(authResult.throwable, errorCode)
                FirebaseErrorReporter.report(
                    errorCode = errorCode,
                    supportCode = supportCode,
                    context = "connect_auth",
                    throwable = authResult.throwable
                )
                abortAuthConnection(
                    preparedSessionId,
                    errorCode.displayMessage(supportCode)
                )
            }

            is Resource.Loading -> Unit
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
            val errorCode = AppErrorCode.SESSION_INCOMPLETE
            Log.w(TAG, "AUTH before connect skipped: missing credentials session=$sessionId")
            FirebaseErrorReporter.report(
                errorCode = errorCode,
                context = "connect_auth_missing_credentials"
            )
            return Resource.Error(
                message = errorCode.displayMessage(),
                supportCode = errorCode.code
            )
        }

        Log.d(TAG, "AUTH before connect started: session=$sessionId")
        val result = loginUseCase(username, password, "android", device)

        if (result is Resource.MultiLoginError) {
            userDataStore.saveMultiLoginDevices(result.devices)
            userDataStore.saveMultiLoginCredentials(username, password, device)
        }

        when (result) {
            is Resource.Success -> Log.d(TAG, "AUTH before connect success: session=$sessionId")
            is Resource.Error -> Log.w(TAG, "AUTH before connect error: session=$sessionId message=${result.message}")
            is Resource.MultiLoginError -> Log.w(TAG, "AUTH before connect multi-login: session=$sessionId devices=${result.devices.size}")
            is Resource.Exception -> Log.e(TAG, "AUTH before connect exception: session=$sessionId", result.throwable)
            is Resource.Loading -> Unit
        }

        return result
    }

    private suspend fun resolveLatestServerAfterAuth(requestedServer: Server): Server {
        val selectedServer = serverDataStore.getSelectedServer()
        if (selectedServer?.serverid == requestedServer.serverid) return selectedServer

        val latestServers = serverDataStore.getServers()
        val updatedServer = latestServers.firstOrNull { it.serverid == requestedServer.serverid }

        return updatedServer
            ?: selectedServer
            ?: latestServers.firstOrNull { it.protocol == requestedServer.protocol }
            ?: requestedServer
    }

    private suspend fun abortAuthConnection(sessionId: Long, message: String) {
        orchestrator.abortPreparedConnection(sessionId, message)
        showUserMessage(message)
    }

    fun disconnect(context: Context? = null) {
        viewModelScope.launch {
            orchestrator.disconnect()
            if (serverDataStore.isSmartServerSelection()) {
                serverDataStore.clearSmartResolvedServer()
                _smartResolvedServer.value = null
            }
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

    private fun reportSmartScanFailure(
        stage: String,
        message: String?,
        throwable: Throwable? = null,
        totalServers: Int,
        invalidConfigs: Int
    ) {
        FirebaseErrorReporter.report(
            errorCode = AppErrorCode.SMART_SERVER_SCAN_FAILED,
            context = "smart_server_scan_$stage",
            throwable = throwable,
            details = mapOf(
                "stage" to stage,
                "server_count" to totalServers.toString(),
                "invalid_config_count" to invalidConfigs.toString(),
                "core_message" to message
            )
        )
    }

    private fun showUserMessage(message: String) {
        Toast.makeText(app.applicationContext, message, Toast.LENGTH_SHORT).show()
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
        cancelSmartSearchInternal(clearDialog = true)
        ipFetchJob?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val SMART_DELAY_MAX_CONCURRENCY = 6
        const val SMART_CANCEL_FALLBACK_MS = 2500L
    }
}
