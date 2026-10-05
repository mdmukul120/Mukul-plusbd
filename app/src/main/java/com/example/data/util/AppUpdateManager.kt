package com.example.data.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.AppUpdateInfo
import com.example.data.model.UpdateDownloadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"

    // Primary GitHub Releases endpoint and metadata
    const val GITHUB_RELEASES_PAGE_URL = "https://github.com/mdmukul120/Mukul-plusbd/releases"
    private const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/mdmukul120/Mukul-plusbd/releases/latest"
    private const val RELEASE_METADATA_JSON_URL = "https://github.com/mdmukul120/Mukul-plusbd/releases/latest/download/app-update.json"
    private const val DIRECT_LATEST_APK_URL = "https://github.com/mdmukul120/Mukul-plusbd/releases/latest/download/MukulPlus-latest.apk"

    // 3 times a day = every 8 hours (24 / 3 = 8)
    private const val SCAN_INTERVAL_MS = 8 * 60 * 60 * 1000L // 8 hours in milliseconds
    private const val PREFS_NAME = "mukul_app_update_prefs"
    private const val KEY_LAST_SCAN_TIME = "last_scan_time"
    private const val KEY_LAST_SEEN_TAG = "last_seen_tag"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _downloadProgress = MutableStateFlow(UpdateDownloadProgress())
    val downloadProgress: StateFlow<UpdateDownloadProgress> = _downloadProgress.asStateFlow()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Get installed app version details
     */
    fun getInstalledVersion(context: Context): Pair<String, Int> {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val vName = pInfo.versionName ?: "1.0"
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            Pair(vName, vCode)
        } catch (_: Exception) {
            Pair("1.0", 1)
        }
    }

    /**
     * Checks if 8 hours (3 times a day) have elapsed since the last scan
     */
    fun isDueForPeriodicScan(context: Context): Boolean {
        val lastScan = getPrefs(context).getLong(KEY_LAST_SCAN_TIME, 0L)
        val now = System.currentTimeMillis()
        return (now - lastScan) >= SCAN_INTERVAL_MS
    }

    /**
     * Records that a scan was performed right now
     */
    fun recordScanTime(context: Context) {
        getPrefs(context).edit().putLong(KEY_LAST_SCAN_TIME, System.currentTimeMillis()).apply()
    }

    /**
     * Mark a specific release tag as dismissed by the user
     */
    fun dismissUpdateTag(context: Context, tag: String) {
        getPrefs(context).edit().putString(KEY_DISMISSED_TAG, tag).apply()
    }

    /**
     * Check if a specific release tag was dismissed by the user
     */
    fun isTagDismissed(context: Context, tag: String): Boolean {
        val dismissed = getPrefs(context).getString(KEY_DISMISSED_TAG, null)
        return dismissed != null && dismissed == tag
    }

    /**
     * Check for updates on GitHub releases.
     * @param force If true, bypasses the 8-hour periodic limit and force scans.
     */
    suspend fun checkForUpdates(context: Context, force: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val (installedVersionName, installedVersionCode) = getInstalledVersion(context)

        if (!force && !isDueForPeriodicScan(context)) {
            // Not yet 8 hours since last scan, skip automatic network scan
            return@withContext null
        }

        try {
            // 1. Try parsing primary GitHub API
            var updateInfo = fetchFromGitHubApi(installedVersionName, installedVersionCode)

            // 2. If GitHub API failed or rate-limited, try the direct app-update.json asset
            if (updateInfo == null) {
                updateInfo = fetchFromUpdateJson(installedVersionName, installedVersionCode)
            }

            if (updateInfo != null) {
                recordScanTime(context)
                getPrefs(context).edit().putString(KEY_LAST_SEEN_TAG, updateInfo.tagName).apply()
                return@withContext updateInfo
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
        }

        null
    }

    /**
     * Fetch latest release from GitHub API
     */
    private fun fetchFromGitHubApi(installedVersionName: String, installedVersionCode: Int): AppUpdateInfo? {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_LATEST_RELEASE)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MukulPlusApp-Updater")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub API returned code ${response.code}")
                return null
            }

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", "Mukul plus $tagName")
            val releaseNotes = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", GITHUB_RELEASES_PAGE_URL)

            // Parse APK asset from assets array
            var downloadUrl = ""
            var apkFileName = "MukulPlus-latest.apk"
            var fileSizeBytes = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                // Priority 1: MukulPlus-latest.apk or specific version apk
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkFileName = name
                        fileSizeBytes = asset.optLong("size", 0L)
                        if (name.contains("latest", ignoreCase = true)) {
                            break
                        }
                    }
                }
            }

            if (downloadUrl.isEmpty()) {
                downloadUrl = DIRECT_LATEST_APK_URL
            }

            // Extract remote version code and name from tag
            val (remoteVersionName, remoteVersionCode) = parseVersionFromTag(tagName, title)

            val isAvailable = isRemoteNewer(
                remoteVersionName = remoteVersionName,
                remoteVersionCode = remoteVersionCode,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
            )

            return AppUpdateInfo(
                versionCode = remoteVersionCode,
                versionName = remoteVersionName,
                tagName = tagName,
                title = title,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                fallbackDownloadUrl = DIRECT_LATEST_APK_URL,
                apkFileName = apkFileName,
                fileSizeBytes = fileSizeBytes,
                publishedAt = publishedAt,
                htmlUrl = htmlUrl,
                isUpdateAvailable = isAvailable
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse GitHub API release", e)
            return null
        }
    }

    /**
     * Fallback: Fetch directly from app-update.json published with the release
     */
    private fun fetchFromUpdateJson(installedVersionName: String, installedVersionCode: Int): AppUpdateInfo? {
        try {
            val request = Request.Builder()
                .url(RELEASE_METADATA_JSON_URL)
                .header("User-Agent", "MukulPlusApp-Updater")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "v1.0.23")
            val versionName = json.optString("version_name", "1.0.23")
            val versionCode = json.optInt("version_code", 23)
            val apkName = json.optString("apk_name", "MukulPlus-latest.apk")
            val downloadUrl = json.optString("download_url", DIRECT_LATEST_APK_URL)
            val publishedAt = json.optString("published_at", "")
            val releaseUrl = json.optString("release_url", GITHUB_RELEASES_PAGE_URL)

            val isAvailable = isRemoteNewer(
                remoteVersionName = versionName,
                remoteVersionCode = versionCode,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
            )

            return AppUpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                tagName = tagName,
                title = "Mukul plus $tagName",
                releaseNotes = "## 🎬 Mukul plus স্বয়ংক্রিয় অ্যাপ আপডেট ($tagName)\n\n" +
                        "• লাইভ BDIX TV ও স্পোর্টস চ্যানেলের উন্নত স্ট্রিমিং\n" +
                        "• বাংলা ওটিটি ও Bongo বিডি নাটক ও সিনেমা ক্যাটালগ\n" +
                        "• ইন-অ্যাপ অটোমেটিক আপডেট সিস্টেম\n" +
                        "• পারফরম্যান্স ও বাফারহীন ভিডিও প্লেব্যাক",
                downloadUrl = downloadUrl,
                fallbackDownloadUrl = DIRECT_LATEST_APK_URL,
                apkFileName = apkName,
                fileSizeBytes = 30293657L,
                publishedAt = publishedAt,
                htmlUrl = releaseUrl,
                isUpdateAvailable = isAvailable
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse app-update.json fallback", e)
            return null
        }
    }

    /**
     * Determines whether the remote version is newer than the currently installed version
     */
    fun isRemoteNewer(
        remoteVersionName: String,
        remoteVersionCode: Int,
        installedVersionName: String,
        installedVersionCode: Int
    ): Boolean {
        // Compare versionCode first if valid
        if (remoteVersionCode > 0 && installedVersionCode > 0) {
            if (remoteVersionCode > installedVersionCode) return true
            if (remoteVersionCode < installedVersionCode) return false
        }

        // Fallback: Semantic version comparison (e.g. 1.0.23 vs 1.0)
        return compareVersionStrings(remoteVersionName, installedVersionName) > 0
    }

    private fun compareVersionStrings(v1: String, v2: String): Int {
        val parts1 = v1.replace("v", "").replace("V", "").split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.replace("v", "").replace("V", "").split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }

    private fun parseVersionFromTag(tagName: String, title: String): Pair<String, Int> {
        val cleanTag = tagName.replace("v", "").replace("V", "").trim()
        val parts = cleanTag.split(".")
        var code = 1
        if (parts.size >= 3) {
            code = parts.last().toIntOrNull() ?: 1
        }
        return Pair(if (cleanTag.isNotEmpty()) cleanTag else "1.0", code)
    }

    /**
     * Downloads the APK file directly inside the application with streaming progress.
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgressUpdate: (UpdateDownloadProgress) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        _downloadProgress.value = UpdateDownloadProgress(isDownloading = true)
        onProgressUpdate(_downloadProgress.value)

        val targetDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir, "updates")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val apkFile = File(targetDir, "MukulPlus-v${updateInfo.versionName}.apk")
        if (apkFile.exists()) {
            apkFile.delete()
        }

        val urlsToTry = listOfNotNull(
            updateInfo.downloadUrl.takeIf { it.isNotBlank() },
            updateInfo.fallbackDownloadUrl.takeIf { it.isNotBlank() },
            DIRECT_LATEST_APK_URL
        ).distinct()

        for (downloadUrl in urlsToTry) {
            try {
                Log.d(TAG, "Starting APK download from: $downloadUrl")
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "MukulPlusApp-Downloader")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.w(TAG, "Download attempt failed with HTTP ${response.code} from $downloadUrl")
                    continue
                }

                val body = response.body ?: continue
                val totalBytes = if (body.contentLength() > 0) body.contentLength() else updateInfo.fileSizeBytes
                var downloadedBytes = 0L

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var lastReportTime = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastReportTime > 200 || downloadedBytes == totalBytes) {
                        lastReportTime = now
                        val fraction = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.5f
                        val progress = UpdateDownloadProgress(
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalBytes,
                            progress = fraction,
                            isDownloading = true,
                            isCompleted = false,
                            downloadedApkPath = apkFile.absolutePath
                        )
                        _downloadProgress.value = progress
                        onProgressUpdate(progress)
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                val completedProgress = UpdateDownloadProgress(
                    bytesDownloaded = downloadedBytes,
                    totalBytes = downloadedBytes,
                    progress = 1.0f,
                    isDownloading = false,
                    isCompleted = true,
                    downloadedApkPath = apkFile.absolutePath
                )
                _downloadProgress.value = completedProgress
                onProgressUpdate(completedProgress)

                Log.d(TAG, "APK successfully downloaded to ${apkFile.absolutePath} ($downloadedBytes bytes)")
                return@withContext apkFile
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK from $downloadUrl", e)
            }
        }

        val failedProgress = UpdateDownloadProgress(
            isDownloading = false,
            isCompleted = false,
            error = "ডাউনলোড সম্পন্ন করা যায়নি। অনুগ্রহ করে ইন্টারনেট সংযোগ পরীক্ষা করুন।"
        )
        _downloadProgress.value = failedProgress
        onProgressUpdate(failedProgress)
        null
    }

    /**
     * Checks if the app has permission to install unknown apps (Android 8.0+)
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Opens the system settings screen where the user can grant permission to install unknown apps.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open install permission settings", e)
            }
        }
    }

    /**
     * Triggers the Android PackageInstaller using FileProvider
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist at ${apkFile.absolutePath}")
            return false
        }

        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting APK installer", e)
            false
        }
    }

    /**
     * Reset download progress state
     */
    fun resetProgress() {
        _downloadProgress.value = UpdateDownloadProgress()
    }
}
