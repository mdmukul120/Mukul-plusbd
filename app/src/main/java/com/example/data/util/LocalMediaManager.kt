package com.example.data.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LocalVideoItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val filePath: String,
    val contentUri: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val resolution: String = "",
    val folderName: String = "Internal",
    val dateModified: Long = 0L
) {
    val durationFormatted: String
        get() {
            if (durationMs <= 0) return "--:--"
            val sec = (durationMs / 1000) % 60
            val min = (durationMs / (1000 * 60)) % 60
            val hr = durationMs / (1000 * 60 * 60)
            return if (hr > 0) String.format("%d:%02d:%02d", hr, min, sec) else String.format("%02d:%02d", min, sec)
        }

    val sizeFormatted: String
        get() = LocalMediaManager.formatFileSize(sizeBytes)
}

data class LocalAudioItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val artist: String = "Unknown Artist",
    val album: String = "Unknown Album",
    val filePath: String,
    val contentUri: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val folderName: String = "Music",
    val dateModified: Long = 0L
) {
    val durationFormatted: String
        get() {
            if (durationMs <= 0) return "--:--"
            val sec = (durationMs / 1000) % 60
            val min = (durationMs / (1000 * 60)) % 60
            val hr = durationMs / (1000 * 60 * 60)
            return if (hr > 0) String.format("%d:%02d:%02d", hr, min, sec) else String.format("%02d:%02d", min, sec)
        }

    val sizeFormatted: String
        get() = LocalMediaManager.formatFileSize(sizeBytes)
}

object LocalMediaManager {
    private const val TAG = "LocalMediaManager"

    fun hasMediaPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val videoGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            videoGranted || audioGranted
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    suspend fun getAllVideos(context: Context): List<LocalVideoItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<LocalVideoItem>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndex(MediaStore.Video.Media.TITLE)
                val displayCol = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = it.getColumnIndex(MediaStore.Video.Media.DATA)
                val durCol = it.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = it.getColumnIndex(MediaStore.Video.Media.SIZE)
                val resCol = it.getColumnIndex(MediaStore.Video.Media.RESOLUTION)
                val dateCol = it.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val bucketCol = it.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = if (titleCol != -1) it.getString(titleCol) ?: "" else ""
                    val displayName = if (displayCol != -1) it.getString(displayCol) ?: "" else ""
                    val path = if (dataCol != -1) it.getString(dataCol) ?: "" else ""
                    val duration = if (durCol != -1) it.getLong(durCol) else 0L
                    val size = if (sizeCol != -1) it.getLong(sizeCol) else 0L
                    val res = if (resCol != -1) it.getString(resCol) ?: "" else ""
                    val date = if (dateCol != -1) it.getLong(dateCol) else 0L
                    val folder = if (bucketCol != -1) it.getString(bucketCol) ?: "Videos" else "Videos"

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val finalTitle = title.ifEmpty { displayName.substringBeforeLast(".") }.ifEmpty { "Video $id" }

                    videoList.add(
                        LocalVideoItem(
                            id = id,
                            title = finalTitle,
                            displayName = displayName.ifEmpty { "$finalTitle.mp4" },
                            filePath = path.ifEmpty { contentUri },
                            contentUri = contentUri,
                            durationMs = duration,
                            sizeBytes = size,
                            resolution = res,
                            folderName = folder,
                            dateModified = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for videos", e)
        }

        // Also check app-specific downloads folder for offline videos
        try {
            val appMovies = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MukulOttDownloads")
            if (appMovies.exists()) {
                val downloadedFiles = appMovies.listFiles { f ->
                    val n = f.name.lowercase()
                    n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".3gp")
                }
                downloadedFiles?.forEach { file ->
                    if (videoList.none { it.filePath == file.absolutePath }) {
                        videoList.add(
                            0,
                            LocalVideoItem(
                                id = file.lastModified(),
                                title = file.nameWithoutExtension.replace("_", " "),
                                displayName = file.name,
                                filePath = file.absolutePath,
                                contentUri = Uri.fromFile(file).toString(),
                                durationMs = 0L,
                                sizeBytes = file.length(),
                                resolution = "HD",
                                folderName = "Mukul Downloads",
                                dateModified = file.lastModified() / 1000
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice checking app downloads folder: ${e.message}")
        }

        videoList
    }

    suspend fun getAllAudios(context: Context): List<LocalAudioItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<LocalAudioItem>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME
        )

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val displayCol = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val dataCol = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val durCol = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = it.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dateCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val bucketCol = it.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = if (titleCol != -1) it.getString(titleCol) ?: "" else ""
                    val displayName = if (displayCol != -1) it.getString(displayCol) ?: "" else ""
                    val artist = if (artistCol != -1) it.getString(artistCol) ?: "<unknown>" else "<unknown>"
                    val album = if (albumCol != -1) it.getString(albumCol) ?: "" else ""
                    val path = if (dataCol != -1) it.getString(dataCol) ?: "" else ""
                    val duration = if (durCol != -1) it.getLong(durCol) else 0L
                    val size = if (sizeCol != -1) it.getLong(sizeCol) else 0L
                    val date = if (dateCol != -1) it.getLong(dateCol) else 0L
                    val folder = if (bucketCol != -1) it.getString(bucketCol) ?: "Music" else "Music"

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val finalTitle = title.ifEmpty { displayName.substringBeforeLast(".") }.ifEmpty { "Audio $id" }
                    val finalArtist = if (artist.contains("unknown", ignoreCase = true) || artist.isBlank()) "শিল্পী অজ্ঞাত" else artist

                    audioList.add(
                        LocalAudioItem(
                            id = id,
                            title = finalTitle,
                            displayName = displayName.ifEmpty { "$finalTitle.mp3" },
                            artist = finalArtist,
                            album = album.ifEmpty { "অডিও অ্যালবাম" },
                            filePath = path.ifEmpty { contentUri },
                            contentUri = contentUri,
                            durationMs = duration,
                            sizeBytes = size,
                            folderName = folder,
                            dateModified = date
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for audios", e)
        }

        // Also check app downloads for downloaded MP3s
        try {
            val appMovies = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "MukulOttDownloads")
            if (appMovies.exists()) {
                val downloadedAudios = appMovies.listFiles { f ->
                    val n = f.name.lowercase()
                    n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".wav") || n.endsWith(".aac")
                }
                downloadedAudios?.forEach { file ->
                    if (audioList.none { it.filePath == file.absolutePath }) {
                        audioList.add(
                            0,
                            LocalAudioItem(
                                id = file.lastModified(),
                                title = file.nameWithoutExtension.replace("_", " "),
                                displayName = file.name,
                                artist = "মুকুল প্লাস ডাউনলোড",
                                album = "অফলাইন ডাউনলোড",
                                filePath = file.absolutePath,
                                contentUri = Uri.fromFile(file).toString(),
                                durationMs = 0L,
                                sizeBytes = file.length(),
                                folderName = "Mukul Downloads",
                                dateModified = file.lastModified() / 1000
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice checking app downloads folder for audio: ${e.message}")
        }

        audioList
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%d KB", bytes / 1024)
            bytes > 0 -> "$bytes B"
            else -> "0 MB"
        }
    }
}
