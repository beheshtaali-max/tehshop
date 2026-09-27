package com.example.fulluikotlin.infrastructure.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.fulluikotlin.domain.`interface`.INotificationService

class NotificationHostService : Service() {

    private lateinit var notificationService: INotificationService
    private var foregroundStarted = false
    private var currentRemark: String = "VPN"

    companion object {
        const val NOTIFICATION_ID = 7281

        const val ACTION_SHOW_CONNECTING = "SHOW_CONNECTING"
        const val ACTION_SHOW_CONNECTED = "SHOW_CONNECTED"
        const val ACTION_UPDATE_TRAFFIC = "UPDATE_TRAFFIC"
        const val ACTION_STOP = "STOP"

        const val EXTRA_REMARK = "remark"
        const val EXTRA_DOWNLOAD_BPS = "downloadBps"
        const val EXTRA_UPLOAD_BPS = "uploadBps"
        const val EXTRA_TOTAL_DOWNLOAD = "totalDownload"
        const val EXTRA_TOTAL_UPLOAD = "totalUpload"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("NotificationHost", "onCreate called")
        notificationService = CustomNotificationProvider(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("NotificationHost", "onStartCommand action=$action foregroundStarted=$foregroundStarted")

        when (action) {
            ACTION_SHOW_CONNECTING -> {
                currentRemark = intent.getStringExtra(EXTRA_REMARK)?.takeIf { it.isNotBlank() } ?: "VPN"
                ensureForegroundNotification(currentRemark)
            }

            ACTION_SHOW_CONNECTED -> {
                currentRemark = intent.getStringExtra(EXTRA_REMARK)?.takeIf { it.isNotBlank() } ?: currentRemark
                ensureForegroundNotification(currentRemark)
                notificationService.showConnected(currentRemark)
            }

            ACTION_UPDATE_TRAFFIC -> {
                val down = intent.getLongExtra(EXTRA_DOWNLOAD_BPS, 0L)
                val up = intent.getLongExtra(EXTRA_UPLOAD_BPS, 0L)
                val totalDown = intent.getLongExtra(EXTRA_TOTAL_DOWNLOAD, 0L)
                val totalUp = intent.getLongExtra(EXTRA_TOTAL_UPLOAD, 0L)

                // اگر سرویس به هر دلیل با UPDATE_TRAFFIC بالا آمد، نوتیف باید خودش ساخته شود.
                ensureForegroundNotification(currentRemark)
                notificationService.updateTraffic(down, up, totalDown, totalUp)
            }

            ACTION_STOP -> {
                notificationService.stop()
                runCatching { stopForeground(true) }
                foregroundStarted = false
                stopSelf()
            }

            else -> {
                // برای سازگاری با start service بدون action، یک foreground notification امن بساز.
                ensureForegroundNotification(currentRemark)
            }
        }

        return START_STICKY
    }

    private fun ensureForegroundNotification(remark: String) {
        if (foregroundStarted) return
        val notification = notificationService.showConnecting(remark)
        startForeground(NOTIFICATION_ID, notification)
        foregroundStarted = true
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
