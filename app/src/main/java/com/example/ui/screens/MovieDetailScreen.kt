package com.example.ui.screens

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.ApiClient
import com.example.data.model.*
import com.example.data.repository.MediaRepository
import com.example.data.util.DownloadUtils
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailScreen(
    movieId: Long?,
    extractorLink: String?,
    extractorProvider: String?,
    initialTitle: String?,
    initialPoster: String?,
    mediaRepository: MediaRepository,
    onBackClick: () -> Unit,
    onSelectMovie: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var ctgMovie by remember { mutableStateOf<CtgMovie?>(null) }
    var extractorInfo by remember { mutableStateOf<ExtractorMovieInfo?>(null) }
    var trailers by remember { mutableStateOf<List<TmdbVideo>>(emptyList()) }
    var recommendations by remember { mutableStateOf<List<TmdbSearchResult>>(emptyList()) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isPlayingInApp by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    // Download & Stream Resolution State
    var isResolvingDownload by remember { mutableStateOf(false) }
    var resolvingMessage by remember { mutableStateOf("") }
    var resolvedServers by remember { mutableStateOf<List<DownloadLink>>(emptyList()) }
    var showServersDialog by remember { mutableStateOf(false) }
    var episodesList by remember { mutableStateOf<List<DownloadLink>>(emptyList()) }
    var showEpisodesDialog by remember { mutableStateOf(false) }
    var targetDownloadFileName by remember { mutableStateOf("") }

    val favorites by mediaRepository.favorites.collectAsState()
    val isFav = movieId != null && favorites.contains(movieId)

    // Fetch movie details from CtgHall, Extractor, or TMDB
    LaunchedEffect(movieId, extractorLink) {
        isLoading = true
        try {
            // 1. CtgHall or Bongo Movie Detail if ID is provided
            if (movieId != null) {
                val movie = if (movieId > 0) {
                    ApiClient.fetchCtgMovieDetail(movieId)
                } else {
                    mediaRepository.getMovieById(movieId) ?: ApiClient.getBongoMovieById(movieId)
                }
                ctgMovie = movie
                val streamUrl = movie?.getFullStreamUrl()
                if (!streamUrl.isNullOrEmpty()) {
                    if (streamUrl.contains("bongo/hls") || (streamUrl.contains("hamyra-api") && streamUrl.contains("id="))) {
                        val resolved = ApiClient.resolveBongoStreamUrl(streamUrl)
                        activeStreamUrl = resolved
                    } else {
                        activeStreamUrl = streamUrl
                    }
                }

                // If tmdb_id exists, fetch trailers & recommendations
                val tmdbIdLong = movie?.tmdb_id?.toLongOrNull()
                if (tmdbIdLong != null) {
                    trailers = ApiClient.fetchTmdbVideos(tmdbIdLong)
                    recommendations = ApiClient.fetchTmdbRecommendations(tmdbIdLong)
                }

                // Auto-fetch Extractor downloads for this movie title from MoviesMod
                if (movie != null && extractorLink.isNullOrEmpty()) {
                    try {
                        val cleanTitle = movie.title.replace(Regex("[^A-Za-z0-9 ]"), " ").trim()
                        val keywords = cleanTitle.split(" ").filter { it.length > 2 }.take(2).joinToString(" ")
                        if (keywords.isNotEmpty()) {
                            val searchResults = ApiClient.fetchExtractorPosts("moviesmod", filter = keywords, page = 1)
                            if (searchResults.isNotEmpty()) {
                                val bestMatch = searchResults.first()
                                val info = ApiClient.fetchExtractorInfo(bestMatch.link, bestMatch.provider)
                                if (info != null) {
                                    extractorInfo = info
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // 2. Extractor info if link is provided
            if (!extractorLink.isNullOrEmpty() && !extractorProvider.isNullOrEmpty()) {
                val info = ApiClient.fetchExtractorInfo(extractorLink, extractorProvider)
                extractorInfo = info
                if (activeStreamUrl == null && !info?.streamLinks.isNullOrEmpty()) {
                    activeStreamUrl = info?.streamLinks?.firstOrNull()?.link
                }
            }
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    val displayTitle = ctgMovie?.title ?: extractorInfo?.title ?: initialTitle ?: "Movie Details"
    val displayPoster = ctgMovie?.getFullPosterUrl() ?: extractorInfo?.image ?: initialPoster ?: ""
    val displayBackdrop = ctgMovie?.getFullBackdropUrl() ?: displayPoster
    val displayOverview = ctgMovie?.overview ?: extractorInfo?.synopsis ?: "No storyline available."

    Scaffold(
        containerColor = CinemaBackground,
        topBar = {
            if (!isFullScreen) {
                TopAppBar(
                    title = {
                        Text(
                            text = displayTitle,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        if (movieId != null) {
                            IconButton(onClick = { mediaRepository.toggleFavorite(movieId) }) {
                                Icon(
                                    imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFav) BrandRed else TextPrimary
                                )
                            }
                        }
                        IconButton(onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, "Watch $displayTitle on Mukul Plus OTT: ${activeStreamUrl ?: "https://www.ctghall.com/api/movies/$movieId"}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Movie"))
                        }) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CinemaSurface)
                )
            }
        }
    ) { innerPadding ->
        if (isFullScreen && isPlayingInApp && !activeStreamUrl.isNullOrEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoPlayerView(
                    videoUrl = activeStreamUrl!!,
                    title = displayTitle,
                    modifier = Modifier.fillMaxSize(),
                    onFullScreenToggle = { isFullScreen = !isFullScreen },
                    isFullScreen = isFullScreen
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 1. Top Media Player or Hero Poster (Taller player height)
                item {
                    if (isPlayingInApp && !activeStreamUrl.isNullOrEmpty()) {
                        VideoPlayerView(
                            videoUrl = activeStreamUrl!!,
                            title = displayTitle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            onFullScreenToggle = { isFullScreen = !isFullScreen },
                            isFullScreen = isFullScreen
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(Color.Black)
                        ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(displayBackdrop)
                                .crossfade(true)
                                .build(),
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color(0x77000000),
                                            CinemaBackground
                                        )
                                    )
                                )
                        )

                        // Play Button
                        Surface(
                            onClick = {
                                if (!activeStreamUrl.isNullOrEmpty()) {
                                    isPlayingInApp = true
                                } else if (!displayTitle.isNullOrEmpty()) {
                                    Toast.makeText(context, "Finding stream source...", Toast.LENGTH_SHORT).show()
                                    isPlayingInApp = true
                                }
                            },
                            shape = CircleShape,
                            color = BrandRed,
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Movie",
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        // Stream tag
                        Surface(
                            color = BrandRed,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "HD 1080P • HIGH QUALITY STREAM",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 2. Movie Title & Meta Information
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = displayTitle,
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val altTitle = ctgMovie?.original_title
                    if (!altTitle.isNullOrEmpty() && altTitle != displayTitle) {
                        Text(
                            text = altTitle,
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Badges row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val rating = ctgMovie?.online_rating ?: ctgMovie?.user_rating
                        if (rating != null && rating > 0.0) {
                            Surface(
                                color = CinemaSurfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = GoldRating,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = String.format("%.1f", rating),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        ctgMovie?.year?.let { y ->
                            Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = y.toString(),
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val genreStr = ctgMovie?.genre ?: ctgMovie?.Library?.name
                        if (!genreStr.isNullOrEmpty()) {
                            Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = genreStr,
                                    color = CyanAccent,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Action Buttons (Stream / Download / Browser)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                isPlayingInApp = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("এখন চালান (Play)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val downloadUrl = activeStreamUrl ?: ctgMovie?.getFullStreamUrl()
                                if (!downloadUrl.isNullOrEmpty()) {
                                    startDownload(context, downloadUrl, displayTitle)
                                } else {
                                    Toast.makeText(context, "ডাউনলোড লিংক নিচে সিলেক্ট করুন", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ডাউনলোড (Save)", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Overview Storyline
                    Text(
                        text = "কাহিনী সংক্ষেপ (Storyline)",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = displayOverview,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Casts if available
                    if (!ctgMovie?.casts.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "অভিনয়ে (Cast): ${ctgMovie?.casts}",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 3. Provider Download & Streaming Links Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = CyanAccent)
                        Text(
                            text = "প্রোভাইডার ডাউনলোড ও স্ট্রিম লিংক",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "নিচের যেকোনো রেজোলিউশনে ক্লিক করে সরাসরি চালান অথবা ডাউনলোড করুন",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    val allDownloads = extractorInfo?.downloadLinks ?: emptyList()
                    val ctgStream = ctgMovie?.getFullStreamUrl()

                    if (allDownloads.isEmpty() && ctgStream == null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = GoldRating)
                                Text(
                                    text = "ডাউনলোড সার্ভার থেকে লিংক ফেচ করা হচ্ছে...",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        // If CtgHall URL exists
                        if (ctgStream != null) {
                            DownloadLinkCard(
                                title = "${ctgMovie?.title ?: "Movie"} (Original HD Web-DL)",
                                quality = "1080p Full HD",
                                link = ctgStream,
                                onPlay = {
                                    activeStreamUrl = ctgStream
                                    isPlayingInApp = true
                                },
                                onDownload = {
                                    startDownload(context, ctgStream, "${ctgMovie?.title}.mp4")
                                },
                                onCopy = {
                                    copyToClipboard(context, ctgStream)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Extractor direct & episode links
                        allDownloads.forEach { dl ->
                            DownloadLinkCard(
                                title = dl.title,
                                quality = dl.quality ?: "HD",
                                link = dl.link,
                                onPlay = {
                                    if (dl.link.contains("archives") || dl.link.contains("episodes")) {
                                        coroutineScope.launch {
                                            isResolvingDownload = true
                                            resolvingMessage = "পর্বের তালিকা সংগ্রহ করা হচ্ছে..."
                                            episodesList = ApiClient.fetchExtractorEpisodes(dl.link)
                                            isResolvingDownload = false
                                            showEpisodesDialog = true
                                        }
                                    } else if (dl.link.contains("sid=") || dl.link.contains("unblockedgames") || dl.link.contains("cloud.")) {
                                        coroutineScope.launch {
                                            isResolvingDownload = true
                                            resolvingMessage = "হাই-স্পিড সার্ভার কানেক্ট করা হচ্ছে..."
                                            val servers = ApiClient.fetchExtractorStream(dl.link)
                                            isResolvingDownload = false
                                            if (servers.isNotEmpty()) {
                                                resolvedServers = servers
                                                showServersDialog = true
                                            } else {
                                                activeStreamUrl = dl.link
                                                isPlayingInApp = true
                                            }
                                        }
                                    } else {
                                        activeStreamUrl = dl.link
                                        isPlayingInApp = true
                                    }
                                },
                                onDownload = {
                                    if (dl.link.contains("archives") || dl.link.contains("episodes")) {
                                        coroutineScope.launch {
                                            isResolvingDownload = true
                                            resolvingMessage = "পর্বের তালিকা সংগ্রহ করা হচ্ছে..."
                                            episodesList = ApiClient.fetchExtractorEpisodes(dl.link)
                                            isResolvingDownload = false
                                            showEpisodesDialog = true
                                        }
                                    } else if (dl.link.contains("sid=") || dl.link.contains("unblockedgames") || dl.link.contains("cloud.")) {
                                        coroutineScope.launch {
                                            isResolvingDownload = true
                                            resolvingMessage = "ডাউনলোড সার্ভার তৈরি করা হচ্ছে..."
                                            val servers = ApiClient.fetchExtractorStream(dl.link)
                                            isResolvingDownload = false
                                            if (servers.isNotEmpty()) {
                                                resolvedServers = servers
                                                targetDownloadFileName = "${displayTitle}_${dl.quality}.mkv"
                                                showServersDialog = true
                                            } else {
                                                startDownload(context, dl.link, "${displayTitle}_${dl.quality}.mp4")
                                            }
                                        }
                                    } else {
                                        startDownload(context, dl.link, "${displayTitle}_${dl.quality}.mp4")
                                    }
                                },
                                onCopy = {
                                    copyToClipboard(context, dl.link)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            // 4. TMDB Trailers
            if (trailers.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "অফিসিয়াল ট্রেলার ও ভিডিও (Trailers)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(trailers) { trailer ->
                                Surface(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailer.youtubeUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    color = CinemaSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.width(180.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(90.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = BrandRed,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = trailer.name,
                                            color = Color.White,
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

            // 5. TMDB Recommendations / More Like This
            if (recommendations.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "আরো পছন্দ হতে পারে (More Like This)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recommendations) { rec ->
                                Card(
                                    modifier = Modifier
                                        .width(115.dp)
                                        .clickable { onSelectMovie(rec.id) },
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(rec.fullPosterUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = rec.displayTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp)
                                        )
                                        Text(
                                            text = rec.displayTitle,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
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
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

    // 1. Loading Resolution Spinner Dialog
    if (isResolvingDownload) {
        AlertDialog(
            onDismissRequest = { isResolvingDownload = false },
            containerColor = CinemaSurface,
            confirmButton = {},
            text = {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = BrandRed, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                    Text(
                        text = resolvingMessage.ifEmpty { "ডাউনলোড সার্ভার প্রসেস হচ্ছে..." },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }

    // 2. Episodes List Dialog
    if (showEpisodesDialog) {
        AlertDialog(
            onDismissRequest = { showEpisodesDialog = false },
            containerColor = CinemaSurface,
            title = {
                Text(
                    text = "পর্ব নির্বাচন করুন (${episodesList.size} Episodes)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(episodesList) { ep ->
                        Surface(
                            onClick = {
                                showEpisodesDialog = false
                                coroutineScope.launch {
                                    isResolvingDownload = true
                                    resolvingMessage = "${ep.title} সার্ভার খোঁজা হচ্ছে..."
                                    val servers = ApiClient.fetchExtractorStream(ep.link)
                                    isResolvingDownload = false
                                    if (servers.isNotEmpty()) {
                                        resolvedServers = servers
                                        targetDownloadFileName = "${displayTitle}_${ep.title}.mkv"
                                        showServersDialog = true
                                    } else {
                                        startDownload(context, ep.link, "${displayTitle}_${ep.title}.mp4")
                                    }
                                }
                            },
                            color = CinemaSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                                    Text(text = ep.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Icon(Icons.Default.Download, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEpisodesDialog = false }) {
                    Text("বন্ধ করুন", color = TextSecondary)
                }
            }
        )
    }

    // 3. Direct Fast Servers Dialog (CF Worker / Resume Worker / G-Drive)
    if (showServersDialog) {
        AlertDialog(
            onDismissRequest = { showServersDialog = false },
            containerColor = CinemaSurface,
            icon = {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    text = "সরাসরি ডাউনলোড সার্ভার",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "নিচের যেকোনো একটি আল্ট্রা-ফাস্ট সার্ভার নির্বাচন করুন:",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    resolvedServers.forEach { server ->
                        Surface(
                            color = CinemaSurfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = server.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            showServersDialog = false
                                            activeStreamUrl = server.link
                                            isPlayingInApp = true
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("প্লে করুন", color = CyanAccent, fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            showServersDialog = false
                                            startDownload(context, server.link, targetDownloadFileName.ifEmpty { "${displayTitle}.mkv" })
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ডাউনলোড", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServersDialog = false }) {
                    Text("বন্ধ করুন", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun DownloadLinkCard(
    title: String,
    quality: String,
    link: String,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = BrandRed,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = quality,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = CinemaSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("স্ট্রিম (Stream)", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ডাউনলোড (Save)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun startDownload(context: Context, url: String, fileName: String) {
    DownloadUtils.openDownloadInChrome(context, url)
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Download Link", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "লিংক ক্লিপবোর্ডে কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
}
