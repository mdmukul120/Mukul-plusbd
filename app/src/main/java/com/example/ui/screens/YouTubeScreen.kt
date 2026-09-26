package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.download.*
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val YOUTUBE_HOME_URL = "https://m.youtube.com"

class YouTubeWebAppInterface(private val onUrlChanged: (String) -> Unit) {
    @JavascriptInterface
    fun notifyUrl(url: String) {
        onUrlChanged(url)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeScreen(
    onNavigateToDownloads: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentUrl by remember { mutableStateOf(YOUTUBE_HOME_URL) }
    var detectedWatchUrl by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoadingPage by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf("YouTube") }

    // Dialog & Extraction States
    var showResolutionDialog by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractionStatus by remember { mutableStateOf("") }
    var extractionJob by remember { mutableStateOf<Job?>(null) }
    var extractionResult by remember { mutableStateOf<ExtractionResult?>(null) }

    // In-app Video Player State
    var activePlayUrl by remember { mutableStateOf<String?>(null) }
    var activePlayTitle by remember { mutableStateOf<String>("") }

    // Manual URL Paste Dialog
    var showUrlInputDialog by remember { mutableStateOf(false) }
    var manualUrlInput by remember { mutableStateOf("") }

    // Monitor InAppDownloader tasks
    val activeTasks by InAppDownloader.tasks.collectAsState()
    val activeYouTubeTask = remember(activeTasks) {
        activeTasks.values.firstOrNull {
            (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
        }
    }

    // Handle back button
    BackHandler(enabled = true) {
        when {
            activePlayUrl != null -> {
                activePlayUrl = null
            }
            showResolutionDialog -> {
                showResolutionDialog = false
            }
            webViewInstance?.canGoBack() == true -> {
                webViewInstance?.goBack()
            }
            onBack != null -> {
                onBack()
            }
        }
    }

    fun handleUrlUpdate(newUrl: String?) {
        if (newUrl.isNullOrBlank()) return
        currentUrl = newUrl
        if (YouTubeDownloaderHelper.isWatchUrl(newUrl)) {
            detectedWatchUrl = newUrl
        } else if (!newUrl.contains("watch") && !newUrl.contains("shorts")) {
            // Cleared if navigated away to home or search results
            detectedWatchUrl = null
        }
    }

    if (activePlayUrl != null) {
        // Full screen / In-App Video Player
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Surface(
                color = CinemaSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { activePlayUrl = null },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activePlayTitle.ifEmpty { "ইউটিউব ভিডিও প্লেয়ার" },
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                VideoPlayerView(
                    videoUrl = activePlayUrl!!,
                    title = activePlayTitle,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(CinemaBackground)
        ) {
            // Minimal Top Bar with Back, Refresh & Title (No browser URL bar)
            Surface(
                color = CinemaSurface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button: navigates web history or goes back in app
                    IconButton(
                        onClick = {
                            if (webViewInstance?.canGoBack() == true) {
                                webViewInstance?.goBack()
                            } else if (onBack != null) {
                                onBack()
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "YouTube",
                        tint = BrandRed,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = if (pageTitle.isNotBlank() && pageTitle != "YouTube") pageTitle else "ইউটিউব (YouTube)",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (detectedWatchUrl != null) {
                        FilledTonalButton(
                            onClick = {
                                showResolutionDialog = true
                                extractionResult = null
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = BrandRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ডাউনলোড", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (isLoadingPage) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = BrandRed,
                    trackColor = Color.Transparent
                )
            }

            // WebView Box Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            isNestedScrollingEnabled = false
                            overScrollMode = android.view.View.OVER_SCROLL_NEVER

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.allowFileAccess = true

                            addJavascriptInterface(
                                YouTubeWebAppInterface { url ->
                                    post { handleUrlUpdate(url) }
                                },
                                "AndroidYouTube"
                            )

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoadingPage = true
                                    handleUrlUpdate(url)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoadingPage = false
                                    pageTitle = view?.title ?: "YouTube"
                                    handleUrlUpdate(url)

                                    // Inject script for smooth scrolling and SPA video detection
                                    val jsScript = """
                                        (function() {
                                            try {
                                                var style = document.createElement('style');
                                                style.innerHTML = 'html, body { -webkit-overflow-scrolling: touch !important; overscroll-behavior-x: none !important; touch-action: pan-y !important; }';
                                                document.head.appendChild(style);
                                            } catch(e){}
                                            function report() {
                                                try {
                                                    if (window.AndroidYouTube && window.location.href) {
                                                        window.AndroidYouTube.notifyUrl(window.location.href);
                                                    }
                                                } catch(e){}
                                            }
                                            report();
                                            window.addEventListener('yt-navigate-finish', report);
                                            window.addEventListener('popstate', report);
                                            window.addEventListener('hashchange', report);
                                            setInterval(report, 1000);
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(jsScript, null)
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    handleUrlUpdate(url)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUrl = request?.url?.toString().orEmpty()
                                    if (reqUrl.startsWith("http://") || reqUrl.startsWith("https://")) {
                                        handleUrlUpdate(reqUrl)
                                        return false
                                    }
                                    return true
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrBlank()) {
                                        pageTitle = title
                                    }
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    isLoadingPage = newProgress < 100
                                }
                            }

                            loadUrl(YOUTUBE_HOME_URL)
                        }
                    },
                    update = {
                        // Keep instance updated
                        webViewInstance = it
                    }
                )

                // FLOATING DOWNLOAD BUTTON (Triggered when video watch URL is detected)
                androidx.compose.animation.AnimatedVisibility(
                    visible = detectedWatchUrl != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed),
                        elevation = CardDefaults.cardElevation(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = BrandRed,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "ভিডিও সনাক্ত হয়েছে",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "রেজুলেশন নির্বাচন করে ডাউনলোড করুন",
                                        color = CyanAccent,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    showResolutionDialog = true
                                    extractionResult = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ডাউনলোড",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ACTIVE DOWNLOAD MINI BANNER
            if (activeYouTubeTask != null) {
                Surface(
                    color = CinemaSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDownloads() }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                CircularProgressIndicator(
                                    progress = { activeYouTubeTask!!.progressPercent / 100f },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandRed
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeYouTubeTask!!.title,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${activeYouTubeTask!!.progressPercent}% (${activeYouTubeTask!!.speedText})",
                                color = CyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { activeYouTubeTask!!.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                            color = BrandRed,
                            trackColor = CinemaBorder
                        )
                    }
                }
            }
        }
    }

    // RESOLUTION SELECTION & EXTRACTION DIALOG
    if (showResolutionDialog) {
        Dialog(onDismissRequest = {
            if (!isExtracting) {
                showResolutionDialog = false
            }
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (extractionResult != null) "ভিডিও প্রস্তুত!" else "রেজুলেশন নির্বাচন করুন",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                extractionJob?.cancel()
                                isExtracting = false
                                showResolutionDialog = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isExtracting) {
                        // EXTRACTION IN PROGRESS
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = BrandRed,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = extractionStatus.ifEmpty { "ডাউনলোড লিঙ্ক তৈরি করা হচ্ছে..." },
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "সার্ভার থেকে ভিডিও ফাইল এনকোড হচ্ছে, কিছুটা সময় লাগতে পারে...",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = {
                                    extractionJob?.cancel()
                                    isExtracting = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRedLight)
                            ) {
                                Text("বাতিল করুন", fontSize = 11.sp)
                            }
                        }
                    } else if (extractionResult != null) {
                        // EXTRACTION COMPLETED - READY TO PLAY OR DOWNLOAD
                        val res = extractionResult!!
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CinemaSurfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (res.thumbnail.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(res.thumbnail)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(54.dp, 40.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = res.title,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "কোয়ালিটি: ${res.format} • প্রস্তুত",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Direct Play Button
                                OutlinedButton(
                                    onClick = {
                                        showResolutionDialog = false
                                        activePlayTitle = res.title
                                        activePlayUrl = res.downloadUrl
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("প্লে করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // In-App Download Button
                                Button(
                                    onClick = {
                                        showResolutionDialog = false
                                        val taskId = InAppDownloader.startDownload(
                                            context = context,
                                            movieSlug = "yt_${res.videoId}",
                                            title = res.title,
                                            poster = res.thumbnail,
                                            quality = res.format,
                                            downloadUrl = res.downloadUrl
                                        )
                                        Toast.makeText(
                                            context,
                                            "অ্যাপে ডাউনলোড শুরু হয়েছে! 'ডাউনলোড' ট্যাবে দেখতে পাবেন।",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ডাউনলোড", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    } else {
                        // RESOLUTION LIST
                        Text(
                            text = "আপনার পছন্দের রেজুলেশনে ক্লিক করুন:",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(YouTubeDownloaderHelper.availableResolutions) { res ->
                                Surface(
                                    color = CinemaSurfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val targetUrl = detectedWatchUrl ?: currentUrl
                                            isExtracting = true
                                            extractionStatus = "সার্ভার শুরু হচ্ছে..."
                                            extractionJob = coroutineScope.launch {
                                                val result = YouTubeDownloaderHelper.extractDownloadUrl(
                                                    youtubeUrl = targetUrl,
                                                    format = res.format,
                                                    onProgressStatus = { status ->
                                                        extractionStatus = status
                                                    }
                                                )
                                                isExtracting = false
                                                result.onSuccess { extracted ->
                                                    extractionResult = extracted
                                                }.onFailure { err ->
                                                    Toast.makeText(
                                                        context,
                                                        err.message ?: "ডাউনলোড লিঙ্ক জেনারেট ব্যর্থ হয়েছে",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            }
                                        }
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
                                                imageVector = if (res.isAudio) Icons.Default.MusicNote else Icons.Default.Videocam,
                                                contentDescription = null,
                                                tint = if (res.isAudio) CyanAccent else BrandRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = res.title,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = res.subtitle,
                                                    color = TextMuted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        Icon(
                                            Icons.Default.ArrowForwardIos,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(14.dp)
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

    // MANUAL YOUTUBE URL SEARCH / PASTE DIALOG
    if (showUrlInputDialog) {
        AlertDialog(
            onDismissRequest = { showUrlInputDialog = false },
            title = {
                Text("ইউটিউব লিঙ্ক বা সার্চ", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "ইউটিউবের কোনো ভিডিও লিঙ্ক পেস্ট করুন অথবা সার্চ করার জন্য টেক্সট লিখুন:",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualUrlInput,
                        onValueChange = { manualUrlInput = it },
                        placeholder = { Text("https://youtu.be/... বা সার্চ লিখুন", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandRed,
                            unfocusedBorderColor = CinemaBorder
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = manualUrlInput.trim()
                        if (input.isNotEmpty()) {
                            val finalUrl = when {
                                input.startsWith("http://") || input.startsWith("https://") -> input
                                else -> "https://m.youtube.com/results?search_query=" + java.net.URLEncoder.encode(input, "UTF-8")
                            }
                            webViewInstance?.loadUrl(finalUrl)
                            handleUrlUpdate(finalUrl)
                        }
                        showUrlInputDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("খুলুন", color = Color.White, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlInputDialog = false }) {
                    Text("বাতিল", color = TextMuted, fontSize = 12.sp)
                }
            },
            containerColor = CinemaSurface
        )
    }
}
