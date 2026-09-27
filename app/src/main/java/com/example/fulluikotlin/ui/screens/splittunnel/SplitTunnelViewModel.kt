package com.example.fulluikotlin.ui.screens.splittunnel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import com.example.fulluikotlin.infrastructure.vpn.splittunnel.SplitTunnelPackageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SplitTunnelViewModel(
    private val context: Context,
    private val splitTunnelDataStore: SplitTunnelDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplitTunnelUiState())
    val uiState: StateFlow<SplitTunnelUiState> = _uiState

    private val _blockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages.asStateFlow()

    init {
        loadSavedSettings()
        observeBlockedApps()
        loadApps()
        showHintWithDelay()
    }

    private fun loadSavedSettings() {
        viewModelScope.launch {
            val savedMode = splitTunnelDataStore.modeFlow.first().normalizeMode()
            val savedBlacklist = splitTunnelDataStore.excludedAppsFlow.first()
            val savedWhitelist = splitTunnelDataStore.whitAppsFlow.first()
            val selected = when (savedMode) {
                SplitTunnelPackageResolver.MODE_BLACKLIST -> savedBlacklist
                SplitTunnelPackageResolver.MODE_WHITELIST -> savedWhitelist
                else -> emptySet()
            }

            _uiState.value = _uiState.value.copy(
                mode = savedMode,
                savedMode = savedMode,
                excludedPackages = savedBlacklist.toList(),
                whitelistPackages = savedWhitelist.toList(),
                selectedPackages = selected,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    private fun observeBlockedApps() {
        viewModelScope.launch {
            splitTunnelDataStore.blockedAppsFlow.collect { blockedList ->
                val blockedSet = blockedList.map { it.packageName }.toSet()
                _blockedPackages.value = blockedSet

                _uiState.value = _uiState.value.copy(
                    apps = _uiState.value.apps.map { app ->
                        app.copy(isBlocked = blockedSet.contains(app.packageName))
                    },
                    filteredApps = _uiState.value.filteredApps.map { app ->
                        app.copy(isBlocked = blockedSet.contains(app.packageName))
                    }
                )
            }
        }
    }

    private fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val packageManager = context.packageManager
                val allApps = mutableListOf<AppItemModel>()
                val blockedSet = _blockedPackages.value.ifEmpty {
                    splitTunnelDataStore.blockedAppsFlow.first().map { it.packageName }.toSet()
                }

                val packages = packageManager.getInstalledPackages(0)
                for (p in packages) {
                    val appInfo = p.applicationInfo ?: continue
                    if (appInfo.packageName == context.packageName) continue

                    // Show user-facing launcher apps even if they are preloaded/system apps.
                    // Admin-blocked packages are also shown so the user can see why they are disabled.
                    val hasLauncher = packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
                    if (!hasLauncher && !blockedSet.contains(appInfo.packageName)) continue

                    val appName = appInfo.loadLabel(packageManager).toString()
                    val icon = appInfo.loadIcon(packageManager)

                    allApps.add(
                        AppItemModel(
                            name = appName,
                            packageName = appInfo.packageName,
                            icon = icon,
                            isBlocked = blockedSet.contains(appInfo.packageName)
                        )
                    )
                }

                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (5000 - elapsed).coerceAtLeast(0)
                if (remaining > 0) delay(remaining)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        apps = allApps,
                        filteredApps = filterApps(allApps, _uiState.value.searchQuery)
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = (5000 - elapsed).coerceAtLeast(0)
                if (remaining > 0) delay(remaining)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    fun onAppChecked(packageName: String, isChecked: Boolean) {
        val state = _uiState.value
        val current = state.selectedPackages.toMutableSet()
        if (isChecked) current.add(packageName) else current.remove(packageName)

        _uiState.value = when (state.mode) {
            SplitTunnelPackageResolver.MODE_BLACKLIST -> state.copy(
                selectedPackages = current,
                excludedPackages = current.toList(),
                errorMessage = null,
                successMessage = null
            )

            SplitTunnelPackageResolver.MODE_WHITELIST -> state.copy(
                selectedPackages = current,
                whitelistPackages = current.toList(),
                errorMessage = null,
                successMessage = null
            )

            else -> state.copy(
                selectedPackages = current,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun changeMode(newMode: String) {
        val mode = newMode.normalizeMode()
        val currentState = snapshotCurrentSelection(_uiState.value)
        val selected = when (mode) {
            SplitTunnelPackageResolver.MODE_BLACKLIST -> currentState.excludedPackages.toSet()
            SplitTunnelPackageResolver.MODE_WHITELIST -> currentState.whitelistPackages.toSet()
            else -> emptySet()
        }

        if (mode == SplitTunnelPackageResolver.MODE_OFF) {
            viewModelScope.launch {
                splitTunnelDataStore.saveMode(SplitTunnelPackageResolver.MODE_OFF)
            }
        }

        _uiState.value = currentState.copy(
            mode = mode,
            savedMode = if (mode == SplitTunnelPackageResolver.MODE_OFF) {
                SplitTunnelPackageResolver.MODE_OFF
            } else {
                currentState.savedMode
            },
            selectedPackages = selected,
            errorMessage = null,
            successMessage = if (mode == SplitTunnelPackageResolver.MODE_OFF) {
                "حالت برنامه‌ها خاموش شد؛ لیست‌های قبلی حفظ شدند"
            } else {
                null
            }
        )
    }

    fun confirmSelection() {
        viewModelScope.launch {
            val state = snapshotCurrentSelection(_uiState.value)
            val selected = state.selectedPackages

            when (state.mode) {
                SplitTunnelPackageResolver.MODE_WHITELIST -> {
                    if (selected.isEmpty()) {
                        _uiState.value = state.copy(
                            errorMessage = "برای وایت لیست باید حداقل یک برنامه انتخاب شود",
                            successMessage = null
                        )
                        return@launch
                    }

                    splitTunnelDataStore.saveMode(SplitTunnelPackageResolver.MODE_WHITELIST)
                    splitTunnelDataStore.saveWhitApps(selected)

                    _uiState.value = state.copy(
                        savedMode = SplitTunnelPackageResolver.MODE_WHITELIST,
                        whitelistPackages = selected.toList(),
                        errorMessage = null,
                        successMessage = "وایت لیست ذخیره شد"
                    )
                }

                SplitTunnelPackageResolver.MODE_BLACKLIST -> {
                    splitTunnelDataStore.saveMode(SplitTunnelPackageResolver.MODE_BLACKLIST)
                    splitTunnelDataStore.saveExcludedApps(selected)

                    _uiState.value = state.copy(
                        savedMode = SplitTunnelPackageResolver.MODE_BLACKLIST,
                        excludedPackages = selected.toList(),
                        errorMessage = null,
                        successMessage = "بلک لیست ذخیره شد"
                    )
                }

                else -> {
                    splitTunnelDataStore.saveMode(SplitTunnelPackageResolver.MODE_OFF)

                    _uiState.value = state.copy(
                        mode = SplitTunnelPackageResolver.MODE_OFF,
                        savedMode = SplitTunnelPackageResolver.MODE_OFF,
                        selectedPackages = emptySet(),
                        errorMessage = null,
                        successMessage = "حالت برنامه‌ها خاموش شد؛ لیست‌های قبلی حفظ شدند"
                    )
                }
            }
        }
    }

    private fun snapshotCurrentSelection(state: SplitTunnelUiState): SplitTunnelUiState {
        return when (state.mode) {
            SplitTunnelPackageResolver.MODE_BLACKLIST -> state.copy(
                excludedPackages = state.selectedPackages.toList()
            )

            SplitTunnelPackageResolver.MODE_WHITELIST -> state.copy(
                whitelistPackages = state.selectedPackages.toList()
            )

            else -> state
        }
    }

    fun onSearchChange(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = filterApps(_uiState.value.apps, query)
        )
    }

    private fun filterApps(apps: List<AppItemModel>, query: String): List<AppItemModel> {
        return if (query.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.name.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    private fun showHintWithDelay() {
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(showHint = true)
        }
    }

    fun dismissHint() {
        _uiState.value = _uiState.value.copy(showHint = false)
    }

    private fun String.normalizeMode(): String {
        return when (uppercase(Locale.US)) {
            SplitTunnelPackageResolver.MODE_BLACKLIST -> SplitTunnelPackageResolver.MODE_BLACKLIST
            SplitTunnelPackageResolver.MODE_WHITELIST -> SplitTunnelPackageResolver.MODE_WHITELIST
            else -> SplitTunnelPackageResolver.MODE_OFF
        }
    }
}
