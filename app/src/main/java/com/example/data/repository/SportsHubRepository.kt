package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.SportsHubSource
import com.example.data.model.SportsMatch
import com.example.data.model.SportsMatchStatus
import com.example.data.model.SportsSourceKind
import com.example.data.model.SportsStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * SportsHubRepository — সম্পূর্ণ নিজস্ব, কনফিগ-চালিত স্পোর্টস হাব ডেটা লেয়ার।
 *
 * কীভাবে কাজ করে:
 *  1. অ্যাপের assets/sports_sources.json পড়ে (ডিফল্টভাবে খালি — কোনো সোর্স নেই)।
 *  2. ব্যবহারকারী Profile → Sports Hub Settings-এ নিজের JSON ফিডের URL বসালে
 *     সেটি SharedPreferences-এ সেভ হয় এবং সেটাই ব্যবহৃত হয়।
 *  3. প্রতিটি সোর্স আলাদা করে, টাইমআউট সহ, 병렬 ভাবে লোড হয় — একটো ব্যর্থ হলে
 *     বাকিগুলো কাজ করে (partial success)।
 *
 * কোনো তৃতীয় পক্ষের অ্যাপ, ব্র্যান্ড বা অনুমতিহীন স্ট্রিম এখানে নেই।
 */
class SportsHubRepository(private val context: Context) {

    companion object {
        private const val TAG = "SportsHubRepository"
        private const val PREFS = "mukul_sports_hub_prefs"
        private const val KEY_FEED_URL = "feed_url"
        private const val KEY_ENABLED = "hub_enabled"
        private const val ASSET_SOURCES = "sports_sources.json"

        // একটি ফিড লোডের সর্বোচ্চ সময় — UI কখনো ঝুলবে না
        private const val FETCH_TIMEOUT_MS = 12_000L
        private const val FRESH_TTL_MS = 5 * 60 * 1000L
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _matches = MutableStateFlow<List<SportsMatch>>(emptyList())
    val matches: StateFlow<List<SportsMatch>> = _matches.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errors = MutableStateFlow<List<String>>(emptyList())
    val errors: StateFlow<List<String>> = _errors.asStateFlow()

    private var lastFetchAt = 0L

    // ---------------------------------------------------------------- settings

    /** ব্যবহারকারীর দেওয়া ফিড URL (খালি হলে কোনো সোর্স নেই) */
    var feedUrl: String
        get() = prefs.getString(KEY_FEED_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_FEED_URL, value.trim()).apply()
            lastFetchAt = 0L
        }

    var isHubEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    fun hasConfiguredSource(): Boolean = feedUrl.isNotBlank() || loadBundledSources().isNotEmpty()

    // ------------------------------------------------------------------ sources

    /** assets থেকে ডিফল্ট সোর্স (রিলিজে খালি অ্যারে — ব্যবহারকারী নিজে ভরবে) */
    private fun loadBundledSources(): List<SportsHubSource> = try {
        val json = context.assets.open(ASSET_SOURCES).bufferedReader().use { it.readText() }
        parseSources(JSONArray(json))
    } catch (e: Exception) {
        Log.d(TAG, "No bundled sports sources: ${e.message}")
        emptyList()
    }

    private fun activeSources(): List<SportsHubSource> {
        val bundled = loadBundledSources()
        val userUrl = feedUrl
        val all = if (userUrl.isNotBlank()) {
            bundled + SportsHubSource(
                id = "user_feed",
                name = "আমার ফিড",
                kind = SportsSourceKind.JSON,
                url = userUrl
            )
        } else bundled
        return all.filter { it.enabled && it.url.isNotBlank() }
    }

    private fun parseSources(arr: JSONArray): List<SportsHubSource> {
        val out = mutableListOf<SportsHubSource>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("url", "").trim()
            if (url.isBlank()) continue
            val kind = when (o.optString("kind", "JSON").uppercase()) {
                "PLAYLIST" -> SportsSourceKind.PLAYLIST
                "EMBED" -> SportsSourceKind.EMBED
                else -> SportsSourceKind.JSON
            }
            out.add(
                SportsHubSource(
                    id = o.optString("id", "src_$i"),
                    name = o.optString("name", "সোর্স ${i + 1}"),
                    kind = kind,
                    url = url,
                    enabled = o.optBoolean("enabled", true),
                    notes = o.optString("notes", "")
                )
            )
        }
        return out
    }

    // ------------------------------------------------------------------- fetch

    /**
     * সব সোর্স (বা force=false হলে ৫ মিনিটের ক্যাশ) লোড করে।
     * একাধিক সোর্স 병렬 লোড হয়; ব্যর্থ সোর্স শুধু error লিস্টে যায়।
     */
    suspend fun refresh(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastFetchAt < FRESH_TTL_MS && _matches.value.isNotEmpty()) return@withContext

        val sources = activeSources()
        if (sources.isEmpty()) {
            _matches.value = emptyList()
            _errors.value = emptyList()
            _isLoading.value = false
            return@withContext
        }

        _isLoading.value = true
        val collected = mutableListOf<SportsMatch>()
        val failures = mutableListOf<String>()

        coroutineScope {
            val jobs = sources.map { source ->
                async {
                    val result = withTimeoutOrNull(FETCH_TIMEOUT_MS) { fetchSource(source) }
                    source to result
                }
            }
            jobs.forEach { job ->
                val (source, result) = job.await()
                if (result == null) {
                    failures.add("${source.name}: সময় শেষ / নেটওয়ার্ক ত্রুটি")
                } else {
                    collected.addAll(result)
                }
            }
        }

        // ডুপ্লিকেট id বাদ, লাইভ → শিডিউল → রিপ্লে ক্রমে
        _matches.value = collected
            .distinctBy { it.id.ifBlank { it.title + it.startTimeEpochMs } }
            .sortedWith(compareBy<SportsMatch>({ statusRank(it.status) }, { it.startTimeEpochMs }))
        _errors.value = failures
        lastFetchAt = System.currentTimeMillis()
        _isLoading.value = false
    }

    private fun statusRank(s: SportsMatchStatus) = when (s) {
        SportsMatchStatus.LIVE -> 0
        SportsMatchStatus.UPCOMING -> 1
        SportsMatchStatus.REPLAY -> 2
        SportsMatchStatus.FINISHED -> 3
    }

    private fun fetchSource(source: SportsHubSource): List<SportsMatch> {
        return try {
            val body = httpGet(source.url)
            if (body.isNullOrBlank()) {
                emptyList()
            } else when (source.kind) {
                SportsSourceKind.JSON -> parseJsonFeed(body, source)
                SportsSourceKind.PLAYLIST -> parsePlaylist(body, source)
                SportsSourceKind.EMBED -> emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Source ${source.name} failed: ${e.message}")
            emptyList()
        }
    }

    private fun httpGet(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "MukulPlus-SportsHub/1.0")
                .header("Accept", "application/json, text/plain, */*")
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP failed for $url: ${e.message}")
            null
        }
    }

    /**
     * সমর্থিত JSON ফরম্যাট (সব ফিল্ড ঐচ্ছিক, যেটা আছে সেটাই নেবে):
     * [
     *   {
     *     "id": "m1", "title": "BD vs IND", "teamA": "Bangladesh", "teamB": "India",
     *     "league": "T20 Series", "status": "LIVE", "startTime": 1763000000000,
     *     "poster": "https://...",
     *     "streams": [ {"label":"HD 1","url":"https://...","quality":"1080p"} ]
     *   }
     * ]
     * অথবা { "matches": [ ... ] }  — দুইভাবেই চলবে।
     */
    private fun parseJsonFeed(body: String, source: SportsHubSource): List<SportsMatch> {
        val trimmed = body.trim()
        val array: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("matches") ?: JSONArray()
            else -> return emptyList()
        }
        val out = mutableListOf<SportsMatch>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            out.add(parseMatch(o, source, i))
        }
        return out
    }

    private fun parseMatch(o: JSONObject, source: SportsHubSource, index: Int): SportsMatch {
        val streamsArr = o.optJSONArray("streams")
        val streams = mutableListOf<SportsStream>()
        if (streamsArr != null) {
            for (j in 0 until streamsArr.length()) {
                val s = streamsArr.optJSONObject(j) ?: continue
                val url = s.optString("url", "").trim()
                if (url.isBlank()) continue
                streams.add(
                    SportsStream(
                        label = s.optString("label", "সার্ভার ${j + 1}"),
                        url = url,
                        quality = s.optString("quality", "HD"),
                        isEmbed = s.optBoolean("embed", false)
                    )
                )
            }
        }
        // একক url ফিল্ড থাকলে সেটাকেও স্ট্রিম হিসেবে নাও
        val singleUrl = o.optString("url", "").trim()
        if (streams.isEmpty() && singleUrl.isNotBlank()) {
            streams.add(SportsStream(label = "সার্ভার ১", url = singleUrl))
        }

        return SportsMatch(
            id = o.optString("id").ifBlank { "${source.id}_$index" },
            title = o.optString("title").ifBlank { "ম্যাচ ${index + 1}" },
            teamA = o.optString("teamA", ""),
            teamB = o.optString("teamB", ""),
            league = o.optString("league", ""),
            subtitle = o.optString("subtitle", ""),
            startTimeEpochMs = o.optLong("startTime", 0L),
            status = parseStatus(o.optString("status", "")),
            posterUrl = o.optString("poster", o.optString("image", "")),
            streams = streams,
            sourceId = source.id
        )
    }

    private fun parseStatus(raw: String): SportsMatchStatus = when (raw.uppercase()) {
        "LIVE", "ONGOING" -> SportsMatchStatus.LIVE
        "REPLAY", "HIGHLIGHT", "HIGHLIGHTS" -> SportsMatchStatus.REPLAY
        "FINISHED", "ENDED", "COMPLETED" -> SportsMatchStatus.FINISHED
        else -> SportsMatchStatus.UPCOMING
    }

    /** এক লাইনে একটি URL — লেবেল হিসেবে ফাইলের নাম/সূচক */
    private fun parsePlaylist(body: String, source: SportsHubSource): List<SportsMatch> =
        body.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("http") && !it.startsWith("#") }
            .mapIndexed { i, url ->
                SportsMatch(
                    id = "${source.id}_pl_$i",
                    title = url.substringAfterLast('/').substringBefore('?').ifBlank { "স্ট্রিম ${i + 1}" },
                    status = SportsMatchStatus.LIVE,
                    streams = listOf(SportsStream(label = "সরাসরি", url = url)),
                    sourceId = source.id
                )
            }
            .toList()
}
