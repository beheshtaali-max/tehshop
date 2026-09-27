package com.example.fulluikotlin.domain.`interface`

import android.app.Notification

interface INotificationService {
    fun showConnecting(remark: String): Notification   // تغییر برگرداندن Notification
    fun showConnected(remark: String)
    fun updateTraffic(downloadBps: Long, uploadBps: Long, totalDownload: Long, totalUpload: Long)
    fun stop()
}