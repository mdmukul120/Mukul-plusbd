package com.example.data.repository

import android.util.Log
import com.example.data.model.SportsCategoryType
import com.example.data.model.SportsMatchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SportsRepository {
    private const val TAG = "SportsRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // In-memory cache so switching tabs or categories is instantaneous and saves user data
    val categoryCache = ConcurrentHashMap<SportsCategoryType, List<SportsMatchItem>>()

    /**
     * Get matches for a sports category (uses memory cache first to save data)
     */
    suspend fun getSportsCategory(
        category: SportsCategoryType,
        forceRefresh: Boolean = false
    ): List<SportsMatchItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            val cached = categoryCache[category]
            if (!cached.isNullOrEmpty()) {
                return@withContext cached
            }
        }

        val items = try {
            when (category) {
                SportsCategoryType.REPLAYS -> fetchReplays(category)
                SportsCategoryType.LIVE_CRICKET -> fetchLiveCricket(category)
                SportsCategoryType.LEAGUE_LIVE -> fetchLeagueLive(category)
                SportsCategoryType.FREE_LIVE_SPORTS -> fetchFreeLiveSports(category)
                SportsCategoryType.WILLOW_EVENTS -> fetchWillowEvents(category)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching sports category: ${category.name}", e)
            emptyList()
        }

        val finalResult = if (items.isNotEmpty()) {
            items
        } else {
            categoryCache[category] ?: getFallbackMatches(category)
        }

        categoryCache[category] = finalResult
        finalResult
    }

    /**
     * Category 1: Real Replays from https://mukul-sports.ai.studio/api/replays/replays.txt
     */
    private fun fetchReplays(category: SportsCategoryType): List<SportsMatchItem> {
        val request = Request.Builder()
            .url(category.endpointUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) MukulPlusApp")
            .header("Accept", "*/*")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val text = response.body?.string().orEmpty().trim()
        if (text.isEmpty()) return emptyList()

        val list = mutableListOf<SportsMatchItem>()
        val blocks = text.split("# ")

        val maxItems = minOf(blocks.size, 150)
        for (idx in 1 until maxItems) {
            val block = blocks[idx].trim()
            if (block.isEmpty()) continue

            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            val title = lines[0]
            if (title.isBlank()) continue

            var categoryName = "Sports"
            var leagueName = "Highlights"
            var posterUrl = ""
            var dateStr = ""
            val servers = mutableListOf<Pair<String, String>>()

            if (lines.size > 1 && lines[1].startsWith("~")) {
                val metaLine = lines[1].removePrefix("~").trim()
                val metaParts = metaLine.split("\t")
                if (metaParts.isNotEmpty()) categoryName = metaParts[0].trim()
                if (metaParts.size > 1) leagueName = metaParts[1].trim()
                if (metaParts.size > 2) posterUrl = metaParts[2].trim()
                if (metaParts.size > 3) dateStr = metaParts[3].trim()
            }

            for (i in 2 until lines.size) {
                val streamLine = lines[i]
                val sParts = streamLine.split("\t")
                if (sParts.size >= 3) {
                    val serverName = sParts[0].trim()
                    val url = sParts[2].trim()
                    if (url.startsWith("http")) {
                        servers.add(serverName to url)
                    }
                } else if (sParts.size == 2 && sParts[1].startsWith("http")) {
                    servers.add(sParts[0].trim() to sParts[1].trim())
                }
            }

            val primaryStream = servers.firstOrNull()?.second ?: ""
            if (primaryStream.isEmpty() && servers.isEmpty()) continue

            list.add(
                SportsMatchItem(
                    id = "replay_$idx",
                    title = title,
                    subtitle = "$categoryName • $leagueName",
                    categoryType = category,
                    streamUrl = primaryStream,
                    posterUrl = posterUrl,
                    isLive = false,
                    statusText = "REPLAY",
                    timeOrDate = dateStr,
                    league = leagueName,
                    servers = servers,
                    isWebEmbed = true
                )
            )
        }

        return list
    }

    /**
     * Category 2: Real Live upcoming cricket from https://raw.githubusercontent.com/srhady/willow-event/refs/heads/main/live_sports.json
     */
    private fun fetchLiveCricket(category: SportsCategoryType): List<SportsMatchItem> {
        val request = Request.Builder()
            .url(category.endpointUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) MukulPlusApp")
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val text = response.body?.string().orEmpty()
        if (text.isEmpty()) return emptyList()

        val list = mutableListOf<SportsMatchItem>()
        try {
            val json = JSONObject(text)
            val matchesArr = json.optJSONArray("Matches")
                ?: json.optJSONArray("matches")
                ?: JSONArray()

            for (i in 0 until matchesArr.length()) {
                val obj = matchesArr.optJSONObject(i) ?: continue
                val matchId = obj.optString("match_id", "cricket_$i")
                val title = obj.optString("title", "Cricket Match $i")
                val synopsis = obj.optString("synopsis", "")
                val status = obj.optString("status", "UPCOMING").uppercase()
                val isLive = status == "LIVE"
                val time = obj.optString("time", "")
                val coverImage = obj.optString("cover_image", "")
                val matchUrl = obj.optString("match_url", "")

                val servers = mutableListOf<Pair<String, String>>()

                val alphaObj = obj.optJSONObject("stream_url_alpha")
                if (alphaObj != null) {
                    val keys = alphaObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val url = alphaObj.optString(key, "")
                        if (url.startsWith("http")) {
                            servers.add(key to url)
                        }
                    }
                } else {
                    val alphaStr = obj.optString("stream_url_alpha", "")
                    if (alphaStr.startsWith("http")) {
                        servers.add("সার্ভার ১" to alphaStr)
                    }
                }

                val bravoObj = obj.optJSONObject("stream_url_bravo")
                if (bravoObj != null) {
                    val keys = bravoObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val url = bravoObj.optString(key, "")
                        if (url.startsWith("http")) {
                            servers.add("সার্ভার ২ ($key)" to url)
                        }
                    }
                }

                // Add live Willow cricket stream option for this live match
                servers.add("উইলো লাইভ স্ট্রিম" to "https://embed.st/embed/admin/admin-willow-cricket/1")

                val primaryStream = servers.firstOrNull()?.second ?: matchUrl

                list.add(
                    SportsMatchItem(
                        id = matchId,
                        title = title,
                        subtitle = synopsis.ifEmpty { if (isLive) "সরাসরি সম্প্রচার চলছে" else "সময়সূচী: $time" },
                        categoryType = category,
                        streamUrl = primaryStream,
                        posterUrl = coverImage,
                        isLive = isLive,
                        statusText = status,
                        timeOrDate = time,
                        league = "আন্তর্জাতিক ও সিরিজ ক্রিকেট",
                        servers = servers,
                        isWebEmbed = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse live cricket JSON", e)
        }
        return list
    }

    /**
     * Category 3: League Live from https://livestreamcricket.cc/cdlive-api/show-match-data.php?play=
     */
    private fun fetchLeagueLive(category: SportsCategoryType): List<SportsMatchItem> {
        val list = mutableListOf<SportsMatchItem>()

        val baseUrl = category.endpointUrl

        val leagueChannels = listOf(
            Triple(1, "BPL & T20 League Live (চ্যানেল ১)", "বাংলাদেশ ও আন্তর্জাতিক টি-২০ প্রিমিয়ার লীগ"),
            Triple(2, "IPL & Major League Live (চ্যানেল ২)", "ইন্ডিয়ান প্রিমিয়ার লীগ ও শীর্ষ ক্লাব ম্যাচ"),
            Triple(3, "PSL & Super League Live (চ্যানেল ৩)", "পাকিস্তান সুপার লীগ ও ফ্র্যাঞ্চাইজি ক্রিকেট"),
            Triple(4, "BBL & Big Bash Live (চ্যানেল ৪)", "অস্ট্রেলিয়ান বিগ ব্যাশ মেগা লীগ"),
            Triple(5, "CPL & Global T20 Live (চ্যানেল ৫)", "ক্যারিবিয়ান ও গ্লোবাল টি-২০ টুর্নামেন্ট"),
            Triple(6, "International League T20 (চ্যানেল ৬)", "আইএল টি-২০ ও এশিয়া কাপ বাছাইপর্ব"),
            Triple(7, "The Hundred & Vitality Live (চ্যানেল ৭)", "ইংল্যান্ড দ্য হান্ড্রেড ও ভাইটালিটি ব্লাস্ট"),
            Triple(8, "World League Cricket HD (চ্যানেল ৮)", "বিশ্বব্যাপী প্রিমিয়ার ক্রিকেট লাইভ ফিড")
        )

        val channelThumbnails = listOf(
            "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=800&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1531415074868-036b1c57e329?w=800&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=800&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1531415074868-036b1c57e329?w=800&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=800&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=800&auto=format&fit=crop&q=80",
            "https://images.unsplash.com/photo-1531415074868-036b1c57e329?w=800&auto=format&fit=crop&q=80"
        )

        for ((index, item) in leagueChannels.withIndex()) {
            val playId = item.first
            val title = item.second
            val subtitle = item.third
            val url = "$baseUrl$playId"
            val thumb = channelThumbnails.getOrElse(index) { channelThumbnails[0] }

            val servers = listOf(
                "সার্ভার ১ (সরাসরি ওয়েব)" to url,
                "সার্ভার ২ (উইলো এইচডি)" to "https://embed.st/embed/admin/admin-willow-cricket/1",
                "সার্ভার ৩ (উইলো ২ এইচডি)" to "https://embed.st/embed/admin/admin-willow-cricket/2"
            )

            list.add(
                SportsMatchItem(
                    id = "league_ch_$playId",
                    title = title,
                    subtitle = subtitle,
                    categoryType = category,
                    streamUrl = url,
                    posterUrl = thumb,
                    isLive = true,
                    statusText = "LIVE",
                    timeOrDate = "সরাসরি সম্প্রচার",
                    league = "T20 Leagues Live",
                    servers = servers,
                    isWebEmbed = true
                )
            )
        }

        return list
    }

    /**
     * Category 4: Real Free Live Sports Channels from https://ga-prod-api.powr.tv/v2/sites/freelivesports/live-channels/
     * 119 Real Channels with direct HLS m3u8 playlists and logos
     */
    private fun fetchFreeLiveSports(category: SportsCategoryType): List<SportsMatchItem> {
        val request = Request.Builder()
            .url(category.endpointUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()
        val text = response.body?.string().orEmpty()
        if (text.isEmpty()) return emptyList()

        val list = mutableListOf<SportsMatchItem>()
        try {
            val jsonArr = JSONArray(text)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "Sports Channel $i")
                val streamUrl = obj.optString("url", "")
                val thumbnail = obj.optString("thumbnail", "")
                val description = obj.optString("description", "২৪/৭ ফ্রি লাইভ স্পোর্টস চ্যানেল")
                val channelId = obj.optString("id", obj.optString("_id", "ch_$i"))

                if (streamUrl.isNotEmpty() && streamUrl.startsWith("http")) {
                    list.add(
                        SportsMatchItem(
                            id = channelId,
                            title = name,
                            subtitle = description,
                            categoryType = category,
                            streamUrl = streamUrl,
                            posterUrl = thumbnail,
                            isLive = true,
                            statusText = "LIVE TV",
                            league = "Free Live Sports HD",
                            isWebEmbed = false // Direct HLS m3u8 stream played with ExoPlayer!
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse freelivesports channels", e)
        }
        return list
    }

    /**
     * Category 5: Real Willow Events Streams from https://pantyflix.com/api/streamed/stream/admin/admin-willow-cricket
     * Real live Willow Cricket stream embeds
     */
    private fun fetchWillowEvents(category: SportsCategoryType): List<SportsMatchItem> {
        val defaultStreams = listOf(
            Triple(1, "Willow Cricket HD (Server 1)", "https://embed.st/embed/admin/admin-willow-cricket/1"),
            Triple(2, "Willow 2 Cricket HD (Server 2)", "https://embed.st/embed/admin/admin-willow-cricket/2"),
            Triple(3, "Willow Sports HD (Server 3)", "https://embed.st/embed/admin/admin-willow-cricket/3"),
            Triple(4, "Willow Cricket SD (Server 4)", "https://embed.st/embed/admin/admin-willow-cricket/4"),
            Triple(5, "Willow 2 Cricket SD (Server 5)", "https://embed.st/embed/admin/admin-willow-cricket/5"),
            Triple(6, "Willow Sports SD (Server 6)", "https://embed.st/embed/admin/admin-willow-cricket/6")
        )

        val list = mutableListOf<SportsMatchItem>()

        try {
            val request = Request.Builder()
                .url(category.endpointUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
                .header("Referer", "https://pantyflix.com/")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                if (text.isNotEmpty()) {
                    val arr = JSONArray(text.trim())
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val streamNo = obj.optInt("streamNo", i + 1)
                        val language = obj.optString("language", "Willow")
                        val isHd = obj.optBoolean("hd", true)
                        val embedUrl = obj.optString("embedUrl", "")
                        val viewers = obj.optInt("viewers", 0)

                        val title = "$language ${if (isHd) "HD" else "SD"} (Server #$streamNo)"

                        if (embedUrl.isNotEmpty()) {
                            list.add(
                                SportsMatchItem(
                                    id = "willow_st_$streamNo",
                                    title = title,
                                    subtitle = "লাইভ ভিউয়ার্স: $viewers জন • ${if (isHd) "ফুল এইচডি 1080p" else "স্ট্যান্ডার্ড 720p"}",
                                    categoryType = category,
                                    streamUrl = embedUrl,
                                    posterUrl = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=800&auto=format&fit=crop&q=80",
                                    isLive = true,
                                    statusText = "LIVE",
                                    league = "Willow Cricket Events",
                                    isWebEmbed = true,
                                    viewers = viewers
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse willow cricket events, using defaults", e)
        }

        // Guaranteed fallback if API was temporarily blocked
        if (list.isEmpty()) {
            for ((num, name, url) in defaultStreams) {
                list.add(
                    SportsMatchItem(
                        id = "willow_def_$num",
                        title = name,
                        subtitle = "উইলো ক্রিকেট লাইভ ব্রডকাস্ট স্ট্রিম",
                        categoryType = category,
                        streamUrl = url,
                        posterUrl = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=800&auto=format&fit=crop&q=80",
                        isLive = true,
                        statusText = "LIVE",
                        league = "Willow Cricket",
                        isWebEmbed = true
                    )
                )
            }
        }

        return list
    }

    private fun getFallbackMatches(category: SportsCategoryType): List<SportsMatchItem> {
        return emptyList()
    }
}
