package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.TvChannel
import com.example.data.repository.MediaRepository
import com.example.ui.components.FilterChipItem
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*

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
    var selectedCategory by remember { mutableStateOf(if (initialChannel != null && initialChannel.groupTitle.isNotBlank()) initialChannel.groupTitle else "All") }
    var activeChannel by remember { mutableStateOf<TvChannel?>(initialChannel) }
    var isFullScreen by remember { mutableStateOf(false) }

    // When initialChannel changes from parent, immediately update activeChannel
    LaunchedEffect(initialChannel) {
        if (initialChannel != null) {
            activeChannel = initialChannel
            if (initialChannel.groupTitle.isNotBlank()) {
                selectedCategory = initialChannel.groupTitle
            }
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        channels = mediaRepository.getChannels()
        if (channels.isNotEmpty() && activeChannel == null) {
            val defaultChannel = channels.firstOrNull { 
                it.groupTitle.contains("Bangla", ignoreCase = true) ||
                it.name.contains("Channel I", ignoreCase = true) ||
                it.name.contains("NTV", ignoreCase = true)
            } ?: channels.firstOrNull { it.groupTitle.contains("Sports", ignoreCase = true) } ?: channels.first()
            activeChannel = defaultChannel
        }
        isLoading = false
    }

    val categories = remember(channels) {
        val list = mutableListOf("All")
        val groups = channels.map { it.groupTitle }.distinct().filter { it.isNotBlank() }
        list.addAll(groups)
        list
    }

    val filteredChannels = remember(channels, searchQuery, selectedCategory) {
        channels.filter { channel ->
            val matchCategory = selectedCategory == "All" || channel.groupTitle.equals(selectedCategory, ignoreCase = true)
            val matchSearch = searchQuery.isBlank() || channel.name.contains(searchQuery, ignoreCase = true)
            matchCategory && matchSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        // Embedded Live Player
        if (activeChannel != null) {
            val playerModifier = if (isFullScreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(270.dp)
            }
            Box(
                modifier = playerModifier.background(Color.Black)
            ) {
                key(activeChannel!!.id, activeChannel!!.streamUrl) {
                    VideoPlayerView(
                        videoUrl = activeChannel!!.streamUrl,
                        title = "🔴 LIVE • ${activeChannel!!.name}",
                        modifier = Modifier.fillMaxSize(),
                        onFullScreenToggle = { isFullScreen = !isFullScreen },
                        isFullScreen = isFullScreen
                    )
                }
            }
        }

        if (isFullScreen) return@Column

        // Search & Category Filters
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Channel Search Bar with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onBack != null) {
                    Surface(
                        onClick = {
                            if (activeChannel != null) {
                                activeChannel = null
                            } else {
                                onBack()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = CinemaSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("টিভি চ্যানেল খুঁজুন (Search Live Channel)", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CinemaSurface,
                        unfocusedContainerColor = CinemaSurface,
                        focusedBorderColor = AuthBrandPrimary,
                        unfocusedBorderColor = CinemaBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AuthBrandPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    FilterChipItem(
                        label = cat,
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }
        }

        // Channel Count Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "লাইভ চ্যানেল তালিকা (${filteredChannels.size})",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    isLoading = true
                    // Refresh
                    activeChannel = null
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        // Channel Grid
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandRed)
            }
        } else if (filteredChannels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো চ্যানেল পাওয়া যায়নি", color = TextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredChannels) { channel ->
                    val isCurrent = activeChannel?.id == channel.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                activeChannel = channel
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) CinemaSurfaceVariant else CinemaSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.5.dp, BrandRed) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Channel Logo
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!channel.logo.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(channel.logo)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Channel Name & Category
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = channel.name,
                                    color = if (isCurrent) BrandRed else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = channel.groupTitle,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isCurrent) {
                                Surface(
                                    color = BrandRed,
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                            }
                        }
                    }
                }
            }
        }
    }
}
