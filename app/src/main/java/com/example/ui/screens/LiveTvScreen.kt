package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import coil.request.ImageRequest
import com.example.data.model.TvChannel
import com.example.data.repository.MediaRepository
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*

enum class TvViewMode {
    GRID, LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    mediaRepository: MediaRepository,
    initialChannel: TvChannel? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var channels by remember { mutableStateOf<List<TvChannel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("সব") }
    var viewMode by remember { mutableStateOf(TvViewMode.GRID) }

    // সেকেন্ড পেজ / প্লেয়ার পেজ স্টেট
    var playingChannel by remember { mutableStateOf<TvChannel?>(initialChannel) }

    LaunchedEffect(initialChannel) {
        if (initialChannel != null) {
            playingChannel = initialChannel
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        channels = mediaRepository.getChannels()
        isLoading = false
    }

    // অটোমেটিক ক্যাটাগরি বাটন
    val categories = remember(channels) {
        val list = mutableListOf("সব")
        val groups = channels.map { it.groupTitle }.distinct().filter { it.isNotBlank() }
        list.addAll(groups)
        list
    }

    val filteredChannels = remember(channels, searchQuery, selectedCategory) {
        channels.filter { ch ->
            val matchCat = selectedCategory == "সব" || ch.groupTitle.equals(selectedCategory, ignoreCase = true)
            val matchSearch = searchQuery.isBlank() || ch.name.contains(searchQuery, ignoreCase = true)
            matchCat && matchSearch
        }
    }

    // ========================================================================
    // সেকেন্ড পেজ: ভিডিও প্লে হবে এবং তার নিচে একই ক্যাটাগরিতে থাকা আরো চ্যানেল
    // ========================================================================
    if (playingChannel != null) {
        val currentChannel = playingChannel!!
        BackHandler {
            playingChannel = null
        }

        val sameCategoryChannels = remember(currentChannel, channels) {
            channels.filter { it.groupTitle.equals(currentChannel.groupTitle, ignoreCase = true) && it.id != currentChannel.id }
                .ifEmpty { channels.filter { it.id != currentChannel.id } }
        }

        Scaffold(
            containerColor = CinemaBackground,
            topBar = {
                Surface(
                    color = CinemaSurface,
                    tonalElevation = 4.dp,
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
                        IconButton(onClick = { playingChannel = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔴 LIVE • ${currentChannel.name}",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentChannel.groupTitle.ifEmpty { "লাইভ টিভি" },
                                color = CyanAccent,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                // ১. ভিডিও প্লেয়ার
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        key(currentChannel.id, currentChannel.streamUrl) {
                            VideoPlayerView(
                                videoUrl = currentChannel.streamUrl,
                                title = "🔴 LIVE • ${currentChannel.name}",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // চ্যানেল ইনফো বার
                item {
                    Surface(
                        color = CinemaSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = CinemaSurface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (!currentChannel.logo.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = currentChannel.logo,
                                            contentDescription = currentChannel.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    } else {
                                        Text(currentChannel.name.take(2).uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(currentChannel.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = "ক্যাটাগরি: ${currentChannel.groupTitle.ifEmpty { "জেনারেল" }} • লাইভ স্ট্রিমিং",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // ২. তার নিচে একই ক্যাটাগরিতে থাকা আরো চ্যানেল
                item {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "একই ক্যাটাগরিতে থাকা আরো চ্যানেল (${currentChannel.groupTitle.ifEmpty { "টিভি" }})",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(sameCategoryChannels) { ch ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier
                                        .width(115.dp)
                                        .clickable { playingChannel = ch }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = CinemaSurfaceVariant,
                                            modifier = Modifier.size(50.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (!ch.logo.isNullOrEmpty()) {
                                                    AsyncImage(
                                                        model = ch.logo,
                                                        contentDescription = ch.name,
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.size(34.dp)
                                                    )
                                                } else {
                                                    Text(ch.name.take(2).uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = ch.name,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // ========================================================================
    // টিভি পেজ মেইন ভিউ
    // হেডারে সার্চ বার, নিচে অটোমেটিক ক্যাটাগরি ও একই লাইনে ডান পাশে ভিউ অপশন
    // তার নিচে টিভি চ্যানেল গুলো
    // ========================================================================
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
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    // হেডারে সার্চ বার
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("টিভি চ্যানেল খুঁজুন...", fontSize = 12.sp, color = TextMuted) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = CyanAccent, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = CinemaBorder,
                                focusedContainerColor = CinemaSurfaceVariant,
                                unfocusedContainerColor = CinemaSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // তার নিচে অটোমেটিক ক্যাটাগরি বাটন হরিজনটাল স্ক্রল হবে,
                    // একই লাইনে ডান পাশে থাকবে ভিউ অপশন (গ্রিড আকরে নাকি লিস্ট আকারে)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ক্যাটাগরি বাটন হরিজনটাল স্ক্রল
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSel = cat == selectedCategory
                                Surface(
                                    onClick = { selectedCategory = cat },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSel) CyanAccent else CinemaSurfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) CyanAccent else CinemaBorder),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 10.dp)
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSel) Color.Black else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // ভিউ অপশন: গ্রিড আকার নাকি লিস্ট আকার টগল বাটন
                        Surface(
                            onClick = {
                                viewMode = if (viewMode == TvViewMode.GRID) TvViewMode.LIST else TvViewMode.GRID
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = CinemaSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (viewMode == TvViewMode.GRID) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle View Mode",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanAccent)
            }
        } else if (filteredChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো চ্যানেল পাওয়া যায়নি", color = TextMuted, fontSize = 14.sp)
            }
        } else {
            if (viewMode == TvViewMode.GRID) {
                // গ্রিড আকারে চ্যানেল সাজানো
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredChannels, key = { it.id }) { ch ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playingChannel = ch }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = CinemaSurfaceVariant,
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (!ch.logo.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = ch.logo,
                                                contentDescription = ch.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.size(38.dp)
                                            )
                                        } else {
                                            Text(ch.name.take(2).uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = ch.name,
                                    color = TextPrimary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = ch.groupTitle.ifEmpty { "টিভি" },
                                    color = TextMuted,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                // লিস্ট আকারে চ্যানেল সাজানো
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredChannels, key = { it.id }) { ch ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playingChannel = ch }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = CinemaSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (!ch.logo.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = ch.logo,
                                                contentDescription = ch.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        } else {
                                            Text(ch.name.take(2).uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ch.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(ch.groupTitle.ifEmpty { "লাইভ টিভি" }, color = TextMuted, fontSize = 10.5.sp)
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BrandRed.copy(alpha = 0.2f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(BrandRed)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("LIVE", color = BrandRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
