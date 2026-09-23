package com.example.data.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class YouTubeResolution(
    val format: String,
    val title: String,
    val subtitle: String,
    val isAudio: Boolean = false
)

data class ExtractionResult(
    val downloadUrl: String,
    val title: String,
    val thumbnail: String,
    val format: String,
    val videoId: String
)

object YouTubeDownloaderHelper {
    private const val TAG = "YouTubeDownloader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val availableResolutions = listOf(
        YouTubeResolution("1080", "1080p FHD", "ফুল এইচডি কোয়ালিটি"),
        YouTubeResolution("720", "720p HD", "হাই ডেফিনিশন (সেরা মান)"),
        YouTubeResolution("480", "480p SD", "স্ট্যান্ডার্ড কোয়ালিটি"),
        YouTubeResolution("360", "360p Medium", "মিডিয়াম ডাটা সেভার"),
        YouTubeResolution("240", "240p Low", "কম ডাটা খরচ"),
        YouTubeResolution("144", "144p Super Low", "ডাটা সেভার মোড"),
        YouTubeResolution("mp3", "MP3 অডিও", "শুধুমাত্র অডিও গান", isAudio = true)
    )

    fun extractVideoId(url: String): String? {
        if (url.isBlank()) return null
        return try {
            when {
                url.contains("youtube.com/watch") -> {
                    val uri = android.net.Uri.parse(url)
                    uri.getQueryParameter("v")
                }
                url.contains("youtu.be/") -> {
                    val path = android.net.Uri.parse(url).pathSegments
                    path.firstOrNull()
                }
                url.contains("/shorts/") -> {
                    val uri = android.net.Uri.parse(url)
                    val segments = uri.pathSegments
                    val shortsIdx = segments.indexOf("shorts")
                    if (shortsIdx != -1 && shortsIdx + 1 < segments.size) {
                        segments[shortsIdx + 1]
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YouTube URL: $url", e)
            null
        }
    }

    fun isWatchUrl(url: String): Boolean {
        return extractVideoId(url) != null
    }

    fun getCanonicalWatchUrl(url: String): String? {
        val id = extractVideoId(url) ?: return null
        return "https://www.youtube.com/watch?v=$id"
    }

    /**
     * Extracts direct download URL using downclip and savenow APIs
     */
    suspend fun extractDownloadUrl(
        youtubeUrl: String,
        format: String,
        onProgressStatus: (String) -> Unit = {}
    ): Result<ExtractionResult> = withContext(Dispatchers.IO) {
        try {
            val videoId = extractVideoId(youtubeUrl) ?: "yt_video"
            val canonicalUrl = getCanonicalWatchUrl(youtubeUrl) ?: youtubeUrl

            onProgressStatus("ডাউনলোড সার্ভারে অনুরোধ পাঠানো হচ্ছে...")

            // Step 1: Call start API
            val startApiUrl = "https://downclip.vercel.app/api/download/start?url=${URLEncoder.encode(canonicalUrl, "UTF-8")}&format=$format"
            val startReq = Request.Builder()
                .url(startApiUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:115.0) MukulPlusApp")
                .header("Accept", "application/json")
                .build()

            val startResponse = client.newCall(startReq).execute()
            val startBody = startResponse.body?.string() ?: throw Exception("সার্ভার থেকে কোনো রেসপন্স পাওয়া যায়নি")

            val startJson = JSONObject(startBody)
            val isSuccess = startJson.optBoolean("success", true)
            val taskId = startJson.optString("id", "")
            val title = startJson.optString("title", "YouTube Video $videoId")
            val thumbnail = startJson.optString("image", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")

            if (taskId.isEmpty()) {
                val errorMsg = startJson.optString("message", "ডাউনলোড লিঙ্ক তৈরি করতে ব্যর্থ হয়েছে")
                throw Exception(errorMsg)
            }

            onProgressStatus("ভিডিও কনভার্ট করা হচ্ছে...")

            // Step 2: Poll progress API
            val progressApiUrl = "https://p.savenow.to/api/progress?id=$taskId"
            var directDownloadUrl: String? = null
            var attempts = 0
            val maxAttempts = 35 // ~50 seconds max

            while (attempts < maxAttempts) {
                delay(1500)
                attempts++

                val pollReq = Request.Builder()
                    .url(progressApiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:115.0) MukulPlusApp")
                    .header("Accept", "application/json")
                    .build()

                try {
                    val pollResponse = client.newCall(pollReq).execute()
                    val pollBody = pollResponse.body?.string().orEmpty()
                    if (pollBody.isNotEmpty()) {
                        val pollJson = JSONObject(pollBody)
                        val dlUrl = pollJson.optString("download_url", "")
                        val text = pollJson.optString("text", "")
                        val progressVal = pollJson.optInt("progress", 0)

                        if (text.isNotEmpty()) {
                            val percent = if (progressVal in 1..1000) " (${progressVal / 10}%)" else ""
                            onProgressStatus("প্রস্তুত হচ্ছে: $text$percent")
                        }

                        if (dlUrl.isNotEmpty() && dlUrl != "null") {
                            directDownloadUrl = dlUrl
                            break
                        }
                    }
                } catch (pe: Exception) {
                    Log.w(TAG, "Polling attempt $attempts failed", pe)
                }
            }

            if (directDownloadUrl.isNullOrEmpty()) {
                throw Exception("ডাউনলোড লিঙ্ক তৈরিতে সময় বেশি লাগছে। পুনরায় চেষ্টা করুন।")
            }

            onProgressStatus("ডাউনলোড প্রস্তুত!")

            Result.success(
                ExtractionResult(
                    downloadUrl = directDownloadUrl,
                    title = title,
                    thumbnail = thumbnail,
                    format = format,
                    videoId = videoId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "extractDownloadUrl failed", e)
            Result.failure(e)
        }
    }
}
