package pw.fullvpn.android.ui.screens.aboutus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fulluikotlin.ui.screens.aboutus.AboutUsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore

class AboutUsViewModel(
    private val appDetailsDataStore: AppDetailsDataStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(AboutUsUiState())
    val uiState: StateFlow<AboutUsUiState> = _uiState

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                appDetailsDataStore.aboutUsFlow,
                appDetailsDataStore.telegramChannelIdFlow,
                appDetailsDataStore.telegramSupportIdFlow,
                appDetailsDataStore.instagramLinkFlow
            ) { aboutUs, telegramChannelId, telegramSupportId, instagramLink ->
                AboutUsUiState(
                    aboutUs = aboutUs ?: "",
                    telegramLink = buildSocialLink("telegram", telegramChannelId),
                    telegramSupportLink = buildSocialLink("telegram", telegramSupportId),
                    instagramLink = buildSocialLink("instagram", instagramLink)
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun buildSocialLink(type: String, value: String?): String {
        if (value.isNullOrBlank()) return ""
        return when (type) {
            "telegram" -> {
                if (value.startsWith("http")) value else "https://t.me/$value"
            }

            "instagram" -> {
                if (value.startsWith("http")) value else "https://instagram.com/$value"
            }

            else -> ""
        }
    }
}