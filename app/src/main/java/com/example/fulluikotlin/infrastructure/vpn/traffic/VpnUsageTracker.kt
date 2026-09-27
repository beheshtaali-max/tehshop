package com.example.fulluikotlin.infrastructure.vpn.traffic

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.ConnectionState
import com.example.fulluikotlin.domain.model.ExpiryType
import com.example.fulluikotlin.domain.model.TrafficSpeed
import com.example.fulluikotlin.domain.usecase.auth.UsageUseCase
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.infrastructure.service.VpnOrchestratorHolder
import com.example.fulluikotlin.ui.utils.DialogEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.util.Locale

/**
 * The single source of truth for VPN traffic accounting.
 *
 * Responsibilities:
 * 1. init(): load persisted unsent traffic and make sure the protocol listener exists.
 * 2. registerProtocolBroadcastListener(): listen to the normalized broadcast emitted by every protocol core.
 * 3. flushUsageAndClear(): send all locally accumulated bytes to ACTION=USAGE and clear them only on success.
 * 4. startPeriodicUsageUpload(): flush usage using the server-provided usage period.
 */
class VpnUsageTracker(
    context: Context
) : KoinComponent {

    private val appContext = context.applicationContext
    private val usageUseCase: UsageUseCase by inject()
    private val userDataStore: UserDataStore by inject()
    private val appDetailsDataStore: AppDetailsDataStore by inject()

    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val flushMutex = Mutex()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val prefsLock = Any()

    private var receiverRegistered = false
    private var periodicJob: Job? = null
    private var runtimeAccountingJob: Job? = null
    private var initialized = false

    private var runtimeProtocol: String? = null
    private var runtimeLastSnapshot: InterfaceTrafficSnapshot? = null
    private var runtimeSessionDownloadBytes: Long = 0L
    private var runtimeSessionUploadBytes: Long = 0L
    private var runtimeInterfaceCounterAvailable: Boolean = false

    private val _protocolState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val protocolState: StateFlow<ConnectionState> = _protocolState.asStateFlow()

    private val _trafficSpeed = MutableStateFlow(TrafficSpeed())
    val trafficSpeed: StateFlow<TrafficSpeed> = _trafficSpeed.asStateFlow()

    private val _totalDownloadBytes = MutableStateFlow(0L)
    val totalDownloadBytes: StateFlow<Long> = _totalDownloadBytes.asStateFlow()

    private val _totalUploadBytes = MutableStateFlow(0L)
    val totalUploadBytes: StateFlow<Long> = _totalUploadBytes.asStateFlow()

    private val protocolTrafficReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != VpnTrafficConstants.ACTION_PROTOCOL_TRAFFIC) return
            handleProtocolTraffic(intent.toProtocolTrafficEvent())
        }
    }

    fun init() {
        if (initialized) return
        initialized = true
        registerProtocolBroadcastListener()
        val pending = pendingUsageBytes()
        Log.d(TAG, "init pendingUsage=${bytesToMegabytesString(pending)}MB")
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerProtocolBroadcastListener() {
        if (receiverRegistered) return
        val filter = IntentFilter(VpnTrafficConstants.ACTION_PROTOCOL_TRAFFIC)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(protocolTrafficReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(protocolTrafficReceiver, filter)
        }
        receiverRegistered = true
    }

    fun startPeriodicUsageUpload() {
        init()
        if (periodicJob?.isActive == true) return
        periodicJob = trackerScope.launch {
            appDetailsDataStore.usagePeriodMinutesFlow
                .distinctUntilChanged()
                .collectLatest { periodMinutes ->
                    val intervalMs = periodMinutes.toUsageIntervalMillis()
                    Log.d(TAG, "periodic usage interval=${periodMinutes}m")
                    while (isActive) {
                        delay(intervalMs)
                        flushUsageAndClear(reason = "periodic", enforceExpiry = true)
                    }
                }
        }
    }

    fun stopPeriodicUsageUpload() {
        periodicJob?.cancel()
        periodicJob = null
    }

    /**
     * Starts a protocol-independent VPN traffic poller.
     *
     * The normalized protocol broadcasts are still supported, but this poller keeps
     * usage accounting and notification traffic alive even when a protocol core does
     * not emit byte-count broadcasts reliably. It reads VPN/TUN interface counters,
     * so it stays closer to the actual tunneled traffic than device-wide TrafficStats.
     */
    fun startRuntimeAccounting(protocol: String?) {
        init()
        synchronized(prefsLock) {
            runtimeProtocol = protocol
            runtimeLastSnapshot = null
            runtimeSessionDownloadBytes = 0L
            runtimeSessionUploadBytes = 0L
            runtimeInterfaceCounterAvailable = false
        }
        resetSessionCounters(protocol)

        // OpenVPN already provides authoritative byte counts through
        // VpnStatus.ByteCountListener. Interface polling can race with those broadcasts
        // and zero/override the totals shown on the home screen, so keep OpenVPN on the
        // protocol-broadcast path only.
        if (protocol.equals(VpnTrafficConstants.PROTOCOL_OPENVPN, ignoreCase = true)) {
            runtimeAccountingJob?.cancel()
            runtimeAccountingJob = null
            Log.d(TAG, "runtime accounting uses protocol broadcast source protocol=$protocol")
            return
        }

        if (runtimeAccountingJob?.isActive == true) return
        runtimeAccountingJob = trackerScope.launch {
            Log.d(TAG, "runtime accounting started protocol=$protocol")
            while (isActive) {
                pollRuntimeInterfaceTraffic()
                delay(RUNTIME_POLL_INTERVAL_MS)
            }
        }
    }

    fun stopRuntimeAccounting() {
        runCatching { pollRuntimeInterfaceTraffic() }
        runtimeAccountingJob?.cancel()
        runtimeAccountingJob = null
        synchronized(prefsLock) {
            runtimeProtocol = null
            runtimeLastSnapshot = null
            runtimeInterfaceCounterAvailable = false
        }
        _trafficSpeed.value = TrafficSpeed()
        Log.d(TAG, "runtime accounting stopped pending=${bytesToMegabytesString(pendingUsageBytes())}MB")
    }

    fun flushUsageAndClearAsync(reason: String = "async") {
        trackerScope.launch { flushUsageAndClear(reason = reason, enforceExpiry = true) }
    }

    /**
     * Sends all pending consumed bytes to the usage API and clears only the sent amount on success.
     */
    suspend fun flushUsageAndClear(
        reason: String = "manual",
        enforceExpiry: Boolean = true
    ): Boolean = flushMutex.withLock {
        val bytesToSend = pendingUsageBytes()
        if (bytesToSend <= 0L) {
            Log.d(TAG, "flush skipped: no pending bytes reason=$reason")
            return@withLock true
        }

        val (username, password, device) = userDataStore.getMultiLoginCredentials()
        if (username.isNullOrBlank() || password.isNullOrBlank() || device.isNullOrBlank()) {
            Log.w(TAG, "flush skipped: missing credentials reason=$reason pending=${bytesToMegabytesString(bytesToSend)}MB")
            return@withLock false
        }

        val usageGb = bytesToGigabytesString(bytesToSend)
        if (usageGb.toDoubleOrNull()?.let { it <= 0.0 } != false) {
            Log.d(
                TAG,
                "flush skipped: usage parameter is zero " +
                    "reason=$reason pending=${bytesToMegabytesString(bytesToSend)}MB usageGb=$usageGb"
            )
            return@withLock true
        }

        Log.d(TAG, "flush reason=$reason usage=${bytesToMegabytesString(bytesToSend)}MB usageGb=$usageGb")

        when (val result = usageUseCase(username, password, usageGb, device, "android")) {
            is Resource.Success -> {
                clearSentBytes(bytesToSend)
                userDataStore.saveUser(result.data)
                if (enforceExpiry) handleExpiryIfNeeded(result.data)
                true
            }
            is Resource.Error -> {
                Log.w(TAG, "flush failed: ${result.message}")
                false
            }
            is Resource.MultiLoginError -> {
                Log.w(TAG, "flush failed: multi login")
                false
            }
            is Resource.Exception -> {
                Log.e(TAG, "flush exception", result.throwable)
                false
            }
            else -> false
        }
    }

    fun pendingUsageBytes(): Long = synchronized(prefsLock) {
        prefs.getLong(KEY_PENDING_BYTES, 0L).coerceAtLeast(0L)
    }

    private fun handleProtocolTraffic(event: ProtocolTrafficEvent) {
        val connectionState = event.state.toConnectionState()
        val isOpenVpn = event.protocol.equals(VpnTrafficConstants.PROTOCOL_OPENVPN, ignoreCase = true)
        _protocolState.value = connectionState

        when (connectionState) {
            is ConnectionState.Connecting -> {
                // OpenVPN may emit transient CONNECTING/WAIT/RECONNECTING states after it has
                // already started sending byte-counts. Resetting here clears the home-screen
                // totals immediately after the first valid usage update.
                if (!isOpenVpn || !hasVisibleSessionTraffic()) {
                    resetSessionCounters(event.protocol)
                    _trafficSpeed.value = TrafficSpeed()
                    _totalDownloadBytes.value = 0L
                    _totalUploadBytes.value = 0L
                }
            }
            is ConnectionState.Connected -> {
                consumeProtocolCounters(event)
            }
            is ConnectionState.Disconnected -> {
                consumeProtocolCounters(event)
                flushUsageAndClearAsync(reason = "protocol_disconnected")
                resetSessionCounters(event.protocol)
                _trafficSpeed.value = TrafficSpeed()
                _totalDownloadBytes.value = 0L
                _totalUploadBytes.value = 0L
            }
            is ConnectionState.Error -> {
                consumeProtocolCounters(event)
                flushUsageAndClearAsync(reason = "protocol_error")
                resetSessionCounters(event.protocol)
                _trafficSpeed.value = TrafficSpeed()
            }
        }
    }

    private fun consumeProtocolCounters(event: ProtocolTrafficEvent) {
        val downTotal = event.downloadTotalBytes.coerceAtLeast(0L)
        val upTotal = event.uploadTotalBytes.coerceAtLeast(0L)
        val shouldAccountFromProtocol = synchronized(prefsLock) {
            event.protocol.equals(VpnTrafficConstants.PROTOCOL_OPENVPN, ignoreCase = true) ||
                !runtimeInterfaceCounterAvailable
        }

        val deltaBytes = if (shouldAccountFromProtocol) {
            synchronized(prefsLock) {
                val savedProtocol = prefs.getString(KEY_SESSION_PROTOCOL, null)
                val lastDown = prefs.getLong(KEY_LAST_DOWNLOAD_TOTAL, NO_COUNTER)
                val lastUp = prefs.getLong(KEY_LAST_UPLOAD_TOTAL, NO_COUNTER)

                val isNewProtocolSession = savedProtocol != event.protocol || lastDown == NO_COUNTER || lastUp == NO_COUNTER
                val deltaDown = if (isNewProtocolSession || downTotal < lastDown) downTotal else downTotal - lastDown
                val deltaUp = if (isNewProtocolSession || upTotal < lastUp) upTotal else upTotal - lastUp
                val delta = (deltaDown + deltaUp).coerceAtLeast(0L)
                addPendingBytesLocked(delta)

                prefs.edit()
                    .putString(KEY_SESSION_PROTOCOL, event.protocol)
                    .putLong(KEY_LAST_DOWNLOAD_TOTAL, downTotal)
                    .putLong(KEY_LAST_UPLOAD_TOTAL, upTotal)
                    .apply()
                delta
            }
        } else {
            0L
        }

        if (!shouldAccountFromProtocol) {
            // Runtime interface polling is the active accounting source. Keep protocol
            // state, but avoid double-counting protocol byte counters.
            return
        }

        _totalDownloadBytes.value = downTotal
        _totalUploadBytes.value = upTotal
        _trafficSpeed.value = TrafficSpeed(
            downloadBps = event.downloadSpeedBps.coerceAtLeast(0L),
            uploadBps = event.uploadSpeedBps.coerceAtLeast(0L)
        )

        if (deltaBytes > 0L) {
            Log.d(
                TAG,
                "traffic delta=${bytesToMegabytesString(deltaBytes)}MB " +
                    "source=protocol protocol=${event.protocol} " +
                    "pending=${bytesToMegabytesString(pendingUsageBytes())}MB"
            )
        }
    }

    private fun pollRuntimeInterfaceTraffic() {
        val snapshot = readVpnInterfaceTrafficSnapshot() ?: return
        var deltaBytes = 0L
        var deltaDown = 0L
        var deltaUp = 0L
        var sessionDown = 0L
        var sessionUp = 0L
        val protocol = synchronized(prefsLock) { runtimeProtocol }

        synchronized(prefsLock) {
            val previous = runtimeLastSnapshot
            runtimeLastSnapshot = snapshot
            runtimeInterfaceCounterAvailable = true

            if (previous != null && snapshot.rxBytes >= previous.rxBytes && snapshot.txBytes >= previous.txBytes) {
                deltaDown = snapshot.rxBytes - previous.rxBytes
                deltaUp = snapshot.txBytes - previous.txBytes
                deltaBytes = (deltaDown + deltaUp).coerceAtLeast(0L)
                runtimeSessionDownloadBytes += deltaDown.coerceAtLeast(0L)
                runtimeSessionUploadBytes += deltaUp.coerceAtLeast(0L)
                addPendingBytesLocked(deltaBytes)
            }

            sessionDown = runtimeSessionDownloadBytes
            sessionUp = runtimeSessionUploadBytes
        }

        _totalDownloadBytes.value = sessionDown
        _totalUploadBytes.value = sessionUp
        _trafficSpeed.value = TrafficSpeed(
            downloadBps = deltaDown.coerceAtLeast(0L),
            uploadBps = deltaUp.coerceAtLeast(0L)
        )

        if (deltaBytes > 0L) {
            Log.d(
                TAG,
                "traffic delta=${bytesToMegabytesString(deltaBytes)}MB " +
                    "source=interface interface=${snapshot.interfaceName} " +
                    "protocol=$protocol pending=${bytesToMegabytesString(pendingUsageBytes())}MB"
            )
        }
    }

    private fun readVpnInterfaceTrafficSnapshot(): InterfaceTrafficSnapshot? {
        return runCatching {
            File(PROC_NET_DEV).readLines()
                .asSequence()
                .mapNotNull { line -> parseInterfaceTrafficLine(line) }
                .filter { it.isVpnInterface() }
                .maxByOrNull { it.rxBytes + it.txBytes }
        }.getOrNull()
    }

    private fun parseInterfaceTrafficLine(line: String): InterfaceTrafficSnapshot? {
        val separatorIndex = line.indexOf(':')
        if (separatorIndex <= 0) return null

        val name = line.substring(0, separatorIndex).trim()
        val values = line.substring(separatorIndex + 1)
            .trim()
            .split(Regex("\\s+"))
        if (values.size < 16) return null

        val rx = values.getOrNull(0)?.toLongOrNull() ?: return null
        val tx = values.getOrNull(8)?.toLongOrNull() ?: return null
        return InterfaceTrafficSnapshot(name, rx.coerceAtLeast(0L), tx.coerceAtLeast(0L))
    }

    private fun InterfaceTrafficSnapshot.isVpnInterface(): Boolean {
        val normalized = interfaceName.lowercase(Locale.US)
        return normalized.startsWith("tun") ||
            normalized.startsWith("tap") ||
            normalized.startsWith("ppp") ||
            normalized.startsWith("ipsec") ||
            normalized.startsWith("wg")
    }

    private fun addPendingBytesLocked(delta: Long) {
        if (delta <= 0L) return
        val pending = prefs.getLong(KEY_PENDING_BYTES, 0L).coerceAtLeast(0L)
        prefs.edit()
            .putLong(KEY_PENDING_BYTES, pending + delta)
            .apply()
    }

    private fun hasVisibleSessionTraffic(): Boolean {
        return _totalDownloadBytes.value > 0L ||
            _totalUploadBytes.value > 0L ||
            _trafficSpeed.value.downloadBps > 0L ||
            _trafficSpeed.value.uploadBps > 0L
    }

    private fun resetSessionCounters(protocol: String?) = synchronized(prefsLock) {
        prefs.edit()
            .putString(KEY_SESSION_PROTOCOL, protocol)
            .putLong(KEY_LAST_DOWNLOAD_TOTAL, NO_COUNTER)
            .putLong(KEY_LAST_UPLOAD_TOTAL, NO_COUNTER)
            .apply()
    }

    private fun clearSentBytes(sentBytes: Long) = synchronized(prefsLock) {
        val current = prefs.getLong(KEY_PENDING_BYTES, 0L).coerceAtLeast(0L)
        prefs.edit()
            .putLong(KEY_PENDING_BYTES, (current - sentBytes).coerceAtLeast(0L))
            .apply()
    }

    private suspend fun handleExpiryIfNeeded(user: com.example.fulluikotlin.domain.model.User) {
        val remainTraffic = user.remainTraffic.toDoubleOrNull() ?: 0.0
        val expiryType = when {
            remainTraffic <= 0 -> ExpiryType.VOLUME_EXPIRED
            user.isExpired || user.remainDays <= 0.toFloat() -> ExpiryType.DATE_EXPIRED
            else -> null
        } ?: return

        DialogEventBus.emitExpiry(expiryType)
        VpnOrchestratorHolder.instance?.disconnectFromNotification()
    }

    private fun Intent.toProtocolTrafficEvent(): ProtocolTrafficEvent {
        return ProtocolTrafficEvent(
            protocol = getStringExtra(VpnTrafficConstants.EXTRA_PROTOCOL).orEmpty(),
            state = getStringExtra(VpnTrafficConstants.EXTRA_STATE)
                ?: VpnTrafficConstants.STATE_DISCONNECTED,
            downloadTotalBytes = getLongExtraCompat(VpnTrafficConstants.EXTRA_DOWNLOAD_TOTAL),
            uploadTotalBytes = getLongExtraCompat(VpnTrafficConstants.EXTRA_UPLOAD_TOTAL),
            downloadSpeedBps = getLongExtraCompat(VpnTrafficConstants.EXTRA_DOWNLOAD_SPEED),
            uploadSpeedBps = getLongExtraCompat(VpnTrafficConstants.EXTRA_UPLOAD_SPEED),
            durationSeconds = getLongExtraCompat(VpnTrafficConstants.EXTRA_DURATION_SECONDS),
            timestampMs = getLongExtraCompat(VpnTrafficConstants.EXTRA_TIMESTAMP)
                .takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun Intent.getLongExtraCompat(name: String): Long {
        val value = extras?.get(name) ?: return 0L
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }.coerceAtLeast(0L)
    }

    private fun String.toConnectionState(): ConnectionState {
        return when (uppercase(Locale.US)) {
            VpnTrafficConstants.STATE_CONNECTED -> ConnectionState.Connected
            VpnTrafficConstants.STATE_CONNECTING -> ConnectionState.Connecting
            VpnTrafficConstants.STATE_ERROR -> ConnectionState.Error("Protocol error")
            else -> ConnectionState.Disconnected
        }
    }

    private fun Long.toUsageIntervalMillis(): Long {
        val safeMinutes = coerceAtLeast(1L)
        return if (safeMinutes > Long.MAX_VALUE / MILLIS_PER_MINUTE) {
            Long.MAX_VALUE
        } else {
            safeMinutes * MILLIS_PER_MINUTE
        }
    }

    private fun bytesToGigabytesString(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return String.format(Locale.US, "%.6f", gb)
    }

    private fun bytesToMegabytesString(bytes: Long): String {
        val mb = bytes / 1_048_576.0
        return String.format(Locale.US, "%.3f", mb)
    }

    private data class InterfaceTrafficSnapshot(
        val interfaceName: String,
        val rxBytes: Long,
        val txBytes: Long
    )

    companion object {
        private const val TAG = "VpnUsageTracker"
        private const val PREFS_NAME = "vpn_usage_tracker"
        private const val KEY_PENDING_BYTES = "pending_usage_bytes"
        private const val KEY_LAST_DOWNLOAD_TOTAL = "last_download_total"
        private const val KEY_LAST_UPLOAD_TOTAL = "last_upload_total"
        private const val KEY_SESSION_PROTOCOL = "session_protocol"
        private const val NO_COUNTER = -1L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val RUNTIME_POLL_INTERVAL_MS = 1_000L
        private const val PROC_NET_DEV = "/proc/net/dev"
    }
}
