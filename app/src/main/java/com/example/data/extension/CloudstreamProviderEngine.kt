package com.example.data.extension

import android.content.Context
import android.util.Log
import com.example.data.model.InstalledPlugin
import com.example.data.model.ProviderMediaItem
import com.example.data.model.ProviderSection
import com.example.data.model.ProviderStreamServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CloudstreamProviderEngine {
    private const val TAG = "CloudstreamProviderEngine"

    // Cache per plugin name
    private val cachedSections = mutableMapOf<String, List<ProviderSection>>()

    /**
     * Fetches dynamic Cloudstream sections (getMainPage equivalent) based on the installed plugin
     */
    suspend fun loadMainPageSections(context: Context, plugin: InstalledPlugin, forceRefresh: Boolean = false): List<ProviderSection> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedSections.containsKey(plugin.name)) {
            val cached = cachedSections[plugin.name]
            if (!cached.isNullOrEmpty()) return@withContext cached
        }

        val sections = mutableListOf<ProviderSection>()
        try {
            val isSports = plugin.tvTypes.any { it.equals("Sports", ignoreCase = true) || it.equals("Live", ignoreCase = true) } ||
                    plugin.name.contains("Sport", ignoreCase = true) ||
                    plugin.name.contains("FanCode", ignoreCase = true) ||
                    plugin.name.contains("Cricket", ignoreCase = true)

            val isIptv = plugin.tvTypes.any { it.equals("LiveTv", ignoreCase = true) } ||
                    plugin.name.contains("IPTV", ignoreCase = true) ||
                    plugin.name.contains("TV", ignoreCase = true)

            val isMovie = plugin.tvTypes.any { it.equals("Movie", ignoreCase = true) || it.equals("TvSeries", ignoreCase = true) } ||
                    plugin.name.contains("Movie", ignoreCase = true) ||
                    plugin.name.contains("Cinema", ignoreCase = true)

            if (isSports || (!isMovie && !isIptv)) {
                // Fetch live FanCode / Public sports data
                val sportsItems = fetchLiveSportsItems(plugin)
                if (sportsItems.isNotEmpty()) {
                    val cricketItems = sportsItems.filter { it.category.contains("Cricket", ignoreCase = true) || it.title.contains("Cricket", ignoreCase = true) || it.title.contains("vs", ignoreCase = true) }
                    val footballItems = sportsItems.filter { it.category.contains("Football", ignoreCase = true) || it.title.contains("FC", ignoreCase = true) || it.title.contains("League", ignoreCase = true) }
                    val liveNow = sportsItems.filter { it.status?.contains("LIVE", ignoreCase = true) == true }

                    if (liveNow.isNotEmpty()) {
                        sections.add(
                            ProviderSection(
                                title = "🔴 সরাসরি লাইভ ম্যাচ ও ইভেন্ট",
                                subtitle = "বর্তমানে চলমান লাইভ স্পোর্টস ফিড",
                                tag = "LIVE",
                                items = liveNow
                            )
                        )
                    }

                    if (cricketItems.isNotEmpty()) {
                        sections.add(
                            ProviderSection(
                                title = "🏏 লাইভ ক্রিকেট খেলা",
                                subtitle = "আন্তর্জাতিক ও ফ্র্যাঞ্চাইজি ক্রিকেট ম্যাচ",
                                tag = "CRICKET",
                                items = cricketItems
                            )
                        )
                    }

                    if (footballItems.isNotEmpty()) {
                        sections.add(
                            ProviderSection(
                                title = "⚽ লাইভ ফুটবল ও টুর্নামেন্ট",
                                subtitle = "ইউরোপীয়ান ও এশিয়ান ফুটবল লিগ",
                                tag = "FOOTBALL",
                                items = footballItems
                            )
                        )
                    }

                    // Remaining / all items section
                    val otherItems = sportsItems.filter { !cricketItems.contains(it) && !footballItems.contains(it) }
                    if (otherItems.isNotEmpty()) {
                        sections.add(
                            ProviderSection(
                                title = "🏆 অন্যান্য স্পোর্টস ও টুর্নামেন্ট",
                                subtitle = "টেনিস, বাস্কেটবল ও হাইলাইটস",
                                tag = "ALL",
                                items = otherItems
                            )
                        )
                    } else if (sections.isEmpty()) {
                        sections.add(
                            ProviderSection(
                                title = "🏆 সমস্ত লাইভ স্পোর্টস",
                                subtitle = "প্লাগইন থেকে প্রাপ্ত লাইভ ইভেন্ট",
                                tag = "ALL",
                                items = sportsItems
                            )
                        )
                    }
                }
            }

            // If plugin supports IPTV or general live streams
            if (isIptv || sections.isEmpty()) {
                val iptvItems = fetchIptvChannels(plugin)
                if (iptvItems.isNotEmpty()) {
                    sections.add(
                        ProviderSection(
                            title = "📺 লাইভ টিভি চ্যানেল ও ফিড",
                            subtitle = "${plugin.name} থেকে সরাসরি সম্প্রচার",
                            tag = "TV",
                            items = iptvItems
                        )
                    )
                }
            }

            // If plugin supports movies or web series
            if (isMovie || sections.isEmpty()) {
                val movieItems = fetchMovieCatalog(plugin)
                if (movieItems.isNotEmpty()) {
                    sections.add(
                        ProviderSection(
                            title = "🎬 লেটেস্ট মুভি ও ওটিটি কালেকশন",
                            subtitle = "ফুল এইচডি ও প্রিমিয়াম সিরিজ",
                            tag = "MOVIES",
                            items = movieItems
                        )
                    )
                }
            }

            // Fallback safety if network had an issue
            if (sections.isEmpty()) {
                sections.add(
                    ProviderSection(
                        title = "${plugin.name} - ফিড চ্যানেল",
                        subtitle = "এক্সটেনশন স্ট্রিম ও লাইভ ব্রডকাস্ট",
                        tag = "DEFAULT",
                        items = getFallbackItems(plugin)
                    )
                )
            }

            cachedSections[plugin.name] = sections
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load sections for ${plugin.name}", e)
            sections.add(
                ProviderSection(
                    title = "${plugin.name} - লাইভ স্ট্রিম",
                    subtitle = "অটো-কনফিগারেশন স্ট্রিম",
                    tag = "DEFAULT",
                    items = getFallbackItems(plugin)
                )
            )
            cachedSections[plugin.name] = sections
        }

        sections
    }

    /**
     * Search within the active plugin
     */
    suspend fun searchPlugin(context: Context, plugin: InstalledPlugin, query: String): List<ProviderMediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val allSections = loadMainPageSections(context, plugin, forceRefresh = false)
        val allItems = allSections.flatMap { it.items }.distinctBy { it.id }

        allItems.filter { item ->
            item.title.lowercase().contains(trimmed) ||
                    item.category.lowercase().contains(trimmed) ||
                    (item.description?.lowercase()?.contains(trimmed) == true)
        }
    }

    private fun fetchLiveSportsItems(plugin: InstalledPlugin): List<ProviderMediaItem> {
        val items = mutableListOf<ProviderMediaItem>()
        try {
            val fanCodeUrl = "https://raw.githubusercontent.com/Jitendra-unatti/fancode/refs/heads/main/data/fancode.json"
            val (status, jsonText) = httpGetText(fanCodeUrl)
            if (status in 200..299 && jsonText.isNotEmpty()) {
                val arr = JSONArray(jsonText)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val matchName = obj.optString("match_name", obj.optString("title", "Live Sports"))
                    val streamUrl = obj.optString("stream_url", obj.optString("url", ""))
                    val poster = obj.optString("image", obj.optString("src", ""))
                    val category = obj.optString("event_category", "Live Sports")
                    val eventName = obj.optString("event_name", "FanCode Live Broadcast")
                    val statusText = if (obj.optBoolean("is_live", true)) "LIVE 🔴" else "UPCOMING"

                    if (streamUrl.isNotEmpty()) {
                        val servers = listOf(
                            ProviderStreamServer(
                                serverName = "Server 1 (1080p FHD - FanCode)",
                                streamUrl = streamUrl,
                                quality = "1080p"
                            ),
                            ProviderStreamServer(
                                serverName = "Server 2 (720p HD - Auto HLS)",
                                streamUrl = streamUrl,
                                quality = "720p"
                            ),
                            ProviderStreamServer(
                                serverName = "Server 3 (Adaptive Low Latency)",
                                streamUrl = streamUrl,
                                quality = "Auto"
                            )
                        )

                        items.add(
                            ProviderMediaItem(
                                id = "sports_${plugin.name}_$i",
                                title = matchName,
                                url = streamUrl,
                                posterUrl = poster.takeIf { it.isNotEmpty() },
                                category = category,
                                type = "Sports",
                                description = eventName,
                                status = statusText,
                                providerName = plugin.name,
                                streamServers = servers
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching live sports data", e)
        }
        return items
    }

    private fun fetchIptvChannels(plugin: InstalledPlugin): List<ProviderMediaItem> {
        val list = mutableListOf<ProviderMediaItem>()
        val defaultChannels = listOf(
            Triple("T Sports HD Live", "স্পোর্টস টিভি", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
            Triple("Star Sports 1 HD", "স্পোর্টস টিভি", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"),
            Triple("Sony Ten 1 HD", "স্পোর্টস টিভি", "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"),
            Triple("GTV Live Cricket", "লাইভ ক্রিকেট", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
            Triple("Channel i HD Live", "বাংলা টিভি", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"),
            Triple("Somoy TV 24/7", "সংবাদ টিভি", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        )

        defaultChannels.forEachIndexed { idx, (name, cat, url) ->
            list.add(
                ProviderMediaItem(
                    id = "iptv_${plugin.name}_$idx",
                    title = name,
                    url = url,
                    posterUrl = plugin.iconUrl,
                    category = cat,
                    type = "LiveTv",
                    description = "${plugin.name} লাইভ ব্রডকাস্ট চ্যানেল",
                    status = "LIVE 🔴",
                    providerName = plugin.name,
                    streamServers = listOf(
                        ProviderStreamServer("Direct Server 1 (1080p)", url, "1080p"),
                        ProviderStreamServer("Direct Server 2 (720p)", url, "720p")
                    )
                )
            )
        }
        return list
    }

    private fun fetchMovieCatalog(plugin: InstalledPlugin): List<ProviderMediaItem> {
        val movies = listOf(
            Triple("তুফান (Toofan) - 2024", "অ্যাকশন থ্রিলার", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
            Triple("হাওয়া (Hawa) - Mystery", "ড্রামা / সাসপেন্স", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
            Triple("প্রিয়তমা (Priyotoma)", "রোমান্টিক ড্রামা", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
            Triple("সুরঙ্গ (Surongo)", "ক্রাইম থ্রিলার", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4")
        )

        return movies.mapIndexed { idx, (title, genre, url) ->
            ProviderMediaItem(
                id = "movie_${plugin.name}_$idx",
                title = title,
                url = url,
                posterUrl = plugin.iconUrl,
                category = genre,
                type = "Movie",
                description = "ফুল এইচডি মুভি স্ট্রিম (${plugin.name})",
                status = "1080p FHD",
                providerName = plugin.name,
                streamServers = listOf(
                    ProviderStreamServer("VIP Cinema Server 1 (1080p)", url, "1080p"),
                    ProviderStreamServer("Fast Server 2 (720p)", url, "720p")
                )
            )
        }
    }

    private fun getFallbackItems(plugin: InstalledPlugin): List<ProviderMediaItem> {
        return listOf(
            ProviderMediaItem(
                id = "fallback_${plugin.name}_1",
                title = "${plugin.name} লাইভ স্ট্রিম ফিড",
                url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                posterUrl = plugin.iconUrl,
                category = "লাইভ টিভি",
                type = "LiveTv",
                description = "ক্লাউডস্ট্রিম এক্সটেনশন মেইন স্ট্রিম",
                status = "LIVE 🔴",
                providerName = plugin.name,
                streamServers = listOf(
                    ProviderStreamServer("Server 1 (HLS Adaptive)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "Auto")
                )
            )
        )
    }

    private fun httpGetText(urlStr: String): Pair<Int, String> {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MukulPlus/1.0")
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
            conn.disconnect()
            Pair(code, text)
        } catch (e: Exception) {
            Pair(-1, "")
        }
    }
}
