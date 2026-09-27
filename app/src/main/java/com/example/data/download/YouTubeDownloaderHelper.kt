package com.example.data.download

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
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
     * Extracts direct download URL with multi-server fallback
     */
    suspend fun extractDownloadUrl(
        youtubeUrl: String,
        format: String,
        onProgressStatus: (String) -> Unit = {}
    ): Result<ExtractionResult> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(youtubeUrl) ?: "yt_video"
        val canonicalUrl = getCanonicalWatchUrl(youtubeUrl) ?: youtubeUrl
        val defaultTitle = "YouTube Video $videoId"
        val defaultThumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        onProgressStatus("ডাউনলোড সার্ভার সংযোগ করা হচ্ছে...")

        // Method 1: Downclip / Savenow primary converter
        try {
            val startApiUrl = "https://downclip.vercel.app/api/download/start?url=${URLEncoder.encode(canonicalUrl, "UTF-8")}&format=$format"
            val startReq = Request.Builder()
                .url(startApiUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) MukulPlusApp")
                .header("Accept", "application/json")
                .build()

            val startResponse = client.newCall(startReq).execute()
            if (startResponse.isSuccessful) {
                val startBody = startResponse.body?.string().orEmpty()
                if (startBody.isNotEmpty()) {
                    val startJson = JSONObject(startBody)
                    val taskId = startJson.optString("id", "")
                    val title = startJson.optString("title", defaultTitle)
                    val thumbnail = startJson.optString("image", defaultThumb)

                    if (taskId.isNotEmpty()) {
                        onProgressStatus("ভিডিও কনভার্ট ও স্ট্রিম প্রস্তুত হচ্ছে...")
                        val progressApiUrl = "https://p.savenow.to/api/progress?id=$taskId"
                        var directDownloadUrl: String? = null
                        var attempts = 0
                        val maxAttempts = 35

                        while (attempts < maxAttempts) {
                            delay(900)
                            attempts++

                            val pollReq = Request.Builder()
                                .url(progressApiUrl)
                                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) MukulPlusApp")
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
                            } catch (_: Exception) {}
                        }

                        if (!directDownloadUrl.isNullOrEmpty()) {
                            onProgressStatus("ডাউনলোড লিঙ্ক তৈরি সম্পন্ন!")
                            return@withContext Result.success(
                                ExtractionResult(
                                    downloadUrl = directDownloadUrl,
                                    title = title,
                                    thumbnail = thumbnail,
                                    format = format,
                                    videoId = videoId
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Method 1 Downclip failed: ${e.message}, trying Method 2")
        }

        // Method 2: Cobalt High-Speed Backend API
        onProgressStatus("ব্যাকআপ সার্ভার থেকে লিঙ্ক সংগ্রহ হচ্ছে...")
        try {
            val cobaltUrls = listOf(
                "https://cobalt-backend.onrender.com/api/json",
                "https://api.cobalt.tools/api/json"
            )

            val jsonBody = JSONObject().apply {
                put("url", canonicalUrl)
                put("vQuality", if (format.contains("1080")) "1080" else if (format.contains("720")) "720" else "480")
                put("isAudioOnly", format.equals("mp3", ignoreCase = true))
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            for (endpoint in cobaltUrls) {
                try {
                    val req = Request.Builder()
                        .url(endpoint)
                        .post(requestBody)
                        .header("Accept", "application/json")
                        .header("User-Agent", "MukulPlusApp/1.0")
                        .build()

                    val res = client.newCall(req).execute()
                    val body = res.body?.string().orEmpty()
                    if (res.isSuccessful && body.isNotEmpty()) {
                        val obj = JSONObject(body)
                        val streamUrl = obj.optString("url", "")
                        if (streamUrl.isNotEmpty()) {
                            onProgressStatus("ডাউনলোড প্রস্তুত!")
                            return@withContext Result.success(
                                ExtractionResult(
                                    downloadUrl = streamUrl,
                                    title = defaultTitle,
                                    thumbnail = defaultThumb,
                                    format = format,
                                    videoId = videoId
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w(TAG, "Method 2 Cobalt failed: ${e.message}")
        }

        // Method 3: Direct Invidious Proxy Stream
        try {
            onProgressStatus("সরাসরি স্ট্রিম লিঙ্ক প্রস্তুত করা হচ্ছে...")
            val invidiousUrl = "https://inv.nadeko.net/api/v1/videos/$videoId"
            val req = Request.Builder()
                .url(invidiousUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string().orEmpty()
                if (body.isNotEmpty()) {
                    val json = JSONObject(body)
                    val title = json.optString("title", defaultTitle)
                    val formatStreams = json.optJSONArray("formatStreams")
                    if (formatStreams != null && formatStreams.length() > 0) {
                        val firstStream = formatStreams.getJSONObject(0)
                        val streamUrl = firstStream.optString("url", "")
                        if (streamUrl.isNotEmpty()) {
                            return@withContext Result.success(
                                ExtractionResult(
                                    downloadUrl = streamUrl,
                                    title = title,
                                    thumbnail = defaultThumb,
                                    format = format,
                                    videoId = videoId
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        Result.failure(Exception("ডাউনলোড সার্ভার ব্যস্ত বা ভিডিওটি রেস্ট্রিক্টেড। পুনরায় চেষ্টা করুন।"))
    }
}
