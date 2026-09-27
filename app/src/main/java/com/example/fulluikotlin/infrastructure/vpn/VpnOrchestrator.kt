package com.example.fulluikotlin.infrastructure.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.fulluikotlin.domain.`interface`.VpnController
import com.example.fulluikotlin.domain.model.ConnectionInfo
import com.example.fulluikotlin.domain.model.ConnectionState
import com.example.fulluikotlin.domain.model.ProtocolType
import com.example.fulluikotlin.domain.model.Server
import com.example.fulluikotlin.domain.model.TrafficSpeed
import com.example.fulluikotlin.infrastructure.service.NotificationHostService
import com.example.fulluikotlin.infrastructure.permission.NotificationPermissionHelper
import com.example.fulluikotlin.infrastructure.service.UserInfoUpdateService
import com.example.fulluikotlin.infrastructure.service.VpnOrchestratorHolder
import com.example.fulluikotlin.infrastructure.vpn.traffic.VpnUsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class VpnOrchestrator(
    private val appContext: Context,
    private val usageTracker: VpnUsageTracker
) : KoinComponent {
    private val factory: VpnAdapterFactory by inject()
    private var currentController: VpnController? = null
    private var currentProtocol: ProtocolType? = null
    private val orchestratorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val collectorJobs = mutableListOf<Job>()
    private var trafficCollectorJob: Job? = null
    private var connectingTimeoutJob: Job? = null
    private var connectionSessionId: Long = 0L

    private val connectionPrefs = appContext.getSharedPreferences(
        CONNECTION_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionStartedAtMillis = MutableStateFlow(loadPersistedConnectionStart())
    val connectionStartedAtMillis: StateFlow<Long> = _connectionStartedAtMillis.asStateFlow()

    private val _trafficSpeed = MutableStateFlow(TrafficSpeed())
    val trafficSpeed: StateFlow<TrafficSpeed> = _trafficSpeed.asStateFlow()

    val totalDownloadBytes: StateFlow<Long> = usageTracker.totalDownloadBytes
    val totalUploadBytes: StateFlow<Long> = usageTracker.totalUploadBytes

    private val _connectionInfo = MutableStateFlow<ConnectionInfo?>(null)
    val connectionInfo: StateFlow<ConnectionInfo?> = _connectionInfo.asStateFlow()

    init {
        val restoredStart = _connectionStartedAtMillis.value
        if (restoredStart > 0L) {
            Log.d("Orchestrator", "restored persisted connection start: $restoredStart")
        }

        VpnOrchestratorHolder.instance = this
        usageTracker.init()
        usageTracker.registerProtocolBroadcastListener()
        launchTrafficCollector()
    }

    private fun sendCommandToNotificationService(cmd: String, extras: Map<String, Any> = emptyMap()) {
        Log.d("Orchestrator", "sendCommand: $cmd")
        val intent = Intent(appContext, NotificationHostService::class.java).apply {
            action = cmd
            extras.forEach { (key, value) ->
                when (value) {
                    is String -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                    is Boolean -> putExtra(key, value)
                }
            }
        }
        appContext.startService(intent)
    }

    private fun startNotificationService(remark: String) {
        sendCommandToNotificationService("SHOW_CONNECTING", mapOf("remark" to remark))
    }

    private fun stopNotificationService() {
        sendCommandToNotificationService("STOP")
    }

    private fun notifyConnected(remark: String) {
        sendCommandToNotificationService("SHOW_CONNECTED", mapOf("remark" to remark))
    }

    private fun notifyTraffic(downloadBps: Long, uploadBps: Long, totalDownload: Long, totalUpload: Long) {
        if (_connectionState.value !is ConnectionState.Connected) return
        sendCommandToNotificationService(
            "UPDATE_TRAFFIC",
            mapOf(
                "downloadBps" to downloadBps,
                "uploadBps" to uploadBps,
                "totalDownload" to totalDownload,
                "totalUpload" to totalUpload
            )
        )
    }

    private fun launchTrafficCollector() {
        if (trafficCollectorJob?.isActive == true) return
        trafficCollectorJob = orchestratorScope.launch {
            usageTracker.trafficSpeed.collect { speed ->
                _trafficSpeed.value = speed
                notifyTraffic(
                    downloadBps = speed.downloadBps,
                    uploadBps = speed.uploadBps,
                    totalDownload = usageTracker.totalDownloadBytes.value,
                    totalUpload = usageTracker.totalUploadBytes.value
                )
            }
        }
    }

    /**
     * Puts the app in the visible Connecting state before the AUTH request is sent.
     * No VPN adapter is started here; the returned session id is later used to either
     * continue the same connection attempt or cancel it if AUTH fails / user cancels.
     */
    suspend fun prepareAuthConnectionState(
        server: Server,
        protocol: ProtocolType,
        activity: Activity? = null
    ): Long? {
        if (!NotificationPermissionHelper.ensurePermission(activity)) {
            Log.d("Orchestrator", "connect paused: notification permission is required")
            _connectionState.value = ConnectionState.Disconnected
            return null
        }

        disconnect(flushUsage = true)

        withContext(Dispatchers.Main) {
            delay(200)
        }

        val sessionId = ++connectionSessionId
        clearPersistedConnectionStart()
        _connectionStartedAtMillis.value = 0L
        _connectionState.value = ConnectionState.Connecting
        _trafficSpeed.value = TrafficSpeed()
        _connectionInfo.value = null
        startNotificationService(server.servername)
        Log.d(
            "Orchestrator",
            "prepared connecting state before AUTH: session=$sessionId protocol=$protocol server=${server.servername}"
        )
        return sessionId
    }

    fun isConnectionSessionActive(sessionId: Long): Boolean {
        return connectionSessionId == sessionId && _connectionState.value is ConnectionState.Connecting
    }

    suspend fun abortPreparedConnection(sessionId: Long, message: String) {
        if (connectionSessionId != sessionId) {
            Log.d("Orchestrator", "prepared connection abort ignored for stale session=$sessionId")
            return
        }

        cancelConnectingTimeoutWatch()
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        currentController?.disconnect()
        currentController?.release()
        currentController = null
        currentProtocol = null
        usageTracker.stopRuntimeAccounting()
        _connectionStartedAtMillis.value = 0L
        clearPersistedConnectionStart()
        _trafficSpeed.value = TrafficSpeed()
        _connectionInfo.value = null
        UserInfoUpdateService.stop(appContext)
        stopNotificationService()
        _connectionState.value = ConnectionState.Error(message)
        Log.w("Orchestrator", "prepared connection aborted: $message")
    }

    suspend fun selectAndConnect(
        server: Server,
        protocol: ProtocolType,
        activity: Activity? = null,
        preparedSessionId: Long? = null
    ) {
        val sessionId = if (preparedSessionId != null && isConnectionSessionActive(preparedSessionId)) {
            Log.d(
                "Orchestrator",
                "continuing prepared connection after AUTH: session=$preparedSessionId protocol=$protocol server=${server.servername}"
            )
            preparedSessionId
        } else {
            if (!NotificationPermissionHelper.ensurePermission(activity)) {
                Log.d("Orchestrator", "connect paused: notification permission is required")
                _connectionState.value = ConnectionState.Disconnected
                return
            }

            disconnect(flushUsage = true)

            withContext(Dispatchers.Main) {
                delay(200)
            }

            val newSessionId = ++connectionSessionId
            clearPersistedConnectionStart()
            _connectionStartedAtMillis.value = 0L
            _connectionState.value = ConnectionState.Connecting
            _trafficSpeed.value = TrafficSpeed()
            _connectionInfo.value = null
            startNotificationService(server.servername)
            newSessionId
        }

        usageTracker.startRuntimeAccounting(protocol.name)
        UserInfoUpdateService.start(appContext)

        val newController = factory.create(protocol)
        currentController = newController
        currentProtocol = protocol
        launchCollectors(newController, server.servername)
        startConnectingTimeoutWatch(sessionId, server.servername)

        runCatching {
            newController.connect(server, protocol, activity)
        }.onFailure { throwable ->
            if (connectionSessionId == sessionId) {
                Log.e("Orchestrator", "connect failed: protocol=$protocol server=${server.servername}", throwable)
                cancelConnectingTimeoutWatch()
                _connectionState.value = ConnectionState.Error(throwable.message ?: "VPN connection failed")
                usageTracker.stopRuntimeAccounting()
                UserInfoUpdateService.stop(appContext)
                stopNotificationService()
            }
            runCatching { newController.disconnect() }
        }

        if (connectionSessionId != sessionId) {
            Log.d("Orchestrator", "connect result ignored because session was cancelled: server=${server.servername}")
            runCatching { newController.disconnect() }
            runCatching { newController.release() }
        }
    }

    private fun launchCollectors(controller: VpnController, serverName: String) {
        collectorJobs += orchestratorScope.launch {
            controller.connectionState.collect { state ->
                when (state) {
                    ConnectionState.Connected -> {
                        if (_connectionStartedAtMillis.value <= 0L) {
                            val startedAt = loadPersistedConnectionStart().takeIf { it > 0L }
                                ?: System.currentTimeMillis()

                            _connectionStartedAtMillis.value = startedAt
                            persistConnectionStart(startedAt)
                        }
                    }
                    ConnectionState.Disconnected, is ConnectionState.Error -> {
                        _connectionStartedAtMillis.value = 0L
                        clearPersistedConnectionStart()
                    }
                    ConnectionState.Connecting -> Unit
                }

                _connectionState.value = state

                when (state) {
                    ConnectionState.Connected -> {
                        cancelConnectingTimeoutWatch()
                        notifyConnected(serverName)
                    }
                    ConnectionState.Connecting -> Unit
                    ConnectionState.Disconnected -> {
                        cancelConnectingTimeoutWatch()
                        if (currentController === controller) {
                            usageTracker.stopRuntimeAccounting()
                            UserInfoUpdateService.stop(appContext)
                            stopNotificationService()
                        }
                    }
                    is ConnectionState.Error -> {
                        cancelConnectingTimeoutWatch()
                        if (currentController === controller) {
                            usageTracker.stopRuntimeAccounting()
                            UserInfoUpdateService.stop(appContext)
                            stopNotificationService()
                        }
                    }
                }
            }
        }
        collectorJobs += orchestratorScope.launch {
            controller.connectionInfo.collect { info -> _connectionInfo.value = info }
        }
    }


    private fun startConnectingTimeoutWatch(sessionId: Long, serverName: String) {
        cancelConnectingTimeoutWatch()
        connectingTimeoutJob = orchestratorScope.launch {
            delay(CONNECTING_TIMEOUT_MS)
            if (connectionSessionId == sessionId && _connectionState.value is ConnectionState.Connecting) {
                Log.w("Orchestrator", "connection timeout while connecting: server=$serverName")
                launch { disconnect(flushUsage = true) }
            }
        }
    }

    private fun cancelConnectingTimeoutWatch() {
        connectingTimeoutJob?.cancel()
        connectingTimeoutJob = null
    }

    suspend fun disconnect(flushUsage: Boolean = true) {
        cancelConnectingTimeoutWatch()
        connectionSessionId++
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        currentController?.disconnect()
        currentController?.release()
        usageTracker.stopRuntimeAccounting()
        currentController = null
        currentProtocol = null
        _connectionState.value = ConnectionState.Disconnected
        _connectionStartedAtMillis.value = 0L
        clearPersistedConnectionStart()
        _trafficSpeed.value = TrafficSpeed()
        _connectionInfo.value = null
        if (flushUsage) {
            usageTracker.flushUsageAndClear(reason = "orchestrator_disconnect", enforceExpiry = false)
        }
        UserInfoUpdateService.stop(appContext)
        stopNotificationService()
    }

    private fun loadPersistedConnectionStart(): Long {
        return connectionPrefs.getLong(CONNECTION_STARTED_AT_KEY, 0L)
    }

    private fun persistConnectionStart(startedAtMillis: Long) {
        connectionPrefs.edit()
            .putLong(CONNECTION_STARTED_AT_KEY, startedAtMillis)
            .apply()
    }

    private fun clearPersistedConnectionStart() {
        connectionPrefs.edit()
            .remove(CONNECTION_STARTED_AT_KEY)
            .apply()
    }

    fun release() {
        cancelConnectingTimeoutWatch()
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        trafficCollectorJob?.cancel()
        trafficCollectorJob = null
        currentController?.release()
        orchestratorScope.cancel()
        VpnOrchestratorHolder.instance = null
    }

    companion object {
        private const val CONNECTING_TIMEOUT_MS = 45_000L
        private const val CONNECTION_PREFS_NAME = "vpn_connection_timer"
        private const val CONNECTION_STARTED_AT_KEY = "connection_started_at_millis"
    }

    fun disconnectFromNotification() {
        orchestratorScope.launch { disconnect(flushUsage = true) }
    }
}
