package com.example.fulluikotlin.infrastructure.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object NotificationPermissionHelper {
    const val REQUEST_CODE_POST_NOTIFICATIONS = 13001

    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasPermission(context: Context): Boolean {
        if (!isRequired()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true when the app can create visible VPN notifications.
     * Returns false after starting the runtime permission request.
     */
    fun ensurePermission(activity: Activity?): Boolean {
        if (!isRequired()) return true
        val act = activity ?: return false
        if (hasPermission(act)) return true

        ActivityCompat.requestPermissions(
            act,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_POST_NOTIFICATIONS
        )
        return false
    }
}
