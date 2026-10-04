package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
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
import com.example.data.api.ApiClient
import com.example.data.model.*
import com.example.data.repository.MediaRepository
import com.example.data.util.LanguageManager
import com.example.ui.components.HeroSlider
import com.example.ui.components.MoviePosterCard
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    mediaRepository: MediaRepository,
    onSelectMovie: (Long) -> Unit,
    onSelectPost: (ExtractorPost) -> Unit,
    onSelectChannel: (TvChannel) -> Unit,
    onNavigateToMovies: () -> Unit,
    onNavigateToLiveTv: () -> Unit,
    onNavigateToExtractor: () -> Unit,
    onNavigateToMusic: () -> Unit = {},
    onNavigateToWeather: () -> Unit = {},
    onNavigateToBanglaOtt: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val cachedFeed by mediaRepository.cachedHomeFeed.collectAsState()

    // Category movie lists
    var trendingMovies by remember { mutableStateOf(cachedFeed?.trendingMovies ?: emptyList()) }
    var bongoVideos by remember { mutableStateOf(cachedFeed?.bongoVideos ?: emptyList()) }
    var hollywoodMovies by remember { mutableStateOf(cachedFeed?.hollywoodMovies ?: emptyList()) }
    var bollywoodMovies by remember { mutableStateOf(cachedFeed?.bollywoodMovies ?: emptyList()) }
    var banglaMovies by remember { mutableStateOf(cachedFeed?.banglaMovies ?: emptyList()) }
    var southActionMovies by remember { mutableStateOf(cachedFeed?.southActionMovies ?: emptyList()) }
    var topRatedMovies by remember { mutableStateOf(cachedFeed?.topRatedMovies ?: emptyList()) }
    var animationMovies by remember { mutableStateOf(cachedFeed?.animationMovies ?: emptyList()) }
    var providerPosts by remember { mutableStateOf<List<ExtractorPost>>(emptyList()) }
    var liveChannels by remember { mutableStateOf(cachedFeed?.liveChannels ?: emptyList()) }
    var isLoading by remember { mutableStateOf(cachedFeed == null || !cachedFeed!!.isLoaded) }

    LaunchedEffect(Unit) {
        if (cachedFeed != null && cachedFeed!!.isLoaded && trendingMovies.isNotEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            // 1. Trending
            val trendingRes = ApiClient.fetchCtgMovies(library = 1, page = 1, sort = "createdAt")
            trendingMovies = trendingRes.data

            // 2. Live TV Channels (for circular display)
            liveChannels = mediaRepository.getChannels()

            // 3. Bongo BD Exclusives, Web Series & Natoks
            bongoVideos = mediaRepository.getBongoVideos()

            // 4. Hollywood
            val hollywoodRes = ApiClient.fetchCtgMovies(library = 1, page = 2, sort = "createdAt")
            hollywoodMovies = hollywoodRes.data

            // 4. Bollywood
            val bollywoodRes = ApiClient.fetchCtgMovies(library = 4, page = 1, sort = "createdAt")
            bollywoodMovies = bollywoodRes.data

            // 5. Bangla
            val banglaRes = ApiClient.fetchCtgMovies(library = 6, page = 1, sort = "createdAt")
            banglaMovies = banglaRes.data

            // 6. South / Action
            val southRes = ApiClient.fetchCtgMovies(genre = "Action", page = 1)
            southActionMovies = southRes.data

            // 7. Top Rated
            val topRatedRes = ApiClient.fetchCtgMovies(sort = "online_rating", sortOrder = "DESC", page = 1)
            topRatedMovies = topRatedRes.data

            // 8. Animation
            val animRes = ApiClient.fetchCtgMovies(genre = "Animation", page = 1)
            animationMovies = animRes.data

            // 9. Extractor Posts
            providerPosts = ApiClient.fetchExtractorPosts("moviesmod", page = 1)

            // Cache all home feed items
            mediaRepository.updateCachedHomeFeed(
                com.example.data.repository.HomeFeedData(
                    trendingMovies = trendingMovies,
                    bongoVideos = bongoVideos,
                    hollywoodMovies = hollywoodMovies,
                    bollywoodMovies = bollywoodMovies,
                    banglaMovies = banglaMovies,
                    southActionMovies = southActionMovies,
                    topRatedMovies = topRatedMovies,
                    animationMovies = animationMovies,
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
        // 1. HERO IMAGE SLIDER (সবার উপরে ইমেজ স্লাইডার)
        // ----------------------------------------------------
        item {
            HeroSlider(
                movies = trendingMovies,
                onMovieClick = { movie -> onSelectMovie(movie.id) },
                onWatchlistToggle = { movie -> mediaRepository.toggleFavorite(movie.id) },
                isFavorite = { id -> mediaRepository.isFavorite(id) }
            )
        }

        // ----------------------------------------------------
        // 2. BANGLADESHI LIVE TV CHANNELS IN CIRCLES
        // (ইমেজ স্লাইডার এর নিচে এবং সকল মুভির ক্যাটাগরি উপরে)
        // ----------------------------------------------------
        item {
            Spacer(modifier = Modifier.height(14.dp))
            SectionHeader(
                title = LanguageManager.get("bangla_tv"),
                subtitle = "বাংলাদেশী লাইভ টিভি চ্যানেল (সার্কেল লিস্ট)",
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
                        it.name.contains("Boishakhi", ignoreCase = true) ||
                        it.name.contains("Deepto", ignoreCase = true) ||
                        it.name.contains("Gtv", ignoreCase = true) ||
                        it.name.contains("Maasranga", ignoreCase = true) ||
                        it.name.contains("T Sports", ignoreCase = true) ||
                        it.name.contains("BTV", ignoreCase = true)
                    }
                    if (banglaList.isNotEmpty()) banglaList.take(24) else liveChannels.take(24)
                } else emptyList()
            }

            if (displayChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
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
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ----------------------------------------------------
        // LIVE WEATHER SUMMARY BANNER (আবহাওয়া পূর্বাভাস কার্ড)
        // ----------------------------------------------------
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB020).copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { onNavigateToWeather() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF1E3C72).copy(alpha = 0.45f),
                                    Color(0xFF2A5298).copy(alpha = 0.2f),
                                    CinemaSurface
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFB020).copy(alpha = 0.2f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB020),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "আজকের আবহাওয়া বার্তা",
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = Color(0xFF10B981),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "তাপমাত্রা, ঘণ্টাওয়ারি ও ৭ দিনের পূর্বাভাস দেখুন",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onNavigateToWeather,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandRed.copy(alpha = 0.2f),
                            contentColor = BrandRedLight
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("দেখুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(10.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ----------------------------------------------------
        // BANGLA OTT PLATFORMS BANNER (চরকি, হইচই, বঙ্গ, নেটফ্লিক্স)
        // ----------------------------------------------------
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { onNavigateToBanglaOtt() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF8B0000).copy(alpha = 0.45f),
                                    Color(0xFFE50914).copy(alpha = 0.2f),
                                    CinemaSurface
                                )
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandRed.copy(alpha = 0.2f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Subscriptions,
                                    contentDescription = null,
                                    tint = BrandRedLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "বাংলা ওটিটি প্ল্যাটফর্ম",
                                    color = TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BrandRed
                                ) {
                                    Text(
                                        text = "NEW",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Chorki, Hoichoi, Bongo, Toffee ও নেটফ্লিক্স মুভিজ",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = onNavigateToBanglaOtt,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BrandRed,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("দেখুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(10.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        // ----------------------------------------------------
        if (bongoVideos.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFE50914),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "BONGO",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "বঙ্গ এক্সক্লুসিভ ও নাটক (Bongo BD)",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "হিট নাটক, ওরিজিনালস ও বাংলা ওয়েব সিরিজ",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    TextButton(onClick = onNavigateToMovies) {
                        Text("সব দেখুন", color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(bongoVideos) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            onClick = { onSelectMovie(movie.id) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }

        // ----------------------------------------------------
        // 3. CATEGORY 1: 🔥 TRENDING & NEW RELEASES
        // ----------------------------------------------------
        if (trendingMovies.isNotEmpty()) {
            item {
                SectionHeader(
                    title = LanguageManager.get("trending"),
                    subtitle = "CtgHall এক্সক্লুসিভ কালেকশন",
                    onSeeAllClick = onNavigateToMovies
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(trendingMovies) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            onClick = { onSelectMovie(movie.id) }
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 🎵 MUKUL MUSIC PROMO BANNER (হিন্দি ৯০s, টপ হিট্স ও গান)
        // ----------------------------------------------------
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onNavigateToMusic() },
                shape = RoundedCornerShape(20.dp),
                color = CinemaSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    BrandRed.copy(alpha = 0.35f),
                                    CinemaSurface
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandRed,
                            modifier = Modifier.size(50.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "মুকুল মিউজিক হাব (Music & Hits)",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "হিন্দি ৯০s, টপ হিট্স, বিন্দু স্পেশাল ও বাংলা গান শুনুন ও ডাউনলোড করুন",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 4. CATEGORY 2: 🎬 HOLLYWOOD BLOCKBUSTERS
        // ----------------------------------------------------
        if (hollywoodMovies.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = LanguageManager.get("hollywood"),
                    subtitle = "হলিউড ড্রামা, সাই-ফাই ও থ্রিলার",
                    onSeeAllClick = onNavigateToMovies
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(hollywoodMovies) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            onClick = { onSelectMovie(movie.id) }
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 5. CATEGORY 3: 🌟 BOLLYWOOD SUPERHITS
        // ----------------------------------------------------
        if (bollywoodMovies.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = LanguageManager.get("bollywood"),
                    subtitle = "বলিউডের সেরা সিনেমা ও মিউজিক্যাল হিটস",
                    onSeeAllClick = onNavigateToMovies
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(bollywoodMovies) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            onClick = { onSelectMovie(movie.id) }
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 6. CATEGORY 4: 🇧🇩 BANGLA CINEMA & DRAMAS
        // ----------------------------------------------------
        if (banglaMovies.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = LanguageManager.get("bangla_cinema"),
                    subtitle = "ঢালিউড বাংলা সুপারহিট মুভি ও সিরিজ",
                    onSeeAllClick = onNavigateToMovies
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(banglaMovies) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            onClick = { onSelectMovie(movie.id) }
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 7. CATEGORY 5: ⚡ SOUTH INDIAN ACTION (Hindi Dubbed)
        // ----------------------------------------------------
        if (southActionMovies.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = LanguageManager.get("south_action"),
                    subtitle = "সাউথ ইন্ডিয়ান ধামাকাদার অ্যাকশন",
                    onSeeAllClick = onNavigateToMovies
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(southActionMovies) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            onClick = { onSelectMovie(movie.id) }
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 8. CATEGORY 6: 🏆 TOP RATED IMDb HITS
        // ----------------------------------------------------
        if (topRatedMovies.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = LanguageManager.get("top_rated"),
                    subtitle = "আইএমডিবি ৮+ রেটেড মাস্টারপিস কালেকশন",
                    onSeeAllClick = onNavigateToMovies
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(topRatedMovies) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            onClick = { onSelectMovie(movie.id) }
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 9. CATEGORY 7: 🎨 ANIMATION & KIDS
        // ----------------------------------------------------
        if (animationMovies.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = LanguageManager.get("animation"),
                    subtitle = "ডিজনি, পিক্সার ও অ্যানিমেশন অ্যাডভেঞ্চার",
                    onSeeAllClick = onNavigateToMovies
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(animationMovies) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            onClick = { onSelectMovie(movie.id) }
                        )
                    }
                }
            }
        }

        // ----------------------------------------------------
        // 10. CATEGORY 8: 📥 FAST EXTRACTOR DOWNLOADS
        // ----------------------------------------------------
        if (providerPosts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = LanguageManager.get("fast_downloads"),
                    subtitle = "Mukul Movies • সরাসরি ডাউনলোড ও ব্রাউজিং",
                    onSeeAllClick = onNavigateToExtractor
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(providerPosts.take(10)) { post ->
                        Card(
                            modifier = Modifier
                                .width(135.dp)
                                .clickable { onSelectPost(post) },
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(175.dp)
                                        .background(CinemaSurfaceVariant)
                                ) {
                                    if (!post.image.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(post.image)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = post.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Surface(
                                        color = BrandRed,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = post.provider.uppercase(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
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
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ==========================================
// CIRCULAR TV CHANNEL COMPONENT
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
            .width(76.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(68.dp),
            contentAlignment = Alignment.Center
        ) {
            // Glowing Gradient Border Ring (Auth-Style Orange Fire Gradient)
            Box(
                modifier = Modifier
                    .size(68.dp)
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

            // Inner Circular Image
            Box(
                modifier = Modifier
                    .size(62.dp)
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
                            .size(50.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = AuthBrandPrimary,
                        modifier = Modifier.size(28.dp)
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

        Spacer(modifier = Modifier.height(6.dp))

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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elegant Auth-Style Vertical Accent Pill
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(AuthBrandGradientStart, AuthBrandGradientEnd)
                        )
                    )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        TextButton(
            onClick = onSeeAllClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = "সব দেখুন >",
                color = AuthBrandPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
