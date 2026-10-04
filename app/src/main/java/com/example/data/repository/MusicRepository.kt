package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.api.MusicApiClient
import com.example.data.model.MusicAlbum
import com.example.data.model.MusicCategory
import com.example.data.model.MusicPlaylist
import com.example.data.model.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MusicRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = context.getSharedPreferences("mukul_music_prefs", Context.MODE_PRIVATE)

    val categories = listOf(
        MusicCategory("hindi_90s", "হিন্দি ৯০s গোল্ডেন", "Hindi 1990s", "Hindi 1990s", playlistId = "1167751266"),
        MusicCategory("hindi_top", "হিন্দি টপ হিট্স", "Hindi Top Hits", "Hindi top hits"),
        MusicCategory("bindu", "বিন্দু ক্লাসিক হিট্স", "Bindu Hits", "Bindu"),
        MusicCategory("arijit", "অরিজিৎ সিং", "Arijit Singh", "Arijit Singh"),
        MusicCategory("bangla", "বাংলা সুপারহিট", "Bangla Hits", "Bengali top hits"),
        MusicCategory("romantic", "রোমান্টিক মেলোডি", "Romantic Melodies", "Romantic hindi hits"),
        MusicCategory("shreya", "শ্রেয়া ঘোষাল", "Shreya Ghoshal", "Shreya Ghoshal"),
        MusicCategory("atif", "আতিফ আসলাম", "Atif Aslam", "Atif Aslam"),
        MusicCategory("hindi_2000s", "বলিউড ২০০০s", "Hindi 2000s", "Hindi 2000s"),
        MusicCategory("kishore", "কিশোর কুমার", "Kishore Kumar", "Kishore Kumar")
    )

    private val defaultTrendingSongs = listOf(
        MusicTrack(
            id = "kesariya_default",
            name = "Kesariya (কেসরিয়া)",
            albumName = "Brahmastra",
            artistNames = "Arijit Singh, Pritam",
            duration = 268,
            imageUrl = "https://c.saavncdn.com/191/Kesariya-From-Brahmastra-Hindi-2022-20220717092820-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/191/69e8f6e80b2a752da3f6cf6310243e86_320.mp4",
            downloadUrl = "https://aac.saavncdn.com/191/69e8f6e80b2a752da3f6cf6310243e86_320.mp4",
            language = "Hindi",
            year = "2022"
        ),
        MusicTrack(
            id = "tum_hi_ho_default",
            name = "Tum Hi Ho (তুম হি হো)",
            albumName = "Aashiqui 2",
            artistNames = "Arijit Singh, Mithoon",
            duration = 262,
            imageUrl = "https://c.saavncdn.com/264/Aashiqui-2-Hindi-2013-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/264/8807d4b47636e05adfe786d70ffcbdf7_320.mp4",
            downloadUrl = "https://aac.saavncdn.com/264/8807d4b47636e05adfe786d70ffcbdf7_320.mp4",
            language = "Hindi",
            year = "2013"
        ),
        MusicTrack(
            id = "bindu_hits_default",
            name = "Chura Ke Dil Mera (বিন্দু ও বলিউড ক্লাসিক)",
            albumName = "Main Khiladi Tu Anari",
            artistNames = "Kumar Sanu, Alka Yagnik",
            duration = 472,
            imageUrl = "https://c.saavncdn.com/712/Main-Khiladi-Tu-Anari-Hindi-1994-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/712/e6628b0f94da943adabfe4122d256db0_320.mp4",
            downloadUrl = "https://aac.saavncdn.com/712/e6628b0f94da943adabfe4122d256db0_320.mp4",
            language = "Hindi",
            year = "1994"
        ),
        MusicTrack(
            id = "chaleya_default",
            name = "Chaleya (চলেয়া)",
            albumName = "Jawan",
            artistNames = "Arijit Singh, Shilpa Rao, Anirudh",
            duration = 200,
            imageUrl = "https://c.saavncdn.com/026/Chaleya-From-Jawan-Hindi-2023-20230814014339-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/026/6684aa5ba4d67319fa308e268a98b48f_320.mp4",
            downloadUrl = "https://aac.saavncdn.com/026/6684aa5ba4d67319fa308e268a98b48f_320.mp4",
            language = "Hindi",
            year = "2023"
        ),
        MusicTrack(
            id = "bangla_hit_default",
            name = "Tumi Robe Nirobe (তুমি রবে নীরবে)",
            albumName = "Rabindra Sangeet Melodies",
            artistNames = "Sahana Bajpaie",
            duration = 240,
            imageUrl = "https://c.saavncdn.com/152/Rabindra-Sangeet-Bengali-2016-500x500.jpg",
            streamUrl = "https://aac.saavncdn.com/152/491b9ee5ca36780775ffb96919eb6022_320.mp4",
            downloadUrl = "https://aac.saavncdn.com/152/491b9ee5ca36780775ffb96919eb6022_320.mp4",
            language = "Bengali",
            year = "2016"
        )
    )

    private val defaultFeaturedPlaylist = MusicPlaylist(
        id = "featured_2026",
        name = "🔥 নতুন সুপারহিট গান ও ৯০s গোল্ডেন",
        description = "Latest Bollywood, Arijit Singh & Bindu Classics",
        imageUrl = "https://c.saavncdn.com/191/Kesariya-From-Brahmastra-Hindi-2022-20220717092820-500x500.jpg",
        songCount = 50
    )

    private val _featuredPlaylist = MutableStateFlow<MusicPlaylist?>(defaultFeaturedPlaylist)
    val featuredPlaylist: StateFlow<MusicPlaylist?> = _featuredPlaylist.asStateFlow()

    private val _featuredSongs = MutableStateFlow<List<MusicTrack>>(defaultTrendingSongs)
    val featuredSongs: StateFlow<List<MusicTrack>> = _featuredSongs.asStateFlow()

    private val _topPlaylists = MutableStateFlow<List<MusicPlaylist>>(
        listOf(
            MusicPlaylist("1167751266", "হিন্দি ৯০s গোল্ডেন হিট্স", "Best of 1990s Bollywood", "https://c.saavncdn.com/712/Main-Khiladi-Tu-Anari-Hindi-1994-500x500.jpg", 50),
            MusicPlaylist("top_hindi_2026", "বলিউড টপ হিট্স ২০২৬", "Current Trending Songs", "https://c.saavncdn.com/026/Chaleya-From-Jawan-Hindi-2023-20230814014339-500x500.jpg", 40),
            MusicPlaylist("bangla_hits_pl", "বাংলা সুপারহিট প্লেলিস্ট", "Best Bangla Hits", "https://c.saavncdn.com/152/Rabindra-Sangeet-Bengali-2016-500x500.jpg", 30)
        )
    )
    val topPlaylists: StateFlow<List<MusicPlaylist>> = _topPlaylists.asStateFlow()

    private val _popularAlbums = MutableStateFlow<List<MusicAlbum>>(emptyList())
    val popularAlbums: StateFlow<List<MusicAlbum>> = _popularAlbums.asStateFlow()

    private val _trendingSongs = MutableStateFlow<List<MusicTrack>>(defaultTrendingSongs)
    val trendingSongs: StateFlow<List<MusicTrack>> = _trendingSongs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _favoriteSongIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteSongIds: StateFlow<Set<String>> = _favoriteSongIds.asStateFlow()

    init {
        loadFavoriteSongIds()
        refreshHomeMusic()
    }

    fun refreshHomeMusic() {
        scope.launch {
            _isLoading.value = true
            try {
                // 1. Load Latest Trending Hits (with fallback queries)
                var newHits = try {
                    MusicApiClient.searchSongs("Latest Bollywood 2026", limit = 50)
                } catch (_: Exception) { emptyList() }

                if (newHits.isEmpty()) {
                    newHits = try {
                        MusicApiClient.searchSongs("Arijit Singh", limit = 50)
                    } catch (_: Exception) { emptyList() }
                }
                if (newHits.isEmpty()) {
                    newHits = try {
                        MusicApiClient.searchSongs("Hindi Top Hits", limit = 50)
                    } catch (_: Exception) { emptyList() }
                }
                if (newHits.isNotEmpty()) {
                    _trendingSongs.value = newHits
                }

                // 2. Load Featured Playlist
                try {
                    val (pl, songs) = MusicApiClient.getPlaylistDetails("1167751266", limit = 50)
                    if (pl != null) {
                        _featuredPlaylist.value = pl
                        _featuredSongs.value = if (songs.isNotEmpty()) songs else newHits.take(50)
                    } else if (newHits.isNotEmpty()) {
                        val virtualPl = MusicPlaylist(
                            id = "featured_2026",
                            name = "🔥 নতুন সুপারহিট গান ২০২৬",
                            description = "Latest Bollywood & Bangla Hits",
                            imageUrl = newHits.first().imageUrl,
                            songCount = newHits.size
                        )
                        _featuredPlaylist.value = virtualPl
                        _featuredSongs.value = newHits
                    }
                } catch (_: Exception) {
                    if (newHits.isNotEmpty()) {
                        _featuredPlaylist.value = MusicPlaylist(
                            id = "featured_fallback",
                            name = "🔥 নতুন সুপারহিট গান ২০২৬",
                            description = "Latest Bollywood Hits",
                            imageUrl = newHits.first().imageUrl,
                            songCount = newHits.size
                        )
                        _featuredSongs.value = newHits
                    }
                }

                // 3. Load Top Trending Playlists
                try {
                    var playlists = MusicApiClient.searchPlaylists("Hindi top hits 2026", limit = 20)
                    if (playlists.isEmpty()) {
                        playlists = MusicApiClient.searchPlaylists("Bollywood hits", limit = 20)
                    }
                    if (playlists.isNotEmpty()) {
                        _topPlaylists.value = playlists
                    }
                } catch (e: Exception) {
                    Log.e("MusicRepository", "Error loading playlists", e)
                }

                // 4. Load Popular Albums
                try {
                    var albums = MusicApiClient.searchAlbums("Arijit Singh Bollywood Hits", limit = 20)
                    if (albums.isEmpty()) {
                        albums = MusicApiClient.searchAlbums("Bollywood", limit = 20)
                    }
                    if (albums.isNotEmpty()) {
                        _popularAlbums.value = albums
                    }
                } catch (e: Exception) {
                    Log.e("MusicRepository", "Error loading albums", e)
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error refreshing home music", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun loadCategorySongs(category: MusicCategory): Pair<MusicPlaylist?, List<MusicTrack>> {
        return if (!category.playlistId.isNullOrEmpty()) {
            MusicApiClient.getPlaylistDetails(category.playlistId, limit = 50)
        } else {
            val songs = MusicApiClient.searchSongs(category.query, limit = 50)
            val pl = MusicPlaylist(
                id = category.id,
                name = category.titleBn,
                description = category.titleEn,
                imageUrl = songs.firstOrNull()?.imageUrl ?: "",
                songCount = songs.size
            )
            Pair(pl, songs)
        }
    }

    suspend fun getPlaylist(id: String): Pair<MusicPlaylist?, List<MusicTrack>> {
        return MusicApiClient.getPlaylistDetails(id, limit = 50)
    }

    suspend fun getAlbum(id: String): Pair<MusicAlbum?, List<MusicTrack>> {
        return MusicApiClient.getAlbumDetails(id)
    }

    suspend fun searchAll(query: String): Triple<List<MusicTrack>, List<MusicAlbum>, List<MusicPlaylist>> {
        return MusicApiClient.globalSearch(query)
    }

    suspend fun searchSongsOnly(query: String): List<MusicTrack> {
        return MusicApiClient.searchSongs(query, limit = 50)
    }

    private fun loadFavoriteSongIds() {
        val raw = prefs.getString("favorite_songs", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                set.add(arr.getString(i))
            }
            _favoriteSongIds.value = set
        } catch (_: Exception) {}
    }

    fun toggleFavoriteSong(songId: String) {
        val current = _favoriteSongIds.value.toMutableSet()
        if (current.contains(songId)) {
            current.remove(songId)
        } else {
            current.add(songId)
        }
        _favoriteSongIds.value = current
        val arr = JSONArray()
        current.forEach { arr.put(it) }
        prefs.edit().putString("favorite_songs", arr.toString()).apply()
    }
}
