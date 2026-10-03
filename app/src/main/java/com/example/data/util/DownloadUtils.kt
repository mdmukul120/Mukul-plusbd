package com.example.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.data.download.InAppDownloader
import com.example.data.model.MusicTrack

object DownloadUtils {

    /**
     * Identifies if a URL is likely a direct or indirect download resource.
     */
    fun isDownloadUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()

        // Common media/archive/app extensions
        val extensions = listOf(
            ".mp4", ".mkv", ".avi", ".mov", ".flv", ".webm", ".ts",
            ".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg",
            ".zip", ".rar", ".7z", ".tar", ".gz", ".apk", ".iso", ".torrent", ".pdf"
        )
        if (extensions.any { lower.contains(it) }) return true

        // Known download hubs & query signatures
        val downloadKeywords = listOf(
            "download", "dl=1", "action=download", "export=download",
            "mediafire.com", "mega.nz", "pixeldrain.com", "1fichier.com", "gofile.io",
            "drive.google.com/uc", "drive.google.com/open?id=", "dropbox.com/s",
            "fastdl", "direct-download", "response-content-disposition"
        )
        return downloadKeywords.any { lower.contains(it) }
    }

    /**
     * Downloads a MusicTrack directly inside the application without navigating to browser.
     */
    fun downloadMusic(context: Context, track: MusicTrack) {
        val taskId = InAppDownloader.downloadMusicTrack(context, track)
        Toast.makeText(context, "গানটি অ্যাপ্লিকেশনে ডাউনলোড হচ্ছে! ডাউনলোড পেজে প্লে করতে পারবেন", Toast.LENGTH_LONG).show()
    }

    /**
     * In-app download handler replacing external browser redirect.
     * Starts download directly inside the app.
     */
    fun openDownloadInChrome(context: Context, url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "ডাউনলোড লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            return
        }

        val trimmedUrl = url.trim()
        val isAudio = trimmedUrl.contains(".mp3") || trimmedUrl.contains(".m4a") ||
                trimmedUrl.contains("audio") || trimmedUrl.contains("saavncdn") ||
                trimmedUrl.contains("jiosaavn")

        val title = if (isAudio) "অফলাইন গান" else "মিডিয়া ফাইল"
        val quality = if (isAudio) "320kbps MP3" else "HD"
        val slug = if (isAudio) "music_${System.currentTimeMillis()}" else "file_${System.currentTimeMillis()}"

        try {
            InAppDownloader.startDownload(
                context = context,
                movieSlug = slug,
                title = title,
                poster = "",
                quality = quality,
                downloadUrl = trimmedUrl
            )
            Toast.makeText(
                context,
                "অ্যাপ্লিকেশনে ডাউনলোড শুরু হয়েছে! ডাউনলোড পেজে দেখতে পারবেন",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "ডাউনলোড শুরু করতে সমস্যা: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
