package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.ApiClient
import com.example.data.model.*
import com.example.data.repository.MediaRepository
import com.example.data.repository.MukulOttRepository
import com.example.ui.components.HeroSlider
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    mediaRepository: MediaRepository,
    onSelectMovie: (Long) -> Unit,
    onSelectChannel: (TvChannel) -> Unit,
    onNavigateToMovies: (MoviesMainTab?) -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToMusic: () -> Unit = {},
    onNavigateToWeather: () -> Unit = {},
    onNavigateToYoutube: () -> Unit = {},
    onSelectMukulMovie: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cachedFeed by mediaRepository.cachedHomeFeed.collectAsState()

    var trendingMovies by remember { mutableStateOf(cachedFeed?.trendingMovies ?: emptyList()) }
    var bongoVideos by remember { mutableStateOf(cachedFeed?.bongoVideos ?: emptyList()) }
    var liveChannels by remember { mutableStateOf(cachedFeed?.liveChannels ?: emptyList()) }
    var mukulLatestMovies by remember { mutableStateOf<List<MukulOttMovieItem>>(MukulOttRepository.cachedMovies) }
    var isLoading by remember { mutableStateOf(cachedFeed == null || !cachedFeed!!.isLoaded) }

    LaunchedEffect(Unit) {
        if (cachedFeed != null && cachedFeed!!.isLoaded && trendingMovies.isNotEmpty() && mukulLatestMovies.isNotEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            // 1. Trending movies from Ctg / Bongo
            val trendingRes = ApiClient.fetchCtgMovies(library = 1, page = 1, sort = "createdAt")
            trendingMovies = trendingRes.data

            // 2. Real Live TV Channels
            liveChannels = mediaRepository.getChannels()

            // 3. Real Bongo BD Originals & Series
            val bongoList = mediaRepository.getBongoVideos()
            bongoVideos = bongoList

            // 4. Real Mukul OTT catalog page 1
            if (mukulLatestMovies.isEmpty()) {
                val loadedMukul = MukulOttRepository.getPageOf50Movies(1)
                mukulLatestMovies = loadedMukul
                MukulOttRepository.cachedMovies = loadedMukul
                MukulOttRepository.isLoaded = true
            }

            // Cache in media repository
            mediaRepository.updateCachedHomeFeed(
                com.example.data.repository.HomeFeedData(
                    trendingMovies = trendingMovies,
                    bongoVideos = bongoVideos,
                    hollywoodMovies = emptyList(),
                    bollywoodMovies = emptyList(),
                    banglaMovies = emptyList(),
                    southActionMovies = emptyList(),
                    topRatedMovies = emptyList(),
                    animationMovies = emptyList(),
                    liveChannels = liveChannels,
                    isLoaded = true
                )
            )
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground),
        contentPadding = PaddingValues(bottom = 72.dp)
    ) {
        // ----------------------------------------------------
        // 1. ইমেজ স্লাইডার (Hero Image Slider)
        // ----------------------------------------------------
        item {
            HeroSlider(
                movies = if (trendingMovies.isNotEmpty()) trendingMovies else bongoVideos,
                onMovieClick = { movie -> onSelectMovie(movie.id) },
                onWatchlistToggle = { movie -> mediaRepository.toggleFavorite(movie.id) },
                isFavorite = { id -> mediaRepository.isFavorite(id) }
            )
        }

        // ----------------------------------------------------
        // 2. বাটন যেমন: মুভি, ওয়েদার, মিউজিক, bongo, ইউটিউব, ইত্যাদি
        // ----------------------------------------------------
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeActionShortcutButton(
                    icon = Icons.Default.Movie,
                    title = "মুভি",
                    color = BrandRed,
                    onClick = { onNavigateToMovies(MoviesMainTab.MUKUL_OTT) }
                )
                HomeActionShortcutButton(
                    icon = Icons.Default.WbSunny,
                    title = "ওয়েদার",
                    color = Color(0xFFFFB020),
                    onClick = onNavigateToWeather
                )
                HomeActionShortcutButton(
                    icon = Icons.Default.MusicNote,
                    title = "মিউজিক",
                    color = Color(0xFF00E676),
                    onClick = onNavigateToMusic
                )
                HomeActionShortcutButton(
                    icon = Icons.Default.Subscriptions,
                    title = "Bongo",
                    color = Color(0xFFE50914),
                    onClick = { onNavigateToMovies(MoviesMainTab.BONGO_OTT) }
                )
                HomeActionShortcutButton(
                    icon = Icons.Default.PlayCircle,
                    title = "ইউটিউব",
                    color = Color(0xFFFF3333),
                    onClick = onNavigateToYoutube
                )
                HomeActionShortcutButton(
                    icon = Icons.Default.Tv,
                    title = "টিভি",
                    color = CyanAccent,
                    onClick = onNavigateToLiveTv
                )
                HomeActionShortcutButton(
                    icon = Icons.Default.CloudDownload,
                    title = "ডাউনলোড",
                    color = Color(0xFF8B5CF6),
                    onClick = onNavigateToDownloads
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ----------------------------------------------------
        // 3. টিভি চ্যানেল হরিজনটাল স্ক্রল
        // ----------------------------------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "টিভি চ্যানেল",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "বাংলাদেশী ও আন্তর্জাতিক লাইভ টিভি",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = onNavigateToLiveTv) {
                    Text("সব দেখুন", color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BrandRed, modifier = Modifier.size(16.dp))
                }
            }

            val displayChannels = remember(liveChannels) {
                if (liveChannels.isNotEmpty()) {
                    val banglaList = liveChannels.filter {
                        it.groupTitle.contains("Bangla", ignoreCase = true) ||
                        it.name.contains("Somoy", ignoreCase = true) ||
                        it.name.contains("Jamuna", ignoreCase = true) ||
                        it.name.contains("Channel 24", ignoreCase = true) ||
                        it.name.contains("Channel i", ignoreCase = true) ||
                        it.name.contains("Ekattor", ignoreCase = true) ||
                        it.name.contains("NTV", ignoreCase = true) ||
                        it.name.contains("ATN", ignoreCase = true) ||
                        it.name.contains("Deepto", ignoreCase = true) ||
                        it.name.contains("T Sports", ignoreCase = true) ||
                        it.name.contains("BTV", ignoreCase = true)
                    }
                    if (banglaList.isNotEmpty()) banglaList.take(20) else liveChannels.take(20)
                } else emptyList()
            }

            if (displayChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(75.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandRed, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(displayChannels) { channel ->
                        CircularChannelAvatar(
                            channel = channel,
                            onClick = { onSelectChannel(channel) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // ----------------------------------------------------
        // 4. Bongo এর কিছু ভিডিও এবং আরো দেখুন বাটন
        // ----------------------------------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Bongo ভিডিও ও সিরিজ",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ব্যাচেলর পয়েন্ট, সুলতান সালাহউদ্দিন ও ড্রামা",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                FilledTonalButton(
                    onClick = { onNavigateToMovies(MoviesMainTab.BONGO_OTT) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = BrandRed
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("আরো দেখুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(15.dp))
                }
            }

            if (bongoVideos.isEmpty() && isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandRed, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(bongoVideos.take(20)) { movie ->
                        PurePosterCard(
                            imageUrl = movie.getFullPosterUrl(),
                            contentDescription = movie.title,
                            onClick = { onSelectMovie(movie.id) },
                            width = 115,
                            height = 165
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // ----------------------------------------------------
        // 5. mukul-ott API এর থাকা ভিডিও এবং আরো দেখুন বাটন
        // ----------------------------------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mukul OTT ভিডিও",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "সরাসরি ১০৮০p / ৭২০p ফুল এইচডি স্ট্রিমিং",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                FilledTonalButton(
                    onClick = { onNavigateToMovies(MoviesMainTab.MUKUL_OTT) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CinemaSurfaceVariant,
                        contentColor = BrandRed
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("আরো দেখুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(15.dp))
                }
            }

            if (mukulLatestMovies.isEmpty() && isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandRed, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(mukulLatestMovies.take(20)) { item ->
                        PurePosterCard(
                            imageUrl = item.poster,
                            contentDescription = item.title,
                            onClick = { onSelectMukulMovie(item.slug) },
                            width = 115,
                            height = 165
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Pure poster card with zero text overlay on top of the image
 * Clean rounded poster card designed strictly to requirement:
 * "মুভি কার্ডের ইমেজে উপর কোন প্রকার লেখা থাকবে না"
 */
@Composable
fun PurePosterCard(
    imageUrl: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 115,
    height: Int = 165
) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .width(width.dp)
            .height(height.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CinemaSurfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun HomeActionShortcutButton(
    icon: ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CinemaSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CircularChannelAvatar(
    channel: TvChannel,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            color = CinemaSurfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, BrandRed.copy(alpha = 0.5f)),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!channel.logo.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.logo)
                            .crossfade(true)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = channel.name.take(2).uppercase(),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = channel.name,
            color = TextPrimary,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
