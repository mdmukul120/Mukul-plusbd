package com.example.data.api

import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    private const val TMDB_API_KEY = "0b3d17a4fe3dd52593a48d9a0dad4bd6"
    private const val IPTV_URL = "https://raw.githubusercontent.com/abusaeeidx/Ayna-BDIX-IPTV-Playlist/refs/heads/main/ayna-playlist.m3u"
    private const val HAMYRA_API_BASE = "https://hamyra-api.mdibrahimkhalil516.workers.dev"
    private const val HAMYRA_ORIGIN = "https://www.hamyra.xyz"
    private const val HAMYRA_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    @Volatile
    private var hamyraToken: String? = null
    @Volatile
    private var hamyraTokenExp: Long = 0L

    @Synchronized
    fun getHamyraToken(): String? {
        val now = System.currentTimeMillis() / 1000
        if (!hamyraToken.isNullOrEmpty() && hamyraTokenExp > now + 30) {
            return hamyraToken
        }
        try {
            val req = Request.Builder()
                .url("$HAMYRA_API_BASE/auth/token")
                .header("Accept", "application/json")
                .header("Origin", HAMYRA_ORIGIN)
                .header("Referer", "$HAMYRA_ORIGIN/")
                .header("User-Agent", HAMYRA_USER_AGENT)
                .build()
            val res = client.newCall(req).execute()
            val body = res.body?.string()
            if (!body.isNullOrEmpty()) {
                val json = JSONObject(body)
                val token = json.optString("token")
                val exp = json.optLong("exp", now + 300)
                if (token.isNotEmpty()) {
                    hamyraToken = token
                    hamyraTokenExp = exp
                    return token
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Hamyra auth token", e)
        }
        return hamyraToken
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 1. CtgHall Movies List
    suspend fun fetchCtgMovies(
        library: Int? = 1,
        page: Int = 1,
        sort: String = "createdAt",
        sortOrder: String = "DESC",
        search: String? = null,
        year: Int? = null,
        genre: String? = null
    ): CtgMoviesResponse = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("https://www.ctghall.com/api/movies?fields=id,title,original_title,year,poster_path,release_date,rating,online_rating&sort=$sort&sort_order=$sortOrder&page=$page")
            if (library != null) {
                urlBuilder.append("&library=$library")
            }
            if (!search.isNullOrBlank()) {
                val encoded = java.net.URLEncoder.encode(search.trim(), "UTF-8")
                urlBuilder.append("&title=$encoded&search=$encoded")
            }
            if (year != null && year > 0) {
                urlBuilder.append("&year=$year")
            }
            if (!genre.isNullOrBlank()) {
                urlBuilder.append("&genre=${java.net.URLEncoder.encode(genre, "UTF-8")}")
            }
            val url = urlBuilder.toString()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MukulPlus-OTT/1.0")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext CtgMoviesResponse()
            val json = JSONObject(body)

            val total = json.optInt("total", 0)
            val pages = json.optInt("pages", 1)
            val currentPage = json.optInt("current_page", 1)
            val dataArray = json.optJSONArray("data") ?: JSONArray()

            val movies = mutableListOf<CtgMovie>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val libObj = item.optJSONObject("Library")
                val lib = if (libObj != null) {
                    CtgLibrary(
                        id = libObj.optInt("id", library ?: 1),
                        name = libObj.optString("name", "Movies"),
                        type = libObj.optString("type", "MOVIE")
                    )
                } else null

                movies.add(
                    CtgMovie(
                        id = item.optLong("id"),
                        title = item.optString("title", "Untitled"),
                        original_title = item.optString("original_title", null),
                        year = if (item.has("year") && !item.isNull("year")) item.optInt("year") else null,
                        poster_path = item.optString("poster_path", null),
                        backdrop_path = item.optString("backdrop_path", null),
                        release_date = item.optString("release_date", null),
                        online_rating = if (item.has("online_rating")) item.optDouble("online_rating") else null,
                        genre = item.optString("genre", null),
                        Library = lib
                    )
                )
            }
            CtgMoviesResponse(total = total, pages = pages, current_page = currentPage, data = movies)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching CtgMovies", e)
            CtgMoviesResponse()
        }
    }

    // 2. CtgHall Movie Detail
    suspend fun fetchCtgMovieDetail(id: Long): CtgMovie? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.ctghall.com/api/movies/$id"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MukulPlus-OTT/1.0")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val item = JSONObject(body)

            val libObj = item.optJSONObject("Library")
            val lib = if (libObj != null) {
                CtgLibrary(
                    id = libObj.optInt("id", 1),
                    name = libObj.optString("name", "Movies"),
                    type = libObj.optString("type", "MOVIE")
                )
            } else null

            CtgMovie(
                id = item.optLong("id", id),
                title = item.optString("title", "Untitled"),
                original_title = item.optString("original_title", null),
                year = if (item.has("year") && !item.isNull("year")) item.optInt("year") else null,
                poster_path = item.optString("poster_path", null),
                backdrop_path = item.optString("backdrop_path", null),
                release_date = item.optString("release_date", null),
                online_rating = if (item.has("online_rating")) item.optDouble("online_rating") else null,
                user_rating = if (item.has("user_rating")) item.optDouble("user_rating") else null,
                genre = item.optString("genre", null),
                casts = item.optString("casts", null),
                overview = item.optString("overview", null),
                trailers = item.optString("trailers", null),
                url = item.optString("url", null),
                file_path = item.optString("file_path", null),
                imdb_id = item.optString("imdb_id", null),
                tmdb_id = item.optString("tmdb_id", null),
                Library = lib
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movie detail: $id", e)
            null
        }
    }

    // 3. CtgHall Menus & Filters
    suspend fun fetchCtgMenus(): CtgMenusData = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.ctghall.com/api/menus"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext CtgMenusData()
            val json = JSONObject(body)

            val movieCategories = mutableListOf<CtgCategoryItem>()
            val tvCategories = mutableListOf<CtgCategoryItem>()
            val years = mutableListOf<Int>()
            val movieGenres = mutableListOf<String>()
            val tvGenres = mutableListOf<String>()

            val categoriesObj = json.optJSONObject("categories")
            if (categoriesObj != null) {
                val movieArr = categoriesObj.optJSONArray("movie") ?: JSONArray()
                for (i in 0 until movieArr.length()) {
                    val cat = movieArr.optJSONObject(i) ?: continue
                    movieCategories.add(
                        CtgCategoryItem(
                            id = cat.optInt("id"),
                            name = cat.optString("name"),
                            type = cat.optString("type"),
                            parent = cat.optString("parent")
                        )
                    )
                }
                val tvArr = categoriesObj.optJSONArray("tv") ?: JSONArray()
                for (i in 0 until tvArr.length()) {
                    val cat = tvArr.optJSONObject(i) ?: continue
                    tvCategories.add(
                        CtgCategoryItem(
                            id = cat.optInt("id"),
                            name = cat.optString("name"),
                            type = cat.optString("type"),
                            parent = cat.optString("parent")
                        )
                    )
                }
            }

            val yearsObj = json.optJSONObject("years")
            if (yearsObj != null) {
                val yearsArr = yearsObj.optJSONArray("movie") ?: JSONArray()
                for (i in 0 until yearsArr.length()) {
                    years.add(yearsArr.optInt(i))
                }
            }

            val genresObj = json.optJSONObject("genres")
            if (genresObj != null) {
                val mgArr = genresObj.optJSONArray("movie") ?: JSONArray()
                for (i in 0 until mgArr.length()) {
                    movieGenres.add(mgArr.optString(i))
                }
                val tgArr = genresObj.optJSONArray("tv") ?: JSONArray()
                for (i in 0 until tgArr.length()) {
                    tvGenres.add(tgArr.optString(i))
                }
            }

            CtgMenusData(
                movieCategories = movieCategories,
                tvCategories = tvCategories,
                years = years,
                movieGenres = movieGenres,
                tvGenres = tvGenres
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching menus", e)
            CtgMenusData(
                movieCategories = listOf(
                    CtgCategoryItem(1, "English Movies", "MOVIE"),
                    CtgCategoryItem(4, "Bollywood Movies", "MOVIE"),
                    CtgCategoryItem(5, "Asian & Anime", "MOVIE"),
                    CtgCategoryItem(6, "Bangla Movies", "MOVIE"),
                    CtgCategoryItem(7, "South Indian", "MOVIE")
                ),
                years = (2026 downTo 2010).toList(),
                movieGenres = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Drama", "Fantasy", "Horror", "Romance", "Sci-Fi", "Thriller")
            )
        }
    }

    // 4. SorryBroRewards Extractor Providers
    suspend fun fetchExtractorProviders(): List<ExtractorProvider> = withContext(Dispatchers.IO) {
        val defaultList = listOf(
            ExtractorProvider("moviesmod", "MoviesMod"),
            ExtractorProvider("topmovies", "TopMovies"),
            ExtractorProvider("uhd", "UHD Movies"),
            ExtractorProvider("moviesdrive", "MoviesDrive"),
            ExtractorProvider("fourkhd", "4K HD"),
            ExtractorProvider("hdhub4u", "HDHub4U"),
            ExtractorProvider("filmyfly", "FilmyFly"),
            ExtractorProvider("kat", "Kat Movie"),
            ExtractorProvider("showbox", "Showbox"),
            ExtractorProvider("castle", "Castle TV"),
            ExtractorProvider("allmovieland", "AllMovieLand")
        )
        try {
            val url = "https://api.sorrybrorewards.com/v2/extractor/api/providers"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext defaultList
            val json = JSONObject(body)
            if (json.optBoolean("success")) {
                val arr = json.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<ExtractorProvider>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    list.add(
                        ExtractorProvider(
                            id = item.optString("id"),
                            name = item.optString("name")
                        )
                    )
                }
                if (list.isNotEmpty()) return@withContext list
            }
            defaultList
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching providers", e)
            defaultList
        }
    }

    // 5. Extractor Posts
    suspend fun fetchExtractorPosts(provider: String, page: Int = 1, filter: String = ""): List<ExtractorPost> = withContext(Dispatchers.IO) {
        try {
            val encodedFilter = java.net.URLEncoder.encode(filter, "UTF-8")
            val url = "https://api.sorrybrorewards.com/v2/extractor/api/posts?provider=$provider&filter=$encodedFilter&page=$page"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val posts = mutableListOf<ExtractorPost>()
            if (json.optBoolean("success")) {
                val arr = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    posts.add(
                        ExtractorPost(
                            title = item.optString("title", "Untitled"),
                            link = item.optString("link", ""),
                            image = item.optString("image", null),
                            provider = provider
                        )
                    )
                }
            }
            posts
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching extractor posts for $provider", e)
            emptyList()
        }
    }

    // 6. Extractor Movie Info & Download Links
    suspend fun fetchExtractorInfo(link: String, provider: String): ExtractorMovieInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.sorrybrorewards.com/v2/extractor/api/info"
            val payload = JSONObject().apply {
                put("link", link)
                put("provider", provider)
            }
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(reqBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            if (!json.optBoolean("success")) return@withContext null

            val data = json.optJSONObject("data") ?: return@withContext null
            val title = data.optString("title", "Unknown")
            val synopsis = data.optString("synopsis", null)
            val image = data.optString("image", null)
            val imdbId = data.optString("imdbId", null)
            val type = data.optString("type", null)

            val downloads = mutableListOf<DownloadLink>()
            val streams = mutableListOf<DownloadLink>()

            val linkList = data.optJSONArray("linkList") ?: JSONArray()
            for (i in 0 until linkList.length()) {
                val item = linkList.optJSONObject(i) ?: continue
                val itemTitle = item.optString("title", "Quality Link")
                val quality = item.optString("quality", "HD")
                val episodesLink = item.optString("episodesLink", null)
                if (!episodesLink.isNullOrEmpty()) {
                    downloads.add(DownloadLink(title = itemTitle, link = episodesLink, type = "Episodes", quality = quality))
                }
                val directLinks = item.optJSONArray("directLinks") ?: JSONArray()
                for (j in 0 until directLinks.length()) {
                    val dLink = directLinks.optJSONObject(j) ?: continue
                    val dlTitle = dLink.optString("title", "Download $quality")
                    val dlUrl = dLink.optString("link", "")
                    val dlType = dLink.optString("type", "direct")
                    if (dlUrl.isNotEmpty()) {
                        downloads.add(DownloadLink(title = "$dlTitle ($quality)", link = dlUrl, type = dlType, quality = quality))
                        // Direct video / drive links can also be streamed
                        if (dlUrl.endsWith(".mp4") || dlUrl.endsWith(".mkv") || dlUrl.contains("stream") || dlUrl.contains("hubcloud") || dlUrl.contains("filepress")) {
                            streams.add(DownloadLink(title = "$quality Stream", link = dlUrl, type = dlType, quality = quality))
                        }
                    }
                }
            }

            ExtractorMovieInfo(
                title = title,
                synopsis = synopsis,
                image = image,
                imdbId = imdbId,
                type = type,
                downloadLinks = downloads,
                streamLinks = streams
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching extractor info", e)
            null
        }
    }

    // 6b. Extractor Episodes List (POST /api/episodes)
    suspend fun fetchExtractorEpisodes(url: String): List<DownloadLink> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://api.sorrybrorewards.com/v2/extractor/api/episodes"
            val payload = JSONObject().apply { put("url", url) }
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpoint).post(reqBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val list = mutableListOf<DownloadLink>()
            if (json.optBoolean("success")) {
                val arr = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val title = item.optString("title", "Episode ${i + 1}")
                    val link = item.optString("link", "")
                    if (link.isNotEmpty()) {
                        list.add(DownloadLink(title = title, link = link, type = "episode", quality = "HD"))
                    }
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching extractor episodes", e)
            emptyList()
        }
    }

    // 6c. Extractor Direct Stream & Download Servers (POST /api/stream)
    suspend fun fetchExtractorStream(url: String): List<DownloadLink> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://api.sorrybrorewards.com/v2/extractor/api/stream"
            val payload = JSONObject().apply { put("url", url) }
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpoint).post(reqBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val servers = mutableListOf<DownloadLink>()
            if (json.optBoolean("success")) {
                val data = json.optJSONObject("data") ?: return@withContext emptyList()
                val serverArr = data.optJSONArray("servers") ?: JSONArray()
                for (i in 0 until serverArr.length()) {
                    val s = serverArr.optJSONObject(i) ?: continue
                    val serverName = s.optString("server", "Server ${i + 1}")
                    val link = s.optString("link", "")
                    val type = s.optString("type", "video")
                    if (link.isNotEmpty()) {
                        servers.add(DownloadLink(title = serverName, link = link, type = type, quality = "Fast DL"))
                    }
                }
            }
            servers
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching extractor stream", e)
            emptyList()
        }
    }

    // 7. Live TV Channels (Hamyra 320+ Live TV Channels & Ayna BDIX Streams)
    suspend fun fetchIptvChannels(): List<TvChannel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<TvChannel>()
        
        // Step 1: Fetch verified live channels from Hamyra Live TV API
        val token = getHamyraToken()
        if (!token.isNullOrEmpty()) {
            try {
                val req = Request.Builder()
                    .url("$HAMYRA_API_BASE/livetv/channels")
                    .header("Authorization", "Bearer $token")
                    .header("Origin", HAMYRA_ORIGIN)
                    .header("Referer", "$HAMYRA_ORIGIN/")
                    .header("User-Agent", HAMYRA_USER_AGENT)
                    .build()
                val response = client.newCall(req).execute()
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val root = JSONObject(body)
                    val items = root.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val stream = item.optString("stream")
                            val name = item.optString("name").ifEmpty { item.optString("channel") }
                            if (stream.isEmpty() || name.isEmpty()) continue
                            val id = item.optString("id").ifEmpty { "hamyra_tv_$i" }
                            val poster = item.optString("poster")
                            val rawCategory = item.optString("category", "General")
                            val category = rawCategory.replaceFirstChar { it.uppercase() }

                            // Avoid exact stream duplicates
                            if (channels.none { it.name.equals(name, ignoreCase = true) || it.streamUrl == stream }) {
                                channels.add(
                                    TvChannel(
                                        id = id,
                                        name = name,
                                        logo = poster.ifEmpty { null },
                                        groupTitle = category,
                                        streamUrl = stream
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Hamyra Live TV channels", e)
            }
        }

        // Step 2: Merge Ayna BDIX IPTV playlist with remote=no_check_ip bypass
        try {
            val request = Request.Builder().url(IPTV_URL).build()
            val response = client.newCall(request).execute()
            val content = response.body?.string()
            if (!content.isNullOrEmpty()) {
                val lines = content.lines()
                var currentChannelName = ""
                var currentLogo = ""
                var currentGroup = "General"
                var currentId = ""

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXTINF:")) {
                        val idMatch = Regex("""tvg-id="([^"]*)"""").find(trimmed)
                        currentId = idMatch?.groupValues?.get(1) ?: ""

                        val logoMatch = Regex("""tvg-logo="([^"]*)"""").find(trimmed)
                        currentLogo = logoMatch?.groupValues?.get(1) ?: ""

                        val groupMatch = Regex("""group-title="([^"]*)"""").find(trimmed)
                        currentGroup = groupMatch?.groupValues?.get(1) ?: "General"

                        val commaIndex = trimmed.lastIndexOf(',')
                        currentChannelName = if (commaIndex != -1 && commaIndex + 1 < trimmed.length) {
                            trimmed.substring(commaIndex + 1).trim()
                        } else {
                            val nameMatch = Regex("""tvg-name="([^"]*)"""").find(trimmed)
                            nameMatch?.groupValues?.get(1) ?: "Channel"
                        }
                    } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        if (currentChannelName.isNotEmpty()) {
                            val finalStreamUrl = if (trimmed.contains("aynaott.com") || trimmed.contains("ayna")) {
                                if (!trimmed.contains("remote=no_check_ip")) {
                                    if (trimmed.contains("?")) "$trimmed&remote=no_check_ip" else "$trimmed?remote=no_check_ip"
                                } else trimmed
                            } else trimmed

                            if (channels.none { it.name.equals(currentChannelName, ignoreCase = true) }) {
                                channels.add(
                                    TvChannel(
                                        id = if (currentId.isNotEmpty()) currentId else "ch_${channels.size}",
                                        name = currentChannelName,
                                        logo = currentLogo.ifEmpty { null },
                                        groupTitle = currentGroup.ifEmpty { "General" },
                                        streamUrl = finalStreamUrl
                                    )
                                )
                            }
                            currentChannelName = ""
                            currentLogo = ""
                            currentGroup = "General"
                            currentId = ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Ayna IPTV playlist", e)
        }

        channels
    }

    // 8. TMDB Multi Search
    suspend fun searchTmdb(query: String): List<TmdbSearchResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&language=en-US&query=$encoded&page=1"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<TmdbSearchResult>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                list.add(
                    TmdbSearchResult(
                        id = item.optLong("id"),
                        title = item.optString("title", null),
                        name = item.optString("name", null),
                        overview = item.optString("overview", null),
                        poster_path = item.optString("poster_path", null),
                        backdrop_path = item.optString("backdrop_path", null),
                        release_date = item.optString("release_date", null),
                        vote_average = if (item.has("vote_average")) item.optDouble("vote_average") else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error searching TMDB", e)
            emptyList()
        }
    }

    // 9. TMDB Movie Videos & Trailers
    suspend fun fetchTmdbVideos(movieId: Long): List<TmdbVideo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.themoviedb.org/3/movie/$movieId/videos?api_key=$TMDB_API_KEY&language=en-US"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<TmdbVideo>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                list.add(
                    TmdbVideo(
                        id = item.optString("id"),
                        key = item.optString("key"),
                        name = item.optString("name", "Trailer"),
                        site = item.optString("site", "YouTube"),
                        type = item.optString("type", "Trailer")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB videos", e)
            emptyList()
        }
    }

    // 10. TMDB Recommendations
    suspend fun fetchTmdbRecommendations(movieId: Long): List<TmdbSearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.themoviedb.org/3/movie/$movieId/recommendations?api_key=$TMDB_API_KEY&language=en-US"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<TmdbSearchResult>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                list.add(
                    TmdbSearchResult(
                        id = item.optLong("id"),
                        title = item.optString("title", null),
                        name = item.optString("name", null),
                        overview = item.optString("overview", null),
                        poster_path = item.optString("poster_path", null),
                        backdrop_path = item.optString("backdrop_path", null),
                        release_date = item.optString("release_date", null),
                        vote_average = if (item.has("vote_average")) item.optDouble("vote_average") else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB recommendations", e)
            emptyList()
        }
    }

    // 11. Bongo BD Movies, Web Series & Natoks
    private val bongoMoviesCache = java.util.concurrent.ConcurrentHashMap<Long, CtgMovie>()

    suspend fun getBongoMovieById(id: Long): CtgMovie? {
        if (bongoMoviesCache.containsKey(id)) {
            return bongoMoviesCache[id]
        }
        val list = fetchBongoVideos()
        return list.find { it.id == id }
    }

    /**
     * Resolves a Bongo video URL or ID into the direct playable HLS stream URL (e.g. px.talkoraai.com)
     */
    fun resolveBongoStreamUrl(urlOrId: String): String {
        val bongoId = when {
            urlOrId.contains("id=") -> urlOrId.substringAfter("id=").substringBefore("&")
            urlOrId.contains("/bongo/hls/") -> urlOrId.substringAfter("/bongo/hls/").substringBefore("?")
            urlOrId.startsWith("http") -> urlOrId.substringAfterLast("/")
            else -> urlOrId
        }

        if (bongoId.isBlank()) return urlOrId

        val token = getHamyraToken()
        if (!token.isNullOrEmpty()) {
            try {
                val noRedirectClient = client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()

                val req = Request.Builder()
                    .url("$HAMYRA_API_BASE/bongo/hls?id=$bongoId")
                    .header("Authorization", "Bearer $token")
                    .header("Origin", HAMYRA_ORIGIN)
                    .header("Referer", "$HAMYRA_ORIGIN/")
                    .header("User-Agent", HAMYRA_USER_AGENT)
                    .build()

                val res = noRedirectClient.newCall(req).execute()
                val loc = res.header("Location")
                if (!loc.isNullOrEmpty()) {
                    Log.d(TAG, "Resolved Bongo HLS direct stream: $loc")
                    return loc
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving Bongo stream url for $bongoId", e)
            }
        }

        return if (urlOrId.startsWith("http")) urlOrId else "$HAMYRA_API_BASE/bongo/hls?id=$bongoId"
    }

    suspend fun fetchBongoVideos(): List<CtgMovie> = withContext(Dispatchers.IO) {
        if (bongoMoviesCache.isNotEmpty()) {
            return@withContext bongoMoviesCache.values.toList()
        }

        val allBongoVideos = mutableListOf<CtgMovie>()
        var dynamicId = -92000L

        // Helper to parse Bongo video JSON object from Hamyra scrape
        fun parseBongoItem(item: JSONObject) {
            val sysId = item.optString("systemId").ifEmpty { item.optString("id") }
            val title = item.optString("title")
            if (sysId.isEmpty() || title.isEmpty()) return

            if (allBongoVideos.any { it.file_path == sysId || it.title.equals(title, ignoreCase = true) }) {
                return
            }

            val thumbnail = item.optString("thumbnail").ifEmpty { item.optString("landscape") }
            val portrait = item.optString("portrait").ifEmpty { thumbnail }
            val cat = item.optString("category", "Bangla Drama")
            val synopsis = item.optString("synopsis", "$title - বংগো বিডি এক্সক্লুসিভ বাংলা নাটক ও ওয়েব সিরিজ।")
            val yearVal = item.optInt("year", 2024).let { if (it in 1990..2030) it else 2024 }

            allBongoVideos.add(
                CtgMovie(
                    id = dynamicId--,
                    title = title,
                    original_title = "Bongo BD • $cat",
                    year = yearVal,
                    poster_path = portrait.ifEmpty { thumbnail },
                    backdrop_path = thumbnail.ifEmpty { portrait },
                    release_date = "$yearVal-01-01",
                    online_rating = 8.8,
                    user_rating = 9.0,
                    genre = cat,
                    casts = "Bongo Cast",
                    overview = synopsis,
                    url = "$HAMYRA_API_BASE/bongo/hls?id=$sysId",
                    file_path = sysId
                )
            )
        }

        // 1. Fetch full catalog from Hamyra /bongo/scrape API
        val token = getHamyraToken()
        if (!token.isNullOrEmpty()) {
            try {
                val req = Request.Builder()
                    .url("$HAMYRA_API_BASE/bongo/scrape")
                    .header("Authorization", "Bearer $token")
                    .header("Origin", HAMYRA_ORIGIN)
                    .header("Referer", "$HAMYRA_ORIGIN/")
                    .header("User-Agent", HAMYRA_USER_AGENT)
                    .build()

                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val root = JSONObject(body)

                    // Categories with items
                    val cats = root.optJSONArray("categories")
                    if (cats != null) {
                        for (i in 0 until cats.length()) {
                            val catObj = cats.optJSONObject(i) ?: continue
                            val catName = catObj.optString("name", "Bongo")
                            val catItems = catObj.optJSONArray("items") ?: continue
                            for (j in 0 until catItems.length()) {
                                val item = catItems.optJSONObject(j) ?: continue
                                if (!item.has("category")) {
                                    item.put("category", catName)
                                }
                                parseBongoItem(item)
                            }
                        }
                    }

                    // Top-level items
                    val topItems = root.optJSONArray("items")
                    if (topItems != null) {
                        for (i in 0 until topItems.length()) {
                            val item = topItems.optJSONObject(i) ?: continue
                            parseBongoItem(item)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scraping Bongo videos from Hamyra API", e)
            }
        }

        // Base curated Bongo BD Originals & Hit Bangla Movies/Web Series with verified working streaming links
        val curatedBongoList = mutableListOf(
            CtgMovie(
                id = -90001L,
                title = "Surongo (2023)",
                original_title = "Surongo - Superhit Bangla Movie",
                year = 2023,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7UkHJogQ1QOihEwmK67-zQz9Pk3ie1408anxnX7aekiaWq9VhpJV1MiW2&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7UkHJogQ1QOihEwmK67-zQz9Pk3ie1408anxnX7aekiaWq9VhpJV1MiW2&s=10",
                release_date = "2023-06-29",
                online_rating = 9.3,
                user_rating = 9.5,
                genre = "Bangla Movie, Crime, Thriller, Heist",
                casts = "Afran Nisho, Toma Mirza, Mostafa Monwar",
                overview = "Masud, an electrician, goes to extreme lengths to satisfy his wife's ambitions, orchestrating an audacious bank tunnel heist that grips the nation.",
                url = "https://pixeldrain.dev/api/file/XLydnsWL?download"
            ),
            CtgMovie(
                id = -90002L,
                title = "Paap (2023) Web Series",
                original_title = "Paap - Bongo Original Series",
                year = 2023,
                poster_path = "https://cdn.bongo-solutions.com/919f93a7-400e-4149-a70d-204beb589074/content/fedf967e-f5e1-4c72-b394-6cf4229b67aa/2f2a30d5-7f4e-4e13-a378-0c62c3d10ee2.jpg",
                backdrop_path = "https://cdn.bongo-solutions.com/919f93a7-400e-4149-a70d-204beb589074/content/fedf967e-f5e1-4c72-b394-6cf4229b67aa/2f2a30d5-7f4e-4e13-a378-0c62c3d10ee2.jpg",
                release_date = "2023-04-20",
                online_rating = 8.7,
                user_rating = 8.9,
                genre = "Bongo Original, Crime, Mystery, Thriller",
                casts = "Puja Cherry, Zakia Bari Mamo, Aman Reza",
                overview = "A murder mystery set during a lavish family puja celebration. Dark family secrets unfold as the police investigator uncovers shocking betrayal.",
                url = "https://pixeldrain.dev/api/file/jWwZ88kD?download"
            ),
            CtgMovie(
                id = -90003L,
                title = "K.G.F: Chapter 1 (2018)",
                original_title = "KGF Chapter 1 Bangla Dubbed",
                year = 2018,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRpAaOJD3Mq-QMINU66EGpF8dZmTUrfGnvVrjeeQ6hMPklCgJaxsBtE0gHg&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRpAaOJD3Mq-QMINU66EGpF8dZmTUrfGnvVrjeeQ6hMPklCgJaxsBtE0gHg&s=10",
                release_date = "2018-12-21",
                online_rating = 9.4,
                user_rating = 9.6,
                genre = "Action, Thriller, Bangla Dubbed",
                casts = "Yash, Srinidhi Shetty, Ramachandra Raju",
                overview = "Rocky, a fierce young man raised in Mumbai streets, arrives at Kolar Gold Fields to fulfill a mother's dying promise and conquer an empire.",
                url = "https://pixeldrain.dev/api/file/sow6gevq?download"
            ),
            CtgMovie(
                id = -90004L,
                title = "K.G.F: Chapter 2 (2022)",
                original_title = "KGF Chapter 2 Bangla Dubbed",
                year = 2022,
                poster_path = "https://upload.wikimedia.org/wikipedia/en/d/d0/K.G.F_Chapter_2.jpg",
                backdrop_path = "https://upload.wikimedia.org/wikipedia/en/d/d0/K.G.F_Chapter_2.jpg",
                release_date = "2022-04-14",
                online_rating = 9.5,
                user_rating = 9.7,
                genre = "Action, Drama, Bangla Dubbed",
                casts = "Yash, Sanjay Dutt, Raveena Tandon, Srinidhi Shetty",
                overview = "The blood-soaked land of Kolar Gold Fields has a new overlord now - Rocky. His name strikes fear into the hearts of his foes as government and assassins close in.",
                url = "https://pixeldrain.dev/api/file/2ghPTUyu?download"
            ),
            CtgMovie(
                id = -90005L,
                title = "Satyabhama (2024)",
                original_title = "Satyabhama Bangla Dubbed",
                year = 2024,
                poster_path = "https://i.ibb.co.com/NnYfgZCh/20250510-183927.jpg",
                backdrop_path = "https://i.ibb.co.com/NnYfgZCh/20250510-183927.jpg",
                release_date = "2024-06-07",
                online_rating = 8.8,
                user_rating = 9.0,
                genre = "Action, Crime, Mystery, Bangla Dubbed",
                casts = "Kajal Aggarwal, Naveen Chandra, Prakash Raj",
                overview = "Determined ACP Satyabhama dives into the shadows to track down a missing girl, exposing an underworld syndicate of corruption.",
                url = "https://pixeldrain.dev/api/file/8tAsgUTc?download"
            ),
            CtgMovie(
                id = -90006L,
                title = "Ala Vaikunthapurramuloo (2020)",
                original_title = "Ala Vaikunthapurramuloo Bangla Dubbed",
                year = 2020,
                poster_path = "https://i.ibb.co.com/7dKbtRhd/20250428-175512.jpg",
                backdrop_path = "https://i.ibb.co.com/7dKbtRhd/20250428-175512.jpg",
                release_date = "2020-01-12",
                online_rating = 9.1,
                user_rating = 9.3,
                genre = "Action, Comedy, Drama, Bangla Dubbed",
                casts = "Allu Arjun, Pooja Hegde, Tabu, Jayaram",
                overview = "Bantu grows up being despised by his father Valmiki, unaware that he was switched at birth and is heir to a sprawling business fortune.",
                url = "https://pixeldrain.dev/api/file/iKk8dMra?download"
            ),
            CtgMovie(
                id = -90007L,
                title = "Sultan: The Saviour (2018)",
                original_title = "Sultan The Saviour Bengali Movie",
                year = 2018,
                poster_path = "https://i.ibb.co.com/Ytd5pN5/20241117-203631.jpg",
                backdrop_path = "https://i.ibb.co.com/Ytd5pN5/20241117-203631.jpg",
                release_date = "2018-06-15",
                online_rating = 8.9,
                user_rating = 9.1,
                genre = "Bangla Movie, Action, Thriller",
                casts = "Jeet, Bidya Sinha Mim, Priyanka Sarkar",
                overview = "A courageous cab driver with a hidden past stands up against an extortion cartel to protect innocent lives and his beloved sister.",
                url = "https://pixeldrain.dev/api/file/epLfcz2F?download"
            ),
            CtgMovie(
                id = -90008L,
                title = "Khoka 420 (2013)",
                original_title = "Khoka 420 Bengali Movie",
                year = 2013,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTf0n-ZDiJgcXxRifolFxWEZlGrh1uaZYuwHUJYgZ82ED8Nd-LH2ozg96y_&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTf0n-ZDiJgcXxRifolFxWEZlGrh1uaZYuwHUJYgZ82ED8Nd-LH2ozg96y_&s=10",
                release_date = "2013-06-14",
                online_rating = 8.8,
                user_rating = 9.0,
                genre = "Bangla Movie, Romantic Comedy, Action",
                casts = "Dev, Subhashree Ganguly, Nusrat Jahan",
                overview = "Krish pretends to be Bhoomi's boyfriend to help her avoid an unwanted alliance, unleashing a series of comical mix-ups and genuine romance.",
                url = "https://pixeldrain.dev/api/file/PDwpFMz1?download"
            ),
            CtgMovie(
                id = -90009L,
                title = "Khokababu (2012)",
                original_title = "Khokababu Bengali Movie",
                year = 2012,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRVFyaQjXZveLFlpHuV4FP1b75X9cI-3ICKhm_UNDzgMn67SY9FCAY_2Yo&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRVFyaQjXZveLFlpHuV4FP1b75X9cI-3ICKhm_UNDzgMn67SY9FCAY_2Yo&s=10",
                release_date = "2012-01-13",
                online_rating = 8.7,
                user_rating = 8.9,
                genre = "Bangla Movie, Action, Romance",
                casts = "Dev, Subhashree Ganguly, Ferdous Ahmed",
                overview = "Khoka is an accountant who accidentally gets employed by the city's notorious don Shankar Das and falls in love with Shankar's sister Pooja.",
                url = "https://pixeldrain.dev/api/file/oj52VSLL?download"
            ),
            CtgMovie(
                id = -90010L,
                title = "Bagh Bandhi Khela (2018)",
                original_title = "Bagh Bandhi Khela Bengali",
                year = 2018,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTZGasp6czaKZN35O6oON3mdqL66s4or4Pfjhw07IxBTUSVNXgjAGdqL3k&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTZGasp6czaKZN35O6oON3mdqL66s4or4Pfjhw07IxBTUSVNXgjAGdqL3k&s=10",
                release_date = "2018-11-16",
                online_rating = 8.6,
                user_rating = 8.8,
                genre = "Bangla Movie, Thriller, Action",
                casts = "Jeet, Prosenjit Chatterjee, Soham Chakraborty, Sayantika",
                overview = "An anthology thriller tracking three parallel stories of intrigue, retribution, and heart-pounding survival.",
                url = "https://pixeldrain.dev/api/file/FWu5iEZP?download"
            ),
            CtgMovie(
                id = -90011L,
                title = "Hoichoi Unlimited (2018)",
                original_title = "Hoichoi Unlimited Comedy",
                year = 2018,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRT1jX_zwSyMCjEfO7Wt6kvMqItH8KeMzFYA2ouootoiLHLl-zDz56GQl_J&s=10" ,
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRT1jX_zwSyMCjEfO7Wt6kvMqItH8KeMzFYA2ouootoiLHLl-zDz56GQl_J&s=10",
                release_date = "2018-10-12",
                online_rating = 8.5,
                user_rating = 8.7,
                genre = "Bangla Movie, Comedy",
                casts = "Dev, Koushani Mukherjee, Puja Banerjee, Kharaj Mukherjee",
                overview = "Four married men plan a secret holiday escape to Uzbekistan, but their hilarious attempts to hide it lead to uncontrollable comic pandemonium.",
                url = "https://pixeldrain.dev/api/file/UL3cWj9m?download"
            ),
            CtgMovie(
                id = -90012L,
                title = "Total Dadagiri (2018)",
                original_title = "Total Dadagiri Romantic Action",
                year = 2018,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTZ_3GXg0OQhP0JflKeSbvbhTwY7L9cFcBNuCZsp7cEKcf3s2kTbCtewNjQ&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTZ_3GXg0OQhP0JflKeSbvbhTwY7L9cFcBNuCZsp7cEKcf3s2kTbCtewNjQ&s=10",
                release_date = "2018-01-19",
                online_rating = 8.6,
                user_rating = 8.7,
                genre = "Bangla Movie, Romantic Action",
                casts = "Yash Dasgupta, Mimi Chakraborty",
                overview = "Joy falls heads over heels for Jonaki, the daughter of a stern college professor, risking everything to win her love.",
                url = "https://pixeldrain.dev/api/file/dSXCK8qv?download"
            )
        )

        // Dynamically fetch and merge additional Bongo & Bangla titles from live playlist
        try {
            val playlistUrl = "https://raw.githubusercontent.com/abusaeeidx/Movie-Playlist-Auto-update/main/Bangla_Movies.m3u"
            val req = Request.Builder().url(playlistUrl).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string()
            if (!body.isNullOrEmpty()) {
                var nextDynamicId = -91000L
                var currentTitle = ""
                var currentLogo = ""
                for (line in body.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXTINF")) {
                        if (trimmed.contains("tvg-logo=\"")) {
                            currentLogo = trimmed.substringAfter("tvg-logo=\"").substringBefore("\"")
                        }
                        val namePart = trimmed.substringAfterLast(",")
                        if (namePart.isNotBlank()) {
                            currentTitle = namePart.trim()
                        }
                    } else if (trimmed.startsWith("http")) {
                        if (currentTitle.isNotBlank() && (
                            currentTitle.contains("bongo", ignoreCase = true) ||
                            currentTitle.contains("bangla", ignoreCase = true) ||
                            currentTitle.contains("surongo", ignoreCase = true) ||
                            currentTitle.contains("paap", ignoreCase = true) ||
                            trimmed.contains("bongo", ignoreCase = true)
                        )) {
                            // Filter out known broken/restricted domains
                            if (!trimmed.contains("r2.dev") && !trimmed.contains("ftpbd.net") && !trimmed.contains("circleftp.net")) {
                                if (curatedBongoList.none { it.title.equals(currentTitle, ignoreCase = true) }) {
                                    curatedBongoList.add(
                                        CtgMovie(
                                            id = nextDynamicId--,
                                            title = currentTitle,
                                            original_title = "Bangla Video",
                                            year = 2024,
                                            poster_path = currentLogo.ifBlank { "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7UkHJogQ1QOihEwmK67-zQz9Pk3ie1408anxnX7aekiaWq9VhpJV1MiW2&s=10" },
                                            backdrop_path = currentLogo.ifBlank { "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7UkHJogQ1QOihEwmK67-zQz9Pk3ie1408anxnX7aekiaWq9VhpJV1MiW2&s=10" },
                                            online_rating = 8.5,
                                            genre = "Bangla, OTT",
                                            url = trimmed,
                                            overview = "$currentTitle - Stream exclusively on Mukul Plus OTT."
                                        )
                                    )
                                }
                            }
                        }
                        currentTitle = ""
                        currentLogo = ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching dynamic Bongo playlist", e)
        }

        // Combine curated highlights first, followed by all dynamically fetched Bongo videos
        val finalList = mutableListOf<CtgMovie>()
        finalList.addAll(curatedBongoList)

        for (bongoVideo in allBongoVideos) {
            if (finalList.none { it.file_path == bongoVideo.file_path || it.title.equals(bongoVideo.title, ignoreCase = true) }) {
                finalList.add(bongoVideo)
            }
        }

        finalList.forEach { movie ->
            bongoMoviesCache[movie.id] = movie
        }

        finalList
    }
}
