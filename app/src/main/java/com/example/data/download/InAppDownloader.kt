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

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

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
    private fun scanExistingFiles(context: Context) {
        val dir = getDownloadDirectory(context)
        val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".mp4") || f.name.endsWith(".mkv")) } ?: return
        val list = mutableListOf<DownloadTask>()
        for (f in files) {
            val name = f.nameWithoutExtension
            val task = DownloadTask(
                id = f.name,
                movieSlug = name,
                title = name.replace("_", " "),
                poster = "",
                quality = if (f.name.contains("1080")) "1080p" else if (f.name.contains("720")) "720p" else "480p",
                downloadUrl = "",
                status = DownloadStatus.COMPLETED,
                progress = 1.0f,
                downloadedBytes = f.length(),
                totalBytes = f.length(),
                speedText = "ডাউনলোড সম্পন্ন",
                filePath = f.absolutePath
            )
            list.add(task)
        }
        _completedDownloads.value = list
    }

    /**
     * Check if a movie slug was completed
     */
    fun getCompletedMovie(movieSlug: String): DownloadTask? {
        if (movieSlug.isEmpty()) return null
        return _completedDownloads.value.firstOrNull {
            it.movieSlug.equals(movieSlug, ignoreCase = true) ||
            it.movieSlug.contains(movieSlug, ignoreCase = true) ||
            it.id.contains(movieSlug, ignoreCase = true)
        }
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
        val taskId = "${movieSlug}_${quality.filter { it.isDigit() }.ifEmpty { "HD" }}"

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
            downloadUrl = downloadUrl,
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
        updateTask(task.copy(status = DownloadStatus.DOWNLOADING, progress = 0f))

        val downloadDir = getDownloadDirectory(context)
        val safeTitle = task.title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val fileName = "${safeTitle}_${task.quality.filter { it.isDigit() }.ifEmpty { "HD" }}.mp4"
        val targetFile = File(downloadDir, fileName)

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            val requestBuilder = Request.Builder()
                .url(task.downloadUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; MukulPlusApp/1.0)")
                .header("Accept", "*/*")

            if (task.downloadUrl.contains("dramalinkbd.tv") || task.downloadUrl.contains("mukul-ott")) {
                requestBuilder.header("Referer", "https://mukul-ott.ai.studio/")
            }

            val request = requestBuilder.build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                updateTask(
                    task.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = "সার্ভার এরর: HTTP ${response.code}"
                    )
                )
                return
            }

            val body = response.body
            if (body == null) {
                updateTask(
                    task.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = "ডাউনলোড ফাইল পাওয়া যায়নি"
                    )
                )
                return
            }

            val totalBytes = body.contentLength()
            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(32 * 1024)
            var downloadedBytes = 0L
            var read: Int

            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
                downloadedBytes += read
                bytesSinceLastUpdate += read

                val now = System.currentTimeMillis()
                if (now - lastUpdateTime >= 500) {
                    val durationSec = (now - lastUpdateTime) / 1000.0
                    val speedBytesPerSec = if (durationSec > 0) (bytesSinceLastUpdate / durationSec).toLong() else 0L
                    val speedText = formatSpeed(speedBytesPerSec)

                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f

                    updateTask(
                        task.copy(
                            status = DownloadStatus.DOWNLOADING,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            progress = progress,
                            speedText = speedText,
                            filePath = targetFile.absolutePath
                        )
                    )

                    lastUpdateTime = now
                    bytesSinceLastUpdate = 0L
                }
            }

            outputStream.flush()

            val completedTask = task.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = downloadedBytes,
                totalBytes = downloadedBytes,
                progress = 1.0f,
                speedText = "ডাউনলোড সম্পন্ন",
                filePath = targetFile.absolutePath
            )
            updateTask(completedTask)

            val currentCompleted = _completedDownloads.value.toMutableList()
            currentCompleted.removeAll { it.id == completedTask.id }
            currentCompleted.add(0, completedTask)
            _completedDownloads.value = currentCompleted

        } catch (e: CancellationException) {
            updateTask(task.copy(status = DownloadStatus.CANCELLED, speedText = "বাতিল করা হয়েছে"))
            if (targetFile.exists()) targetFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for ${task.title}", e)
            updateTask(
                task.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = e.message ?: "ডাউনলোড ব্যর্থ হয়েছে"
                )
            )
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (_: Exception) {}
            activeJobs.remove(task.id)
        }
    }

    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        val current = _tasks.value[taskId]
        if (current != null) {
            updateTask(current.copy(status = DownloadStatus.CANCELLED))
        }
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
