package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.YouTubeApiService
import com.example.data.api.YouTubeVideoItem
import com.example.data.download.*
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class YouTubeCategory(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun YouTubeScreen(
    onNavigateToDownloads: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen State with in-memory caching to save user data
    var searchQuery by remember { mutableStateOf(com.example.data.api.YouTubeFeedCache.cachedQuery) }
    var selectedCategory by remember { mutableStateOf(com.example.data.api.YouTubeFeedCache.cachedCategory) }
    var isLoadingFeed by remember { mutableStateOf(com.example.data.api.YouTubeFeedCache.cachedVideos.isEmpty()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var videoList by remember { mutableStateOf<List<YouTubeVideoItem>>(com.example.data.api.YouTubeFeedCache.cachedVideos) }

    // Selected Video for Play Page
    var activeVideo by remember { mutableStateOf<YouTubeVideoItem?>(null) }
    var relatedVideos by remember { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }
    var isLoadingRelated by remember { mutableStateOf(false) }

    // Download & Resolution Dialog State
    var targetDownloadVideo by remember { mutableStateOf<YouTubeVideoItem?>(null) }
    var showResolutionDialog by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractionStatus by remember { mutableStateOf("") }
    var extractionJob by remember { mutableStateOf<Job?>(null) }
    var extractionResult by remember { mutableStateOf<ExtractionResult?>(null) }

    // Categories
    val categories = remember {
        listOf(
            YouTubeCategory("", "🔥 ট্রেন্ডিং (Trending)", Icons.Default.TrendingUp),
            YouTubeCategory("bangla_song", "🎵 বাংলা গান", Icons.Default.MusicNote),
            YouTubeCategory("natok", "🎭 নাটক (Natok)", Icons.Default.LiveTv),
            YouTubeCategory("movie_trailer", "🎬 ট্রেইলার", Icons.Default.Movie),
            YouTubeCategory("hindi_song", "🎶 হিন্দি গান", Icons.Default.Headphones),
            YouTubeCategory("islamic", "🌙 ইসলামিক", Icons.Default.WbTwilight),
            YouTubeCategory("news", "📰 সংবাদ (News)", Icons.Default.Newspaper),
            YouTubeCategory("gaming", "🎮 গেমিং", Icons.Default.SportsEsports)
        )
    }

    // Function to load feed with cache updating
    fun loadFeed(category: String = "", query: String = "", force: Boolean = false) {
        coroutineScope.launch {
            if (videoList.isEmpty() || force) {
                isLoadingFeed = true
            }
            val loaded = if (query.isNotBlank()) {
                YouTubeApiService.searchVideos(query, forceRefresh = force)
            } else {
                YouTubeApiService.getTrendingVideos(category, forceRefresh = force)
            }
            if (loaded.isNotEmpty()) {
                videoList = loaded
                com.example.data.api.YouTubeFeedCache.cachedVideos = loaded
                com.example.data.api.YouTubeFeedCache.cachedCategory = category
                com.example.data.api.YouTubeFeedCache.cachedQuery = query
                com.example.data.api.YouTubeFeedCache.isLoaded = true
            }
            isLoadingFeed = false
        }
    }

    // Function to load more videos (Load More button)
    fun loadMore() {
        if (isLoadingMore) return
        coroutineScope.launch {
            isLoadingMore = true
            val more = if (searchQuery.isNotBlank()) {
                YouTubeApiService.searchVideos(searchQuery, maxResults = 25)
            } else {
                YouTubeApiService.getTrendingVideos(selectedCategory)
            }
            val existingIds = videoList.map { it.id }.toSet()
            val newVideos = more.filter { it.id !in existingIds }
            val combined = videoList + if (newVideos.isNotEmpty()) newVideos else more
            videoList = combined
            com.example.data.api.YouTubeFeedCache.cachedVideos = combined
            isLoadingMore = false
        }
    }

    // Function to open Video Play Page
    fun openVideo(video: YouTubeVideoItem) {
        activeVideo = video
        coroutineScope.launch {
            isLoadingRelated = true
            relatedVideos = YouTubeApiService.getRelatedVideos(video.id, video.title)
            isLoadingRelated = false
        }
    }

    // Initial load: Only load if cache is empty
    LaunchedEffect(Unit) {
        if (com.example.data.api.YouTubeFeedCache.cachedVideos.isNotEmpty()) {
            videoList = com.example.data.api.YouTubeFeedCache.cachedVideos
            isLoadingFeed = false
        } else {
            loadFeed()
        }
    }

    // Back button handling
    BackHandler(enabled = true) {
        when {
            showResolutionDialog -> {
                if (!isExtracting) showResolutionDialog = false
            }
            activeVideo != null -> {
                activeVideo = null
            }
            searchQuery.isNotBlank() -> {
                searchQuery = ""
                loadFeed(selectedCategory)
            }
            onBack != null -> {
                onBack()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        if (activeVideo != null) {
            // ==============================================================
            // 1. CUSTOM VIDEO PLAY PAGE
            // ==============================================================
            val currentVideo = activeVideo!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CinemaBackground)
            ) {
                // Top Custom Player Bar
                Surface(
                    color = CinemaSurface,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { activeVideo = null },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentVideo.title,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, currentVideo.title)
                                    putExtra(Intent.EXTRA_TEXT, "Watch on Mukul Plus: ${currentVideo.watchUrl}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Dedicated Compact Hardware-Accelerated Video Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Black)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                tag = currentVideo.id
                                setBackgroundColor(android.graphics.Color.BLACK)
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    databaseEnabled = true
                                    allowContentAccess = true
                                    allowFileAccess = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                                }

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
                                }

                                val htmlData = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                        <style>
                                            * { margin:0; padding:0; box-sizing:border-box; }
                                            html, body { width:100%; height:100%; background:#000000; overflow:hidden; display:flex; align-items:center; justify-content:center; }
                                            .player-container { position:relative; width:100%; height:100%; }
                                            iframe { position:absolute; top:0; left:0; width:100%; height:100%; border:0; }
                                        </style>
                                    </head>
                                    <body>
                                        <div class="player-container">
                                            <iframe 
                                                src="https://www.youtube.com/embed/${currentVideo.id}?autoplay=1&playsinline=1&enablejsapi=1&rel=0&modestbranding=1&controls=1&fs=1&origin=https://www.youtube.com" 
                                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                                allowfullscreen>
                                            </iframe>
                                        </div>
                                    </body>
                                    </html>
                                """.trimIndent()

                                loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)
                            }
                        },
                        update = { webView ->
                            val lastId = webView.tag as? String
                            if (lastId != currentVideo.id) {
                                webView.tag = currentVideo.id
                                val htmlData = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                        <style>
                                            * { margin:0; padding:0; box-sizing:border-box; }
                                            html, body { width:100%; height:100%; background:#000000; overflow:hidden; display:flex; align-items:center; justify-content:center; }
                                            .player-container { position:relative; width:100%; height:100%; }
                                            iframe { position:absolute; top:0; left:0; width:100%; height:100%; border:0; }
                                        </style>
                                    </head>
                                    <body>
                                        <div class="player-container">
                                            <iframe 
                                                src="https://www.youtube.com/embed/${currentVideo.id}?autoplay=1&playsinline=1&enablejsapi=1&rel=0&modestbranding=1&controls=1&fs=1&origin=https://www.youtube.com" 
                                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                                allowfullscreen>
                                            </iframe>
                                        </div>
                                    </body>
                                    </html>
                                """.trimIndent()
                                webView.loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)
                            }
                        }
                    )
                }

                // Quick Player Action Strip
                Surface(
                    color = CinemaSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎬 ফুল এইচডি ভিডিও প্লেয়ার",
                            color = CyanAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    val ytIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(currentVideo.watchUrl))
                                    try {
                                        context.startActivity(ytIntent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "ইউটিউব অ্যাপ নেই", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = BrandRedLight, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("অ্যাপে দেখুন", color = BrandRedLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    targetDownloadVideo = currentVideo
                                    showResolutionDialog = true
                                    extractionResult = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Download", tint = TextPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Video Details & Related Videos List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Video Information Block
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = currentVideo.title,
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (currentVideo.viewCount.isNotEmpty()) {
                                    Text(
                                        text = currentVideo.viewCount,
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                if (currentVideo.publishedTime.isNotEmpty()) {
                                    Text(
                                        text = "• ${currentVideo.publishedTime}",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Buttons Row (Download, Share, Downloads Page)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        targetDownloadVideo = currentVideo
                                        showResolutionDialog = true
                                        extractionResult = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.3f).height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "Download",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ডাউনলোড",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = onNavigateToDownloads,
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "ডাউনলোডস",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Channel Information Card
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CinemaSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(BrandRedDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = currentVideo.channelTitle.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentVideo.channelTitle,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "অফিসিয়াল ইউটিউব চ্যানেল",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = BrandRed.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "ইউটিউব ভেরিফাইড",
                                            color = BrandRedLight,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Related Videos Header
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            HorizontalDivider(color = CinemaBorder, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "সম্পর্কিত আরও ভিডিও (Recommended)",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Related Videos List
                    if (isLoadingRelated) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else {
                        items(relatedVideos) { relVideo ->
                            YouTubeVideoRowItem(
                                video = relVideo,
                                onPlayClick = { openVideo(relVideo) },
                                onDownloadClick = {
                                    targetDownloadVideo = relVideo
                                    showResolutionDialog = true
                                    extractionResult = null
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // ==============================================================
            // 2. MAIN BROWSE / SEARCH PAGE
            // ==============================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CinemaBackground)
            ) {
                // Top Slim Responsive Header with Search Bar (চিকন ও রেসপনসিভ)
                Surface(
                    color = CinemaSurface,
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        // Single unified responsive search row (Height ~40dp, well below 150px)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            if (onBack != null) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                // YouTube Mini Brand Badge
                                Surface(
                                    color = BrandRed,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.size(26.dp, 19.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }

                            // Slim Responsive Search Box
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = CinemaBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = BrandRed,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = {
                                            Text(
                                                "ইউটিউব খুঁজুন...",
                                                color = TextMuted,
                                                fontSize = 11.5.sp
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                if (searchQuery.isNotBlank()) {
                                                    loadFeed(query = searchQuery)
                                                }
                                            }
                                        ),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary,
                                            cursorColor = BrandRed
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                loadFeed(selectedCategory)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = TextMuted,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Refresh Feed Button
                            IconButton(
                                onClick = { loadFeed(selectedCategory, force = true) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }

                            // Downloads Folder Button
                            IconButton(
                                onClick = onNavigateToDownloads,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Downloads",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Compact Category Chips Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                val isSelected = selectedCategory == cat.id && searchQuery.isBlank()
                                Surface(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCategory = cat.id
                                        loadFeed(category = cat.id)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) BrandRed else CinemaBorder
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else TextMuted,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = cat.title,
                                            color = if (isSelected) Color.White else TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Video List Content
                if (isLoadingFeed) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BrandRed)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("ইউটিউব ভিডিও লোড হচ্ছে...", color = TextMuted, fontSize = 13.sp)
                        }
                    }
                } else if (videoList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "কোনো ভিডিও খুঁজে পাওয়া যায়নি",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "দয়া করে অন্য কোনো শব্দ দিয়ে সার্চ করুন",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    loadFeed()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("হোম পেজে ফিরুন")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(videoList) { video ->
                            YouTubeVideoCard(
                                video = video,
                                onPlayClick = { openVideo(video) },
                                onDownloadClick = {
                                    targetDownloadVideo = video
                                    showResolutionDialog = true
                                    extractionResult = null
                                }
                            )
                        }

                        // Bottom Load More Button (নিচে লোড বাটন থাকবে)
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { loadMore() },
                                    enabled = !isLoadingMore,
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("ভিডিও লোড হচ্ছে...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("আরও লোড করুন (Load More)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==============================================================
        // 3. RESOLUTION SELECTION & DOWNLOAD DIALOG
        // ==============================================================
        if (showResolutionDialog && targetDownloadVideo != null) {
            val videoToDownload = targetDownloadVideo!!

            Dialog(onDismissRequest = {
                if (!isExtracting) {
                    showResolutionDialog = false
                    targetDownloadVideo = null
                }
            }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        // Dialog Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (extractionResult != null) "ভিডিও প্রস্তুত!" else "রেজুলেশন নির্বাচন করুন",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    extractionJob?.cancel()
                                    isExtracting = false
                                    showResolutionDialog = false
                                    targetDownloadVideo = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Target Video Preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CinemaSurfaceVariant, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(videoToDownload.thumbnailUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(70.dp, 44.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = videoToDownload.title,
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = videoToDownload.channelTitle,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Extraction Progress
                        if (isExtracting) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = extractionStatus.ifEmpty { "ডাউনলোড লিঙ্ক তৈরি করা হচ্ছে..." },
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "অনুগ্রহ করে কয়েক সেকেন্ড অপেক্ষা করুন",
                                    color = TextMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        } else if (extractionResult != null) {
                            // Extraction Success State
                            val res = extractionResult!!
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "সরাসরি ডাউনলোড শুরু করুন",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        val taskId = InAppDownloader.startDownload(
                                            context = context,
                                            movieSlug = "yt_${res.videoId}",
                                            title = videoToDownload.title,
                                            poster = res.thumbnail.ifEmpty { videoToDownload.thumbnailUrl },
                                            quality = res.format,
                                            downloadUrl = res.downloadUrl
                                        )
                                        Toast.makeText(context, "ডাউনলোড শুরু হয়েছে! ডাউনলোড পেজে দেখুন", Toast.LENGTH_LONG).show()
                                        showResolutionDialog = false
                                        targetDownloadVideo = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(44.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("এখনই ডাউনলোড শুরু করুন", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Resolutions List
                            Text(
                                text = "পছন্দের রেজুলেশন ট্যাপ করুন:",
                                color = TextMuted,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(YouTubeDownloaderHelper.availableResolutions) { resItem ->
                                    Surface(
                                        onClick = {
                                            isExtracting = true
                                            extractionStatus = "${resItem.title} প্রসেস করা হচ্ছে..."
                                            extractionJob = coroutineScope.launch {
                                                val extractResult = YouTubeDownloaderHelper.extractDownloadUrl(
                                                    youtubeUrl = videoToDownload.watchUrl,
                                                    format = resItem.format,
                                                    onProgressStatus = { extractionStatus = it }
                                                )
                                                isExtracting = false
                                                if (extractResult.isSuccess) {
                                                    val res = extractResult.getOrNull()
                                                    if (res != null && res.downloadUrl.isNotEmpty()) {
                                                        InAppDownloader.startDownload(
                                                            context = context,
                                                            movieSlug = "yt_${res.videoId}",
                                                            title = videoToDownload.title,
                                                            poster = res.thumbnail.ifEmpty { videoToDownload.thumbnailUrl },
                                                            quality = res.format,
                                                            downloadUrl = res.downloadUrl
                                                        )
                                                        Toast.makeText(context, "ডাউনলোড শুরু হয়েছে! ডাউনলোড পেজে দেখুন", Toast.LENGTH_LONG).show()
                                                        showResolutionDialog = false
                                                        targetDownloadVideo = null
                                                    } else {
                                                        extractionResult = res
                                                    }
                                                } else {
                                                    val err = extractResult.exceptionOrNull()?.message ?: "ডাউনলোড লিঙ্ক তৈরি করতে ব্যর্থ হয়েছে"
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = CinemaSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (resItem.isAudio) Icons.Default.MusicNote else Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                tint = if (resItem.isAudio) GoldRating else BrandRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = resItem.title,
                                                    color = TextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = resItem.subtitle,
                                                    color = TextMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.ArrowForwardIos,
                                                contentDescription = null,
                                                tint = TextMuted,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val browserUrl = YouTubeDownloaderHelper.getBrowserDownloadUrl(videoToDownload.id)
                                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(browserUrl))
                                            try {
                                                context.startActivity(intent)
                                                showResolutionDialog = false
                                                targetDownloadVideo = null
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "ব্রাউজার খোলা যায়নি", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                                        modifier = Modifier.fillMaxWidth().height(38.dp)
                                    ) {
                                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🌐 ব্রাউজারে দ্রুত ডাউনলোড (SaveFrom)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

/**
 * Slim & Sleek YouTube Video Card (চিকন ও সরু কার্ড ডিজাইন)
 */
@Composable
private fun YouTubeVideoCard(
    video: YouTubeVideoItem,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .clickable(onClick = onPlayClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Compact 16:9 Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 105.dp, height = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaSurfaceVariant)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // HD Badge at Top-Left
                Surface(
                    color = BrandRed.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "HD",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                    )
                }

                // Play icon in center
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center)
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

                // Duration Badge on Photo (Bottom-Right)
                if (video.duration.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(9.dp))

            // Right: Video Title, Channel, Stats, and Slim Action Buttons
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.title,
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Channel Info & Views in compact row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = video.channelTitle,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (video.viewCount.isNotEmpty()) {
                        Text(
                            text = " • ${video.viewCount}",
                            color = TextMuted,
                            fontSize = 9.5.sp,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Direct Action Buttons: Slim Play & Download
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CinemaSurfaceVariant,
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("প্লে", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = onDownloadClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("ডাউনলোড", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Compact Video Row Item (used for related videos in player page)
 */
@Composable
private fun YouTubeVideoRowItem(
    video: YouTubeVideoItem,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Surface(
        onClick = onPlayClick,
        shape = RoundedCornerShape(14.dp),
        color = CinemaSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(90.dp, 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaSurfaceVariant)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (video.duration.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.channelTitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Download",
                    tint = BrandRed,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
