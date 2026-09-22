package com.example.data.model

data class CtgMovie(
    val id: Long,
    val title: String,
    val original_title: String? = null,
    val year: Int? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val online_rating: Double? = null,
    val user_rating: Double? = null,
    val genre: String? = null,
    val casts: String? = null,
    val overview: String? = null,
    val trailers: String? = null,
    val url: String? = null,
    val file_path: String? = null,
    val imdb_id: String? = null,
    val tmdb_id: String? = null,
    val Library: CtgLibrary? = null
) {
    fun getFullPosterUrl(): String {
        return when {
            poster_path.isNullOrEmpty() -> ""
            poster_path.startsWith("http") -> poster_path
            poster_path.startsWith("/") && (poster_path.endsWith(".jpg") || poster_path.endsWith(".png") || poster_path.endsWith(".webp")) && !poster_path.contains("storage") -> "https://image.tmdb.org/t/p/w500$poster_path"
            else -> "https://www.ctghall.com$poster_path"
        }
    }

    fun getFullBackdropUrl(): String {
        return when {
            backdrop_path.isNullOrEmpty() -> getFullPosterUrl()
            backdrop_path.startsWith("http") -> backdrop_path
            backdrop_path.startsWith("/") && (backdrop_path.endsWith(".jpg") || backdrop_path.endsWith(".png")) -> "https://image.tmdb.org/t/p/w780$backdrop_path"
            else -> "https://www.ctghall.com$backdrop_path"
        }
    }

    fun getFullStreamUrl(): String? {
        if (url.isNullOrEmpty()) return null
        return if (url.startsWith("http")) url else "https://www.ctghall.com$url"
    }
}

data class CtgLibrary(
    val id: Int,
    val name: String,
    val type: String? = null
)

data class CtgMoviesResponse(
    val total: Int = 0,
    val pages: Int = 0,
    val current_page: Int = 1,
    val data: List<CtgMovie> = emptyList()
)

data class CtgCategoryItem(
    val id: Int,
    val name: String,
    val type: String? = null,
    val parent: String? = null
)

data class CtgMenusData(
    val movieCategories: List<CtgCategoryItem> = emptyList(),
    val tvCategories: List<CtgCategoryItem> = emptyList(),
    val years: List<Int> = emptyList(),
    val movieGenres: List<String> = emptyList(),
    val tvGenres: List<String> = emptyList()
)

data class ExtractorProvider(
    val id: String,
    val name: String
)

data class ExtractorPost(
    val title: String,
    val link: String,
    val image: String? = null,
    val provider: String
)

data class DownloadLink(
    val title: String,
    val link: String,
    val type: String? = null,
    val quality: String? = null
)

data class ExtractorMovieInfo(
    val title: String,
    val synopsis: String? = null,
    val image: String? = null,
    val imdbId: String? = null,
    val type: String? = null,
    val downloadLinks: List<DownloadLink> = emptyList(),
    val streamLinks: List<DownloadLink> = emptyList()
)

data class TvChannel(
    val id: String,
    val name: String,
    val logo: String? = null,
    val groupTitle: String = "General",
    val streamUrl: String
)

data class TmdbSearchResult(
    val id: Long,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val release_date: String? = null,
    val vote_average: Double? = null
) {
    val displayTitle: String get() = title ?: name ?: "Untitled"
    val fullPosterUrl: String get() = if (poster_path.isNullOrEmpty()) "" else "https://image.tmdb.org/t/p/w500$poster_path"
    val fullBackdropUrl: String get() = if (backdrop_path.isNullOrEmpty()) fullPosterUrl else "https://image.tmdb.org/t/p/w780$backdrop_path"
}

data class TmdbVideo(
    val id: String,
    val key: String,
    val name: String,
    val site: String,
    val type: String
) {
    val youtubeUrl: String get() = "https://www.youtube.com/watch?v=$key"
}

data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val isGuest: Boolean = false,
    val isEmailVerified: Boolean = false
)
