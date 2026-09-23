package com.example.data.model

data class MukulOttMovieItem(
    val slug: String,
    val kind: String, // "movie" or "series"
    val title: String,
    val year: Int = 2026,
    val qualityTag: String = "",
    val poster: String = ""
)

data class MukulOttWatchSource(
    val url: String,
    val downloadUrl: String,
    val quality: Int, // e.g. 1080, 720, 480
    val qualityLabel: String = "",
    val audio: String = "",
    val name: String = "",
    val episode: String? = null,
    val directUrl: String = ""
)

data class MukulOttDownloadOption(
    val gatePath: String = "",
    val quality: String, // "1080p", "720p", "480p"
    val size: String = "", // "1.5 GB", "687 MB", "416 MB"
    val episode: String? = null,
    val downloadUrl: String = ""
)

data class MukulOttEpisode(
    val id: String = "",
    val title: String = "",
    val episodeNumber: Int = 1,
    val streamUrl: String = "",
    val downloadUrl: String = ""
)

data class MukulOttMovieDetail(
    val ok: Boolean,
    val slug: String,
    val kind: String,
    val title: String,
    val poster: String = "",
    val screenshots: List<String> = emptyList(),
    val description: String = "",
    val genre: String = "",
    val language: String = "",
    val quality: String = "",
    val resolution: String = "",
    val watchUrl: String = "",
    val watchSources: List<MukulOttWatchSource> = emptyList(),
    val downloads: List<MukulOttDownloadOption> = emptyList(),
    val episodes: List<MukulOttEpisode> = emptyList(),
    val backdrop: String = ""
)
