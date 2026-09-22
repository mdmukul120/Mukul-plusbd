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

    private val _featuredPlaylist = MutableStateFlow<MusicPlaylist?>(null)
    val featuredPlaylist: StateFlow<MusicPlaylist?> = _featuredPlaylist.asStateFlow()

    private val _featuredSongs = MutableStateFlow<List<MusicTrack>>(emptyList())
    val featuredSongs: StateFlow<List<MusicTrack>> = _featuredSongs.asStateFlow()

    private val _topPlaylists = MutableStateFlow<List<MusicPlaylist>>(emptyList())
    val topPlaylists: StateFlow<List<MusicPlaylist>> = _topPlaylists.asStateFlow()

    private val _popularAlbums = MutableStateFlow<List<MusicAlbum>>(emptyList())
    val popularAlbums: StateFlow<List<MusicAlbum>> = _popularAlbums.asStateFlow()

    private val _trendingSongs = MutableStateFlow<List<MusicTrack>>(emptyList())
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
                // 1. Load User's Hindi 1990s Playlist (id: 1167751266)
                val (pl, songs) = MusicApiClient.getPlaylistDetails("1167751266", limit = 30)
                if (pl != null) {
                    _featuredPlaylist.value = pl
                    _featuredSongs.value = songs
                }

                // 2. Load Hindi top hits playlists (User request #1)
                val playlists = MusicApiClient.searchPlaylists("Hindi top hits", limit = 15)
                if (playlists.isNotEmpty()) {
                    _topPlaylists.value = playlists
                }

                // 3. Load Popular Albums (e.g. Arijit Singh / Bollywood hits)
                val albums = MusicApiClient.searchAlbums("Bollywood hits", limit = 15)
                if (albums.isNotEmpty()) {
                    _popularAlbums.value = albums
                }

                // 4. Load Bindu Specials & Hindi Golden Hits
                val binduSongs = MusicApiClient.searchSongs("Bindu", limit = 15)
                if (binduSongs.isNotEmpty()) {
                    _trendingSongs.value = binduSongs
                } else if (songs.isNotEmpty()) {
                    _trendingSongs.value = songs.take(15)
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
            // First try searching songs, if few try playlist search
            val songs = MusicApiClient.searchSongs(category.query, limit = 30)
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
        return MusicApiClient.searchSongs(query, limit = 30)
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
