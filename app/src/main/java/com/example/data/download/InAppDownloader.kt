package com.example.data.download

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val id: String,
    val movieSlug: String,
    val title: String,
    val poster: String,
    val quality: String,
    val downloadUrl: String,
    val fileName: String,
    val filePath: String = "",
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val progressPercent: Int = 0,
    val speedText: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null
)

object InAppDownloader {
    private const val TAG = "InAppDownloader"
    private const val PREFS_NAME = "mukul_downloads_prefs"
    private const val KEY_COMPLETED = "completed_downloads"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadTask>> = _tasks.asStateFlow()

    private val _completedList = MutableStateFlow<List<DownloadTask>>(emptyList())
    val completedList: StateFlow<List<DownloadTask>> = _completedList.asStateFlow()
    val completedDownloads: StateFlow<List<DownloadTask>> get() = completedList

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadCompletedFromPrefs(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadCompletedFromPrefs(context: Context) {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_COMPLETED, null) ?: return
        try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadTask>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val filePath = obj.optString("filePath")
                val file = File(filePath)
                if (file.exists() && file.length() > 0) {
                    list.add(
                        DownloadTask(
                            id = obj.optString("id"),
                            movieSlug = obj.optString("movieSlug"),
                            title = obj.optString("title"),
                            poster = obj.optString("poster"),
                            quality = obj.optString("quality"),
                            downloadUrl = obj.optString("downloadUrl"),
                            fileName = obj.optString("fileName"),
                            filePath = filePath,
                            totalBytes = obj.optLong("totalBytes", file.length()),
                            downloadedBytes = file.length(),
                            progressPercent = 100,
                            status = DownloadStatus.COMPLETED
                        )
                    )
                }
            }
            _completedList.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading completed downloads", e)
        }
    }

    private fun saveCompletedToPrefs(context: Context) {
        val prefs = getPrefs(context)
        val array = JSONArray()
        _completedList.value.forEach { task ->
            val obj = JSONObject().apply {
                put("id", task.id)
                put("movieSlug", task.movieSlug)
                put("title", task.title)
                put("poster", task.poster)
                put("quality", task.quality)
                put("downloadUrl", task.downloadUrl)
                put("fileName", task.fileName)
                put("filePath", task.filePath)
                put("totalBytes", task.totalBytes)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_COMPLETED, array.toString()).apply()
    }

    fun startDownload(
        context: Context,
        movieSlug: String,
        title: String,
        poster: String,
        quality: String,
        downloadUrl: String
    ): String {
        init(context)

        val id = "${movieSlug}_${quality.filter { it.isLetterOrDigit() }}"

        // If already completed and file exists, don't duplicate
        val existingCompleted = _completedList.value.firstOrNull { it.id == id }
        if (existingCompleted != null && File(existingCompleted.filePath).exists()) {
            return id
        }

        // If currently downloading
        val currentTask = _tasks.value[id]
        if (currentTask != null && currentTask.status == DownloadStatus.DOWNLOADING) {
            return id
        }

        val sanitized = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_')
        val cleanTitle = if (sanitized.isNotEmpty()) sanitized.take(40) else (movieSlug.ifEmpty { "video_${System.currentTimeMillis()}" }).take(40)
        val cleanQuality = quality.replace(Regex("[^a-zA-Z0-9]"), "").ifEmpty { "HD" }
        val ext = when {
            quality.lowercase().contains("mp3") || downloadUrl.contains(".mp3") -> "mp3"
            downloadUrl.contains(".mkv") -> "mkv"
            else -> "mp4"
        }
        val fileName = "${cleanTitle}_${cleanQuality}.${ext}"

        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        if (!moviesDir.exists()) {
            moviesDir.mkdirs()
        }
        val targetFile = File(moviesDir, fileName)

        val task = DownloadTask(
            id = id,
            movieSlug = movieSlug,
            title = title,
            poster = poster,
            quality = quality,
            downloadUrl = downloadUrl,
            fileName = fileName,
            filePath = targetFile.absolutePath,
            status = DownloadStatus.DOWNLOADING
        )

        updateTask(task)

        val job = scope.launch {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "MukulPlusApp/1.0 (Android; Downloader)")
                    .header("Accept", "*/*")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()
                inputStream = body.byteStream()
                outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastUpdateTime = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) {
                        targetFile.delete()
                        updateTask(task.copy(status = DownloadStatus.CANCELLED))
                        return@launch
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime >= 300) {
                        val durationSec = (now - lastUpdateTime) / 1000.0
                        val speedBytesPerSec = if (durationSec > 0) (bytesSinceLastUpdate / durationSec).toLong() else 0L
                        val speedStr = formatSpeed(speedBytesPerSec)

                        val percent = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 99)
                        } else {
                            0
                        }

                        updateTask(
                            task.copy(
                                totalBytes = contentLength,
                                downloadedBytes = totalBytesRead,
                                progressPercent = percent,
                                speedText = speedStr,
                                status = DownloadStatus.DOWNLOADING
                            )
                        )

                        lastUpdateTime = now
                        bytesSinceLastUpdate = 0L
                    }
                }

                outputStream.flush()

                // Finished successfully!
                val completedTask = task.copy(
                    totalBytes = if (contentLength > 0) contentLength else totalBytesRead,
                    downloadedBytes = totalBytesRead,
                    progressPercent = 100,
                    speedText = "",
                    status = DownloadStatus.COMPLETED
                )

                updateTask(completedTask)

                withContext(Dispatchers.Main) {
                    val updated = _completedList.value.filterNot { it.id == id } + completedTask
                    _completedList.value = updated
                    saveCompletedToPrefs(context)
                }

            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Download error for $title", e)
                    updateTask(
                        task.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = e.localizedMessage ?: "Download failed"
                        )
                    )
                }
            } finally {
                try {
                    inputStream?.close()
                } catch (_: Exception) {}
                try {
                    outputStream?.close()
                } catch (_: Exception) {}
                activeJobs.remove(id)
            }
        }

        activeJobs[id] = job
        return id
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        val current = _tasks.value[id]
        if (current != null) {
            try {
                if (current.filePath.isNotEmpty()) {
                    File(current.filePath).delete()
                }
            } catch (_: Exception) {}
            updateTask(current.copy(status = DownloadStatus.CANCELLED))
        }
    }

    fun deleteDownloadedMovie(context: Context, id: String) {
        val task = _completedList.value.firstOrNull { it.id == id }
        if (task != null) {
            try {
                val file = File(task.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
            _completedList.value = _completedList.value.filterNot { it.id == id }
            saveCompletedToPrefs(context)
        }
        val taskMap = _tasks.value.toMutableMap()
        taskMap.remove(id)
        _tasks.value = taskMap
    }

    fun getTask(id: String): DownloadTask? {
        return _tasks.value[id] ?: _completedList.value.firstOrNull { it.id == id }
    }

    fun getCompletedMovie(slug: String): DownloadTask? {
        return _completedList.value.firstOrNull { it.movieSlug == slug && File(it.filePath).exists() }
    }

    private fun updateTask(task: DownloadTask) {
        val map = _tasks.value.toMutableMap()
        map[task.id] = task
        _tasks.value = map
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format("%.1f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return ""
        val kb = bytesPerSec / 1024.0
        return if (kb >= 1024) {
            String.format("%.1f MB/s", kb / 1024.0)
        } else {
            String.format("%.0f KB/s", kb)
        }
    }
}
