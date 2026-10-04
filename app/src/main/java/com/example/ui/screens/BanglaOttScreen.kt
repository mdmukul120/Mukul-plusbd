package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.BanglaMovieApiClient
import com.example.data.download.InAppDownloader
import com.example.data.model.*
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanglaOttScreen(
    onBack: (() -> Unit)? = null,
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ------------------------------------------------------------------------
    // Catalog & Filter State
    // ------------------------------------------------------------------------
    var movies by remember { mutableStateOf<List<BanglaMovie>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var totalMoviesCount by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedPlatform by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) } // null, "MOVIE", "SERIES"

    var searchQuery by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // ------------------------------------------------------------------------
    // Active Movie Detail & Video Player State
    // ------------------------------------------------------------------------
    var activeMovieId by remember { mutableStateOf<String?>(null) }
    var activeMovieDetail by remember { mutableStateOf<BanglaMovieDetail?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }
    var activePlayUrl by remember { mutableStateOf<String?>(null) }
    var activeQualityLabel by remember { mutableStateOf("") }
    var isDetailsExpanded by remember { mutableStateOf(false) }

    // Selected Episode Bundle for Series
    var selectedBundleIndex by remember { mutableIntStateOf(0) }

    // ------------------------------------------------------------------------
    // Download Dialog State
    // ------------------------------------------------------------------------
    var showDownloadDialog by remember { mutableStateOf(false) }
    var targetDownloadMovie by remember { mutableStateOf<BanglaMovie?>(null) }
    var downloadDialogDetail by remember { mutableStateOf<BanglaMovieDetail?>(null) }
    var isLoadingDownloadDetail by remember { mutableStateOf(false) }

    // Back handler
    BackHandler {
        when {
            activeMovieId != null -> {
                activeMovieId = null
                activePlayUrl = null
                activeMovieDetail = null
            }
            searchQuery.isNotEmpty() -> {
                searchQuery = ""
            }
            onBack != null -> {
                onBack()
            }
        }
    }

    // Function to fetch movies
    fun loadCatalog(page: Int = currentPage) {
        isLoading = true
        coroutineScope.launch {
            try {
                val res = BanglaMovieApiClient.fetchMovies(
                    page = page,
                    limit = 20,
                    platform = selectedPlatform,
                    language = selectedLanguage,
                    type = selectedType,
                    searchQuery = searchQuery.ifBlank { null }
                )
                movies = res.movies
                currentPage = res.page
                totalPages = res.totalPages
                totalMoviesCount = res.totalMovies
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    // Load on change of filters
    LaunchedEffect(selectedPlatform, selectedLanguage, selectedType) {
        currentPage = 1
        loadCatalog(1)
    }

    // Search query listener with debounce
    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            delay(300)
            currentPage = 1
            loadCatalog(1)
        }
    }

    // Load active movie detail
    LaunchedEffect(activeMovieId) {
        val mid = activeMovieId
        if (mid != null) {
            isLoadingDetail = true
            activeMovieDetail = null
            activePlayUrl = null
            selectedBundleIndex = 0
            val detail = BanglaMovieApiClient.fetchMovieDetail(mid)
            activeMovieDetail = detail
            isLoadingDetail = false

            if (detail != null) {
                // Find first playable URL
                val firstQuality = detail.qualities.firstOrNull()
                val serverQuality = detail.downloadServers.firstOrNull()?.qualities?.firstOrNull()
                val bundleQuality = detail.downloadServers.firstOrNull()?.episodeBundles?.firstOrNull()?.qualities?.firstOrNull()

                val playCandidate = firstQuality?.downloadUrl
                    ?: serverQuality?.downloadUrl
                    ?: bundleQuality?.downloadUrl

                activePlayUrl = playCandidate
                activeQualityLabel = firstQuality?.label ?: serverQuality?.label ?: bundleQuality?.label ?: "HD"
            }
        }
    }

    // Trigger download dialog detail fetch
    fun openDownloadDialog(movie: BanglaMovie) {
        targetDownloadMovie = movie
        showDownloadDialog = true
        isLoadingDownloadDetail = true
        downloadDialogDetail = null

        coroutineScope.launch {
            val d = BanglaMovieApiClient.fetchMovieDetail(movie.id)
            downloadDialogDetail = d
            isLoadingDownloadDetail = false
        }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = BrandRed.copy(alpha = 0.2f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Subscriptions,
                                        contentDescription = null,
                                        tint = BrandRed,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "বাংলা ওটিটি (Bangla OTT)",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (totalMoviesCount > 0) "$totalMoviesCount টি মুভি ও সিরিজ" else "চরকি, হইচই, বঙ্গ, নেটফ্লিক্স ও আরও",
                                    color = TextMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        // Downloads button shortcut
                        IconButton(onClick = onNavigateToDownloads) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Downloads",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Refresh button
                        IconButton(onClick = { loadCatalog(currentPage) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Search Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("মুভি বা সিরিজের নাম দিয়ে খুঁজুন...", color = TextMuted, fontSize = 12.5.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                unfocusedBorderColor = CinemaBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ================================================================
            // 1. ACTIVE VIDEO PLAYER (যখন কোনো মুভি সিলেক্ট করা হয়)
            // ================================================================
            if (activeMovieId != null) {
                val detail = activeMovieDetail
                Surface(
                    color = Color.Black,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Video Player Screen
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        ) {
                            if (isLoadingDetail) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(36.dp))
                                }
                            } else if (!activePlayUrl.isNullOrEmpty()) {
                                VideoPlayerView(
                                    videoUrl = activePlayUrl!!,
                                    title = detail?.title ?: "Playing Video",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PlayDisabled, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("সরাসরি ভিডিও স্ট্রিম পাওয়া যায়নি", color = TextSecondary, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        detail?.downloadServers?.firstOrNull()?.qualities?.firstOrNull()?.let { q ->
                                            Button(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(q.downloadUrl))
                                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("ব্রাউজারে ভিডিও দেখুন", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            // Close player button at top-right
                            IconButton(
                                onClick = {
                                    activeMovieId = null
                                    activePlayUrl = null
                                    activeMovieDetail = null
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close Player", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Player Control Row: Servers, Qualities & Action Buttons
                        if (detail != null) {
                            Surface(
                                color = CinemaSurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = detail.title,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Action buttons bar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Quality Pills / Server Selector
                                        val allQualities = detail.qualities.ifEmpty {
                                            detail.downloadServers.firstOrNull()?.qualities ?: emptyList()
                                        }

                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            allQualities.forEach { q ->
                                                val isSelected = activePlayUrl == q.downloadUrl
                                                Surface(
                                                    onClick = {
                                                        activePlayUrl = q.downloadUrl
                                                        activeQualityLabel = q.label
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BrandRedLight else CinemaBorder)
                                                ) {
                                                    Text(
                                                        text = "${q.label} ${if (q.size.isNotEmpty()) "(${q.size})" else ""}",
                                                        color = if (isSelected) Color.White else TextSecondary,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Direct Download Trigger Button
                                            Button(
                                                onClick = {
                                                    targetDownloadMovie = BanglaMovie(
                                                        id = detail.id,
                                                        title = detail.title,
                                                        poster = detail.poster,
                                                        platform = detail.platform
                                                    )
                                                    downloadDialogDetail = detail
                                                    showDownloadDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text("ডাউনলোড", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            // Details info toggle
                                            FilledTonalButton(
                                                onClick = { isDetailsExpanded = !isDetailsExpanded },
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = if (isDetailsExpanded) BrandRed.copy(alpha = 0.2f) else CinemaSurfaceVariant,
                                                    contentColor = if (isDetailsExpanded) BrandRedLight else TextPrimary
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(if (isDetailsExpanded) "সংক্ষিপ্ত" else "তথ্য", fontSize = 10.5.sp)
                                            }
                                        }
                                    }

                                    // Episode Bundles (if series)
                                    val bundles = detail.downloadServers.firstOrNull()?.episodeBundles ?: emptyList()
                                    if (bundles.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("পর্ব নির্বাচন (Episodes):", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            bundles.forEachIndexed { bIdx, b ->
                                                val isBundleActive = selectedBundleIndex == bIdx
                                                Surface(
                                                    onClick = {
                                                        selectedBundleIndex = bIdx
                                                        val q1 = b.qualities.firstOrNull()
                                                        if (q1 != null) {
                                                            activePlayUrl = q1.downloadUrl
                                                            activeQualityLabel = q1.label
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isBundleActive) CyanAccent.copy(alpha = 0.2f) else CinemaSurfaceVariant,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isBundleActive) CyanAccent else CinemaBorder)
                                                ) {
                                                    Text(
                                                        text = b.episodeRange,
                                                        color = if (isBundleActive) CyanAccent else TextPrimary,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = if (isBundleActive) FontWeight.Bold else FontWeight.Normal,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Expanded Details Block
                                    if (isDetailsExpanded) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = CinemaBorder)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Storyline
                                        if (detail.storyline.isNotBlank()) {
                                            Text("কাহিনী সংক্ষেপ:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(text = detail.storyline, color = TextSecondary, fontSize = 11.5.sp, lineHeight = 16.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        // Cast
                                        if (detail.cast.isNotBlank()) {
                                            Text("অভিনয়ে:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(text = detail.cast, color = TextPrimary, fontSize = 11.5.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        // Metadata pills
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (detail.platform.isNotBlank()) {
                                                Surface(color = BrandRed.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                    Text(text = detail.platform, color = BrandRedLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                            if (detail.rating > 0.0) {
                                                Surface(color = Color(0xFFFFB020).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                                    Text(text = "★ ${detail.rating}", color = Color(0xFFFFB020), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                            if (detail.printQuality.isNotBlank()) {
                                                Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                                    Text(text = detail.printQuality, color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        }

                                        // Screenshots gallery
                                        if (detail.screenshots.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("স্ক্রিনশটস:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                items(detail.screenshots) { scUrl ->
                                                    AsyncImage(
                                                        model = scUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .width(130.dp)
                                                            .height(75.dp)
                                                            .clip(RoundedCornerShape(6.dp))
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

            // ================================================================
            // 2. FILTER CHIPS ROW (প্ল্যাটফর্ম ও ক্যাটাগরি চিপস)
            // ================================================================
            Surface(
                color = CinemaSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    // Row 1: OTT Platforms (Chorki, Hoichoi, Bongo, Toffee, Binge, Deepto Play, iScreen, Netflix, etc.)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BanglaMovieApiClient.platforms.forEach { (label, value) ->
                            val isSelected = selectedPlatform == value
                            Surface(
                                onClick = { selectedPlatform = value },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BrandRedLight else CinemaBorder)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row 2: Type & Languages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // All Type
                        TypeChip(label = "সব কন্টেন্ট", isSelected = selectedType == null) { selectedType = null }
                        TypeChip(label = "মুভি", isSelected = selectedType == "MOVIE") { selectedType = "MOVIE" }
                        TypeChip(label = "ওয়েব সিরিজ", isSelected = selectedType == "SERIES") { selectedType = "SERIES" }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Languages
                        BanglaMovieApiClient.languages.forEach { (label, value) ->
                            val isLangSelected = selectedLanguage == value
                            Surface(
                                onClick = { selectedLanguage = value },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isLangSelected) CyanAccent.copy(alpha = 0.2f) else CinemaSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isLangSelected) CyanAccent else CinemaBorder)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isLangSelected) CyanAccent else TextMuted,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isLangSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 3. MOVIE CATALOG GRID
            // ================================================================
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(38.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("মুভি ও সিরিজ লোড হচ্ছে...", color = TextSecondary, fontSize = 12.5.sp)
                    }
                }
            } else if (movies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("কোনো কন্টেন্ট খুঁজে পাওয়া যায়নি", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("অন্য প্ল্যাটফর্ম বা ফিল্টার নির্বাচন করে চেষ্টা করুন", color = TextMuted, fontSize = 11.5.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                selectedPlatform = null
                                selectedLanguage = null
                                selectedType = null
                                searchQuery = ""
                                loadCatalog(1)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ফিল্টার রিসেট করুন")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(movies, key = { it.id }) { movie ->
                        BanglaMovieCard(
                            movie = movie,
                            onPlayClick = { activeMovieId = movie.id },
                            onDownloadClick = { openDownloadDialog(movie) }
                        )
                    }

                    // Pagination Footer
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        BanglaOttPaginationBar(
                            currentPage = currentPage,
                            totalPages = totalPages,
                            onPageChange = { newPage ->
                                if (newPage in 1..totalPages) {
                                    currentPage = newPage
                                    loadCatalog(newPage)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ================================================================
    // 4. DOWNLOAD RESOLUTION PICKER MODAL DIALOG
    // ================================================================
    if (showDownloadDialog && targetDownloadMovie != null) {
        val target = targetDownloadMovie!!
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            containerColor = CinemaSurface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = BrandRed, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ডাউনলোড রেজুলেশন",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Movie info header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CinemaSurfaceVariant, RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = target.poster,
                            contentDescription = target.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 46.dp, height = 64.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.title,
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (!target.platform.isNullOrBlank()) {
                                Text(
                                    text = target.platform,
                                    color = BrandRedLight,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isLoadingDownloadDetail) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(32.dp))
                        }
                    } else {
                        val detail = downloadDialogDetail
                        val allQualities = detail?.qualities.orEmpty().ifEmpty {
                            detail?.downloadServers?.firstOrNull()?.qualities.orEmpty().ifEmpty {
                                detail?.downloadServers?.firstOrNull()?.episodeBundles?.firstOrNull()?.qualities.orEmpty()
                            }
                        }

                        if (allQualities.isEmpty()) {
                            Text(
                                text = "এই মুভির সরাসরি ডাউনলোড লিঙ্ক এখনও প্রস্তুত নয়।",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Text(
                                text = "পছন্দের রেজুলেশন সিলেক্ট করে সরাসরি ডাউনলোড শুরু করুন:",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(allQualities) { q ->
                                    Surface(
                                        onClick = {
                                            showDownloadDialog = false
                                            InAppDownloader.startDownload(
                                                context = context,
                                                movieSlug = "bangla_${target.id}",
                                                title = target.title,
                                                poster = target.poster,
                                                quality = q.label,
                                                downloadUrl = q.downloadUrl
                                            )
                                            Toast.makeText(
                                                context,
                                                "${q.label} ডাউনলোড শুরু হয়েছে! ডাউনলোড পেজে দেখতে পারবেন",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = CinemaSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.FileDownload,
                                                    contentDescription = null,
                                                    tint = BrandRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = q.label,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (q.size.isNotBlank()) {
                                                    Surface(
                                                        color = BrandRed.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = q.size,
                                                            color = BrandRedLight,
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                }
                                                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = TextMuted, modifier = Modifier.size(11.dp))
                                            }
                                        }
                                    }
                                }

                                // External / Browser open fallback option
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    allQualities.firstOrNull()?.let { firstQ ->
                                        OutlinedButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(firstQ.downloadUrl))
                                                try {
                                                    context.startActivity(intent)
                                                    showDownloadDialog = false
                                                } catch (_: Exception) {
                                                    Toast.makeText(context, "ব্রাউজার খোলা যায়নি", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                        ) {
                                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("ব্রাউজারে লিঙ্ক খুলুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    Text("বন্ধ করুন", color = BrandRed)
                }
            }
        )
    }
}

/**
 * Reusable Type Chip
 */
@Composable
private fun TypeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Color(0xFFFFB020) else CinemaSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFFFFB020) else CinemaBorder)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Bangla Movie Card with Poster, Badges, and Dual Play & Download Action Buttons
 */
@Composable
private fun BanglaMovieCard(
    movie: BanglaMovie,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Poster Box with Aspect Ratio 2:3
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(CinemaSurfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(movie.poster)
                        .crossfade(true)
                        .build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-Left: Platform Badge (e.g. Chorki, Hoichoi, Bongo)
                if (!movie.platform.isNullOrBlank()) {
                    val platformColor = when (movie.platform.lowercase()) {
                        "chorki" -> Color(0xFFFF9900)
                        "hoichoi" -> Color(0xFFE50914)
                        "bongo" -> Color(0xFFE50914)
                        "toffee" -> Color(0xFF0072B5)
                        "binge" -> Color(0xFF9C27B0)
                        "netflix" -> Color(0xFFE50914)
                        "prime video" -> Color(0xFF00A8E1)
                        else -> BrandRed
                    }
                    Surface(
                        color = platformColor,
                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = movie.platform,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Top-Right: Type / Quality (WEB-DL, HD)
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (movie.type == "SERIES") "SERIES" else movie.printQuality,
                        color = if (movie.type == "SERIES") CyanAccent else Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Center Play Icon Overlay
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Bottom Overlay: Rating & Language
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (movie.rating > 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format("%.1f", movie.rating),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (movie.language.isNotBlank()) {
                        Text(
                            text = movie.language,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 8.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Title & Info
            Column(modifier = Modifier.padding(7.dp)) {
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Dual Action Buttons: Play & Download (ঠিক ওটিটি পেজের মতো)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play Button
                    FilledTonalButton(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CinemaSurfaceVariant,
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("প্লে", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Download Button
                    Button(
                        onClick = onDownloadClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(26.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("ডাউনলোড", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Responsive Pagination Bar for Bangla OTT
 */
@Composable
private fun BanglaOttPaginationBar(
    currentPage: Int,
    totalPages: Int,
    onPageChange: (Int) -> Unit
) {
    Surface(
        color = CinemaSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = { onPageChange(currentPage - 1) },
                enabled = currentPage > 1,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = CinemaSurfaceVariant,
                    contentColor = TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("পূর্ববর্তী", fontSize = 11.sp)
            }

            Text(
                text = "পৃষ্ঠা $currentPage / $totalPages",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            FilledTonalButton(
                onClick = { onPageChange(currentPage + 1) },
                enabled = currentPage < totalPages,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = CinemaSurfaceVariant,
                    contentColor = TextPrimary
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("পরবর্তী", fontSize = 11.sp)
                Spacer(modifier = Modifier.width(2.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}
