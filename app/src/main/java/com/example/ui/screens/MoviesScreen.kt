package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.ApiClient
import com.example.data.download.InAppDownloader
import com.example.data.model.*
import com.example.data.repository.MediaRepository
import com.example.data.repository.MukulOttRepository
import com.example.ui.components.FilterBottomSheet
import com.example.ui.components.FilterChipItem
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MoviesMainTab(val title: String, val badge: String) {
    MUKUL_OTT("মুকুল ওটিটি", "হিট"),
    BONGO_OTT("বঙ্গ ওটিটি", "সিরিজ"),
    BANGLA_OTT("বাংলা ওটিটি", "বাংলা"),
    ALL_MOVIES("সকল সিনেমা", "সিনেমা")
}

enum class MukulFilterType {
    ALL, MOVIES, SERIES, ANIME, DOWNLOADED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    mediaRepository: MediaRepository,
    onSelectMovie: (Long) -> Unit = {},
    initialTab: MoviesMainTab = MoviesMainTab.MUKUL_OTT,
    initialSlug: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Initialize in-app downloader
    LaunchedEffect(Unit) {
        InAppDownloader.init(context)
    }

    // Main Tab Selection (Mukul OTT / Bongo OTT / Bangla OTT / All Movies)
    var selectedMainTab by remember { mutableStateOf(initialTab) }

    // Update if initialTab changes from parent
    LaunchedEffect(initialTab) {
        selectedMainTab = initialTab
    }

    // Menus Data for Filter
    val menusData by mediaRepository.menusData.collectAsState()

    // ------------------------------------------------------------------------
    // MUKUL OTT STATES
    // ------------------------------------------------------------------------
    var mukulMovies by remember { mutableStateOf<List<MukulOttMovieItem>>(MukulOttRepository.cachedMovies) }
    var mukulMoviesList by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }
    var mukulAnimeList by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }
    var mukulSeriesList by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }
    var isLoadingCategory by remember { mutableStateOf(false) }

    var mukulCurrentPage by remember { mutableIntStateOf(MukulOttRepository.cachedPage) }
    val mukulTotalPages = 200
    var isLoadingMukulPage by remember { mutableStateOf(false) }
    var selectedMukulFilter by remember { mutableStateOf(MukulFilterType.ALL) }
    var showPageJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf(MukulOttRepository.cachedPage.toString()) }

    var mukulSearchQuery by remember { mutableStateOf("") }
    var isDeepSearching by remember { mutableStateOf(false) }
    var deepSearchResults by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Mukul Active Player & Episode State
    var selectedMukulMovieSlug by remember { mutableStateOf<String?>(initialSlug) }
    var selectedEpisodeIndex by remember(selectedMukulMovieSlug) { mutableIntStateOf(0) }
    var selectedMoviePreferredQuality by remember { mutableStateOf<String>("") }
    var mukulMovieDetail by remember { mutableStateOf<MukulOttMovieDetail?>(null) }
    var isLoadingMukulDetail by remember { mutableStateOf(false) }

    var mukulActivePlayUrl by remember { mutableStateOf<String?>(null) }
    var mukulActiveQualityLabel by remember { mutableStateOf<String>("") }
    var mukulActiveEpisodeLabel by remember { mutableStateOf<String>("মেইন ভিডিও") }

    var showMukulResolutionDropdown by remember { mutableStateOf(false) }
    var showMukulEpisodeDropdown by remember { mutableStateOf(false) }
    var isMukulDetailsExpanded by remember { mutableStateOf(false) }
    var mukulLotteryMovies by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }

    val mukulGridState = rememberLazyGridState()

    // ------------------------------------------------------------------------
    // BONGO & BANGLA OTT STATES
    // ------------------------------------------------------------------------
    var allBongoVideos by remember { mutableStateOf<List<CtgMovie>>(emptyList()) }
    var isLoadingBongo by remember { mutableStateOf(true) }
    var bongoSearchQuery by remember { mutableStateOf("") }
    var selectedBongoGenre by remember { mutableStateOf("সব (All)") }
    var activeBongoDetailMovie by remember { mutableStateOf<CtgMovie?>(null) }

    // ------------------------------------------------------------------------
    // ALL MOVIES (CTG / EXTERNAL CATALOG) STATES
    // ------------------------------------------------------------------------
    var allCinemaMovies by remember { mutableStateOf<List<CtgMovie>>(emptyList()) }
    var isLoadingAllCinema by remember { mutableStateOf(false) }
    var allCinemaPage by remember { mutableIntStateOf(1) }
    var allCinemaTotalPages by remember { mutableIntStateOf(1) }
    var allCinemaSearchQuery by remember { mutableStateOf("") }
    var allCinemaDebouncedQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<CtgCategoryItem?>(null) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var selectedSort by remember { mutableStateOf("createdAt") }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Download state observation
    val completedDownloads by InAppDownloader.completedDownloads.collectAsState()

    // Load Mukul Page helper
    fun loadMukulPage(page: Int) {
        val target = page.coerceIn(1, mukulTotalPages)
        mukulCurrentPage = target
        MukulOttRepository.cachedPage = target
        coroutineScope.launch {
            isLoadingMukulPage = true
            val loaded = MukulOttRepository.getPageOf50Movies(target)
            mukulMovies = loaded
            MukulOttRepository.cachedMovies = loaded
            MukulOttRepository.isLoaded = true
            isLoadingMukulPage = false
            mukulGridState.scrollToItem(0)
        }
    }

    // Initial Mukul Load
    LaunchedEffect(Unit) {
        if (mukulMovies.isEmpty()) {
            loadMukulPage(1)
        }
        mediaRepository.getMenus()
    }

    // Initial Bongo Catalog Load
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            isLoadingBongo = true
            try {
                val videos = mediaRepository.getBongoVideos()
                allBongoVideos = videos
            } catch (_: Exception) {
                try {
                    allBongoVideos = ApiClient.fetchBongoVideos()
                } catch (_: Exception) {}
            } finally {
                isLoadingBongo = false
            }
        }
    }

    // Mukul Fast Search
    LaunchedEffect(mukulSearchQuery) {
        val query = mukulSearchQuery.trim()
        if (query.isEmpty()) {
            isDeepSearching = false
            searchJob?.cancel()
            searchJob = null
            deepSearchResults = emptyList()
        } else {
            searchJob?.cancel()
            searchJob = coroutineScope.launch {
                delay(200)
                isDeepSearching = true
                val results = MukulOttRepository.searchMoviesFast(query)
                val localMatches = mukulMovies.filter { it.title.contains(query, ignoreCase = true) }
                val merged = (results + localMatches).distinctBy { it.slug }
                deepSearchResults = merged
                isDeepSearching = false
            }
        }
    }

    // Mukul Category List Loader
    LaunchedEffect(selectedMukulFilter) {
        when (selectedMukulFilter) {
            MukulFilterType.MOVIES -> {
                if (mukulMoviesList.isEmpty()) {
                    coroutineScope.launch {
                        isLoadingCategory = true
                        mukulMoviesList = MukulOttRepository.getMoviesList()
                        isLoadingCategory = false
                    }
                }
            }
            MukulFilterType.SERIES -> {
                if (mukulSeriesList.isEmpty()) {
                    coroutineScope.launch {
                        isLoadingCategory = true
                        mukulSeriesList = MukulOttRepository.getSeriesList()
                        isLoadingCategory = false
                    }
                }
            }
            MukulFilterType.ANIME -> {
                if (mukulAnimeList.isEmpty()) {
                    coroutineScope.launch {
                        isLoadingCategory = true
                        mukulAnimeList = MukulOttRepository.getAnimeList()
                        isLoadingCategory = false
                    }
                }
            }
            else -> {}
        }
    }

    // Mukul Movie Selection & Video Player Setup
    LaunchedEffect(selectedMukulMovieSlug) {
        val slug = selectedMukulMovieSlug
        if (slug != null) {
            isLoadingMukulDetail = true
            mukulMovieDetail = null
            isMukulDetailsExpanded = false
            val detail = MukulOttRepository.getMovieDetail(slug)
            mukulMovieDetail = detail
            isLoadingMukulDetail = false

            if (detail != null) {
                val targetQualityNum = selectedMoviePreferredQuality.filter { it.isDigit() }.toIntOrNull()
                    ?: detail.resolution.filter { it.isDigit() }.toIntOrNull()
                    ?: detail.quality.filter { it.isDigit() }.toIntOrNull()

                val matchedSource = if (targetQualityNum != null && detail.watchSources.isNotEmpty()) {
                    detail.watchSources.firstOrNull { it.quality == targetQualityNum }
                        ?: detail.watchSources.minByOrNull { kotlin.math.abs(it.quality - targetQualityNum) }
                } else {
                    detail.watchSources.firstOrNull()
                }

                if (matchedSource != null) {
                    val streamCandidate = matchedSource.proxyUrl.ifEmpty {
                        if (matchedSource.url.isNotEmpty()) "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(matchedSource.url, "UTF-8")
                        else matchedSource.directUrl.ifEmpty { matchedSource.downloadUrl }
                    }
                    mukulActivePlayUrl = streamCandidate
                    mukulActiveQualityLabel = "${matchedSource.quality}p"
                    mukulActiveEpisodeLabel = matchedSource.episode ?: "মেইন ভিডিও"
                } else if (detail.watchSources.isNotEmpty()) {
                    val firstSource = detail.watchSources.first()
                    val candidate = firstSource.proxyUrl.ifEmpty {
                        if (firstSource.url.isNotEmpty()) "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(firstSource.url, "UTF-8")
                        else firstSource.downloadUrl
                    }
                    mukulActivePlayUrl = candidate
                    mukulActiveQualityLabel = "${firstSource.quality}p"
                    mukulActiveEpisodeLabel = firstSource.episode ?: "মেইন ভিডিও"
                } else if (detail.watchUrl.isNotEmpty()) {
                    val finalWatch = if (detail.watchUrl.startsWith("http") && !detail.watchUrl.contains("stream-proxy") && detail.watchUrl.contains("fsldownload")) {
                        "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(detail.watchUrl, "UTF-8")
                    } else detail.watchUrl
                    mukulActivePlayUrl = finalWatch
                    mukulActiveQualityLabel = if (selectedMoviePreferredQuality.isNotEmpty()) selectedMoviePreferredQuality else detail.resolution.ifEmpty { "HD" }
                    mukulActiveEpisodeLabel = "মেইন ভিডিও"
                } else if (detail.episodes.isNotEmpty()) {
                    val ep1 = detail.episodes.first()
                    val epStream = ep1.sources.firstOrNull()?.let {
                        it.proxyUrl.ifEmpty {
                            if (it.url.isNotEmpty()) "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(it.url, "UTF-8") else ""
                        }
                    } ?: ep1.streamUrl
                    mukulActivePlayUrl = if (epStream.startsWith("http") && !epStream.contains("stream-proxy") && epStream.contains("fsldownload")) {
                        "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(epStream, "UTF-8")
                    } else epStream
                    mukulActiveQualityLabel = "HD"
                    mukulActiveEpisodeLabel = ep1.title
                }

                // Load lottery recommendations
                coroutineScope.launch {
                    mukulLotteryMovies = MukulOttRepository.getRandomLotteryMovies(15)
                }
            }
        }
    }

    // Load All Cinema Catalog
    LaunchedEffect(selectedMainTab, selectedCategory, selectedYear, selectedGenre, selectedSort, allCinemaPage, allCinemaDebouncedQuery) {
        if (selectedMainTab == MoviesMainTab.ALL_MOVIES) {
            isLoadingAllCinema = true
            val targetLibrary = if (allCinemaDebouncedQuery.isNotBlank() && selectedCategory == null) null else (selectedCategory?.id ?: 1)
            var res = ApiClient.fetchCtgMovies(
                library = targetLibrary,
                page = allCinemaPage,
                sort = selectedSort,
                sortOrder = "DESC",
                search = allCinemaDebouncedQuery.ifBlank { null },
                year = selectedYear,
                genre = selectedGenre
            )
            if (res.data.isEmpty() && allCinemaDebouncedQuery.isNotBlank() && targetLibrary != null) {
                res = ApiClient.fetchCtgMovies(
                    library = null,
                    page = allCinemaPage,
                    sort = selectedSort,
                    sortOrder = "DESC",
                    search = allCinemaDebouncedQuery.ifBlank { null },
                    year = selectedYear,
                    genre = selectedGenre
                )
            }
            allCinemaMovies = res.data
            allCinemaTotalPages = res.pages
            isLoadingAllCinema = false
        }
    }

    // ========================================================================
    // DETAIL PLAYER VIEW: MUKUL OTT SELECTED MOVIE
    // ========================================================================
    if (selectedMukulMovieSlug != null) {
        BackHandler {
            selectedMukulMovieSlug = null
            mukulMovieDetail = null
            mukulActivePlayUrl = null
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = CinemaBackground,
            topBar = {
                Surface(
                    color = CinemaSurface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(52.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            selectedMukulMovieSlug = null
                            mukulMovieDetail = null
                            mukulActivePlayUrl = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mukulMovieDetail?.title ?: "ভিডিও লোড হচ্ছে...",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "মুকুল ওটিটি প্লেয়ার • $mukulActiveQualityLabel • $mukulActiveEpisodeLabel",
                                color = CyanAccent,
                                fontSize = 10.5.sp
                            )
                        }

                        // Download shortcut
                        IconButton(onClick = {
                            if (!mukulActivePlayUrl.isNullOrEmpty() && mukulMovieDetail != null) {
                                InAppDownloader.startDownload(
                                    context = context,
                                    movieSlug = mukulMovieDetail!!.slug,
                                    title = "${mukulMovieDetail!!.title} ($mukulActiveQualityLabel)",
                                    poster = mukulMovieDetail!!.poster,
                                    quality = mukulActiveQualityLabel,
                                    downloadUrl = mukulActivePlayUrl!!
                                )
                                Toast.makeText(context, "${mukulMovieDetail!!.title} ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "ডাউনলোড লিঙ্ক প্রস্তুত হচ্ছে...", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Download", tint = CyanAccent, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 1. Top VLC Video Player
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        if (!mukulActivePlayUrl.isNullOrEmpty()) {
                            VideoPlayerView(
                                videoUrl = mukulActivePlayUrl!!,
                                title = mukulMovieDetail?.title ?: "মুকুল ওটিটি ভিডিও",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandRed)
                            }
                        }
                    }
                }

                // 2. Control Action Bar: [ফিরে যান, রেজুলেশন, এপিসোড, ডাউনলোড]
                item {
                    Surface(
                        color = CinemaSurfaceVariant,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back
                            OutlinedButton(
                                onClick = {
                                    selectedMukulMovieSlug = null
                                    mukulMovieDetail = null
                                    mukulActivePlayUrl = null
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("ফিরে যান", fontSize = 11.sp)
                            }

                            // Resolution Dropdown
                            val watchSources = mukulMovieDetail?.watchSources ?: emptyList()
                            if (watchSources.isNotEmpty()) {
                                Box {
                                    OutlinedButton(
                                        onClick = { showMukulResolutionDropdown = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.HighQuality, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("রেজুলেশন: $mukulActiveQualityLabel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }

                                    DropdownMenu(
                                        expanded = showMukulResolutionDropdown,
                                        onDismissRequest = { showMukulResolutionDropdown = false },
                                        modifier = Modifier.background(CinemaSurface)
                                    ) {
                                        watchSources.forEach { source ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "${source.quality}p ${if (source.qualityLabel.isNotEmpty()) "(${source.qualityLabel})" else ""}",
                                                        color = if (mukulActiveQualityLabel.startsWith("${source.quality}")) BrandRed else TextPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (mukulActiveQualityLabel.startsWith("${source.quality}")) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                onClick = {
                                                    val stream = source.proxyUrl.ifEmpty {
                                                        if (source.url.isNotEmpty()) "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(source.url, "UTF-8") else source.downloadUrl
                                                    }
                                                    mukulActivePlayUrl = stream
                                                    mukulActiveQualityLabel = "${source.quality}p"
                                                    showMukulResolutionDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Episode Dropdown (if series)
                            val episodes = mukulMovieDetail?.episodes ?: emptyList()
                            if (episodes.isNotEmpty()) {
                                Box {
                                    OutlinedButton(
                                        onClick = { showMukulEpisodeDropdown = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB020)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB020).copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(mukulActiveEpisodeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }

                                    DropdownMenu(
                                        expanded = showMukulEpisodeDropdown,
                                        onDismissRequest = { showMukulEpisodeDropdown = false },
                                        modifier = Modifier.background(CinemaSurface)
                                    ) {
                                        episodes.forEachIndexed { idx, ep ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "পর্ব ${idx + 1}: ${ep.title.ifEmpty { "Episode ${idx + 1}" }}",
                                                        color = if (mukulActiveEpisodeLabel == ep.title) BrandRed else TextPrimary,
                                                        fontSize = 12.sp
                                                    )
                                                },
                                                onClick = {
                                                    val epSource = ep.sources.firstOrNull()?.let {
                                                        it.proxyUrl.ifEmpty {
                                                            if (it.url.isNotEmpty()) "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(it.url, "UTF-8") else it.downloadUrl
                                                        }
                                                    } ?: ep.streamUrl
                                                    if (epSource.isNotEmpty()) {
                                                        mukulActivePlayUrl = epSource
                                                    }
                                                    mukulActiveEpisodeLabel = ep.title.ifEmpty { "পর্ব ${idx + 1}" }
                                                    selectedEpisodeIndex = idx
                                                    showMukulEpisodeDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Download Button
                            Button(
                                onClick = {
                                    if (!mukulActivePlayUrl.isNullOrEmpty() && mukulMovieDetail != null) {
                                        InAppDownloader.startDownload(
                                            context = context,
                                            movieSlug = mukulMovieDetail!!.slug,
                                            title = "${mukulMovieDetail!!.title} ($mukulActiveQualityLabel)",
                                            poster = mukulMovieDetail!!.poster,
                                            quality = mukulActiveQualityLabel,
                                            downloadUrl = mukulActivePlayUrl!!
                                        )
                                        Toast.makeText(context, "ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ডাউনলোড", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 3. Movie Title & Info
                mukulMovieDetail?.let { detail ->
                    item {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = detail.title,
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (detail.quality.isNotEmpty()) {
                                    Surface(color = BrandRed, shape = RoundedCornerShape(4.dp)) {
                                        Text(text = detail.quality, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                if (detail.genre.isNotEmpty()) {
                                    Text(text = detail.genre, color = CyanAccent, fontSize = 11.sp)
                                }
                                if (detail.language.isNotEmpty()) {
                                    Text(text = "• ${detail.language}", color = TextMuted, fontSize = 11.sp)
                                }
                            }

                            if (detail.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = detail.description,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // 4. Screenshots Gallery
                val screenshots = mukulMovieDetail?.screenshots ?: emptyList()
                if (screenshots.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            Text("স্ক্রিনশট গ্যালারি", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(screenshots) { ssUrl ->
                                    AsyncImage(
                                        model = ssUrl,
                                        contentDescription = "Screenshot",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(180.dp)
                                            .height(105.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Lottery Recommendations ("লটারি অনুযায়ী আরো ভিডিও")
                if (mukulLotteryMovies.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            Text(
                                text = "🎲 লটারি অনুযায়ী আরো ভিডিও (Recommended)",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(mukulLotteryMovies) { rec ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                        modifier = Modifier
                                            .width(115.dp)
                                            .clickable {
                                                selectedMukulMovieSlug = rec.slug
                                            }
                                    ) {
                                        Column {
                                            AsyncImage(
                                                model = rec.poster,
                                                contentDescription = rec.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(150.dp)
                                                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                            )
                                            Text(
                                                text = rec.title,
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ========================================================================
    // DETAIL PLAYER VIEW: BONGO / BANGLA OTT SELECTED MOVIE
    // ========================================================================
    if (activeBongoDetailMovie != null) {
        val currentMovie = activeBongoDetailMovie!!
        var bongoActiveStreamUrl by remember(currentMovie) { mutableStateOf(currentMovie.getFullStreamUrl() ?: "") }
        var bongoActiveEpLabel by remember(currentMovie) { mutableStateOf<String?>(null) }
        var bongoShowEpisodes by remember(currentMovie) { mutableStateOf<BongoShow?>(null) }

        LaunchedEffect(currentMovie) {
            var url = currentMovie.getFullStreamUrl() ?: ""
            if (url.isBlank() && currentMovie.id > 0) {
                try {
                    val detail = ApiClient.fetchCtgMovieDetail(currentMovie.id)
                    if (detail?.getFullStreamUrl()?.isNotBlank() == true) {
                        url = detail.getFullStreamUrl()!!
                    }
                } catch (_: Exception) {}
            }
            bongoActiveStreamUrl = url

            val titleLower = currentMovie.title.lowercase()
            val sysId = when {
                currentMovie.file_path?.length == 11 -> currentMovie.file_path
                titleLower.contains("bachelor point") -> "zvcly4FdFv0"
                titleLower.contains("salahuddin") || titleLower.contains("ayyubi") -> "dSH3So8VrJG"
                titleLower.contains("user not found") -> "3ScklzcngJy"
                titleLower.contains("she was pretty") || titleLower.contains("shundoritoma") -> "vdc0v0XXsTi"
                titleLower.contains("kimi wa pet") || titleLower.contains("adorer boyfriend") -> "3WTufg8lxDK"
                else -> null
            }
            if (!sysId.isNullOrEmpty()) {
                try {
                    val show = ApiClient.fetchBongoShowEpisodes(sysId)
                    bongoShowEpisodes = show
                } catch (_: Exception) {}
            } else {
                bongoShowEpisodes = null
            }
        }

        BackHandler {
            activeBongoDetailMovie = null
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = CinemaBackground,
            topBar = {
                Surface(
                    color = CinemaSurface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(52.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { activeBongoDetailMovie = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentMovie.title,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (bongoActiveEpLabel != null) "ওটিটি প্লেয়ার • $bongoActiveEpLabel" else "বঙ্গ ওটিটি প্লেয়ার • ${currentMovie.genre ?: "বাংলা কন্টেন্ট"}",
                                color = CyanAccent,
                                fontSize = 10.5.sp
                            )
                        }

                        IconButton(onClick = {
                            if (bongoActiveStreamUrl.isNotBlank()) {
                                InAppDownloader.startDownload(
                                    context = context,
                                    movieSlug = "movie_${currentMovie.id}",
                                    title = if (bongoActiveEpLabel != null) "${currentMovie.title} - $bongoActiveEpLabel" else currentMovie.title,
                                    poster = currentMovie.getFullPosterUrl(),
                                    quality = "Full HD",
                                    downloadUrl = bongoActiveStreamUrl
                                )
                                Toast.makeText(context, "${currentMovie.title} ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Download", tint = CyanAccent, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Video Player
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        if (bongoActiveStreamUrl.isNotBlank()) {
                            VideoPlayerView(
                                videoUrl = bongoActiveStreamUrl,
                                title = if (bongoActiveEpLabel != null) "${currentMovie.title} • $bongoActiveEpLabel" else currentMovie.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandRed)
                            }
                        }
                    }
                }

                // Show Episodes Rail (Bachelor Point, Sultan Salahuddin, etc.)
                if (bongoShowEpisodes?.items?.isNotEmpty() == true) {
                    item {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = "পর্বসমূহ (${bongoShowEpisodes!!.items.size} Episodes)",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(bongoShowEpisodes!!.items) { ep ->
                                    val isCurrentEp = bongoActiveEpLabel == ep.title
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = if (isCurrentEp) BrandRed.copy(alpha = 0.3f) else CinemaSurface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCurrentEp) BrandRed else CinemaBorder),
                                        modifier = Modifier
                                            .width(135.dp)
                                            .clickable {
                                                bongoActiveEpLabel = ep.title
                                                bongoActiveStreamUrl = ep.hlsUrl
                                            }
                                    ) {
                                        Column {
                                            AsyncImage(
                                                model = ep.thumbnail,
                                                contentDescription = ep.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(75.dp)
                                            )
                                            Text(
                                                text = ep.title,
                                                color = if (isCurrentEp) BrandRedLight else TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Overview & Casts
                item {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = currentMovie.title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = currentMovie.genre ?: "বাংলা ওটিটি এক্সক্লুসিভ", color = CyanAccent, fontSize = 12.sp)
                        if (!currentMovie.overview.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = currentMovie.overview, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        if (!currentMovie.casts.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "অভিনয়ে: ${currentMovie.casts}", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }

                // Related Bongo Videos
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "অন্যান্য ওটিটি ভিডিও",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        val related = allBongoVideos.filter { it.id != currentMovie.id }.take(15)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(related) { rel ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    modifier = Modifier
                                        .width(115.dp)
                                        .clickable { activeBongoDetailMovie = rel }
                                ) {
                                    Column {
                                        AsyncImage(
                                            model = rel.getFullPosterUrl(),
                                            contentDescription = rel.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                        )
                                        Text(
                                            text = rel.title,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ========================================================================
    // MAIN MOVIES & OTT SCREEN LAYOUT
    // ========================================================================
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        // TOP APP BAR / SEARCH & FILTER ROW
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Main Top Segmented Tabs: [মুকুল ওটিটি, বঙ্গ ওটিটি, বাংলা ওটিটি, সকল সিনেমা]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                }

                MoviesMainTab.values().forEach { tab ->
                    val isSelected = selectedMainTab == tab
                    Surface(
                        onClick = { selectedMainTab = tab },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BrandRed else CinemaBorder),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tab.title,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) Color.White.copy(alpha = 0.25f) else BrandRed.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = tab.badge,
                                    color = if (isSelected) Color.White else BrandRedLight,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-Search bar based on selected tab
            when (selectedMainTab) {
                MoviesMainTab.MUKUL_OTT -> {
                    OutlinedTextField(
                        value = mukulSearchQuery,
                        onValueChange = { mukulSearchQuery = it },
                        placeholder = { Text("মুকুল ওটিটি: মুভি, অ্যানিমে বা সিরিজ খুঁজুন...", color = TextMuted, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed) },
                        trailingIcon = {
                            if (mukulSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { mukulSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CinemaSurface,
                            unfocusedContainerColor = CinemaSurface,
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CinemaBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = BrandRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Mukul Category Chips: [সব, সিনেমা, সিরিজ, অ্যানিমে, ডাউনলোড]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChipItem(label = "🔥 সব (${mukulMovies.size})", selected = selectedMukulFilter == MukulFilterType.ALL, onClick = { selectedMukulFilter = MukulFilterType.ALL })
                        FilterChipItem(label = "🎬 সিনেমা", selected = selectedMukulFilter == MukulFilterType.MOVIES, onClick = { selectedMukulFilter = MukulFilterType.MOVIES })
                        FilterChipItem(label = "📺 সিরিজ", selected = selectedMukulFilter == MukulFilterType.SERIES, onClick = { selectedMukulFilter = MukulFilterType.SERIES })
                        FilterChipItem(label = "⚔️ অ্যানিমে", selected = selectedMukulFilter == MukulFilterType.ANIME, onClick = { selectedMukulFilter = MukulFilterType.ANIME })
                        FilterChipItem(label = "📥 ডাউনলোড (${completedDownloads.size})", selected = selectedMukulFilter == MukulFilterType.DOWNLOADED, onClick = { selectedMukulFilter = MukulFilterType.DOWNLOADED })
                    }
                }

                MoviesMainTab.BONGO_OTT, MoviesMainTab.BANGLA_OTT -> {
                    OutlinedTextField(
                        value = bongoSearchQuery,
                        onValueChange = { bongoSearchQuery = it },
                        placeholder = { Text("বঙ্গ ও বাংলা ওটিটি নাটক, সিরিজ বা মুভি খুঁজুন...", color = TextMuted, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed) },
                        trailingIcon = {
                            if (bongoSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { bongoSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CinemaSurface,
                            unfocusedContainerColor = CinemaSurface,
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CinemaBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = BrandRed
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Bongo Genres
                    val bongoGenres = listOf("সব (All)", "নাটক (Drama)", "ওয়েব সিরিজ (Series)", "সিনেমা (Movie)", "কমেডি (Comedy)", "থ্রিলার (Thriller)", "রোমান্স (Romance)", "অ্যাকশন (Action)")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        bongoGenres.forEach { g ->
                            FilterChipItem(
                                label = g,
                                selected = selectedBongoGenre == g,
                                onClick = { selectedBongoGenre = g }
                            )
                        }
                    }
                }

                MoviesMainTab.ALL_MOVIES -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = allCinemaSearchQuery,
                            onValueChange = {
                                allCinemaSearchQuery = it
                                allCinemaDebouncedQuery = it
                            },
                            placeholder = { Text("ইংরেজি, বলিউড ও সাউথ সিনেমা...", color = TextMuted, fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed) },
                            trailingIcon = {
                                if (allCinemaSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        allCinemaSearchQuery = ""
                                        allCinemaDebouncedQuery = ""
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CinemaSurface,
                                unfocusedContainerColor = CinemaSurface,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = CinemaBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = BrandRed
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            onClick = { showFilterSheet = true },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedCategory != null || selectedYear != null || selectedGenre != null) BrandRed else CinemaSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChipItem(label = "Hollywood (1)", selected = selectedCategory?.id == 1 || selectedCategory == null, onClick = { selectedCategory = CtgCategoryItem(1, "English Movies", "MOVIE") })
                        FilterChipItem(label = "Bollywood (4)", selected = selectedCategory?.id == 4, onClick = { selectedCategory = CtgCategoryItem(4, "Bollywood Movies", "MOVIE") })
                        FilterChipItem(label = "South Indian (7)", selected = selectedCategory?.id == 7, onClick = { selectedCategory = CtgCategoryItem(7, "South Indian Movies", "MOVIE") })
                        FilterChipItem(label = "Asian & Anime (5)", selected = selectedCategory?.id == 5, onClick = { selectedCategory = CtgCategoryItem(5, "Asian & Anime", "MOVIE") })
                        FilterChipItem(label = "Bangla Hall (6)", selected = selectedCategory?.id == 6, onClick = { selectedCategory = CtgCategoryItem(6, "Bangla Movies", "MOVIE") })
                    }
                }
            }
        }

        // ====================================================================
        // CONTENT AREA BASED ON TAB
        // ====================================================================
        Box(modifier = Modifier.weight(1f)) {
            when (selectedMainTab) {
                // 1. MUKUL OTT CATALOG VIEW
                MoviesMainTab.MUKUL_OTT -> {
                    val displayedMukulItems = remember(
                        selectedMukulFilter,
                        mukulMovies,
                        mukulMoviesList,
                        mukulSeriesList,
                        mukulAnimeList,
                        deepSearchResults,
                        mukulSearchQuery
                    ) {
                        if (mukulSearchQuery.isNotBlank()) {
                            deepSearchResults
                        } else {
                            when (selectedMukulFilter) {
                                MukulFilterType.ALL -> mukulMovies
                                MukulFilterType.MOVIES -> mukulMoviesList.ifEmpty { mukulMovies }
                                MukulFilterType.SERIES -> mukulSeriesList.ifEmpty { mukulMovies }
                                MukulFilterType.ANIME -> mukulAnimeList.ifEmpty { mukulMovies }
                                MukulFilterType.DOWNLOADED -> emptyList()
                            }
                        }
                    }

                    if (isLoadingMukulPage || isDeepSearching || isLoadingCategory) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = BrandRed)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("মুকুল ওটিটি কন্টেন্ট লোড হচ্ছে...", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    } else if (displayedMukulItems.isEmpty() && selectedMukulFilter != MukulFilterType.DOWNLOADED) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("কোনো ওটিটি ভিডিও পাওয়া যায়নি", color = TextSecondary, fontSize = 14.sp)
                        }
                    } else if (selectedMukulFilter == MukulFilterType.DOWNLOADED) {
                        // Downloaded list
                        if (completedDownloads.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("এখনো কোনো ভিডিও ডাউনলোড করা হয়নি", color = TextSecondary, fontSize = 14.sp)
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 115.dp),
                                contentPadding = PaddingValues(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(completedDownloads) { task ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                        modifier = Modifier.clickable {
                                            if (task.filePath.isNotEmpty()) {
                                                mukulActivePlayUrl = task.filePath
                                                selectedMukulMovieSlug = task.movieSlug
                                            }
                                        }
                                    ) {
                                        Column {
                                            AsyncImage(
                                                model = task.poster,
                                                contentDescription = task.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(160.dp)
                                            )
                                            Text(
                                                text = task.title,
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = mukulGridState,
                                columns = GridCells.Adaptive(minSize = 115.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(displayedMukulItems, key = { it.slug }) { item ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedMoviePreferredQuality = item.qualityTag
                                                selectedMukulMovieSlug = item.slug
                                            }
                                    ) {
                                        Column {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(165.dp)
                                                    .background(CinemaSurfaceVariant)
                                            ) {
                                                AsyncImage(
                                                    model = item.poster,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                // Play overlay badge
                                                Surface(
                                                    shape = CircleShape,
                                                    color = BrandRed.copy(alpha = 0.85f),
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .align(Alignment.Center)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                                                    }
                                                }

                                                // Quality Tag (1080p / 720p)
                                                if (item.qualityTag.isNotEmpty()) {
                                                    Surface(
                                                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                        color = BrandRed,
                                                        modifier = Modifier.align(Alignment.TopStart)
                                                    ) {
                                                        Text(
                                                            text = item.qualityTag,
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                // Year
                                                if (item.year > 0) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color.Black.copy(alpha = 0.7f),
                                                        modifier = Modifier
                                                            .align(Alignment.BottomEnd)
                                                            .padding(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "${item.year}",
                                                            color = Color(0xFFFFB020),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = item.title,
                                                color = TextPrimary,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Bottom Pagination Bar for Mukul OTT
                            if (mukulSearchQuery.isBlank() && selectedMukulFilter == MukulFilterType.ALL) {
                                Surface(
                                    color = CinemaSurface,
                                    tonalElevation = 4.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Previous Page
                                        OutlinedButton(
                                            onClick = { if (mukulCurrentPage > 1) loadMukulPage(mukulCurrentPage - 1) },
                                            enabled = mukulCurrentPage > 1,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("আগের পেজ", fontSize = 11.sp)
                                        }

                                        // Current Page & Jump Button
                                        Surface(
                                            onClick = {
                                                jumpPageInput = mukulCurrentPage.toString()
                                                showPageJumpDialog = true
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = CinemaSurfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "পৃষ্ঠা $mukulCurrentPage / $mukulTotalPages",
                                                    color = CyanAccent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(Icons.Default.Edit, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                            }
                                        }

                                        // Next Page
                                        Button(
                                            onClick = { if (mukulCurrentPage < mukulTotalPages) loadMukulPage(mukulCurrentPage + 1) },
                                            enabled = mukulCurrentPage < mukulTotalPages,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("পরের পেজ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. BONGO OTT & BANGLA OTT CATALOG VIEW
                MoviesMainTab.BONGO_OTT, MoviesMainTab.BANGLA_OTT -> {
                    val filteredBongo = remember(allBongoVideos, bongoSearchQuery, selectedBongoGenre) {
                        var list = allBongoVideos
                        if (selectedBongoGenre != "সব (All)") {
                            val gLower = selectedBongoGenre.lowercase()
                            list = list.filter {
                                val gf = it.genre?.lowercase() ?: ""
                                val tf = it.title.lowercase()
                                val of = it.original_title?.lowercase() ?: ""
                                when {
                                    gLower.contains("সিনেমা") || gLower.contains("movie") -> gf.contains("movie") || tf.contains("movie") || of.contains("movie")
                                    gLower.contains("নাটক") || gLower.contains("drama") -> gf.contains("drama") || tf.contains("নাটক")
                                    gLower.contains("সিরিজ") || gLower.contains("series") -> gf.contains("series") || tf.contains("series")
                                    gLower.contains("কমেডি") || gLower.contains("comedy") -> gf.contains("comedy")
                                    gLower.contains("থ্রিলার") || gLower.contains("thriller") -> gf.contains("thriller") || gf.contains("crime")
                                    gLower.contains("রোমান্স") || gLower.contains("romance") -> gf.contains("romance")
                                    gLower.contains("অ্যাকশন") || gLower.contains("action") -> gf.contains("action")
                                    else -> gf.contains(gLower) || tf.contains(gLower)
                                }
                            }
                        }
                        if (bongoSearchQuery.isNotBlank()) {
                            val q = bongoSearchQuery.trim()
                            list = list.filter {
                                it.title.contains(q, ignoreCase = true) ||
                                (it.casts?.contains(q, ignoreCase = true) == true) ||
                                (it.genre?.contains(q, ignoreCase = true) == true)
                            }
                        }
                        list
                    }

                    if (isLoadingBongo) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrandRed)
                        }
                    } else if (filteredBongo.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("কোনো বঙ্গ ভিডিও পাওয়া যায়নি", color = TextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 115.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredBongo, key = { it.id }) { movie ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            activeBongoDetailMovie = movie
                                        }
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(165.dp)
                                                .background(CinemaSurfaceVariant)
                                        ) {
                                            AsyncImage(
                                                model = movie.getFullPosterUrl(),
                                                contentDescription = movie.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            Surface(
                                                shape = CircleShape,
                                                color = BrandRed.copy(alpha = 0.85f),
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .align(Alignment.Center)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                color = Color(0xFFE50914),
                                                modifier = Modifier.align(Alignment.TopStart)
                                            ) {
                                                Text(
                                                    text = "BONGO",
                                                    color = Color.White,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = movie.title,
                                            color = TextPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. ALL MOVIES (HOLLYWOOD / BOLLYWOOD / SOUTH)
                MoviesMainTab.ALL_MOVIES -> {
                    if (isLoadingAllCinema) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = BrandRed)
                        }
                    } else if (allCinemaMovies.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("কোনো সিনেমা পাওয়া যায়নি", color = TextSecondary, fontSize = 14.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 115.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(allCinemaMovies, key = { it.id }) { movie ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            activeBongoDetailMovie = movie
                                        }
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(165.dp)
                                                .background(CinemaSurfaceVariant)
                                        ) {
                                            AsyncImage(
                                                model = movie.getFullPosterUrl(),
                                                contentDescription = movie.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            Surface(
                                                shape = CircleShape,
                                                color = BrandRed.copy(alpha = 0.85f),
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .align(Alignment.Center)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }

                                        Text(
                                            text = movie.title,
                                            color = TextPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Page Jump Dialog for Mukul OTT
    if (showPageJumpDialog) {
        AlertDialog(
            onDismissRequest = { showPageJumpDialog = false },
            containerColor = CinemaSurface,
            title = {
                Text("পেজ নম্বর নির্বাচন করুন (১ - $mukulTotalPages)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = jumpPageInput,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 4) {
                            jumpPageInput = input
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val p = jumpPageInput.toIntOrNull()
                        if (p != null && p in 1..mukulTotalPages) {
                            loadMukulPage(p)
                            showPageJumpDialog = false
                        }
                    }),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CinemaSurfaceVariant,
                        unfocusedContainerColor = CinemaSurfaceVariant,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CinemaBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = jumpPageInput.toIntOrNull()
                        if (p != null && p in 1..mukulTotalPages) {
                            loadMukulPage(p)
                            showPageJumpDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("যাও", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPageJumpDialog = false }) {
                    Text("বাতিল", color = TextMuted)
                }
            }
        )
    }

    // Bottom Filter Sheet for All Cinema
    if (showFilterSheet) {
        FilterBottomSheet(
            menusData = menusData ?: CtgMenusData(),
            selectedCategory = selectedCategory,
            selectedYear = selectedYear,
            selectedGenre = selectedGenre,
            selectedSort = selectedSort,
            onCategoryChange = { selectedCategory = it },
            onYearChange = { selectedYear = it },
            onGenreChange = { selectedGenre = it },
            onSortChange = { selectedSort = it },
            onApply = {
                showFilterSheet = false
                allCinemaPage = 1
            },
            onReset = {
                selectedCategory = null
                selectedYear = null
                selectedGenre = null
                selectedSort = "createdAt"
                showFilterSheet = false
                allCinemaPage = 1
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}
