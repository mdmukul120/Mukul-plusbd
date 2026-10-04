package com.example.data.download

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    PAUSED,
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
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedText: String = "",
    val filePath: String = "",
    val errorMessage: String = ""
) {
    val progressPercent: Int
        get() = (progress * 100).toInt().coerceIn(0, 100)
}

object InAppDownloader {
    private const val TAG = "InAppDownloader"
    private const val DOWNLOAD_DIR_NAME = "MukulOttDownloads"
    private const val PREFS_NAME = "mukul_downloads_prefs"
    private const val KEY_SAVED_DOWNLOADS = "saved_completed_downloads"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, okhttp3.Call>()

    // Tasks Map
    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadTask>> = _tasks.asStateFlow()

    // Completed downloads cache
    private val _completedDownloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val completedDownloads: StateFlow<List<DownloadTask>> = _completedDownloads.asStateFlow()

    /**
     * Optional initialization method called by screens
     */
    fun init(context: Context) {
        coroutineScope.launch {
            loadSavedDownloads(context)
            scanExistingFiles(context)
        }
    }

    /**
     * Get the dedicated In-App offline movies folder
     */
    fun getDownloadDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), DOWNLOAD_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Scan downloaded files on disk to populate completedDownloads
     */
    fun scanFiles(context: Context) {
        coroutineScope.launch {
            loadSavedDownloads(context)
            scanExistingFiles(context)
        }
    }

    private fun loadSavedDownloads(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_SAVED_DOWNLOADS, null) ?: return
            val jsonArr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<DownloadTask>()

            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.optJSONObject(i) ?: continue
                val filePath = obj.optString("filePath", "")
                val file = File(filePath)
                if (file.exists() && file.length() > 0) {
                    list.add(
                        DownloadTask(
                            id = obj.optString("id", file.name),
                            movieSlug = obj.optString("movieSlug", ""),
                            title = obj.optString("title", file.nameWithoutExtension),
                            poster = obj.optString("poster", ""),
                            quality = obj.optString("quality", "HD"),
                            downloadUrl = obj.optString("downloadUrl", ""),
                            status = DownloadStatus.COMPLETED,
                            progress = 1.0f,
                            downloadedBytes = file.length(),
                            totalBytes = file.length(),
                            speedText = "ডাউনলোড সম্পন্ন",
                            filePath = file.absolutePath
                        )
                    )
                }
            }
            _completedDownloads.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load saved downloads from prefs", e)
        }
    }

    private fun persistCompletedDownloads(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonArr = org.json.JSONArray()
            for (task in _completedDownloads.value) {
                if (task.status != DownloadStatus.COMPLETED) continue
                val file = File(task.filePath)
                if (!file.exists() || file.length() < 50 * 1024) continue
                val obj = org.json.JSONObject().apply {
                    put("id", task.id)
                    put("movieSlug", task.movieSlug)
                    put("title", task.title)
                    put("poster", task.poster)
                    put("quality", task.quality)
                    put("downloadUrl", task.downloadUrl)
                    put("filePath", task.filePath)
                    put("totalBytes", task.totalBytes)
                }
                jsonArr.put(obj)
            }
            prefs.edit().putString(KEY_SAVED_DOWNLOADS, jsonArr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist completed downloads", e)
        }
    }

    private fun scanExistingFiles(context: Context) {
        val downloadDir = getDownloadDirectory(context)
        if (!downloadDir.exists()) return

        val existingMap = _completedDownloads.value.associateBy { it.filePath }.toMutableMap()
        val currentActiveTasks = _tasks.value
        val list = mutableListOf<DownloadTask>()

        val files = downloadDir.listFiles { f ->
            if (!f.isFile) return@listFiles false
            val n = f.name.lowercase()
            if (n.endsWith(".downloading") || n.endsWith(".tmp")) return@listFiles false
            (n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".m4a") || n.endsWith(".mp3")) && f.length() > 50 * 1024
        } ?: return

        for (f in files) {
            // If this file is currently being downloaded, do not add it as completed!
            val isDownloadingNow = currentActiveTasks.values.any {
                (it.filePath == f.absolutePath || it.id.contains(f.nameWithoutExtension)) &&
                (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
            }
            if (isDownloadingNow) continue

            val existing = existingMap[f.absolutePath]
            if (existing != null && existing.status == DownloadStatus.COMPLETED) {
                list.add(existing)
            }
        }
        _completedDownloads.value = list.distinctBy { it.filePath }
        persistCompletedDownloads(context)
    }

    /**
     * Check if a movie slug was completed
     */
    fun getCompletedMovie(movieSlug: String): DownloadTask? {
        if (movieSlug.isBlank()) return null
        return _completedDownloads.value.firstOrNull {
            it.status == DownloadStatus.COMPLETED &&
            (it.movieSlug.equals(movieSlug, ignoreCase = true) || it.id.equals(movieSlug, ignoreCase = true)) &&
            File(it.filePath).let { f -> f.exists() && f.length() > 100 * 1024 }
        }
    }

    /**
     * Helper to download a Music track directly inside the application without browser
     */
    fun downloadMusicTrack(context: Context, track: com.example.data.model.MusicTrack): String {
        val dlUrl = if (track.downloadUrl.isNotEmpty()) track.downloadUrl else track.streamUrl
        val slug = "music_${if (track.id.isNotEmpty()) track.id else System.currentTimeMillis().toString()}"
        val cleanName = track.name.ifBlank { "গান" }
        val displayTitle = if (track.artistNames.isNotBlank() && !cleanName.contains(track.artistNames)) {
            "$cleanName - ${track.artistNames}"
        } else {
            cleanName
        }
        return startDownload(
            context = context,
            movieSlug = slug,
            title = displayTitle,
            poster = track.imageUrl,
            quality = "320kbps MP3",
            downloadUrl = dlUrl
        )
    }

    /**
     * Start an in-app download
     */
    fun startDownload(
        context: Context,
        movieSlug: String,
        title: String,
        poster: String,
        quality: String,
        downloadUrl: String
    ): String {
        // Resolve relative URLs to absolute OTT host
        val cleanUrl = when {
            downloadUrl.startsWith("/") -> "https://mukul-ott.ai.studio$downloadUrl"
            downloadUrl.startsWith("http") -> downloadUrl
            else -> downloadUrl
        }

        val taskId = "${movieSlug}_${quality.filter { it.isDigit() }.ifEmpty { if (quality.contains("mp3", ignoreCase = true)) "mp3" else "HD" }}"

        val existing = _tasks.value[taskId]
        if (existing != null && existing.status == DownloadStatus.DOWNLOADING) {
            return taskId
        }

        val task = DownloadTask(
            id = taskId,
            movieSlug = movieSlug,
            title = title,
            poster = poster,
            quality = quality,
            downloadUrl = cleanUrl,
            status = DownloadStatus.QUEUED
        )

        updateTask(task)

        val job = coroutineScope.launch {
            runDownload(context, task)
        }
        activeJobs[taskId] = job

        return taskId
    }

    private suspend fun runDownload(context: Context, task: DownloadTask) {
        val initialTask = task.copy(status = DownloadStatus.DOWNLOADING, progress = 0f)
        updateTask(initialTask)

        // Load thumbnail bitmap for notification
        val posterBitmap = DownloadNotificationHelper.loadPosterBitmap(context, task.poster)
        DownloadNotificationHelper.showDownloadProgress(context, initialTask, posterBitmap)

        val downloadDir = getDownloadDirectory(context)
        val safeId = task.id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val safeTitle = task.title.replace("[^a-zA-Z0-9_\u0980-\u09FF\\s-]".toRegex(), "").trim().replace("\\s+".toRegex(), "_").take(40)
        val isAudio = task.quality.contains("mp3", ignoreCase = true) ||
                task.movieSlug.startsWith("music_") ||
                task.id.startsWith("music_") ||
                task.downloadUrl.contains(".mp3", ignoreCase = true) ||
                task.downloadUrl.contains("mime=audio", ignoreCase = true)
        val ext = if (isAudio) ".mp3" else ".mp4"
        val fileName = if (safeTitle.isNotBlank()) "${safeTitle}_${safeId}$ext" else if (safeId.isNotBlank()) "${safeId}$ext" else "mukul_${if (isAudio) "audio" else "vid"}_${System.currentTimeMillis()}$ext"
        val targetFile = File(downloadDir, fileName)
        val tempFile = File(downloadDir, "${fileName}.downloading")

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        var call: okhttp3.Call? = null

        try {
            val requestBuilder = Request.Builder()
                .url(task.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                .header("Accept", "*/*")

            if (task.downloadUrl.contains("mukul-ott") || task.downloadUrl.contains("stream-proxy") || task.downloadUrl.contains("fsldownload")) {
                requestBuilder.header("Referer", "https://mukul-ott.ai.studio/")
            }

            // Auto wrap fsldownload links with stream-proxy if not already wrapped
            var effectiveUrl = task.downloadUrl
            if (effectiveUrl.contains("fsldownload.com") && !effectiveUrl.contains("stream-proxy")) {
                effectiveUrl = "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(effectiveUrl, "UTF-8")
            } else if (effectiveUrl.startsWith("/")) {
                effectiveUrl = "https://mukul-ott.ai.studio$effectiveUrl"
            }
            requestBuilder.url(effectiveUrl)

            val request = requestBuilder.build()
            call = client.newCall(request)
            activeCalls[task.id] = call

            val response = call.execute()

            if (!response.isSuccessful) {
                if (call.isCanceled() || _tasks.value[task.id]?.status == DownloadStatus.CANCELLED) {
                    updateTask(task.copy(status = DownloadStatus.CANCELLED, speedText = "বাতিল করা হয়েছে"))
                    DownloadNotificationHelper.cancelNotification(context, task.id)
                    if (tempFile.exists()) tempFile.delete()
                    if (targetFile.exists()) targetFile.delete()
                    return
                }
                val err = "সার্ভার এরর: HTTP ${response.code}"
                updateTask(task.copy(status = DownloadStatus.FAILED, errorMessage = err))
                DownloadNotificationHelper.showDownloadFailed(context, task, err)
                return
            }

            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.contains("text/html", ignoreCase = true) && !task.downloadUrl.contains("stream-proxy")) {
                val err = "মিডিয়া ফাইল পাওয়া যায়নি (ওয়েব রিডাইরেক্ট)"
                updateTask(task.copy(status = DownloadStatus.FAILED, errorMessage = err))
                DownloadNotificationHelper.showDownloadFailed(context, task, err)
                return
            }

            val body = response.body
            if (body == null) {
                val err = "ডাউনলোড ফাইল পাওয়া যায়নি"
                updateTask(task.copy(status = DownloadStatus.FAILED, errorMessage = err))
                DownloadNotificationHelper.showDownloadFailed(context, task, err)
                return
            }

            val totalBytes = body.contentLength()
            inputStream = body.byteStream()
            outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(32 * 1024)
            var downloadedBytes = 0L
            var read: Int

            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            while (inputStream.read(buffer).also { read = it } != -1) {
                // Check if user cancelled download mid-stream
                if (!coroutineScope.coroutineContext.isActive || call.isCanceled() || _tasks.value[task.id]?.status == DownloadStatus.CANCELLED) {
                    try { tempFile.delete() } catch (_: Exception) {}
                    try { targetFile.delete() } catch (_: Exception) {}
                    throw CancellationException("Cancelled by user")
                }

                outputStream.write(buffer, 0, read)
                downloadedBytes += read
                bytesSinceLastUpdate += read

                val now = System.currentTimeMillis()
                if (now - lastUpdateTime >= 500) {
                    val durationSec = (now - lastUpdateTime) / 1000.0
                    val speedBytesPerSec = if (durationSec > 0) (bytesSinceLastUpdate / durationSec).toLong() else 0L
                    val speedText = formatSpeed(speedBytesPerSec)

                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

                    val progressTask = task.copy(
                        status = DownloadStatus.DOWNLOADING,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        progress = progress,
                        speedText = speedText,
                        filePath = targetFile.absolutePath
                    )
                    updateTask(progressTask)

                    // Real-time notification with image and progress bar
                    DownloadNotificationHelper.showDownloadProgress(context, progressTask, posterBitmap)

                    lastUpdateTime = now
                    bytesSinceLastUpdate = 0L
                }
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null

            // Rename temp file to target file atomically
            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            val completedTask = task.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = downloadedBytes,
                totalBytes = downloadedBytes,
                progress = 1.0f,
                speedText = "ডাউনলোড সম্পন্ন",
                filePath = targetFile.absolutePath
            )
            updateTask(completedTask)

            // Show completed notification with image and play button
            DownloadNotificationHelper.showDownloadCompleted(context, completedTask, posterBitmap)

            val currentCompleted = _completedDownloads.value.toMutableList()
            currentCompleted.removeAll { it.id == completedTask.id }
            currentCompleted.add(0, completedTask)
            _completedDownloads.value = currentCompleted
            persistCompletedDownloads(context)

            try {
                android.media.MediaScannerConnection.scanFile(
                    context.applicationContext,
                    arrayOf(targetFile.absolutePath),
                    null,
                    null
                )
            } catch (_: Exception) {}

        } catch (e: CancellationException) {
            updateTask(task.copy(status = DownloadStatus.CANCELLED, speedText = "বাতিল করা হয়েছে"))
            DownloadNotificationHelper.cancelNotification(context, task.id)
            if (targetFile.exists()) targetFile.delete()
        } catch (e: Exception) {
            val isCancelled = call?.isCanceled() == true ||
                _tasks.value[task.id]?.status == DownloadStatus.CANCELLED ||
                e.message?.contains("cancel", ignoreCase = true) == true ||
                e.message?.contains("closed", ignoreCase = true) == true

            if (isCancelled) {
                updateTask(task.copy(status = DownloadStatus.CANCELLED, speedText = "বাতিল করা হয়েছে"))
                DownloadNotificationHelper.cancelNotification(context, task.id)
                if (targetFile.exists()) targetFile.delete()
                return
            }

            Log.e(TAG, "Download failed for ${task.title}", e)
            val err = e.message ?: "ডাউনলোড ব্যর্থ হয়েছে"
            updateTask(
                task.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = err
                )
            )
            DownloadNotificationHelper.showDownloadFailed(context, task, err)
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (_: Exception) {}
            activeCalls.remove(task.id)
            activeJobs.remove(task.id)
        }
    }

    fun cancelDownload(taskId: String) {
        val current = _tasks.value[taskId]
        if (current != null) {
            updateTask(current.copy(status = DownloadStatus.CANCELLED, speedText = "বাতিল করা হয়েছে"))
            if (current.filePath.isNotEmpty()) {
                try {
                    val f = File(current.filePath)
                    if (f.exists()) f.delete()
                } catch (_: Exception) {}
            }
        }
        activeCalls[taskId]?.cancel()
        activeCalls.remove(taskId)
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
    }

    fun deleteDownloadedMovie(context: Context, taskId: String) {
        val task = _tasks.value[taskId] ?: _completedDownloads.value.firstOrNull { it.id == taskId }
        if (task != null && task.filePath.isNotEmpty()) {
            val file = File(task.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
        val currentTasks = _tasks.value.toMutableMap()
        currentTasks.remove(taskId)
        _tasks.value = currentTasks

        val currentCompleted = _completedDownloads.value.toMutableList()
        currentCompleted.removeAll { it.id == taskId }
        _completedDownloads.value = currentCompleted
        persistCompletedDownloads(context)
    }

    private fun updateTask(task: DownloadTask) {
        val current = _tasks.value.toMutableMap()
        current[task.id] = task
        _tasks.value = current
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec.toDouble() / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%d KB/s", bytesPerSec / 1024)
            else -> "$bytesPerSec B/s"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}
