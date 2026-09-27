package com.example.fulluikotlin.ui.screens.activeservice

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.User

class ActiveServiceViewModel(
    private val userDataStore: UserDataStore,
    private val appDetailsDataStore: AppDetailsDataStore,
) : ViewModel() {

    val rechargeAccLinkFlow: Flow<String?> = appDetailsDataStore.rechargeAccLinkFlow

    private val _user = mutableStateOf<User?>(null)
    val user: User? get() = _user.value

    val devices: StateFlow<List<String>> = userDataStore.multiLoginDevicesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            userDataStore.userFlow.collect { user ->
                _user.value = user
            }
        }
    }


}