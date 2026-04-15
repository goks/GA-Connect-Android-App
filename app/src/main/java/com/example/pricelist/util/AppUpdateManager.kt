package com.example.pricelist.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class AppUpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val apkUrl: String
)

object AppUpdateManager {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/goks/GA-Connect-Android-App/releases/latest"

    suspend fun checkForUpdate(currentVersionName: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val json = getText(LATEST_RELEASE_URL)
        val release = JSONObject(json)
        val tagName = release.getString("tag_name")
        val latestVersionName = tagName.removePrefix("v").removePrefix("V")
        if (compareVersions(latestVersionName, currentVersionName) <= 0) {
            return@withContext null
        }

        val assets = release.getJSONArray("assets")
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name").lowercase(Locale.US)
            if (name.endsWith(".apk")) {
                apkUrl = asset.getString("browser_download_url")
                break
            }
        }

        val downloadUrl = apkUrl ?: error("The latest GitHub release does not include an APK asset.")
        AppUpdateInfo(
            versionName = latestVersionName,
            releaseUrl = release.getString("html_url"),
            apkUrl = downloadUrl
        )
    }

    suspend fun downloadApk(context: Context, update: AppUpdateInfo): File = withContext(Dispatchers.IO) {
        val target = File(context.externalCacheDir ?: context.cacheDir, "ga-connect-${update.versionName}.apk")
        URL(update.apkUrl).openConnection().let { connection ->
            connection as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Accept", "application/octet-stream")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()
        }
        target
    }

    fun canInstallPackages(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "GA-Connect-Android-App")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                error("GitHub update check failed: HTTP $code")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = left.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val rightParts = right.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val count = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until count) {
            val diff = (leftParts.getOrNull(i) ?: 0) - (rightParts.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }
}
