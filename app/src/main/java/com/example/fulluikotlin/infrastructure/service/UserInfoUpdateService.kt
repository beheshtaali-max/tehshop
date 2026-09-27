package com.example.fulluikotlin.infrastructure.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.fulluikotlin.infrastructure.vpn.traffic.VpnUsageTracker
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps usage accounting alive while a VPN core is running.
 * The actual accounting is centralized in VpnUsageTracker.
 */
class UserInfoUpdateService : Service(), KoinComponent {

    private val usageTracker: VpnUsageTracker by inject()

    companion object {
        private const val TAG = "UserInfoService"

        fun start(context: Context) {
            context.startService(Intent(context, UserInfoUpdateService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UserInfoUpdateService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        usageTracker.init()
        usageTracker.registerProtocolBroadcastListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        usageTracker.init()
        usageTracker.registerProtocolBroadcastListener()
        usageTracker.startPeriodicUsageUpload()
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        usageTracker.flushUsageAndClearAsync(reason = "usage_service_destroy")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
