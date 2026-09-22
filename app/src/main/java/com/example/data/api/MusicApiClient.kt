package com.example.data.api

import android.util.Log
import com.example.data.model.MusicAlbum
import com.example.data.model.MusicPlaylist
import com.example.data.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object MusicApiClient {
    private const val TAG = "MusicApiClient"
    private const val BASE_URL = "https://music45-api.vercel.app"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun get(endpoint: String): String? {
        val url = if (endpoint.startsWith("http")) endpoint else "$BASE_URL$endpoint"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
            .header("Accept", "application/json")
            .build()

        return try {
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                res.body?.string()
            } else {
                Log.w(TAG, "Request failed: code=${res.code} for $url")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error on $url", e)
            null
        }
    }

    private fun extractImageUrl(imageObj: Any?): String {
        return when (imageObj) {
            is JSONArray -> {
                if (imageObj.length() == 0) ""
                else {
                    // Try last item (highest quality 500x500)
                    val last = imageObj.optJSONObject(imageObj.length() - 1)
                    last?.optString("url") ?: imageObj.optJSONObject(0)?.optString("url") ?: ""
                }
            }
            is JSONObject -> imageObj.optString("url")
            is String -> imageObj
            else -> ""
        }
    }

    private fun extractStreamAndDownloadUrls(dlObj: Any?): Pair<String, String> {
        if (dlObj is JSONArray && dlObj.length() > 0) {
            var streamUrl = ""
            var downloadUrl = ""

            // Find best 320kbps or 160kbps
            for (i in 0 until dlObj.length()) {
                val item = dlObj.optJSONObject(i) ?: continue
                val quality = item.optString("quality").lowercase()
                val url = item.optString("url")
                if (url.isNotEmpty()) {
                    if (quality.contains("320") || quality.contains("160") || streamUrl.isEmpty()) {
                        streamUrl = url
                    }
                    if (quality.contains("320") || downloadUrl.isEmpty()) {
                        downloadUrl = url
                    }
                }
            }
            val lastUrl = dlObj.optJSONObject(dlObj.length() - 1)?.optString("url") ?: ""
            if (streamUrl.isEmpty()) streamUrl = lastUrl
            if (downloadUrl.isEmpty()) downloadUrl = lastUrl
            return Pair(streamUrl, downloadUrl)
        }
        return Pair("", "")
    }

    private fun extractArtistNames(artistsObj: Any?): String {
        return when (artistsObj) {
            is JSONObject -> {
                val primary = artistsObj.optJSONArray("primary")
                if (primary != null && primary.length() > 0) {
                    val names = mutableListOf<String>()
                    for (i in 0 until primary.length()) {
                        val a = primary.optJSONObject(i)
                        val name = a?.optString("name")
                        if (!name.isNullOrBlank()) names.add(name)
                    }
                    names.joinToString(", ")
                } else {
                    val all = artistsObj.optJSONArray("all")
                    if (all != null && all.length() > 0) {
                        val names = mutableListOf<String>()
                        for (i in 0 until all.length()) {
                            val a = all.optJSONObject(i)
                            val name = a?.optString("name")
                            if (!name.isNullOrBlank()) names.add(name)
                        }
                        names.joinToString(", ")
                    } else ""
                }
            }
            is JSONArray -> {
                val names = mutableListOf<String>()
                for (i in 0 until artistsObj.length()) {
                    val a = artistsObj.optJSONObject(i)
                    val name = a?.optString("name")
                    if (!name.isNullOrBlank()) names.add(name)
                }
                names.joinToString(", ")
            }
            is String -> artistsObj
            else -> ""
        }
    }

    fun parseSongJson(json: JSONObject): MusicTrack {
        val id = json.optString("id")
        val name = json.optString("name", json.optString("title"))
        val duration = json.optInt("duration", 0)
        val language = json.optString("language", "")
        val year = json.optString("year", "")

        val albumObj = json.opt("album")
        val albumName = when (albumObj) {
            is JSONObject -> albumObj.optString("name", albumObj.optString("title"))
            is String -> albumObj
            else -> ""
        }

        val artistsStr = extractArtistNames(json.opt("artists"))
        val imageUrl = extractImageUrl(json.opt("image"))
        val (streamUrl, downloadUrl) = extractStreamAndDownloadUrls(json.opt("downloadUrl"))

        return MusicTrack(
            id = id,
            name = name,
            albumName = albumName,
            artistNames = if (artistsStr.isNotEmpty()) artistsStr else json.optString("subtitle", ""),
            duration = duration,
            imageUrl = imageUrl,
            streamUrl = streamUrl,
            downloadUrl = if (downloadUrl.isNotEmpty()) downloadUrl else streamUrl,
            language = language,
            year = year
        )
    }

    /**
     * Fetch Playlist details by ID e.g. 1167751266 (Hindi 1990s)
     */
    suspend fun getPlaylistDetails(id: String, page: Int = 1, limit: Int = 50): Pair<MusicPlaylist?, List<MusicTrack>> =
        withContext(Dispatchers.IO) {
            try {
                val body = get("/api/playlists?id=$id&page=$page&limit=$limit") ?: return@withContext Pair(null, emptyList())
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return@withContext Pair(null, emptyList())

                val playlist = MusicPlaylist(
                    id = data.optString("id"),
                    name = data.optString("name", data.optString("title")),
                    description = data.optString("description", ""),
                    imageUrl = extractImageUrl(data.opt("image")),
                    songCount = data.optInt("songCount", 0),
                    type = data.optString("type", "playlist"),
                    language = data.optString("language", "")
                )

                val songsArray = data.optJSONArray("songs") ?: JSONArray()
                val songs = mutableListOf<MusicTrack>()
                for (i in 0 until songsArray.length()) {
                    val sObj = songsArray.optJSONObject(i) ?: continue
                    songs.add(parseSongJson(sObj))
                }

                Pair(playlist, songs)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching playlist $id", e)
                Pair(null, emptyList())
            }
        }

    /**
     * Search playlists e.g. "Hindi top hits"
     */
    suspend fun searchPlaylists(query: String, limit: Int = 20): List<MusicPlaylist> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val body = get("/api/search/playlists?query=$encoded&limit=$limit") ?: return@withContext emptyList()
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return@withContext emptyList()
                val results = data.optJSONArray("results") ?: JSONArray()

                val list = mutableListOf<MusicPlaylist>()
                for (i in 0 until results.length()) {
                    val obj = results.optJSONObject(i) ?: continue
                    list.add(
                        MusicPlaylist(
                            id = obj.optString("id"),
                            name = obj.optString("name", obj.optString("title")),
                            description = obj.optString("description", obj.optString("subtitle", "")),
                            imageUrl = extractImageUrl(obj.opt("image")),
                            songCount = obj.optInt("songCount", 0),
                            type = "playlist",
                            language = obj.optString("language", "")
                        )
                    )
                }
                list
            } catch (e: Exception) {
                Log.e(TAG, "Error searching playlists for '$query'", e)
                emptyList()
            }
        }

    /**
     * Search albums e.g. "Arijit Singh"
     */
    suspend fun searchAlbums(query: String, limit: Int = 20): List<MusicAlbum> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val body = get("/api/search/albums?query=$encoded&limit=$limit") ?: return@withContext emptyList()
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return@withContext emptyList()
                val results = data.optJSONArray("results") ?: JSONArray()

                val list = mutableListOf<MusicAlbum>()
                for (i in 0 until results.length()) {
                    val obj = results.optJSONObject(i) ?: continue
                    list.add(
                        MusicAlbum(
                            id = obj.optString("id"),
                            name = obj.optString("name", obj.optString("title")),
                            artistNames = extractArtistNames(obj.opt("artists")),
                            imageUrl = extractImageUrl(obj.opt("image")),
                            year = obj.optString("year", ""),
                            songCount = obj.optInt("songCount", 0)
                        )
                    )
                }
                list
            } catch (e: Exception) {
                Log.e(TAG, "Error searching albums for '$query'", e)
                emptyList()
            }
        }

    /**
     * Get Album details & its songs e.g. id=38682222
     */
    suspend fun getAlbumDetails(id: String): Pair<MusicAlbum?, List<MusicTrack>> =
        withContext(Dispatchers.IO) {
            try {
                val body = get("/api/albums?id=$id") ?: return@withContext Pair(null, emptyList())
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return@withContext Pair(null, emptyList())

                val album = MusicAlbum(
                    id = data.optString("id"),
                    name = data.optString("name", data.optString("title")),
                    artistNames = extractArtistNames(data.opt("artists")),
                    imageUrl = extractImageUrl(data.opt("image")),
                    year = data.optString("year", ""),
                    songCount = data.optInt("songCount", 0)
                )

                val songsArray = data.optJSONArray("songs") ?: JSONArray()
                val songs = mutableListOf<MusicTrack>()
                for (i in 0 until songsArray.length()) {
                    val sObj = songsArray.optJSONObject(i) ?: continue
                    songs.add(parseSongJson(sObj))
                }

                Pair(album, songs)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching album $id", e)
                Pair(null, emptyList())
            }
        }

    /**
     * Search songs by query e.g. "Bindu" or "Chura Ke Dil Mera"
     */
    suspend fun searchSongs(query: String, limit: Int = 30): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val body = get("/api/search/songs?query=$encoded&limit=$limit") ?: return@withContext emptyList()
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return@withContext emptyList()
                val results = data.optJSONArray("results") ?: JSONArray()

                val list = mutableListOf<MusicTrack>()
                for (i in 0 until results.length()) {
                    val obj = results.optJSONObject(i) ?: continue
                    list.add(parseSongJson(obj))
                }
                list
            } catch (e: Exception) {
                Log.e(TAG, "Error searching songs for '$query'", e)
                emptyList()
            }
        }

    /**
     * Global Search: query returns songs, albums, and playlists
     */
    suspend fun globalSearch(query: String): Triple<List<MusicTrack>, List<MusicAlbum>, List<MusicPlaylist>> =
        withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val body = get("/api/search?query=$encoded") ?: return@withContext Triple(emptyList(), emptyList(), emptyList())
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: return@withContext Triple(emptyList(), emptyList(), emptyList())

                val songs = mutableListOf<MusicTrack>()
                val albums = mutableListOf<MusicAlbum>()
                val playlists = mutableListOf<MusicPlaylist>()

                // Songs
                val songsObj = data.optJSONObject("songs")
                val songResults = songsObj?.optJSONArray("results") ?: JSONArray()
                for (i in 0 until songResults.length()) {
                    val obj = songResults.optJSONObject(i) ?: continue
                    songs.add(parseSongJson(obj))
                }

                // Albums
                val albumsObj = data.optJSONObject("albums")
                val albumResults = albumsObj?.optJSONArray("results") ?: JSONArray()
                for (i in 0 until albumResults.length()) {
                    val obj = albumResults.optJSONObject(i) ?: continue
                    albums.add(
                        MusicAlbum(
                            id = obj.optString("id"),
                            name = obj.optString("title", obj.optString("name")),
                            artistNames = obj.optString("description", obj.optString("subtitle", "")),
                            imageUrl = extractImageUrl(obj.opt("image")),
                            year = obj.optString("year", ""),
                            songCount = 0
                        )
                    )
                }

                // Playlists
                val plObj = data.optJSONObject("playlists")
                val plResults = plObj?.optJSONArray("results") ?: JSONArray()
                for (i in 0 until plResults.length()) {
                    val obj = plResults.optJSONObject(i) ?: continue
                    playlists.add(
                        MusicPlaylist(
                            id = obj.optString("id"),
                            name = obj.optString("title", obj.optString("name")),
                            description = obj.optString("description", ""),
                            imageUrl = extractImageUrl(obj.opt("image")),
                            songCount = 0,
                            type = "playlist"
                        )
                    )
                }

                Triple(songs, albums, playlists)
            } catch (e: Exception) {
                Log.e(TAG, "Error in global search for '$query'", e)
                Triple(emptyList(), emptyList(), emptyList())
            }
        }
}
