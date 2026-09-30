package com.example.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object DownloadNotificationHelper {
    private const val TAG = "DownloadNotification"

    const val CHANNEL_DOWNLOADS = "mukul_downloads_channel"
    const val CHANNEL_NEW_MOVIES = "mukul_new_movies_channel"

    const val ACTION_PLAY_MOVIE = "com.example.ACTION_PLAY_MOVIE"
    const val ACTION_CANCEL_DOWNLOAD = "com.example.ACTION_CANCEL_DOWNLOAD"
    const val EXTRA_MOVIE_SLUG = "extra_movie_slug"
    const val EXTRA_MOVIE_TITLE = "extra_movie_title"
    const val EXTRA_TASK_ID = "extra_task_id"

    // Bitmap in-memory cache to prevent downloading thumbnail repeatedly while progress updates
    private val thumbnailCache = ConcurrentHashMap<String, Bitmap>()

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Downloads Channel (Low importance so progress updates don't make sounds)
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Mukul Plus Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ভিডিও ডাউনলোড প্রগ্রেস এবং স্ট্যাটাস"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(downloadChannel)

            // 2. New Movies Channel (High importance for alerts, sound, and notification heads-up)
            val newMoviesChannel = NotificationChannel(
                CHANNEL_NEW_MOVIES,
                "নতুন মুভি ও সিরিজ আপডেট",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "সকল API এর নতুন মুভি ও রিলিজ নোটিফিকেশন"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(newMoviesChannel)
        }
    }

    suspend fun loadPosterBitmap(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        thumbnailCache[url]?.let { return@withContext it }

        try {
            // First attempt: Coil ImageLoader
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // required for Notification LargeIcon
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    thumbnailCache[url] = bitmap
                    return@withContext bitmap
                }
            }
        } catch (_: Exception) {}

        try {
            // Fallback attempt: direct URL stream
            val stream = URL(url).openStream()
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()
            if (bitmap != null) {
                thumbnailCache[url] = bitmap
                return@withContext bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load poster bitmap: ${e.message}")
        }
        return@withContext null
    }

    private fun getNotificationId(key: String): Int {
        return kotlin.math.abs(key.hashCode()) % 100000 + 1000
    }

    fun showDownloadProgress(
        context: Context,
        task: DownloadTask,
        bitmap: Bitmap?
    ) {
        initChannels(context)

        val notificationId = getNotificationId(task.id)

        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
            putExtra(EXTRA_TASK_ID, task.id)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speed = if (task.speedText.isNotEmpty()) task.speedText else "ডাউনলোড চলছে..."
        val sizeText = "${InAppDownloader.formatFileSize(task.downloadedBytes)} / ${InAppDownloader.formatFileSize(task.totalBytes)}"

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.title)
            .setContentText("${task.quality} • ${task.progressPercent}% • $speed ($sizeText)")
            .setSubText("Mukul Plus")
            .setProgress(100, task.progressPercent, task.totalBytes <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "বাতিল", cancelPendingIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission not granted: ${e.message}")
        }
    }

    fun showDownloadCompleted(
        context: Context,
        task: DownloadTask,
        bitmap: Bitmap?
    ) {
        initChannels(context)

        val notificationId = getNotificationId(task.id)

        val playIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_PLAY_MOVIE
            putExtra(EXTRA_MOVIE_SLUG, task.movieSlug)
            putExtra(EXTRA_MOVIE_TITLE, task.title)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val playPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 2,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("✅ ডাউনলোড সম্পন্ন: ${task.title}")
            .setContentText("অফলাইনে দেখতে ট্যাপ করুন (${task.quality})")
            .setSubText("Mukul Plus")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(playPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "▶ এখনই দেখুন", playPendingIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .bigLargeIcon(null as Bitmap?)
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission error: ${e.message}")
        }
    }

    fun showDownloadFailed(
        context: Context,
        task: DownloadTask,
        reason: String
    ) {
        initChannels(context)

        val notificationId = getNotificationId(task.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("❌ ডাউনলোড ব্যর্থ: ${task.title}")
            .setContentText(reason.ifEmpty { "নেটওয়ার্ক বা সার্ভার ত্রুটি" })
            .setSubText("Mukul Plus")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
    }

    fun cancelNotification(context: Context, taskId: String) {
        val notificationId = getNotificationId(taskId)
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: Exception) {}
    }

    /**
     * সকল API এ নতুন কোনো মুভি যোগ হলে নোটিফিকেশন এ ছবি, নাম এবং Mukul plus এর লোগো সহ প্লে বাটন প্রদর্শন
     */
    fun showNewMovieNotification(
        context: Context,
        title: String,
        posterBitmap: Bitmap?,
        quality: String,
        slug: String
    ) {
        initChannels(context)

        val notificationId = getNotificationId("new_movie_$slug")

        val playIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_PLAY_MOVIE
            putExtra(EXTRA_MOVIE_SLUG, slug)
            putExtra(EXTRA_MOVIE_TITLE, title)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val playPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mukul Plus App Logo
        val appLogo = try {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val qualityLabel = if (quality.isNotEmpty()) "[$quality]" else "[HD]"

        val builder = NotificationCompat.Builder(context, CHANNEL_NEW_MOVIES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎬 নতুন মুভি যোগ হয়েছে: $title")
            .setContentText("$qualityLabel Mukul Plus এ সম্পূর্ণ ফ্রিতে এখনই উপভোগ করুন!")
            .setSubText("Mukul Plus OTT")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(playPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "▶ প্লে করুন (Play Now)", playPendingIntent)

        if (posterBitmap != null) {
            builder.setLargeIcon(appLogo ?: posterBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(posterBitmap)
                    .bigLargeIcon(appLogo)
                    .setBigContentTitle("🎬 $title $qualityLabel")
                    .setSummaryText("Mukul Plus এ নতুন রিলিজ! সরাসরি দেখতে ট্যাপ করুন")
            )
        } else if (appLogo != null) {
            builder.setLargeIcon(appLogo)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification permission error: ${e.message}")
        }
    }
}
