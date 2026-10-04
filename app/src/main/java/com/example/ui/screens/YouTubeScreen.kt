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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import com.example.data.util.findActivity
import com.example.data.util.VideoPlayerState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.YouTubeApiService
import com.example.data.api.YouTubeVideoItem
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor

enum class YouTubeCardLayout {
    TILE, GRID, LIST
}

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
    val prefs = remember { context.getSharedPreferences("mukul_prefs", android.content.Context.MODE_PRIVATE) }

    // Card Layout Selection: Tile, Grid, List (saved automatically locally)
    var cardLayoutMode by remember {
        val saved = prefs.getString("yt_card_layout", "TILE") ?: "TILE"
        mutableStateOf(try { YouTubeCardLayout.valueOf(saved) } catch (_: Exception) { YouTubeCardLayout.TILE })
    }

    fun updateCardLayout(mode: YouTubeCardLayout) {
        cardLayoutMode = mode
        prefs.edit().putString("yt_card_layout", mode.name).apply()
    }

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

    // Background playback & state tracking
    LaunchedEffect(activeVideo) {
        VideoPlayerState.isPlaying = (activeVideo != null)
    }

    // Restore orientation when leaving YouTube player
    DisposableEffect(Unit) {
        onDispose {
            VideoPlayerState.isPlaying = false
            VideoPlayerState.isFullScreen = false
            context.findActivity()?.let { act ->
                try {
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } catch (_: Exception) {}
            }
        }
    }

    // Categories without emojis (Clean titles + vector icons)
    val categories = remember {
        listOf(
            YouTubeCategory("", "ট্রেন্ডিং", Icons.Default.TrendingUp),
            YouTubeCategory("bangla_song", "বাংলা গান", Icons.Default.MusicNote),
            YouTubeCategory("natok", "নাটক", Icons.Default.LiveTv),
            YouTubeCategory("movie_trailer", "মুভি ট্রেইলার", Icons.Default.Movie),
            YouTubeCategory("hindi_song", "হিন্দি গান", Icons.Default.Headphones),
            YouTubeCategory("islamic", "ইসলামিক", Icons.Default.WbTwilight),
            YouTubeCategory("news", "সংবাদ", Icons.Default.Newspaper),
            YouTubeCategory("gaming", "গেমিং", Icons.Default.SportsEsports)
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

    var isYouTubeFullScreen by remember { mutableStateOf(false) }

    // Immersive mode for YouTube Fullscreen
    val activity = context.findActivity()
    DisposableEffect(isYouTubeFullScreen) {
        VideoPlayerState.isFullScreen = isYouTubeFullScreen
        if (isYouTubeFullScreen && activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            VideoPlayerState.isFullScreen = false
            if (activity != null) {
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Back button handling
    BackHandler(enabled = true) {
        when {
            isYouTubeFullScreen -> {
                isYouTubeFullScreen = false
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
                // Top Custom Player Bar (Hidden in Fullscreen)
                if (!isYouTubeFullScreen) {
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

                            // Screen Rotate button
                            IconButton(
                                onClick = {
                                    val act = context.findActivity()
                                    if (act != null) {
                                        val orient = act.resources.configuration.orientation
                                        if (orient == Configuration.ORIENTATION_LANDSCAPE) {
                                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Rotate",
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Fullscreen toggle button
                            IconButton(
                                onClick = { isYouTubeFullScreen = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

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
                }

                // Dedicated Hardware-Accelerated Video Player
                val playerBoxModifier = if (isYouTubeFullScreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                }

                Box(
                    modifier = playerBoxModifier.background(Color.Black)
                ) {
                    var playerReloadKey by remember(currentVideo.id) { mutableIntStateOf(0) }

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
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                                        return android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
                                    }
                                }
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
                                                src="https://www.youtube-nocookie.com/embed/${currentVideo.id}?autoplay=1&playsinline=1&controls=1&enablejsapi=1&rel=0&modestbranding=1" 
                                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                                allowfullscreen>
                                            </iframe>
                                        </div>
                                    </body>
                                    </html>
                                """.trimIndent()

                                loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlData, "text/html", "UTF-8", null)
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
                                                src="https://www.youtube-nocookie.com/embed/${currentVideo.id}?autoplay=1&playsinline=1&controls=1&enablejsapi=1&rel=0&modestbranding=1" 
                                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                                allowfullscreen>
                                            </iframe>
                                        </div>
                                    </body>
                                    </html>
                                """.trimIndent()
                                webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlData, "text/html", "UTF-8", null)
                            }
                        }
                    )

                    // Floating controls for Fullscreen mode
                    if (isYouTubeFullScreen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val act = context.findActivity()
                                    if (act != null) {
                                        val orient = act.resources.configuration.orientation
                                        if (orient == Configuration.ORIENTATION_LANDSCAPE) {
                                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0x88000000), CircleShape)
                            ) {
                                Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate Screen", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { isYouTubeFullScreen = false },
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0x88000000), CircleShape)
                            ) {
                                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                if (!isYouTubeFullScreen) {
                // Quick Player Action Strip
                Surface(
                    color = CinemaSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎬 ফুল এইচডি প্লেয়ার",
                            color = CyanAccent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // YouTube App direct launch
                            TextButton(
                                onClick = {
                                    val ytIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(currentVideo.watchUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(ytIntent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "ইউটিউব অ্যাপ নেই", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = BrandRedLight, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("অ্যাপে দেখুন", color = BrandRedLight, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            // Browser launch fallback
                            IconButton(
                                onClick = {
                                    try {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(currentVideo.watchUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(browserIntent)
                                    } catch (_: Exception) {}
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Language, contentDescription = "Browser", tint = TextSecondary, modifier = Modifier.size(15.dp))
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

                            // Action Buttons Row (Fullscreen Play, Share)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { isYouTubeFullScreen = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.3f).height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ফুলস্ক্রিন প্লে",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, currentVideo.title)
                                            putExtra(Intent.EXTRA_TEXT, "Watch on YouTube: ${currentVideo.watchUrl}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "শেয়ার",
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
                                onPlayClick = { openVideo(relVideo) }
                            )
                        }
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

                            // Slim Responsive Search Box with full keyboard support (BasicTextField)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = CinemaBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = BrandRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        cursorBrush = SolidColor(BrandRed),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                if (searchQuery.isNotBlank()) {
                                                    loadFeed(query = searchQuery)
                                                }
                                            }
                                        ),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    "ইউটিউব খুঁজুন...",
                                                    color = TextMuted,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            innerTextField()
                                        }
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
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Card Layout Switcher: Tile (টাইল), Grid (গ্রিড), List (লিস্ট)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CinemaSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { updateCardLayout(YouTubeCardLayout.TILE) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewAgenda,
                                            contentDescription = "টাইল মোড",
                                            tint = if (cardLayoutMode == YouTubeCardLayout.TILE) BrandRed else TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { updateCardLayout(YouTubeCardLayout.GRID) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GridView,
                                            contentDescription = "গ্রিড মোড",
                                            tint = if (cardLayoutMode == YouTubeCardLayout.GRID) BrandRed else TextMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { updateCardLayout(YouTubeCardLayout.LIST) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewList,
                                            contentDescription = "লিস্ট মোড",
                                            tint = if (cardLayoutMode == YouTubeCardLayout.LIST) BrandRed else TextMuted,
                                            modifier = Modifier.size(17.dp)
                                        )
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
                    when (cardLayoutMode) {
                        YouTubeCardLayout.TILE -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(videoList, key = { it.id }) { video ->
                                    YouTubeVideoTileCard(
                                        video = video,
                                        onPlayClick = { openVideo(video) }
                                    )
                                }
                                item {
                                    LoadMoreButton(isLoadingMore = isLoadingMore, onClick = { loadMore() })
                                }
                            }
                        }
                        YouTubeCardLayout.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 120.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(videoList, key = { it.id }) { video ->
                                    YouTubeVideoGridCard(
                                        video = video,
                                        onPlayClick = { openVideo(video) }
                                    )
                                }
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                    LoadMoreButton(isLoadingMore = isLoadingMore, onClick = { loadMore() })
                                }
                            }
                        }
                        YouTubeCardLayout.LIST -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(videoList, key = { it.id }) { video ->
                                    YouTubeVideoCard(
                                        video = video,
                                        onPlayClick = { openVideo(video) }
                                    )
                                }
                                item {
                                    LoadMoreButton(isLoadingMore = isLoadingMore, onClick = { loadMore() })
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
    onPlayClick: () -> Unit
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

                // Direct Action Button: Slim Play
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandRed.copy(alpha = 0.2f),
                            contentColor = BrandRedLight
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("প্লে", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * YouTube Video Tile Card (ওয়াইড টাইল কার্ড মোড)
 */
@Composable
private fun YouTubeVideoTileCard(
    video: YouTubeVideoItem,
    onPlayClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clickable(onClick = onPlayClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 16:9 Wide Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
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
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "HD",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Center Play Icon Overlay
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Duration Badge (Bottom-Right)
                if (video.duration.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Info & Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = video.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = video.channelTitle,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (video.viewCount.isNotEmpty()) {
                            Text(
                                text = " • ${video.viewCount}",
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Action button: Play
                    FilledTonalButton(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandRed,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("প্লে করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * YouTube Video Grid Card (২-কলাম গ্রিড কার্ড মোড)
 */
@Composable
private fun YouTubeVideoGridCard(
    video: YouTubeVideoItem,
    onPlayClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 16:9 Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(CinemaSurfaceVariant)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Duration Badge
                if (video.duration.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(7.dp)) {
                Text(
                    text = video.title,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.channelTitle,
                    color = TextSecondary,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandRed.copy(alpha = 0.2f),
                            contentColor = BrandRedLight
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.fillMaxWidth().height(25.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("প্লে করুন", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Reusable Load More Button
 */
@Composable
private fun LoadMoreButton(
    isLoadingMore: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
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

/**
 * Compact Video Row Item (used for related videos in player page)
 */
@Composable
private fun YouTubeVideoRowItem(
    video: YouTubeVideoItem,
    onPlayClick: () -> Unit
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

            Surface(
                shape = CircleShape,
                color = BrandRed.copy(alpha = 0.15f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = BrandRedLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
