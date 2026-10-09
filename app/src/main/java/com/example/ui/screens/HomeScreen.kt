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
import androidx.compose.ui.graphics.Brush
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
import com.example.data.util.LanguageManager
import com.example.ui.components.HeroSlider
import com.example.ui.components.MoviePosterCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    mediaRepository: MediaRepository,
    onSelectMovie: (Long) -> Unit,
    onSelectPost: (ExtractorPost) -> Unit,
    onSelectChannel: (TvChannel) -> Unit,
    onNavigateToMovies: (MoviesMainTab?) -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToExtractor: () -> Unit,
    onNavigateToMusic: () -> Unit = {},
    onNavigateToWeather: () -> Unit = {},
    onNavigateToBanglaOtt: () -> Unit = {},
    onNavigateToSports: () -> Unit = {},
    onSelectMukulMovie: (String) -> Unit = {},
    onOpenPluginManager: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val cachedFeed by mediaRepository.cachedHomeFeed.collectAsState()

    var trendingMovies by remember { mutableStateOf(cachedFeed?.trendingMovies ?: emptyList()) }
    var bongoVideos by remember { mutableStateOf(cachedFeed?.bongoVideos ?: emptyList()) }
    var liveChannels by remember { mutableStateOf(cachedFeed?.liveChannels ?: emptyList()) }
    var mukulLatestMovies by remember { mutableStateOf<List<MukulOttMovieItem>>(MukulOttRepository.cachedMovies) }
    var providerPosts by remember { mutableStateOf<List<ExtractorPost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(cachedFeed == null || !cachedFeed!!.isLoaded) }

    // Fetch real data without any demo content
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

            // 5. Fast extractor posts for downloads section
            providerPosts = ApiClient.fetchExtractorPosts("moviesmod", page = 1)

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
            .background(CinemaBackground)
    ) {
        // ----------------------------------------------------
        // 1. HERO IMAGE SLIDER (সবার উপরে প্রিমিয়াম ব্যানার)
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
        // 2. QUICK SHORTCUT PILLS (ক্যাটাগরি বাটন রো)
        // ----------------------------------------------------
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeQuickPill(
                    icon = Icons.Default.VideoLibrary,
                    title = "মুকুল ওটিটি",
                    color = BrandRed,
                    onClick = { onNavigateToMovies(MoviesMainTab.MUKUL_OTT) }
                )
                HomeQuickPill(
                    icon = Icons.Default.PlayCircle,
                    title = "বঙ্গ ওটিটি",
                    color = Color(0xFFE50914),
                    onClick = { onNavigateToMovies(MoviesMainTab.BONGO_OTT) }
                )
                HomeQuickPill(
                    icon = Icons.Default.Tv,
                    title = "লাইভ টিভি",
                    color = CyanAccent,
                    onClick = onNavigateToLiveTv
                )
                HomeQuickPill(
                    icon = Icons.Default.SportsCricket,
                    title = "স্পোর্টস",
                    color = Color(0xFF00E676),
                    onClick = onNavigateToSports
                )
                HomeQuickPill(
                    icon = Icons.Default.MusicNote,
                    title = "মিউজিক",
                    color = Color(0xFFFFB020),
                    onClick = onNavigateToMusic
                )
                HomeQuickPill(
                    icon = Icons.Default.CloudDownload,
                    title = "ডাউনলোড",
                    color = Color(0xFF8B5CF6),
                    onClick = onNavigateToExtractor
                )
                HomeQuickPill(
                    icon = Icons.Default.AddCircleOutline,
                    title = "প্লাগইন (+)",
                    color = BrandRed,
                    onClick = { onOpenPluginManager?.invoke() }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ----------------------------------------------------
        // 3. BANGLADESHI LIVE TV CHANNELS (সার্কেল লিস্ট)
        // ----------------------------------------------------
        item {
            SectionHeader(
                title = "লাইভ টিভি চ্যানেল",
                subtitle = "বাংলাদেশী ও আন্তর্জাতিক টিভি চ্যানেল",
                onSeeAllClick = onNavigateToLiveTv
            )

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
                        it.name.contains("Banglavision", ignoreCase = true) ||
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ----------------------------------------------------
        // 4. MUKUL OTT - LATEST RELEASES & TRENDING
        // (আসল মুকুল ওটিটি কার্ড - এক ক্লিকে মুভি পেজে প্লে)
        // ----------------------------------------------------
        if (mukulLatestMovies.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "🌟 মুকুল ওটিটি - লেটেস্ট রিলিজ",
                    subtitle = "সরাসরি ১০৮০p / ৭২০p ফুল এইচডি স্ট্রিমিং ও ডাউনলোড",
                    onSeeAllClick = { onNavigateToMovies(MoviesMainTab.MUKUL_OTT) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(mukulLatestMovies.take(20)) { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                            modifier = Modifier
                                .width(125.dp)
                                .clickable {
                                    onSelectMukulMovie(item.slug)
                                }
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(175.dp)
                                        .background(CinemaSurfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = item.poster,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Play icon overlay
                                    Surface(
                                        shape = CircleShape,
                                        color = BrandRed.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .size(32.dp)
                                            .align(Alignment.Center)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    // Quality badge
                                    if (item.qualityTag.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(bottomEnd = 6.dp),
                                            color = BrandRed,
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = item.qualityTag,
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Year
                                    if (item.year > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = "${item.year}",
                                                color = Color(0xFFFFB020),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = item.title,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ----------------------------------------------------
        // 5. BONGO BD ORIGINALS & WEB SERIES
        // ----------------------------------------------------
        if (bongoVideos.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "🎬 বঙ্গ ওটিটি - মেগা সিরিজ ও নাটক",
                    subtitle = "ব্যাচেলর পয়েন্ট, সুলতান সালাহউদ্দিন ও ড্রামা কালেকশন",
                    onSeeAllClick = { onNavigateToMovies(MoviesMainTab.BONGO_OTT) }
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(bongoVideos.take(20)) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            onClick = { onSelectMovie(movie.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ----------------------------------------------------
        // 6. LIVE SPORTS BANNER
        // ----------------------------------------------------
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clickable { onNavigateToSports() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF00E676).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            Icons.Default.SportsCricket,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.padding(10.dp).size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF00E676), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE SPORTS",
                                color = Color(0xFF00E676),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "লাইভ ক্রিকেট ও ফুটবল ম্যাচ",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "T Sports, Star Sports ও লাইভ স্কোর দেখুন",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onNavigateToSports,
                        colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("দেখুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ----------------------------------------------------
        // 7. MUKUL MUSIC PROMO
        // ----------------------------------------------------
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .clickable { onNavigateToMusic() },
                shape = RoundedCornerShape(16.dp),
                color = CinemaSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.35f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(BrandRed.copy(alpha = 0.3f), CinemaSurface)
                            )
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandRed,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "মুকুল মিউজিক হাব (Music & Hits)",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "বাংলা গান, হিন্দি ৯০s ও টপ চার্ট অডিও শুনুন",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BrandRed, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ----------------------------------------------------
        // 8. FAST DOWNLOADS / EXTRACTOR SECTION
        // ----------------------------------------------------
        if (providerPosts.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "📥 সরাসরি ডাউনলোড হাব",
                    subtitle = "সিনেমা ও সিরিজের সরাসরি অফলাইন ডাউনলোড",
                    onSeeAllClick = onNavigateToExtractor
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(providerPosts.take(10)) { post ->
                        Card(
                            modifier = Modifier
                                .width(125.dp)
                                .clickable { onSelectPost(post) },
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .background(CinemaSurfaceVariant)
                                ) {
                                    if (!post.image.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = post.image,
                                            contentDescription = post.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Surface(
                                        color = BrandRed,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                                    ) {
                                        Text(text = "DL", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                Text(
                                    text = post.title,
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

// ==========================================
// QUICK PILL BUTTON
// ==========================================
@Composable
private fun HomeQuickPill(
    icon: ImageVector,
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = CinemaSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ==========================================
// SECTION HEADER
// ==========================================
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
        }
        TextButton(
            onClick = onSeeAllClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("সব দেখুন", color = BrandRedLight, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BrandRedLight, modifier = Modifier.size(16.dp))
        }
    }
}

// ==========================================
// CIRCULAR TV CHANNEL AVATAR
// ==========================================
@Composable
private fun CircularChannelAvatar(
    channel: TvChannel,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glowing border
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                AuthBrandGradientStart,
                                AuthBrandPrimary,
                                Color(0xFFFFB020),
                                AuthBrandGradientEnd,
                                AuthBrandGradientStart
                            )
                        )
                    )
            )

            // Inner image
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(CinemaSurface),
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
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = AuthBrandPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Red LIVE Dot Badge
            Surface(
                color = BrandRed,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 3.dp)
            ) {
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = channel.name,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
