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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MoviesMainTab(val title: String) {
    MUKUL_OTT("Mukul OTT"),
    BONGO_OTT("বংগ (Bongo)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesScreen(
    mediaRepository: MediaRepository,
    onSelectMovie: (Long) -> Unit = {},
    initialTab: MoviesMainTab = MoviesMainTab.MUKUL_OTT,
    initialSlug: String? = null,
    onTabChanged: ((MoviesMainTab) -> Unit)? = null,
    onNavigateToDownloads: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Two Main Sections: Mukul OTT & Bongo
    var selectedSection by remember { mutableStateOf(initialTab) }

    LaunchedEffect(initialTab) {
        selectedSection = initialTab
    }

    // Search bar state
    var searchQuery by remember { mutableStateOf("") }

    // ------------------------------------------------------------------------
    // MUKUL OTT STATES
    // ------------------------------------------------------------------------
    var mukulCurrentPage by remember { mutableIntStateOf(MukulOttRepository.cachedPage) }
    var mukulMoviesList by remember { mutableStateOf<List<MukulOttMovieItem>>(MukulOttRepository.cachedMovies) }
    var isLoadingMukul by remember { mutableStateOf(mukulMoviesList.isEmpty()) }
    var selectedMukulCategory by remember { mutableStateOf("সব") }

    // Mukul Player Sub-Page State
    var activeMukulSlug by remember { mutableStateOf<String?>(initialSlug) }
    var activeMukulDetail by remember { mutableStateOf<MukulOttMovieDetail?>(null) }
    var isLoadingMukulDetail by remember { mutableStateOf(false) }
    var activeMukulPlayUrl by remember { mutableStateOf<String?>(null) }
    var activeMukulResolution by remember { mutableStateOf("১০৮০p") }
    var activeMukulEpisodeTitle by remember { mutableStateOf("মেইন ভিডিও") }
    var showMukulResDropdown by remember { mutableStateOf(false) }
    var isMukulInfoExpanded by remember { mutableStateOf(false) }
    var mukulLotteryMovies by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }

    // ------------------------------------------------------------------------
    // BONGO OTT STATES
    // ------------------------------------------------------------------------
    var allBongoVideos by remember { mutableStateOf<List<CtgMovie>>(emptyList()) }
    var bongoCurrentPage by remember { mutableIntStateOf(1) }
    var isLoadingBongo by remember { mutableStateOf(true) }
    var selectedBongoCategory by remember { mutableStateOf("সব") }

    // Bongo Player Sub-Page State
    var activeBongoMovie by remember { mutableStateOf<CtgMovie?>(null) }
    var activeBongoStreamUrl by remember { mutableStateOf<String?>(null) }
    var activeBongoResolution by remember { mutableStateOf("১০৮০p") }
    var activeBongoShowEpisodes by remember { mutableStateOf<List<BongoEpisode>>(emptyList()) }
    var activeBongoEpisodeTitle by remember { mutableStateOf("মেইন ভিডিও") }
    var showBongoResDropdown by remember { mutableStateOf(false) }
    var isBongoInfoExpanded by remember { mutableStateOf(false) }
    var bongoLotteryMovies by remember { mutableStateOf<List<CtgMovie>>(emptyList()) }

    // Fetch Mukul OTT 50 movies
    fun loadMukulPage(page: Int) {
        coroutineScope.launch {
            isLoadingMukul = true
            try {
                val list = MukulOttRepository.getPageOf50Movies(page)
                mukulMoviesList = list
                MukulOttRepository.cachedMovies = list
                MukulOttRepository.cachedPage = page
                MukulOttRepository.isLoaded = true
            } catch (_: Exception) {
            } finally {
                isLoadingMukul = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (mukulMoviesList.isEmpty()) {
            loadMukulPage(mukulCurrentPage)
        }
        // Fetch Bongo videos
        isLoadingBongo = true
        try {
            val bongo = mediaRepository.getBongoVideos()
            allBongoVideos = if (bongo.isNotEmpty()) bongo else {
                val res = ApiClient.fetchCtgMovies(library = 1, page = 1, sort = "createdAt")
                res.data
            }
        } catch (_: Exception) {
        } finally {
            isLoadingBongo = false
        }
    }

    // Handle initialSlug
    LaunchedEffect(initialSlug) {
        if (!initialSlug.isNullOrEmpty()) {
            activeMukulSlug = initialSlug
        }
    }

    // Load Mukul Movie Detail when activeMukulSlug changes
    LaunchedEffect(activeMukulSlug) {
        val slug = activeMukulSlug
        if (slug != null) {
            isLoadingMukulDetail = true
            isMukulInfoExpanded = false
            try {
                val detail = MukulOttRepository.getMovieDetail(slug)
                activeMukulDetail = detail

                // Determine stream URL & resolution
                val firstSource = detail?.watchSources?.firstOrNull()
                if (firstSource != null) {
                    val streamCandidate = firstSource.proxyUrl.ifEmpty {
                        if (firstSource.url.isNotEmpty()) "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(firstSource.url, "UTF-8") else firstSource.downloadUrl
                    }
                    activeMukulPlayUrl = streamCandidate
                    activeMukulResolution = "${firstSource.quality}p"
                    activeMukulEpisodeTitle = firstSource.episode ?: "মেইন ভিডিও"
                } else if (detail != null && detail.watchUrl.isNotEmpty()) {
                    val finalUrl = if (detail.watchUrl.startsWith("http") && !detail.watchUrl.contains("stream-proxy") && detail.watchUrl.contains("fsldownload")) {
                        "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(detail.watchUrl, "UTF-8")
                    } else detail.watchUrl
                    activeMukulPlayUrl = finalUrl
                    activeMukulResolution = detail.resolution.ifEmpty { "১০৮০p" }
                    activeMukulEpisodeTitle = "মেইন ভিডিও"
                } else if (detail != null && detail.episodes.isNotEmpty()) {
                    val ep1 = detail.episodes.first()
                    val epStream = ep1.sources.firstOrNull()?.let {
                        it.proxyUrl.ifEmpty {
                            if (it.url.isNotEmpty()) "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(it.url, "UTF-8") else ""
                        }
                    } ?: ep1.streamUrl
                    activeMukulPlayUrl = epStream
                    activeMukulResolution = "১০৮০p"
                    activeMukulEpisodeTitle = ep1.title
                }

                // Lottery movies
                mukulLotteryMovies = MukulOttRepository.getRandomLotteryMovies(15)
            } catch (_: Exception) {
            } finally {
                isLoadingMukulDetail = false
            }
        }
    }

    // Load Bongo Movie Detail when activeBongoMovie changes
    LaunchedEffect(activeBongoMovie) {
        val movie = activeBongoMovie
        if (movie != null) {
            isBongoInfoExpanded = false
            activeBongoResolution = "১০৮০p"
            activeBongoEpisodeTitle = "মেইন ভিডিও"
            activeBongoShowEpisodes = emptyList()

            val rawStream = movie.getFullStreamUrl() ?: ""
            if (rawStream.isNotEmpty()) {
                if (rawStream.contains("bongo/hls") || (rawStream.contains("hamyra-api") && rawStream.contains("id="))) {
                    ApiClient.ensureHamyraToken()
                    activeBongoStreamUrl = ApiClient.resolveBongoStreamUrl(rawStream)
                } else {
                    activeBongoStreamUrl = rawStream
                }
            }

            // Detect Bongo show episodes
            val titleLower = movie.title.lowercase()
            val bongoSysId = when {
                movie.file_path?.length == 11 -> movie.file_path
                titleLower.contains("bachelor point") -> "zvcly4FdFv0"
                titleLower.contains("salahuddin") || titleLower.contains("ayyubi") -> "dSH3So8VrJG"
                titleLower.contains("user not found") -> "3ScklzcngJy"
                titleLower.contains("she was pretty") || titleLower.contains("shundoritoma") -> "vdc0v0XXsTi"
                titleLower.contains("kimi wa pet") -> "3WTufg8lxDK"
                else -> null
            }
            if (!bongoSysId.isNullOrEmpty()) {
                try {
                    val show = ApiClient.fetchBongoShowEpisodes(bongoSysId)
                    if (show != null && show.items.isNotEmpty()) {
                        activeBongoShowEpisodes = show.items
                        ApiClient.ensureHamyraToken()
                        activeBongoStreamUrl = ApiClient.resolveBongoStreamUrl(show.items.first().hlsUrl)
                        activeBongoEpisodeTitle = show.items.first().title
                    }
                } catch (_: Exception) {}
            }

            bongoLotteryMovies = allBongoVideos.shuffled().take(15)
        }
    }

    // ========================================================================
    // SUB-PAGE: MUKUL OTT VIDEO PLAYER PAGE
    // ========================================================================
    if (activeMukulSlug != null) {
        BackHandler {
            activeMukulSlug = null
            activeMukulDetail = null
            activeMukulPlayUrl = null
        }

        Scaffold(
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
                            .height(54.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ফিরে আসা বাটন
                        IconButton(onClick = {
                            activeMukulSlug = null
                            activeMukulDetail = null
                            activeMukulPlayUrl = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }

                        // Title
                        Text(
                            text = activeMukulDetail?.title ?: "ভিডিও প্লেয়ার",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        // ডাউনলোড পেজে যাওয়ার বাটন
                        IconButton(onClick = onNavigateToDownloads) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Downloads", tint = CyanAccent)
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                // ১. ভিডিও প্লেয়ার
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        if (!activeMukulPlayUrl.isNullOrEmpty()) {
                            VideoPlayerView(
                                videoUrl = activeMukulPlayUrl!!,
                                title = activeMukulDetail?.title ?: "Mukul OTT",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandRed)
                            }
                        }
                    }
                }

                // ২. একলাইনে চিকন সরু লাইনে: রেজুলেশন সিলেক্ট বাটন ও ডাউনলোড বাটন
                item {
                    Surface(
                        color = CinemaSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // রেজুলেশন বাটন
                            val watchSources = activeMukulDetail?.watchSources ?: emptyList()
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { showMukulResDropdown = true },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CinemaSurface,
                                        contentColor = CyanAccent
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                ) {
                                    Icon(Icons.Default.HighQuality, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রেজুলেশন: $activeMukulResolution", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp))
                                }

                                DropdownMenu(
                                    expanded = showMukulResDropdown,
                                    onDismissRequest = { showMukulResDropdown = false },
                                    modifier = Modifier.background(CinemaSurface)
                                ) {
                                    if (watchSources.isNotEmpty()) {
                                        watchSources.forEach { source ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "${source.quality}p ${if (source.qualityLabel.isNotEmpty()) "(${source.qualityLabel})" else ""}",
                                                        color = if (activeMukulResolution.startsWith("${source.quality}")) BrandRed else TextPrimary,
                                                        fontSize = 11.5.sp
                                                    )
                                                },
                                                onClick = {
                                                    val candidate = source.proxyUrl.ifEmpty {
                                                        if (source.url.isNotEmpty()) "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(source.url, "UTF-8") else source.downloadUrl
                                                    }
                                                    activeMukulPlayUrl = candidate
                                                    activeMukulResolution = "${source.quality}p"
                                                    showMukulResDropdown = false
                                                }
                                            )
                                        }
                                    } else {
                                        listOf("১০৮০p", "৭২০p", "৪৮০p", "৩৬০p").forEach { res ->
                                            DropdownMenuItem(
                                                text = { Text(res, color = TextPrimary, fontSize = 11.5.sp) },
                                                onClick = {
                                                    activeMukulResolution = res
                                                    showMukulResDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // ডাউনলোড বাটন (রেজুলেশন সিলেক্ট অনুযায়ী)
                            Button(
                                onClick = {
                                    if (!activeMukulPlayUrl.isNullOrEmpty() && activeMukulDetail != null) {
                                        InAppDownloader.startDownload(
                                            context = context,
                                            movieSlug = activeMukulDetail!!.slug,
                                            title = "${activeMukulDetail!!.title} ($activeMukulResolution)",
                                            poster = activeMukulDetail!!.poster,
                                            quality = activeMukulResolution,
                                            downloadUrl = activeMukulPlayUrl!!
                                        )
                                        Toast.makeText(context, "${activeMukulDetail!!.title} ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "ডাউনলোড লিংক তৈরি হচ্ছে...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ডাউনলোড", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ৩. হরিজনটাল এপিসড চিকন সরু লাইন (যদি এপিসড থাকে তাহলে দেখাবে তাছাড়া দেখাবে না)
                val episodes = activeMukulDetail?.episodes ?: emptyList()
                if (episodes.isNotEmpty()) {
                    item {
                        Surface(
                            color = CinemaSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(episodes.size) { idx ->
                                    val ep = episodes[idx]
                                    val isSelected = activeMukulEpisodeTitle == ep.title
                                    Surface(
                                        onClick = {
                                            val epSource = ep.sources.firstOrNull()?.let {
                                                it.proxyUrl.ifEmpty {
                                                    if (it.url.isNotEmpty()) "https://mukul-ott.ai.studio/api/stream-proxy?url=" + java.net.URLEncoder.encode(it.url, "UTF-8") else it.downloadUrl
                                                }
                                            } ?: ep.streamUrl
                                            if (epSource.isNotEmpty()) {
                                                activeMukulPlayUrl = epSource
                                            }
                                            activeMukulEpisodeTitle = ep.title.ifEmpty { "পর্ব ${idx + 1}" }
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isSelected) BrandRed else CinemaBorder),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = if (ep.title.isNotEmpty()) ep.title else "পর্ব ${idx + 1}",
                                                color = if (isSelected) Color.White else TextPrimary,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ৪. চিকন সরু লাইনে তথ্য: বাটনের ভিতরে হিডেন থাকবে, বাটনে ক্লিক করলে তথ্য ও স্ক্রিনশট দেখাবে
                item {
                    Surface(
                        color = CinemaSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isMukulInfoExpanded = !isMukulInfoExpanded }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "তথ্য ও স্ক্রিনশট (ক্লিক করে দেখুন)",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = if (isMukulInfoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (isMukulInfoExpanded) {
                                activeMukulDetail?.let { detail ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = detail.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (detail.quality.isNotEmpty()) Text(detail.quality, color = Color(0xFFFFB020), fontSize = 10.5.sp)
                                            if (detail.genre.isNotEmpty()) Text("• ${detail.genre}", color = CyanAccent, fontSize = 10.5.sp)
                                            if (detail.language.isNotEmpty()) Text("• ${detail.language}", color = TextMuted, fontSize = 10.5.sp)
                                        }
                                        if (detail.description.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = detail.description,
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        // Screenshots
                                        val screenshots = detail.screenshots
                                        if (screenshots.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("স্ক্রিনশট:", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(screenshots) { ssUrl ->
                                                    AsyncImage(
                                                        model = ssUrl,
                                                        contentDescription = "Screenshot",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .width(140.dp)
                                                            .height(80.dp)
                                                            .clip(RoundedCornerShape(4.dp))
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

                // ৫. হরিজনটাল স্ক্রল অনুযায়ী লটারি ভিডিও কার্ড
                if (mukulLotteryMovies.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = "লটারি ভিডিও কার্ড (অন্যান্য ভিডিও)",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(mukulLotteryMovies) { lotMovie ->
                                    PurePosterCard(
                                        imageUrl = lotMovie.poster,
                                        contentDescription = lotMovie.title,
                                        onClick = {
                                            activeMukulSlug = lotMovie.slug
                                        },
                                        width = 95,
                                        height = 135
                                    )
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
    // SUB-PAGE: BONGO VIDEO PLAYER PAGE
    // (বংগ ভিডিও তো ডাউনলোড করা যাবে না তাই বংগ ভিডিও প্লে হওয়ার সময় ডাউনলোড বাটন দেখাবে না)
    // ========================================================================
    if (activeBongoMovie != null) {
        val bMovie = activeBongoMovie!!
        BackHandler {
            activeBongoMovie = null
            activeBongoStreamUrl = null
        }

        Scaffold(
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
                            .height(54.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ফিরে আসা বাটন
                        IconButton(onClick = {
                            activeBongoMovie = null
                            activeBongoStreamUrl = null
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }

                        // Title
                        Text(
                            text = bMovie.title,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        // ডাউনলোড পেজে যাওয়ার বাটন
                        IconButton(onClick = onNavigateToDownloads) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Downloads", tint = CyanAccent)
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                // ১. ভিডিও প্লেয়ার
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        if (!activeBongoStreamUrl.isNullOrEmpty()) {
                            VideoPlayerView(
                                videoUrl = activeBongoStreamUrl!!,
                                title = bMovie.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandRed)
                            }
                        }
                    }
                }

                // ২. একলাইনে চিকন সরু লাইনে: রেজুলেশন সিলেক্ট বাটন (বংগ ভিডিওতে ডাউনলোড বাটন দেখানো হবে না!)
                item {
                    Surface(
                        color = CinemaSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // রেজুলেশন বাটন (পুরো লাইন জুড়ে চিকন বার)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showBongoResDropdown = true },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CinemaSurface,
                                        contentColor = CyanAccent
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                ) {
                                    Icon(Icons.Default.HighQuality, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রেজুলেশন: $activeBongoResolution", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(15.dp))
                                }

                                DropdownMenu(
                                    expanded = showBongoResDropdown,
                                    onDismissRequest = { showBongoResDropdown = false },
                                    modifier = Modifier.background(CinemaSurface)
                                ) {
                                    listOf("১০৮০p", "৭২০p", "৪৮০p", "৩৬০p").forEach { res ->
                                        DropdownMenuItem(
                                            text = { Text(res, color = TextPrimary, fontSize = 11.5.sp) },
                                            onClick = {
                                                activeBongoResolution = res
                                                showBongoResDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                            // বংগ ভিডিও কপিরাইট প্রোটেক্টেড হওয়ায় ডাউনলোড বাটন দেখানো হবে না (নিয়ম অনুযায়ী)
                        }
                    }
                }

                // ৩. হরিজনটাল এপিসড চিকন সরু লাইন (যদি এপিসড থাকে তাহলে দেখাবে)
                if (activeBongoShowEpisodes.isNotEmpty()) {
                    item {
                        Surface(
                            color = CinemaSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(activeBongoShowEpisodes.size) { idx ->
                                    val ep = activeBongoShowEpisodes[idx]
                                    val isSelected = activeBongoEpisodeTitle == ep.title
                                    Surface(
                                        onClick = {
                                            activeBongoEpisodeTitle = ep.title
                                            coroutineScope.launch {
                                                ApiClient.ensureHamyraToken()
                                                activeBongoStreamUrl = ApiClient.resolveBongoStreamUrl(ep.hlsUrl)
                                            }
                                        },
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, if (isSelected) BrandRed else CinemaBorder),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        ) {
                                            Text(
                                                text = if (ep.title.isNotEmpty()) ep.title else "পর্ব ${idx + 1}",
                                                color = if (isSelected) Color.White else TextPrimary,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ৪. চিকন সরু লাইনে তথ্য: বাটনের ভিতরে হিডেন থাকবে, বাটনে ক্লিক করলে তথ্য ও স্ক্রিনশট দেখাবে
                item {
                    Surface(
                        color = CinemaSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isBongoInfoExpanded = !isBongoInfoExpanded }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "তথ্য ও বিবরণ (ক্লিক করে দেখুন)",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = if (isBongoInfoExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (isBongoInfoExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(text = bMovie.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(bMovie.genre.orEmpty().ifEmpty { "নাটক / সিরিজ" }, color = CyanAccent, fontSize = 10.5.sp)
                                        if (bMovie.year != null) Text("• ${bMovie.year}", color = Color(0xFFFFB020), fontSize = 10.5.sp)
                                    }
                                    if (!bMovie.overview.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = bMovie.overview,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ৫. হরিজনটাল স্ক্রল অনুযায়ী লটারি ভিডিও কার্ড
                if (bongoLotteryMovies.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = "লটারি ভিডিও কার্ড (সম্পর্কিত ভিডিও)",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(bongoLotteryMovies) { lotMovie ->
                                    PurePosterCard(
                                        imageUrl = lotMovie.getFullPosterUrl(),
                                        contentDescription = lotMovie.title,
                                        onClick = {
                                            activeBongoMovie = lotMovie
                                        },
                                        width = 95,
                                        height = 135
                                    )
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
    // MAIN MOVIES PAGE (হেডার, ২ টি সেকশন, ক্যাটাগরি, ৫০ টি কার্ড ৫ কলামে, পেজিনেশন)
    // ========================================================================
    Scaffold(
        containerColor = CinemaBackground,
        topBar = {
            Surface(
                color = CinemaSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // হেডারে সার্চ বার ও ডাউনলোডে পেজে যাওয়ার বাটন
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                        }

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("মুভি, সিরিজ সার্চ করুন...", fontSize = 12.sp, color = TextMuted) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = BrandRed, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = CinemaBorder,
                                focusedContainerColor = CinemaSurfaceVariant,
                                unfocusedContainerColor = CinemaSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        )

                        // ডাউনলোড পেজে যাওয়ার বাটন
                        Surface(
                            onClick = onNavigateToDownloads,
                            shape = RoundedCornerShape(10.dp),
                            color = CinemaSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = "Downloads",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // তার নিচে দুইটা সেকশন থাকবে: Mukul Ott এবং বংগ
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                selectedSection = MoviesMainTab.MUKUL_OTT
                                onTabChanged?.invoke(MoviesMainTab.MUKUL_OTT)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedSection == MoviesMainTab.MUKUL_OTT) BrandRed else CinemaSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedSection == MoviesMainTab.MUKUL_OTT) BrandRed else CinemaBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Mukul Ott",
                                    color = if (selectedSection == MoviesMainTab.MUKUL_OTT) Color.White else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                selectedSection = MoviesMainTab.BONGO_OTT
                                onTabChanged?.invoke(MoviesMainTab.BONGO_OTT)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedSection == MoviesMainTab.BONGO_OTT) BrandRed else CinemaSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedSection == MoviesMainTab.BONGO_OTT) BrandRed else CinemaBorder),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "বংগ (Bongo)",
                                    color = if (selectedSection == MoviesMainTab.BONGO_OTT) Color.White else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // তার নিচে ক্যাটাগরি থাকবে: মুভি, সিরিজ, anime ইত্যাদি
                    // যা Mukul ott ও bongo ভিডিও এর অটোমেটিক ক্যাটাগরি সিলেকশন থেকে তৈরি হবে
                    val categories = remember(selectedSection, mukulMoviesList, allBongoVideos) {
                        if (selectedSection == MoviesMainTab.MUKUL_OTT) {
                            val autoCats = mutableListOf("সব", "মুভি", "সিরিজ", "Anime")
                            val kinds = mukulMoviesList.map { it.kind.lowercase() }.distinct()
                            kinds.forEach { k ->
                                val label = when (k) {
                                    "movie" -> "মুভি"
                                    "series", "tv" -> "সিরিজ"
                                    "anime" -> "Anime"
                                    else -> k.replaceFirstChar { it.uppercase() }
                                }
                                if (!autoCats.contains(label)) autoCats.add(label)
                            }
                            autoCats.distinct()
                        } else {
                            val autoCats = mutableListOf("সব", "সিরিজ", "নাটক", "মুভি")
                            val genres = allBongoVideos.mapNotNull { it.genre }.filter { it.isNotBlank() }.distinct()
                            autoCats.addAll(genres.take(6))
                            autoCats.distinct()
                        }
                    }

                    val currentCategory = if (selectedSection == MoviesMainTab.MUKUL_OTT) selectedMukulCategory else selectedBongoCategory

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            val isSel = cat == currentCategory
                            Surface(
                                onClick = {
                                    if (selectedSection == MoviesMainTab.MUKUL_OTT) {
                                        selectedMukulCategory = cat
                                    } else {
                                        selectedBongoCategory = cat
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSel) BrandRed else CinemaSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) BrandRed else CinemaBorder),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSel) Color.White else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // ----------------------------------------------------
        // এক লাইনে ৫ টি করে মুভি কার্ড এবং দশটি সারি মোট ৫০ টি ভিডিও
        // ----------------------------------------------------
        if (selectedSection == MoviesMainTab.MUKUL_OTT) {
            val filteredMukul = remember(mukulMoviesList, searchQuery, selectedMukulCategory) {
                mukulMoviesList.filter { item ->
                    val matchSearch = searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true)
                    val matchCat = when (selectedMukulCategory) {
                        "সব" -> true
                        "মুভি" -> item.kind.contains("movie", ignoreCase = true)
                        "সিরিজ" -> item.kind.contains("series", ignoreCase = true) || item.kind.contains("tv", ignoreCase = true)
                        "Anime" -> item.kind.contains("anime", ignoreCase = true)
                        else -> item.kind.contains(selectedMukulCategory, ignoreCase = true)
                    }
                    matchSearch && matchCat
                }
            }

            // Exactly 50 items (5 columns x 10 rows)
            val pageItems = remember(filteredMukul) {
                filteredMukul.take(50)
            }

            if (isLoadingMukul && mukulMoviesList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandRed)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    item {
                        // 5 columns grid
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val rows = pageItems.chunked(5)
                                rows.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowItems.forEach { item ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                PurePosterCard(
                                                    imageUrl = item.poster,
                                                    contentDescription = item.title,
                                                    onClick = {
                                                        activeMukulSlug = item.slug
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    width = 70,
                                                    height = 105
                                                )
                                            }
                                        }
                                        // Fill remaining slots if row has fewer than 5 items
                                        if (rowItems.size < 5) {
                                            repeat(5 - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // তার নিচে গেলে লোড মোর বাটন ও পেগিনেশন বাটন থাকবে কন্টেন্ট অটোমেটিক কাউন্ট পেজ অনুযায়ী
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // লোড মোর বাটন
                            FilledTonalButton(
                                onClick = {
                                    mukulCurrentPage += 1
                                    loadMukulPage(mukulCurrentPage)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CinemaSurfaceVariant,
                                    contentColor = BrandRed
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                if (isLoadingMukul) {
                                    CircularProgressIndicator(color = BrandRed, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("লোড হচ্ছে...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("আরো ভিডিও লোড করুন (পরবর্তী ৫০ টি)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // পেজিনেশন বাটন
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (mukulCurrentPage > 1) {
                                            mukulCurrentPage -= 1
                                            loadMukulPage(mukulCurrentPage)
                                        }
                                    },
                                    enabled = mukulCurrentPage > 1,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("পূর্ববর্তী", fontSize = 11.sp)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                                ) {
                                    Text(
                                        text = "পেজ $mukulCurrentPage / ২০০",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        mukulCurrentPage += 1
                                        loadMukulPage(mukulCurrentPage)
                                    },
                                    enabled = mukulCurrentPage < 200,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                                ) {
                                    Text("পরবর্তী", fontSize = 11.sp)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ----------------------------------------------------
            // BONGO SECTION
            // ----------------------------------------------------
            val filteredBongo = remember(allBongoVideos, searchQuery, selectedBongoCategory) {
                allBongoVideos.filter { item ->
                    val matchSearch = searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true)
                    val matchCat = when (selectedBongoCategory) {
                        "সব" -> true
                        "সিরিজ" -> item.title.contains("series", ignoreCase = true) || item.genre.orEmpty().contains("series", ignoreCase = true) || item.title.contains("point", ignoreCase = true)
                        "নাটক" -> item.genre.orEmpty().contains("drama", ignoreCase = true) || item.genre.orEmpty().contains("নাটক", ignoreCase = true)
                        "মুভি" -> item.genre.orEmpty().contains("movie", ignoreCase = true) || item.genre.orEmpty().contains("চলচ্চিত্র", ignoreCase = true)
                        else -> item.genre.orEmpty().contains(selectedBongoCategory, ignoreCase = true)
                    }
                    matchSearch && matchCat
                }
            }

            val pageSize = 50
            val totalBongoPages = remember(filteredBongo) {
                ((filteredBongo.size + pageSize - 1) / pageSize).coerceAtLeast(1)
            }
            val startIdx = (bongoCurrentPage - 1) * pageSize
            val pageItems = remember(filteredBongo, bongoCurrentPage) {
                filteredBongo.drop(startIdx).take(pageSize)
            }

            if (isLoadingBongo && allBongoVideos.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandRed)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    item {
                        // 5 columns grid
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val rows = pageItems.chunked(5)
                                rows.forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        rowItems.forEach { item ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                PurePosterCard(
                                                    imageUrl = item.getFullPosterUrl(),
                                                    contentDescription = item.title,
                                                    onClick = {
                                                        activeBongoMovie = item
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    width = 70,
                                                    height = 105
                                                )
                                            }
                                        }
                                        if (rowItems.size < 5) {
                                            repeat(5 - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pagination controls
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (bongoCurrentPage < totalBongoPages) {
                                FilledTonalButton(
                                    onClick = {
                                        bongoCurrentPage += 1
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = CinemaSurfaceVariant,
                                        contentColor = BrandRed
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("আরো ভিডিও লোড করুন (পরবর্তী ৫০ টি)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (bongoCurrentPage > 1) {
                                            bongoCurrentPage -= 1
                                        }
                                    },
                                    enabled = bongoCurrentPage > 1,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("পূর্ববর্তী", fontSize = 11.sp)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                                ) {
                                    Text(
                                        text = "পেজ $bongoCurrentPage / $totalBongoPages",
                                        color = TextPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (bongoCurrentPage < totalBongoPages) {
                                            bongoCurrentPage += 1
                                        }
                                    },
                                    enabled = bongoCurrentPage < totalBongoPages,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                                ) {
                                    Text("পরবর্তী", fontSize = 11.sp)
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
