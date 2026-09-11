package com.tushar.voidplayer.utils

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL


object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/TUSHAR91316/Void-Player/releases/latest"
    private fun getAppVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "2.3"
        } catch (_: Throwable) {
            "2.3"
        }
    }

    suspend fun checkForUpdates(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVersion = getAppVersionName(context)
        val isFdroid = isInstalledViaFdroid(context)
        try {
            val url = URL(GITHUB_API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "VoidPlayer-Android/$currentVersion")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 4000
                readTimeout = 4000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                val tagName = json.optString("tag_name", "").trim()
                val releaseName = json.optString("name", tagName)
                val body = json.optString("body", "Bug fixes and performance improvements.")
                val htmlUrl = json.optString("html_url", "https://github.com/TUSHAR91316/Void-Player/releases")

                // Find APK asset download URL
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            val url = asset.optString("browser_download_url", "")
                            if (url.isNotBlank()) {
                                apkUrl = url
                            }
                            break
                        }
                    }
                }

                val cleanRemoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
                val isNewer = isVersionNewer(cleanRemoteVersion, currentVersion)

                return@withContext UpdateInfo(
                    isUpdateAvailable = isNewer,
                    latestVersion = cleanRemoteVersion.ifBlank { currentVersion },
                    currentVersion = currentVersion,
                    releaseNotes = body,
                    downloadUrl = apkUrl ?: htmlUrl,
                    releasePageUrl = htmlUrl,
                    isFdroidInstall = isFdroid
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Update check request failed: ${e.message}")
        }

        UpdateInfo(
            isUpdateAvailable = false,
            latestVersion = currentVersion,
            currentVersion = currentVersion,
            releaseNotes = "",
            downloadUrl = null,
            releasePageUrl = "https://github.com/TUSHAR91316/Void-Player/releases",
            isFdroidInstall = isFdroid
        )
    }

    private fun isInstalledViaFdroid(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
            installer?.contains("fdroid", ignoreCase = true) == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        if (remote.isBlank()) return false
        val remoteParts = remote.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = current.split('.').mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
