package com.example.fulluikotlin.ui.screens.splittunnel

data class SplitTunnelUiState(
    val isLoading: Boolean = true,
    val apps: List<AppItemModel> = emptyList(),
    val filteredApps: List<AppItemModel> = emptyList(),
    val excludedPackages: List<String> = emptyList(),
    val whitelistPackages: List<String> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val mode: String = "OFF",
    val savedMode: String = "OFF",
    val searchQuery: String = "",
    val showHint: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
