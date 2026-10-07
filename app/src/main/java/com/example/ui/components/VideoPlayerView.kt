package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
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
import com.example.data.util.findActivity
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.GoldRating
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class VlcGestureType {
    NONE, SEEK, BRIGHTNESS, VOLUME
}

/**
 * VLC-Style Video Player with:
 * - Accurate Matroska (MKV) & MP4 HTTP seeking without jumping back to 0
 * - Async metadata duration extraction so total movie duration is always loaded
 * - Smooth Scrubbing Slider with buffered playback visualizer
 * - Horizontal swipe to seek forward/backward with time-delta HUD
 * - Left vertical swipe for Brightness control HUD
 * - Right vertical swipe for Volume control HUD
 * - Double tap left/right to skip 10 seconds
 * - Screen Lock, Aspect Ratio, Speed, and External Player controls
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerView(
    videoUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    onFullScreenToggle: (() -> Unit)? = null,
    isFullScreen: Boolean = false,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    // Resolve URL for Hamyra / Bongo if necessary
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

    // Player Playback States
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }
    var pendingSeekTarget by remember { mutableStateOf<Long?>(null) }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isBuffering by remember { mutableStateOf(true) }
    var autoRetryCount by remember(playableUrl) { mutableIntStateOf(0) }

    // VLC Gesture States
    var playerSize by remember { mutableStateOf(IntSize.Zero) }
    var currentGesture by remember { mutableStateOf(VlcGestureType.NONE) }
    var gestureSeekDeltaMs by remember { mutableLongStateOf(0L) }
    var gestureSeekTargetMs by remember { mutableLongStateOf(0L) }
    var currentBrightnessPercent by remember { mutableIntStateOf(50) }
    var currentVolumePercent by remember { mutableIntStateOf(50) }
    var showDoubleTapFeedback by remember { mutableStateOf<String?>(null) } // "LEFT_10" or "RIGHT_10"

    // Controller Options
    var isScreenLocked by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Auto (HD)") }

    var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }
    var userInteractionTrigger by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Initialize current brightness and volume
    LaunchedEffect(Unit) {
        activity?.window?.attributes?.screenBrightness?.let { b ->
            currentBrightnessPercent = if (b < 0) 50 else (b * 100).toInt()
        }
        audioManager?.let { am ->
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            currentVolumePercent = ((curVol.toFloat() / maxVol) * 100).toInt()
        }
    }

    // Proactive background duration retriever for MKV/MP4 network files
    LaunchedEffect(playableUrl) {
        if (playableUrl.startsWith("http")) {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    val headers = HashMap<String, String>()
                    headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
                    headers["Accept"] = "*/*"
                    retriever.setDataSource(playableUrl, headers)
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durMs = durStr?.toLongOrNull() ?: 0L
                    if (durMs > 0L) {
                        if (totalDuration <= 0L || durMs > totalDuration) {
                            totalDuration = durMs
                            Log.d("VideoPlayerView", "Retrieved stream duration: $durMs ms")
                        }
                    }
                    retriever.release()
                } catch (e: Exception) {
                    Log.w("VideoPlayerView", "Metadata duration extract notice: ${e.message}")
                }
            }
        } else if (playableUrl.startsWith("/") || playableUrl.startsWith("file:")) {
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
                } catch (_: Exception) {}
            }
        }
    }

    // ExoPlayer Instance Configuration
    val exoPlayer = remember(playableUrl) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val uri = try { Uri.parse(playableUrl) } catch (_: Exception) { null }
        val host = uri?.host?.lowercase() ?: ""
        val dynamicHeaders = mutableMapOf(
            "Accept" to "*/*",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
        )
        if (host.contains("hamyra")) {
            dynamicHeaders["Origin"] = "https://www.hamyra.xyz"
            dynamicHeaders["Referer"] = "https://www.hamyra.xyz/"
        } else if (host.contains("ctghall")) {
            dynamicHeaders["Referer"] = "https://www.ctghall.com/"
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36")
            .setDefaultRequestProperties(dynamicHeaders)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        // CRITICAL FIX: Do NOT enable ConstantBitrateSeeking for MKV/MP4 VBR video containers!
        // CBR seeking falsely computes byte offsets linearly, causing decode failure and seek reset to 0.
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(false)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            .build().apply {
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                try {
                    val isLocalFile = playableUrl.startsWith("/") || playableUrl.startsWith("file:")
                    val isHls = !isLocalFile && (
                        playableUrl.contains(".m3u8", ignoreCase = true) ||
                        playableUrl.contains(".m3u", ignoreCase = true) ||
                        playableUrl.contains("/px/hls", ignoreCase = true) ||
                        playableUrl.contains("bongo/hls", ignoreCase = true) ||
                        playableUrl.contains("talkoraai.com", ignoreCase = true) ||
                        playableUrl.contains("aynaott", ignoreCase = true) ||
                        playableUrl.contains("hridoytv", ignoreCase = true)
                    )

                    val mediaUri = if (isLocalFile) {
                        if (playableUrl.startsWith("file:")) Uri.parse(playableUrl) else Uri.fromFile(java.io.File(playableUrl))
                    } else {
                        Uri.parse(playableUrl)
                    }

                    val mediaItemBuilder = MediaItem.Builder().setUri(mediaUri)
                    if (isHls) {
                        mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    } else if (playableUrl.contains(".mkv", ignoreCase = true) || playableUrl.contains("matroska", ignoreCase = true)) {
                        mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MATROSKA)
                    } else if (playableUrl.contains(".mp4", ignoreCase = true)) {
                        mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
                    }

                    setMediaItem(mediaItemBuilder.build())
                    prepare()
                } catch (e: Exception) {
                    hasError = true
                    errorMessage = e.message ?: "Playback initialization failed"
                }
            }
    }

    // Function to perform robust, jitter-free seeking
    fun performSeekTo(targetMs: Long) {
        val maxLimit = if (totalDuration > 0L) totalDuration else Long.MAX_VALUE
        val clampedTarget = targetMs.coerceIn(0L, maxLimit)
        pendingSeekTarget = clampedTarget
        currentPosition = clampedTarget
        exoPlayer.seekTo(clampedTarget)
        userInteractionTrigger = System.currentTimeMillis()
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
                        if (dur > 0L && dur != C.TIME_UNSET) {
                            totalDuration = dur
                        }
                        // Clear pending seek once playback is ready
                        pendingSeekTarget = null
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
                if (dur > 0L && dur != C.TIME_UNSET) {
                    totalDuration = dur
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("VideoPlayerView", "Player error: ${error.message}", error)
                if (autoRetryCount < 2) {
                    autoRetryCount++
                    coroutineScope.launch {
                        delay(1000)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                    return
                }

                isBuffering = false
                hasError = true
                errorMessage = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "নেটওয়ার্ক সংযোগ সাময়িক সমস্যা"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "সার্ভার রেসপন্স ত্রুটি (HTTP Error)"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "মিডিয়া ফরম্যাট ত্রুটি"
                    else -> error.localizedMessage ?: "ভিডিও স্ট্রিম সাময়িক অনুপলব্ধ"
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            try {
                exoPlayer.removeListener(listener)
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (_: Exception) {}
        }
    }

    // Continuous Position, Buffer, and Duration tracker
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isDraggingSlider && currentGesture != VlcGestureType.SEEK) {
                val pending = pendingSeekTarget
                if (pending != null) {
                    val currentExo = exoPlayer.currentPosition.coerceAtLeast(0L)
                    // Only release pending lock if ExoPlayer caught up with target
                    if (exoPlayer.playbackState == Player.STATE_READY || kotlin.math.abs(currentExo - pending) < 2000L) {
                        currentPosition = currentExo
                        pendingSeekTarget = null
                    }
                } else {
                    currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                }

                bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0L && dur != C.TIME_UNSET && dur > totalDuration) {
                    totalDuration = dur
                }
            }
            delay(200)
        }
    }

    // Auto-hide controls after 3 seconds of inactivity
    LaunchedEffect(showControls, isPlaying, isDraggingSlider, currentGesture, isScreenLocked, userInteractionTrigger) {
        if (showControls && isPlaying && !isDraggingSlider && currentGesture == VlcGestureType.NONE && !isScreenLocked) {
            delay(3000)
            showControls = false
        }
    }

    // Double tap feedback auto-hide
    LaunchedEffect(showDoubleTapFeedback) {
        if (showDoubleTapFeedback != null) {
            delay(650)
            showDoubleTapFeedback = null
        }
    }

    // Update resize mode on player view when changed
    LaunchedEffect(resizeMode) {
        playerViewInstance?.resizeMode = resizeMode
    }

    // Main Player Box Container
    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { playerSize = it }
            .pointerInput(isScreenLocked, playerSize, totalDuration, currentPosition) {
                if (isScreenLocked) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                            userInteractionTrigger = System.currentTimeMillis()
                        }
                    )
                } else {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val width = playerSize.width.toFloat().coerceAtLeast(1f)
                            if (offset.x < width * 0.35f) {
                                // Rewind 10s
                                showDoubleTapFeedback = "LEFT_10"
                                performSeekTo(currentPosition - 10000L)
                            } else if (offset.x > width * 0.65f) {
                                // Forward 10s
                                showDoubleTapFeedback = "RIGHT_10"
                                performSeekTo(currentPosition + 10000L)
                            } else {
                                // Double tap center toggles play/pause
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                            userInteractionTrigger = System.currentTimeMillis()
                        },
                        onTap = {
                            showControls = !showControls
                            userInteractionTrigger = System.currentTimeMillis()
                        }
                    )
                }
            }
            .pointerInput(isScreenLocked, playerSize, totalDuration, currentPosition) {
                if (!isScreenLocked) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = playerSize.width.toFloat().coerceAtLeast(1f)
                            val isLeft = offset.x < width * 0.4f
                            val isRight = offset.x > width * 0.6f

                            currentGesture = when {
                                isLeft -> VlcGestureType.BRIGHTNESS
                                isRight -> VlcGestureType.VOLUME
                                else -> VlcGestureType.SEEK
                            }
                            gestureSeekDeltaMs = 0L
                            gestureSeekTargetMs = currentPosition
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val width = playerSize.width.toFloat().coerceAtLeast(1f)
                            val height = playerSize.height.toFloat().coerceAtLeast(1f)

                            when (currentGesture) {
                                VlcGestureType.SEEK -> {
                                    // Scale horizontal movement: 1 px = ~150ms
                                    val deltaMs = (dragAmount.x * 200).toLong()
                                    gestureSeekDeltaMs += deltaMs
                                    val maxDur = if (totalDuration > 0L) totalDuration else 7200000L
                                    gestureSeekTargetMs = (currentPosition + gestureSeekDeltaMs).coerceIn(0L, maxDur)
                                }
                                VlcGestureType.BRIGHTNESS -> {
                                    val deltaPercent = (-dragAmount.y / height * 100).toInt()
                                    currentBrightnessPercent = (currentBrightnessPercent + deltaPercent).coerceIn(0, 100)
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        lp.screenBrightness = (currentBrightnessPercent / 100f).coerceIn(0.01f, 1f)
                                        act.window.attributes = lp
                                    }
                                }
                                VlcGestureType.VOLUME -> {
                                    val deltaPercent = (-dragAmount.y / height * 100).toInt()
                                    currentVolumePercent = (currentVolumePercent + deltaPercent).coerceIn(0, 100)
                                    audioManager?.let { am ->
                                        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        val targetVol = (currentVolumePercent / 100f * maxVol).toInt().coerceIn(0, maxVol)
                                        am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    }
                                }
                                else -> {}
                            }
                        },
                        onDragEnd = {
                            if (currentGesture == VlcGestureType.SEEK) {
                                performSeekTo(gestureSeekTargetMs)
                            }
                            currentGesture = VlcGestureType.NONE
                            userInteractionTrigger = System.currentTimeMillis()
                        },
                        onDragCancel = {
                            currentGesture = VlcGestureType.NONE
                        }
                    )
                }
            }
    ) {
        // TextureView PlayerView from XML layout
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
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        // ====================================================================
        // VLC CENTER HUD: Gesture HUD (Seek Scrub, Volume, Brightness)
        // ====================================================================
        if (currentGesture != VlcGestureType.NONE) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xDD111520),
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        when (currentGesture) {
                            VlcGestureType.SEEK -> {
                                val isForward = gestureSeekDeltaMs >= 0
                                Icon(
                                    imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                                    contentDescription = null,
                                    tint = if (isForward) CyanAccent else GoldRating,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${if (isForward) "+" else ""}${formatTime(gestureSeekDeltaMs)}",
                                    color = if (isForward) CyanAccent else GoldRating,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val displayMax = if (totalDuration > 0L) totalDuration else 7200000L
                                Text(
                                    text = "${formatTime(gestureSeekTargetMs)} / ${formatTime(displayMax)}",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                            VlcGestureType.BRIGHTNESS -> {
                                Icon(
                                    imageVector = Icons.Default.BrightnessMedium,
                                    contentDescription = null,
                                    tint = GoldRating,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "উজ্জ্বলতা (Brightness): $currentBrightnessPercent%",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { currentBrightnessPercent / 100f },
                                    color = GoldRating,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.width(140.dp).height(6.dp)
                                )
                            }
                            VlcGestureType.VOLUME -> {
                                Icon(
                                    imageVector = if (currentVolumePercent > 0) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "সাউন্ড (Volume): $currentVolumePercent%",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { currentVolumePercent / 100f },
                                    color = CyanAccent,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.width(140.dp).height(6.dp)
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // ====================================================================
        // DOUBLE TAP FEEDBACK RIPPLE HUD (-10s / +10s)
        // ====================================================================
        if (showDoubleTapFeedback != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (showDoubleTapFeedback == "LEFT_10") Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xAA000000),
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .size(76.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (showDoubleTapFeedback == "LEFT_10") Icons.Default.Replay10 else Icons.Default.Forward10,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = if (showDoubleTapFeedback == "LEFT_10") "-10s" else "+10s",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
                        text = errorMessage.ifEmpty { "ভিডিও স্ট্রিম লোড হতে সমস্যা হচ্ছে। পুনরায় চেষ্টা করুন।" },
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 3,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                hasError = false
                                isBuffering = true
                                coroutineScope.launch {
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
                                    context.startActivity(Intent.createChooser(intent, "VLC / অন্য প্লেয়ারে চালান"))
                                } catch (_: Exception) {
                                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                    context.startActivity(fallback)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Launch, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("VLC প্লেয়ারে (External)", color = CyanAccent, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // ====================================================================
        // CONTROLS OVERLAY: Top Bar, Center Buttons, Bottom Timeline
        // ====================================================================
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
                                Color(0x22000000),
                                Color(0xDD000000)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onClose != null) {
                        IconButton(
                            onClick = {
                                try {
                                    exoPlayer.stop()
                                    exoPlayer.clearMediaItems()
                                } catch (_: Exception) {}
                                onClose()
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Player",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

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
                        // Playback Speed
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

                        // Quality Button
                        IconButton(onClick = { showQualityDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Quality",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Mute / Unmute
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

                        // Aspect Ratio (Fit / Zoom / Stretch)
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

                        // Launch in External Player (VLC/MX Player)
                        IconButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                                    setDataAndType(Uri.parse(videoUrl), "video/*")
                                }
                                context.startActivity(Intent.createChooser(intent, "VLC প্লেয়ার বেছে নিন"))
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

                // Center Controls: Rewind 30s, Rewind 10s, Play/Pause, Forward 10s, Forward 30s
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 30s
                    IconButton(
                        onClick = { performSeekTo(currentPosition - 30000L) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x66000000),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("-30s", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Rewind 10s
                    IconButton(
                        onClick = { performSeekTo(currentPosition - 10000L) },
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
                            userInteractionTrigger = System.currentTimeMillis()
                        },
                        shape = CircleShape,
                        color = BrandRed,
                        modifier = Modifier.size(62.dp),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Forward 10s
                    IconButton(
                        onClick = { performSeekTo(currentPosition + 10000L) },
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
                        onClick = { performSeekTo(currentPosition + 30000L) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x66000000),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+30s", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bottom Timeline Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val maxDuration = if (totalDuration > 0L) totalDuration else 7200000L
                    val displayPos = when {
                        isDraggingSlider -> sliderDragPosition.toLong()
                        pendingSeekTarget != null -> pendingSeekTarget!!
                        else -> currentPosition
                    }
                    val sliderVal = displayPos.toFloat().coerceIn(0f, maxDuration.toFloat())

                    // Scrubbing info tooltip
                    if (isDraggingSlider) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BrandRed,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "টানা হচ্ছে: ${formatTime(displayPos)} / ${formatTime(maxDuration)}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Slider with Buffered Track
                    Box(modifier = Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
                        // Background Buffered Progress Bar
                        if (maxDuration > 0L) {
                            val bufferRatio = (bufferedPosition.toFloat() / maxDuration).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { bufferRatio },
                                color = Color.White.copy(alpha = 0.35f),
                                trackColor = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }

                        // Interactive Foreground Seek Slider
                        Slider(
                            value = sliderVal,
                            onValueChange = { newPos ->
                                isDraggingSlider = true
                                sliderDragPosition = newPos
                                userInteractionTrigger = System.currentTimeMillis()
                            },
                            onValueChangeFinished = {
                                val targetMs = sliderDragPosition.toLong()
                                isDraggingSlider = false
                                performSeekTo(targetMs)
                            },
                            valueRange = 0f..maxDuration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = BrandRed,
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatTime(displayPos)} / ${formatTime(maxDuration)}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick Jump +60s
                            TextButton(
                                onClick = { performSeekTo(currentPosition + 60000L) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("+60s", color = CyanAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }

                            // Screen Rotate Toggle
                            IconButton(
                                onClick = {
                                    val act = context.findActivity()
                                    if (act != null) {
                                        val orientation = act.resources.configuration.orientation
                                        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                        userInteractionTrigger = System.currentTimeMillis()
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Screen Rotate",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Fullscreen Toggle
                            if (onFullScreenToggle != null) {
                                IconButton(
                                    onClick = {
                                        onFullScreenToggle()
                                        userInteractionTrigger = System.currentTimeMillis()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
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
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
