package com.example.data.api

import android.util.Log
import com.example.data.model.BanglaMovieDetail
import com.example.data.model.ScrapedVideoLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object BanglaMovieLinkScraper {
    private const val TAG = "BanglaMovieScraper"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val PIXELDRAIN_PATTERN = Pattern.compile("pixeldrain\\.(?:dev|com)/u/([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE)
    private val PIXELDRAIN_API_PATTERN = Pattern.compile("pixeldrain\\.(?:dev|com)/api/file/([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE)

    /**
     * Resolve and scrape all streamable & downloadable links for a movie or episode
     */
    suspend fun resolveMediaLinks(
        detail: BanglaMovieDetail,
        bundleIndex: Int = 0
    ): List<ScrapedVideoLink> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScrapedVideoLink>()

        // 1. Direct qualities from movie detail
        for (q in detail.qualities) {
            if (q.downloadUrl.isNotBlank()) {
                val scraped = scrapeSingleUrl(
                    rawUrl = q.downloadUrl,
                    serverName = "Direct Server",
                    quality = q.label,
                    fileSize = q.size
                )
                results.add(scraped)
            }
        }

        // 2. Download Servers
        for (server in detail.downloadServers) {
            // Check direct server qualities
            for (q in server.qualities) {
                if (q.downloadUrl.isNotBlank()) {
                    val scraped = scrapeSingleUrl(
                        rawUrl = q.downloadUrl,
                        serverName = server.serverName,
                        quality = q.label,
                        fileSize = q.size
                    )
                    results.add(scraped)
                }
            }

            // Check episode bundles (if series)
            if (server.episodeBundles.isNotEmpty()) {
                val bundle = server.episodeBundles.getOrNull(bundleIndex) ?: server.episodeBundles.first()
                for (q in bundle.qualities) {
                    if (q.downloadUrl.isNotBlank()) {
                        val scraped = scrapeSingleUrl(
                            rawUrl = q.downloadUrl,
                            serverName = "${server.serverName} (${bundle.episodeRange})",
                            quality = q.label,
                            fileSize = q.size
                        )
                        results.add(scraped)
                    }
                }
            }
        }

        // Deduplicate by playUrl
        val uniqueResults = results.distinctBy { it.playUrl }
        return@withContext uniqueResults
    }

    /**
     * Scrape and transform a single URL into a direct stream/download candidate
     */
    suspend fun scrapeSingleUrl(
        rawUrl: String,
        serverName: String = "Server",
        quality: String = "HD",
        fileSize: String = ""
    ): ScrapedVideoLink = withContext(Dispatchers.IO) {
        val cleanUrl = rawUrl.trim()
        val domain = extractDomain(cleanUrl)

        // 1. Check if already a direct video file
        val lower = cleanUrl.lowercase()
        if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".m3u8") || lower.endsWith(".webm")) {
            return@withContext ScrapedVideoLink(
                playUrl = cleanUrl,
                downloadUrl = cleanUrl,
                serverName = serverName,
                quality = quality,
                fileSize = fileSize,
                isDirectVideo = true,
                requiresBrowser = false,
                sourceDomain = domain
            )
        }

        // 2. Check for Pixeldrain URLs
        val pixMatch = PIXELDRAIN_PATTERN.matcher(cleanUrl)
        if (pixMatch.find()) {
            val fileId = pixMatch.group(1) ?: ""
            val directApiUrl = "https://pixeldrain.dev/api/file/$fileId"
            return@withContext ScrapedVideoLink(
                playUrl = directApiUrl,
                downloadUrl = directApiUrl,
                serverName = if (serverName.contains("Server", ignoreCase = true)) "Pixeldrain Fast" else serverName,
                quality = quality,
                fileSize = fileSize,
                isDirectVideo = true,
                requiresBrowser = false,
                sourceDomain = "pixeldrain.dev"
            )
        }

        val pixApiMatch = PIXELDRAIN_API_PATTERN.matcher(cleanUrl)
        if (pixApiMatch.find()) {
            return@withContext ScrapedVideoLink(
                playUrl = cleanUrl,
                downloadUrl = cleanUrl,
                serverName = serverName,
                quality = quality,
                fileSize = fileSize,
                isDirectVideo = true,
                requiresBrowser = false,
                sourceDomain = "pixeldrain.dev"
            )
        }

        // 3. Attempt scraping the target page for video tags or nested links
        try {
            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Referer", cleanUrl)
                .build()

            val res = httpClient.newCall(req).execute()
            val html = res.body?.string() ?: ""

            // Check if HTML contains a pixeldrain link
            val nestedPixMatch = PIXELDRAIN_PATTERN.matcher(html)
            if (nestedPixMatch.find()) {
                val fileId = nestedPixMatch.group(1) ?: ""
                val directApiUrl = "https://pixeldrain.dev/api/file/$fileId"
                return@withContext ScrapedVideoLink(
                    playUrl = directApiUrl,
                    downloadUrl = directApiUrl,
                    serverName = "$serverName (Direct)",
                    quality = quality,
                    fileSize = fileSize,
                    isDirectVideo = true,
                    requiresBrowser = false,
                    sourceDomain = "pixeldrain.dev"
                )
            }

            // Check for direct video tag: <video src="..." or <source src="..."
            val videoTagPattern = Pattern.compile("<(?:video|source)[^>]*src=[\"']([^\"']+\\.(?:mp4|mkv|m3u8|webm)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
            val vMatch = videoTagPattern.matcher(html)
            if (vMatch.find()) {
                val vSrc = vMatch.group(1) ?: ""
                if (vSrc.isNotBlank()) {
                    return@withContext ScrapedVideoLink(
                        playUrl = vSrc,
                        downloadUrl = vSrc,
                        serverName = "$serverName (Direct)",
                        quality = quality,
                        fileSize = fileSize,
                        isDirectVideo = true,
                        requiresBrowser = false,
                        sourceDomain = domain
                    )
                }
            }

            // Check for download buttons in HTML pointing to fast cloud storage (e.g. gdxfiles sublinks, r2, gofile)
            val sublinkPattern = Pattern.compile("href=[\"'](https?://[^\"']*(?:pixeldrain|gofile|r2|fastdrive)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
            val sMatch = sublinkPattern.matcher(html)
            if (sMatch.find()) {
                val subUrl = sMatch.group(1) ?: ""
                val subPixMatch = PIXELDRAIN_PATTERN.matcher(subUrl)
                if (subPixMatch.find()) {
                    val fileId = subPixMatch.group(1) ?: ""
                    val directApiUrl = "https://pixeldrain.dev/api/file/$fileId"
                    return@withContext ScrapedVideoLink(
                        playUrl = directApiUrl,
                        downloadUrl = directApiUrl,
                        serverName = "$serverName (Pixeldrain)",
                        quality = quality,
                        fileSize = fileSize,
                        isDirectVideo = true,
                        requiresBrowser = false,
                        sourceDomain = "pixeldrain.dev"
                    )
                }
                return@withContext ScrapedVideoLink(
                    playUrl = subUrl,
                    downloadUrl = subUrl,
                    serverName = "$serverName (Cloud)",
                    quality = quality,
                    fileSize = fileSize,
                    isDirectVideo = true,
                    requiresBrowser = false,
                    sourceDomain = extractDomain(subUrl)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scrape failed for $cleanUrl: ${e.message}")
        }

        // Fallback: return the original URL with browser/external support enabled
        ScrapedVideoLink(
            playUrl = cleanUrl,
            downloadUrl = cleanUrl,
            serverName = serverName,
            quality = quality,
            fileSize = fileSize,
            isDirectVideo = false,
            requiresBrowser = true,
            sourceDomain = domain
        )
    }

    private fun extractDomain(url: String): String {
        return try {
            val uri = URI(url)
            uri.host ?: url
        } catch (_: Exception) {
            "web"
        }
    }
}
