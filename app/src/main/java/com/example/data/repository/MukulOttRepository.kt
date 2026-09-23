package com.example.data.repository

import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object MukulOttRepository {
    private const val TAG = "MukulOttRepository"
    private const val BASE_URL = "https://mukul-ott.ai.studio"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getMovies(page: Int = 1): List<MukulOttMovieItem> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/api/movies?page=$page"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; MukulPlusApp/1.0)")
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch movies page $page: HTTP ${response.code}")
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val itemsArray = json.optJSONArray("items") ?: return@withContext emptyList()

            val list = mutableListOf<MukulOttMovieItem>()
            for (i in 0 until itemsArray.length()) {
                val obj = itemsArray.getJSONObject(i)
                list.add(
                    MukulOttMovieItem(
                        slug = obj.optString("slug"),
                        kind = obj.optString("kind", "movie"),
                        title = obj.optString("title"),
                        year = obj.optInt("year", 2026),
                        qualityTag = obj.optString("quality_tag"),
                        poster = obj.optString("poster")
                    )
                )
            }
            return@withContext list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movies page $page", e)
            return@withContext emptyList()
        }
    }

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

            // Parse watch sources (with streaming url & direct download url)
            val watchSourcesList = mutableListOf<MukulOttWatchSource>()
            val wsArray = json.optJSONArray("watch_sources")
            if (wsArray != null) {
                for (i in 0 until wsArray.length()) {
                    val wsObj = wsArray.getJSONObject(i)
                    val rawQuality = wsObj.opt("quality")
                    val qualityInt = when (rawQuality) {
                        is Number -> rawQuality.toInt()
                        is String -> rawQuality.filter { it.isDigit() }.toIntOrNull() ?: 480
                        else -> 480
                    }

                    watchSourcesList.add(
                        MukulOttWatchSource(
                            url = wsObj.optString("url"),
                            downloadUrl = wsObj.optString("download_url"),
                            quality = qualityInt,
                            qualityLabel = wsObj.optString("quality_label"),
                            audio = wsObj.optString("audio"),
                            name = wsObj.optString("name"),
                            episode = if (wsObj.isNull("episode")) null else wsObj.optString("episode"),
                            directUrl = wsObj.optString("direct_url")
                        )
                    )
                }
            }

            // Parse downloads array
            val downloadsList = mutableListOf<MukulOttDownloadOption>()
            val dlArray = json.optJSONArray("downloads")
            if (dlArray != null) {
                for (i in 0 until dlArray.length()) {
                    val dlObj = dlArray.getJSONObject(i)
                    val q = dlObj.optString("quality")
                    val size = dlObj.optString("size")
                    val gatePath = dlObj.optString("gate_path")

                    // Match matching watch_source to get real downloadUrl
                    val matchingSource = watchSourcesList.firstOrNull {
                        val num = q.filter { c -> c.isDigit() }.toIntOrNull()
                        num != null && it.quality == num
                    } ?: watchSourcesList.getOrNull(i)

                    val directDl = matchingSource?.downloadUrl ?: (if (gatePath.isNotEmpty()) "$BASE_URL$gatePath" else "")

                    downloadsList.add(
                        MukulOttDownloadOption(
                            gatePath = gatePath,
                            quality = q,
                            size = size,
                            episode = if (dlObj.isNull("episode")) null else dlObj.optString("episode"),
                            downloadUrl = directDl
                        )
                    )
                }
            }

            // Parse episodes if any
            val episodesList = mutableListOf<MukulOttEpisode>()
            val epArray = json.optJSONArray("episodes")
            if (epArray != null) {
                for (i in 0 until epArray.length()) {
                    val epObj = epArray.getJSONObject(i)
                    episodesList.add(
                        MukulOttEpisode(
                            id = epObj.optString("id"),
                            title = epObj.optString("title", "Episode ${i + 1}"),
                            episodeNumber = epObj.optInt("episode_number", i + 1),
                            streamUrl = epObj.optString("stream_url", epObj.optString("url")),
                            downloadUrl = epObj.optString("download_url")
                        )
                    )
                }
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
                watchUrl = json.optString("watch_url"),
                watchSources = watchSourcesList,
                downloads = downloadsList,
                episodes = episodesList,
                backdrop = json.optString("backdrop")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching movie detail for $slug", e)
            return@withContext null
        }
    }
}
