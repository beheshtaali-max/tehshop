package com.example.fulluikotlin.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.usecase.auth.LoginUseCase
import com.example.fulluikotlin.domain.utils.AppErrorClassifier
import com.example.fulluikotlin.domain.utils.AppErrorCode
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.infrastructure.reporting.FirebaseErrorReporter
import com.example.fulluikotlin.infrastructure.network.NetworkConnectivityChecker


class LoginViewModel(
    private val loginUseCase: LoginUseCase,
    private val userDataStore: UserDataStore,
    private val appDetailsDataStore: AppDetailsDataStore,
    private val networkConnectivityChecker: NetworkConnectivityChecker

) : ViewModel() {


    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val devicesFlow: StateFlow<List<String>> = userDataStore.multiLoginDevicesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val telegramSupportIdFlow: Flow<String?> = appDetailsDataStore.telegramSupportIdFlow

    init {
        loadSavedCredentials()
    }

    private fun loadSavedCredentials() {
        viewModelScope.launch {
            try {
                val (savedUsername, savedPassword) = userDataStore.getSavedCredentials()
                if (!savedUsername.isNullOrBlank() && !savedPassword.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        username = savedUsername,
                        password = savedPassword
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                FirebaseErrorReporter.report(
                    errorCode = AppErrorCode.SESSION_READ_FAILED,
                    context = "login_load_saved_credentials",
                    throwable = error
                )
                _uiState.value = _uiState.value.copy(
                    errorMessage = AppErrorCode.SESSION_READ_FAILED.displayMessage()
                )
            }
        }
    }

    fun login(device: String) {
        val username = _uiState.value.username.trim()
        val password = _uiState.value.password.trim()

        // اعتبارسنجی
        var hasError = false
        var usernameError: String? = null
        var passwordError: String? = null

        if (username.isEmpty()) {
            usernameError = "نام کاربری را وارد کنید"
            hasError = true
        }
        if (password.isEmpty()) {
            passwordError = "رمز عبور را وارد کنید"
            hasError = true
        }

        if (hasError) {
            _uiState.value = _uiState.value.copy(
                usernameError = usernameError,
                passwordError = passwordError
            )
            return
        }

        if (!networkConnectivityChecker.isInternetAvailable()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = AppErrorCode.NETWORK_OFFLINE.displayMessage()
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            when (val result = loginUseCase(username, password, "android", device)) {
                is Resource.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoginSuccess = true,
                        showMultiLoginSheet = false
                    )
                }

                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }

                is Resource.MultiLoginError -> {
                    try {
                        userDataStore.saveMultiLoginDevices(result.devices)
                        userDataStore.saveMultiLoginCredentials(username, password, device)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        FirebaseErrorReporter.report(
                            errorCode = AppErrorCode.LOCAL_STORAGE_FAILED,
                            context = "save_multi_login_state",
                            throwable = error
                        )
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showMultiLoginSheet = true,
                        errorMessage = AppErrorCode.AUTH_DEVICE_LIMIT.displayMessage()
                    )
                }

                is Resource.Exception -> {
                    val errorCode = AppErrorClassifier.classify(result.throwable)
                    val supportCode = AppErrorClassifier.supportCode(result.throwable, errorCode)
                    FirebaseErrorReporter.report(
                        errorCode = errorCode,
                        supportCode = supportCode,
                        context = "manual_login",
                        throwable = result.throwable
                    )
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = errorCode.displayMessage(supportCode)
                    )
                }

                is Resource.Loading -> { /* غیرفعال */
                }
            }
        }
    }


    fun showExternalError(message: String) {
        if (message.isNotBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = message)
        }
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(
            username = value,
            usernameError = null,
            errorMessage = null
        )
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(
            password = value,
            passwordError = null,
            errorMessage = null
        )
    }

    fun resetLoginState() {
        _uiState.value = _uiState.value.copy(isLoginSuccess = false)
    }

    // وقتی کاربر شیت را می‌بندد یا عملیات حذف تمام شد
    fun closeMultiLoginSheet() {
        _uiState.value = _uiState.value.copy(showMultiLoginSheet = false)
        // اختیاری: پاک کردن اطلاعات موقت اگر نیاز نیست
        viewModelScope.launch {
            userDataStore.clearMultiLoginData()
        }
    }
}