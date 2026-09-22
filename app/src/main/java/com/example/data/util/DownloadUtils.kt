package com.example.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

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
     * Explicitly opens the given download link in Google Chrome.
     * If Chrome is not installed on the device, seamlessly falls back to the system browser chooser.
     */
    fun openDownloadInChrome(context: Context, url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, "ডাউনলোড লিংক পাওয়া যায়নি (Download URL not found)", Toast.LENGTH_SHORT).show()
            return
        }

        val trimmedUrl = url.trim()

        try {
            // Priority 1: Launch directly with Google Chrome package
            val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmedUrl)).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chromeIntent)
            Toast.makeText(context, "ক্রোম ব্রাউজারে ডাউনলোড খোলা হচ্ছে...", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            // Priority 2: Fallback to general browser chooser if Chrome package is absent
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmedUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(genericIntent, "ক্রোম বা ব্রাউজারে ডাউনলোড করুন").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                Toast.makeText(context, "ব্রাউজারে ডাউনলোড খোলা হচ্ছে...", Toast.LENGTH_SHORT).show()
            } catch (fallbackError: Exception) {
                Toast.makeText(
                    context,
                    "ডাউনলোড লিংক ওপেন করতে সমস্যা: ${fallbackError.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
