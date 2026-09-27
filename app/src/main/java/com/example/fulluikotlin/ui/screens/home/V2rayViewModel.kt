package com.example.fulluikotlin.ui.screens.home

// V2rayViewModel.kt
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dev7.lib.v2ray.V2rayController
import dev.dev7.lib.v2ray.services.V2rayProxyService
import dev.dev7.lib.v2ray.services.V2rayVPNService
import dev.dev7.lib.v2ray.utils.V2rayConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.fulluikotlin.data.local.datastore.ServerDataStore
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.ExpiryType
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.domain.usecase.servers.ServersUseCase
import com.example.fulluikotlin.infrastructure.service.CustomNotificationProvider
import com.example.fulluikotlin.infrastructure.service.UserInfoUpdateService
import com.example.fulluikotlin.infrastructure.permission.NotificationPermissionHelper
import com.example.fulluikotlin.infrastructure.vpn.splittunnel.SplitTunnelPackageResolver
import com.example.fulluikotlin.ui.utils.Crypto
import com.example.fulluikotlin.ui.utils.DialogEventBus
import java.util.Locale


class V2rayViewModel(
    private val splitTunnelDataStore: SplitTunnelDataStore,
    private val serverDataStore: ServerDataStore,
    private val serversUseCase: ServersUseCase,
    private val userDataStore: UserDataStore,
) : ViewModel() {

    private val telemetryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Connection state
    private val _serverSelected = mutableStateOf<Server?>(null)
    val serverSelected: Server? get() = _serverSelected.value

    private val _protocolSelected = mutableStateOf<ProtocolType?>(null)
    val protocolSelected: ProtocolType? get() = _protocolSelected.value

    // Connection state
    private val _connectionState = MutableStateFlow(V2rayConstants.CONNECTION_STATES.DISCONNECTED)
    val connectionState: StateFlow<V2rayConstants.CONNECTION_STATES> = _connectionState

    // Elapsed time (milliseconds)
    private val _elapsedTime = mutableStateOf(0L)
    val elapsedTime: Long get() = _elapsedTime.value

    // Traffic stats
    private val _downloadSpeed = mutableStateOf("0.0 B/s")
    val downloadSpeed: String get() = _downloadSpeed.value

    private val _uploadSpeed = mutableStateOf("0.0 B/s")
    val uploadSpeed: String get() = _uploadSpeed.value

    private val _downloadedTotal = mutableStateOf("0.0 B")
    val downloadedTotal: String get() = _downloadedTotal.value

    private val _uploadedTotal = mutableStateOf("0.0 B")
    val uploadedTotal: String get() = _uploadedTotal.value

    // Timer job for elapsed time
    private var timerJob: Job? = null

    // Broadcast receiver to receive stats from service
    private var statsReceiver: BroadcastReceiver? = null

    private val _publicIp = MutableStateFlow<String?>(null)
    val publicIp = _publicIp.asStateFlow()

    private val _isIpLoading = MutableStateFlow(false)
    val isIpLoading = _isIpLoading.asStateFlow()

    private var ipFetchJob: Job? = null

    init {
        viewModelScope.launch {
            serverDataStore.selectedServerFlow.collect { server ->
                _serverSelected.value = server
            }
        }
        viewModelScope.launch {
            serverDataStore.selectedProtocolFlow.collect { protocol ->
                _protocolSelected.value = protocol
            }
        }
        // Periodically check connection state (fallback if broadcast is missed)
        viewModelScope.launch {
            while (true) {
                val state = V2rayController.getConnectionState()
                if (_connectionState.value != state) {
                    _connectionState.value = state
                    if (state == V2rayConstants.CONNECTION_STATES.CONNECTED) startTimer()
                    else stopTimer()
                }
                delay(500)
            }
        }

        viewModelScope.launch {
            _connectionState.collectLatest { state ->
                when (state) {
                    V2rayConstants.CONNECTION_STATES.CONNECTED -> {
                        ipFetchJob?.cancel()
                        _publicIp.value = null
                        _isIpLoading.value = false
                    }

                    else -> {
                        ipFetchJob?.cancel()
                        _publicIp.value = null
                        _isIpLoading.value = false
                    }
                }
            }
        }
    }

    /**
     * Register the broadcast receiver to listen for traffic stats and connection updates.
     * Call this in your Activity/Composable's onResume or with a lifecycle-aware component.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun registerStatsReceiver(context: Context) {
        if (statsReceiver != null) return
        statsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT) {
                    val downSpeed =
                        intent.getStringExtra(V2rayConstants.SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA)
                            ?: "0.0 B/s"
                    val upSpeed =
                        intent.getStringExtra(V2rayConstants.SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA)
                            ?: "0.0 B/s"
                    val downTotal =
                        intent.getStringExtra(V2rayConstants.SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA)
                            ?: "0.0 B"
                    val upTotal =
                        intent.getStringExtra(V2rayConstants.SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA)
                            ?: "0.0 B"

                    _downloadSpeed.value = downSpeed
                    _uploadSpeed.value = upSpeed
                    _downloadedTotal.value = downTotal
                    _uploadedTotal.value = upTotal

                    val stateExtra =
                        intent.getSerializableExtra(V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA)
                    if (stateExtra is V2rayConstants.CONNECTION_STATES && _connectionState.value != stateExtra) {
                        _connectionState.value = stateExtra
                        if (stateExtra == V2rayConstants.CONNECTION_STATES.CONNECTED) startTimer()
                        else if (stateExtra == V2rayConstants.CONNECTION_STATES.DISCONNECTED) stopTimer()
                    }
                }
            }
        }
        val filter = IntentFilter(V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Context.RECEIVER_EXPORTED
        } else {
            0
        }
        context.registerReceiver(statsReceiver, filter, flags)
    }

    /**
     * Unregister the broadcast receiver. Call this in onPause or onDestroy.
     */
    fun unregisterStatsReceiver(context: Context) {
        statsReceiver?.let { context.unregisterReceiver(it) }
        statsReceiver = null
    }

    private fun startTimer() {
        stopTimer()
        _elapsedTime.value = 0L
        timerJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            while (_connectionState.value == V2rayConstants.CONNECTION_STATES.CONNECTED) {
                _elapsedTime.value = System.currentTimeMillis() - startTime
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _elapsedTime.value = 0L
    }

    /**
     * Legacy direct V2Ray path. The actual split-tunnel rule is intentionally delegated to
     * SplitTunnelPackageResolver so it stays identical for every VPN protocol.
     */
    private suspend fun getCombinedBlockedAppsList(activity: Activity): ArrayList<String> {
        return SplitTunnelPackageResolver.resolveDisallowedPackages(
            context = activity,
            splitTunnelDataStore = splitTunnelDataStore
        )
    }

    suspend fun connect(activity: Activity, server: Server?) {

        val user = userDataStore.userFlow.first() ?: return
        val remainTraffic = user.remainTraffic.toDoubleOrNull() ?: 0.0

        val expiryType = when {
            remainTraffic <= 0 -> ExpiryType.VOLUME_EXPIRED
            user.isExpired || user.remainDays <= 0 -> ExpiryType.DATE_EXPIRED
            else -> null
        }

        if (expiryType != null) {
            viewModelScope.launch {
                DialogEventBus.emitExpiry(expiryType)
            }
            return
        }

        if (!NotificationPermissionHelper.ensurePermission(activity)) {
            Log.d("V2rayViewModel", "connect paused: notification permission is required")
            return
        }

        if (_connectionState.value != V2rayConstants.CONNECTION_STATES.DISCONNECTED) return
        _connectionState.value = V2rayConstants.CONNECTION_STATES.CONNECTING
        // Reset stats
        _downloadSpeed.value = "0.0 B/s"
        _uploadSpeed.value = "0.0 B/s"
        _downloadedTotal.value = "0.0 B"
        _uploadedTotal.value = "0.0 B"

        telemetryScope.launch {
            serversUseCase(
                protocol = server?.protocol?.name,
                server = server?.serverid
            )
        }

        val blockedApps = getCombinedBlockedAppsList(activity)
        /*val provider = CustomNotificationProvider(activity)
        V2rayVPNService.setCustomNotificationProvider(provider)*/
//        V2rayProxyService.setCustomNotificationProvider(provider)
        V2rayController.startV2ray(
            activity,
            server?.servername,
            Crypto.decryptThis(server?.config),
            blockedApps
        )
        UserInfoUpdateService.start(activity)
    }

    fun disconnect(context: Context) {
        if (_connectionState.value == V2rayConstants.CONNECTION_STATES.DISCONNECTED) return
        V2rayController.stopV2ray(context)
        UserInfoUpdateService.stop(context)
        // The broadcast will update connection state to DISCONNECTED and clear stats
    }

    fun formatElapsedTime(): String {
        val seconds = elapsedTime / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format(Locale.US,"%02d:%02d:%02d", hours, minutes, secs)
    }
}
