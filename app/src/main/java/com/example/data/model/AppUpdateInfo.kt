package com.example.data.model

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val tagName: String,
    val title: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fallbackDownloadUrl: String = "",
    val apkFileName: String = "MukulPlus-latest.apk",
    val fileSizeBytes: Long = 0L,
    val publishedAt: String = "",
    val htmlUrl: String = "https://github.com/mdmukul120/Mukul-plusbd/releases",
    val isUpdateAvailable: Boolean = false
) {
    fun getFormattedFileSize(): String {
        if (fileSizeBytes <= 0L) return "প্রায় ২৯ এমবি"
        val mb = fileSizeBytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }
}

data class UpdateDownloadProgress(
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null,
    val downloadedApkPath: String? = null
)
