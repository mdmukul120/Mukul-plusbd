package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.download.DownloadStatus
import com.example.data.download.DownloadTask
import com.example.data.download.InAppDownloader
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*

@Composable
fun ExtractorScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        InAppDownloader.init(context)
    }

    val allTasks by InAppDownloader.tasks.collectAsState()
    val completedDownloads by InAppDownloader.completedDownloads.collectAsState()

    val activeTasks = remember(allTasks) {
        allTasks.values.filter {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
    }

    var activePlayFilePath by remember { mutableStateOf<String?>(null) }
    var activePlayTitle by remember { mutableStateOf<String>("") }

    if (activePlayFilePath != null) {
        // Play downloaded video offline
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
                        onClick = {
                            activePlayFilePath = null
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "অফলাইন প্লে: $activePlayTitle",
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
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                VideoPlayerView(
                    videoUrl = activePlayFilePath!!,
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
            // Header Stats Banner
            Surface(
                color = CinemaSurface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ইন-অ্যাপ মুভি ডাউনলোডার",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val totalBytes = completedDownloads.sumOf { it.totalBytes }
                        Text(
                            text = "${completedDownloads.size}টি মুভি সংরক্ষিত (${InAppDownloader.formatFileSize(totalBytes)})",
                            color = CyanAccent,
                            fontSize = 11.sp
                        )
                    }

                    Surface(
                        color = BrandRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = BrandRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ডিভাইস স্টোরেজ",
                                color = BrandRedLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ACTIVE IN-PROGRESS DOWNLOADS SECTION
                if (activeTasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "চলমান ডাউনলোডসমূহ (${activeTasks.size})",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(activeTasks, key = { it.id }) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (task.poster.isNotEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(task.poster)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(40.dp, 56.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "রেজুলেশন: ${task.quality} • ${task.progressPercent}%",
                                            color = CyanAccent,
                                            fontSize = 10.sp
                                        )
                                        if (task.speedText.isNotEmpty()) {
                                            Text(
                                                text = "স্পিড: ${task.speedText}",
                                                color = Color(0xFF10B981),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { InAppDownloader.cancelDownload(task.id) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel",
                                            tint = BrandRedLight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LinearProgressIndicator(
                                    progress = { task.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(2.5.dp)),
                                    color = BrandRed,
                                    trackColor = CinemaBorder
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${InAppDownloader.formatFileSize(task.downloadedBytes)} / ${InAppDownloader.formatFileSize(task.totalBytes)}",
                                        color = TextMuted,
                                        fontSize = 9.5.sp
                                    )
                                    Text(
                                        text = "অ্যাপে সরাসরি ডাউনলোড হচ্ছে",
                                        color = TextMuted,
                                        fontSize = 9.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // COMPLETED DOWNLOADS SECTION
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ডিভাইসে সংরক্ষিত ভিডিও ও মুভি তালিকা",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                if (completedDownloads.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = CinemaSurfaceVariant,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownloadOff,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "ডিভাইসে কোনো ডাউনলোড করা ভিডিও নেই",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ইউটিউব বা ওটিটি থেকে যেকোনো ভিডিও/মুভি ডাউনলোড বাটনে ক্লিক করলে তা সরাসরি অ্যাপের ভেতরে জমা হবে এবং অফলাইনে চালানো যাবে।",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(completedDownloads, key = { it.id }) { task ->
                        val isYouTube = task.movieSlug.startsWith("yt_")
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (task.poster.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(task.poster)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(50.dp, 70.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (isYouTube) BrandRed.copy(alpha = 0.2f) else CyanAccent.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (isYouTube) "ইউটিউব" else "ওটিটি",
                                                color = if (isYouTube) BrandRedLight else CyanAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = task.title,
                                            color = TextPrimary,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${task.quality} • ${InAppDownloader.formatFileSize(task.totalBytes)}",
                                        color = CyanAccent,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "ডিভাইসে সংরক্ষিত • অফলাইন প্লে",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp
                                    )
                                }

                                // Play Button
                                FilledTonalButton(
                                    onClick = {
                                        activePlayFilePath = task.filePath
                                        activePlayTitle = task.title
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = BrandRed,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("প্লে", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Delete Button
                                IconButton(
                                    onClick = {
                                        InAppDownloader.deleteDownloadedMovie(context, task.id)
                                        Toast.makeText(context, "মুভি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
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
