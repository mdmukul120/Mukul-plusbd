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

    // Screen State
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var isLoadingFeed by remember { mutableStateOf(false) }
    var videoList by remember { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }

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

    // Function to load feed
    fun loadFeed(category: String = "", query: String = "") {
        coroutineScope.launch {
            isLoadingFeed = true
            if (query.isNotBlank()) {
                videoList = YouTubeApiService.searchVideos(query)
            } else {
                videoList = YouTubeApiService.getTrendingVideos(category)
            }
            isLoadingFeed = false
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

    // Initial load
    LaunchedEffect(Unit) {
        loadFeed()
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

                // Dedicated Player View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .background(Color.Black)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT

                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        return false
                                    }
                                }

                                val htmlData = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                        <style>
                                            * { margin:0; padding:0; box-sizing:border-box; }
                                            body, html { width:100%; height:100%; background:#000000; overflow:hidden; }
                                            iframe { width:100%; height:100%; border:none; }
                                        </style>
                                    </head>
                                    <body>
                                        <iframe 
                                            src="https://www.youtube-nocookie.com/embed/${currentVideo.id}?autoplay=1&playsinline=1&rel=0&modestbranding=1" 
                                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                            allowfullscreen>
                                        </iframe>
                                    </body>
                                    </html>
                                """.trimIndent()

                                loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlData, "text/html", "UTF-8", null)
                            }
                        },
                        update = { webView ->
                            val htmlData = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                    <style>
                                        * { margin:0; padding:0; box-sizing:border-box; }
                                        body, html { width:100%; height:100%; background:#000000; overflow:hidden; }
                                        iframe { width:100%; height:100%; border:none; }
                                    </style>
                                </head>
                                <body>
                                    <iframe 
                                        src="https://www.youtube-nocookie.com/embed/${currentVideo.id}?autoplay=1&playsinline=1&rel=0&modestbranding=1" 
                                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                        allowfullscreen>
                                    </iframe>
                                </body>
                                </html>
                            """.trimIndent()
                            webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", htmlData, "text/html", "UTF-8", null)
                        }
                    )
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
                // Top Header with Search Box
                Surface(
                    color = CinemaSurface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (onBack != null) {
                                Surface(
                                    onClick = onBack,
                                    shape = RoundedCornerShape(12.dp),
                                    color = CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier.size(46.dp)
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

                            // Custom Search Input
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = CinemaBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = BrandRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = {
                                            Text(
                                                "ইউটিউব ভিডিও খুঁজুন (গান, নাটক, খবর)...",
                                                color = TextMuted,
                                                fontSize = 12.5.sp
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
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                loadFeed(selectedCategory)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Go to Downloads Folder Button
                            IconButton(
                                onClick = onNavigateToDownloads,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.FolderSpecial,
                                            contentDescription = "Downloads",
                                            tint = CyanAccent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category Chips Row
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) BrandRed else CinemaBorder
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else TextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cat.title,
                                            color = if (isSelected) Color.White else TextPrimary,
                                            fontSize = 12.sp,
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
                                                    extractionResult = extractResult.getOrNull()
                                                } else {
                                                    val err = extractResult.exceptionOrNull()?.message ?: "ডাউনলোড লিঙ্ক তৈরি করতে ব্যর্থ হয়েছে"
                                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = CinemaSurfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (resItem.isAudio) Icons.Default.MusicNote else Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                tint = if (resItem.isAudio) GoldRating else BrandRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = resItem.title,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = resItem.subtitle,
                                                    color = TextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.ArrowForwardIos,
                                                contentDescription = null,
                                                tint = TextMuted,
                                                modifier = Modifier.size(12.dp)
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
}

/**
 * Modern Full-width YouTube Video Card for Feed
 */
@Composable
private fun YouTubeVideoCard(
    video: YouTubeVideoItem,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CinemaSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clickable(onClick = onPlayClick)
    ) {
        Column {
            // Thumbnail container with Duration Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(CinemaSurfaceVariant)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark gradient at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                )

                // Play Button Overlay
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Duration Badge
                if (video.duration.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = video.duration,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Info and Action Buttons
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = video.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Channel & Views Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.channelTitle,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (video.viewCount.isNotEmpty()) {
                                Text(
                                    text = video.viewCount,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (video.publishedTime.isNotEmpty()) {
                                Text(
                                    text = "• ${video.publishedTime}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Direct Action Buttons: Play & Download
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = onPlayClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CinemaSurfaceVariant,
                                contentColor = TextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("প্লে", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDownloadClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("ডাউনলোড", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
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
