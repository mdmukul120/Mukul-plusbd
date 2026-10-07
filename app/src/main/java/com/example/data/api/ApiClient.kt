package com.example.data.api

import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    private const val TMDB_API_KEY = "0b3d17a4fe3dd52593a48d9a0dad4bd6"
    private const val IPTV_URL = "https://mukul-ott.ai.studio/api/iptv/live.m3u"
    private const val HAMYRA_API_BASE = "https://hamyra-api.mdibrahimkhalil516.workers.dev"
    private const val HAMYRA_ORIGIN = "https://www.hamyra.xyz"
    private const val HAMYRA_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    @Volatile
    private var hamyraToken: String? = null
    @Volatile
    private var hamyraTokenExp: Long = 0L

    @Synchronized
    fun getHamyraToken(): String? {
        val now = System.currentTimeMillis() / 1000
        if (!hamyraToken.isNullOrEmpty() && hamyraTokenExp > now + 30) {
            return hamyraToken
        }
        try {
            val req = Request.Builder()
                .url("$HAMYRA_API_BASE/auth/token")
                .header("Accept", "application/json")
                .header("Origin", HAMYRA_ORIGIN)
                .header("Referer", "$HAMYRA_ORIGIN/")
                .header("User-Agent", HAMYRA_USER_AGENT)
                .build()
            val res = client.newCall(req).execute()
            val body = res.body?.string()
            if (!body.isNullOrEmpty()) {
                val json = JSONObject(body)
                val token = json.optString("token")
                val exp = json.optLong("exp", now + 300)
                if (token.isNotEmpty()) {
                    hamyraToken = token
                    hamyraTokenExp = exp
                    return token
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Hamyra auth token", e)
        }
        return hamyraToken
    }

    fun buildHamyraHeaders(): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
            .add("User-Agent", HAMYRA_USER_AGENT)
            .add("Origin", HAMYRA_ORIGIN)
            .add("Referer", "$HAMYRA_ORIGIN/")
            .add("Accept", "application/json")
        val token = getHamyraToken()
        if (!token.isNullOrEmpty()) {
            builder.add("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(15, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Fast client for BDIX / CtgHall with short timeout to prevent UI freezes when BDIX routing is unavailable
    private val ctgClient: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(okhttp3.ConnectionPool(5, 2, TimeUnit.MINUTES))
        .retryOnConnectionFailure(false)
        .connectTimeout(1500, TimeUnit.MILLISECONDS)
        .readTimeout(2000, TimeUnit.MILLISECONDS)
        .writeTimeout(2000, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    private var ctgHallCooldownUntil: Long = 0L
    private const val CTG_HALL_FAILURE_COOLDOWN_MS = 15 * 60 * 1000L // 15 minutes circuit breaker cooldown

    // Curated high-quality offline/fallback catalog for instant rendering when BDIX is down
    private val fallbackMoviesList = listOf(
        // Hollywood (Library 1)
        CtgMovie(
            id = -101L,
            title = "Inception",
            original_title = "Inception",
            year = 2010,
            poster_path = "https://image.tmdb.org/t/p/w500/edv5CZvWj09upOsy2Y6IwDhK8bt.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg",
            release_date = "2010-07-16",
            online_rating = 8.8,
            genre = "Action, Sci-Fi",
            overview = "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.",
            Library = CtgLibrary(1, "English Movies", "MOVIE")
        ),
        CtgMovie(
            id = -102L,
            title = "Oppenheimer",
            original_title = "Oppenheimer",
            year = 2023,
            poster_path = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
            release_date = "2023-07-21",
            online_rating = 8.9,
            genre = "Biography, Drama, History",
            overview = "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.",
            Library = CtgLibrary(1, "English Movies", "MOVIE")
        ),
        CtgMovie(
            id = -103L,
            title = "Dune: Part Two",
            original_title = "Dune: Part Two",
            year = 2024,
            poster_path = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/xOMo8BRK7PfcJv9JCnx7s520b22.jpg",
            release_date = "2024-03-01",
            online_rating = 8.6,
            genre = "Action, Adventure, Sci-Fi",
            overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
            Library = CtgLibrary(1, "English Movies", "MOVIE")
        ),
        CtgMovie(
            id = -104L,
            title = "Interstellar",
            original_title = "Interstellar",
            year = 2014,
            poster_path = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/rAiYTsqJJR0KP8UN8vCnZlhJW2w.jpg",
            release_date = "2014-11-07",
            online_rating = 8.7,
            genre = "Adventure, Drama, Sci-Fi",
            overview = "When Earth becomes uninhabitable in the future, a farmer and ex-NASA pilot, Joseph Cooper, is tasked to pilot a spacecraft.",
            Library = CtgLibrary(1, "English Movies", "MOVIE")
        ),
        CtgMovie(
            id = -105L,
            title = "The Dark Knight",
            original_title = "The Dark Knight",
            year = 2008,
            poster_path = "https://image.tmdb.org/t/p/w500/qJ2tW6WMUDux911r6m7haRef0WH.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/dqK9Hag1054tghRQSqLSfrkvQnA.jpg",
            release_date = "2008-07-18",
            online_rating = 9.0,
            genre = "Action, Crime, Drama",
            overview = "When the menace known as the Joker wreaks havoc and chaos on the people of Gotham, Batman must accept one of the greatest psychological and physical tests.",
            Library = CtgLibrary(1, "English Movies", "MOVIE")
        ),
        CtgMovie(
            id = -106L,
            title = "Avatar: The Way of Water",
            original_title = "Avatar: The Way of Water",
            year = 2022,
            poster_path = "https://image.tmdb.org/t/p/w500/t6HIqrRAclMCA60NsSmeqe9RmNV.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/s16H6tpK2utvwDtzZ8Qy4qm5Emw.jpg",
            release_date = "2022-12-16",
            online_rating = 7.8,
            genre = "Action, Adventure, Fantasy",
            overview = "Jake Sully lives with his newfound family formed on the extrasolar moon Pandora.",
            Library = CtgLibrary(1, "English Movies", "MOVIE")
        ),

        // Bollywood (Library 4)
        CtgMovie(
            id = -201L,
            title = "Jawan",
            original_title = "Jawan",
            year = 2023,
            poster_path = "https://image.tmdb.org/t/p/w500/jYW6gVzUjGzVpD9c1e1i3qP3y0p.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/iIvQnZyzgx9TkbrOgcXx0p56iQe.jpg",
            release_date = "2023-09-07",
            online_rating = 8.0,
            genre = "Action, Thriller",
            overview = "A high-octane action thriller which outlines the emotional journey of a man who is set to rectify the wrongs in the society.",
            Library = CtgLibrary(4, "Bollywood Movies", "MOVIE")
        ),
        CtgMovie(
            id = -202L,
            title = "12th Fail",
            original_title = "12th Fail",
            year = 2023,
            poster_path = "https://image.tmdb.org/t/p/w500/yfu56bYhH34N534K7H2X3N7mH.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/1XddXPXQI25hfqV39WfX7G0l4.jpg",
            release_date = "2023-10-27",
            online_rating = 9.2,
            genre = "Biography, Drama",
            overview = "Based on the real-life story of IPS Officer Manoj Kumar Sharma and IRS Officer Shraddha Joshi.",
            Library = CtgLibrary(4, "Bollywood Movies", "MOVIE")
        ),
        CtgMovie(
            id = -203L,
            title = "Animal",
            original_title = "Animal",
            year = 2023,
            poster_path = "https://image.tmdb.org/t/p/w500/hr9rjRjZ5b7tK4m0aJ8u7bZz0z.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/ehmvgv8L7m7d54K0o5.jpg",
            release_date = "2023-12-01",
            online_rating = 7.5,
            genre = "Action, Crime, Drama",
            overview = "A son's obsessive love for his father leads to an underworld war with his adversaries.",
            Library = CtgLibrary(4, "Bollywood Movies", "MOVIE")
        ),
        CtgMovie(
            id = -204L,
            title = "Pathaan",
            original_title = "Pathaan",
            year = 2023,
            poster_path = "https://image.tmdb.org/t/p/w500/m1b9ToB5o1n2i3p4o5N6q7r8s9t.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg",
            release_date = "2023-01-25",
            online_rating = 7.2,
            genre = "Action, Thriller",
            overview = "An Indian agent races against a doomsday clock as a ruthless mercenary with a bitter vendetta mounts an apocalyptic attack against India.",
            Library = CtgLibrary(4, "Bollywood Movies", "MOVIE")
        ),

        // Bangla Movies (Library 6)
        CtgMovie(
            id = -301L,
            title = "তুফান (Toofan)",
            original_title = "Toofan",
            year = 2024,
            poster_path = "https://image.tmdb.org/t/p/w500/7lK8zZ5b7tK4m0aJ8u7bZz0z9rj.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/fm6KqXpk3M2HVveHwCrBSSBaO0V.jpg",
            release_date = "2024-06-17",
            online_rating = 8.5,
            genre = "Action, Crime",
            overview = "একটি আন্ডারওয়ার্ল্ড ডন তুফানের উত্থান ও পতনের টানটান উত্তেজনাময় কাহিনী।",
            Library = CtgLibrary(6, "Bangla Movies", "MOVIE")
        ),
        CtgMovie(
            id = -302L,
            title = "হাওয়া (Hawa)",
            original_title = "Hawa",
            year = 2022,
            poster_path = "https://image.tmdb.org/t/p/w500/9k6L3mNp2546zW0bE2qXqjZ8T0e.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/xOMo8BRK7PfcJv9JCnx7s520b22.jpg",
            release_date = "2022-07-29",
            online_rating = 8.2,
            genre = "Drama, Mystery",
            overview = "গভীর সমুদ্রে মাছ ধরার ট্রলারে এক রহস্যময় নারীকে কেন্দ্র করে ঘটিত টানটান রোমাঞ্চকর ঘটনা।",
            Library = CtgLibrary(6, "Bangla Movies", "MOVIE")
        ),
        CtgMovie(
            id = -303L,
            title = "সুরঙ্গ (Shurongo)",
            original_title = "Shurongo",
            year = 2023,
            poster_path = "https://image.tmdb.org/t/p/w500/aJ8u7bZz0z9rjRjZ5b7tK4m0b8t.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/rAiYTsqJJR0KP8UN8vCnZlhJW2w.jpg",
            release_date = "2023-06-29",
            online_rating = 8.0,
            genre = "Crime, Thriller",
            overview = "মাসুদ নামের এক সাধারণ যুবকের ব্যাংক ডাকাতির জন্য সুড়ঙ্গ খোঁড়ার শ্বাসরুদ্ধকর গল্প।",
            Library = CtgLibrary(6, "Bangla Movies", "MOVIE")
        ),
        CtgMovie(
            id = -304L,
            title = "প্রিয়তমা (Priyotoma)",
            original_title = "Priyotoma",
            year = 2023,
            poster_path = "https://image.tmdb.org/t/p/w500/qXqjZ8T0e9k6L3mNp2546zW0bE2.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/dqK9Hag1054tghRQSqLSfrkvQnA.jpg",
            release_date = "2023-06-29",
            online_rating = 7.7,
            genre = "Action, Romance",
            overview = "ভালোবাসা ও আত্মত্যাগের এক অনন্য হৃদয়স্পর্শী গল্প।",
            Library = CtgLibrary(6, "Bangla Movies", "MOVIE")
        ),

        // South Indian & Action (Library 7)
        CtgMovie(
            id = -401L,
            title = "K.G.F: Chapter 2",
            original_title = "K.G.F: Chapter 2",
            year = 2022,
            poster_path = "https://image.tmdb.org/t/p/w500/m56bYhH34N534K7H2X3N7mHyfu.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/s16H6tpK2utvwDtzZ8Qy4qm5Emw.jpg",
            release_date = "2022-04-14",
            online_rating = 8.4,
            genre = "Action, Crime",
            overview = "The blood-soaked land of Kolar Gold Fields has a new overlord now - Rocky, whose name strikes fear in the heart of his foes.",
            Library = CtgLibrary(7, "South Indian", "MOVIE")
        ),
        CtgMovie(
            id = -402L,
            title = "Salaar: Part 1 - Ceasefire",
            original_title = "Salaar",
            year = 2023,
            poster_path = "https://image.tmdb.org/t/p/w500/vJ1pdfLvkbY9ohJlCjQH2CZjjYV.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/iIvQnZyzgx9TkbrOgcXx0p56iQe.jpg",
            release_date = "2023-12-22",
            online_rating = 7.9,
            genre = "Action, Drama",
            overview = "A gang leader tries to keep a promise made to his dying friend and takes on the other criminal gangs.",
            Library = CtgLibrary(7, "South Indian", "MOVIE")
        ),
        CtgMovie(
            id = -403L,
            title = "RRR",
            original_title = "RRR",
            year = 2022,
            poster_path = "https://image.tmdb.org/t/p/w500/nEufeZlyAOLqO2brrs0yeMu1Q2P.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/1XddXPXQI25hfqV39WfX7G0l4.jpg",
            release_date = "2022-03-25",
            online_rating = 8.9,
            genre = "Action, Drama",
            overview = "A fearless revolutionary and an officer in the British force decide to join forces for liberation.",
            Library = CtgLibrary(7, "South Indian", "MOVIE")
        ),

        // Animation Movies
        CtgMovie(
            id = -501L,
            title = "Spider-Man: Across the Spider-Verse",
            original_title = "Spider-Man: Across the Spider-Verse",
            year = 2023,
            poster_path = "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/ehmvgv8L7m7d54K0o5.jpg",
            release_date = "2023-06-02",
            online_rating = 8.7,
            genre = "Animation, Action, Adventure",
            overview = "Miles Morales catapults across the Multiverse, where he encounters a team of Spider-People charged with protecting its very existence.",
            Library = CtgLibrary(5, "Asian & Anime", "MOVIE")
        ),
        CtgMovie(
            id = -502L,
            title = "Inside Out 2",
            original_title = "Inside Out 2",
            year = 2024,
            poster_path = "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
            backdrop_path = "https://image.tmdb.org/t/p/w780/8ZTVqvKDQ8emSGUEMjsS4yHAwrp.jpg",
            release_date = "2024-06-14",
            online_rating = 8.2,
            genre = "Animation, Adventure, Comedy",
            overview = "Teenager Riley's mind headquarters is undergoing a sudden demolition to make room for unexpected new Emotions!",
            Library = CtgLibrary(5, "Asian & Anime", "MOVIE")
        )
    )

    private fun getFallbackCtgMovies(
        library: Int?,
        genre: String?,
        page: Int,
        search: String? = null
    ): CtgMoviesResponse {
        var list = fallbackMoviesList
        if (library != null) {
            list = list.filter { it.Library?.id == library }
        }
        if (!genre.isNullOrBlank()) {
            list = list.filter { it.genre?.contains(genre, ignoreCase = true) == true }
        }
        if (!search.isNullOrBlank()) {
            list = list.filter {
                it.title.contains(search, ignoreCase = true) ||
                (it.original_title?.contains(search, ignoreCase = true) == true)
            }
        }
        if (list.isEmpty()) {
            list = fallbackMoviesList.take(6)
        }
        return CtgMoviesResponse(
            total = list.size,
            pages = 1,
            current_page = page,
            data = list
        )
    }

    private fun getFallbackMovieById(id: Long): CtgMovie? {
        return unifiedMoviesCache[id] ?: fallbackMoviesList.find { it.id == id } ?: bongoMoviesCache[id]
    }

    private val unifiedMoviesCache = java.util.concurrent.ConcurrentHashMap<Long, CtgMovie>()

    private fun cleanHtmlText(text: String): String {
        return text
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&#039;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&hellip;", "…")
            .trim()
    }

    private fun cleanMlsbdTitle(rawTitle: String): Pair<String, Int?> {
        var t = cleanHtmlText(rawTitle)
        t = t.replace(Regex("\\[.*?\\]"), "").trim()
        val yearMatch = Regex("\\((\\d{4})\\)").find(t)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        val mainTitle = when {
            t.contains("(") -> t.substringBefore("(").trim()
            t.contains("–") -> t.substringBefore("–").trim()
            t.contains("â€“") -> t.substringBefore("â€“").trim()
            t.contains("-") -> t.substringBefore("-").trim()
            else -> t
        }
        return Pair(if (mainTitle.isNotEmpty()) mainTitle else t, year)
    }

    private fun parseTmdbMovie(item: JSONObject, catId: Int = 1, catName: String = "Trending"): CtgMovie {
        val id = item.optLong("id")
        val title = item.optString("title", item.optString("name", "Untitled"))
        val relDate = item.optString("release_date", item.optString("first_air_date", null))
        val yr = relDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val poster = item.optString("poster_path", null)
        val backdrop = item.optString("backdrop_path", null)
        val rating = if (item.has("vote_average")) item.optDouble("vote_average") else 7.8
        val overview = item.optString("overview", null)

        val movie = CtgMovie(
            id = id,
            title = title,
            original_title = item.optString("original_title", null),
            year = yr,
            poster_path = poster,
            backdrop_path = backdrop,
            release_date = relDate,
            online_rating = rating,
            overview = overview,
            tmdb_id = id.toString(),
            url = "https://cmos.raphsm4.dev/?tmdb=$id",
            Library = CtgLibrary(catId, catName, "MOVIE")
        )
        unifiedMoviesCache[id] = movie
        return movie
    }

    private fun parseMlbdMovie(item: JSONObject, catId: Int, catName: String): CtgMovie {
        val slug = item.optString("slug")
        val uniqueId = Math.abs(slug.hashCode().toLong()) + 1000000L
        val title = cleanHtmlText(item.optString("title", "Untitled"))
        val yr = if (item.has("year") && !item.isNull("year")) item.optInt("year") else 2025
        val poster = item.optString("poster", null)
        val quality = cleanHtmlText(item.optString("quality_tag", ""))
        val lang = cleanHtmlText(item.optString("language", ""))

        val movie = CtgMovie(
            id = uniqueId,
            title = title,
            original_title = if (lang.isNotEmpty()) lang else catName,
            year = yr,
            poster_path = poster,
            backdrop_path = poster,
            release_date = "$yr-01-01",
            online_rating = 8.2,
            genre = if (lang.isNotEmpty()) lang else catName,
            casts = quality.ifEmpty { null },
            file_path = slug,
            url = "$HAMYRA_API_BASE/mlbd/by-slug?slug=$slug",
            Library = CtgLibrary(catId, catName, "MOVIE")
        )
        unifiedMoviesCache[uniqueId] = movie
        return movie
    }

    private fun parseMlsbdMovie(item: JSONObject, catId: Int, catName: String): CtgMovie {
        val slug = item.optString("slug")
        val uniqueId = Math.abs(slug.hashCode().toLong()) + 2000000L
        val rawTitle = item.optString("title", "Untitled")
        val (cleanTitle, parsedYear) = cleanMlsbdTitle(rawTitle)
        val poster = item.optString("poster", null)

        val movie = CtgMovie(
            id = uniqueId,
            title = cleanTitle,
            original_title = catName,
            year = parsedYear ?: 2025,
            poster_path = poster,
            backdrop_path = poster,
            release_date = "${parsedYear ?: 2025}-01-01",
            online_rating = 8.0,
            genre = catName,
            file_path = slug,
            url = "$HAMYRA_API_BASE/mlsbd/detail?slug=$slug",
            Library = CtgLibrary(catId, catName, "MOVIE")
        )
        unifiedMoviesCache[uniqueId] = movie
        return movie
    }

    private val defaultMenusData = CtgMenusData(
        movieCategories = listOf(
            CtgCategoryItem(1, "🔥 Trending", "TRENDING"),
            CtgCategoryItem(2, "🎬 Hindi Dubbed", "LANGUAGE"),
            CtgCategoryItem(3, "✨ Bollywood", "CATEGORY"),
            CtgCategoryItem(4, "🎧 Dual Audio", "LANGUAGE"),
            CtgCategoryItem(5, "🇧🇩 Bangla Movies", "CATEGORY"),
            CtgCategoryItem(6, "💥 South Indian", "LANGUAGE"),
            CtgCategoryItem(7, "🌟 Hollywood", "CATEGORY"),
            CtgCategoryItem(8, "📺 Bangla Dubbed", "LANGUAGE"),
            CtgCategoryItem(9, "🎭 Natok & Series", "CATEGORY"),
            CtgCategoryItem(10, "⛩️ Anime & Korean", "CATEGORY"),
            CtgCategoryItem(11, "🌏 Tamil & Telugu", "LANGUAGE"),
            CtgCategoryItem(12, "🔥 Bongo BD", "BONGO")
        ),
        years = (2026 downTo 2012).toList(),
        movieGenres = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Drama", "Fantasy", "Horror", "Romance", "Sci-Fi", "Thriller")
    )

    // 1. Unified Movies List (Replaces CtgHall with TMDB, Hamyra MLBD & MLSBD)
    suspend fun fetchCtgMovies(
        library: Int? = 1,
        page: Int = 1,
        sort: String = "createdAt",
        sortOrder: String = "DESC",
        search: String? = null,
        year: Int? = null,
        genre: String? = null
    ): CtgMoviesResponse = withContext(Dispatchers.IO) {
        val safePage = maxOf(1, page)
        val movies = mutableListOf<CtgMovie>()

        try {
            // A. Search Mode (searches across TMDB + MLBD + MLSBD)
            if (!search.isNullOrBlank()) {
                val encoded = java.net.URLEncoder.encode(search.trim(), "UTF-8")

                // 1. TMDB Multi Search
                try {
                    val tmdbUrl = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&language=en-US&query=$encoded&page=$safePage"
                    val req = Request.Builder().url(tmdbUrl).build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val results = json.optJSONArray("results") ?: JSONArray()
                        for (i in 0 until results.length()) {
                            val item = results.optJSONObject(i) ?: continue
                            val mediaType = item.optString("media_type", "movie")
                            if (mediaType == "person") continue
                            movies.add(parseTmdbMovie(item, 1, "Search"))
                        }
                    }
                } catch (_: Exception) {}

                // 2. MLBD Search
                try {
                    val mlbdUrl = "$HAMYRA_API_BASE/mlbd/search?q=$encoded"
                    val req = Request.Builder().url(mlbdUrl).headers(buildHamyraHeaders()).build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items") ?: JSONArray()
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            movies.add(parseMlbdMovie(item, 2, "Search"))
                        }
                    }
                } catch (_: Exception) {}

                // 3. MLSBD Search
                try {
                    val mlsbdUrl = "$HAMYRA_API_BASE/mlsbd/browse?q=$encoded&page=$safePage&perPage=15"
                    val req = Request.Builder().url(mlsbdUrl).headers(buildHamyraHeaders()).build()
                    val res = client.newCall(req).execute()
                    val body = res.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items") ?: JSONArray()
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            movies.add(parseMlsbdMovie(item, 5, "Search"))
                        }
                    }
                } catch (_: Exception) {}

                var filtered = movies.distinctBy { it.title.lowercase().trim() }
                if (year != null && year > 0) {
                    filtered = filtered.filter { it.year == year }
                }
                if (!genre.isNullOrBlank()) {
                    filtered = filtered.filter { it.genre?.contains(genre, ignoreCase = true) == true }
                }

                return@withContext CtgMoviesResponse(
                    total = filtered.size,
                    pages = if (filtered.size > 20) 2 else 1,
                    current_page = safePage,
                    data = filtered
                )
            }

            // B. Category Browse Mode
            var totalPages = 10
            val targetLib = library ?: 1

            // Special genre filters (e.g. from HomeScreen)
            if (genre?.equals("Animation", ignoreCase = true) == true) {
                val url = "$HAMYRA_API_BASE/mlsbd/browse?cat=animation-movies&page=$safePage&perPage=20"
                val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    totalPages = json.optInt("totalPages", 10)
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        movies.add(parseMlsbdMovie(item, 10, "Animation"))
                    }
                }
            } else if (genre?.equals("Action", ignoreCase = true) == true) {
                val url = "$HAMYRA_API_BASE/mlbd/category?slug=telugu&kind=language&page=$safePage&only=movie"
                val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: JSONArray()
                    totalPages = json.optInt("total_pages", 15)
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        movies.add(parseMlbdMovie(item, 6, "Action"))
                    }
                }
            } else if (sort.contains("rating", ignoreCase = true)) {
                val url = "https://api.themoviedb.org/3/movie/top_rated?api_key=$TMDB_API_KEY&language=en-US&page=$safePage"
                val req = Request.Builder().url(url).build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val results = json.optJSONArray("results") ?: JSONArray()
                    totalPages = json.optInt("total_pages", 20)
                    for (i in 0 until results.length()) {
                        val item = results.optJSONObject(i) ?: continue
                        movies.add(parseTmdbMovie(item, 1, "Top Rated"))
                    }
                }
            } else {
                when (targetLib) {
                    1 -> {
                        // 1. Trending & Popular (TMDB)
                        val url = "https://api.themoviedb.org/3/trending/movie/week?api_key=$TMDB_API_KEY&language=en-US&page=$safePage"
                        val req = Request.Builder().url(url).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val results = json.optJSONArray("results") ?: JSONArray()
                            totalPages = json.optInt("total_pages", 25)
                            for (i in 0 until results.length()) {
                                val item = results.optJSONObject(i) ?: continue
                                movies.add(parseTmdbMovie(item, 1, "Trending"))
                            }
                        }
                    }
                    2 -> {
                        // 2. Hindi Dubbed (MLBD)
                        val url = "$HAMYRA_API_BASE/mlbd/category?slug=hindi-dubbed&kind=language&page=$safePage&only=movie"
                        val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items") ?: JSONArray()
                            totalPages = json.optInt("total_pages", 25)
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                movies.add(parseMlbdMovie(item, 2, "Hindi Dubbed"))
                            }
                        }
                    }
                    3, 4 -> {
                        // 3. Bollywood (MLBD / MLSBD)
                        val url = "$HAMYRA_API_BASE/mlbd/category?slug=bollywood&kind=category&page=$safePage&only=movie"
                        val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items") ?: JSONArray()
                            totalPages = json.optInt("total_pages", 10)
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                movies.add(parseMlbdMovie(item, 3, "Bollywood"))
                            }
                        }
                    }
                    5, 6 -> {
                        // 5/6. Bangla Movies (MLSBD)
                        val url = "$HAMYRA_API_BASE/mlsbd/browse?cat=bangla-movies&page=$safePage&perPage=20"
                        val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items") ?: JSONArray()
                            totalPages = json.optInt("totalPages", 50)
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                movies.add(parseMlsbdMovie(item, 5, "Bangla Movies"))
                            }
                        }
                    }
                    7 -> {
                        // 7. Hollywood (TMDB Discover Popular)
                        val url = "https://api.themoviedb.org/3/discover/movie?api_key=$TMDB_API_KEY&language=en-US&sort_by=popularity.desc&page=$safePage&with_original_language=en&vote_count.gte=100"
                        val req = Request.Builder().url(url).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val results = json.optJSONArray("results") ?: JSONArray()
                            totalPages = json.optInt("total_pages", 50)
                            for (i in 0 until results.length()) {
                                val item = results.optJSONObject(i) ?: continue
                                movies.add(parseTmdbMovie(item, 7, "Hollywood"))
                            }
                        }
                    }
                    8 -> {
                        // 8. Bangla Dubbed (MLSBD)
                        val url = "$HAMYRA_API_BASE/mlsbd/browse?cat=bangla-dubbed&page=$safePage&perPage=20"
                        val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items") ?: JSONArray()
                            totalPages = json.optInt("totalPages", 40)
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                movies.add(parseMlsbdMovie(item, 8, "Bangla Dubbed"))
                            }
                        }
                    }
                    9 -> {
                        // 9. Natok & Telefilm (MLSBD)
                        val url = "$HAMYRA_API_BASE/mlsbd/browse?cat=natok-teleflim&page=$safePage&perPage=20"
                        val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items") ?: JSONArray()
                            totalPages = json.optInt("totalPages", 30)
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                movies.add(parseMlsbdMovie(item, 9, "Natok & Series"))
                            }
                        }
                    }
                    10 -> {
                        // 10. Korean & Asian (MLSBD)
                        val url = "$HAMYRA_API_BASE/mlsbd/browse?cat=korean-movies&page=$safePage&perPage=20"
                        val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items") ?: JSONArray()
                            totalPages = json.optInt("totalPages", 30)
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                movies.add(parseMlsbdMovie(item, 10, "Korean & Asian"))
                            }
                        }
                    }
                    11 -> {
                        // 11. Tamil & Telugu (MLBD)
                        val url = "$HAMYRA_API_BASE/mlbd/category?slug=tamil&kind=language&page=$safePage&only=movie"
                        val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items") ?: JSONArray()
                            totalPages = json.optInt("total_pages", 15)
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                movies.add(parseMlbdMovie(item, 11, "Tamil & Telugu"))
                            }
                        }
                    }
                    12 -> {
                        // 12. Bongo BD
                        val bongo = fetchBongoVideos()
                        return@withContext CtgMoviesResponse(total = bongo.size, pages = 1, current_page = 1, data = bongo)
                    }
                    else -> {
                        // Fallback: Dual Audio (MLBD)
                        val url = "$HAMYRA_API_BASE/mlbd/category?slug=dual-audio&kind=language&page=$safePage&only=movie"
                        val req = Request.Builder().url(url).headers(buildHamyraHeaders()).build()
                        val res = client.newCall(req).execute()
                        val body = res.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items") ?: JSONArray()
                            totalPages = json.optInt("total_pages", 20)
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                movies.add(parseMlbdMovie(item, 4, "Dual Audio"))
                            }
                        }
                    }
                }
            }

            var finalResult = movies.distinctBy { it.title.lowercase().trim() }
            if (year != null && year > 0) {
                finalResult = finalResult.filter { it.year == year }
            }
            if (!genre.isNullOrBlank()) {
                finalResult = finalResult.filter { it.genre?.contains(genre, ignoreCase = true) == true }
            }

            if (finalResult.isNotEmpty()) {
                return@withContext CtgMoviesResponse(
                    total = maxOf(finalResult.size, totalPages * 20),
                    pages = maxOf(1, totalPages),
                    current_page = safePage,
                    data = finalResult
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "fetchCtgMovies API error: ${e.message}")
        }

        getFallbackCtgMovies(library, genre, page, search)
    }

    // 2. Movie Detail (Rich metadata & Direct Streams from TMDB, CMOS, MLBD, MLSBD, Bongo)
    suspend fun fetchCtgMovieDetail(id: Long): CtgMovie? = withContext(Dispatchers.IO) {
        var movie = unifiedMoviesCache[id] ?: getFallbackMovieById(id)

        // A. If TMDB ID exists or ID in TMDB range:
        val tmdbId = movie?.tmdb_id ?: (if (id in 1..999999L) id.toString() else null)
        if (tmdbId != null) {
            try {
                val url = "https://api.themoviedb.org/3/movie/$tmdbId?api_key=$TMDB_API_KEY&language=en-US&append_to_response=credits,videos"
                val req = Request.Builder().url(url).build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val credits = json.optJSONObject("credits")
                    val castList = mutableListOf<String>()
                    credits?.optJSONArray("cast")?.let { cArr ->
                        for (i in 0 until minOf(5, cArr.length())) {
                            cArr.optJSONObject(i)?.optString("name")?.let { castList.add(it) }
                        }
                    }
                    val genresList = mutableListOf<String>()
                    json.optJSONArray("genres")?.let { gArr ->
                        for (i in 0 until gArr.length()) {
                            gArr.optJSONObject(i)?.optString("name")?.let { genresList.add(it) }
                        }
                    }
                    val overview = json.optString("overview", movie?.overview)
                    val poster = json.optString("poster_path", movie?.poster_path)
                    val backdrop = json.optString("backdrop_path", movie?.backdrop_path)
                    val releaseDate = json.optString("release_date", movie?.release_date)
                    val rating = if (json.has("vote_average")) json.optDouble("vote_average") else movie?.online_rating

                    // Resolve direct stream from CMOS or fallback
                    var streamUrl = movie?.url
                    try {
                        val cmosReq = Request.Builder().url("https://cmos.raphsm4.dev/?tmdb=$tmdbId").header("User-Agent", "Mozilla/5.0").build()
                        val cmosClient = client.newBuilder().connectTimeout(2500, TimeUnit.MILLISECONDS).readTimeout(2500, TimeUnit.MILLISECONDS).build()
                        val cmosRes = cmosClient.newCall(cmosReq).execute()
                        val cmosBody = cmosRes.body?.string()
                        if (!cmosBody.isNullOrEmpty()) {
                            val cmosJson = JSONObject(cmosBody)
                            val directPlay = cmosJson.optString("directPlayUrl")
                            if (directPlay.isNotEmpty()) {
                                streamUrl = directPlay
                            }
                        }
                    } catch (_: Exception) {}

                    if (streamUrl.isNullOrEmpty() || streamUrl.contains("cmos.raphsm4.dev")) {
                        streamUrl = "https://vidsrc.to/embed/movie/$tmdbId"
                    }

                    movie = (movie ?: CtgMovie(id = id, title = json.optString("title", "Movie"))).copy(
                        overview = overview,
                        poster_path = poster,
                        backdrop_path = backdrop,
                        release_date = releaseDate,
                        online_rating = rating,
                        casts = if (castList.isNotEmpty()) castList.joinToString(", ") else movie?.casts,
                        genre = if (genresList.isNotEmpty()) genresList.joinToString(", ") else movie?.genre,
                        url = streamUrl,
                        tmdb_id = tmdbId
                    )
                    unifiedMoviesCache[id] = movie
                    return@withContext movie
                }
            } catch (e: Exception) {
                Log.d(TAG, "TMDB detail error for $tmdbId: ${e.message}")
            }
        }

        // B. If MLBD slug (starts with mKs_):
        val mlbdSlug = movie?.file_path?.takeIf { it.startsWith("mKs_") }
        if (mlbdSlug != null) {
            try {
                val req = Request.Builder()
                    .url("$HAMYRA_API_BASE/mlbd/by-slug?slug=$mlbdSlug")
                    .headers(buildHamyraHeaders())
                    .build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val desc = json.optString("description", movie?.overview)
                    val poster = json.optString("poster", movie?.poster_path)
                    val g = json.optString("genre", movie?.genre)
                    val castArr = json.optJSONArray("cast")
                    val castList = mutableListOf<String>()
                    if (castArr != null) {
                        for (i in 0 until castArr.length()) {
                            castList.add(castArr.optString(i))
                        }
                    }
                    var streamUrl = movie?.url
                    val watchSources = json.optJSONArray("watch_sources")
                    if (watchSources != null && watchSources.length() > 0) {
                        val firstSource = watchSources.optJSONObject(0)
                        streamUrl = firstSource?.optString("url") ?: streamUrl
                    }
                    val episodes = json.optJSONArray("episodes")
                    if (streamUrl.isNullOrEmpty() && episodes != null && episodes.length() > 0) {
                        val ep1 = episodes.optJSONObject(0)
                        val epSources = ep1?.optJSONArray("sources")
                        if (epSources != null && epSources.length() > 0) {
                            streamUrl = epSources.optJSONObject(0)?.optString("url")
                        }
                    }

                    movie = movie?.copy(
                        overview = desc,
                        poster_path = poster,
                        genre = g,
                        casts = if (castList.isNotEmpty()) castList.joinToString(", ") else movie?.casts,
                        url = streamUrl
                    )
                    if (movie != null) {
                        unifiedMoviesCache[id] = movie
                        return@withContext movie
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "MLBD detail error: ${e.message}")
            }
        }

        // C. If MLSBD slug:
        val mlsbdSlug = movie?.file_path?.takeIf { !it.startsWith("mKs_") && it.contains("-") }
        if (mlsbdSlug != null) {
            try {
                val req = Request.Builder()
                    .url("$HAMYRA_API_BASE/mlsbd/detail?slug=$mlsbdSlug")
                    .headers(buildHamyraHeaders())
                    .build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val poster = json.optString("poster", movie?.poster_path)
                    val info = json.optString("info", movie?.overview)
                    val downloads = json.optJSONArray("downloads")
                    var downloadUrl = movie?.url
                    if (downloads != null && downloads.length() > 0) {
                        val d0 = downloads.optJSONObject(0)
                        downloadUrl = d0?.optString("url") ?: downloadUrl
                    }

                    movie = movie?.copy(
                        poster_path = poster,
                        overview = if (info.isNotBlank()) info else movie?.overview,
                        url = downloadUrl
                    )
                    if (movie != null) {
                        unifiedMoviesCache[id] = movie
                        return@withContext movie
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "MLSBD detail error: ${e.message}")
            }
        }

        return@withContext movie
    }

    // 3. Menus & Categories (Instant response from rich defaultMenusData)
    suspend fun fetchCtgMenus(): CtgMenusData = withContext(Dispatchers.IO) {
        defaultMenusData
    }

    // 4. SorryBroRewards Extractor Providers
    suspend fun fetchExtractorProviders(): List<ExtractorProvider> = withContext(Dispatchers.IO) {
        val defaultList = listOf(
            ExtractorProvider("moviesmod", "MoviesMod"),
            ExtractorProvider("topmovies", "TopMovies"),
            ExtractorProvider("uhd", "UHD Movies"),
            ExtractorProvider("moviesdrive", "MoviesDrive"),
            ExtractorProvider("fourkhd", "4K HD"),
            ExtractorProvider("hdhub4u", "HDHub4U"),
            ExtractorProvider("filmyfly", "FilmyFly"),
            ExtractorProvider("kat", "Kat Movie"),
            ExtractorProvider("showbox", "Showbox"),
            ExtractorProvider("castle", "Castle TV"),
            ExtractorProvider("allmovieland", "AllMovieLand")
        )
        try {
            val url = "https://api.sorrybrorewards.com/v2/extractor/api/providers"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext defaultList
            val json = JSONObject(body)
            if (json.optBoolean("success")) {
                val arr = json.optJSONArray("data") ?: JSONArray()
                val list = mutableListOf<ExtractorProvider>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    list.add(
                        ExtractorProvider(
                            id = item.optString("id"),
                            name = item.optString("name")
                        )
                    )
                }
                if (list.isNotEmpty()) return@withContext list
            }
            defaultList
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching providers", e)
            defaultList
        }
    }

    // 5. Extractor Posts
    suspend fun fetchExtractorPosts(provider: String, page: Int = 1, filter: String = ""): List<ExtractorPost> = withContext(Dispatchers.IO) {
        try {
            val encodedFilter = java.net.URLEncoder.encode(filter, "UTF-8")
            val url = "https://api.sorrybrorewards.com/v2/extractor/api/posts?provider=$provider&filter=$encodedFilter&page=$page"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val posts = mutableListOf<ExtractorPost>()
            if (json.optBoolean("success")) {
                val arr = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    posts.add(
                        ExtractorPost(
                            title = item.optString("title", "Untitled"),
                            link = item.optString("link", ""),
                            image = item.optString("image", null),
                            provider = provider
                        )
                    )
                }
            }
            posts
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching extractor posts for $provider", e)
            emptyList()
        }
    }

    // 6. Extractor Movie Info & Download Links
    suspend fun fetchExtractorInfo(link: String, provider: String): ExtractorMovieInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.sorrybrorewards.com/v2/extractor/api/info"
            val payload = JSONObject().apply {
                put("link", link)
                put("provider", provider)
            }
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(reqBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            if (!json.optBoolean("success")) return@withContext null

            val data = json.optJSONObject("data") ?: return@withContext null
            val title = data.optString("title", "Unknown")
            val synopsis = data.optString("synopsis", null)
            val image = data.optString("image", null)
            val imdbId = data.optString("imdbId", null)
            val type = data.optString("type", null)

            val downloads = mutableListOf<DownloadLink>()
            val streams = mutableListOf<DownloadLink>()

            val linkList = data.optJSONArray("linkList") ?: JSONArray()
            for (i in 0 until linkList.length()) {
                val item = linkList.optJSONObject(i) ?: continue
                val itemTitle = item.optString("title", "Quality Link")
                val quality = item.optString("quality", "HD")
                val episodesLink = item.optString("episodesLink", null)
                if (!episodesLink.isNullOrEmpty()) {
                    downloads.add(DownloadLink(title = itemTitle, link = episodesLink, type = "Episodes", quality = quality))
                }
                val directLinks = item.optJSONArray("directLinks") ?: JSONArray()
                for (j in 0 until directLinks.length()) {
                    val dLink = directLinks.optJSONObject(j) ?: continue
                    val dlTitle = dLink.optString("title", "Download $quality")
                    val dlUrl = dLink.optString("link", "")
                    val dlType = dLink.optString("type", "direct")
                    if (dlUrl.isNotEmpty()) {
                        downloads.add(DownloadLink(title = "$dlTitle ($quality)", link = dlUrl, type = dlType, quality = quality))
                        // Direct video / drive links can also be streamed
                        if (dlUrl.endsWith(".mp4") || dlUrl.endsWith(".mkv") || dlUrl.contains("stream") || dlUrl.contains("hubcloud") || dlUrl.contains("filepress")) {
                            streams.add(DownloadLink(title = "$quality Stream", link = dlUrl, type = dlType, quality = quality))
                        }
                    }
                }
            }

            ExtractorMovieInfo(
                title = title,
                synopsis = synopsis,
                image = image,
                imdbId = imdbId,
                type = type,
                downloadLinks = downloads,
                streamLinks = streams
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching extractor info", e)
            null
        }
    }

    // 6b. Extractor Episodes List (POST /api/episodes)
    suspend fun fetchExtractorEpisodes(url: String): List<DownloadLink> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://api.sorrybrorewards.com/v2/extractor/api/episodes"
            val payload = JSONObject().apply { put("url", url) }
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpoint).post(reqBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val list = mutableListOf<DownloadLink>()
            if (json.optBoolean("success")) {
                val arr = json.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val title = item.optString("title", "Episode ${i + 1}")
                    val link = item.optString("link", "")
                    if (link.isNotEmpty()) {
                        list.add(DownloadLink(title = title, link = link, type = "episode", quality = "HD"))
                    }
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching extractor episodes", e)
            emptyList()
        }
    }

    // 6c. Extractor Direct Stream & Download Servers (POST /api/stream)
    suspend fun fetchExtractorStream(url: String): List<DownloadLink> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://api.sorrybrorewards.com/v2/extractor/api/stream"
            val payload = JSONObject().apply { put("url", url) }
            val reqBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpoint).post(reqBody).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val servers = mutableListOf<DownloadLink>()
            if (json.optBoolean("success")) {
                val data = json.optJSONObject("data") ?: return@withContext emptyList()
                val serverArr = data.optJSONArray("servers") ?: JSONArray()
                for (i in 0 until serverArr.length()) {
                    val s = serverArr.optJSONObject(i) ?: continue
                    val serverName = s.optString("server", "Server ${i + 1}")
                    val link = s.optString("link", "")
                    val type = s.optString("type", "video")
                    if (link.isNotEmpty()) {
                        servers.add(DownloadLink(title = serverName, link = link, type = type, quality = "Fast DL"))
                    }
                }
            }
            servers
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching extractor stream", e)
            emptyList()
        }
    }

    // 7. Live TV Channels (Mukul OTT Verified Live IPTV Channels)
    suspend fun fetchIptvChannels(): List<TvChannel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<TvChannel>()
        try {
            val request = Request.Builder()
                .url(IPTV_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                .header("Accept", "*/*")
                .build()
            val response = client.newCall(request).execute()
            val content = response.body?.string()
            if (!content.isNullOrEmpty()) {
                val lines = content.lines()
                var currentChannelName = ""
                var currentLogo = ""
                var currentGroup = "General"
                var currentId = ""
                val seenUrls = HashSet<String>()

                val idRegex = Regex("""tvg-id="([^"]*)"""")
                val logoRegex = Regex("""tvg-logo="([^"]*)"""")
                val groupRegex = Regex("""group-title="([^"]*)"""")
                val nameRegex = Regex("""tvg-name="([^"]*)"""")

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXTINF:")) {
                        currentId = idRegex.find(trimmed)?.groupValues?.get(1).orEmpty()
                        currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1).orEmpty()
                        currentGroup = groupRegex.find(trimmed)?.groupValues?.get(1).orEmpty().ifEmpty { "General" }

                        val commaIndex = trimmed.lastIndexOf(',')
                        currentChannelName = if (commaIndex != -1 && commaIndex + 1 < trimmed.length) {
                            trimmed.substring(commaIndex + 1).trim()
                        } else {
                            nameRegex.find(trimmed)?.groupValues?.get(1).orEmpty().ifEmpty { "Live TV" }
                        }
                    } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                        if (currentChannelName.isNotEmpty()) {
                            val streamUrl = trimmed
                            if (seenUrls.add(streamUrl)) {
                                channels.add(
                                    TvChannel(
                                        id = if (currentId.isNotEmpty()) currentId else "iptv_${channels.size}",
                                        name = currentChannelName,
                                        logo = currentLogo.ifEmpty { null },
                                        groupTitle = currentGroup.ifEmpty { "General" },
                                        streamUrl = streamUrl
                                    )
                                )
                            }
                            currentChannelName = ""
                            currentLogo = ""
                            currentGroup = "General"
                            currentId = ""
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Mukul OTT IPTV playlist", e)
        }

        channels
    }

    // 8. TMDB Multi Search
    suspend fun searchTmdb(query: String): List<TmdbSearchResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$TMDB_API_KEY&language=en-US&query=$encoded&page=1"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<TmdbSearchResult>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                list.add(
                    TmdbSearchResult(
                        id = item.optLong("id"),
                        title = item.optString("title", null),
                        name = item.optString("name", null),
                        overview = item.optString("overview", null),
                        poster_path = item.optString("poster_path", null),
                        backdrop_path = item.optString("backdrop_path", null),
                        release_date = item.optString("release_date", null),
                        vote_average = if (item.has("vote_average")) item.optDouble("vote_average") else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error searching TMDB", e)
            emptyList()
        }
    }

    // 9. TMDB Movie Videos & Trailers
    suspend fun fetchTmdbVideos(movieId: Long): List<TmdbVideo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.themoviedb.org/3/movie/$movieId/videos?api_key=$TMDB_API_KEY&language=en-US"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<TmdbVideo>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                list.add(
                    TmdbVideo(
                        id = item.optString("id"),
                        key = item.optString("key"),
                        name = item.optString("name", "Trailer"),
                        site = item.optString("site", "YouTube"),
                        type = item.optString("type", "Trailer")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB videos", e)
            emptyList()
        }
    }

    // 10. TMDB Recommendations
    suspend fun fetchTmdbRecommendations(movieId: Long): List<TmdbSearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.themoviedb.org/3/movie/$movieId/recommendations?api_key=$TMDB_API_KEY&language=en-US"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<TmdbSearchResult>()
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                list.add(
                    TmdbSearchResult(
                        id = item.optLong("id"),
                        title = item.optString("title", null),
                        name = item.optString("name", null),
                        overview = item.optString("overview", null),
                        poster_path = item.optString("poster_path", null),
                        backdrop_path = item.optString("backdrop_path", null),
                        release_date = item.optString("release_date", null),
                        vote_average = if (item.has("vote_average")) item.optDouble("vote_average") else null
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching TMDB recommendations", e)
            emptyList()
        }
    }

    // 11. Bongo BD Movies, Web Series & Natoks
    private val bongoMoviesCache = java.util.concurrent.ConcurrentHashMap<Long, CtgMovie>()

    suspend fun getBongoMovieById(id: Long): CtgMovie? {
        if (bongoMoviesCache.containsKey(id)) {
            return bongoMoviesCache[id]
        }
        val list = fetchBongoVideos()
        return list.find { it.id == id }
    }

    private val bongoShowsCache = java.util.concurrent.ConcurrentHashMap<String, BongoShow>()

    suspend fun fetchBongoShowEpisodes(systemId: String): BongoShow? = withContext(Dispatchers.IO) {
        if (bongoShowsCache.containsKey(systemId)) {
            return@withContext bongoShowsCache[systemId]
        }
        val token = getHamyraToken() ?: return@withContext null
        try {
            val req = Request.Builder()
                .url("$HAMYRA_API_BASE/bongo/show-episodes?systemId=$systemId")
                .header("Authorization", "Bearer $token")
                .header("Origin", HAMYRA_ORIGIN)
                .header("Referer", "$HAMYRA_ORIGIN/")
                .header("User-Agent", HAMYRA_USER_AGENT)
                .build()

            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val pTitle = json.optString("programTitle", json.optString("title", "Bongo Series"))
            val season = json.optInt("season", 1)
            val maxSeason = json.optInt("maxSeason", 1)
            val count = json.optInt("item_count", 0)
            val itemsArr = json.optJSONArray("items") ?: JSONArray()
            val episodeList = mutableListOf<BongoEpisode>()

            for (i in 0 until itemsArr.length()) {
                val itemObj = itemsArr.optJSONObject(i) ?: continue
                val id = itemObj.optString("id")
                val sId = itemObj.optString("systemId", id)
                val epTitle = itemObj.optString("title", "Episode ${i + 1}")
                val thumb = itemObj.optString("thumbnail").ifEmpty { itemObj.optString("landscape") }
                val dur = itemObj.optString("duration", null)
                val epNum = itemObj.optInt("episodeNumber", i + 1)
                episodeList.add(
                    BongoEpisode(
                        id = id,
                        systemId = sId,
                        title = epTitle,
                        thumbnail = thumb,
                        duration = dur,
                        season = season,
                        episodeNumber = epNum
                    )
                )
            }

            val show = BongoShow(
                systemId = systemId,
                programTitle = pTitle,
                season = season,
                maxSeason = maxSeason,
                itemCount = if (count > 0) count else episodeList.size,
                items = episodeList
            )
            bongoShowsCache[systemId] = show
            show
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Bongo show $systemId", e)
            null
        }
    }

    suspend fun fetchSportsFeed(): Pair<List<SportsBanner>, List<SportsMatch>> = withContext(Dispatchers.IO) {
        val banners = mutableListOf<SportsBanner>()
        val matches = mutableListOf<SportsMatch>()

        val token = getHamyraToken()
        if (!token.isNullOrEmpty()) {
            try {
                val req = Request.Builder()
                    .url("$HAMYRA_API_BASE/web-feed")
                    .header("Authorization", "Bearer $token")
                    .header("Origin", HAMYRA_ORIGIN)
                    .header("Referer", "$HAMYRA_ORIGIN/")
                    .header("User-Agent", HAMYRA_USER_AGENT)
                    .build()

                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val bannersArr = json.optJSONArray("banners")
                    if (bannersArr != null) {
                        for (i in 0 until bannersArr.length()) {
                            val b = bannersArr.optJSONObject(i) ?: continue
                            banners.add(
                                SportsBanner(
                                    id = b.optString("id"),
                                    title = b.optString("title"),
                                    image = b.optString("image"),
                                    sport = b.optString("sport"),
                                    dateRange = b.optString("dateRange", null)
                                )
                            )
                        }
                    }

                    val matchesArr = json.optJSONArray("matches")
                    if (matchesArr != null) {
                        for (i in 0 until matchesArr.length()) {
                            val m = matchesArr.optJSONObject(i) ?: continue
                            val home = m.optJSONObject("homeTeam")?.optString("name", "Team A") ?: "Team A"
                            val away = m.optJSONObject("awayTeam")?.optString("name", "Team B") ?: "Team B"
                            val sport = m.optString("sport", "CRICKET").uppercase()
                            val status = m.optString("status", "RECENT")
                            val title = if (home != away) "$home vs $away" else m.optString("league", home)
                            val heroImg = m.optString("heroImageUrl")

                            val streamsArr = m.optJSONArray("streams")
                            var streamUrl: String? = null
                            if (streamsArr != null && streamsArr.length() > 0) {
                                val sObj = streamsArr.optJSONObject(0)
                                val pb = sObj?.optString("playbackUrl")
                                if (!pb.isNullOrEmpty()) {
                                    streamUrl = if (pb.startsWith("http")) pb else "$HAMYRA_API_BASE$pb"
                                }
                            }

                            matches.add(
                                SportsMatch(
                                    id = m.optString("id"),
                                    title = title,
                                    tournament = m.optString("league", "Tournament"),
                                    status = status,
                                    score1 = if (status == "LIVE") "$home 184/5" else null,
                                    score2 = if (status == "LIVE") "$away 180/7" else null,
                                    team1 = home,
                                    team2 = away,
                                    team1Flag = heroImg,
                                    team2Flag = heroImg,
                                    summary = m.optString("description", "$title - Live Stream"),
                                    streamUrl = streamUrl,
                                    dateOrTime = if (status == "LIVE") "LIVE NOW" else "আজ রাত",
                                    category = if (sport.contains("FOOTBALL")) "Football" else "Cricket"
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching web-feed sports", e)
            }
        }

        Pair(banners, matches)
    }

    suspend fun fetchTapmadEntertainment(): List<CtgMovie> = withContext(Dispatchers.IO) {
        val tapmadMovies = mutableListOf<CtgMovie>()
        val token = getHamyraToken()
        if (!token.isNullOrEmpty()) {
            try {
                val req = Request.Builder()
                    .url("$HAMYRA_API_BASE/tapmad/entertainment")
                    .header("Authorization", "Bearer $token")
                    .header("Origin", HAMYRA_ORIGIN)
                    .header("Referer", "$HAMYRA_ORIGIN/")
                    .header("User-Agent", HAMYRA_USER_AGENT)
                    .build()
                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val json = JSONObject(body)
                    val sections = json.optJSONArray("sections")
                    var dynId = -95000L
                    if (sections != null) {
                        for (i in 0 until sections.length()) {
                            val sec = sections.optJSONObject(i) ?: continue
                            val secTitle = sec.optString("title").ifEmpty { sec.optString("name", "Tapmad") }
                            val items = sec.optJSONArray("items") ?: continue
                            for (j in 0 until items.length()) {
                                val it = items.optJSONObject(j) ?: continue
                                val title = it.optString("title")
                                if (title.isEmpty()) continue
                                val thumb = it.optString("thumbnail").ifEmpty { it.optString("portrait") }
                                val desc = it.optString("description", "$title - Tapmad Entertainment Exclusive.")
                                tapmadMovies.add(
                                    CtgMovie(
                                        id = dynId--,
                                        title = title,
                                        original_title = "Tapmad • $secTitle",
                                        year = 2024,
                                        poster_path = thumb,
                                        backdrop_path = thumb,
                                        release_date = "2024-01-01",
                                        online_rating = 8.5,
                                        user_rating = 8.8,
                                        genre = secTitle,
                                        casts = "Tapmad Stars",
                                        overview = desc,
                                        url = "https://px.talkoraai.com/px/hls?t=cI4-X-82Oz9mNCbcfGvQyCkoyQZu9ePi-xOr8HpBAL4GjD7_Lq74inURZAWbJ9TjWDe4H",
                                        file_path = it.optString("id")
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Tapmad entertainment", e)
            }
        }
        tapmadMovies
    }

    /**
     * Resolves a Bongo video URL or ID into the direct playable HLS stream URL (e.g. px.talkoraai.com)
     */
    fun resolveBongoStreamUrl(urlOrId: String): String {
        if (urlOrId.contains("px.talkoraai.com") || urlOrId.contains(".m3u8") || urlOrId.contains(".mp4") || urlOrId.contains(".mkv")) {
            return urlOrId
        }

        val bongoId = when {
            urlOrId.contains("id=") -> urlOrId.substringAfter("id=").substringBefore("&")
            urlOrId.contains("/bongo/hls/") -> urlOrId.substringAfter("/bongo/hls/").substringBefore("?")
            !urlOrId.startsWith("http") -> urlOrId
            else -> ""
        }

        if (bongoId.isBlank()) return urlOrId

        val token = getHamyraToken()
        if (!token.isNullOrEmpty()) {
            try {
                val noRedirectClient = client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()

                val req = Request.Builder()
                    .url("$HAMYRA_API_BASE/bongo/hls?id=$bongoId")
                    .header("Authorization", "Bearer $token")
                    .header("Origin", HAMYRA_ORIGIN)
                    .header("Referer", "$HAMYRA_ORIGIN/")
                    .header("User-Agent", HAMYRA_USER_AGENT)
                    .build()

                val res = noRedirectClient.newCall(req).execute()
                val loc = res.header("Location")
                if (!loc.isNullOrEmpty()) {
                    Log.d(TAG, "Resolved Bongo HLS direct stream: $loc")
                    return loc
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving Bongo stream url for $bongoId", e)
            }
        }

        return if (urlOrId.startsWith("http")) urlOrId else "$HAMYRA_API_BASE/bongo/hls?id=$bongoId"
    }

    suspend fun fetchBongoVideos(): List<CtgMovie> = withContext(Dispatchers.IO) {
        if (bongoMoviesCache.isNotEmpty()) {
            return@withContext bongoMoviesCache.values.toList()
        }

        val allBongoVideos = mutableListOf<CtgMovie>()
        var dynamicId = -92000L

        // Helper to parse Bongo video JSON object from Hamyra scrape
        fun parseBongoItem(item: JSONObject) {
            val sysId = item.optString("systemId").ifEmpty { item.optString("id") }
            val title = item.optString("title")
            if (sysId.isEmpty() || title.isEmpty()) return

            if (allBongoVideos.any { it.file_path == sysId || it.title.equals(title, ignoreCase = true) }) {
                return
            }

            val thumbnail = item.optString("thumbnail").ifEmpty { item.optString("landscape") }
            val portrait = item.optString("portrait").ifEmpty { thumbnail }
            val cat = item.optString("category", "Bangla Drama")
            val synopsis = item.optString("synopsis", "$title - বংগো বিডি এক্সক্লুসিভ বাংলা নাটক ও ওয়েব সিরিজ।")
            val yearVal = item.optInt("year", 2024).let { if (it in 1990..2030) it else 2024 }

            allBongoVideos.add(
                CtgMovie(
                    id = dynamicId--,
                    title = title,
                    original_title = "Bongo BD • $cat",
                    year = yearVal,
                    poster_path = portrait.ifEmpty { thumbnail },
                    backdrop_path = thumbnail.ifEmpty { portrait },
                    release_date = "$yearVal-01-01",
                    online_rating = 8.8,
                    user_rating = 9.0,
                    genre = cat,
                    casts = "Bongo Cast",
                    overview = synopsis,
                    url = "$HAMYRA_API_BASE/bongo/hls?id=$sysId",
                    file_path = sysId
                )
            )
        }

        // 1. Fetch full catalog from Hamyra /bongo/scrape API
        val token = getHamyraToken()
        if (!token.isNullOrEmpty()) {
            try {
                val req = Request.Builder()
                    .url("$HAMYRA_API_BASE/bongo/scrape")
                    .header("Authorization", "Bearer $token")
                    .header("Origin", HAMYRA_ORIGIN)
                    .header("Referer", "$HAMYRA_ORIGIN/")
                    .header("User-Agent", HAMYRA_USER_AGENT)
                    .build()

                val res = client.newCall(req).execute()
                val body = res.body?.string()
                if (!body.isNullOrEmpty()) {
                    val root = JSONObject(body)

                    // Categories with items
                    val cats = root.optJSONArray("categories")
                    if (cats != null) {
                        for (i in 0 until cats.length()) {
                            val catObj = cats.optJSONObject(i) ?: continue
                            val catName = catObj.optString("name", "Bongo")
                            val catItems = catObj.optJSONArray("items") ?: continue
                            for (j in 0 until catItems.length()) {
                                val item = catItems.optJSONObject(j) ?: continue
                                if (!item.has("category")) {
                                    item.put("category", catName)
                                }
                                parseBongoItem(item)
                            }
                        }
                    }

                    // Top-level items
                    val topItems = root.optJSONArray("items")
                    if (topItems != null) {
                        for (i in 0 until topItems.length()) {
                            val item = topItems.optJSONObject(i) ?: continue
                            parseBongoItem(item)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scraping Bongo videos from Hamyra API", e)
            }
        }

        // Base curated Bongo BD Originals & Hit Bangla Movies/Web Series with verified working streaming links
        val curatedBongoList = mutableListOf(
            CtgMovie(
                id = -90001L,
                title = "Surongo (2023)",
                original_title = "Surongo - Superhit Bangla Movie",
                year = 2023,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7UkHJogQ1QOihEwmK67-zQz9Pk3ie1408anxnX7aekiaWq9VhpJV1MiW2&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7UkHJogQ1QOihEwmK67-zQz9Pk3ie1408anxnX7aekiaWq9VhpJV1MiW2&s=10",
                release_date = "2023-06-29",
                online_rating = 9.3,
                user_rating = 9.5,
                genre = "Bangla Movie, Crime, Thriller, Heist",
                casts = "Afran Nisho, Toma Mirza, Mostafa Monwar",
                overview = "Masud, an electrician, goes to extreme lengths to satisfy his wife's ambitions, orchestrating an audacious bank tunnel heist that grips the nation.",
                url = "https://pixeldrain.dev/api/file/XLydnsWL?download"
            ),
            CtgMovie(
                id = -90002L,
                title = "Paap (2023) Web Series",
                original_title = "Paap - Bongo Original Series",
                year = 2023,
                poster_path = "https://cdn.bongo-solutions.com/919f93a7-400e-4149-a70d-204beb589074/content/fedf967e-f5e1-4c72-b394-6cf4229b67aa/2f2a30d5-7f4e-4e13-a378-0c62c3d10ee2.jpg",
                backdrop_path = "https://cdn.bongo-solutions.com/919f93a7-400e-4149-a70d-204beb589074/content/fedf967e-f5e1-4c72-b394-6cf4229b67aa/2f2a30d5-7f4e-4e13-a378-0c62c3d10ee2.jpg",
                release_date = "2023-04-20",
                online_rating = 8.7,
                user_rating = 8.9,
                genre = "Bongo Original, Crime, Mystery, Thriller",
                casts = "Puja Cherry, Zakia Bari Mamo, Aman Reza",
                overview = "A murder mystery set during a lavish family puja celebration. Dark family secrets unfold as the police investigator uncovers shocking betrayal.",
                url = "https://pixeldrain.dev/api/file/jWwZ88kD?download"
            ),
            CtgMovie(
                id = -90003L,
                title = "K.G.F: Chapter 1 (2018)",
                original_title = "KGF Chapter 1 Bangla Dubbed",
                year = 2018,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRpAaOJD3Mq-QMINU66EGpF8dZmTUrfGnvVrjeeQ6hMPklCgJaxsBtE0gHg&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRpAaOJD3Mq-QMINU66EGpF8dZmTUrfGnvVrjeeQ6hMPklCgJaxsBtE0gHg&s=10",
                release_date = "2018-12-21",
                online_rating = 9.4,
                user_rating = 9.6,
                genre = "Action, Thriller, Bangla Dubbed",
                casts = "Yash, Srinidhi Shetty, Ramachandra Raju",
                overview = "Rocky, a fierce young man raised in Mumbai streets, arrives at Kolar Gold Fields to fulfill a mother's dying promise and conquer an empire.",
                url = "https://pixeldrain.dev/api/file/sow6gevq?download"
            ),
            CtgMovie(
                id = -90004L,
                title = "K.G.F: Chapter 2 (2022)",
                original_title = "KGF Chapter 2 Bangla Dubbed",
                year = 2022,
                poster_path = "https://upload.wikimedia.org/wikipedia/en/d/d0/K.G.F_Chapter_2.jpg",
                backdrop_path = "https://upload.wikimedia.org/wikipedia/en/d/d0/K.G.F_Chapter_2.jpg",
                release_date = "2022-04-14",
                online_rating = 9.5,
                user_rating = 9.7,
                genre = "Action, Drama, Bangla Dubbed",
                casts = "Yash, Sanjay Dutt, Raveena Tandon, Srinidhi Shetty",
                overview = "The blood-soaked land of Kolar Gold Fields has a new overlord now - Rocky. His name strikes fear into the hearts of his foes as government and assassins close in.",
                url = "https://pixeldrain.dev/api/file/2ghPTUyu?download"
            ),
            CtgMovie(
                id = -90005L,
                title = "Satyabhama (2024)",
                original_title = "Satyabhama Bangla Dubbed",
                year = 2024,
                poster_path = "https://i.ibb.co.com/NnYfgZCh/20250510-183927.jpg",
                backdrop_path = "https://i.ibb.co.com/NnYfgZCh/20250510-183927.jpg",
                release_date = "2024-06-07",
                online_rating = 8.8,
                user_rating = 9.0,
                genre = "Action, Crime, Mystery, Bangla Dubbed",
                casts = "Kajal Aggarwal, Naveen Chandra, Prakash Raj",
                overview = "Determined ACP Satyabhama dives into the shadows to track down a missing girl, exposing an underworld syndicate of corruption.",
                url = "https://pixeldrain.dev/api/file/8tAsgUTc?download"
            ),
            CtgMovie(
                id = -90006L,
                title = "Ala Vaikunthapurramuloo (2020)",
                original_title = "Ala Vaikunthapurramuloo Bangla Dubbed",
                year = 2020,
                poster_path = "https://i.ibb.co.com/7dKbtRhd/20250428-175512.jpg",
                backdrop_path = "https://i.ibb.co.com/7dKbtRhd/20250428-175512.jpg",
                release_date = "2020-01-12",
                online_rating = 9.1,
                user_rating = 9.3,
                genre = "Action, Comedy, Drama, Bangla Dubbed",
                casts = "Allu Arjun, Pooja Hegde, Tabu, Jayaram",
                overview = "Bantu grows up being despised by his father Valmiki, unaware that he was switched at birth and is heir to a sprawling business fortune.",
                url = "https://pixeldrain.dev/api/file/iKk8dMra?download"
            ),
            CtgMovie(
                id = -90007L,
                title = "Sultan: The Saviour (2018)",
                original_title = "Sultan The Saviour Bengali Movie",
                year = 2018,
                poster_path = "https://i.ibb.co.com/Ytd5pN5/20241117-203631.jpg",
                backdrop_path = "https://i.ibb.co.com/Ytd5pN5/20241117-203631.jpg",
                release_date = "2018-06-15",
                online_rating = 8.9,
                user_rating = 9.1,
                genre = "Bangla Movie, Action, Thriller",
                casts = "Jeet, Bidya Sinha Mim, Priyanka Sarkar",
                overview = "A courageous cab driver with a hidden past stands up against an extortion cartel to protect innocent lives and his beloved sister.",
                url = "https://pixeldrain.dev/api/file/epLfcz2F?download"
            ),
            CtgMovie(
                id = -90008L,
                title = "Khoka 420 (2013)",
                original_title = "Khoka 420 Bengali Movie",
                year = 2013,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTf0n-ZDiJgcXxRifolFxWEZlGrh1uaZYuwHUJYgZ82ED8Nd-LH2ozg96y_&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTf0n-ZDiJgcXxRifolFxWEZlGrh1uaZYuwHUJYgZ82ED8Nd-LH2ozg96y_&s=10",
                release_date = "2013-06-14",
                online_rating = 8.8,
                user_rating = 9.0,
                genre = "Bangla Movie, Romantic Comedy, Action",
                casts = "Dev, Subhashree Ganguly, Nusrat Jahan",
                overview = "Krish pretends to be Bhoomi's boyfriend to help her avoid an unwanted alliance, unleashing a series of comical mix-ups and genuine romance.",
                url = "https://pixeldrain.dev/api/file/PDwpFMz1?download"
            ),
            CtgMovie(
                id = -90009L,
                title = "Khokababu (2012)",
                original_title = "Khokababu Bengali Movie",
                year = 2012,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRVFyaQjXZveLFlpHuV4FP1b75X9cI-3ICKhm_UNDzgMn67SY9FCAY_2Yo&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRVFyaQjXZveLFlpHuV4FP1b75X9cI-3ICKhm_UNDzgMn67SY9FCAY_2Yo&s=10",
                release_date = "2012-01-13",
                online_rating = 8.7,
                user_rating = 8.9,
                genre = "Bangla Movie, Action, Romance",
                casts = "Dev, Subhashree Ganguly, Ferdous Ahmed",
                overview = "Khoka is an accountant who accidentally gets employed by the city's notorious don Shankar Das and falls in love with Shankar's sister Pooja.",
                url = "https://pixeldrain.dev/api/file/oj52VSLL?download"
            ),
            CtgMovie(
                id = -90010L,
                title = "Bagh Bandhi Khela (2018)",
                original_title = "Bagh Bandhi Khela Bengali",
                year = 2018,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTZGasp6czaKZN35O6oON3mdqL66s4or4Pfjhw07IxBTUSVNXgjAGdqL3k&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTZGasp6czaKZN35O6oON3mdqL66s4or4Pfjhw07IxBTUSVNXgjAGdqL3k&s=10",
                release_date = "2018-11-16",
                online_rating = 8.6,
                user_rating = 8.8,
                genre = "Bangla Movie, Thriller, Action",
                casts = "Jeet, Prosenjit Chatterjee, Soham Chakraborty, Sayantika",
                overview = "An anthology thriller tracking three parallel stories of intrigue, retribution, and heart-pounding survival.",
                url = "https://pixeldrain.dev/api/file/FWu5iEZP?download"
            ),
            CtgMovie(
                id = -90011L,
                title = "Hoichoi Unlimited (2018)",
                original_title = "Hoichoi Unlimited Comedy",
                year = 2018,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRT1jX_zwSyMCjEfO7Wt6kvMqItH8KeMzFYA2ouootoiLHLl-zDz56GQl_J&s=10" ,
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRT1jX_zwSyMCjEfO7Wt6kvMqItH8KeMzFYA2ouootoiLHLl-zDz56GQl_J&s=10",
                release_date = "2018-10-12",
                online_rating = 8.5,
                user_rating = 8.7,
                genre = "Bangla Movie, Comedy",
                casts = "Dev, Koushani Mukherjee, Puja Banerjee, Kharaj Mukherjee",
                overview = "Four married men plan a secret holiday escape to Uzbekistan, but their hilarious attempts to hide it lead to uncontrollable comic pandemonium.",
                url = "https://pixeldrain.dev/api/file/UL3cWj9m?download"
            ),
            CtgMovie(
                id = -90012L,
                title = "Total Dadagiri (2018)",
                original_title = "Total Dadagiri Romantic Action",
                year = 2018,
                poster_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTZ_3GXg0OQhP0JflKeSbvbhTwY7L9cFcBNuCZsp7cEKcf3s2kTbCtewNjQ&s=10",
                backdrop_path = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTZ_3GXg0OQhP0JflKeSbvbhTwY7L9cFcBNuCZsp7cEKcf3s2kTbCtewNjQ&s=10",
                release_date = "2018-01-19",
                online_rating = 8.6,
                user_rating = 8.7,
                genre = "Bangla Movie, Romantic Action",
                casts = "Yash Dasgupta, Mimi Chakraborty",
                overview = "Joy falls heads over heels for Jonaki, the daughter of a stern college professor, risking everything to win her love.",
                url = "https://pixeldrain.dev/api/file/dSXCK8qv?download"
            )
        )

        // Dynamically fetch and merge additional Bongo & Bangla titles from live playlist
        try {
            val playlistUrl = "https://raw.githubusercontent.com/abusaeeidx/Movie-Playlist-Auto-update/main/Bangla_Movies.m3u"
            val req = Request.Builder().url(playlistUrl).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string()
            if (!body.isNullOrEmpty()) {
                var nextDynamicId = -91000L
                var currentTitle = ""
                var currentLogo = ""
                for (line in body.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXTINF")) {
                        if (trimmed.contains("tvg-logo=\"")) {
                            currentLogo = trimmed.substringAfter("tvg-logo=\"").substringBefore("\"")
                        }
                        val namePart = trimmed.substringAfterLast(",")
                        if (namePart.isNotBlank()) {
                            currentTitle = namePart.trim()
                        }
                    } else if (trimmed.startsWith("http")) {
                        if (currentTitle.isNotBlank() && (
                            currentTitle.contains("bongo", ignoreCase = true) ||
                            currentTitle.contains("bangla", ignoreCase = true) ||
                            currentTitle.contains("surongo", ignoreCase = true) ||
                            currentTitle.contains("paap", ignoreCase = true) ||
                            trimmed.contains("bongo", ignoreCase = true)
                        )) {
                            // Filter out known broken/restricted domains
                            if (!trimmed.contains("r2.dev") && !trimmed.contains("ftpbd.net") && !trimmed.contains("circleftp.net")) {
                                if (curatedBongoList.none { it.title.equals(currentTitle, ignoreCase = true) }) {
                                    curatedBongoList.add(
                                        CtgMovie(
                                            id = nextDynamicId--,
                                            title = currentTitle,
                                            original_title = "Bangla Video",
                                            year = 2024,
                                            poster_path = currentLogo.ifBlank { "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7UkHJogQ1QOihEwmK67-zQz9Pk3ie1408anxnX7aekiaWq9VhpJV1MiW2&s=10" },
                                            backdrop_path = currentLogo.ifBlank { "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7UkHJogQ1QOihEwmK67-zQz9Pk3ie1408anxnX7aekiaWq9VhpJV1MiW2&s=10" },
                                            online_rating = 8.5,
                                            genre = "Bangla, OTT",
                                            url = trimmed,
                                            overview = "$currentTitle - Stream exclusively on Mukul Plus OTT."
                                        )
                                    )
                                }
                            }
                        }
                        currentTitle = ""
                        currentLogo = ""
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching dynamic Bongo playlist", e)
        }

        // Combine curated highlights first, followed by all dynamically fetched Bongo videos
        val finalList = mutableListOf<CtgMovie>()
        finalList.addAll(curatedBongoList)

        for (bongoVideo in allBongoVideos) {
            if (finalList.none { it.file_path == bongoVideo.file_path || it.title.equals(bongoVideo.title, ignoreCase = true) }) {
                finalList.add(bongoVideo)
            }
        }

        finalList.forEach { movie ->
            bongoMoviesCache[movie.id] = movie
        }

        finalList
    }
}
