// infrastructure/service/CustomNotificationProvider.kt
package com.example.fulluikotlin.infrastructure.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.fulluikotlin.domain.`interface`.INotificationService
import com.example.fulluikotlin.infrastructure.vpn.VpnOrchestrator
import pw.fullvpn.android.R

class CustomNotificationProvider(
    private val context: Context
) : INotificationService {

    private val NOTIFICATION_ID = 7281
    private lateinit var notificationManager: NotificationManager
    lateinit var builder: NotificationCompat.Builder
    private var isConnected = false
    private var currentRemark = ""

    override fun showConnecting(remark: String): Notification {   // تغییر برگرداندن Notification
        currentRemark = remark
        createChannel()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(context, DisconnectReceiver::class.java)
        val disconnectPending = PendingIntent.getBroadcast(
            context, 0, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        builder = NotificationCompat.Builder(context, "custom_vpn_channel")
            .setSmallIcon(R.drawable.ic_connecting)
            .setContentTitle("Connecting to Server...")
            .setContentText("Please wait...")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPending
            )

        isConnected = false
        return builder.build()
    }

    override fun showConnected(remark: String) {
        if (!::builder.isInitialized) return
        builder.setContentTitle("Connected to $remark")
        builder.setSmallIcon(R.drawable.ic_connected)
        builder.setContentText("Tap to open application")
        notificationManager.notify(NOTIFICATION_ID, builder.build())
        isConnected = true
    }

    override fun updateTraffic(downloadBps: Long, uploadBps: Long, totalDownload: Long, totalUpload: Long) {
        if (!::builder.isInitialized) return
        if (!isConnected) showConnected(currentRemark)
        val downText = formatSpeed(downloadBps)
        val upText = formatSpeed(uploadBps)
        builder.setContentText("↓ $downText  ↑ $upText")
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    override fun stop() {
        if (::notificationManager.isInitialized) {
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    private fun startForeground() {
        // برای سرویسی که این نوتیف را نشان می‌دهد، باید startForeground صدا زده شود
        // اما این کلاس فقط Provider است، پس شروع foreground بر عهده NotificationHostService است
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "custom_vpn_channel",
                "VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "VPN Connection Status"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024f * 1024f))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024f)
            else -> "$bytesPerSec B/s"
        }
    }
}

// یک BroadcastReceiver ساده برای قطع اتصال
class DisconnectReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // اینجا باید به Orchestrator بگویید disconnect کند
        // می‌توانید از EventBus یا LiveData یا یک Singleton استفاده کنید
        VpnOrchestratorHolder.instance?.disconnectFromNotification()
    }
}

object VpnOrchestratorHolder {
    var instance: VpnOrchestrator? = null
}