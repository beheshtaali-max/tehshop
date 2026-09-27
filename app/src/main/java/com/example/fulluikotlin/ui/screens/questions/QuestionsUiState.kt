package com.example.fulluikotlin.ui.screens.questions

import com.example.fulluikotlin.domain.model.Faq

data class QuestionsUiState(
    val items: List<QuestionsItemState> = emptyList()
)

data class QuestionsItemState(
    val faq: Faq,
    val isExpanded: Boolean = false
)
