package com.example.data.repository

import android.content.Context
import com.example.data.api.ApiClient
import com.example.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class MediaRepository(context: Context) {
    private val prefs = context.getSharedPreferences("mukul_plus_media_prefs", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    private val _menusData = MutableStateFlow(CtgMenusData())
    val menusData: StateFlow<CtgMenusData> = _menusData.asStateFlow()

    private val _cachedChannels = MutableStateFlow<List<TvChannel>>(emptyList())
    val cachedChannels: StateFlow<List<TvChannel>> = _cachedChannels.asStateFlow()

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        val favJson = prefs.getString("favorite_ids", "[]") ?: "[]"
        try {
            val arr = JSONArray(favJson)
            val set = mutableSetOf<Long>()
            for (i in 0 until arr.length()) {
                set.add(arr.getLong(i))
            }
            _favorites.value = set
        } catch (_: Exception) {
            _favorites.value = emptySet()
        }
    }

    fun toggleFavorite(movieId: Long) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(movieId)) {
            current.remove(movieId)
        } else {
            current.add(movieId)
        }
        _favorites.value = current
        val arr = JSONArray(current)
        prefs.edit().putString("favorite_ids", arr.toString()).apply()
    }

    fun isFavorite(movieId: Long): Boolean = _favorites.value.contains(movieId)

    suspend fun getMenus(): CtgMenusData {
        if (_menusData.value.movieGenres.isNotEmpty()) {
            return _menusData.value
        }
        val data = ApiClient.fetchCtgMenus()
        _menusData.value = data
        return data
    }

    suspend fun getChannels(forceRefresh: Boolean = false): List<TvChannel> {
        if (!forceRefresh && _cachedChannels.value.isNotEmpty()) {
            return _cachedChannels.value
        }
        val channels = ApiClient.fetchIptvChannels()
        _cachedChannels.value = channels
        return channels
    }

    private val _bongoVideos = MutableStateFlow<List<CtgMovie>>(emptyList())
    val bongoVideos: StateFlow<List<CtgMovie>> = _bongoVideos.asStateFlow()

    suspend fun getBongoVideos(forceRefresh: Boolean = false): List<CtgMovie> {
        if (!forceRefresh && _bongoVideos.value.isNotEmpty()) {
            return _bongoVideos.value
        }
        val list = ApiClient.fetchBongoVideos()
        _bongoVideos.value = list
        return list
    }

    suspend fun getMovieById(id: Long): CtgMovie? {
        if (id < 0) {
            return ApiClient.getBongoMovieById(id) ?: _bongoVideos.value.find { it.id == id }
        }
        return ApiClient.fetchCtgMovieDetail(id)
    }
}
