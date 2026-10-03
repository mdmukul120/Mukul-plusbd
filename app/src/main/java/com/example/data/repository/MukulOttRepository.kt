package com.example.data.repository

import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object MukulOttRepository {
    private const val TAG = "MukulOttRepository"
    const val BASE_URL = "https://mukul-ott.ai.studio"

    private val client = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(15, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Page cache to prevent redundant network calls
    private val pageCache = ConcurrentHashMap<Int, List<MukulOttMovieItem>>()

    // Screen state cache across navigation
    var cachedMovies: List<MukulOttMovieItem> = emptyList()
    var cachedPage: Int = 1
    var isLoaded: Boolean = false

    /**
     * Fetch paginated movies catalog
     * Endpoint: https://mukul-ott.ai.studio/api/movies?page=1
     */
    suspend fun getMovies(page: Int): List<MukulOttMovieItem> = withContext(Dispatchers.IO) {
        val cached = pageCache[page]
        if (cached != null) return@withContext cached

        val url = "$BASE_URL/api/movies?page=$page"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; MukulPlusApp/1.0)")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch page $page: HTTP ${response.code}")
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val itemsArray = json.optJSONArray("items") ?: JSONArray()

            val resultList = mutableListOf<MukulOttMovieItem>()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.getJSONObject(i)
                resultList.add(
                    MukulOttMovieItem(
                        slug = itemObj.optString("slug"),
                        kind = itemObj.optString("kind", "movie"),
                        title = itemObj.optString("title"),
                        year = itemObj.optInt("year", 2026),
                        qualityTag = itemObj.optString("quality_tag"),
                        poster = itemObj.optString("poster")
                    )
                )
            }

            pageCache[page] = resultList
            return@withContext resultList
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching page $page", e)
            return@withContext emptyList()
        }
    }

    /**
     * Fetch exactly 50 movie cards per user page.
     * Fetches backend sub-pages in parallel to guarantee 50 unique video cards.
     */
    suspend fun getPageOf50Movies(userPage: Int): List<MukulOttMovieItem> = withContext(Dispatchers.IO) {
        val pageIdx = userPage.coerceAtLeast(1)
        val startApiPage = (pageIdx - 1) * 6 + 1
        val endApiPage = startApiPage + 7 // Fetch 8 sub-pages in parallel (around 64-72 items)

        val batch = coroutineScope {
            (startApiPage..endApiPage).map { p ->
                async(Dispatchers.IO) { getMovies(p) }
            }.awaitAll()
        }
        val combined = batch.flatten().distinctBy { it.slug }
        if (combined.size >= 50) {
            combined.take(50)
        } else {
            val extraList = mutableListOf<MukulOttMovieItem>()
            extraList.addAll(combined)
            var nextP = endApiPage + 1
            while (extraList.size < 50 && nextP <= startApiPage + 14) {
                val more = getMovies(nextP)
                if (more.isEmpty()) break
                extraList.addAll(more.filter { m -> extraList.none { it.slug == m.slug } })
                nextP++
            }
            if (extraList.size > 50) extraList.take(50) else extraList
        }
    }

    /**
     * Fast server-side search directly using:
     * https://mukul-ott.ai.studio/api/search?q=
     */
    suspend fun searchMoviesFast(query: String): List<MukulOttMovieItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        try {
            val url = "$BASE_URL/api/search?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; MukulPlusApp/1.0)")
                .header("Accept", "application/json")
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                if (body.isNotEmpty()) {
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    val list = mutableListOf<MukulOttMovieItem>()
                    for (i in 0 until items.length()) {
                        val it = items.getJSONObject(i)
                        val slug = it.optString("slug")
                        if (slug.isNotEmpty()) {
                            list.add(
                                MukulOttMovieItem(
                                    slug = slug,
                                    kind = it.optString("kind", "movie"),
                                    title = it.optString("title"),
                                    year = it.optInt("year", 2026),
                                    qualityTag = it.optString("quality_tag"),
                                    poster = it.optString("poster")
                                )
                            )
                        }
                    }
                    return@withContext list
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fast search failed for $trimmed", e)
        }
        return@withContext emptyList()
    }

    /**
     * Load anime list directly via search query
     */
    suspend fun getAnimeList(): List<MukulOttMovieItem> = withContext(Dispatchers.IO) {
        val animeSearch = searchMoviesFast("anime")
        val cached = pageCache.values.flatten().filter { it.kind == "anime" }
        (animeSearch + cached).distinctBy { it.slug }
    }

    /**
     * Load web series list directly via search query and cache
     */
    suspend fun getSeriesList(): List<MukulOttMovieItem> = withContext(Dispatchers.IO) {
        val seriesSearch = searchMoviesFast("series")
        val seasonSearch = searchMoviesFast("season")
        val cached = pageCache.values.flatten().filter { it.kind == "series" }
        (seriesSearch + seasonSearch + cached).distinctBy { it.slug }
    }

    /**
     * Load movies list directly via catalog and search query
     */
    suspend fun getMoviesList(): List<MukulOttMovieItem> = withContext(Dispatchers.IO) {
        val movieSearch = searchMoviesFast("movie")
        val page1 = getMovies(1)
        val page2 = getMovies(2)
        val cached = pageCache.values.flatten().filter { it.kind == "movie" }
        (movieSearch + page1.filter { it.kind == "movie" } + page2.filter { it.kind == "movie" } + cached).distinctBy { it.slug }
    }

    /**
     * Search movies across 1 to 200 pages.
     * Uses server-side search API first (/api/search?q=...) for instant results,
     * then supplements by scanning paginated catalog up to maxPages.
     */
    suspend fun searchMoviesAcrossPages(
        query: String,
        maxPages: Int = 200,
        onProgress: (scanned: Int, foundCount: Int) -> Unit = { _, _ -> }
    ): List<MukulOttMovieItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val foundMap = ConcurrentHashMap<String, MukulOttMovieItem>()

        // 1. Fast server-side search first
        try {
            val searchApiUrl = "$BASE_URL/api/search?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
            val searchReq = Request.Builder()
                .url(searchApiUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; MukulPlusApp/1.0)")
                .header("Accept", "application/json")
                .build()

            val searchResp = client.newCall(searchReq).execute()
            if (searchResp.isSuccessful) {
                val sBody = searchResp.body?.string()
                if (!sBody.isNullOrEmpty()) {
                    val sJson = JSONObject(sBody)
                    val sItems = sJson.optJSONArray("items")
                    if (sItems != null) {
                        for (i in 0 until sItems.length()) {
                            val it = sItems.getJSONObject(i)
                            val item = MukulOttMovieItem(
                                slug = it.optString("slug"),
                                kind = it.optString("kind", "movie"),
                                title = it.optString("title"),
                                year = it.optInt("year", 2026),
                                qualityTag = it.optString("quality_tag"),
                                poster = it.optString("poster")
                            )
                            if (item.slug.isNotEmpty()) {
                                foundMap[item.slug] = item
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server-side search failed, will fallback to deep page scan: ${e.message}")
        }
        onProgress(1, foundMap.size)

        // 2. Scan catalog pages
        var scannedPages = 0
        var consecutiveEmptyPages = 0
        val batchSize = 10

        for (batchStart in 1..maxPages step batchSize) {
            if (consecutiveEmptyPages >= 3 && batchStart > 30) {
                break
            }

            val batchEnd = (batchStart + batchSize - 1).coerceAtMost(maxPages)
            val results: List<Pair<Int, List<MukulOttMovieItem>>> = coroutineScope {
                (batchStart..batchEnd).map { pageNum ->
                    async(Dispatchers.IO) {
                        val pageItems = getMovies(pageNum)
                        Pair(pageNum, pageItems)
                    }
                }.awaitAll()
            }

            for ((_, items) in results) {
                scannedPages++
                if (items.isEmpty()) {
                    consecutiveEmptyPages++
                } else {
                    consecutiveEmptyPages = 0
                    for (item in items) {
                        if (item.title.contains(trimmed, ignoreCase = true) ||
                            item.slug.contains(trimmed, ignoreCase = true) ||
                            item.qualityTag.contains(trimmed, ignoreCase = true)
                        ) {
                            foundMap[item.slug] = item
                        }
                    }
                }
            }
            onProgress(scannedPages, foundMap.size)
        }

        return@withContext foundMap.values.toList()
    }

    /**
     * Get randomized lottery movies for recommendation
     */
    suspend fun getRandomLotteryMovies(count: Int = 15): List<MukulOttMovieItem> = withContext(Dispatchers.IO) {
        val allCached = pageCache.values.flatten()
        if (allCached.size >= count) {
            return@withContext allCached.shuffled().take(count)
        }

        // Load a few random pages to populate lottery pool
        val randomPages = listOf((1..15).random(), (16..35).random(), (1..5).random()).distinct()
        for (p in randomPages) {
            getMovies(p)
        }

        val pool = pageCache.values.flatten().distinctBy { it.slug }
        if (pool.isNotEmpty()) {
            pool.shuffled().take(count)
        } else {
            getMovies(1)
        }
    }

    /**
     * Fetch Movie Detail and Media Streams / Downloads.
     * Robustly parses:
     * - Top-level watch_sources & downloads
     * - Top-level watch_url
     * - Episode-level sources and downloads (e.g. series where sources are inside episodes[i].sources)
     * - If watch_sources is empty at top-level but episodes have sources, surfaces episode 1 sources to top-level
     * - Resolves proxy_url to absolute URL if starting with "/"
     */
    suspend fun getMovieDetail(slug: String): MukulOttMovieDetail? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/movie/$slug"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; MukulPlusApp/1.0)")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch movie detail for $slug: HTTP ${response.code}")
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            val screenshotsList = mutableListOf<String>()
            val ssArray = json.optJSONArray("screenshots")
            if (ssArray != null) {
                for (i in 0 until ssArray.length()) {
                    val ss = ssArray.optString(i)
                    if (ss.isNotEmpty()) screenshotsList.add(ss)
                }
            }

            // Helper to parse watch source JSON object
            fun parseWatchSource(wsObj: JSONObject, defaultEp: String? = null, epNumber: Int = 1): MukulOttWatchSource {
                val rawQuality = wsObj.opt("quality")
                val qualityInt = when (rawQuality) {
                    is Number -> rawQuality.toInt()
                    is String -> rawQuality.filter { it.isDigit() }.toIntOrNull() ?: 480
                    else -> 480
                }

                var proxy = wsObj.optString("proxy_url")
                val rawUrl = wsObj.optString("url")
                val directUrl = wsObj.optString("direct_url")
                val dlUrl = wsObj.optString("download_url")

                val targetUrl = rawUrl.ifEmpty { directUrl.ifEmpty { dlUrl } }
                if (proxy.isNotEmpty()) {
                    if (proxy.startsWith("/")) {
                        proxy = "$BASE_URL$proxy"
                    }
                    if (!proxy.contains("slug=")) {
                        val sep = if (proxy.contains("?")) "&" else "?"
                        proxy = "$proxy${sep}slug=${java.net.URLEncoder.encode(slug, "UTF-8")}&quality=$qualityInt&ep=$epNumber"
                    }
                } else if (targetUrl.isNotEmpty()) {
                    val encodedTarget = java.net.URLEncoder.encode(targetUrl, "UTF-8")
                    val encodedSlug = java.net.URLEncoder.encode(slug, "UTF-8")
                    proxy = "$BASE_URL/api/stream-proxy?url=$encodedTarget&slug=$encodedSlug&quality=$qualityInt&ep=$epNumber"
                }

                val ep = if (wsObj.isNull("episode")) defaultEp else wsObj.optString("episode")

                return MukulOttWatchSource(
                    url = rawUrl,
                    downloadUrl = dlUrl,
                    quality = qualityInt,
                    qualityLabel = wsObj.optString("quality_label"),
                    audio = wsObj.optString("audio"),
                    name = wsObj.optString("name"),
                    episode = ep,
                    directUrl = directUrl,
                    proxyUrl = proxy
                )
            }

            // Helper to parse download option JSON object
            fun parseDownloadOption(dlObj: JSONObject, matchingSources: List<MukulOttWatchSource>, defaultEp: String? = null, epNumber: Int = 1): MukulOttDownloadOption {
                val q = dlObj.optString("quality")
                val size = dlObj.optString("size")
                val gatePath = dlObj.optString("gate_path")
                val explicitLink = dlObj.optString("link")

                val qNum = q.filter { c -> c.isDigit() }.toIntOrNull()
                val matchingSource = matchingSources.firstOrNull {
                    qNum != null && it.quality == qNum
                }

                val directDl = if (!matchingSource?.proxyUrl.isNullOrEmpty()) {
                    matchingSource!!.proxyUrl
                } else if (explicitLink.startsWith("http")) {
                    explicitLink
                } else if (!matchingSource?.url.isNullOrEmpty()) {
                    val encodedTarget = java.net.URLEncoder.encode(matchingSource!!.url, "UTF-8")
                    val encodedSlug = java.net.URLEncoder.encode(slug, "UTF-8")
                    "$BASE_URL/api/stream-proxy?url=$encodedTarget&slug=$encodedSlug&quality=${matchingSource!!.quality}&ep=$epNumber"
                } else if (!matchingSource?.downloadUrl.isNullOrEmpty()) {
                    if (matchingSource!!.downloadUrl.startsWith("http")) {
                        val encodedTarget = java.net.URLEncoder.encode(matchingSource!!.downloadUrl, "UTF-8")
                        val encodedSlug = java.net.URLEncoder.encode(slug, "UTF-8")
                        "$BASE_URL/api/stream-proxy?url=$encodedTarget&slug=$encodedSlug&quality=720&ep=$epNumber"
                    } else {
                        matchingSource!!.downloadUrl
                    }
                } else if (matchingSources.isNotEmpty()) {
                    val first = matchingSources.first()
                    first.proxyUrl.ifEmpty {
                        if (first.url.isNotEmpty()) {
                            val encodedTarget = java.net.URLEncoder.encode(first.url, "UTF-8")
                            val encodedSlug = java.net.URLEncoder.encode(slug, "UTF-8")
                            "$BASE_URL/api/stream-proxy?url=$encodedTarget&slug=$encodedSlug&quality=${first.quality}&ep=$epNumber"
                        } else first.downloadUrl
                    }
                } else {
                    ""
                }

                val ep = if (dlObj.isNull("episode")) defaultEp else dlObj.optString("episode")

                return MukulOttDownloadOption(
                    gatePath = gatePath,
                    quality = if (q.isNotEmpty()) q else (if (qNum != null) "${qNum}p" else "HD"),
                    size = size,
                    episode = ep,
                    downloadUrl = directDl
                )
            }

            // 1. Parse top-level watch sources
            val topWatchSources = mutableListOf<MukulOttWatchSource>()
            val wsArray = json.optJSONArray("watch_sources")
            if (wsArray != null) {
                for (i in 0 until wsArray.length()) {
                    topWatchSources.add(parseWatchSource(wsArray.getJSONObject(i)))
                }
            }

            // 2. Parse top-level downloads
            val topDownloads = mutableListOf<MukulOttDownloadOption>()
            val dlArray = json.optJSONArray("downloads")
            if (dlArray != null) {
                for (i in 0 until dlArray.length()) {
                    topDownloads.add(parseDownloadOption(dlArray.getJSONObject(i), topWatchSources))
                }
            }

            // 3. Parse episodes
            val episodesList = mutableListOf<MukulOttEpisode>()
            val epArray = json.optJSONArray("episodes")
            if (epArray != null) {
                for (i in 0 until epArray.length()) {
                    val epObj = epArray.getJSONObject(i)
                    val epNum = epObj.optInt("episodeNumber", epObj.optInt("episode_number", i + 1))
                    val epTitle = epObj.optString("title", "Episode $epNum")
                    val epScreenshot = epObj.optString("screenshot")

                    // Parse episode-level sources
                    val epSources = mutableListOf<MukulOttWatchSource>()
                    val epWsArray = epObj.optJSONArray("sources")
                    if (epWsArray != null) {
                        for (sIdx in 0 until epWsArray.length()) {
                            epSources.add(parseWatchSource(epWsArray.getJSONObject(sIdx), defaultEp = epTitle, epNumber = epNum))
                        }
                    }

                    // Parse episode-level downloads
                    val epDownloads = mutableListOf<MukulOttDownloadOption>()
                    val epDlArray = epObj.optJSONArray("downloads")
                    if (epDlArray != null) {
                        for (dIdx in 0 until epDlArray.length()) {
                            epDownloads.add(parseDownloadOption(epDlArray.getJSONObject(dIdx), epSources, defaultEp = epTitle, epNumber = epNum))
                        }
                    }

                    // If episode downloads list is empty, generate download options from episode sources
                    if (epDownloads.isEmpty() && epSources.isNotEmpty()) {
                        for (src in epSources) {
                            val streamDl = src.proxyUrl.ifEmpty {
                                if (src.url.isNotEmpty()) "$BASE_URL/api/stream-proxy?url=" + java.net.URLEncoder.encode(src.url, "UTF-8") + "&slug=" + java.net.URLEncoder.encode(slug, "UTF-8") + "&quality=${src.quality}&ep=$epNum"
                                else src.downloadUrl
                            }
                            val estimatedSize = when {
                                src.quality >= 1080 -> "1.2 GB"
                                src.quality >= 720 -> "680 MB"
                                else -> "390 MB"
                            }
                            epDownloads.add(
                                MukulOttDownloadOption(
                                    quality = "${src.quality}p",
                                    size = estimatedSize,
                                    episode = epTitle,
                                    downloadUrl = streamDl
                                )
                            )
                        }
                    }

                    val firstEpStream = epSources.firstOrNull()?.let {
                        it.proxyUrl.ifEmpty { it.url.ifEmpty { it.directUrl } }
                    } ?: epObj.optString("stream_url", epObj.optString("url"))

                    val firstEpDl = epSources.firstOrNull()?.proxyUrl?.ifEmpty { epSources.firstOrNull()?.downloadUrl }
                        ?: epDownloads.firstOrNull()?.downloadUrl
                        ?: epObj.optString("download_url")

                    episodesList.add(
                        MukulOttEpisode(
                            id = epObj.optString("id", epNum.toString()),
                            title = epTitle,
                            episodeNumber = epNum,
                            streamUrl = firstEpStream,
                            downloadUrl = firstEpDl,
                            sources = epSources,
                            downloads = epDownloads,
                            screenshot = epScreenshot
                        )
                    )
                }
            }

            // If top-level watchSources is empty, check if any episodes have sources
            val finalWatchSources = if (topWatchSources.isEmpty() && episodesList.isNotEmpty()) {
                val ep1Sources = episodesList.firstOrNull { it.sources.isNotEmpty() }?.sources ?: emptyList()
                if (ep1Sources.isNotEmpty()) {
                    ep1Sources
                } else {
                    // Fallback: create virtual sources from episodes
                    episodesList.map { ep ->
                        MukulOttWatchSource(
                            url = ep.streamUrl,
                            downloadUrl = ep.downloadUrl,
                            quality = 720,
                            qualityLabel = "HD",
                            name = ep.title,
                            episode = ep.title
                        )
                    }
                }
            } else {
                topWatchSources
            }

            // If top-level downloads is empty, check if finalWatchSources has download links
            val finalDownloads = if (topDownloads.isEmpty()) {
                if (finalWatchSources.isNotEmpty()) {
                    finalWatchSources.map { src ->
                        val streamDl = src.proxyUrl.ifEmpty {
                            if (src.url.isNotEmpty()) "$BASE_URL/api/stream-proxy?url=" + java.net.URLEncoder.encode(src.url, "UTF-8")
                            else src.downloadUrl
                        }
                        MukulOttDownloadOption(
                            quality = "${src.quality}p",
                            size = "",
                            episode = src.episode,
                            downloadUrl = streamDl
                        )
                    }
                } else {
                    episodesList.flatMap { it.downloads }
                }
            } else {
                topDownloads.map { dl ->
                    if (dl.downloadUrl.isEmpty() || dl.downloadUrl.contains("fsldownload")) {
                        val matching = finalWatchSources.firstOrNull { src ->
                            val qNum = dl.quality.filter { it.isDigit() }.toIntOrNull()
                            qNum != null && src.quality == qNum
                        } ?: finalWatchSources.firstOrNull()
                        val newDl = matching?.proxyUrl?.ifEmpty { null } ?: dl.downloadUrl
                        dl.copy(downloadUrl = newDl)
                    } else dl
                }
            }

            // Fallback watchUrl
            var watchUrl = json.optString("watch_url")
            if (watchUrl.isEmpty()) {
                watchUrl = finalWatchSources.firstOrNull()?.let {
                    it.proxyUrl.ifEmpty { it.url.ifEmpty { it.directUrl } }
                } ?: episodesList.firstOrNull()?.streamUrl ?: ""
            }

            return@withContext MukulOttMovieDetail(
                ok = json.optBoolean("ok", true),
                slug = json.optString("slug", slug),
                kind = json.optString("kind", "movie"),
                title = json.optString("title"),
                poster = json.optString("poster"),
                screenshots = screenshotsList,
                description = json.optString("description"),
                genre = json.optString("genre"),
                language = json.optString("language"),
                quality = json.optString("quality"),
                resolution = json.optString("resolution"),
                watchUrl = watchUrl,
                watchSources = finalWatchSources,
                downloads = finalDownloads,
                episodes = episodesList,
                backdrop = json.optString("backdrop")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movie detail for $slug", e)
            return@withContext null
        }
    }
}
