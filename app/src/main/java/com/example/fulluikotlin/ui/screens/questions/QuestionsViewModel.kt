package com.example.fulluikotlin.ui.screens.questions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore

class QuestionsViewModel(
    private val appDetailsDataStore: AppDetailsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionsUiState())
    val uiState: StateFlow<QuestionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appDetailsDataStore.faqListFlow.collect { faqList ->
                _uiState.update { state ->
                    state.copy(
                        items = faqList.map { faq ->
                            // حفظ وضعیت قبلی اگر آیتمی با question یکسان وجود داشت
                            val previous = state.items.find { it.faq.question == faq.question }
                            QuestionsItemState(
                                faq = faq,
                                isExpanded = previous?.isExpanded ?: false
                            )
                        }
                    )
                }
            }
        }
    }

    fun toggleExpanded(index: Int) {
        val currentItems = _uiState.value.items.toMutableList()
        if (index in currentItems.indices) {
            val item = currentItems[index]
            currentItems[index] = item.copy(isExpanded = !item.isExpanded)
            _uiState.update { it.copy(items = currentItems) }
        }
    }
}