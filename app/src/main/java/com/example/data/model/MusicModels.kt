package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class MusicTrack(
    val id: String,
    val name: String,
    val albumName: String = "",
    val artistNames: String = "",
    val duration: Int = 0, // in seconds
    val imageUrl: String = "",
    val streamUrl: String = "",
    val downloadUrl: String = "",
    val language: String = "",
    val year: String = ""
) {
    val durationFormatted: String
        get() {
            if (duration <= 0) return "--:--"
            val minutes = duration / 60
            val seconds = duration % 60
            return "%02d:%02d".format(minutes, seconds)
        }
}

@Immutable
data class MusicPlaylist(
    val id: String,
    val name: String,
    val description: String = "",
    val imageUrl: String = "",
    val songCount: Int = 0,
    val type: String = "playlist",
    val language: String = ""
)

@Immutable
data class MusicAlbum(
    val id: String,
    val name: String,
    val artistNames: String = "",
    val imageUrl: String = "",
    val year: String = "",
    val songCount: Int = 0
)

data class MusicCategory(
    val id: String,
    val titleBn: String,
    val titleEn: String,
    val query: String,
    val playlistId: String? = null
)
