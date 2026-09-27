package com.example.fulluikotlin.ui.screens.splash

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fulluikotlin.ui.main.MainViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import com.example.fulluikotlin.domain.utils.Resource

class SplashViewModel(
    private val getAppDetailsUseCase: GetAppDetailsUseCase,
    private val getPopupAdsUseCase: GetPopupAdsUseCase,
    private val loginUseCase: LoginUseCase,
    private val appDetailsDataStore: AppDetailsDataStore,
    private val userDataStore: UserDataStore,
    private val splitTunnelDataStore: SplitTunnelDataStore,
    private val mainViewModel: MainViewModel
) : ViewModel() {

    private val _appDetails = MutableStateFlow<AppDetails?>(null)
    val appDetails: StateFlow<AppDetails?> = _appDetails

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading


    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    fun retryLoading() {
        loadAllData()
    }

    private fun loadAllData() {
        viewModelScope.launch {
            // وضعیت بارگذاری + پاک کردن خطاهای قبلی
            _uiState.value = SplashUiState(
                isLoading = true,
                isLoggedIn = null,
                errorMessage = null,
                showRetry = false
            )

            // 1. دریافت اطلاعات اولیه از DataStore
            val initiallyLoggedIn = try {
                userDataStore.isLoggedIn.first()
            } catch (e: Exception) {
                false
            }

            var multiLogin = false
            var notUser = false

            Log.e("initiallyLoggedIn", initiallyLoggedIn.toString())
            // 2. راه‌اندازی درخواست‌های همزمان
            val appDetailsDeferred = async { getAppDetailsUseCase() }
            val popupAdsDeferred = async { getPopupAdsUseCase() }

            // 3. احراز هویت خودکار (در صورت نیاز)
            var user: User? = null
            val authDeferred = if (initiallyLoggedIn) {
                async {
                    val (username, password, device) = userDataStore.getMultiLoginCredentials()
                    if (username.isNullOrBlank() || password.isNullOrBlank() || device.isNullOrBlank()) {
                        return@async false
                    }
                    val result = loginUseCase(username, password, "android", device)
                    when (result) {
                        is Resource.Success -> {
                            user = result.data
                            true
                        }
                        is Resource.Error -> {
                            if (result.message.startsWith("نام کاربری یا رمز عبور اشتباه است")) {
                                notUser = true
                                userDataStore.logout()
                            }
                            false
                        }
                        is Resource.MultiLoginError -> {
                            userDataStore.logout()
                            multiLogin = true
                            false
                        }
                        else -> {
                            false
                        }
                    }
                }
            } else null

            // 4. منتظر ماندن برای نتایج هر سه عملیات
            val appDetailsResult = try {
                appDetailsDeferred.await()
            } catch (e: Exception) {
                Resource.Exception(e)
            }
            val popupAdsResult = try {
                popupAdsDeferred.await()
            } catch (e: Exception) {
                Resource.Exception(e)
            }
            val authSuccess = try {
                authDeferred?.await() ?: false
            } catch (e: Exception) {
                false
            }

            // 5. بررسی موفقیت هر سه بخش (دو API + احراز هویت در صورت نیاز)
            val isAppDetailsOk = appDetailsResult is Resource.Success
            val isPopupAdsOk = popupAdsResult is Resource.Success
            val isAuthOk =
                if (initiallyLoggedIn) authSuccess else true  // اگر از قبل لاگین نبوده، نیازی به احراز نیست

            if (isAppDetailsOk && isPopupAdsOk && isAuthOk) {
                // ---------- همه چیز موفقیت‌آمیز ----------
                val appDetails = (appDetailsResult as Resource.Success).data
                val popupConfig = (popupAdsResult as Resource.Success).data

                // ذخیره اطلاعات در DataStore
                splitTunnelDataStore.saveBlockedApps(appDetails.blockedApps)
                appDetails.apply {
                    appDetailsDataStore.saveAllDetails(
                        telegramSupportId = telegramSupportId,
                        telegramChannelId = telegramChannelId,
                        buyAccLink = buyAccLink,
                        testAccLink = testAccLink,
                        rechargeAccLink = rechargeAccLink,
                        instagramLink = instagramLink,
                        defaultProtocol = defaultProtocol,
                        usagePeriodMinutes = usagePeriodMinutes,
                        aboutUs = aboutUs,
                        faqList = faq
                    )
                }

                // ارسال دیالوگ‌ها و آپدیت‌ها به MainViewModel
                mainViewModel.prepareDialogsFromResponse(
                    updateDetails = appDetails.updateDetails,
                    customDialog = appDetails.customDialogStatus,
                    adsDialog = popupConfig   // همیشه پاس داده می‌شود
                )

                // بررسی انقضای حجم یا زمان (در صورت موفقیت احراز هویت)
                if (authSuccess && user != null) {
                    val remainTraffic = user!!.remainTraffic.toDoubleOrNull() ?: 0.0
                    val expiryType = when {
                        remainTraffic <= 0.0 && user!!.isExpired -> ExpiryType.VOLUME_EXPIRED
                        user!!.isExpired || user!!.remainDays < 0 -> ExpiryType.DATE_EXPIRED
                        (user!!.remainDays > 0) and (user!!.remainDays <= 1) -> ExpiryType.DATE_EXPIRING_SOON
                        else -> null
                    }
                    expiryType?.let {
                        mainViewModel.addExpiryDialog(it, user!!.username)
                    }
                }

                // تعیین وضعیت نهایی لاگین
                val finalLoggedIn = initiallyLoggedIn && authSuccess

                _uiState.value = SplashUiState(
                    isLoading = false,
                    isLoggedIn = finalLoggedIn,
                    errorMessage = null,
                    showRetry = false
                )
            } else {
                // ---------- حداقل یکی از درخواست‌ها با خطا مواجه شده ----------
                val errorMessage = buildErrorMessage(
                    appDetailsResult,
                    popupAdsResult,
                    initiallyLoggedIn,
                    multiLogin,
                    notUser,
                    authSuccess
                )
                _uiState.value = SplashUiState(
                    isLoading = false,
                    isLoggedIn = null,
                    errorMessage = errorMessage,
                    showRetry = true
                )
            }
        }
    }

    private fun buildErrorMessage(
        appDetailsResult: Resource<AppDetails>,
        popupAdsResult: Resource<PopupAdsConfig>,
        initiallyLoggedIn: Boolean,
        multiLogin: Boolean,
        notUser: Boolean,
        authSuccess: Boolean
    ): String {
        return when {
            appDetailsResult is Resource.Error -> "خطا در دریافت  ${appDetailsResult.message}"
            appDetailsResult is Resource.Exception -> "عدم ارتباط با سرور"
            appDetailsResult is Resource.MultiLoginError -> "خطای چند دستگاه: ${appDetailsResult.devices.joinToString()}"
            popupAdsResult is Resource.Error -> "خطا در دریافت تبلیغات: ${popupAdsResult.message}"
            popupAdsResult is Resource.Exception -> "عدم ارتباط با سرور (تبلیغات): ${popupAdsResult.throwable.message ?: "مشکل شبکه"}"
            popupAdsResult is Resource.MultiLoginError -> "خطای چند دستگاه در تبلیغات: ${popupAdsResult.devices.joinToString()}"
            multiLogin -> "ورود خودکار ناموفق. دستگاه شما را از اکانت خارج کرده اند"
            notUser -> "ورود خودکار ناموفق. اکانت شما پاک شده است"
            initiallyLoggedIn -> "ورود خودکار ناموفق. شما از اکانت خارج شدید"
            !authSuccess -> "ورود خودکار ناموفق. لطفاً دوباره تلاش کنید."
            else -> "خطای ناشناخته. لطفاً بعداً تلاش کنید."
        }
    }
}