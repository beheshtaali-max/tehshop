package com.example.fulluikotlin.ui.screens.devicesactive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.usecase.auth.DeleteDeviceUseCase
import com.example.fulluikotlin.domain.utils.AppErrorClassifier
import com.example.fulluikotlin.domain.utils.AppErrorCode
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.infrastructure.reporting.FirebaseErrorReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DevicesViewModel(
    private val deleteDeviceUseCase: DeleteDeviceUseCase,
    private val userDataStore: UserDataStore
) : ViewModel() {

    private val _deleteState = MutableStateFlow<DeleteState?>(null)
    val deleteState: StateFlow<DeleteState?> = _deleteState

    fun deleteDevice(username: String, deviceId: String) {
        viewModelScope.launch {
            _deleteState.value = DeleteState.Loading(deviceId)

            when (val result = deleteDeviceUseCase(username, deviceId)) {
                is Resource.Success -> {
                    try {
                        val currentDevices = userDataStore.getMultiLoginDevices() ?: emptyList()
                        val updatedDevices = currentDevices.filterNot { it == deviceId }
                        userDataStore.updateMultiLoginDevices(updatedDevices)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // The server operation succeeded. Report the local-cache issue but do not
                        // tell the user that deletion failed.
                        FirebaseErrorReporter.report(
                            errorCode = AppErrorCode.LOCAL_STORAGE_FAILED,
                            context = "delete_device_local_cache",
                            throwable = error
                        )
                    }
                    _deleteState.value = DeleteState.Success(deviceId)
                }

                is Resource.Error -> {
                    _deleteState.value = DeleteState.Error(deviceId, result.message)
                }

                is Resource.Exception -> {
                    val errorCode = AppErrorClassifier.classify(result.throwable)
                    val supportCode = AppErrorClassifier.supportCode(result.throwable, errorCode)
                    FirebaseErrorReporter.report(
                        errorCode = errorCode,
                        supportCode = supportCode,
                        context = "delete_device_request",
                        throwable = result.throwable
                    )
                    _deleteState.value = DeleteState.Error(
                        deviceId,
                        errorCode.displayMessage(supportCode)
                    )
                }

                is Resource.MultiLoginError,
                is Resource.Loading -> {
                    val errorCode = AppErrorCode.DEVICE_DELETE_FAILED
                    _deleteState.value = DeleteState.Error(
                        deviceId,
                        errorCode.displayMessage()
                    )
                }
            }
        }
    }

    fun resetDeleteState() {
        _deleteState.value = null
    }
}
