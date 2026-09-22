package com.example.data.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.data.model.MusicTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MusicPlayerManager {
    private const val TAG = "MusicPlayerManager"

    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<MusicTrack>>(emptyList())
    val queue: StateFlow<List<MusicTrack>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isRepeat = MutableStateFlow(false)
    val isRepeat: StateFlow<Boolean> = _isRepeat.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _showFullPlayer = MutableStateFlow(false)
    val showFullPlayer: StateFlow<Boolean> = _showFullPlayer.asStateFlow()

    @OptIn(UnstableApi::class)
    fun init(context: Context) {
        if (exoPlayer != null) return

        val appContext = context.applicationContext
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("MukulPlusMusic/1.0 (Android; Mobile)")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _isBuffering.value = true
                            }
                            Player.STATE_READY -> {
                                _isBuffering.value = false
                                _durationMs.value = duration.coerceAtLeast(0L)
                            }
                            Player.STATE_ENDED -> {
                                _isBuffering.value = false
                                handleTrackEnded()
                            }
                            Player.STATE_IDLE -> {
                                _isBuffering.value = false
                            }
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startProgressTracking()
                        } else {
                            stopProgressTracking()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer playback error: ${error.message}", error)
                        _isBuffering.value = false
                        _isPlaying.value = false
                    }
                })
            }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration
                    if (dur > 0L) {
                        _durationMs.value = dur
                    }
                }
                delay(400)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
        exoPlayer?.let {
            _positionMs.value = it.currentPosition.coerceAtLeast(0L)
        }
    }

    fun playTrack(track: MusicTrack, newQueue: List<MusicTrack> = emptyList()) {
        val player = exoPlayer ?: return
        val effectiveQueue = if (newQueue.isNotEmpty()) newQueue else listOf(track)
        _queue.value = effectiveQueue

        val idx = effectiveQueue.indexOfFirst { it.id == track.id }
        _currentIndex.value = if (idx >= 0) idx else 0
        _currentTrack.value = track

        val stream = if (track.streamUrl.isNotEmpty()) track.streamUrl else track.downloadUrl
        if (stream.isEmpty()) {
            Log.e(TAG, "No valid stream URL for track: ${track.name}")
            return
        }

        try {
            val mediaItem = MediaItem.fromUri(Uri.parse(stream))
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            _isPlaying.value = true
            _isBuffering.value = true
            _positionMs.value = 0L
            _durationMs.value = (track.duration * 1000L).coerceAtLeast(0L)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing track: ${track.name}", e)
        }
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE && _currentTrack.value != null) {
                _currentTrack.value?.let { playTrack(it, _queue.value) }
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val target = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        player.seekTo(target)
        _positionMs.value = target
    }

    fun playNext() {
        val q = _queue.value
        if (q.isEmpty()) return

        var nextIdx = _currentIndex.value + 1
        if (_isShuffle.value && q.size > 1) {
            var randomIdx = (0 until q.size).random()
            while (randomIdx == _currentIndex.value && q.size > 1) {
                randomIdx = (0 until q.size).random()
            }
            nextIdx = randomIdx
        }

        if (nextIdx < q.size) {
            playTrack(q[nextIdx], q)
        } else if (_isRepeat.value && q.isNotEmpty()) {
            playTrack(q[0], q)
        }
    }

    fun playPrevious() {
        val player = exoPlayer ?: return
        // If passed 3 seconds, replay current song first
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
            _positionMs.value = 0L
            return
        }

        val q = _queue.value
        if (q.isEmpty()) return

        val prevIdx = _currentIndex.value - 1
        if (prevIdx >= 0) {
            playTrack(q[prevIdx], q)
        } else if (q.isNotEmpty()) {
            playTrack(q.last(), q)
        }
    }

    private fun handleTrackEnded() {
        if (_isRepeat.value) {
            exoPlayer?.seekTo(0L)
            exoPlayer?.play()
        } else {
            playNext()
        }
    }

    fun toggleRepeat() {
        _isRepeat.value = !_isRepeat.value
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun openFullPlayer() {
        _showFullPlayer.value = true
    }

    fun closeFullPlayer() {
        _showFullPlayer.value = false
    }

    fun release() {
        stopProgressTracking()
        exoPlayer?.release()
        exoPlayer = null
    }
}
