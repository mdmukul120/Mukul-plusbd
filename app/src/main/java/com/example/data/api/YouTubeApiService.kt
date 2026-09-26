package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class YouTubeVideoItem(
    val id: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val duration: String = "",
    val viewCount: String = "",
    val publishedTime: String = "",
    val description: String = ""
) {
    val watchUrl: String
        get() = "https://www.youtube.com/watch?v=$id"
}

object YouTubeApiService {
    private const val TAG = "YouTubeApiService"
    const val API_KEY = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Search videos via YouTube Data API v3 with automatic fallback
     */
    suspend fun searchVideos(
        query: String,
        maxResults: Int = 20
    ): List<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().ifEmpty { "trending bangla" }
        // 1. Try official YouTube Data API v3 first
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=$maxResults&q=$encodedQuery&type=video&key=$API_KEY"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MukulPlusApp/1.0")
                .header("Accept", "application/json")
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val list = mutableListOf<YouTubeVideoItem>()
                        val videoIds = mutableListOf<String>()

                        for (i in 0 until items.length()) {
                            val it = items.optJSONObject(i) ?: continue
                            val idObj = it.optJSONObject("id")
                            val videoId = idObj?.optString("videoId") ?: continue
                            val snippet = it.optJSONObject("snippet") ?: continue

                            val title = decodeHtml(snippet.optString("title"))
                            val channel = decodeHtml(snippet.optString("channelTitle"))
                            val thumbs = snippet.optJSONObject("thumbnails")
                            val thumb = thumbs?.optJSONObject("high")?.optString("url")
                                ?: thumbs?.optJSONObject("medium")?.optString("url")
                                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            val publishedAt = formatPublishedTime(snippet.optString("publishedAt"))
                            val desc = snippet.optString("description")

                            list.add(
                                YouTubeVideoItem(
                                    id = videoId,
                                    title = title,
                                    channelTitle = channel,
                                    thumbnailUrl = thumb,
                                    publishedTime = publishedAt,
                                    description = desc
                                )
                            )
                            videoIds.add(videoId)
                        }

                        // Enrich with duration and views if possible
                        return@withContext enrichVideoDetails(list, videoIds)
                    }
                }
            } else {
                Log.w(TAG, "YouTube v3 API returned code ${res.code}, using fast fallback")
            }
        } catch (e: Exception) {
            Log.w(TAG, "YouTube v3 API call exception: ${e.message}, switching to fallback")
        }

        // 2. Reliable Scraper Fallback (Never leaves user with empty/broken state)
        return@withContext fallbackScrapeSearch(trimmed)
    }

    /**
     * Get Trending / Popular videos (Home Feed)
     */
    suspend fun getTrendingVideos(category: String = ""): List<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        val query = when (category) {
            "bangla_song" -> "bangla new song official video"
            "natok" -> "bangla natok new 2026"
            "movie_trailer" -> "movie trailer bangla hindi english"
            "hindi_song" -> "latest hindi song bollywood"
            "islamic" -> "bangla islamic waz gojol"
            "news" -> "bangla live news channel"
            "gaming" -> "gaming video bangla"
            else -> "bangla trending entertainment videos"
        }

        // Try YouTube Data API v3 Most Popular
        if (category.isEmpty()) {
            try {
                val url = "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails,statistics&chart=mostPopular&regionCode=BD&maxResults=24&key=$API_KEY"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "MukulPlusApp/1.0")
                    .header("Accept", "application/json")
                    .build()

                val res = client.newCall(req).execute()
                if (res.isSuccessful) {
                    val body = res.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items")
                        if (items != null && items.length() > 0) {
                            val list = mutableListOf<YouTubeVideoItem>()
                            for (i in 0 until items.length()) {
                                val it = items.optJSONObject(i) ?: continue
                                val videoId = it.optString("id")
                                val snippet = it.optJSONObject("snippet") ?: continue
                                val details = it.optJSONObject("contentDetails")
                                val stats = it.optJSONObject("statistics")

                                val title = decodeHtml(snippet.optString("title"))
                                val channel = decodeHtml(snippet.optString("channelTitle"))
                                val thumbs = snippet.optJSONObject("thumbnails")
                                val thumb = thumbs?.optJSONObject("high")?.optString("url")
                                    ?: thumbs?.optJSONObject("medium")?.optString("url")
                                    ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                val duration = parseIsoDuration(details?.optString("duration") ?: "")
                                val views = formatViewCount(stats?.optLong("viewCount") ?: 0L)
                                val publishedAt = formatPublishedTime(snippet.optString("publishedAt"))

                                list.add(
                                    YouTubeVideoItem(
                                        id = videoId,
                                        title = title,
                                        channelTitle = channel,
                                        thumbnailUrl = thumb,
                                        duration = duration,
                                        viewCount = views,
                                        publishedTime = publishedAt,
                                        description = snippet.optString("description")
                                    )
                                )
                            }
                            return@withContext list
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Trending v3 API failed: ${e.message}")
            }
        }

        return@withContext fallbackScrapeSearch(query)
    }

    /**
     * Get related videos for player page
     */
    suspend fun getRelatedVideos(videoId: String, title: String): List<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        val keywords = title.split(" ")
            .filter { it.length > 3 }
            .take(3)
            .joinToString(" ")
        val query = keywords.ifEmpty { "bangla video" }
        return@withContext searchVideos(query, 10).filter { it.id != videoId }
    }

    /**
     * Scrape Search from YouTube directly
     */
    private fun fallbackScrapeSearch(query: String): List<YouTubeVideoItem> {
        val results = mutableListOf<YouTubeVideoItem>()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encoded"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9,bn;q=0.8")
                .build()

            val res = client.newCall(req).execute()
            val html = res.body?.string() ?: return results

            val pattern = Pattern.compile("var ytInitialData = (\\{.*?\\});</script>")
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val jsonStr = matcher.group(1) ?: return results
                val root = JSONObject(jsonStr)
                parseVideosRecursive(root, results)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback scrape search", e)
        }
        return results.distinctBy { it.id }
    }

    private fun parseVideosRecursive(obj: Any?, out: MutableList<YouTubeVideoItem>) {
        if (obj is JSONObject) {
            if (obj.has("videoRenderer")) {
                val vr = obj.optJSONObject("videoRenderer")
                if (vr != null) {
                    val vidId = vr.optString("videoId")
                    val titleObj = vr.optJSONObject("title")
                    val titleRuns = titleObj?.optJSONArray("runs")
                    val title = titleRuns?.optJSONObject(0)?.optString("text") ?: ""

                    val ownerObj = vr.optJSONObject("ownerText")
                    val ownerRuns = ownerObj?.optJSONArray("runs")
                    val channel = ownerRuns?.optJSONObject(0)?.optString("text") ?: ""

                    val thumbObj = vr.optJSONObject("thumbnail")
                    val thumbArr = thumbObj?.optJSONArray("thumbnails")
                    val thumb = if (thumbArr != null && thumbArr.length() > 0) {
                        thumbArr.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""
                    } else "https://i.ytimg.com/vi/$vidId/hqdefault.jpg"

                    val dur = vr.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                    val views = vr.optJSONObject("viewCountText")?.optString("simpleText") ?: ""
                    val pubTime = vr.optJSONObject("publishedTimeText")?.optString("simpleText") ?: ""

                    if (vidId.isNotEmpty() && title.isNotEmpty()) {
                        out.add(
                            YouTubeVideoItem(
                                id = vidId,
                                title = decodeHtml(title),
                                channelTitle = decodeHtml(channel),
                                thumbnailUrl = thumb,
                                duration = dur,
                                viewCount = views,
                                publishedTime = pubTime
                            )
                        )
                    }
                }
            }
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                parseVideosRecursive(obj.opt(k), out)
            }
        } else if (obj is org.json.JSONArray) {
            for (i in 0 until obj.length()) {
                parseVideosRecursive(obj.opt(i), out)
            }
        }
    }

    private fun enrichVideoDetails(
        videos: List<YouTubeVideoItem>,
        videoIds: List<String>
    ): List<YouTubeVideoItem> {
        if (videoIds.isEmpty()) return videos
        try {
            val idsParam = videoIds.take(50).joinToString(",")
            val url = "https://www.googleapis.com/youtube/v3/videos?part=contentDetails,statistics&id=$idsParam&key=$API_KEY"

            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MukulPlusApp/1.0")
                .build()

            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                val body = res.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: return videos
                    val detailMap = mutableMapOf<String, Pair<String, String>>()
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val id = it.optString("id")
                        val cd = it.optJSONObject("contentDetails")
                        val st = it.optJSONObject("statistics")
                        val dur = parseIsoDuration(cd?.optString("duration") ?: "")
                        val views = formatViewCount(st?.optLong("viewCount") ?: 0L)
                        detailMap[id] = Pair(dur, views)
                    }

                    return videos.map { v ->
                        val extra = detailMap[v.id]
                        if (extra != null) {
                            v.copy(duration = extra.first, viewCount = extra.second)
                        } else v
                    }
                }
            }
        } catch (_: Exception) {}
        return videos
    }

    private fun parseIsoDuration(isoDuration: String): String {
        if (isoDuration.isEmpty()) return ""
        try {
            var time = isoDuration.removePrefix("PT")
            var hours = 0
            var minutes = 0
            var seconds = 0

            if (time.contains("H")) {
                val parts = time.split("H")
                hours = parts[0].toIntOrNull() ?: 0
                time = if (parts.size > 1) parts[1] else ""
            }
            if (time.contains("M")) {
                val parts = time.split("M")
                minutes = parts[0].toIntOrNull() ?: 0
                time = if (parts.size > 1) parts[1] else ""
            }
            if (time.contains("S")) {
                val parts = time.split("S")
                seconds = parts[0].toIntOrNull() ?: 0
            }

            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        } catch (e: Exception) {
            return ""
        }
    }

    private fun formatViewCount(views: Long): String {
        return when {
            views >= 1000000 -> String.format("%.1fM ভিউ", views / 1000000.0)
            views >= 1000 -> String.format("%.1fK ভিউ", views / 1000.0)
            views > 0 -> "$views ভিউ"
            else -> ""
        }
    }

    private fun formatPublishedTime(isoDate: String): String {
        if (isoDate.isEmpty()) return ""
        return try {
            isoDate.take(10)
        } catch (_: Exception) {
            isoDate
        }
    }

    private fun decodeHtml(text: String): String {
        return android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
    }
}
