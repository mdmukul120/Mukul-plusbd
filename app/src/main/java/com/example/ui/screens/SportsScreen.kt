package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.SportsCategoryType
import com.example.data.model.SportsMatchItem
import com.example.data.repository.SportsRepository
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Active Category (null = Category Hub with 5 category buttons)
    var selectedCategory by remember { mutableStateOf<SportsCategoryType?>(null) }
    var matches by remember { mutableStateOf<List<SportsMatchItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Active Player state
    var activePlayingMatch by remember { mutableStateOf<SportsMatchItem?>(null) }
    var activeStreamUrl by remember { mutableStateOf("") }
    var isFullScreenPlayer by remember { mutableStateOf(false) }

    fun loadCategory(cat: SportsCategoryType, force: Boolean = false) {
        selectedCategory = cat
        coroutineScope.launch {
            isLoading = true
            val res = SportsRepository.getSportsCategory(cat, forceRefresh = force)
            matches = res
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategory) {
        selectedCategory?.let { loadCategory(it) }
    }

    // Set initial active stream when match is picked
    LaunchedEffect(activePlayingMatch) {
        activePlayingMatch?.let {
            activeStreamUrl = it.streamUrl
        }
    }

    BackHandler(enabled = true) {
        when {
            isFullScreenPlayer -> {
                isFullScreenPlayer = false
            }
            activePlayingMatch != null -> {
                activePlayingMatch = null
                activeStreamUrl = ""
            }
            searchQuery.isNotEmpty() -> {
                searchQuery = ""
            }
            selectedCategory != null -> {
                selectedCategory = null
            }
            else -> {
                onNavigateBack()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ==============================================================
            // 1. TOP HEADER & BRANDING
            // ==============================================================
            Surface(
                color = CinemaSurface,
                border = BorderStroke(1.dp, CinemaBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedCategory != null) {
                                IconButton(
                                    onClick = { selectedCategory = null },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to categories",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // Sports Brand Icon
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = BrandRed,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = selectedCategory?.icon ?: Icons.Default.SportsCricket,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "MUKUL SPORTS",
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = selectedCategory?.title ?: "স্পোর্টস হাব (ক্যাটাগরি বাটন)",
                                    color = CyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Refresh / Info Buttons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedCategory != null) {
                                IconButton(
                                    onClick = { loadCategory(selectedCategory!!, force = true) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // If a category is selected, show Search and Quick Category Switch Buttons
                    if (selectedCategory != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Search input
                        Surface(
                            color = CinemaSurfaceVariant,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, CinemaBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = TextMuted,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = TextPrimary,
                                        fontSize = 13.sp
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "ম্যাচ, দল বা চ্যানেল সার্চ করুন...",
                                                color = TextMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = TextMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Horizontal Quick Switch Buttons for all 5 Categories
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(SportsCategoryType.values()) { cat ->
                                val isSelected = selectedCategory == cat
                                Surface(
                                    onClick = { loadCategory(cat) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) BrandRed else CinemaBorder
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = cat.icon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else TextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cat.title,
                                            color = if (isSelected) Color.White else TextPrimary,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==============================================================
            // 2. VIDEO PLAYER DISPLAY (Seamless ExoPlayer or Web Embed)
            // ==============================================================
            if (activePlayingMatch != null) {
                val match = activePlayingMatch!!
                val isWeb = match.isWebEmbed || activeStreamUrl.contains("embed") ||
                        activeStreamUrl.contains("php") || activeStreamUrl.contains("html") ||
                        activeStreamUrl.contains("soccerfull") || activeStreamUrl.contains("ok.ru") ||
                        activeStreamUrl.contains("dailymotion") || activeStreamUrl.contains("pantyflix")

                val playerHeight = if (isFullScreenPlayer) 320.dp else 225.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(playerHeight)
                        .background(Color.Black)
                ) {
                    if (isWeb) {
                        SportsWebPlayerView(
                            streamUrl = activeStreamUrl.ifEmpty { match.streamUrl },
                            title = match.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        VideoPlayerView(
                            videoUrl = activeStreamUrl.ifEmpty { match.streamUrl },
                            title = match.title,
                            onClose = {
                                activePlayingMatch = null
                                activeStreamUrl = ""
                            },
                            onFullScreenToggle = { isFullScreenPlayer = !isFullScreenPlayer },
                            isFullScreen = isFullScreenPlayer,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Player Match Title and Action Controls
                Surface(
                    color = CinemaSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = match.title,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (match.subtitle.isNotEmpty()) match.subtitle else match.league,
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = BrandRed,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (match.isLive) "🔴 LIVE" else "HD",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        activePlayingMatch = null
                                        activeStreamUrl = ""
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close player",
                                        tint = TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Server / Part Selector (if match has multiple servers or parts)
                        if (match.servers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(match.servers) { server ->
                                    val isCurrentServer = activeStreamUrl == server.second
                                    Surface(
                                        onClick = { activeStreamUrl = server.second },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCurrentServer) BrandRed else CinemaSurface,
                                        border = BorderStroke(1.dp, if (isCurrentServer) BrandRed else CinemaBorder)
                                    ) {
                                        Text(
                                            text = server.first,
                                            color = if (isCurrentServer) Color.White else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isCurrentServer) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Quick Player Action Buttons
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { isFullScreenPlayer = !isFullScreenPlayer },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CinemaSurface,
                                    contentColor = TextPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullScreenPlayer) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isFullScreenPlayer) "ছোট করুন" else "পূর্ণ পর্দা", fontSize = 11.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val targetUrl = activeStreamUrl.ifEmpty { match.streamUrl }
                                    if (targetUrl.isNotEmpty()) {
                                        try {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(browserIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "ব্রাউজারে খুলতে ব্যর্থ", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = CinemaSurface,
                                    contentColor = TextSecondary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ব্রাউজারে খুলুন", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // ==============================================================
            // 3. MAIN CONTENT: 5 CATEGORY BUTTONS OR MATCH LIST
            // ==============================================================
            if (selectedCategory == null) {
                // CATEGORY SELECTION HUB (বাটন আকারে ৫টি লিঙ্ক ক্যাটাগরি পেজ)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Column {
                            Text(
                                text = "স্পোর্টস ক্যাটাগরি নির্বাচন করুন",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "নিচের যে কোনো বাটনে ক্লিক করে সরাসরি নির্দিষ্ট লাইভ বা হাইলাইটস পেজে প্রবেশ করুন",
                                color = TextMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    items(SportsCategoryType.values()) { cat ->
                        SportsCategoryBigButton(
                            category = cat,
                            onClick = { loadCategory(cat) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            } else {
                // MATCHES / CHANNELS LIST FOR SELECTED CATEGORY
                val filtered = remember(matches, searchQuery) {
                    if (searchQuery.isBlank()) matches
                    else matches.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.subtitle.contains(searchQuery, ignoreCase = true) ||
                                it.league.contains(searchQuery, ignoreCase = true)
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = BrandRed)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "${selectedCategory?.title} লোড হচ্ছে...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "বর্তমানে কোনো ম্যাচ বা স্ট্রিম পাওয়া যায়নি",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "দয়া করে রিফ্রেশ করুন বা অন্য ক্যাটাগরি দেখুন",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { loadCategory(selectedCategory!!, force = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("পুনরায় চেষ্টা করুন")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "মোট আইটেম: ${filtered.size} টি",
                                    color = TextMuted,
                                    fontSize = 11.5.sp
                                )
                                Text(
                                    text = "সরাসরি প্লে করতে ট্যাপ করুন",
                                    color = CyanAccent,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        items(filtered, key = { it.id }) { match ->
                            SportsMatchCard(
                                match = match,
                                isPlaying = activePlayingMatch?.id == match.id,
                                onPlayClick = {
                                    if (match.streamUrl.isNotEmpty()) {
                                        activePlayingMatch = match
                                        activeStreamUrl = match.streamUrl
                                    } else {
                                        Toast.makeText(context, "এই ম্যাচের স্ট্রিম লিঙ্ক শীঘ্রই আসছে", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5 Category Big Interactive Buttons (বাটন আকারে নির্দিষ্ট লিঙ্ক ক্যাটাগরি)
 */
@Composable
private fun SportsCategoryBigButton(
    category: SportsCategoryType,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = BorderStroke(1.dp, CinemaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when (category) {
                    SportsCategoryType.REPLAYS -> Color(0xFFE11D48)
                    SportsCategoryType.LIVE_CRICKET -> Color(0xFF0284C7)
                    SportsCategoryType.LEAGUE_LIVE -> Color(0xFF059669)
                    SportsCategoryType.FREE_LIVE_SPORTS -> Color(0xFFD97706)
                    SportsCategoryType.WILLOW_EVENTS -> Color(0xFF7C3AED)
                },
                modifier = Modifier.size(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.title,
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = BrandRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = category.badge,
                            color = BrandRedLight,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = category.subtitle,
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Surface(
                color = CinemaSurfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Uniform Sports Match Card (সমস্ত কার্ড একসমান ও ইউজার ফ্রেন্ডলি)
 */
@Composable
private fun SportsMatchCard(
    match: SportsMatchItem,
    isPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlayClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) CinemaSurfaceVariant else CinemaSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isPlaying) BrandRed else CinemaBorder
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Icon box (Fixed Uniform Size 96 x 64 dp)
            Box(
                modifier = Modifier
                    .size(96.dp, 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CinemaSurfaceVariant)
            ) {
                if (match.posterUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(match.posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = match.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(CinemaSurfaceVariant, CinemaBorder)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = match.categoryType.icon,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Live status overlay badge
                Surface(
                    color = if (match.isLive) Color(0xFFDC2626).copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (match.isLive) "🔴 LIVE" else match.statusText.ifEmpty { "HD" },
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Server count indicator if multiple servers exist
                if (match.servers.size > 1) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(topStart = 6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = "${match.servers.size} সার্ভার",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details (Uniform layout)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = match.title,
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.5.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (match.league.isNotEmpty()) match.league else match.categoryType.title,
                        color = CyanAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (match.timeOrDate.isNotEmpty()) {
                        Text(
                            text = " • ${match.timeOrDate}",
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = if (match.subtitle.isNotEmpty() && match.subtitle != match.title) match.subtitle else "ক্লিক করে স্ট্রিম চালু করুন",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play Action Button
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) Color(0xFF10B981) else BrandRed
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Tv,
                    contentDescription = "Watch",
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (isPlaying) "চলছে" else "দেখুন",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * High-Performance In-App Hardware-Accelerated Web Player
 * Handles HTML iframe embeds, Willow streams, Replay servers, and Cloudflare pages seamlessly
 */
@Composable
private fun SportsWebPlayerView(
    streamUrl: String,
    title: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    userAgentString = "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
                }

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        return false // keep navigation inside webview
                    }
                }

                // If URL is an embed link or iframe source, wrap in clean responsive player html
                if (streamUrl.startsWith("http")) {
                    val htmlWrapper = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                * { margin:0; padding:0; box-sizing:border-box; }
                                body, html { width:100%; height:100%; background:#000000; overflow:hidden; display:flex; align-items:center; justify-content:center; }
                                iframe { width:100%; height:100%; border:none; }
                            </style>
                        </head>
                        <body>
                            <iframe 
                                src="$streamUrl" 
                                allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen" 
                                allowfullscreen>
                            </iframe>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL(streamUrl, htmlWrapper, "text/html", "UTF-8", null)
                }
            }
        },
        update = { webView ->
            if (streamUrl.startsWith("http")) {
                val htmlWrapper = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            * { margin:0; padding:0; box-sizing:border-box; }
                            body, html { width:100%; height:100%; background:#000000; overflow:hidden; display:flex; align-items:center; justify-content:center; }
                            iframe { width:100%; height:100%; border:none; }
                        </style>
                    </head>
                    <body>
                        <iframe 
                            src="$streamUrl" 
                            allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; fullscreen" 
                            allowfullscreen>
                        </iframe>
                    </body>
                    </html>
                """.trimIndent()
                webView.loadDataWithBaseURL(streamUrl, htmlWrapper, "text/html", "UTF-8", null)
            }
        }
    )
}
