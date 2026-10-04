package com.example.data.model

data class BanglaMovie(
    val id: String,
    val title: String,
    val type: String = "MOVIE", // MOVIE or SERIES
    val language: String = "",
    val tag: String? = null,
    val rating: Double = 0.0,
    val printQuality: String = "WEB-DL",
    val poster: String = "",
    val views: Int = 0,
    val platform: String? = null,
    val isPinned: Boolean = false,
    val releaseDate: String? = null
)

data class BanglaMovieQuality(
    val label: String,
    val size: String,
    val downloadUrl: String
)

data class BanglaEpisodeBundle(
    val bundleTitle: String,
    val episodeRange: String,
    val qualities: List<BanglaMovieQuality>
)

data class BanglaDownloadServer(
    val serverName: String,
    val qualities: List<BanglaMovieQuality>,
    val episodeBundles: List<BanglaEpisodeBundle>
)

data class BanglaMovieDetail(
    val id: String,
    val tmdbId: String? = null,
    val title: String,
    val type: String = "MOVIE",
    val language: String = "",
    val genre: List<String> = emptyList(),
    val rating: Double = 0.0,
    val printQuality: String = "WEB-DL",
    val poster: String = "",
    val screenshots: List<String> = emptyList(),
    val storyline: String = "",
    val cast: String = "",
    val releaseDate: String = "",
    val platform: String = "",
    val qualities: List<BanglaMovieQuality> = emptyList(),
    val downloadServers: List<BanglaDownloadServer> = emptyList(),
    val views: Int = 0
)

data class BanglaMoviesResponse(
    val page: Int,
    val totalMovies: Int,
    val totalPages: Int,
    val movies: List<BanglaMovie>
)
