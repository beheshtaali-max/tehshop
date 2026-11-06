package com.example.fulluikotlin.ui.screens.splash

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fulluikotlin.data.local.datastore.AppDetailsDataStore
import com.example.fulluikotlin.data.local.datastore.SplitTunnelDataStore
import com.example.fulluikotlin.data.local.datastore.UserDataStore
import com.example.fulluikotlin.domain.model.AppDetails
import com.example.fulluikotlin.domain.model.ExpiryType
import com.example.fulluikotlin.domain.model.PopupAdsConfig
import com.example.fulluikotlin.domain.model.User
import com.example.fulluikotlin.domain.usecase.app.GetAppDetailsUseCase
import com.example.fulluikotlin.domain.usecase.auth.LoginUseCase
import com.example.fulluikotlin.domain.usecase.popup.GetPopupAdsUseCase
import com.example.fulluikotlin.domain.utils.AppErrorClassifier
import com.example.fulluikotlin.domain.utils.AppErrorCode
import com.example.fulluikotlin.domain.utils.Resource
import com.example.fulluikotlin.infrastructure.reporting.FirebaseErrorReporter
import com.example.fulluikotlin.ui.main.MainViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplashViewModel(
    private val getAppDetailsUseCase: GetAppDetailsUseCase,
    private val getPopupAdsUseCase: GetPopupAdsUseCase,
    private val loginUseCase: LoginUseCase,
    private val appDetailsDataStore: AppDetailsDataStore,
    private val userDataStore: UserDataStore,
    private val splitTunnelDataStore: SplitTunnelDataStore,
    private val mainViewModel: MainViewModel
) : ViewModel() {

    private sealed interface AutoLoginResult {
        data object NotRequired : AutoLoginResult
        data class Success(val user: User) : AutoLoginResult
        data class RequiresLogin(
            val errorCode: AppErrorCode,
            val supportCode: String = errorCode.code,
            val reason: String
        ) : AutoLoginResult
        data class RetryableFailure(
            val errorCode: AppErrorCode,
            val supportCode: String = errorCode.code,
            val throwable: Throwable? = null
        ) : AutoLoginResult
    }

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    private var loadingJob: Job? = null

    init {
        loadAllData()
    }

    fun retryLoading() {
        loadAllData()
    }

    private fun loadAllData() {
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _uiState.value = SplashUiState(
                isLoading = true,
                isLoggedIn = null,
                errorMessage = null,
                showRetry = false
            )

            var loginStateReadFailed = false
            val initiallyLoggedIn = try {
                userDataStore.isLoggedIn.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Failed to read login state; opening Login", error)
                loginStateReadFailed = true
                FirebaseErrorReporter.report(
                    errorCode = AppErrorCode.SESSION_READ_FAILED,
                    context = "read_login_state",
                    throwable = error
                )
                false
            }

            // Bootstrap calls still run in parallel, but an invalid local/auth session no longer
            // waits for them before deciding that the user must be sent to Login.
            val appDetailsDeferred = async { getAppDetailsUseCase() }
            val popupAdsDeferred = async { getPopupAdsUseCase() }
            val authDeferred = if (initiallyLoggedIn) {
                async { performAutoLogin() }
            } else {
                null
            }

            val authResult = authDeferred?.let { deferred ->
                try {
                    deferred.await()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val errorCode = AppErrorClassifier.classify(error)
                    val supportCode = AppErrorClassifier.supportCode(error, errorCode)
                    FirebaseErrorReporter.report(
                        errorCode = errorCode,
                        supportCode = supportCode,
                        context = "auto_login_unhandled",
                        throwable = error
                    )
                    AutoLoginResult.RetryableFailure(
                        errorCode = errorCode,
                        supportCode = supportCode,
                        throwable = error
                    )
                }
            } ?: AutoLoginResult.NotRequired

            // Missing credentials, rejected credentials and multi-login rejection are terminal for
            // the stored session. Clear it and navigate directly to Login. Previously the code kept
            // is_logged_in=true in several paths, so Retry repeated the exact same failed auto-login.
            if (authResult is AutoLoginResult.RequiresLogin) {
                Log.w(TAG, "Stored session is invalid: ${authResult.reason}")
                if (authResult.errorCode == AppErrorCode.SESSION_INCOMPLETE) {
                    FirebaseErrorReporter.report(
                        errorCode = authResult.errorCode,
                        supportCode = authResult.supportCode,
                        context = "auto_login_requires_login",
                        details = mapOf("reason" to authResult.reason)
                    )
                }

                try {
                    userDataStore.logout()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to clear invalid session", error)
                    FirebaseErrorReporter.report(
                        errorCode = AppErrorCode.SESSION_CLEAR_FAILED,
                        context = "clear_invalid_session",
                        throwable = error
                    )
                }

                // Do not lock the user on Splash because an unrelated bootstrap endpoint failed
                // or is still waiting. Existing users already have cached app details.
                appDetailsDeferred.cancel()
                popupAdsDeferred.cancel()
                navigateToLogin(authResult.errorCode.displayMessage(authResult.supportCode))
                return@launch
            }

            // For a fresh install / explicit logout, keep the existing bootstrap behavior so the
            // login screen and the first Main screen have the latest support/update configuration.
            if (authResult is AutoLoginResult.NotRequired) {
                persistAvailableBootstrapData(
                    appDetailsResult = awaitAppDetails(appDetailsDeferred),
                    popupAdsResult = awaitPopupAds(popupAdsDeferred)
                )
                navigateToLogin(
                    message = if (loginStateReadFailed) {
                        AppErrorCode.SESSION_READ_FAILED.displayMessage()
                    } else {
                        null
                    }
                )
                return@launch
            }

            if (authResult is AutoLoginResult.RetryableFailure) {
                // Network/serialization failures do not prove that the account was logged out.
                // Keep the session and show an accurate retryable message.
                _uiState.value = SplashUiState(
                    isLoading = false,
                    isLoggedIn = null,
                    errorMessage = authResult.errorCode.displayMessage(authResult.supportCode),
                    showRetry = true
                )
                return@launch
            }

            val authenticatedUser = (authResult as AutoLoginResult.Success).user
            val appDetailsResult = awaitAppDetails(appDetailsDeferred)
            val popupAdsResult = awaitPopupAds(popupAdsDeferred)

            if (appDetailsResult is Resource.Success && popupAdsResult is Resource.Success) {
                val persistenceError = try {
                    persistBootstrapData(appDetailsResult.data, popupAdsResult.data)
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    error
                }

                if (persistenceError != null) {
                    Log.e(TAG, "Failed to persist bootstrap data", persistenceError)
                    FirebaseErrorReporter.report(
                        errorCode = AppErrorCode.LOCAL_STORAGE_FAILED,
                        context = "persist_bootstrap_data",
                        throwable = persistenceError
                    )
                    _uiState.value = SplashUiState(
                        isLoading = false,
                        isLoggedIn = null,
                        errorMessage = AppErrorCode.LOCAL_STORAGE_FAILED.displayMessage(),
                        showRetry = true
                    )
                    return@launch
                }

                addExpiryDialogIfNeeded(authenticatedUser)

                _uiState.value = SplashUiState(
                    isLoading = false,
                    isLoggedIn = true,
                    errorMessage = null,
                    showRetry = false
                )
            } else {
                _uiState.value = SplashUiState(
                    isLoading = false,
                    isLoggedIn = null,
                    errorMessage = buildBootstrapErrorMessage(appDetailsResult, popupAdsResult),
                    showRetry = true
                )
            }
        }
    }

    private suspend fun performAutoLogin(): AutoLoginResult {
        val credentials = try {
            userDataStore.getAutoLoginCredentials()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Failed to read auto-login credentials", error)
            FirebaseErrorReporter.report(
                errorCode = AppErrorCode.SESSION_READ_FAILED,
                context = "read_auto_login_credentials",
                throwable = error
            )
            return AutoLoginResult.RetryableFailure(
                errorCode = AppErrorCode.SESSION_READ_FAILED,
                supportCode = AppErrorCode.SESSION_READ_FAILED.code,
                throwable = error
            )
        }

        if (credentials == null) {
            return AutoLoginResult.RequiresLogin(
                errorCode = AppErrorCode.SESSION_INCOMPLETE,
                reason = "is_logged_in=true but credentials are incomplete"
            )
        }

        return when (
            val result = loginUseCase(
                credentials.username,
                credentials.password,
                "android",
                credentials.device
            )
        ) {
            is Resource.Success -> AutoLoginResult.Success(result.data)

            // Server-side authentication/account rejection is terminal for the stored session.
            is Resource.Error -> AutoLoginResult.RequiresLogin(
                errorCode = AppErrorCode.fromCode(result.supportCode)
                    ?: AppErrorCode.AUTH_ACCOUNT_REJECTED,
                supportCode = result.supportCode
                    ?: AppErrorCode.AUTH_ACCOUNT_REJECTED.code,
                reason = buildString {
                    append("server rejected stored session")
                    result.serverCode?.let { append("; code=").append(it) }
                }
            )

            is Resource.MultiLoginError -> AutoLoginResult.RequiresLogin(
                errorCode = AppErrorCode.AUTH_DEVICE_LIMIT,
                reason = "device limit / device removed"
            )

            is Resource.Exception -> {
                val errorCode = AppErrorClassifier.classify(result.throwable)
                val supportCode = AppErrorClassifier.supportCode(result.throwable, errorCode)
                FirebaseErrorReporter.report(
                    errorCode = errorCode,
                    supportCode = supportCode,
                    context = "auto_login_request",
                    throwable = result.throwable
                )
                AutoLoginResult.RetryableFailure(
                    errorCode = errorCode,
                    supportCode = supportCode,
                    throwable = result.throwable
                )
            }

            is Resource.Loading -> AutoLoginResult.RetryableFailure(
                errorCode = AppErrorCode.UNKNOWN,
                supportCode = AppErrorCode.UNKNOWN.code
            )
        }
    }

    private suspend fun awaitAppDetails(
        deferred: kotlinx.coroutines.Deferred<Resource<AppDetails>>
    ): Resource<AppDetails> {
        return try {
            deferred.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Resource.Exception(error)
        }
    }

    private suspend fun awaitPopupAds(
        deferred: kotlinx.coroutines.Deferred<Resource<PopupAdsConfig>>
    ): Resource<PopupAdsConfig> {
        return try {
            deferred.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Resource.Exception(error)
        }
    }

    private suspend fun persistAvailableBootstrapData(
        appDetailsResult: Resource<AppDetails>,
        popupAdsResult: Resource<PopupAdsConfig>
    ) {
        val appDetails: AppDetails? = when (appDetailsResult) {
            is Resource.Success -> appDetailsResult.data
            else -> null
        }
        val popupConfig: PopupAdsConfig? = when (popupAdsResult) {
            is Resource.Success -> popupAdsResult.data
            else -> null
        }

        if (appDetails != null) {
            try {
                persistAppDetails(appDetails)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Could not cache app details before Login", error)
                FirebaseErrorReporter.report(
                    errorCode = AppErrorCode.LOCAL_STORAGE_FAILED,
                    context = "cache_app_details_before_login",
                    throwable = error
                )
            }
        }

        if (appDetails != null || popupConfig != null) {
            mainViewModel.prepareDialogsFromResponse(
                updateDetails = appDetails?.updateDetails,
                customDialog = appDetails?.customDialogStatus,
                adsDialog = popupConfig
            )
        }
    }

    private suspend fun persistBootstrapData(
        appDetails: AppDetails,
        popupConfig: PopupAdsConfig
    ) {
        persistAppDetails(appDetails)
        mainViewModel.prepareDialogsFromResponse(
            updateDetails = appDetails.updateDetails,
            customDialog = appDetails.customDialogStatus,
            adsDialog = popupConfig
        )
    }

    private suspend fun persistAppDetails(appDetails: AppDetails) {
        splitTunnelDataStore.saveBlockedApps(appDetails.blockedApps)
        appDetailsDataStore.saveAllDetails(
            telegramSupportId = appDetails.telegramSupportId,
            telegramChannelId = appDetails.telegramChannelId,
            buyAccLink = appDetails.buyAccLink,
            testAccLink = appDetails.testAccLink,
            rechargeAccLink = appDetails.rechargeAccLink,
            instagramLink = appDetails.instagramLink,
            defaultProtocol = appDetails.defaultProtocol,
            usagePeriodMinutes = appDetails.usagePeriodMinutes,
            aboutUs = appDetails.aboutUs,
            faqList = appDetails.faq
        )
    }

    private fun addExpiryDialogIfNeeded(user: User) {
        val remainTraffic = user.remainTraffic.toDoubleOrNull() ?: 0.0
        val expiryType = when {
            remainTraffic <= 0.0 && user.isExpired -> ExpiryType.VOLUME_EXPIRED
            user.isExpired || user.remainDays < 0 -> ExpiryType.DATE_EXPIRED
            user.remainDays in 0f..1f && user.remainDays > 0f -> ExpiryType.DATE_EXPIRING_SOON
            else -> null
        }
        expiryType?.let { mainViewModel.addExpiryDialog(it, user.username) }
    }

    private fun navigateToLogin(message: String? = null) {
        _uiState.value = SplashUiState(
            isLoading = false,
            isLoggedIn = false,
            errorMessage = message,
            showRetry = false
        )
    }

    private fun buildBootstrapErrorMessage(
        appDetailsResult: Resource<AppDetails>,
        popupAdsResult: Resource<PopupAdsConfig>
    ): String {
        val (errorCode, throwable, context) = when {
            appDetailsResult is Resource.Error -> Triple(
                AppErrorCode.fromCode(appDetailsResult.supportCode)
                    ?: AppErrorCode.APP_DETAILS_FAILED,
                null,
                "app_details_server_error"
            )
            appDetailsResult is Resource.Exception -> Triple(
                AppErrorClassifier.classify(appDetailsResult.throwable),
                appDetailsResult.throwable,
                "app_details_request"
            )
            appDetailsResult is Resource.MultiLoginError -> Triple(
                AppErrorCode.APP_DETAILS_FAILED,
                null,
                "app_details_unexpected_multi_login"
            )
            popupAdsResult is Resource.Error -> Triple(
                AppErrorCode.fromCode(popupAdsResult.supportCode)
                    ?: AppErrorCode.POPUP_DETAILS_FAILED,
                null,
                "popup_server_error"
            )
            popupAdsResult is Resource.Exception -> Triple(
                AppErrorCode.POPUP_DETAILS_FAILED,
                popupAdsResult.throwable,
                "popup_request"
            )
            popupAdsResult is Resource.MultiLoginError -> Triple(
                AppErrorCode.POPUP_DETAILS_FAILED,
                null,
                "popup_unexpected_multi_login"
            )
            else -> Triple(AppErrorCode.UNKNOWN, null, "bootstrap_unknown")
        }

        val supportCode = if (
            throwable != null && errorCode != AppErrorCode.POPUP_DETAILS_FAILED
        ) {
            AppErrorClassifier.supportCode(throwable, errorCode)
        } else {
            errorCode.code
        }

        FirebaseErrorReporter.report(
            errorCode = errorCode,
            supportCode = supportCode,
            context = context,
            throwable = throwable
        )
        return errorCode.displayMessage(supportCode)
    }

    override fun onCleared() {
        loadingJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val TAG = "SplashViewModel"
    }
}
