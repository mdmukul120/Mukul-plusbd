package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.YouTubeApiService
import com.example.data.api.YouTubeReelItem
import com.example.data.download.ExtractionResult
import com.example.data.download.InAppDownloader
import com.example.data.download.YouTubeDownloaderHelper
import com.example.data.download.YouTubeResolution
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * YouTube Shorts / Reels Screen
 * উপরে স্লাইড করলে নতুন ভিডিও আসবে, YouTube V3 অটো আপডেট ও কাস্টম প্লেয়ার
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen State
    var reelsList by remember { mutableStateOf<List<YouTubeReelItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var nextPageToken by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var isMuted by remember { mutableStateOf(false) }

    // Download Dialog State
    var downloadReelTarget by remember { mutableStateOf<YouTubeReelItem?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var isExtractingDownload by remember { mutableStateOf(false) }

    // Comments Sheet State
    var showCommentsSheet by remember { mutableStateOf(false) }
    var activeCommentsReel by remember { mutableStateOf<YouTubeReelItem?>(null) }

    // Load initial reels with search support and daily updates
    fun loadReels(category: String = selectedCategory, query: String = searchQuery, reset: Boolean = false) {
        coroutineScope.launch {
            if (reset) {
                isLoading = true
                nextPageToken = ""
            }
            try {
                val res = YouTubeApiService.getYouTubeReels(category = category, pageToken = "", searchQuery = query)
                reelsList = res.items
                nextPageToken = res.nextPageToken
            } catch (e: Exception) {
                // Keep existing or show error notice
            } finally {
                isLoading = false
            }
        }
    }

    // Load more reels for infinite auto-update
    fun loadMoreReels() {
        if (isLoadingMore || nextPageToken.isEmpty()) return
        coroutineScope.launch {
            isLoadingMore = true
            try {
                val res = YouTubeApiService.getYouTubeReels(category = selectedCategory, pageToken = nextPageToken, searchQuery = searchQuery)
                val newItems = res.items.filter { newItem -> reelsList.none { it.id == newItem.id } }
                if (newItems.isNotEmpty()) {
                    reelsList = reelsList + newItems
                }
                nextPageToken = res.nextPageToken
            } catch (_: Exception) {
            } finally {
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        loadReels(selectedCategory, query = searchQuery, reset = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading && reelsList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(42.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "নতুন রিলস লোড হচ্ছে...",
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (reelsList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SlowMotionVideo,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "কোনো রিলস পাওয়া যায়নি",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "পুনরায় লোড করতে নিচের বাটনে চাপ দিন",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadReels(selectedCategory, reset = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("পুনরায় চেষ্টা করুন")
                    }
                }
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { reelsList.size })

            // Auto-load next reels when nearing the end (অটো আপডেট)
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage >= reelsList.size - 2 && !isLoadingMore && nextPageToken.isNotEmpty()) {
                    loadMoreReels()
                }
            }

            // Vertical Pager (উপরে স্লাইড করলে নতুন ভিডিও আসবে)
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { pageIndex ->
                val reel = reelsList.getOrNull(pageIndex)
                if (reel != null) {
                    val isCurrentPage = pagerState.currentPage == pageIndex
                    ReelVideoItemView(
                        reel = reel,
                        isCurrentPage = isCurrentPage,
                        isMuted = isMuted,
                        onToggleMute = { isMuted = !isMuted },
                        onLikeClick = { /* Heart Animation and like handled inside */ },
                        onCommentClick = {
                            activeCommentsReel = reel
                            showCommentsSheet = true
                        },
                        onShareClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "মুকুল প্লাসে দেখুন সেরা রিলস: ${reel.title}\n${reel.watchUrl}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "রিলস শেয়ার করুন"))
                        },
                        onDownloadClick = {
                            downloadReelTarget = reel
                            showDownloadDialog = true
                        },
                        onOpenYouTube = {
                            val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse(reel.watchUrl))
                            try {
                                context.startActivity(ytIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "ইউটিউব অ্যাপ চালু করা যায়নি", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // Floating Top Overlay: Slim Search Bar & Category chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp)
        ) {
            // Slim Search Row (চিকন সার্চবার ও রিফ্রেশ বাটন)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onNavigateBack != null) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Slim Responsive Search Bar
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Reels",
                            tint = BrandRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "বাংলাদেশি রিলস খুঁজুন...",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.5.sp
                                )
                            },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Search
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onSearch = {
                                    loadReels(selectedCategory, query = searchQuery, reset = true)
                                }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = BrandRed
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    loadReels(selectedCategory, query = "", reset = true)
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // New Reels / Refresh Button (অটো আপডেট ও রিফ্রেশ)
                IconButton(
                    onClick = { loadReels(selectedCategory, query = searchQuery, reset = true) },
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Reels",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (onNavigateToProfile != null) {
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Surface(
                            color = BrandRed,
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Category Chips Row (বাংলাদেশ ভাইরাল, গান, নাটক, কমেডি ইত্যাদি)
            val categories = listOf(
                Pair("all", "🔥 সব"),
                Pair("viral", "🇧🇩 ভাইরাল"),
                Pair("comedy", "😂 কমেডি"),
                Pair("music", "🎵 গান"),
                Pair("natok", "🎭 নাটক"),
                Pair("islamic", "✨ ইসলামিক")
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(categories) { (key, label) ->
                    val isSelected = selectedCategory == key && searchQuery.isBlank()
                    Surface(
                        onClick = {
                            searchQuery = ""
                            selectedCategory = key
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) BrandRed else Color.Black.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) BrandRed else Color.White.copy(alpha = 0.25f)
                        )
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Download Bottom Sheet Dialog
        if (showDownloadDialog && downloadReelTarget != null) {
            val target = downloadReelTarget!!
            AlertDialog(
                onDismissRequest = {
                    if (!isExtractingDownload) {
                        showDownloadDialog = false
                        downloadReelTarget = null
                    }
                },
                containerColor = CinemaSurface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = BrandRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "রিলস ডাউনলোড করুন",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = target.title,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "রেজুলেশন নির্বাচন করুন:",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isExtractingDownload) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("ভিডিও লিঙ্ক সংগ্রহ হচ্ছে...", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                        } else {
                            val options = listOf(
                                Pair("720", "720p HD (সেরা কোয়ালিটি)"),
                                Pair("480", "480p SD (স্ট্যান্ডার্ড)"),
                                Pair("360", "360p ডাটা সেভার"),
                                Pair("mp3", "MP3 অডিও সাউন্ড")
                            )

                            options.forEach { (format, label) ->
                                Surface(
                                    onClick = {
                                        coroutineScope.launch {
                                            isExtractingDownload = true
                                            try {
                                                val extRes = YouTubeDownloaderHelper.extractDownloadUrl(target.watchUrl, format).getOrNull()
                                                if (extRes != null && extRes.downloadUrl.isNotEmpty()) {
                                                    InAppDownloader.startDownload(
                                                        context = context,
                                                        movieSlug = "reel_${target.id}",
                                                        title = target.title,
                                                        poster = target.thumbnailUrl,
                                                        quality = if (format == "mp3") "MP3" else "${format}p",
                                                        downloadUrl = extRes.downloadUrl
                                                    )
                                                    Toast.makeText(context, "রিলস ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                                    showDownloadDialog = false
                                                    downloadReelTarget = null
                                                } else {
                                                    Toast.makeText(context, "ডাউনলোড লিঙ্ক সংগ্রহ ব্যর্থ হয়েছে, পুনরায় চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isExtractingDownload = false
                                            }
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
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(label, color = TextPrimary, fontSize = 12.sp)
                                        Icon(Icons.Default.Download, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        showDownloadDialog = false
                        downloadReelTarget = null
                    }) {
                        Text("বাতিল", color = TextMuted)
                    }
                }
            )
        }

        // Comments Bottom Sheet
        if (showCommentsSheet && activeCommentsReel != null) {
            val reel = activeCommentsReel!!
            ModalBottomSheet(
                onDismissRequest = {
                    showCommentsSheet = false
                    activeCommentsReel = null
                },
                containerColor = CinemaSurface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.4f)) }
            ) {
                ReelsCommentsSheet(reel = reel)
            }
        }
    }
}

/**
 * Single Reel Player Item View (Vertical Edge-to-Edge)
 */
@Composable
private fun ReelVideoItemView(
    reel: YouTubeReelItem,
    isCurrentPage: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onOpenYouTube: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var showPlayPauseIndicator by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }
    var likesCount by remember { mutableStateOf(reel.likesCount) }
    var showHeartPop by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Double tap heart animation scale
    val heartScale by animateFloatAsState(
        targetValue = if (showHeartPop) 1.25f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "heartScale"
    )

    // Handle Page Pause / Play on swipe
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            isPlaying = true
            webViewRef?.evaluateJavascript("if(typeof playVideo === 'function') playVideo();", null)
        } else {
            isPlaying = false
            webViewRef?.evaluateJavascript("if(typeof pauseVideo === 'function') pauseVideo();", null)
        }
    }

    // Handle Mute toggle
    LaunchedEffect(isMuted) {
        if (isMuted) {
            webViewRef?.evaluateJavascript("if(typeof muteVideo === 'function') muteVideo();", null)
        } else {
            webViewRef?.evaluateJavascript("if(typeof unMuteVideo === 'function') unMuteVideo();", null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        isLiked = true
                        showHeartPop = true
                        onLikeClick()
                    },
                    onTap = {
                        isPlaying = !isPlaying
                        showPlayPauseIndicator = true
                        if (isPlaying) {
                            webViewRef?.evaluateJavascript("if(typeof playVideo === 'function') playVideo();", null)
                        } else {
                            webViewRef?.evaluateJavascript("if(typeof pauseVideo === 'function') pauseVideo();", null)
                        }
                    }
                )
            }
    ) {
        // High-res Thumbnail placeholder before WebView renders
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(reel.thumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Custom HTML5 YouTube Player optimized for Reels (9:16 vertical full-bleed)
        if (isCurrentPage) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT

                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
                        }

                        val html = buildReelPlayerHtml(reel.id, isMuted)
                        loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                }
            )
        }

        // Top Gradient Shadow (for top bar contrast)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
        )

        // Bottom Gradient Shadow (for caption and actions contrast)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)
                    )
                )
        )

        // Center Animated Play / Pause Indicator on Tap
        LaunchedEffect(showPlayPauseIndicator) {
            if (showPlayPauseIndicator) {
                delay(750)
                showPlayPauseIndicator = false
            }
        }

        if (showPlayPauseIndicator) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }

        // Double-Tap Animated Heart Popup
        LaunchedEffect(showHeartPop) {
            if (showHeartPop) {
                delay(850)
                showHeartPop = false
            }
        }

        if (showHeartPop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier
                        .size(100.dp)
                        .scale(heartScale)
                )
            }
        }

        // Mute / Unmute Button in Top Right corner
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 44.dp, end = 12.dp)
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = "Toggle Audio",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // Vertical Floating Action Bar (Right side)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Channel Avatar with Follow Button
            Box(
                modifier = Modifier.size(46.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = CircleShape,
                    color = BrandRed,
                    modifier = Modifier.size(42.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = reel.channelTitle.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Like Action
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        isLiked = !isLiked
                        onLikeClick()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) BrandRed else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = likesCount,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Comments Action
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onCommentClick,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Comment,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = reel.commentsCount,
                    color = Color.White,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Share Action
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "শেয়ার",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Download Action (In-App Downloader)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Download Reel",
                        tint = CyanAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "ডাউনলোড",
                    color = CyanAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // YouTube Open Action
            IconButton(
                onClick = onOpenYouTube,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open in YouTube",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Bottom-Left Reel Caption & Channel Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 14.dp, bottom = 26.dp)
        ) {
            // Channel Name Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Text(
                    text = "@${reel.channelTitle}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = BrandRed,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "ফলো",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Caption / Title
            Text(
                text = reel.title,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Views & Audio Marquee
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (reel.viewCount.isNotEmpty()) {
                    Text(
                        text = reel.viewCount,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "মূল অডিও - ${reel.channelTitle}",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Comments Bottom Sheet View
 */
@Composable
private fun ReelsCommentsSheet(reel: YouTubeReelItem) {
    var newCommentText by remember { mutableStateOf("") }
    val sampleComments = remember {
        mutableStateListOf(
            Pair("রাকিব হাসান", "অসাধারণ রিলস ভাই! অনেক ভালো লাগলো 🔥"),
            Pair("তানিয়া আক্তার", "খুব সুন্দর এবং চমৎকার ভিডিও ❤️"),
            Pair("সোহাগ আহমেদ", "পরের পর্ব কবে আসবে? দারুণ অভিনয়!"),
            Pair("মেহজাবিন নূর", "লাইক আর শেয়ার করে দিলাম 👍"),
            Pair("মুকুল ইসলাম", "খুবই সুন্দর কনটেন্ট, এগিয়ে যান!")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 500.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "মন্তব্যসমূহ (${reel.commentsCount})",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sampleComments) { (user, comment) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BrandRed.copy(alpha = 0.2f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(user.take(1), color = BrandRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user, color = TextSecondary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(comment, color = TextPrimary, fontSize = 12.5.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input comment box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newCommentText,
                onValueChange = { newCommentText = it },
                placeholder = { Text("একটি মন্তব্য লিখুন...", fontSize = 12.sp, color = TextMuted) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CinemaSurfaceVariant,
                    unfocusedContainerColor = CinemaSurfaceVariant,
                    focusedBorderColor = BrandRed,
                    unfocusedBorderColor = CinemaBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (newCommentText.isNotBlank()) {
                        sampleComments.add(0, Pair("আপনি", newCommentText.trim()))
                        newCommentText = ""
                    }
                },
                enabled = newCommentText.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .background(if (newCommentText.isNotBlank()) BrandRed else CinemaSurfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Comment",
                    tint = if (newCommentText.isNotBlank()) Color.White else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Build optimized YouTube HTML5 Player for full-bleed vertical Shorts / Reels
 */
private fun buildReelPlayerHtml(videoId: String, isMuted: Boolean): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * { margin:0; padding:0; box-sizing:border-box; }
                body, html { width:100%; height:100%; background:#000000; overflow:hidden; }
                #player-wrapper {
                    position: absolute;
                    top: 50%;
                    left: 50%;
                    transform: translate(-50%, -50%);
                    width: 100vw;
                    height: 100vh;
                    overflow: hidden;
                }
                iframe {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100% !important;
                    height: 100% !important;
                    border: none;
                    pointer-events: auto;
                }
            </style>
        </head>
        <body>
            <div id="player-wrapper">
                <div id="player"></div>
            </div>
            <script>
                var tag = document.createElement('script');
                tag.src = "https://www.youtube.com/iframe_api";
                var firstScriptTag = document.getElementsByTagName('script')[0];
                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                var player;
                function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                        videoId: '$videoId',
                        playerVars: {
                            'autoplay': 1,
                            'controls': 0,
                            'rel': 0,
                            'playsinline': 1,
                            'loop': 1,
                            'playlist': '$videoId',
                            'modestbranding': 1,
                            'showinfo': 0,
                            'iv_load_policy': 3,
                            'fs': 0,
                            'disablekb': 1,
                            'origin': 'https://www.youtube-nocookie.com'
                        },
                        events: {
                            'onReady': onPlayerReady
                        }
                    });
                }

                function onPlayerReady(event) {
                    ${if (isMuted) "event.target.mute();" else "event.target.unMute();"}
                    event.target.playVideo();
                }

                function playVideo() {
                    if (player && typeof player.playVideo === 'function') {
                        player.playVideo();
                    }
                }

                function pauseVideo() {
                    if (player && typeof player.pauseVideo === 'function') {
                        player.pauseVideo();
                    }
                }

                function muteVideo() {
                    if (player && typeof player.mute === 'function') {
                        player.mute();
                    }
                }

                function unMuteVideo() {
                    if (player && typeof player.unMute === 'function') {
                        player.unMute();
                    }
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
