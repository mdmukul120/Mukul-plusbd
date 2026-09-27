package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.api.ApiClient
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CyanAccent
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    onFullScreenToggle: (() -> Unit)? = null,
    isFullScreen: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Automatic stream URL optimization and Bongo direct stream resolution
    var playableUrl by remember(videoUrl) {
        val initial = if (videoUrl.contains("aynaott.com") && !videoUrl.contains("remote=no_check_ip")) {
            if (videoUrl.contains("?")) "$videoUrl&remote=no_check_ip" else "$videoUrl?remote=no_check_ip"
        } else {
            videoUrl
        }
        mutableStateOf(initial)
    }

    LaunchedEffect(videoUrl) {
        if (videoUrl.contains("bongo/hls") || (videoUrl.contains("hamyra-api") && videoUrl.contains("id="))) {
            val resolved = withContext(Dispatchers.IO) {
                ApiClient.resolveBongoStreamUrl(videoUrl)
            }
            if (resolved.isNotBlank() && resolved != playableUrl) {
                playableUrl = resolved
            }
        }
    }

    // Player State
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isBuffering by remember { mutableStateOf(true) }
    var autoRetryCount by remember(playableUrl) { mutableIntStateOf(0) }

    // Immediately extract duration for offline/local files using MediaMetadataRetriever
    LaunchedEffect(playableUrl) {
        if (playableUrl.startsWith("/") || playableUrl.startsWith("file:")) {
            withContext(Dispatchers.IO) {
                try {
                    val cleanPath = if (playableUrl.startsWith("file://")) Uri.parse(playableUrl).path ?: playableUrl.removePrefix("file://") else playableUrl
                    val file = java.io.File(cleanPath)
                    if (file.exists() && file.length() > 0) {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(cleanPath)
                        val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durMs = durStr?.toLongOrNull() ?: 0L
                        if (durMs > 0L) {
                            totalDuration = durMs
                        }
                        retriever.release()
                    }
                } catch (e: Exception) {
                    Log.w("VideoPlayerView", "Offline duration extract notice: ${e.message}")
                }
            }
        }
    }

    // 10 Custom Controllers State
    var isScreenLocked by remember { mutableStateOf(false) } // 1. Screen Lock
    var isMuted by remember { mutableStateOf(false) }        // 2. Mute / Unmute
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) } // 3. Playback Speed
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) } // 4. Aspect Ratio / Fit / Fill
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Auto (HD)") }

    var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }

    // Setup ExoPlayer with Hardware Decoder Fallback & TextureView
    val exoPlayer = remember(playableUrl) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
            .setDefaultRequestProperties(
                mapOf(
                    "Origin" to "https://www.hamyra.xyz",
                    "Referer" to "https://www.hamyra.xyz/",
                    "Accept" to "*/*"
                )
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(25000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                try {
                    val isLocalFile = playableUrl.startsWith("/") || playableUrl.startsWith("file:")
                    val isHls = !isLocalFile && (playableUrl.contains(".m3u8", ignoreCase = true) ||
                        playableUrl.contains(".m3u", ignoreCase = true) ||
                        playableUrl.contains("/px/hls", ignoreCase = true) ||
                        playableUrl.contains("bongo/hls", ignoreCase = true) ||
                        playableUrl.contains("talkoraai.com", ignoreCase = true) ||
                        playableUrl.contains("workers.dev", ignoreCase = true) ||
                        playableUrl.contains("aynaott", ignoreCase = true) ||
                        playableUrl.contains("live", ignoreCase = true) ||
                        playableUrl.contains("hridoytv", ignoreCase = true))

                    val mediaUri = if (isLocalFile) {
                        if (playableUrl.startsWith("file:")) Uri.parse(playableUrl) else Uri.fromFile(java.io.File(playableUrl))
                    } else {
                        Uri.parse(playableUrl)
                    }

                    val mediaItem = MediaItem.Builder()
                        .setUri(mediaUri)
                        .apply {
                            if (isHls) {
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                            }
                        }
                        .build()
                    setMediaItem(mediaItem)
                    prepare()
                } catch (e: Exception) {
                    hasError = true
                    errorMessage = e.message ?: "Playback initialization failed"
                }
            }
    }

    // Player event listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        hasError = false
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        hasError = false
                        val dur = exoPlayer.duration
                        if (dur > 0L) totalDuration = dur
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                        isPlaying = false
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val dur = exoPlayer.duration
                if (dur > 0L) totalDuration = dur
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("VideoPlayerView", "Player error for $playableUrl: ${error.message}", error)
                if (autoRetryCount < 2) {
                    autoRetryCount++
                    if (playableUrl.contains("aynaott.com") && !playableUrl.contains("remote=no_check_ip")) {
                        playableUrl = if (playableUrl.contains("?")) "$playableUrl&remote=no_check_ip" else "$playableUrl?remote=no_check_ip"
                        return
                    }
                    coroutineScope.launch {
                        delay(1200)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                    return
                }

                isBuffering = false
                hasError = true
                errorMessage = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "নেটওয়ার্ক টাইমআউট বা সংযোগ সাময়িক সমস্যা"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "সার্ভার রেসপন্স ত্রুটি (HTTP Response Code)"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "মিডিয়া স্ট্রিমিং ফরম্যাট ত্রুটি"
                    else -> error.localizedMessage ?: "স্ট্রিম সাময়িক অনুপলব্ধ"
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Continuous Position and Duration tracker
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isDraggingSlider) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0L) {
                    totalDuration = dur
                }
            }
            delay(250)
        }
    }

    // Auto-hide controls after 4.5 seconds
    LaunchedEffect(showControls, isPlaying, isScreenLocked) {
        if (showControls && isPlaying && !isScreenLocked) {
            delay(4500)
            showControls = false
        }
    }

    // Update resize mode on player view when changed
    LaunchedEffect(resizeMode) {
        playerViewInstance?.resizeMode = resizeMode
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // TextureView PlayerView from XML layout (fixes the black screen issue completely!)
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx).inflate(R.layout.media3_player_view, null) as PlayerView
                view.player = exoPlayer
                view.useController = false
                view.resizeMode = resizeMode
                playerViewInstance = view
                view
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering Spinner
        if (isBuffering && !hasError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = BrandRed,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        // Screen Lock Floating Toggle (always accessible when touched)
        if (showControls) {
            IconButton(
                onClick = { isScreenLocked = !isScreenLocked },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .background(Color(0x88000000), CircleShape)
            ) {
                Icon(
                    imageVector = if (isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Screen Lock",
                    tint = if (isScreenLocked) BrandRed else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Error Banner
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE090C15)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = "প্লেব্যাক সংযোগ সমস্যা (Playback Notice)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = errorMessage.ifEmpty { "ভিডিও স্ট্রিম লোড হতে সমস্যা হচ্ছে। স্বয়ংক্রিয় সমাধান করে পুনরায় চেষ্টা করুন।" },
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 3
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                hasError = false
                                isBuffering = true
                                coroutineScope.launch {
                                    if (videoUrl.contains("bongo/hls") || videoUrl.contains("hamyra-api")) {
                                        val direct = withContext(Dispatchers.IO) {
                                            ApiClient.resolveBongoStreamUrl(videoUrl)
                                        }
                                        if (direct.isNotBlank()) playableUrl = direct
                                    } else if (playableUrl.contains("aynaott.com") && !playableUrl.contains("remote=no_check_ip")) {
                                        playableUrl = if (playableUrl.contains("?")) "$playableUrl&remote=no_check_ip" else "$playableUrl?remote=no_check_ip"
                                    }
                                    exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পুনরায় চেষ্টা (Retry)", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val targetUrl = playableUrl.ifEmpty { videoUrl }
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                        setDataAndType(Uri.parse(targetUrl), "video/*")
                                    }
                                    context.startActivity(Intent.createChooser(intent, "অন্য প্লেয়ারে চালান (External Player)"))
                                } catch (_: Exception) {
                                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                    context.startActivity(fallback)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Launch, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("অন্য প্লেয়ারে (External)", color = CyanAccent, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Controls Overlay (Hidden when screen is locked)
        AnimatedVisibility(
            visible = showControls && !hasError && !isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC000000),
                                Color(0x33000000),
                                Color(0xDD000000)
                            )
                        )
                    )
            ) {
                // Top Action Bar: Title, Speed, Quality, External Player
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Controller 5: Speed Button
                        TextButton(
                            onClick = { showSpeedDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = CyanAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Controller 6: Quality Button
                        IconButton(onClick = { showQualityDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Quality",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Controller 7: Mute / Unmute
                        IconButton(onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        }) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "Volume Toggle",
                                tint = if (isMuted) Color(0xFFFF5252) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Controller 8: Aspect Ratio (Fit / Zoom / Stretch)
                        IconButton(onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Aspect Ratio",
                                tint = if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT) CyanAccent else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Controller 9: Launch External Player
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                                    setDataAndType(Uri.parse(videoUrl), "video/*")
                                }
                                context.startActivity(Intent.createChooser(intent, "Play in External Player"))
                            } catch (_: Exception) {
                                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                context.startActivity(fallback)
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open in external player",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Center Main Controls: Rewind 30s, Rewind 10s, Play/Pause, Forward 10s, Forward 30s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 30s
                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition - 30000).coerceAtLeast(0)
                            exoPlayer.seekTo(newPos)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x66000000),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("-30s", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Rewind 10s
                    IconButton(
                        onClick = {
                            val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                            exoPlayer.seekTo(newPos)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play / Pause Circle
                    Surface(
                        onClick = {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        shape = CircleShape,
                        color = BrandRed,
                        modifier = Modifier.size(60.dp),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            val maxDur = if (totalDuration > 0) totalDuration else Long.MAX_VALUE
                            val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(maxDur)
                            exoPlayer.seekTo(newPos)
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Forward 30s
                    IconButton(
                        onClick = {
                            val maxDur = if (totalDuration > 0) totalDuration else Long.MAX_VALUE
                            val newPos = (exoPlayer.currentPosition + 30000).coerceAtMost(maxDur)
                            exoPlayer.seekTo(newPos)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x66000000),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+30s", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bottom Bar: Timeline seek bar, Live / Time stamp, Fullscreen Toggle (Controller 4)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val fallbackDur = if (totalDuration > 0) totalDuration else exoPlayer.duration.coerceAtLeast(0L)
                    val effectiveDuration = if (fallbackDur > 0) fallbackDur else (currentPosition + 300000L).coerceAtLeast(300000L)
                    val displayPos = if (isDraggingSlider) sliderDragPosition.toLong() else currentPosition
                    val currentVal = (if (isDraggingSlider) sliderDragPosition else currentPosition.toFloat()).coerceIn(0f, effectiveDuration.toFloat())

                    // Scrubbing position indicator when dragging
                    if (isDraggingSlider) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BrandRed,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "ভিডিও টানা হচ্ছে: ${formatTime(displayPos)} / ${formatTime(effectiveDuration)}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Interactive Scrubbing Slider (প্রোগ্রেস বার টেনে দেখা)
                    Slider(
                        value = currentVal,
                        onValueChange = { newPos ->
                            isDraggingSlider = true
                            sliderDragPosition = newPos
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(sliderDragPosition.toLong())
                            currentPosition = sliderDragPosition.toLong()
                            isDraggingSlider = false
                        },
                        valueRange = 0f..effectiveDuration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = BrandRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(displayPos)} / ${formatTime(effectiveDuration)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Controller 4: Fullscreen Toggle
                        if (onFullScreenToggle != null) {
                            IconButton(onClick = onFullScreenToggle) {
                                Icon(
                                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Always-visible Interactive Progress Bar at bottom when controls are hidden (টেনে দেখার সুযোগ)
        if (!showControls && !isScreenLocked) {
            val fallbackDur = if (totalDuration > 0) totalDuration else exoPlayer.duration.coerceAtLeast(0L)
            val effectiveDuration = if (fallbackDur > 0) fallbackDur else 1L
            val progressFraction = if (effectiveDuration > 1L) (currentPosition.toFloat() / effectiveDuration.toFloat()).coerceIn(0f, 1f) else 0f

            Surface(
                color = Color(0x99000000),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clickable { showControls = true }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp)),
                        color = BrandRed,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(currentPosition)} / ${if (effectiveDuration > 1L) formatTime(effectiveDuration) else "--:--"}",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "ভিডিও টানতে ট্যাপ করুন (Tap to seek)",
                            color = CyanAccent,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    playbackSpeed = speed
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (playbackSpeed == speed),
                                onClick = {
                                    playbackSpeed = speed
                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                    showSpeedDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "${speed}x Normal", fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Quality Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Stream Quality", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("Auto (Recommended)", "1080p Full HD", "720p HD", "480p SD", "Audio Only").forEach { q ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedQuality = q
                                    showQualityDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedQuality == q),
                                onClick = {
                                    selectedQuality = q
                                    showQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = q, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
