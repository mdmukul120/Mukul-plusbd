package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
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
import com.example.data.download.DownloadStatus
import com.example.data.download.DownloadTask
import com.example.data.download.InAppDownloader
import com.example.data.model.*
import com.example.data.repository.MukulOttRepository
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class OttFilterType {
    ALL, MOVIES, SERIES, DOWNLOADED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MukulOttScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize downloader
    LaunchedEffect(Unit) {
        InAppDownloader.init(context)
    }

    // State for Movie List
    var movies by remember { mutableStateOf<List<MukulOttMovieItem>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(OttFilterType.ALL) }

    // Active Player State
    var selectedMovieSlug by remember { mutableStateOf<String?>(null) }
    var movieDetail by remember { mutableStateOf<MukulOttMovieDetail?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }

    // Player Playback Parameters
    var activePlayUrl by remember { mutableStateOf<String?>(null) }
    var activeQualityLabel by remember { mutableStateOf<String>("") }
    var activeEpisodeLabel by remember { mutableStateOf<String>("মেইন স্ট্রিম") }
    var showEpisodeDropdown by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    // Downloader observation
    val allTasks by InAppDownloader.tasks.collectAsState()
    val completedDownloads by InAppDownloader.completedDownloads.collectAsState()

    val gridState = rememberLazyGridState()

    // Initial load of movies
    LaunchedEffect(Unit) {
        if (movies.isEmpty()) {
            isLoadingPage = true
            val initial = MukulOttRepository.getMovies(1)
            movies = initial
            isLoadingPage = false
        }
    }

    // Infinite Pagination: Detect when scrolled near end
    LaunchedEffect(gridState.canScrollForward) {
        if (!gridState.canScrollForward && !isLoadingPage && canLoadMore && searchQuery.isEmpty() && selectedFilter != OttFilterType.DOWNLOADED) {
            isLoadingPage = true
            val nextPage = currentPage + 1
            val newItems = MukulOttRepository.getMovies(nextPage)
            if (newItems.isNotEmpty() && newItems.first().slug != movies.firstOrNull()?.slug) {
                movies = movies + newItems
                currentPage = nextPage
            } else {
                canLoadMore = false
            }
            isLoadingPage = false
        }
    }

    // Load detail when a movie is selected
    LaunchedEffect(selectedMovieSlug) {
        val slug = selectedMovieSlug
        if (slug != null) {
            isLoadingDetail = true
            movieDetail = null
            val detail = MukulOttRepository.getMovieDetail(slug)
            movieDetail = detail
            isLoadingDetail = false

            if (detail != null) {
                // Rule: movie কার্ডে ক্লিক করলে সর্বনিম্ন রেজুলেশন অনুযায়ী প্লে হবে
                // Find source with lowest quality (e.g., 480p < 720p < 1080p)
                val lowestSource = detail.watchSources.minByOrNull { it.quality }
                if (lowestSource != null) {
                    activePlayUrl = lowestSource.url.ifEmpty { lowestSource.directUrl }
                    activeQualityLabel = "${lowestSource.quality}p"
                    activeEpisodeLabel = lowestSource.episode ?: "ডিফল্ট স্ট্রিম"
                } else if (detail.watchUrl.isNotEmpty()) {
                    activePlayUrl = detail.watchUrl
                    activeQualityLabel = detail.resolution.ifEmpty { "স্ট্যান্ডার্ড" }
                    activeEpisodeLabel = "মেইন ভিডিও"
                } else if (detail.episodes.isNotEmpty()) {
                    val ep1 = detail.episodes.first()
                    activePlayUrl = ep1.streamUrl
                    activeQualityLabel = "Auto"
                    activeEpisodeLabel = ep1.title
                }
            }
        } else {
            movieDetail = null
            activePlayUrl = null
        }
    }

    // Handle back button if player is open
    BackHandler(enabled = selectedMovieSlug != null) {
        selectedMovieSlug = null
        activePlayUrl = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        if (selectedMovieSlug != null) {
            // ================================================================
            // 🎬 MOVIE PLAYER VIEW & CONTROLS ROW
            // ================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CinemaBackground)
            ) {
                // 1. VIDEO PLAYER CONTAINER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingDetail) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "মুভি ও সর্বনিম্ন রেজুলেশন লোড হচ্ছে...",
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                        }
                    } else if (!activePlayUrl.isNullOrEmpty()) {
                        VideoPlayerView(
                            videoUrl = activePlayUrl!!,
                            title = movieDetail?.title ?: "মুকুল ওটিটি প্লেয়ার",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "এই ভিডিওর সরাসরি স্ট্রিমিং লিঙ্ক প্রক্রিয়াধীন",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // ------------------------------------------------------------
                // 2. REQUIRED CONTROLS ROW:
                // [১. আগের পেজে ফেরার ছোট বাটন]  [২. এপিসোড/কোয়ালিটি সিলেট ড্রপ ডাউন]  [৩. ডাউনলোড বাটন]
                // ------------------------------------------------------------
                Surface(
                    color = CinemaSurfaceVariant,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // (১) আগের পেজে ফেরার ছোট বাটন (Small Back Button)
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
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ফিরে যান",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // (২) এপিসোড সিলেট ড্রপ ডাউন বাটন (Episode / Quality Selector Dropdown)
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showEpisodeDropdown = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = CinemaSurface,
                                    contentColor = TextPrimary
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (activeQualityLabel.isNotEmpty()) "রেজুলেশন: $activeQualityLabel" else activeEpisodeLabel,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Episode / Stream Dropdown Menu
                            DropdownMenu(
                                expanded = showEpisodeDropdown,
                                onDismissRequest = { showEpisodeDropdown = false },
                                modifier = Modifier.background(CinemaSurface)
                            ) {
                                // Watch Sources (Qualities)
                                if (movieDetail?.watchSources?.isNotEmpty() == true) {
                                    Text(
                                        text = "রেজুলেশন ও সোর্স নির্বাচন:",
                                        color = BrandRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                    movieDetail!!.watchSources.sortedBy { it.quality }.forEach { src ->
                                        val isSelected = activeQualityLabel == "${src.quality}p"
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${src.quality}p (HD Video)",
                                                        color = if (isSelected) BrandRed else TextPrimary,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 12.sp
                                                    )
                                                    if (src.quality <= 480) {
                                                        Text(
                                                            text = "সর্বনিম্ন (দ্রুত)",
                                                            color = Color(0xFF10B981),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                activePlayUrl = src.url.ifEmpty { src.directUrl }
                                                activeQualityLabel = "${src.quality}p"
                                                showEpisodeDropdown = false
                                                Toast.makeText(context, "${src.quality}p নির্বাচন করা হয়েছে", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }

                                // Series Episodes if available
                                if (movieDetail?.episodes?.isNotEmpty() == true) {
                                    HorizontalDivider(color = CinemaBorder)
                                    Text(
                                        text = "এপিসোড তালিকা:",
                                        color = BrandRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                    movieDetail!!.episodes.forEach { ep ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = ep.title.ifEmpty { "Episode ${ep.episodeNumber}" },
                                                    color = TextPrimary,
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

                        // (৩) ডাউনলোড বাটন (Download Button)
                        Button(
                            onClick = { showDownloadDialog = true },
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
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ডাউনলোড",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ------------------------------------------------------------
                // 3. IN-APP DOWNLOAD PROGRESS BAR (যদি ডাউনলোড চলমান থাকে)
                // ------------------------------------------------------------
                val activeDownloadTask = allTasks.values.firstOrNull {
                    it.movieSlug == selectedMovieSlug &&
                        (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
                }

                if (activeDownloadTask != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = BrandRed
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "অ্যাপে ডাউনলোড হচ্ছে (${activeDownloadTask.quality}): ${activeDownloadTask.progressPercent}%",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
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

                            Spacer(modifier = Modifier.height(6.dp))

                            // Linear Progress Bar
                            LinearProgressIndicator(
                                progress = { activeDownloadTask.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = BrandRed,
                                trackColor = CinemaBorder
                            )

                            Spacer(modifier = Modifier.height(4.dp))

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
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "ডিভাইসে সম্পূর্ণ ডাউনলোড আছে (${downloadedMovie.quality})",
                                        color = TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "সাইজ: ${InAppDownloader.formatFileSize(downloadedMovie.totalBytes)} • অফলাইনে চলবে",
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    activePlayUrl = downloadedMovie.filePath
                                    Toast.makeText(context, "অফলাইন ডাউনলোড থেকে প্লে হচ্ছে", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
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

                // ------------------------------------------------------------
                // 4. MOVIE DETAILS & SYNOPSIS
                // ------------------------------------------------------------
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(14.dp)
                ) {
                    item {
                        Text(
                            text = movieDetail?.title ?: "",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!movieDetail?.quality.isNullOrEmpty()) {
                                Surface(
                                    color = BrandRed.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = movieDetail!!.quality,
                                        color = BrandRedLight,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (!movieDetail?.language.isNullOrEmpty()) {
                                Text(
                                    text = movieDetail!!.language,
                                    color = CyanAccent,
                                    fontSize = 11.sp
                                )
                            }
                            if (!movieDetail?.genre.isNullOrEmpty()) {
                                Text(
                                    text = "• ${movieDetail!!.genre}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (!movieDetail?.description.isNullOrEmpty() && movieDetail?.description != "&#8203;") {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = movieDetail!!.description,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }

                        // Screenshots preview
                        if (movieDetail?.screenshots?.isNotEmpty() == true) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "স্ক্রিনশট গ্যালারি",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                movieDetail!!.screenshots.take(3).forEach { ssUrl ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(ssUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------------------
            // 5. DOWNLOAD RESOLUTION SELECTION DIALOG
            // ----------------------------------------------------------------
            if (showDownloadDialog) {
                AlertDialog(
                    onDismissRequest = { showDownloadDialog = false },
                    containerColor = CinemaSurface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = BrandRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ডাউনলোড রেজুলেশন পছন্দ করুন", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column {
                            Text(
                                text = "রেজুলেশনে ট্যাপ করলে সরাসরি অ্যাপের ভেতরে ডাউনলোড শুরু হবে। কোথাও যাওয়া লাগবে না:",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val availableResolutions = if (movieDetail?.watchSources?.isNotEmpty() == true) {
                                movieDetail!!.watchSources.sortedByDescending { it.quality }.map { src ->
                                    val matchingDl = movieDetail!!.downloads.firstOrNull {
                                        it.quality.filter { c -> c.isDigit() } == src.quality.toString()
                                    }
                                    DownloadOptionView(
                                        quality = "${src.quality}p",
                                        size = matchingDl?.size ?: "",
                                        downloadUrl = src.downloadUrl.ifEmpty { src.url },
                                        qualityInt = src.quality
                                    )
                                }
                            } else {
                                movieDetail?.downloads?.map { dl ->
                                    DownloadOptionView(
                                        quality = dl.quality,
                                        size = dl.size,
                                        downloadUrl = dl.downloadUrl,
                                        qualityInt = dl.quality.filter { it.isDigit() }.toIntOrNull() ?: 480
                                    )
                                } ?: emptyList()
                            }

                            if (availableResolutions.isEmpty()) {
                                Text(
                                    text = "কোনো ডাউনলোড রেজুলেশন পাওয়া যায়নি।",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            } else {
                                availableResolutions.forEach { opt ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                showDownloadDialog = false
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
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = CinemaSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.FileDownload,
                                                    contentDescription = null,
                                                    tint = if (opt.qualityInt <= 480) Color(0xFF10B981) else BrandRed,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = opt.quality,
                                                        color = TextPrimary,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (opt.qualityInt <= 480) {
                                                        Text("সর্বনিম্ন রেজুলেশন (দ্রুত)", color = Color(0xFF10B981), fontSize = 10.sp)
                                                    }
                                                }
                                            }

                                            if (opt.size.isNotEmpty()) {
                                                Surface(
                                                    color = BrandRed.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = opt.size,
                                                        color = BrandRedLight,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDownloadDialog = false }) {
                            Text("বন্ধ করুন", color = BrandRedLight)
                        }
                    }
                )
            }

        } else {
            // ================================================================
            // 🎬 NATIVE MUKUL OTT MOVIES GRID VIEW
            // ================================================================
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Search & Filter Bar
                Surface(
                    color = CinemaSurface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        // Search Box
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("মুকুল ওটিটি মুভি ও সিরিজ খুঁজুন...", fontSize = 12.sp, color = TextMuted) },
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CinemaSurfaceVariant,
                                unfocusedContainerColor = CinemaSurfaceVariant,
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Filter Chips Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OttFilterChip(
                                label = "সব মুভি (${movies.size})",
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
                                .padding(12.dp),
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
                    // Filtered movies
                    val filteredMovies = remember(movies, searchQuery, selectedFilter) {
                        movies.filter { item ->
                            val matchesSearch = searchQuery.isEmpty() ||
                                item.title.contains(searchQuery, ignoreCase = true)
                            val matchesKind = when (selectedFilter) {
                                OttFilterType.MOVIES -> item.kind == "movie"
                                OttFilterType.SERIES -> item.kind == "series"
                                else -> true
                            }
                            matchesSearch && matchesKind
                        }
                    }

                    if (filteredMovies.isEmpty() && !isLoadingPage) {
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
            }
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
                            .padding(6.dp)
                            .size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Title and Year
            Column(modifier = Modifier.padding(6.dp)) {
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
