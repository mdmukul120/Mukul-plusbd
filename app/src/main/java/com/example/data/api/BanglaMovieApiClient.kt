package com.example.data.api

import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object BanglaMovieApiClient {
    private const val TAG = "BanglaMovieApiClient"
    private const val BASE_URL = "https://banglamoveapi.opaalooman.com/api/movies"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Supported OTT Platforms
    val platforms = listOf(
        "সব (All)" to null,
        "Chorki" to "Chorki",
        "Hoichoi" to "Hoichoi",
        "Bongo" to "Bongo",
        "Toffee" to "Toffee",
        "Binge" to "Binge",
        "Deepto Play" to "Deepto Play",
        "iScreen" to "iScreen",
        "Netflix" to "Netflix",
        "Prime Video" to "Prime Video",
        "Disney+" to "Disney+",
        "Ullu" to "Ullu"
    )

    // Supported Languages
    val languages = listOf(
        "সব ভাষা" to null,
        "বাংলা" to "Bengali",
        "বাংলা ডাবড" to "Bangla Dubbed",
        "হিন্দি ডাবড" to "Hindi Dubbed",
        "হিন্দি" to "Hindi",
        "কোরিয়ান" to "Korean"
    )

    /**
     * Fetch list of movies with optional filters & pagination
     */
    suspend fun fetchMovies(
        page: Int = 1,
        limit: Int = 20,
        platform: String? = null,
        language: String? = null,
        type: String? = null,
        searchQuery: String? = null
    ): BanglaMoviesResponse = withContext(Dispatchers.IO) {
        val queryParams = mutableListOf<String>()
        queryParams.add("page=$page")
        queryParams.add("limit=$limit")

        if (!searchQuery.isNullOrBlank()) {
            queryParams.add("search=" + URLEncoder.encode(searchQuery.trim(), "UTF-8"))
        }
        if (!platform.isNullOrBlank()) {
            queryParams.add("platform=" + URLEncoder.encode(platform.trim(), "UTF-8"))
        }
        if (!language.isNullOrBlank()) {
            queryParams.add("language=" + URLEncoder.encode(language.trim(), "UTF-8"))
        }
        if (!type.isNullOrBlank()) {
            queryParams.add("type=" + URLEncoder.encode(type.trim(), "UTF-8"))
        }

        val url = "$BASE_URL?" + queryParams.joinToString("&")

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .build()

            val res = client.newCall(req).execute()
            val body = res.body?.string()
            if (!body.isNullOrEmpty()) {
                val json = JSONObject(body)
                val retPage = json.optInt("page", page)
                val totalMovies = json.optInt("totalMovies", 0)
                val totalPages = json.optInt("totalPages", 1)
                val moviesArr = json.optJSONArray("movies") ?: JSONArray()

                val movieList = mutableListOf<BanglaMovie>()
                for (i in 0 until moviesArr.length()) {
                    val m = moviesArr.getJSONObject(i)
                    movieList.add(
                        BanglaMovie(
                            id = m.optString("_id", ""),
                            title = m.optString("title", "Untitled"),
                            type = m.optString("type", "MOVIE"),
                            language = m.optString("language", ""),
                            tag = m.optString("tag", null),
                            rating = m.optDouble("rating", 0.0),
                            printQuality = m.optString("printQuality", "WEB-DL"),
                            poster = m.optString("poster", ""),
                            views = m.optInt("views", 0),
                            platform = m.optString("platform", null),
                            isPinned = m.optBoolean("isPinned", false),
                            releaseDate = m.optString("releaseDate", null)
                        )
                    )
                }

                return@withContext BanglaMoviesResponse(
                    page = retPage,
                    totalMovies = totalMovies,
                    totalPages = totalPages,
                    movies = movieList
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movies: ${e.message}")
        }

        BanglaMoviesResponse(page = page, totalMovies = 0, totalPages = 1, movies = emptyList())
    }

    /**
     * Fetch complete detail for a movie
     */
    suspend fun fetchMovieDetail(movieId: String): BanglaMovieDetail? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$movieId"
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .build()

            val res = client.newCall(req).execute()
            val body = res.body?.string()
            if (!body.isNullOrEmpty()) {
                val root = JSONObject(body)
                val m = root.optJSONObject("movie") ?: return@withContext null

                val id = m.optString("_id", movieId)
                val tmdbId = m.optString("tmdbId", null)
                val title = m.optString("title", "")
                val type = m.optString("type", "MOVIE")
                val language = m.optString("language", "")
                val rating = m.optDouble("rating", 0.0)
                val printQuality = m.optString("printQuality", "WEB-DL")
                val poster = m.optString("poster", "")
                val storyline = m.optString("storyline", "")
                val cast = m.optString("cast", "")
                val releaseDate = m.optString("releaseDate", "")
                val platform = m.optString("platform", "")
                val views = m.optInt("views", 0)

                // Screenshots
                val screensArr = m.optJSONArray("screenshots") ?: JSONArray()
                val screens = mutableListOf<String>()
                for (s in 0 until screensArr.length()) {
                    val sUrl = screensArr.optString(s, "")
                    if (sUrl.isNotBlank()) screens.add(sUrl)
                }

                // Genre
                val genreArr = m.optJSONArray("genre") ?: JSONArray()
                val genres = mutableListOf<String>()
                for (g in 0 until genreArr.length()) {
                    val gStr = genreArr.optString(g, "")
                    if (gStr.isNotBlank() && !gStr.startsWith("[")) {
                        genres.add(gStr.trimEnd(','))
                    }
                }

                // Qualities
                val qualArr = m.optJSONArray("qualities") ?: JSONArray()
                val directQualities = parseQualities(qualArr)

                // Download Servers
                val serverArr = m.optJSONArray("downloadServers") ?: JSONArray()
                val downloadServers = mutableListOf<BanglaDownloadServer>()
                for (s in 0 until serverArr.length()) {
                    val servObj = serverArr.getJSONObject(s)
                    val sName = servObj.optString("serverName", "Server ${s + 1}")
                    val sQuals = parseQualities(servObj.optJSONArray("qualities") ?: JSONArray())
                    val bundles = mutableListOf<BanglaEpisodeBundle>()

                    val bundArr = servObj.optJSONArray("episodeBundles") ?: JSONArray()
                    for (b in 0 until bundArr.length()) {
                        val bObj = bundArr.getJSONObject(b)
                        val bTitle = bObj.optString("bundleTitle", "")
                        val bRange = bObj.optString("episodeRange", "Episode ${b + 1}")
                        val bQuals = parseQualities(bObj.optJSONArray("qualities") ?: JSONArray())
                        bundles.add(
                            BanglaEpisodeBundle(
                                bundleTitle = bTitle,
                                episodeRange = bRange,
                                qualities = bQuals
                            )
                        )
                    }

                    downloadServers.add(
                        BanglaDownloadServer(
                            serverName = sName,
                            qualities = sQuals,
                            episodeBundles = bundles
                        )
                    )
                }

                return@withContext BanglaMovieDetail(
                    id = id,
                    tmdbId = tmdbId,
                    title = title,
                    type = type,
                    language = language,
                    genre = genres,
                    rating = rating,
                    printQuality = printQuality,
                    poster = poster,
                    screenshots = screens,
                    storyline = storyline,
                    cast = cast,
                    releaseDate = releaseDate,
                    platform = platform,
                    qualities = directQualities,
                    downloadServers = downloadServers,
                    views = views
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movie detail: ${e.message}")
        }
        null
    }

    private fun parseQualities(arr: JSONArray): List<BanglaMovieQuality> {
        val list = mutableListOf<BanglaMovieQuality>()
        for (q in 0 until arr.length()) {
            val qObj = arr.getJSONObject(q)
            list.add(
                BanglaMovieQuality(
                    label = qObj.optString("label", "HD"),
                    size = qObj.optString("size", ""),
                    downloadUrl = qObj.optString("downloadUrl", "")
                )
            )
        }
        return list
    }
}
