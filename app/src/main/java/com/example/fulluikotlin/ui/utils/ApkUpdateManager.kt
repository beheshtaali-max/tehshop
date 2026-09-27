package com.example.fulluikotlin.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.fulluikotlin.data.local.network.NetworkRequestExecutor
import com.example.fulluikotlin.domain.model.UpdateDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object ApkUpdateManager {
    private const val TAG = "ApkUpdateManager"
    private const val BUFFER_SIZE = 16 * 1024

    data class Progress(
        val percent: Int?,
        val downloadedBytes: Long,
        val totalBytes: Long
    )

    suspend fun downloadApk(
        context: Context,
        updateDetails: UpdateDetails,
        onProgress: (Progress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val targetFile = getTargetFile(context, updateDetails)
        if (targetFile.exists() && targetFile.length() > 0L) {
            onProgress(Progress(percent = 100, downloadedBytes = targetFile.length(), totalBytes = targetFile.length()))
            return@withContext targetFile
        }

        NetworkRequestExecutor.execute {
            downloadApkOnce(targetFile, updateDetails, onProgress)
        }
    }

    private fun downloadApkOnce(
        targetFile: File,
        updateDetails: UpdateDetails,
        onProgress: (Progress) -> Unit
    ): File {
        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.download")
        if (tmpFile.exists()) tmpFile.delete()
        targetFile.parentFile?.mkdirs()

        val connection = (URL(updateDetails.link).openConnection() as HttpURLConnection).apply {
            connectTimeout = NetworkRequestExecutor.TIMEOUT_MS.toInt()
            readTimeout = NetworkRequestExecutor.TIMEOUT_MS.toInt()
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IllegalStateException("Download failed. HTTP status: $responseCode")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            var downloadedBytes = 0L
            onProgress(Progress(percent = 0, downloadedBytes = 0L, totalBytes = totalBytes))

            connection.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val percent = if (totalBytes > 0L) {
                            ((downloadedBytes * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
                        } else {
                            null
                        }
                        onProgress(Progress(percent = percent, downloadedBytes = downloadedBytes, totalBytes = totalBytes))
                    }
                    output.flush()
                }
            }

            if (!tmpFile.renameTo(targetFile)) {
                tmpFile.copyTo(targetFile, overwrite = true)
                tmpFile.delete()
            }

            onProgress(Progress(percent = 100, downloadedBytes = targetFile.length(), totalBytes = targetFile.length()))
            return targetFile
        } finally {
            connection.disconnect()
        }
    }

    fun installApk(context: Context, apkPath: String): Boolean {
        val apkFile = File(apkPath)
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            Log.w(TAG, "APK file does not exist: $apkPath")
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return false
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
        return true
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "نامشخص"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    private fun getTargetFile(context: Context, updateDetails: UpdateDetails): File {
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
            .ifBlank { "app" }
            .sanitizeForFileName()
        val version = updateDetails.version.ifBlank { "update" }.sanitizeForFileName()
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(downloadsDir, "$appName-$version.apk")
    }

    private fun String.sanitizeForFileName(): String {
        return replace(Regex("[^A-Za-z0-9._\u0600-\u06FF-]+"), "_")
            .trim('_', '.', '-')
            .ifBlank { "app" }
    }
}
