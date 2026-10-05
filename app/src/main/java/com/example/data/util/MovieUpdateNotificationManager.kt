package com.example.data.util

import android.content.Context
import android.util.Log
import com.example.data.api.ApiClient
import com.example.data.download.DownloadNotificationHelper
import com.example.data.repository.MukulOttRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MovieUpdateNotificationManager {
    private const val TAG = "MovieNotificationMgr"
    private const val PREFS_NAME = "mukul_movie_notifications_prefs"
    private const val KEY_SEEN_SLUGS = "notified_movie_slugs"
    private const val KEY_INITIAL_SEEDED = "initial_seeded"

    suspend fun checkForNewMovies(context: Context) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val seenSlugs = prefs.getStringSet(KEY_SEEN_SLUGS, emptySet())?.toMutableSet() ?: mutableSetOf()
            val isInitialSeeded = prefs.getBoolean(KEY_INITIAL_SEEDED, false)

            // 1. Fetch latest movies from Mukul OTT API
            val ottMovies = try {
                MukulOttRepository.getMovies(1)
            } catch (e: Exception) {
                Log.w(TAG, "MukulOtt getMovies error: ${e.message}")
                emptyList()
            }

            // 2. Fetch latest movies from CtgHall API
            val ctgResponse = try {
                ApiClient.fetchCtgMovies(page = 1, sort = "createdAt", sortOrder = "DESC")
            } catch (e: Exception) {
                Log.d(TAG, "CtgHall fetchCtgMovies: ${e.message}")
                null
            }

            val newlyAddedOtt = ottMovies.filter { it.slug !in seenSlugs && it.title.isNotBlank() }
            val ctgList = (ctgResponse?.data ?: emptyList()).filter { it.id > 0 }
            val newlyAddedCtg = ctgList.filter {
                val slug = "ctg_${it.id}"
                slug !in seenSlugs && it.title.isNotBlank()
            }

            if (!isInitialSeeded) {
                // First run: Seed existing movies into seen set so we don't spam notifications
                val allCurrentSlugs = ottMovies.map { it.slug } + ctgList.map { "ctg_${it.id}" }
                seenSlugs.addAll(allCurrentSlugs)

                // Pick the #1 newest movie to notify user with a rich notification with logo & play button
                val featureMovie = ottMovies.firstOrNull()
                if (featureMovie != null) {
                    val posterBitmap = DownloadNotificationHelper.loadPosterBitmap(context, featureMovie.poster)
                    DownloadNotificationHelper.showNewMovieNotification(
                        context = context,
                        title = featureMovie.title,
                        posterBitmap = posterBitmap,
                        quality = featureMovie.qualityTag,
                        slug = featureMovie.slug
                    )
                }

                prefs.edit()
                    .putStringSet(KEY_SEEN_SLUGS, seenSlugs)
                    .putBoolean(KEY_INITIAL_SEEDED, true)
                    .apply()
                return@withContext
            }

            // Subsequent checks: notify for newly detected movies (up to 3 items)
            var notificationCount = 0

            for (movie in newlyAddedOtt) {
                if (notificationCount >= 3) break
                val posterBitmap = DownloadNotificationHelper.loadPosterBitmap(context, movie.poster)
                DownloadNotificationHelper.showNewMovieNotification(
                    context = context,
                    title = movie.title,
                    posterBitmap = posterBitmap,
                    quality = movie.qualityTag,
                    slug = movie.slug
                )
                seenSlugs.add(movie.slug)
                notificationCount++
            }

            for (ctgMovie in newlyAddedCtg) {
                if (notificationCount >= 3) break
                val slug = "ctg_${ctgMovie.id}"
                val posterUrl = ctgMovie.getFullPosterUrl()
                val posterBitmap = DownloadNotificationHelper.loadPosterBitmap(context, posterUrl)
                DownloadNotificationHelper.showNewMovieNotification(
                    context = context,
                    title = ctgMovie.title,
                    posterBitmap = posterBitmap,
                    quality = "HD",
                    slug = slug
                )
                seenSlugs.add(slug)
                notificationCount++
            }

            prefs.edit().putStringSet(KEY_SEEN_SLUGS, seenSlugs).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for new movies across APIs: ${e.message}", e)
        }
    }
}
