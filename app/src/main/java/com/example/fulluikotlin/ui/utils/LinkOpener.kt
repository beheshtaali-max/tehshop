package com.example.fulluikotlin.ui.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object LinkOpener {

    private const val TAG = "LinkOpener"

    private val telegramPackages = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram"
    )

    private val instagramPackages = listOf(
        "com.instagram.android"
    )

    fun openTelegram(context: Context, username: String?) {
        if (username.isNullOrBlank()) return

        val normalized = username.trim()
        val webUrl = buildTelegramWebUrl(normalized)
        val appUri = buildTelegramAppUri(normalized)

        if (openInInstalledApp(context, appUri, telegramPackages)) return
        if (openInInstalledApp(context, Uri.parse(webUrl), telegramPackages)) return

        openUrl(context, webUrl)
    }

    fun openInstagram(context: Context, handle: String?) {
        if (handle.isNullOrBlank()) return

        val normalized = handle.trim()
        val webUrl = buildInstagramWebUrl(normalized)
        val appUri = buildInstagramAppUri(normalized)

        if (openInInstalledApp(context, appUri, instagramPackages)) return
        if (openInInstalledApp(context, Uri.parse(webUrl), instagramPackages)) return

        openUrl(context, webUrl)
    }

    fun openUrl(context: Context, url: String) {
        if (url.isBlank()) {
            Log.w(TAG, "URL is blank, cannot open")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                Log.w(TAG, "No activity found to handle URL: $url")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: $url", e)
        }
    }

    private fun openInInstalledApp(
        context: Context,
        uri: Uri,
        packageNames: List<String>
    ): Boolean {
        for (packageName in packageNames) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try next known package, then fall back to web.
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open $uri with package=$packageName", e)
            }
        }

        return false
    }

    private fun buildTelegramWebUrl(value: String): String {
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("tg://")) return "https://t.me/${extractTelegramUsername(value)}"
        return "https://t.me/${value.removePrefix("@")}" 
    }

    private fun buildTelegramAppUri(value: String): Uri {
        val username = extractTelegramUsername(value)
        return if (username.isNotBlank()) {
            Uri.parse("tg://resolve?domain=$username")
        } else {
            Uri.parse(buildTelegramWebUrl(value))
        }
    }

    private fun extractTelegramUsername(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("tg://") -> {
                Uri.parse(trimmed).getQueryParameter("domain").orEmpty().removePrefix("@")
            }

            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                val uri = Uri.parse(trimmed)
                uri.lastPathSegment.orEmpty().removePrefix("@")
            }

            else -> trimmed.removePrefix("@")
        }
    }

    private fun buildInstagramWebUrl(value: String): String {
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("instagram://")) return "https://instagram.com/${extractInstagramUsername(value)}"
        return "https://instagram.com/${value.removePrefix("@")}" 
    }

    private fun buildInstagramAppUri(value: String): Uri {
        val username = extractInstagramUsername(value)
        return if (username.isNotBlank()) {
            Uri.parse("instagram://user?username=$username")
        } else {
            Uri.parse(buildInstagramWebUrl(value))
        }
    }

    private fun extractInstagramUsername(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("instagram://") -> {
                Uri.parse(trimmed).getQueryParameter("username").orEmpty().removePrefix("@")
            }

            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                val uri = Uri.parse(trimmed)
                uri.pathSegments.firstOrNull().orEmpty().removePrefix("@")
            }

            else -> trimmed.removePrefix("@")
        }
    }
}
