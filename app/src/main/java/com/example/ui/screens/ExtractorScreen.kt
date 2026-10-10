package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import coil.compose.AsyncImage
import com.example.data.download.DownloadStatus
import com.example.data.download.DownloadTask
import com.example.data.download.InAppDownloader
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import java.io.File

enum class DownloadCategoryFilter(val title: String) {
    ALL("সব"),
    MOVIES("মুভি"),
    SERIES("সিরিজ"),
    MUSIC("মিউজিক")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractorScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(DownloadCategoryFilter.ALL) }
    var activePlayFilePath by remember { mutableStateOf<String?>(null) }
    var activePlayTitle by remember { mutableStateOf("") }

    // Collect in-app download tasks
    val tasksMap by InAppDownloader.tasks.collectAsState()
    val completedList by InAppDownloader.completedDownloads.collectAsState()

    // Combine all in-app tasks
    val allInAppTasks = remember(tasksMap, completedList) {
        val list = mutableListOf<DownloadTask>()
        tasksMap.values.forEach { list.add(it) }
        completedList.forEach { comp ->
            if (list.none { it.id == comp.id }) {
                list.add(comp)
            }
        }
        list
    }

    LaunchedEffect(Unit) {
        InAppDownloader.init(context)
    }

    if (activePlayFilePath != null) {
        BackHandler {
            activePlayFilePath = null
        }

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                Surface(
                    color = CinemaSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(54.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { activePlayFilePath = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                        Text(
                            text = activePlayTitle.ifEmpty { "অফলাইন ভিডিও" },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                VideoPlayerView(
                    videoUrl = activePlayFilePath!!,
                    title = activePlayTitle,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    val filteredTasks = remember(allInAppTasks, selectedCategory) {
        allInAppTasks.filter { task ->
            val titleLower = task.title.lowercase()
            val isMusic = titleLower.contains("গান") || titleLower.contains("music") || task.filePath.endsWith(".mp3")
            val isSeries = titleLower.contains("পর্ব") || titleLower.contains("series") || titleLower.contains("season") || task.movieSlug.contains("series")
            val isMovie = !isMusic && !isSeries

            when (selectedCategory) {
                DownloadCategoryFilter.ALL -> true
                DownloadCategoryFilter.MOVIES -> isMovie
                DownloadCategoryFilter.SERIES -> isSeries
                DownloadCategoryFilter.MUSIC -> isMusic
            }
        }
    }

    Scaffold(
        containerColor = CinemaBackground,
        topBar = {
            Surface(
                color = CinemaSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ডাউনলোড ও অফলাইন কন্টেন্ট",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "অ্যাপ্লিকেশনের ডাউনলোডকৃত কন্টেন্ট (${allInAppTasks.size} টি ফাইল)",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ক্যাটাগরি অনুযায়ী আলাদা করে: মিউজিক, মুভি, সিরিজ ইত্যাদি
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DownloadCategoryFilter.values().forEach { cat ->
                            val isSel = cat == selectedCategory
                            Surface(
                                onClick = { selectedCategory = cat },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) BrandRed else CinemaSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) BrandRed else CinemaBorder),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = cat.title,
                                        color = if (isSel) Color.White else TextSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (filteredTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = CinemaSurfaceVariant,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "এখনো কোনো কন্টেন্ট ডাউনলোড করা হয়নি",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "মুভি বা সিরিজ পেজে গিয়ে ডাউনলোড বাটনে ক্লিক করলে তা এখানে অফলাইনে দেখা যাবে।",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Poster
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CinemaSurfaceVariant,
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(85.dp)
                            ) {
                                if (task.poster.isNotEmpty()) {
                                    AsyncImage(
                                        model = task.poster,
                                        contentDescription = task.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Movie, contentDescription = null, tint = TextMuted)
                                    }
                                }
                            }

                            // Info & Progress
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = BrandRed.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = task.quality.ifEmpty { "HD" },
                                            color = BrandRed,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }

                                    val statusText = when (task.status) {
                                        DownloadStatus.COMPLETED -> "ডাউনলোড সম্পন্ন ✓"
                                        DownloadStatus.DOWNLOADING -> "ডাউনলোড হচ্ছে (${task.progressPercent}%)"
                                        DownloadStatus.QUEUED -> "অপেক্ষমান..."
                                        DownloadStatus.PAUSED -> "পজ করা"
                                        DownloadStatus.FAILED -> "ব্যর্থ হয়েছে"
                                        else -> "ডাউনলোড"
                                    }
                                    Text(
                                        text = statusText,
                                        color = if (task.status == DownloadStatus.COMPLETED) Color(0xFF00E676) else TextMuted,
                                        fontSize = 10.5.sp
                                    )
                                }

                                if (task.status == DownloadStatus.DOWNLOADING) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { task.progress },
                                        color = BrandRed,
                                        trackColor = CinemaSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = task.speedText.ifEmpty { "${task.downloadedBytes / (1024 * 1024)} MB" },
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }

                            // Actions
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (task.status == DownloadStatus.COMPLETED || File(task.filePath).exists()) {
                                    FilledIconButton(
                                        onClick = {
                                            activePlayFilePath = task.filePath
                                            activePlayTitle = task.title
                                        },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = BrandRed),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        InAppDownloader.cancelDownload(task.id)
                                        InAppDownloader.deleteDownloadedMovie(context, task.id)
                                        Toast.makeText(context, "মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
