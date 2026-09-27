package com.example.fulluikotlin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.fulluikotlin.data.local.datastore.SettingsDataStore
import com.example.fulluikotlin.infrastructure.vpn.traffic.VpnUsageTracker
import com.example.fulluikotlin.ui.navigation.AppNavHost
import com.example.fulluikotlin.ui.utils.NetworkRetryEventBus
import com.example.fulluikotlin.ui.theme.FullKotlinTheme
import dev.dev7.lib.v2ray.V2rayController
import dev.dev7.lib.v2ray.utils.Utilities
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import pw.fullvpn.android.R


class MainActivity : AppCompatActivity() {

    private val settingsDataStore: SettingsDataStore by inject()
    private val usageTracker: VpnUsageTracker by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NetworkRetryEventBus.retryEvents.collect { message ->
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            val isDarkMode by settingsDataStore.isDarkMode.collectAsState(initial = false)
            FullKotlinTheme(darkTheme = isDarkMode) {
                AppNavHost()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        usageTracker.init()
        usageTracker.registerProtocolBroadcastListener()
        usageTracker.startPeriodicUsageUpload()
        lifecycleScope.launch {
            usageTracker.flushUsageAndClear(reason = "app_on_start", enforceExpiry = true)
        }
    }

}