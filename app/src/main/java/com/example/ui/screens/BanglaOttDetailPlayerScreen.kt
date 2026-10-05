package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.BanglaMovieApiClient
import com.example.data.api.BanglaMovieLinkScraper
import com.example.data.download.InAppDownloader
import com.example.data.model.*
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanglaOttDetailPlayerScreen(
    movieId: String,
    onBack: () -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onSelectRelatedMovie: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<BanglaMovieDetail?>(null) }
    var isLoadingDetail by remember { mutableStateOf(true) }

    // Video Scraping & Stream state
    var scrapedLinks by remember { mutableStateOf<List<ScrapedVideoLink>>(emptyList()) }
    var selectedLinkIndex by remember { mutableIntStateOf(0) }
    var selectedBundleIndex by remember { mutableIntStateOf(0) }
    var isScrapingLinks by remember { mutableStateOf(false) }
    var activePlayUrl by remember { mutableStateOf<String?>(null) }

    // Related movies
    var relatedMovies by remember { mutableStateOf<List<BanglaMovie>>(emptyList()) }
    var isStorylineExpanded by remember { mutableStateOf(false) }

    // Download modal state
    var showDownloadSheet by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    // Function to scrape and update video links for a selected bundle
    fun scrapeLinksForBundle(mDetail: BanglaMovieDetail, bundleIdx: Int) {
        isScrapingLinks = true
        coroutineScope.launch {
            try {
                val links = BanglaMovieLinkScraper.resolveMediaLinks(mDetail, bundleIdx)
                scrapedLinks = links
                selectedLinkIndex = 0
                val bestPlayable = links.firstOrNull { it.isDirectVideo } ?: links.firstOrNull()
                activePlayUrl = bestPlayable?.playUrl
            } catch (_: Exception) {
            } finally {
                isScrapingLinks = false
            }
        }
    }

    // Load movie details on start or when movieId changes
    LaunchedEffect(movieId) {
        isLoadingDetail = true
        detail = null
        scrapedLinks = emptyList()
        activePlayUrl = null
        selectedBundleIndex = 0
        selectedLinkIndex = 0

        val fetched = BanglaMovieApiClient.fetchMovieDetail(movieId)
        detail = fetched
        isLoadingDetail = false

        if (fetched != null) {
            scrapeLinksForBundle(fetched, 0)

            // Fetch related movies from same platform
            if (fetched.platform.isNotBlank()) {
                coroutineScope.launch {
                    try {
                        val res = BanglaMovieApiClient.fetchMovies(page = 1, limit = 10, platform = fetched.platform)
                        relatedMovies = res.movies.filter { it.id != movieId }
                    } catch (_: Exception) {}
                }
            }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column {
                            Text(
                                text = detail?.title ?: "ভিডিও প্লেয়ার ও বিবরণ",
                                color = TextPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (detail?.platform?.isNotBlank() == true) {
                                Text(
                                    text = "প্ল্যাটফর্ম: ${detail!!.platform}",
                                    color = BrandRedLight,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Share Button
                    IconButton(
                        onClick = {
                            val currentTitle = detail?.title ?: "Bangla OTT Movie"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, currentTitle)
                                putExtra(Intent.EXTRA_TEXT, "$currentTitle\nদেখুন Mukul Plus বাংলা ওটিটি অ্যাপে!\n${activePlayUrl ?: ""}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }

                    // Downloads Shortcut Button
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Downloads", tint = CyanAccent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLoadingDetail) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(42.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "মুভি ও ভিডিও লিংক লোড হচ্ছে...",
                        color = TextSecondary,
                        fontSize = 13.5.sp
                    )
                }
            }
        } else if (detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = BrandRed, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("মুভির তথ্য পাওয়া যায়নি", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = BrandRed)) {
                        Text("ক্যাটালগে ফিরে যান")
                    }
                }
            }
        } else {
            val movie = detail!!
            val currentScrapedLink = scrapedLinks.getOrNull(selectedLinkIndex)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // ============================================================
                // 1. HERO 16:9 VIDEO PLAYER (হুবহু ওটিটি প্লেয়ারের মতো)
                // ============================================================
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        if (isScrapingLinks) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.85f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = BrandRed, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "ভিডিও স্ট্রিম লিঙ্ক সংগ্রহ ও প্রস্তুত করা হচ্ছে...",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else if (!activePlayUrl.isNullOrEmpty() && (currentScrapedLink?.isDirectVideo == true || activePlayUrl!!.contains("pixeldrain", ignoreCase = true) || activePlayUrl!!.endsWith(".mp4") || activePlayUrl!!.endsWith(".mkv") || activePlayUrl!!.endsWith(".m3u8"))) {
                            // Direct Video Stream using ExoPlayer
                            VideoPlayerView(
                                videoUrl = activePlayUrl!!,
                                title = movie.title,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            // Fallback Stream View with backdrop poster and quick buttons
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = movie.poster,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.95f))
                                            )
                                        )
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = BrandRed.copy(alpha = 0.25f),
                                            modifier = Modifier.size(50.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayCircle,
                                                    contentDescription = null,
                                                    tint = BrandRed,
                                                    modifier = Modifier.size(34.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "অনলাইন স্ট্রিম প্রস্তুত",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "ক্লাউড সুরক্ষা থাকায় ব্রাউজার বা এক্সটার্নাল প্লেয়ার এ সরাসরি দেখতে পারবেন",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    val targetUrl = activePlayUrl ?: movie.qualities.firstOrNull()?.downloadUrl ?: ""
                                                    if (targetUrl.isNotBlank()) {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "ব্রাউজার খোলা যায়নি", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("ব্রাউজারে ভিডিও দেখুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            OutlinedButton(
                                                onClick = { showDownloadSheet = true },
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("ডাউনলোড", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 2. SERVER & QUALITY SELECTOR (স্ক্র্যাপ করা সার্ভার ও রেজুলেশন)
                // ============================================================
                item {
                    Surface(
                        color = CinemaSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "সার্ভার ও রেজুলেশন নির্বাচন:",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (scrapedLinks.isNotEmpty()) {
                                    Text(
                                        text = "${scrapedLinks.size} টি স্ট্রিম সোর্স",
                                        color = TextMuted,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (scrapedLinks.isEmpty() && !isScrapingLinks) {
                                Text(
                                    text = "সরাসরি সার্ভার লিংক পাওয়া যায়নি",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            } else {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    scrapedLinks.forEachIndexed { idx, sLink ->
                                        val isSelected = selectedLinkIndex == idx
                                        Surface(
                                            onClick = {
                                                selectedLinkIndex = idx
                                                activePlayUrl = sLink.playUrl
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSelected) BrandRedLight else CinemaBorder
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (sLink.isDirectVideo) Icons.Default.PlayCircleFilled else Icons.Default.CloudQueue,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else CyanAccent,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = "${sLink.serverName} • ${sLink.quality}${if (sLink.fileSize.isNotEmpty()) " (${sLink.fileSize})" else ""}",
                                                    color = if (isSelected) Color.White else TextPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 3. ACTION BUTTONS: PLAY, DOWNLOAD, BROWSER, COPY
                // ============================================================
                item {
                    Surface(
                        color = CinemaSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Play Button
                            Button(
                                onClick = {
                                    val currentUrl = activePlayUrl ?: scrapedLinks.firstOrNull()?.playUrl
                                    if (!currentUrl.isNullOrEmpty()) {
                                        activePlayUrl = currentUrl
                                        Toast.makeText(context, "ভিডিও প্লেয়ার সক্রিয় হচ্ছে", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("প্লে", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // 2. Download Button
                            Button(
                                onClick = { showDownloadSheet = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3C72)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(36.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ডাউনলোড", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // 3. Open in Browser Button
                            FilledTonalButton(
                                onClick = {
                                    val targetUrl = activePlayUrl ?: movie.qualities.firstOrNull()?.downloadUrl
                                    if (!targetUrl.isNullOrBlank()) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "ব্রাউজার খোলা যায়নি", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CinemaSurface,
                                    contentColor = TextPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Language, contentDescription = "Browser", modifier = Modifier.size(16.dp))
                            }

                            // 4. Copy Link Button
                            FilledTonalButton(
                                onClick = {
                                    val targetUrl = activePlayUrl ?: movie.qualities.firstOrNull()?.downloadUrl ?: ""
                                    if (targetUrl.isNotBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Movie Link", targetUrl)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "ভিডিও লিংক কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CinemaSurface,
                                    contentColor = CyanAccent
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // ============================================================
                // 4. SERIES EPISODES PICKER (পর্বসমূহ)
                // ============================================================
                val bundles = movie.downloadServers.firstOrNull()?.episodeBundles ?: emptyList()
                if (bundles.isNotEmpty()) {
                    item {
                        Surface(
                            color = CinemaSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "সিরিজের পর্বসমূহ (Episodes):",
                                        color = TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    bundles.forEachIndexed { bIdx, b ->
                                        val isCurrentBundle = selectedBundleIndex == bIdx
                                        Surface(
                                            onClick = {
                                                selectedBundleIndex = bIdx
                                                scrapeLinksForBundle(movie, bIdx)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isCurrentBundle) CyanAccent.copy(alpha = 0.2f) else CinemaSurfaceVariant,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isCurrentBundle) CyanAccent else CinemaBorder
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = if (isCurrentBundle) CyanAccent else TextMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = b.episodeRange,
                                                    color = if (isCurrentBundle) CyanAccent else TextPrimary,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = if (isCurrentBundle) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 5. OTT METADATA, STORYLINE & CAST (বিশদ বিবরণ)
                // ============================================================
                item {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Title
                        Text(
                            text = movie.title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Metadata Badges (Platform, Rating, Quality, Type, Language)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Platform
                            if (movie.platform.isNotBlank()) {
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
                                Surface(color = platformColor, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = movie.platform,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    )
                                }
                            }

                            // Rating
                            if (movie.rating > 0.0) {
                                Surface(color = Color(0xFFFFB020).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(text = "${movie.rating}", color = Color(0xFFFFB020), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Type
                            Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = if (movie.type == "SERIES") "ওয়েব সিরিজ" else "মুভি",
                                    color = CyanAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Print Quality
                            if (movie.printQuality.isNotBlank()) {
                                Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = movie.printQuality,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Language
                            if (movie.language.isNotBlank()) {
                                Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = movie.language,
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Release Date & Views
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (movie.releaseDate.isNotBlank()) {
                                Text(text = "মুক্তি: ${movie.releaseDate}", color = TextMuted, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            if (movie.views > 0) {
                                Text(text = "ভিউ: ${movie.views} বার", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        // Storyline (কাহিনী সংক্ষেপ)
                        if (movie.storyline.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(text = "কাহিনী সংক্ষেপ:", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = movie.storyline,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                maxLines = if (isStorylineExpanded) 100 else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (movie.storyline.length > 120) {
                                TextButton(
                                    onClick = { isStorylineExpanded = !isStorylineExpanded },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = if (isStorylineExpanded) "কম দেখুন" else "আরও পড়ুন...",
                                        color = CyanAccent,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Cast
                        if (movie.cast.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(text = "অভিনয়ে:", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = movie.cast, color = TextMuted, fontSize = 11.5.sp)
                        }

                        // Screenshots Gallery
                        if (movie.screenshots.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(text = "সিনেমার দৃশ্যপট (Screenshots):", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(movie.screenshots) { scUrl ->
                                    AsyncImage(
                                        model = scUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(140.dp)
                                            .height(82.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, CinemaBorder, RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                // ============================================================
                // 6. RELATED MOVIES FROM THIS OTT PLATFORM
                // ============================================================
                if (relatedMovies.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(
                                text = "${movie.platform} এর অন্যান্য কনটেন্ট",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(relatedMovies) { rMovie ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                        modifier = Modifier
                                            .width(115.dp)
                                            .clickable { onSelectRelatedMovie(rMovie.id) }
                                    ) {
                                        Column {
                                            AsyncImage(
                                                model = rMovie.poster,
                                                contentDescription = rMovie.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(150.dp)
                                            )
                                            Text(
                                                text = rMovie.title,
                                                color = TextPrimary,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
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

        // ============================================================
        // 7. DOWNLOAD QUALITY SELECTION MODAL
        // ============================================================
        if (showDownloadSheet && detail != null) {
            val d = detail!!
            val qualitiesToDownload = d.qualities.ifEmpty {
                d.downloadServers.firstOrNull()?.qualities.orEmpty().ifEmpty {
                    d.downloadServers.firstOrNull()?.episodeBundles?.getOrNull(selectedBundleIndex)?.qualities.orEmpty()
                }
            }

            AlertDialog(
                onDismissRequest = { showDownloadSheet = false },
                containerColor = CinemaSurface,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = BrandRed, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ডাউনলোড রেজুলেশন", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = d.title,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (qualitiesToDownload.isEmpty()) {
                            Text(text = "কোন ডাউনলোড লিংক পাওয়া যায়নি", color = TextMuted, fontSize = 11.5.sp)
                        } else {
                            qualitiesToDownload.forEach { q ->
                                Surface(
                                    onClick = {
                                        showDownloadSheet = false
                                        // Scrape direct download URL if needed
                                        coroutineScope.launch {
                                            val resolved = BanglaMovieLinkScraper.scrapeSingleUrl(q.downloadUrl, quality = q.label, fileSize = q.size)
                                            InAppDownloader.startDownload(
                                                context = context,
                                                movieSlug = "bangla_${d.id}",
                                                title = d.title,
                                                poster = d.poster,
                                                quality = q.label,
                                                downloadUrl = resolved.downloadUrl
                                            )
                                            Toast.makeText(context, "${q.label} ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = q.label, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        if (q.size.isNotBlank()) {
                                            Text(text = q.size, color = CyanAccent, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDownloadSheet = false }) {
                        Text("বন্ধ করুন", color = BrandRed)
                    }
                }
            )
        }
    }
}
