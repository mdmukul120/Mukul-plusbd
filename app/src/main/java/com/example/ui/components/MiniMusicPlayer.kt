package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.player.MusicPlayerManager
import com.example.data.util.DownloadUtils
import com.example.ui.theme.*

@Composable
fun MiniMusicPlayer(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack by MusicPlayerManager.currentTrack.collectAsState()
    val isPlaying by MusicPlayerManager.isPlaying.collectAsState()
    val isBuffering by MusicPlayerManager.isBuffering.collectAsState()
    val positionMs by MusicPlayerManager.positionMs.collectAsState()
    val durationMs by MusicPlayerManager.durationMs.collectAsState()

    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val track = currentTrack ?: return@AnimatedVisibility

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable { MusicPlayerManager.openFullPlayer() },
            shape = RoundedCornerShape(16.dp),
            color = CinemaSurface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album Thumbnail
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CinemaSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (track.imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = track.imageUrl,
                                contentDescription = track.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = BrandRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = BrandRed,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title & Artist
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = track.name,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = if (track.artistNames.isNotEmpty()) track.artistNames else track.albumName,
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    // Direct Chrome Download Button
                    IconButton(
                        onClick = {
                            val dlUrl = if (track.downloadUrl.isNotEmpty()) track.downloadUrl else track.streamUrl
                            DownloadUtils.openDownloadInChrome(context, dlUrl)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Download in Chrome",
                            tint = BrandRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play/Pause Button
                    FilledIconButton(
                        onClick = { MusicPlayerManager.togglePlayPause() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = BrandRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Next Track Button
                    IconButton(
                        onClick = { MusicPlayerManager.playNext() },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Tiny Progress Line at Bottom
                val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = BrandRed,
                    trackColor = CinemaBorder
                )
            }
        }
    }
}
