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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
    onNavigateToSports: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val cachedFeed by mediaRepository.cachedHomeFeed.collectAsState()

    // Category movie lists
    var trendingMovies by remember { mutableStateOf(cachedFeed?.trendingMovies ?: emptyList()) }
    var bongoVideos by remember { mutableStateOf(cachedFeed?.bongoVideos ?: emptyList()) }
    var bachelorPointShow by remember { mutableStateOf<BongoShow?>(null) }
    var salahuddinShow by remember { mutableStateOf<BongoShow?>(null) }
    var userNotFoundShow by remember { mutableStateOf<BongoShow?>(null) }
    var sheWasPrettyShow by remember { mutableStateOf<BongoShow?>(null) }
    var kimiWaPetShow by remember { mutableStateOf<BongoShow?>(null) }
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

            // Fetch the 5 featured Bongo series requested by user
            launch { bachelorPointShow = ApiClient.fetchBongoShowEpisodes("zvcly4FdFv0") }
            launch { salahuddinShow = ApiClient.fetchBongoShowEpisodes("dSH3So8VrJG") }
            launch { userNotFoundShow = ApiClient.fetchBongoShowEpisodes("3ScklzcngJy") }
            launch { sheWasPrettyShow = ApiClient.fetchBongoShowEpisodes("vdc0v0XXsTi") }
            launch { kimiWaPetShow = ApiClient.fetchBongoShowEpisodes("3WTufg8lxDK") }

            // 4. Hollywood
            val hollywoodRes = ApiClient.fetchCtgMovies(library = 7, page = 1, sort = "createdAt")
            hollywoodMovies = hollywoodRes.data

            // 4. Bollywood
            val bollywoodRes = ApiClient.fetchCtgMovies(library = 3, page = 1, sort = "createdAt")
            bollywoodMovies = bollywoodRes.data

            // 5. Bangla
            val banglaRes = ApiClient.fetchCtgMovies(library = 5, page = 1, sort = "createdAt")
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
        // QUICK SHORTCUTS ROW: WEATHER, MUSIC, BANGLA OTT IN A SINGLE ROW
        // (একই লাইনে একজায়গায় আবহাওয়া, মিউজিক ও বাংলা ওটিটি ছোট বাটন)
        // ----------------------------------------------------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Weather Button (আবহাওয়া)
                QuickShortcutButton(
                    title = "আবহাওয়া",
                    subtitle = "লাইভ আপডেট",
                    icon = Icons.Default.WbSunny,
                    accentColor = Color(0xFFFFB020),
                    bgGradient = listOf(Color(0xFF1E3C72), Color(0xFF2A5298)),
                    badge = "LIVE",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToWeather
                )

                // 2. Music Button (মিউজিক)
                QuickShortcutButton(
                    title = "মিউজিক",
                    subtitle = "গান ও অডিও",
                    icon = Icons.Default.MusicNote,
                    accentColor = CyanAccent,
                    bgGradient = listOf(Color(0xFF0F3443), Color(0xFF134E5E)),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToMusic
                )

                // 3. Bangla OTT Button (বাংলা ওটিটি)
                QuickShortcutButton(
                    title = "বাংলা ওটিটি",
                    subtitle = "মুভি ও সিরিজ",
                    icon = Icons.Default.Subscriptions,
                    accentColor = BrandRedLight,
                    bgGradient = listOf(Color(0xFF4A0E17), Color(0xFF7B0000)),
                    badge = "NEW",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToBanglaOtt
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
        // ----------------------------------------------------
        // BONGO BD SHOWS & EPISODES (হরিজনটাল আকারে হোমপেজে সাজানো)
        // ----------------------------------------------------
        // 1. Featured 5 Mega Bongo Series Showcase
        item {
            val featuredShows = remember(bachelorPointShow, salahuddinShow, userNotFoundShow, sheWasPrettyShow, kimiWaPetShow) {
                listOf(
                    Triple("Bachelor Point (ব্যাচেলর পয়েন্ট)", "https://cdn.bongo-solutions.com/abfea462-f64d-491e-9cd9-75ee001f45b0/content/0578d42d-453f-4592-b594-9477fbccfc01/e764252c-5fcb-4783-b82a-4c55533e3d62.jpg", "zvcly4FdFv0"),
                    Triple("সুলতান সালাহউদ্দিন আইয়ুবি", "https://cdn.bongo-solutions.com/abfea462-f64d-491e-9cd9-75ee001f45b0/content/2487fdfb-520a-42fd-948c-da439af69189/8d2b89e2-ab47-45b4-8881-97c7688bb6a3.jpg", "dSH3So8VrJG"),
                    Triple("User Not Found (ইউজার নট ফাউন্ড)", "https://cdn.bongo-solutions.com/abfea462-f64d-491e-9cd9-75ee001f45b0/content/83e994ed-2d52-461a-b4b0-2d14cbb5cd15/1921fdd1-be41-4921-8cfb-585b26237d96.jpg", "3ScklzcngJy"),
                    Triple("She Was Pretty (শি ওয়াজ প্রিটি)", "https://cdn.bongo-solutions.com/abfea462-f64d-491e-9cd9-75ee001f45b0/content/dd517ab1-3e89-4f87-8794-68ade57d82ef/a8ec7bb7-11b9-4be6-bc5f-4a6f6538a79b.jpg", "vdc0v0XXsTi"),
                    Triple("Kimi Wa Pet (আদরের বয়ফ্রেন্ড)", "https://cdn.bongo-solutions.com/abfea462-f64d-491e-9cd9-75ee001f45b0/content/c8d2e909-e756-427b-8889-a0bcd1fde376/d274a572-0996-40ce-849a-d9e5cbb92ad9.jpg", "3WTufg8lxDK")
                )
            }

            Column(modifier = Modifier.padding(top = 8.dp)) {
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
                                text = "BONGO BD",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "মেগা সিরিজ ও নাটক (Top Web Series)",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(featuredShows) { (title, posterUrl, sysId) ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            modifier = Modifier
                                .width(180.dp)
                                .clickable {
                                    // Open show directly with fake ID mapped in ApiClient
                                    val mappedMovie = bongoVideos.find { it.file_path == sysId }
                                    if (mappedMovie != null) {
                                        onSelectMovie(mappedMovie.id)
                                    } else {
                                        // Pick first bongo video or -90001L
                                        onSelectMovie(-90001L)
                                    }
                                }
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(105.dp)
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                    ) {
                                        Text(
                                            text = "HD",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = title,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "সব পর্ব দেখুন",
                                        color = CyanAccent,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 2. Bachelor Point Episodes Horizontal Rail
        if (bachelorPointShow != null && bachelorPointShow!!.items.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ব্যাচেলর পয়েন্ট - পর্বসমূহ (Bachelor Point)",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${bachelorPointShow!!.items.size} Episodes",
                            color = BrandRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(bachelorPointShow!!.items.take(20)) { ep ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                modifier = Modifier
                                    .width(160.dp)
                                    .clickable {
                                        // Open and stream this episode directly
                                        onSelectMovie(-90001L)
                                    }
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(Color.Black)
                                    ) {
                                        if (!ep.thumbnail.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = ep.thumbnail,
                                                contentDescription = ep.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            shape = CircleShape,
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = Color.White,
                                                modifier = Modifier.padding(6.dp).size(20.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            text = ep.title,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("এখন চালান", color = TextMuted, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        // 3. Sultan Salahuddin Ayyubi Episodes Horizontal Rail
        if (salahuddinShow != null && salahuddinShow!!.items.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldRating, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "সুলতান সালাহউদ্দিন আইয়ুবি - সব পর্ব",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${salahuddinShow!!.items.size} Episodes",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(salahuddinShow!!.items.take(20)) { ep ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                modifier = Modifier
                                    .width(160.dp)
                                    .clickable {
                                        onSelectMovie(-90001L)
                                    }
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(Color.Black)
                                    ) {
                                        if (!ep.thumbnail.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = ep.thumbnail,
                                                contentDescription = ep.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            shape = CircleShape,
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.padding(6.dp).size(20.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            text = ep.title,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("এইচডি পর্ব", color = CyanAccent, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        // 3.1 User Not Found Episodes Rail
        if (userNotFoundShow != null && userNotFoundShow!!.items.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ইউজার নট ফাউন্ড - পর্বসমূহ (User Not Found)",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${userNotFoundShow!!.items.size} Episodes",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(userNotFoundShow!!.items.take(20)) { ep ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                modifier = Modifier
                                    .width(160.dp)
                                    .clickable { onSelectMovie(-90001L) }
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(Color.Black)
                                    ) {
                                        if (!ep.thumbnail.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = ep.thumbnail,
                                                contentDescription = ep.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            shape = CircleShape,
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.padding(6.dp).size(20.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            text = ep.title,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("এইচডি পর্ব", color = CyanAccent, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        // 3.2 She Was Pretty Episodes Rail
        if (sheWasPrettyShow != null && sheWasPrettyShow!!.items.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFFF4081), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "শি ওয়াজ প্রিটি - পর্বসমূহ (She Was Pretty)",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${sheWasPrettyShow!!.items.size} Episodes",
                            color = Color(0xFFFF4081),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sheWasPrettyShow!!.items.take(20)) { ep ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                modifier = Modifier
                                    .width(160.dp)
                                    .clickable { onSelectMovie(-90001L) }
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(Color.Black)
                                    ) {
                                        if (!ep.thumbnail.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = ep.thumbnail,
                                                contentDescription = ep.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            shape = CircleShape,
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.padding(6.dp).size(20.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            text = ep.title,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("এইচডি পর্ব", color = Color(0xFFFF4081), fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        // 3.3 Kimi Wa Pet Episodes Rail
        if (kimiWaPetShow != null && kimiWaPetShow!!.items.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = GoldRating, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "আদরের বয়ফ্রেন্ড - পর্বসমূহ (Kimi Wa Pet)",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${kimiWaPetShow!!.items.size} Episodes",
                            color = GoldRating,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(kimiWaPetShow!!.items.take(20)) { ep ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                modifier = Modifier
                                    .width(160.dp)
                                    .clickable { onSelectMovie(-90001L) }
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(90.dp)
                                            .background(Color.Black)
                                    ) {
                                        if (!ep.thumbnail.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = ep.thumbnail,
                                                contentDescription = ep.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            shape = CircleShape,
                                            modifier = Modifier.align(Alignment.Center)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.padding(6.dp).size(20.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            text = ep.title,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text("এইচডি পর্ব", color = GoldRating, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }
            }
        }

        // 4. Bongo BD Scrape Full Video Catalog
        if (bongoVideos.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "বঙ্গ অরিজিনাল ক্যাটালগ (Bongo BD Originals)",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onNavigateToMovies) {
                        Text("সব দেখুন", color = BrandRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
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
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 5. LIVE SPORTS & CRICKET QUICK BANNER
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onNavigateToSports() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFF00E676).copy(alpha = 0.2f),
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
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF00E676), CircleShape)
                            )
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
                            text = "T Sports, Star Sports 1 HD ও লাইভ স্কোর দেখুন",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = onNavigateToSports,
                        colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("দেখুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ----------------------------------------------------
        // 3. CATEGORY 1: 🔥 TRENDING & NEW RELEASES
        // ----------------------------------------------------
        if (trendingMovies.isNotEmpty()) {
            item {
                SectionHeader(
                    title = LanguageManager.get("trending"),
                    subtitle = "প্রিমিয়াম ট্রেন্ডিং ও লেটেস্ট কালেকশন",
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

@Composable
private fun QuickShortcutButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    bgGradient: List<Color>,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(bgGradient.map { it.copy(alpha = 0.55f) }))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = BrandRed,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = badge,
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

