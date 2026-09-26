package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import com.example.data.download.DownloadStatus
import com.example.data.download.DownloadTask
import com.example.data.download.InAppDownloader
import com.example.data.model.*
import com.example.data.repository.MukulOttRepository
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class OttFilterType {
    ALL, MOVIES, SERIES, DOWNLOADED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MukulOttScreen(
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

    // ------------------------------------------------------------------------
    // Pagination & Catalog State
    // ------------------------------------------------------------------------
    var movies by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    val totalPages = 200
    var isLoadingPage by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(OttFilterType.ALL) }
    var showPageJumpDialog by remember { mutableStateOf(false) }
    var jumpPageInput by remember { mutableStateOf("1") }

    // ------------------------------------------------------------------------
    // Deep Search Across 1-200 Pages State
    // ------------------------------------------------------------------------
    var searchQuery by remember { mutableStateOf("") }
    var isDeepSearching by remember { mutableStateOf(false) }
    var deepSearchResults by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }
    var searchScannedCount by remember { mutableIntStateOf(0) }
    var searchFoundCount by remember { mutableIntStateOf(0) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // ------------------------------------------------------------------------
    // Active Player State
    // ------------------------------------------------------------------------
    var selectedMovieSlug by remember { mutableStateOf<String?>(null) }
    var selectedMoviePreferredQuality by remember { mutableStateOf<String>("") }
    var movieDetail by remember { mutableStateOf<MukulOttMovieDetail?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }

    // Playback Parameters
    var activePlayUrl by remember { mutableStateOf<String?>(null) }
    var activeQualityLabel by remember { mutableStateOf<String>("") }
    var activeEpisodeLabel by remember { mutableStateOf<String>("মেইন ভিডিও") }

    // Separate Dropdowns
    var showResolutionDropdown by remember { mutableStateOf(false) }
    var showEpisodeDropdown by remember { mutableStateOf(false) }
    var showDownloadDropdown by remember { mutableStateOf(false) }

    // Hidden Movie Info & Screenshots Toggle (Default: hidden/collapsed)
    var isDetailsExpanded by remember { mutableStateOf(false) }

    // Lottery Recommendations ("লটারি অনুযায়ী আরো ভিডিও")
    var lotteryMovies by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }
    var isLoadingLottery by remember { mutableStateOf(false) }

    // Download state observation
    val allTasks by InAppDownloader.tasks.collectAsState()
    val completedDownloads by InAppDownloader.completedDownloads.collectAsState()

    val gridState = rememberLazyGridState()

    // Helper: Load a specific page
    fun loadPage(page: Int) {
        val target = page.coerceIn(1, totalPages)
        currentPage = target
        coroutineScope.launch {
            isLoadingPage = true
            val loaded = MukulOttRepository.getMovies(target)
            movies = loaded
            isLoadingPage = false
            gridState.scrollToItem(0)
        }
    }

    // Initial load: Page 1
    LaunchedEffect(Unit) {
        if (movies.isEmpty()) {
            loadPage(1)
        }
    }

    // Deep Search across 1-200 pages when user types
    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            isDeepSearching = false
            searchJob?.cancel()
            searchJob = null
            deepSearchResults = emptyList()
            searchScannedCount = 0
            searchFoundCount = 0
        } else {
            searchJob?.cancel()
            searchJob = coroutineScope.launch {
                delay(350) // Debounce
                isDeepSearching = true
                searchScannedCount = 0
                searchFoundCount = 0
                val results = MukulOttRepository.searchMoviesAcrossPages(
                    query = query,
                    maxPages = 200,
                    onProgress = { scanned, found ->
                        searchScannedCount = scanned
                        searchFoundCount = found
                    }
                )
                deepSearchResults = results
                isDeepSearching = false
            }
        }
    }

    // Movie Detail and Playback setup when a card is selected
    LaunchedEffect(selectedMovieSlug) {
        val slug = selectedMovieSlug
        if (slug != null) {
            isLoadingDetail = true
            movieDetail = null
            isDetailsExpanded = false
            val detail = MukulOttRepository.getMovieDetail(slug)
            movieDetail = detail
            isLoadingDetail = false

            if (detail != null) {
                // Rule: OTT page এর সকল কার্ডের ভিডিও প্লে হবে যেই রেজুলেশন থাকবে সেই রেজুলেশন অনুযায়ী
                val targetQualityNum = selectedMoviePreferredQuality.filter { it.isDigit() }.toIntOrNull()
                    ?: detail.resolution.filter { it.isDigit() }.toIntOrNull()
                    ?: detail.quality.filter { it.isDigit() }.toIntOrNull()

                // Find matching watch source for that resolution
                val matchedSource = if (targetQualityNum != null && detail.watchSources.isNotEmpty()) {
                    detail.watchSources.firstOrNull { it.quality == targetQualityNum }
                        ?: detail.watchSources.minByOrNull { Math.abs(it.quality - targetQualityNum) }
                } else {
                    detail.watchSources.firstOrNull()
                }

                if (matchedSource != null) {
                    val streamCandidate = matchedSource.proxyUrl.ifEmpty {
                        matchedSource.url.ifEmpty {
                            matchedSource.directUrl.ifEmpty { matchedSource.downloadUrl }
                        }
                    }
                    activePlayUrl = streamCandidate
                    activeQualityLabel = "${matchedSource.quality}p"
                    activeEpisodeLabel = matchedSource.episode ?: "মেইন ভিডিও"
                } else if (detail.watchUrl.isNotEmpty()) {
                    activePlayUrl = detail.watchUrl
                    activeQualityLabel = if (selectedMoviePreferredQuality.isNotEmpty()) selectedMoviePreferredQuality else detail.resolution.ifEmpty { "HD" }
                    activeEpisodeLabel = "মেইন ভিডিও"
                } else if (detail.episodes.isNotEmpty()) {
                    val ep1 = detail.episodes.first()
                    activePlayUrl = ep1.streamUrl.ifEmpty { ep1.downloadUrl }
                    activeQualityLabel = "HD"
                    activeEpisodeLabel = ep1.title
                } else if (detail.downloads.isNotEmpty()) {
                    val dl1 = detail.downloads.first()
                    activePlayUrl = dl1.downloadUrl
                    activeQualityLabel = dl1.quality
                    activeEpisodeLabel = dl1.episode ?: "মেইন ভিডিও"
                }

                // Load lottery recommendations
                coroutineScope.launch {
                    isLoadingLottery = true
                    lotteryMovies = MukulOttRepository.getRandomLotteryMovies(15)
                    isLoadingLottery = false
                }
            }
        } else {
            movieDetail = null
            activePlayUrl = null
        }
    }

    // Hardware back press handler
    BackHandler(enabled = true) {
        if (selectedMovieSlug != null) {
            selectedMovieSlug = null
            activePlayUrl = null
        } else if (onBack != null) {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        if (selectedMovieSlug != null) {
            // ================================================================
            // 🎬 IN-APP VIDEO PLAYER & MOVIE CONTROLS
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CinemaBackground)
            ) {
                // 1. VIDEO PLAYER VIEW
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (activePlayUrl != null) {
                        VideoPlayerView(
                            videoUrl = activePlayUrl!!,
                            title = movieDetail?.title ?: "Mukul OTT",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isLoadingDetail) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "ভিডিও ও রেজুলেশন লোড হচ্ছে...",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (movieDetail?.kind == "series" && movieDetail?.episodes?.isEmpty() == true)
                                        "এই সিরিজের পর্বগুলো সার্ভার হতে শীঘ্রই আপলোড হবে"
                                    else
                                        "সার্ভারে এই ভিডিও লিঙ্ক প্রক্রিয়াধীন রয়েছে",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val curSlug = selectedMovieSlug
                                        selectedMovieSlug = null
                                        selectedMovieSlug = curSlug
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRedLight),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("পুনরায় চেষ্টা করুন", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // 2. REQUIRED CONTROLS ROW:
                // [১. আগের পেজে ফেরার ছোট বাটন]
                // [২. রেজুলেশন বাটন (আলাদা)]
                // [৩. এপিসোড বাটন (আলাদা)]
                // [৪. ডাউনলোড বাটন (ক্লিক করলে রেজুলেশন ড্রপডাউন)]
                Surface(
                    color = CinemaSurfaceVariant,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // (১) ফিরে যান বাটন (Small Back Button)
                        FilledTonalButton(
                            onClick = {
                                selectedMovieSlug = null
                                activePlayUrl = null
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CinemaSurface,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "ফিরে যান",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // (২) রেজুলেশন বাটন (Resolution Selector Dropdown - ALADA BUTTON)
                        Box {
                            OutlinedButton(
                                onClick = { showResolutionDropdown = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = CinemaSurface,
                                    contentColor = TextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (activeQualityLabel.isNotEmpty()) "রেজুলেশন: $activeQualityLabel" else "রেজুলেশন",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showResolutionDropdown,
                                onDismissRequest = { showResolutionDropdown = false },
                                modifier = Modifier.background(CinemaSurface)
                            ) {
                                Text(
                                    text = "রেজুলেশন নির্বাচন করুন:",
                                    color = BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                                val sources = movieDetail?.watchSources?.sortedByDescending { it.quality } ?: emptyList()
                                if (sources.isNotEmpty()) {
                                    sources.forEach { src ->
                                        val isSelected = activeQualityLabel == "${src.quality}p"
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${src.quality}p Video",
                                                        color = if (isSelected) BrandRed else TextPrimary,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 12.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    if (src.quality >= 720) {
                                                        Text("HD", color = BrandRedLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    } else {
                                                        Text("SD", color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            },
                                            onClick = {
                                                val playTarget = src.proxyUrl.ifEmpty {
                                                    src.url.ifEmpty { src.directUrl.ifEmpty { src.downloadUrl } }
                                                }
                                                activePlayUrl = playTarget
                                                activeQualityLabel = "${src.quality}p"
                                                showResolutionDropdown = false
                                                Toast.makeText(context, "${src.quality}p রেজুলেশনে চলছে", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("স্ট্যান্ডার্ড (${activeQualityLabel.ifEmpty { "Default" }})", color = TextPrimary, fontSize = 12.sp) },
                                        onClick = { showResolutionDropdown = false }
                                    )
                                }
                            }
                        }

                        // (৩) এপিসোড বাটন (Episode Selector Dropdown - ALADA BUTTON)
                        val hasEpisodes = movieDetail?.episodes?.isNotEmpty() == true || movieDetail?.watchSources?.any { it.episode != null } == true
                        if (hasEpisodes) {
                            Box {
                                OutlinedButton(
                                    onClick = { showEpisodeDropdown = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CinemaSurface,
                                        contentColor = TextPrimary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB020),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = activeEpisodeLabel,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showEpisodeDropdown,
                                    onDismissRequest = { showEpisodeDropdown = false },
                                    modifier = Modifier.background(CinemaSurface)
                                ) {
                                    Text(
                                        text = "এপিসোড নির্বাচন করুন:",
                                        color = BrandRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                    movieDetail?.episodes?.forEach { ep ->
                                        val isSelected = activeEpisodeLabel == ep.title
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = ep.title.ifEmpty { "Episode ${ep.episodeNumber}" },
                                                    color = if (isSelected) BrandRed else TextPrimary,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 12.sp
                                                )
                                            },
                                            onClick = {
                                                activePlayUrl = ep.streamUrl
                                                activeEpisodeLabel = ep.title
                                                showEpisodeDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // (৪) ডাউনলোড বাটন (Download Button with Direct Resolution Dropdown)
                        Box {
                            Button(
                                onClick = { showDownloadDropdown = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "ডাউনলোড",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            // ডাউনলোড বাটনে ক্লিক করলে রেজুলেশন ড্রপ ডাউন মেনু
                            DropdownMenu(
                                expanded = showDownloadDropdown,
                                onDismissRequest = { showDownloadDropdown = false },
                                modifier = Modifier.background(CinemaSurface)
                            ) {
                                Text(
                                    text = "ডাউনলোড রেজুলেশন পছন্দ করুন:",
                                    color = BrandRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )

                                val availableResolutions = if (movieDetail?.watchSources?.isNotEmpty() == true) {
                                    movieDetail!!.watchSources.sortedByDescending { it.quality }.map { src ->
                                        val matchingDl = movieDetail!!.downloads.firstOrNull {
                                            it.quality.filter { c -> c.isDigit() } == src.quality.toString()
                                        }
                                        val dlUrl = src.downloadUrl.ifEmpty {
                                            matchingDl?.downloadUrl?.ifEmpty { src.url } ?: src.url
                                        }
                                        DownloadOptionView(
                                            quality = "${src.quality}p",
                                            size = matchingDl?.size ?: "",
                                            downloadUrl = dlUrl,
                                            qualityInt = src.quality
                                        )
                                    }
                                } else if (movieDetail?.downloads?.isNotEmpty() == true) {
                                    movieDetail!!.downloads.map { dl ->
                                        DownloadOptionView(
                                            quality = dl.quality,
                                            size = dl.size,
                                            downloadUrl = dl.downloadUrl,
                                            qualityInt = dl.quality.filter { it.isDigit() }.toIntOrNull() ?: 480
                                        )
                                    }
                                } else if (movieDetail?.episodes?.isNotEmpty() == true) {
                                    movieDetail!!.episodes.flatMap { it.downloads.ifEmpty { 
                                        it.sources.map { s -> MukulOttDownloadOption(quality = "${s.quality}p", downloadUrl = s.downloadUrl.ifEmpty { s.url }) }
                                    } }.distinctBy { it.downloadUrl }.map { dl ->
                                        DownloadOptionView(
                                            quality = dl.quality,
                                            size = dl.size,
                                            downloadUrl = dl.downloadUrl,
                                            qualityInt = dl.quality.filter { it.isDigit() }.toIntOrNull() ?: 480
                                        )
                                    }
                                } else {
                                    emptyList()
                                }

                                if (availableResolutions.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("কোনো ডাউনলোড লিঙ্ক নেই", color = TextMuted, fontSize = 11.sp) },
                                        onClick = { showDownloadDropdown = false }
                                    )
                                } else {
                                    availableResolutions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.FileDownload,
                                                            contentDescription = null,
                                                            tint = if (opt.qualityInt <= 480) Color(0xFF10B981) else BrandRed,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = opt.quality,
                                                            color = TextPrimary,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    if (opt.size.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Surface(
                                                            color = BrandRed.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = opt.size,
                                                                color = BrandRedLight,
                                                                fontSize = 10.sp,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {
                                                showDownloadDropdown = false
                                                val slug = movieDetail?.slug ?: ""
                                                val title = movieDetail?.title ?: "Movie"
                                                val poster = movieDetail?.poster ?: ""
                                                val url = opt.downloadUrl
                                                if (url.isNotEmpty()) {
                                                    InAppDownloader.startDownload(
                                                        context = context,
                                                        movieSlug = slug,
                                                        title = title,
                                                        poster = poster,
                                                        quality = opt.quality,
                                                        downloadUrl = url
                                                    )
                                                    Toast.makeText(context, "${opt.quality} ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "ডাউনলোড লিঙ্ক প্রস্তুত নয়", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. IN-APP DOWNLOAD PROGRESS BAR (যদি ডাউনলোড চলমান থাকে)
                val activeDownloadTask = allTasks.values.firstOrNull {
                    it.movieSlug == selectedMovieSlug &&
                        (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
                }

                if (activeDownloadTask != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(13.dp),
                                        strokeWidth = 2.dp,
                                        color = BrandRed
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ডাউনলোড হচ্ছে (${activeDownloadTask.quality}): ${activeDownloadTask.progressPercent}%",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                TextButton(
                                    onClick = { InAppDownloader.cancelDownload(activeDownloadTask.id) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("বাতিল", color = BrandRedLight, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            LinearProgressIndicator(
                                progress = { activeDownloadTask.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BrandRed,
                                trackColor = CinemaBorder
                            )

                            Spacer(modifier = Modifier.height(3.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${InAppDownloader.formatFileSize(activeDownloadTask.downloadedBytes)} / ${InAppDownloader.formatFileSize(activeDownloadTask.totalBytes)}",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                if (activeDownloadTask.speedText.isNotEmpty()) {
                                    Text(
                                        text = "গতি: ${activeDownloadTask.speedText}",
                                        color = CyanAccent,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Check if already downloaded on device
                val downloadedMovie = InAppDownloader.getCompletedMovie(selectedMovieSlug ?: "")
                if (downloadedMovie != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ডিভাইসে ডাউনলোড আছে (${downloadedMovie.quality})",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    activePlayUrl = downloadedMovie.filePath
                                    Toast.makeText(context, "অফলাইন ডাউনলোড থেকে প্লে হচ্ছে", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("অফলাইন প্লে", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Scrollable container for Info & Lottery Recommendations
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // ------------------------------------------------------------
                    // 4. এক লাইনে মুভির তথ্য ও স্ক্রিনশট (যা হিডেন আইকনে লুকানো থাকবে)
                    // ------------------------------------------------------------
                    item {
                        Surface(
                            color = CinemaSurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                // এক লাইনে শিরোনাম, রেজুলেশন ও হিডেন আইকন বাটন
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isDetailsExpanded = !isDetailsExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = movieDetail?.title ?: "মুভির বিবরণ",
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!movieDetail?.quality.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = BrandRed.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = movieDetail!!.quality,
                                                    color = BrandRedLight,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (!movieDetail?.language.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = movieDetail!!.language,
                                                color = CyanAccent,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // হিডেন আইকন এবং টগল টেক্সট
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isDetailsExpanded) "লুকান" else "তথ্য ও ছবি",
                                            color = if (isDetailsExpanded) BrandRedLight else TextMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (isDetailsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = "তথ্য ও স্ক্রিনশট লুকানো/দেখুন",
                                            tint = if (isDetailsExpanded) BrandRedLight else TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // হিডেন আইকনে ক্লিক করলে উন্মোচিত বিস্তারিত তথ্য ও স্ক্রিনশট গ্যালারি
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = isDetailsExpanded,
                                    enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                    exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                                ) {
                                    Column(modifier = Modifier.padding(top = 8.dp)) {
                                        HorizontalDivider(color = CinemaBorder, thickness = 0.5.dp)
                                        Spacer(modifier = Modifier.height(6.dp))

                                        if (!movieDetail?.genre.isNullOrEmpty()) {
                                            Text(
                                                text = "ধরন: ${movieDetail!!.genre}",
                                                color = CyanAccent,
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (!movieDetail?.description.isNullOrEmpty() && movieDetail?.description != "&#8203;") {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = movieDetail!!.description,
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        // স্ক্রিনশট গ্যালারি
                                        if (movieDetail?.screenshots?.isNotEmpty() == true) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "স্ক্রিনশট গ্যালারি (${movieDetail!!.screenshots.size}):",
                                                color = TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(movieDetail!!.screenshots) { ssUrl ->
                                                    AsyncImage(
                                                        model = ImageRequest.Builder(context)
                                                            .data(ssUrl)
                                                            .crossfade(true)
                                                            .build(),
                                                        contentDescription = "Screenshot",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .width(140.dp)
                                                            .height(80.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(CinemaSurfaceVariant)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ------------------------------------------------------------
                    // 5. লটারি অনুযায়ী আরো ভিডিও লোড (হরিজনটাল স্ক্রল)
                    // ------------------------------------------------------------
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "🎰 লটারি অনুযায়ী আরো ভিডিও",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFFFB020).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "লাকি ড্র",
                                            color = Color(0xFFFFB020),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                // Re-roll / Refresh Lottery
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            isLoadingLottery = true
                                            lotteryMovies = MukulOttRepository.getRandomLotteryMovies(15)
                                            isLoadingLottery = false
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Casino,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("নতুন লটারি ড্র", color = CyanAccent, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (isLoadingLottery && lotteryMovies.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(24.dp))
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    items(lotteryMovies, key = { "lottery_${it.slug}" }) { item ->
                                        LotteryMovieCard(
                                            item = item,
                                            onClick = {
                                                selectedMovieSlug = item.slug
                                                selectedMoviePreferredQuality = item.qualityTag
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } else {
            // ================================================================
            // 🎬 MUKUL OTT MOVIES GRID & PAGINATION VIEW
            // ================================================================
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Search & Filter Bar
                Surface(
                    color = CinemaSurface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        // Search Box (1-200 pages deep search) with Back button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (onBack != null) {
                                Surface(
                                    onClick = onBack,
                                    shape = RoundedCornerShape(12.dp),
                                    color = CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = TextPrimary
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("১-২০০ পেজের সব মুভি ও সিরিজ খুঁজুন...", fontSize = 12.sp, color = TextMuted) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = CinemaSurfaceVariant,
                                    unfocusedContainerColor = CinemaSurfaceVariant,
                                    focusedBorderColor = BrandRed,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                            )
                        }

                        // Deep Search Status Banner (if active search)
                        if (searchQuery.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = CinemaSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isDeepSearching) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 2.dp,
                                                color = BrandRed
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "১-২০০ পেজে অনুসন্ধান চলছে... (পেজ $searchScannedCount/২০০ স্ক্যান হয়েছে)",
                                                color = TextPrimary,
                                                fontSize = 10.sp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "১-২০০ পেজ সার্চ সম্পন্ন! (${deepSearchResults.size} টি মুভি পাওয়া গেছে)",
                                                color = Color(0xFF10B981),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (isDeepSearching) {
                                        TextButton(
                                            onClick = {
                                                searchJob?.cancel()
                                                isDeepSearching = false
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("থামান", color = BrandRedLight, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Filter Chips Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val allCount = if (searchQuery.isNotEmpty()) deepSearchResults.size else movies.size
                            OttFilterChip(
                                label = "সব মুভি ($allCount)",
                                selected = selectedFilter == OttFilterType.ALL,
                                onClick = { selectedFilter = OttFilterType.ALL }
                            )
                            OttFilterChip(
                                label = "সিনেমা",
                                selected = selectedFilter == OttFilterType.MOVIES,
                                onClick = { selectedFilter = OttFilterType.MOVIES }
                            )
                            OttFilterChip(
                                label = "সিরিজ",
                                selected = selectedFilter == OttFilterType.SERIES,
                                onClick = { selectedFilter = OttFilterType.SERIES }
                            )
                            OttFilterChip(
                                label = "ডাউনলোড (${completedDownloads.size})",
                                selected = selectedFilter == OttFilterType.DOWNLOADED,
                                onClick = { selectedFilter = OttFilterType.DOWNLOADED }
                            )
                        }
                    }
                }

                // Main Content: Grid or Downloads list
                if (selectedFilter == OttFilterType.DOWNLOADED) {
                    // DOWNLOADED MOVIES LIST (ALL IN-APP)
                    if (completedDownloads.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("এখনো কোনো মুভি ডাউনলোড করা হয়নি", color = TextMuted, fontSize = 13.sp)
                                Text("মুভি কার্ডে গিয়ে ডাউনলোড বাটনে ক্লিক করলেই এখানে জমা হবে", color = TextMuted.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(completedDownloads, key = { it.id }) { task ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = CinemaSurfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(task.poster)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(54.dp, 72.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = task.title,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${task.quality} • ${InAppDownloader.formatFileSize(task.totalBytes)}",
                                                color = CyanAccent,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "ডিভাইসে সম্পূর্ণ সংরক্ষিত (In-App)",
                                                color = Color(0xFF10B981),
                                                fontSize = 10.sp
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                selectedMovieSlug = task.movieSlug
                                                activePlayUrl = task.filePath
                                                activeQualityLabel = task.quality
                                            }
                                        ) {
                                            Icon(Icons.Default.PlayCircle, contentDescription = "Play", tint = BrandRed, modifier = Modifier.size(32.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                InAppDownloader.deleteDownloadedMovie(context, task.id)
                                                Toast.makeText(context, "মুভি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                } else {
                    // Movies to display: either deep search results or current page movies
                    val sourceList = if (searchQuery.isNotEmpty()) deepSearchResults else movies
                    val filteredMovies = remember(sourceList, selectedFilter) {
                        sourceList.filter { item ->
                            when (selectedFilter) {
                                OttFilterType.MOVIES -> item.kind == "movie"
                                OttFilterType.SERIES -> item.kind == "series"
                                else -> true
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        if (filteredMovies.isEmpty() && !isLoadingPage && !isDeepSearching) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("কোনো মুভি পাওয়া যায়নি", color = TextMuted, fontSize = 13.sp)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                state = gridState,
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredMovies, key = { it.slug }) { item ->
                                    MukulOttMovieCard(
                                        item = item,
                                        onClick = {
                                            selectedMovieSlug = item.slug
                                            selectedMoviePreferredQuality = item.qualityTag
                                        }
                                    )
                                }

                                if (isLoadingPage) {
                                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color = BrandRed,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --------------------------------------------------------
                    // OTT PAGE PAGINATION BAR (Ott page এ পরবর্তী পেজে যাওয়ার পেগিনেশন)
                    // --------------------------------------------------------
                    if (searchQuery.isEmpty() && selectedFilter != OttFilterType.DOWNLOADED) {
                        Surface(
                            color = CinemaSurface,
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // [⏮ প্রথম পেজ]
                                IconButton(
                                    onClick = { if (currentPage > 1) loadPage(1) },
                                    enabled = currentPage > 1 && !isLoadingPage,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FirstPage,
                                        contentDescription = "First Page",
                                        tint = if (currentPage > 1) TextPrimary else TextMuted
                                    )
                                }

                                // [◀ পূর্ববর্তী পেজ]
                                OutlinedButton(
                                    onClick = { if (currentPage > 1) loadPage(currentPage - 1) },
                                    enabled = currentPage > 1 && !isLoadingPage,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = CinemaSurfaceVariant,
                                        contentColor = TextPrimary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("পূর্ববর্তী", fontSize = 11.sp)
                                }

                                // [ পেজ X / 200 ] (Direct Click opens Jump Dialog)
                                Surface(
                                    onClick = {
                                        jumpPageInput = currentPage.toString()
                                        showPageJumpDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "পেজ $currentPage",
                                            color = BrandRedLight,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = " / $totalPages",
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.Default.SwapVert,
                                            contentDescription = "Jump",
                                            tint = CyanAccent,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                // [পরবর্তী পেজ ▶]
                                Button(
                                    onClick = { if (currentPage < totalPages) loadPage(currentPage + 1) },
                                    enabled = currentPage < totalPages && !isLoadingPage,
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("পরবর্তী", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }

                                // [সর্বশেষ পেজ ⏭]
                                IconButton(
                                    onClick = { if (currentPage < totalPages) loadPage(totalPages) },
                                    enabled = currentPage < totalPages && !isLoadingPage,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LastPage,
                                        contentDescription = "Last Page",
                                        tint = if (currentPage < totalPages) TextPrimary else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --------------------------------------------------------------------
        // PAGE JUMP DIALOG (১-২০০ পেজের যেকোনো পেজে সরাসরি যাওয়ার ডায়লগ)
        // --------------------------------------------------------------------
        if (showPageJumpDialog) {
            AlertDialog(
                onDismissRequest = { showPageJumpDialog = false },
                containerColor = CinemaSurface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FindInPage, contentDescription = null, tint = BrandRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("পেজে জাম্প করুন (১ - ২০০)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "যে পেজে যেতে চান সেই পেজ নম্বর লিখুন অথবা নিচের কুইক পেজ বাটন ব্যবহার করুন:",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = jumpPageInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    jumpPageInput = input.take(3)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Jump Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1, 10, 50, 100, 200).forEach { p ->
                                Surface(
                                    onClick = { jumpPageInput = p.toString() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "P$p",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = jumpPageInput.toIntOrNull() ?: 1
                            showPageJumpDialog = false
                            loadPage(target)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                    ) {
                        Text("যান", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPageJumpDialog = false }) {
                        Text("বাতিল", color = TextMuted)
                    }
                }
            )
        }
    }
}

private data class DownloadOptionView(
    val quality: String,
    val size: String,
    val downloadUrl: String,
    val qualityInt: Int
)

@Composable
private fun OttFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) BrandRed else CinemaSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) BrandRed else CinemaBorder
        )
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

/**
 * Standard OTT Movie Card in Grid View
 */
@Composable
fun MukulOttMovieCard(
    item: MukulOttMovieItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(CinemaSurfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.poster)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Quality / Kind Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = if (item.kind == "series") CyanAccent.copy(alpha = 0.85f) else BrandRed.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = if (item.kind == "series") "সিরিজ" else "মুভি",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    if (item.qualityTag.isNotEmpty()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = item.qualityTag,
                                color = Color(0xFFFFB020),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Play Overlay Icon at bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                startY = 100f
                            )
                        ),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BrandRed.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(5.dp)
                            .size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // Title and Year
            Column(modifier = Modifier.padding(5.dp)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.year}",
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }
        }
    }
}

/**
 * Compact Movie Card for Lottery Recommendations (Horizontal Scroll)
 */
@Composable
fun LotteryMovieCard(
    item: MukulOttMovieItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        modifier = modifier
            .width(105.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(CinemaSurfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.poster)
                        .crossfade(true)
                        .build(),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Lottery Tag Badge
                Surface(
                    color = Color(0xFFFFB020).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "🎲 লটারি",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                // Quality Badge
                if (item.qualityTag.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                    ) {
                        Text(
                            text = item.qualityTag,
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 70f
                            )
                        ),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BrandRed,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.year}",
                    color = TextMuted,
                    fontSize = 8.5.sp
                )
            }
        }
    }
}
