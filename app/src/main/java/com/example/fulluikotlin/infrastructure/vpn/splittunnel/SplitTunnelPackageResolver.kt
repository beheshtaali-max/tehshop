package com.example.fulluikotlin.infrastructure.vpn.splittunnel

import android.content.Context
import android.util.Log
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import kotlinx.coroutines.flow.first
import java.util.Locale

/**
 * Single source of truth for split-tunnel package rules.
 *
 * Android VpnService receives a DISALLOWED package list for the current VPN tunnel.
 * - OFF: no user split-tunnel rule is applied. The host app is still excluded.
 * - BLACKLIST: admin-blocked apps + user-selected blacklist apps do not use the VPN.
 * - WHITELIST: only user-selected whitelist apps use the VPN; every other installed app is disallowed.
 *
 * Keep this logic here so every protocol adapter applies the exact same rules.
 */
object SplitTunnelPackageResolver {
    private const val TAG = "SplitTunnelResolver"

    const val MODE_OFF = "OFF"
    const val MODE_BLACKLIST = "BLACKLIST"
    const val MODE_WHITELIST = "WHITELIST"

    suspend fun resolveDisallowedPackages(
        context: Context,
        splitTunnelDataStore: SplitTunnelDataStore
    ): ArrayList<String> {
        val appContext = context.applicationContext
        val mode = splitTunnelDataStore.modeFlow.first().uppercase(Locale.US)
        val ownPackage = appContext.packageName

        val disallowedPackages = when (mode) {
            MODE_WHITELIST -> resolveWhitelistModeDisallowedPackages(
                context = appContext,
                splitTunnelDataStore = splitTunnelDataStore,
                ownPackage = ownPackage
            )

            MODE_BLACKLIST -> resolveBlacklistModeDisallowedPackages(
                splitTunnelDataStore = splitTunnelDataStore,
                ownPackage = ownPackage
            )

            else -> emptyList()
        }.toMutableList()

        // The host app must never be routed through the VPN tunnel.
        // Keep it excluded regardless of split-tunnel mode.
        disallowedPackages.add(ownPackage)

        val result = disallowedPackages
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toCollection(ArrayList())

        Log.d(TAG, "mode=$mode disallowedPackages=${result.size}")
        return result
    }

    private suspend fun resolveWhitelistModeDisallowedPackages(
        context: Context,
        splitTunnelDataStore: SplitTunnelDataStore,
        ownPackage: String
    ): List<String> {
        val whitelist = splitTunnelDataStore.whitAppsFlow.first()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != ownPackage }
            .toSet()

        // Empty whitelist is treated as OFF at protocol level to avoid accidentally blocking
        // the whole device if a stale/invalid mode is saved. The UI prevents saving this state.
        if (whitelist.isEmpty()) return emptyList()

        val installedPackages = context.packageManager.getInstalledPackages(0)
            .map { it.packageName }
            .filter { it.isNotBlank() && it != ownPackage }
            .toSet()

        return (installedPackages - whitelist).toList()
    }

    private suspend fun resolveBlacklistModeDisallowedPackages(
        splitTunnelDataStore: SplitTunnelDataStore,
        ownPackage: String
    ): List<String> {
        val serverBlocked = splitTunnelDataStore.blockedAppsFlow.first()
            .map { it.packageName.trim() }
            .filter { it.isNotBlank() && it != ownPackage }

        val userExcluded = splitTunnelDataStore.excludedAppsFlow.first()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != ownPackage }

        return serverBlocked + userExcluded
    }
}
