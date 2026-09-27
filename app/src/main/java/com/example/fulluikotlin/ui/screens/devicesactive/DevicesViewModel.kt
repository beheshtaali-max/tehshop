package com.example.fulluikotlin.ui.screens.devicesactive

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.usecase.auth.DeleteDeviceUseCase
import com.example.fulluikotlin.domain.utils.Resource

class DevicesViewModel(
    private val deleteDeviceUseCase: DeleteDeviceUseCase,
    private val userDataStore: UserDataStore
) : ViewModel() {

    private val _deleteState = MutableStateFlow<DeleteState?>(null)
    val deleteState: StateFlow<DeleteState?> = _deleteState

    fun deleteDevice(username: String, deviceId: String) {
        viewModelScope.launch {
            _deleteState.value = DeleteState.Loading(deviceId)
            val result = deleteDeviceUseCase(username, deviceId)
            if (result is Resource.Success) {
                // ✅ پس از موفقیت در سرور، لیست DataStore را به‌روز می‌کنیم
                val currentDevices = userDataStore.getMultiLoginDevices() ?: emptyList()
                val updatedDevices = currentDevices.filterNot { it == deviceId }
                userDataStore.updateMultiLoginDevices(updatedDevices)
                _deleteState.value = DeleteState.Success(deviceId)
            } else {
                Log.e("erorrlogon3", result.toString())
                val errorMsg = (result as? Resource.Error)?.message ?: "خطا در حذف دستگاه"
                _deleteState.value = DeleteState.Error(deviceId, errorMsg)
            }
        }
    }

    fun resetDeleteState() {
        _deleteState.value = null
    }
}