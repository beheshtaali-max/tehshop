package com.example.fulluikotlin.domain.utils

/**
 * Stable, non-technical support codes shown to users.
 * The code is intentionally opaque; the meaning is documented for support/development only.
 */
enum class AppErrorCode(
    val code: String,
    val userMessage: String,
    val reportToFirebase: Boolean
) {
    AUTH_INVALID_CREDENTIALS(
        code = "AU01",
        userMessage = "نام کاربری یا رمز عبور صحیح نیست.",
        reportToFirebase = false
    ),
    AUTH_DEVICE_LIMIT(
        code = "AU02",
        userMessage = "ظرفیت دستگاه‌های فعال این حساب تکمیل شده است.",
        reportToFirebase = false
    ),
    AUTH_ACCOUNT_REJECTED(
        code = "AU03",
        userMessage = "در حال حاضر امکان ورود به این حساب وجود ندارد. لطفاً با پشتیبانی تماس بگیرید.",
        reportToFirebase = true
    ),
    AUTH_INVALID_RESPONSE(
        code = "AU04",
        userMessage = "پاسخ ورود قابل پردازش نبود. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    DEVICE_DELETE_FAILED(
        code = "DV01",
        userMessage = "حذف دستگاه انجام نشد. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    SESSION_INCOMPLETE(
        code = "SS01",
        userMessage = "اطلاعات ورود نیاز به ثبت مجدد دارد. لطفاً دوباره وارد شوید.",
        reportToFirebase = true
    ),
    SESSION_READ_FAILED(
        code = "SS02",
        userMessage = "خواندن اطلاعات ورود انجام نشد. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    SESSION_CLEAR_FAILED(
        code = "SS03",
        userMessage = "بازنشانی اطلاعات ورود کامل نشد. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    NETWORK_OFFLINE(
        code = "NW01",
        userMessage = "اتصال اینترنت برقرار نیست. اینترنت را بررسی کنید.",
        reportToFirebase = false
    ),
    NETWORK_TIMEOUT(
        code = "NW02",
        userMessage = "پاسخی از سرور دریافت نشد. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    SERVER_UNAVAILABLE(
        code = "NW03",
        userMessage = "سرویس موقتاً در دسترس نیست. لطفاً کمی بعد دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    RESPONSE_INVALID(
        code = "DT01",
        userMessage = "اطلاعات دریافتی قابل پردازش نبود. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    LOCAL_STORAGE_FAILED(
        code = "DT02",
        userMessage = "ذخیره اطلاعات برنامه انجام نشد. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    SMART_SERVER_SCAN_FAILED(
        code = "SV01",
        userMessage = "بررسی خودکار سرورها انجام نشد. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    APP_DETAILS_FAILED(
        code = "BS01",
        userMessage = "دریافت تنظیمات برنامه انجام نشد. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    ),
    POPUP_DETAILS_FAILED(
        code = "BS02",
        userMessage = "دریافت اطلاعات اولیه کامل نشد. لطفاً دوباره تلاش کنید.",
        reportToFirebase = false
    ),
    UNKNOWN(
        code = "UN01",
        userMessage = "خطای پیش‌بینی‌نشده‌ای رخ داد. لطفاً دوباره تلاش کنید.",
        reportToFirebase = true
    );

    fun displayMessage(supportCode: String = code): String =
        "$userMessage\nکد پیگیری: $supportCode"

    companion object {
        fun fromCode(code: String?): AppErrorCode? {
            val baseCode = code?.substringBefore('-')
            return entries.firstOrNull { it.code == baseCode }
        }
    }
}
