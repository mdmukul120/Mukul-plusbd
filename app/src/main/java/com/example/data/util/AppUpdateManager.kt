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

    // GitHub Releases API endpoints for scanning updates
    private const val RELEASES_API_LATEST = "https://api.github.com/repos/mdmukul120/Mukul-plusbd/releases/latest"
    private const val RELEASE_METADATA_JSON_URL = "https://github.com/mdmukul120/Mukul-plusbd/releases/latest/download/app-update.json"
    private const val DIRECT_LATEST_APK_URL = "https://github.com/mdmukul120/Mukul-plusbd/releases/latest/download/MukulPlus-latest.apk"

    // 3 times a day = every 8 hours (24 / 3 = 8)
    private const val SCAN_INTERVAL_MS = 8 * 60 * 60 * 1000L
    private const val PREFS_NAME = "mukul_app_update_prefs"
    private const val KEY_LAST_SCAN_TIME = "last_scan_time"
    private const val KEY_LAST_SEEN_TAG = "last_seen_tag"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
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
     * Checks if current installed version matches the release version.
     * If matched, returns true (no update needed, download disabled).
     */
    fun isVersionMatched(
        remoteVersionName: String,
        remoteVersionCode: Int,
        installedVersionName: String,
        installedVersionCode: Int
    ): Boolean {
        val cleanRemote = remoteVersionName.replace("v", "", ignoreCase = true).trim()
        val cleanInstalled = installedVersionName.replace("v", "", ignoreCase = true).trim()

        if (cleanRemote.equals(cleanInstalled, ignoreCase = true)) {
            return true
        }

        if (remoteVersionCode > 0 && installedVersionCode > 0) {
            if (installedVersionCode >= remoteVersionCode) {
                return true
            }
        }

        // Semantic check: if installed version is >= remote version, it is matched
        if (compareVersionStrings(cleanInstalled, cleanRemote) >= 0) {
            return true
        }

        return false
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

    /**
     * Checks if 8 hours have elapsed since the last scan (3 times a day)
     */
    fun isDueForPeriodicScan(context: Context): Boolean {
        val lastScan = getPrefs(context).getLong(KEY_LAST_SCAN_TIME, 0L)
        val now = System.currentTimeMillis()
        return (now - lastScan) >= SCAN_INTERVAL_MS
    }

    fun recordScanTime(context: Context) {
        getPrefs(context).edit().putLong(KEY_LAST_SCAN_TIME, System.currentTimeMillis()).apply()
    }

    fun dismissUpdateTag(context: Context, tag: String) {
        getPrefs(context).edit().putString(KEY_DISMISSED_TAG, tag).apply()
    }

    fun isTagDismissed(context: Context, tag: String): Boolean {
        val dismissed = getPrefs(context).getString(KEY_DISMISSED_TAG, null)
        return dismissed != null && dismissed == tag
    }

    /**
     * Scan the releases endpoint and match with current app version.
     * @param force If true, bypasses the 8-hour interval check (used for manual checks).
     */
    suspend fun checkForUpdates(context: Context, force: Boolean = false): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val (installedVersionName, installedVersionCode) = getInstalledVersion(context)

        if (!force && !isDueForPeriodicScan(context)) {
            return@withContext null
        }

        try {
            // 1. Try parsing primary release API
            var updateInfo = fetchFromReleasesApi(installedVersionName, installedVersionCode)

            // 2. Fallback to direct app-update.json metadata
            if (updateInfo == null) {
                updateInfo = fetchFromUpdateJson(installedVersionName, installedVersionCode)
            }

            if (updateInfo != null) {
                recordScanTime(context)
                getPrefs(context).edit().putString(KEY_LAST_SEEN_TAG, updateInfo.tagName).apply()
                return@withContext updateInfo
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check network issue: ${e.message}")
        }

        null
    }

    private fun fetchFromReleasesApi(installedVersionName: String, installedVersionCode: Int): AppUpdateInfo? {
        try {
            val request = Request.Builder()
                .url(RELEASES_API_LATEST)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "MukulPlusApp-Updater")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", "Mukul plus $tagName")
            val rawReleaseNotes = json.optString("body", "")
            val publishedAt = json.optString("published_at", "")

            var downloadUrl = ""
            var apkFileName = "MukulPlus-latest.apk"
            var fileSizeBytes = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkFileName = name
                        fileSizeBytes = asset.optLong("size", 0L)
                        if (name.contains("latest", ignoreCase = true)) break
                    }
                }
            }

            if (downloadUrl.isEmpty()) {
                downloadUrl = DIRECT_LATEST_APK_URL
            }

            val (remoteVersionName, remoteVersionCode) = parseVersionFromTag(tagName, title)

            // Match release version with current installed version
            val isMatched = isVersionMatched(
                remoteVersionName = remoteVersionName,
                remoteVersionCode = remoteVersionCode,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
            )

            // If matched, isUpdateAvailable is false (no download). If not matched, update is available.
            val isAvailable = !isMatched

            return AppUpdateInfo(
                versionCode = remoteVersionCode,
                versionName = remoteVersionName,
                tagName = tagName,
                title = title,
                releaseNotes = sanitizeReleaseNotes(rawReleaseNotes),
                downloadUrl = downloadUrl,
                fallbackDownloadUrl = DIRECT_LATEST_APK_URL,
                apkFileName = apkFileName,
                fileSizeBytes = if (fileSizeBytes > 0L) fileSizeBytes else 30293657L,
                publishedAt = publishedAt,
                isUpdateAvailable = isAvailable,
                isCurrentVersionMatched = isMatched
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse releases api: ${e.message}")
            return null
        }
    }

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

            val isMatched = isVersionMatched(
                remoteVersionName = versionName,
                remoteVersionCode = versionCode,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode
            )
            val isAvailable = !isMatched

            return AppUpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                tagName = tagName,
                title = "Mukul plus $tagName",
                releaseNotes = "• লাইভ টিভি ও ওটিটি স্ট্রিমিং অভিজ্ঞতা উন্নত করা হয়েছে\n• দ্রুত বাফারলেস প্লেব্যাক\n• বাগ ফিক্স এবং সিস্টেম স্টেবিলিটি বৃদ্ধি",
                downloadUrl = downloadUrl,
                fallbackDownloadUrl = DIRECT_LATEST_APK_URL,
                apkFileName = apkName,
                fileSizeBytes = 30293657L,
                publishedAt = publishedAt,
                isUpdateAvailable = isAvailable,
                isCurrentVersionMatched = isMatched
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse app-update.json fallback: ${e.message}")
            return null
        }
    }

    /**
     * Sanitizes release notes by removing all GitHub repo links, workflows, commits, and URLs.
     * Retains ONLY clean bullet points describing the actual features.
     */
    private fun sanitizeReleaseNotes(notes: String): String {
        val cleanList = notes
            .lines()
            .map { it.trim() }
            .filter { line ->
                val l = line.lowercase()
                !l.contains("github") && !l.contains("workflow") && !l.contains("commit") &&
                !l.contains("compare/") && !l.contains("actions") && !l.contains("sha") &&
                !l.contains("package name") && !l.contains("build number") && !l.contains("http://") &&
                !l.contains("https://") && !l.startsWith("##") && !l.startsWith("###") &&
                line.isNotBlank()
            }
            .map { line ->
                var l = line.replace("**", "").replace("`", "").trim()
                if (!l.startsWith("•") && !l.startsWith("-")) {
                    l = "• $l"
                } else if (l.startsWith("-")) {
                    l = "• " + l.substring(1).trim()
                }
                l
            }

        return if (cleanList.isNotEmpty()) {
            cleanList.take(4).joinToString("\n")
        } else {
            "• লাইভ টিভি ও ওটিটি স্ট্রিমিং অভিজ্ঞতা উন্নত করা হয়েছে\n• দ্রুত বাফারলেস প্লেব্যাক\n• বাগ ফিক্স এবং সিস্টেম স্টেবিলিটি বৃদ্ধি"
        }
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
     * Downloads APK directly inside application with streaming progress.
     * If version is matched (current version), download is prevented.
     */
    suspend fun downloadApk(
        context: Context,
        updateInfo: AppUpdateInfo,
        onProgressUpdate: (UpdateDownloadProgress) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        // Enforce rule: if version is matched, do NOT download
        if (updateInfo.isCurrentVersionMatched) {
            Log.w(TAG, "Download rejected: Current version matches release version.")
            return@withContext null
        }

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
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "MukulPlusApp-Downloader")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) continue

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

                return@withContext apkFile
            } catch (e: Exception) {
                Log.w(TAG, "Download attempt error: ${e.message}")
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

    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open settings: ${e.message}")
            }
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!apkFile.exists()) return false

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
            Log.w(TAG, "Failed to launch installer: ${e.message}")
            false
        }
    }

    fun resetProgress() {
        _downloadProgress.value = UpdateDownloadProgress()
    }
}
