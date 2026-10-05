package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.data.download.InAppDownloader
import com.example.data.model.CtgMovie
import com.example.data.repository.MediaRepository
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BongoBrandRed = Color(0xFFE50914)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanglaOttScreen(
    mediaRepository: MediaRepository? = null,
    onBack: (() -> Unit)? = null,
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ------------------------------------------------------------------------
    // Bongo Videos State
    // ------------------------------------------------------------------------
    var allBongoVideos by remember { mutableStateOf<List<CtgMovie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    // Active Selected Bongo Movie for Second Page Video Player
    var activeBongoMovie by remember { mutableStateOf<CtgMovie?>(null) }

    // Load all Bongo videos from MediaRepository or ApiClient
    fun loadBongoCatalog(forceRefresh: Boolean = false) {
        isLoading = true
        coroutineScope.launch {
            try {
                val videos = if (mediaRepository != null) {
                    mediaRepository.getBongoVideos(forceRefresh)
                } else {
                    ApiClient.fetchBongoVideos()
                }
                allBongoVideos = videos
            } catch (e: Exception) {
                // Fallback to ApiClient
                try {
                    allBongoVideos = ApiClient.fetchBongoVideos()
                } catch (_: Exception) {}
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadBongoCatalog()
    }

    // Dynamic categories extracted from Bongo videos
    val categories = remember(allBongoVideos) {
        val set = mutableSetOf<String>()
        allBongoVideos.forEach { movie ->
            val g = movie.genre ?: ""
            when {
                g.contains("Drama", ignoreCase = true) || g.contains("নাটক", ignoreCase = true) -> set.add("Bangla Drama")
                g.contains("Series", ignoreCase = true) || g.contains("সিরিজ", ignoreCase = true) -> set.add("Web Series")
                g.contains("Movie", ignoreCase = true) || g.contains("সিনেমা", ignoreCase = true) -> set.add("Bangla Movie")
                g.contains("Comedy", ignoreCase = true) -> set.add("Comedy")
                g.contains("Thriller", ignoreCase = true) || g.contains("Crime", ignoreCase = true) -> set.add("Thriller")
                g.contains("Romance", ignoreCase = true) -> set.add("Romance")
                g.contains("Action", ignoreCase = true) -> set.add("Action")
            }
        }
        listOf("সব (All)") + set.toList().sorted()
    }

    // Filtered Bongo Videos
    val filteredVideos = remember(allBongoVideos, searchQuery, selectedCategory) {
        var list = allBongoVideos

        if (!selectedCategory.isNullOrBlank() && selectedCategory != "সব (All)") {
            list = list.filter { movie ->
                movie.genre?.contains(selectedCategory!!, ignoreCase = true) == true ||
                movie.original_title?.contains(selectedCategory!!, ignoreCase = true) == true
            }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter { movie ->
                movie.title.contains(q, ignoreCase = true) ||
                (movie.casts?.contains(q, ignoreCase = true) == true) ||
                (movie.genre?.contains(q, ignoreCase = true) == true) ||
                (movie.overview?.contains(q, ignoreCase = true) == true)
            }
        }

        list
    }

    // ========================================================================
    // SECOND PAGE: OTT VIDEO PLAYER & DETAIL SCREEN
    // (হুবহু ওটিটি পেজের মতো ভিডিও প্লেয়ার ও বিশদ বিবরণ)
    // ========================================================================
    if (activeBongoMovie != null) {
        BongoOttDetailPlayerScreen(
            movie = activeBongoMovie!!,
            allBongoVideos = allBongoVideos,
            onBack = { activeBongoMovie = null },
            onNavigateToDownloads = onNavigateToDownloads,
            onSelectRelatedMovie = { nextMovie -> activeBongoMovie = nextMovie },
            modifier = modifier
        )
        return
    }

    // ========================================================================
    // FIRST PAGE: BONGO OTT CATALOG SCREEN
    // ========================================================================
    BackHandler(enabled = searchQuery.isNotEmpty() || onBack != null) {
        if (searchQuery.isNotEmpty()) {
            searchQuery = ""
        } else if (onBack != null) {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = TextPrimary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = BongoBrandRed.copy(alpha = 0.2f),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Subscriptions,
                                        contentDescription = null,
                                        tint = BongoBrandRed,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "বাংলা ওটিটি",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = BongoBrandRed
                                    ) {
                                        Text(
                                            text = "BONGO BD",
                                            color = Color.White,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (filteredVideos.isNotEmpty()) "${filteredVideos.size} টি মুভি, নাটক ও ওয়েব সিরিজ" else "বংগো বিডি এক্সক্লুসিভ বাংলা কনটেন্ট",
                                    color = TextMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        // Downloads button shortcut
                        IconButton(onClick = onNavigateToDownloads) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Downloads",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Refresh button
                        IconButton(onClick = { loadBongoCatalog(forceRefresh = true) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Search Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("বংগো নাটক, মুভি বা অভিনেতার নাম দিয়ে খুঁজুন...", color = TextMuted, fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = BongoBrandRed, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BongoBrandRed,
                                unfocusedBorderColor = CinemaBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ================================================================
            // CATEGORY FILTER CHIPS ROW
            // ================================================================
            Surface(
                color = CinemaSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = (selectedCategory == null && cat == "সব (All)") || selectedCategory == cat
                        Surface(
                            onClick = {
                                selectedCategory = if (cat == "সব (All)") null else cat
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) BongoBrandRed else CinemaSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BongoBrandRed else CinemaBorder
                            )
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // ================================================================
            // BONGO VIDEOS GRID (মুভি ও নাটকের গ্রিড)
            // ================================================================
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BongoBrandRed, modifier = Modifier.size(38.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("বংগো বিডি ভিডিও লোড হচ্ছে...", color = TextSecondary, fontSize = 12.5.sp)
                    }
                }
            } else if (filteredVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(Icons.Default.MovieFilter, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("কোনো ভিডিও পাওয়া যায়নি", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("সার্চ পরিবর্তন করে চেষ্টা করুন", color = TextMuted, fontSize = 11.5.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                searchQuery = ""
                                selectedCategory = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BongoBrandRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ফিল্টার রিসেট করুন")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    items(filteredVideos, key = { it.id }) { movie ->
                        BongoMovieCard(
                            movie = movie,
                            onPlayClick = { activeBongoMovie = movie },
                            onDownloadClick = {
                                val streamUrl = movie.getFullStreamUrl() ?: ""
                                if (streamUrl.isNotBlank()) {
                                    InAppDownloader.startDownload(
                                        context = context,
                                        movieSlug = "bongo_${movie.id}",
                                        title = movie.title,
                                        poster = movie.getFullPosterUrl(),
                                        quality = "Full HD",
                                        downloadUrl = streamUrl
                                    )
                                    Toast.makeText(context, "${movie.title} ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "ডাউনলোড লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Second Page: OTT Video Player & Movie Detail Screen for Bongo BD
 * (হুবহু ওটিটি পেজের মতো ভিডিও প্লেয়ার ও বিশদ বিবরণ)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BongoOttDetailPlayerScreen(
    movie: CtgMovie,
    allBongoVideos: List<CtgMovie>,
    onBack: () -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onSelectRelatedMovie: (CtgMovie) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isStorylineExpanded by remember { mutableStateOf(false) }

    val streamUrl = remember(movie) { movie.getFullStreamUrl() ?: "" }

    // Related Bongo videos
    val relatedVideos = remember(movie, allBongoVideos) {
        allBongoVideos.filter { it.id != movie.id }.take(10)
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column {
                            Text(
                                text = movie.title,
                                color = TextPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = BongoBrandRed
                                ) {
                                    Text(
                                        text = "BONGO BD",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = movie.genre ?: "Bangla Drama",
                                    color = TextMuted,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Share Button
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, movie.title)
                                putExtra(Intent.EXTRA_TEXT, "${movie.title}\nবংগো বিডি এক্সক্লুসিভ বাংলা কন্টেন্ট দেখুন Mukul Plus এ!\n$streamUrl")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "শেয়ার করুন"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }

                    // Downloads Shortcut Button
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Downloads", tint = CyanAccent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // ============================================================
            // 1. HERO 16:9 VIDEO PLAYER (হুবহু ওটিটি প্লেয়ারের মতো)
            // ============================================================
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (streamUrl.isNotBlank()) {
                        VideoPlayerView(
                            videoUrl = streamUrl,
                            title = movie.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Fallback Poster View
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = movie.getFullBackdropUrl(),
                                contentDescription = movie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("ভিডিও লোড হচ্ছে...", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ============================================================
            // 2. STREAMING STATUS & QUALITY BAR
            // ============================================================
            item {
                Surface(
                    color = CinemaSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981),
                                modifier = Modifier.size(7.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Bongo BD Direct Stream • 1080p Full HD",
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = BongoBrandRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "HLS LIVE",
                                color = BongoBrandRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ============================================================
            // 3. ACTION BUTTONS: PLAY, DOWNLOAD, BROWSER, COPY LINK
            // ============================================================
            item {
                Surface(
                    color = CinemaSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Play / Replay Button
                        Button(
                            onClick = {
                                Toast.makeText(context, "ভিডিও প্লেয়ার চালু রয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BongoBrandRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("প্লে", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // 2. Download Button
                        Button(
                            onClick = {
                                if (streamUrl.isNotBlank()) {
                                    InAppDownloader.startDownload(
                                        context = context,
                                        movieSlug = "bongo_${movie.id}",
                                        title = movie.title,
                                        poster = movie.getFullPosterUrl(),
                                        quality = "Full HD",
                                        downloadUrl = streamUrl
                                    )
                                    Toast.makeText(context, "${movie.title} ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "ডাউনলোড লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3C72)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(36.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ডাউনলোড", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // 3. Open in Browser Button
                        FilledTonalButton(
                            onClick = {
                                if (streamUrl.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "ব্রাউজার খোলা যায়নি", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CinemaSurface,
                                contentColor = TextPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Browser", modifier = Modifier.size(16.dp))
                        }

                        // 4. Copy Link Button
                        FilledTonalButton(
                            onClick = {
                                if (streamUrl.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Bongo Link", streamUrl)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "ভিডিও লিংক কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CinemaSurface,
                                contentColor = CyanAccent
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ============================================================
            // 4. OTT METADATA, STORYLINE & CAST
            // ============================================================
            item {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = movie.title,
                        color = TextPrimary,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metadata Badges (Platform, Rating, Year, Genre)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(color = BongoBrandRed, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "Bongo BD",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }

                        val rating = movie.online_rating ?: movie.user_rating ?: 8.8
                        Surface(color = Color(0xFFFFB020).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(text = String.format("%.1f", rating), color = Color(0xFFFFB020), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (movie.year != null && movie.year > 0) {
                            Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "${movie.year}",
                                    color = CyanAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "Full HD • 1080p",
                                color = TextMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (!movie.genre.isNullOrBlank()) {
                            Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = movie.genre,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Synopsis / Storyline (কাহিনী সংক্ষেপ)
                    val overview = movie.overview.orEmpty().ifBlank {
                        "${movie.title} - বংগো বিডি এক্সক্লুসিভ বাংলা নাটক ও ওয়েব সিরিজ। এইচডি কোয়ালিটিতে উপভোগ করুন।"
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "কাহিনী সংক্ষেপ:", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = overview,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        maxLines = if (isStorylineExpanded) 100 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (overview.length > 100) {
                        TextButton(
                            onClick = { isStorylineExpanded = !isStorylineExpanded },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = if (isStorylineExpanded) "কম দেখুন" else "আরও পড়ুন...",
                                color = CyanAccent,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Casts
                    if (!movie.casts.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "অভিনয়ে:", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = movie.casts, color = TextMuted, fontSize = 11.5.sp)
                    }
                }
            }

            // ============================================================
            // 5. RELATED BONGO VIDEOS (বংগো বিডি এর আরও কন্টেন্ট)
            // ============================================================
            if (relatedVideos.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            text = "বংগো বিডি এর আরও নাটক ও সিনেমা",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(relatedVideos) { rMovie ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier
                                        .width(115.dp)
                                        .clickable { onSelectRelatedMovie(rMovie) }
                                ) {
                                    Column {
                                        AsyncImage(
                                            model = rMovie.getFullPosterUrl(),
                                            contentDescription = rMovie.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                        )
                                        Text(
                                            text = rMovie.title,
                                            color = TextPrimary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
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
        }
    }
}

/**
 * Bongo Movie Card with Poster, Bongo BD Branding, and Action Buttons
 */
@Composable
private fun BongoMovieCard(
    movie: CtgMovie,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlayClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Poster Box with Aspect Ratio 2:3
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(CinemaSurfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(movie.getFullPosterUrl())
                        .crossfade(true)
                        .build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-Left: Bongo BD Brand Badge
                Surface(
                    color = BongoBrandRed,
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "Bongo",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Top-Right: Quality Badge
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (movie.genre?.contains("Series", ignoreCase = true) == true) "SERIES" else "Full HD",
                        color = if (movie.genre?.contains("Series", ignoreCase = true) == true) CyanAccent else Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Center Play Icon Overlay
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Bottom Overlay: Rating & Year
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val rating = movie.online_rating ?: movie.user_rating ?: 8.8
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (movie.year != null && movie.year > 0) {
                        Text(
                            text = "${movie.year}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 8.5.sp
                        )
                    }
                }
            }

            // Title & Info
            Column(modifier = Modifier.padding(7.dp)) {
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Dual Action Buttons: Play & Download (ঠিক ওটিটি পেজের মতো)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play Button
                    FilledTonalButton(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CinemaSurfaceVariant,
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("প্লে", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Download Button
                    Button(
                        onClick = onDownloadClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BongoBrandRed),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(26.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("ডাউনলোড", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
