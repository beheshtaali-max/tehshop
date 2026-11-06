package com.example.fulluikotlin.infrastructure.reporting

import android.util.Log
import com.example.fulluikotlin.domain.utils.AppErrorCode
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends only selected, actionable non-fatal errors to Crashlytics.
 * Never pass usernames, passwords, tokens, device IDs or full request/response bodies in [details].
 */
object FirebaseErrorReporter {
    private const val TAG = "FirebaseErrorReporter"
    private const val REPORT_COOLDOWN_MS = 10 * 60 * 1000L
    private const val MAX_DETAIL_LENGTH = 160

    private val lastReportAt = ConcurrentHashMap<String, Long>()

    fun report(
        errorCode: AppErrorCode,
        context: String,
        throwable: Throwable? = null,
        supportCode: String = errorCode.code,
        details: Map<String, String?> = emptyMap()
    ) {
        if (!errorCode.reportToFirebase) return

        val throttleKey = "$supportCode:$context"
        val now = System.currentTimeMillis()
        val previous = lastReportAt[throttleKey]
        if (previous != null && now - previous < REPORT_COOLDOWN_MS) return
        lastReportAt[throttleKey] = now

        try {
            val keysBuilder = CustomKeysAndValues.Builder()
                .putString("support_code", supportCode.take(MAX_DETAIL_LENGTH))
                .putString("error_context", context.take(MAX_DETAIL_LENGTH))

            details.forEach { (key, value) ->
                if (!value.isNullOrBlank()) {
                    keysBuilder.putString(
                        key.take(40),
                        sanitize(value)
                    )
                }
            }

            val reportable = throwable ?: AppNonFatalException(errorCode.code)
            FirebaseCrashlytics.getInstance().recordException(reportable, keysBuilder.build())
        } catch (reportingError: Exception) {
            // Reporting must never affect the app's normal error handling.
            Log.w(TAG, "Crashlytics reporting failed", reportingError)
        }
    }

    private fun sanitize(value: String): String {
        return value
            .replace(Regex("(?i)(pass(word)?|token|authorization|api[_-]?key)\\s*[:=]\\s*[^,;\\s]+"), "$1=[redacted]")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .take(MAX_DETAIL_LENGTH)
    }

    private class AppNonFatalException(code: String) : Exception("App non-fatal error [$code]")
}
