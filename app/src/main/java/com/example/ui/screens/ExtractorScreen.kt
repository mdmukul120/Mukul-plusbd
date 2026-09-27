package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.download.DownloadStatus
import com.example.data.download.DownloadTask
import com.example.data.download.InAppDownloader
import com.example.data.model.MusicTrack
import com.example.data.player.MusicPlayerManager
import com.example.data.util.LocalAudioItem
import com.example.data.util.LocalMediaManager
import com.example.data.util.LocalVideoItem
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

private enum class LarkTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    VIDEOS("ভিডিও", Icons.Default.VideoLibrary),
    AUDIOS("গান / অডিও", Icons.Default.MusicNote),
    DOWNLOADS("ডাউনলোড", Icons.Default.CloudDownload),
    FOLDERS("ফোল্ডার", Icons.Default.Folder)
}

private enum class SortOrder(val label: String) {
    RECENT("নতুন প্রথম"),
    SIZE("সাইজ (বড়)"),
    NAME("নাম (A-Z)"),
    DURATION("দৈর্ঘ্য")
}

/**
 * Lark Player Style Local Media & Download Management Screen
 * ডিভাইসের সকল ভিডিও ও অডিও গান ব্রাউজ ও প্লে করা যায়, ডাউনলোড পরিচালনা করা যায়।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractorScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Permissions State
    var hasPermission by remember { mutableStateOf(LocalMediaManager.hasMediaPermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        hasPermission = granted
        if (granted) {
            Toast.makeText(context, "ডিভাইস মিডিয়া পারমিশন সফল হয়েছে!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "মিডিয়া ফাইল দেখার জন্য পারমিশন প্রয়োজন", Toast.LENGTH_LONG).show()
        }
    }

    // Media Data States
    var localVideos by remember { mutableStateOf<List<LocalVideoItem>>(emptyList()) }
    var localAudios by remember { mutableStateOf<List<LocalAudioItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Active Playback State
    var activePlayFilePath by remember { mutableStateOf<String?>(null) }
    var activePlayTitle by remember { mutableStateOf("") }

    // In-App Downloader State
    val allTasks by InAppDownloader.tasks.collectAsState()
    val completedDownloads by InAppDownloader.completedDownloads.collectAsState()

    val activeTasks = remember(allTasks) {
        allTasks.values.filter {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
    }

    // Navigation and Filter States
    var currentLarkTab by remember { mutableStateOf(LarkTab.VIDEOS) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSortOrder by remember { mutableStateOf(SortOrder.RECENT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedFolderFilter by remember { mutableStateOf<String?>(null) }

    // Rescan Local Media
    fun refreshMedia() {
        coroutineScope.launch {
            isScanning = true
            try {
                InAppDownloader.init(context)
                InAppDownloader.scanFiles(context)
                if (hasPermission) {
                    localVideos = LocalMediaManager.getAllVideos(context)
                    localAudios = LocalMediaManager.getAllAudios(context)
                }
            } catch (e: Exception) {
                // handle error
            } finally {
                isScanning = false
            }
        }
    }

    LaunchedEffect(hasPermission) {
        refreshMedia()
    }

    // System Back Handler:
    // If playing video, back cleanly closes the player!
    BackHandler(enabled = activePlayFilePath != null || selectedFolderFilter != null || searchQuery.isNotEmpty()) {
        when {
            activePlayFilePath != null -> {
                activePlayFilePath = null
                activePlayTitle = ""
            }
            selectedFolderFilter != null -> {
                selectedFolderFilter = null
            }
            searchQuery.isNotEmpty() -> {
                searchQuery = ""
            }
        }
    }

    // ==============================================================
    // 1. ACTIVE VIDEO PLAYBACK VIEW (WITH CLEAN CLOSE & LIFECYCLE)
    // ==============================================================
    if (activePlayFilePath != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar with Back and Direct Close button
                Surface(
                    color = Color.Black.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                activePlayFilePath = null
                                activePlayTitle = ""
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activePlayTitle,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "লার্ক প্লেয়ার • অফলাইন ভিডিও",
                                color = CyanAccent,
                                fontSize = 10.5.sp
                            )
                        }

                        // Prominent Close Button
                        IconButton(
                            onClick = {
                                activePlayFilePath = null
                                activePlayTitle = ""
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .background(BrandRed.copy(alpha = 0.8f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Full-Featured Video Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    VideoPlayerView(
                        videoUrl = activePlayFilePath!!,
                        title = activePlayTitle,
                        modifier = Modifier.fillMaxSize(),
                        onClose = {
                            activePlayFilePath = null
                            activePlayTitle = ""
                        }
                    )
                }
            }
        }
        return
    }

    // ==============================================================
    // 2. MAIN LARK PLAYER MEDIA HUB
    // ==============================================================
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        // TOP APP BAR: Lark Player Branding, Refresh, Search
        Surface(
            color = CinemaSurface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Lark Player Brand Badge
                        Surface(
                            color = BrandRed,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "লার্ক মিডিয়া প্লেয়ার",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = CyanAccent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LARK PRO",
                                        color = CyanAccent,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            val totalVideoCount = localVideos.size + completedDownloads.size
                            Text(
                                text = "${totalVideoCount}টি ভিডিও • ${localAudios.size}টি গান • ${activeTasks.size} ডাউনলোড",
                                color = TextMuted,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Refresh Button
                        IconButton(
                            onClick = { refreshMedia() },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Sort Menu Button
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(CinemaSurfaceVariant)
                            ) {
                                SortOrder.values().forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (selectedSortOrder == order) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Text(order.label, color = if (selectedSortOrder == order) BrandRed else TextPrimary, fontSize = 12.sp)
                                            }
                                        },
                                        onClick = {
                                            selectedSortOrder = order
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar in Lark Player
                Surface(
                    color = CinemaSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "ডিভাইসের ভিডিও ও গান খুঁজুন...",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
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
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Lark Player 4 Main Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LarkTab.values().forEach { tab ->
                        val isSelected = currentLarkTab == tab
                        Surface(
                            onClick = {
                                currentLarkTab = tab
                                selectedFolderFilter = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==============================================================
        // PERMISSION NOTICE BANNER IF NOT GRANTED
        // ==============================================================
        if (!hasPermission) {
            Surface(
                color = CinemaSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BrandRed.copy(alpha = 0.2f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FolderShared, contentDescription = null, tint = BrandRed, modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ডিভাইস মিডিয়া পারমিশন দিন",
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ফোনের মেমোরি কার্ড বা স্টোরেজে থাকা সকল অডিও গান ও ভিডিও সরাসরি লার্ক প্লেয়ারে চালাতে অনুমতি দিন।",
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            permissionLauncher.launch(LocalMediaManager.getRequiredPermissions())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("অনুমতি দিন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active filter indicator if folder selected
        if (selectedFolderFilter != null) {
            Surface(
                color = CinemaSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ফোল্ডার: $selectedFolderFilter",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { selectedFolderFilter = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // ==============================================================
        // TAB CONTENTS (LazyColumn)
        // ==============================================================
        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (currentLarkTab) {
                LarkTab.VIDEOS -> {
                    // Filter and Sort Videos
                    val filteredVideos = remember(localVideos, searchQuery, selectedSortOrder, selectedFolderFilter) {
                        var list = localVideos.filter {
                            val matchesSearch = searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true)
                            val matchesFolder = selectedFolderFilter == null || it.folderName == selectedFolderFilter
                            matchesSearch && matchesFolder
                        }
                        when (selectedSortOrder) {
                            SortOrder.RECENT -> list.sortedByDescending { it.dateModified }
                            SortOrder.SIZE -> list.sortedByDescending { it.sizeBytes }
                            SortOrder.NAME -> list.sortedBy { it.title.lowercase() }
                            SortOrder.DURATION -> list.sortedByDescending { it.durationMs }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
                    ) {
                        if (isScanning && filteredVideos.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = BrandRed)
                                }
                            }
                        } else if (filteredVideos.isEmpty()) {
                            item {
                                EmptyMediaState(
                                    icon = Icons.Default.VideoLibrary,
                                    title = "কোনো ভিডিও পাওয়া যায়নি",
                                    subtitle = if (!hasPermission) "ভিডিও দেখার জন্য উপরের পারমিশন বাটনে ক্লিক করুন" else "আপনার ডিভাইসে কোনো সমর্থিত ভিডিও ফরম্যাট পাওয়া যায়নি।"
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = "সকল ভিডিও ফাইল (${filteredVideos.size}টি)",
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            items(filteredVideos, key = { it.id }) { video ->
                                LarkVideoCard(
                                    video = video,
                                    onPlay = {
                                        activePlayFilePath = video.filePath
                                        activePlayTitle = video.title
                                    },
                                    onShare = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/*"
                                            putExtra(Intent.EXTRA_STREAM, Uri.parse(video.contentUri))
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "ভিডিও শেয়ার করুন"))
                                    }
                                )
                            }
                        }
                    }
                }

                LarkTab.AUDIOS -> {
                    // Filter and Sort Audios
                    val filteredAudios = remember(localAudios, searchQuery, selectedSortOrder, selectedFolderFilter) {
                        var list = localAudios.filter {
                            val matchesSearch = searchQuery.isBlank() ||
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                it.artist.contains(searchQuery, ignoreCase = true) ||
                                it.displayName.contains(searchQuery, ignoreCase = true)
                            val matchesFolder = selectedFolderFilter == null || it.folderName == selectedFolderFilter
                            matchesSearch && matchesFolder
                        }
                        when (selectedSortOrder) {
                            SortOrder.RECENT -> list.sortedByDescending { it.dateModified }
                            SortOrder.SIZE -> list.sortedByDescending { it.sizeBytes }
                            SortOrder.NAME -> list.sortedBy { it.title.lowercase() }
                            SortOrder.DURATION -> list.sortedByDescending { it.durationMs }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
                    ) {
                        if (isScanning && filteredAudios.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = BrandRed)
                                }
                            }
                        } else if (filteredAudios.isEmpty()) {
                            item {
                                EmptyMediaState(
                                    icon = Icons.Default.MusicNote,
                                    title = "কোনো অডিও গান পাওয়া যায়নি",
                                    subtitle = if (!hasPermission) "গান শোনার জন্য উপরের পারমিশন বাটনে ক্লিক করুন" else "ডিভাইস স্টোরেজে কোনো MP3 বা অডিও গান পাওয়া যায়নি।"
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = "সকল অডিও গান (${filteredAudios.size}টি)",
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }

                            items(filteredAudios, key = { it.id }) { audio ->
                                LarkAudioCard(
                                    audio = audio,
                                    onPlay = {
                                        // Play via MusicPlayerManager
                                        val track = MusicTrack(
                                            id = audio.id.toString(),
                                            name = audio.title,
                                            artistNames = audio.artist,
                                            albumName = audio.album,
                                            duration = (audio.durationMs / 1000).toInt(),
                                            streamUrl = audio.filePath
                                        )
                                        MusicPlayerManager.playTrack(track, listOf(track))
                                        Toast.makeText(context, "গান বাজানো হচ্ছে: ${audio.title}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                LarkTab.DOWNLOADS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
                    ) {
                        // 1. In-Progress Active Downloads
                        if (activeTasks.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "চলমান ডাউনলোডসমূহ (${activeTasks.size})",
                                        color = TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(color = BrandRed.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text("লাইভ ডাউনলোডার", color = BrandRedLight, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }

                            items(activeTasks, key = { it.id }) { task ->
                                LarkActiveDownloadCard(
                                    task = task,
                                    onCancel = { InAppDownloader.cancelDownload(task.id) }
                                )
                            }
                        }

                        // 2. Completed In-App Downloads
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "অ্যাপে ডাউনলোডকৃত ফাইলসমূহ (${completedDownloads.size}টি)",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (completedDownloads.isEmpty()) {
                            item {
                                EmptyMediaState(
                                    icon = Icons.Default.CloudDone,
                                    title = "কোনো ইন-অ্যাপ ডাউনলোড নেই",
                                    subtitle = "ইউটিউব, রিলস বা ওটিটি মুভি থেকে যেকোনো ডাউনলোড বাটনে চাপলে সরাসরি এখানে অফলাইনে যুক্ত হবে।"
                                )
                            }
                        } else {
                            items(completedDownloads, key = { it.id }) { task ->
                                LarkCompletedDownloadCard(
                                    task = task,
                                    onPlay = {
                                        val file = File(task.filePath)
                                        val path = if (file.exists() && file.length() > 0) task.filePath else {
                                            val fallback = File(InAppDownloader.getDownloadDirectory(context), task.id)
                                            if (fallback.exists() && fallback.length() > 0) fallback.absolutePath else null
                                        }

                                        if (path != null) {
                                            activePlayFilePath = path
                                            activePlayTitle = task.title
                                        } else {
                                            Toast.makeText(context, "ফাইলটি খুঁজে পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onDelete = {
                                        InAppDownloader.deleteDownloadedMovie(context, task.id)
                                        Toast.makeText(context, "মুভি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                LarkTab.FOLDERS -> {
                    // Group local media by folder
                    val videoFolders = localVideos.groupBy { it.folderName }
                    val audioFolders = localAudios.groupBy { it.folderName }
                    val allFolders = (videoFolders.keys + audioFolders.keys).distinct().sorted()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
                    ) {
                        item {
                            Text(
                                text = "ডিভাইসের মিডিয়া ফোল্ডারসমূহ (${allFolders.size}টি)",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (allFolders.isEmpty()) {
                            item {
                                EmptyMediaState(
                                    icon = Icons.Default.Folder,
                                    title = "কোনো ফোল্ডার পাওয়া যায়নি",
                                    subtitle = "ডিভাইস স্টোরেজের পারমিশন দিন এবং ফাইল রিফ্রেশ করুন।"
                                )
                            }
                        } else {
                            items(allFolders, key = { it }) { folderName ->
                                val vCount = videoFolders[folderName]?.size ?: 0
                                val aCount = audioFolders[folderName]?.size ?: 0

                                Surface(
                                    onClick = {
                                        selectedFolderFilter = folderName
                                        currentLarkTab = if (vCount >= aCount) LarkTab.VIDEOS else LarkTab.AUDIOS
                                    },
                                    color = CinemaSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = CinemaSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(42.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Folder, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = folderName,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${vCount}টি ভিডিও • ${aCount}টি গান",
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(20.dp)
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

/**
 * Modern Lark Player Video Card Item
 */
@Composable
private fun LarkVideoCard(
    video: LocalVideoItem,
    onPlay: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        onClick = onPlay,
        color = CinemaSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail Box with Duration Badge
            Box(
                modifier = Modifier
                    .size(96.dp, 62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaSurfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.contentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Play icon overlay
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(26.dp).align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }

                // Duration badge at bottom right
                if (video.durationFormatted != "--:--") {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp)
                    ) {
                        Text(
                            text = video.durationFormatted,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = CyanAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = video.folderName,
                            color = CyanAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = video.sizeFormatted,
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                    if (video.resolution.isNotEmpty()) {
                        Text(
                            text = " • ${video.resolution}",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Action Buttons
            FilledTonalButton(
                onClick = onPlay,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandRed, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("প্লে", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Modern Lark Player Audio Card Item
 */
@Composable
private fun LarkAudioCard(
    audio: LocalAudioItem,
    onPlay: () -> Unit
) {
    Surface(
        onClick = onPlay,
        color = CinemaSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = CinemaSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = BrandRed, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.title,
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${audio.artist} • ${audio.durationFormatted}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${audio.folderName} • ${audio.sizeFormatted}",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(34.dp).background(BrandRed.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = BrandRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Active Live Downloading Task Card
 */
@Composable
private fun LarkActiveDownloadCard(
    task: DownloadTask,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.poster.isNotEmpty()) {
                    AsyncImage(
                        model = task.poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp, 56.dp).clip(RoundedCornerShape(6.dp))
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
                        text = "কোয়ালিটি: ${task.quality} • ${task.progressPercent}%",
                        color = CyanAccent,
                        fontSize = 10.5.sp
                    )
                    if (task.speedText.isNotEmpty()) {
                        Text(
                            text = "গতি: ${task.speedText}",
                            color = Color(0xFF10B981),
                            fontSize = 9.5.sp
                        )
                    }
                }

                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = BrandRedLight, modifier = Modifier.size(18.dp))
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

/**
 * Completed Download Card
 */
@Composable
private fun LarkCompletedDownloadCard(
    task: DownloadTask,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val isYouTube = task.movieSlug.startsWith("yt_")
    Surface(
        onClick = onPlay,
        color = CinemaSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (task.poster.isNotEmpty()) {
                AsyncImage(
                    model = task.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(6.dp))
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
                            text = if (isYouTube) "ইউটিউব" else "ওটিটি মুভি",
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
                    text = "অফলাইন প্লে প্রস্তুত",
                    color = Color(0xFF10B981),
                    fontSize = 10.sp
                )
            }

            FilledTonalButton(
                onClick = onPlay,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandRed, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text("প্লে", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyMediaState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = CinemaSurfaceVariant,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
