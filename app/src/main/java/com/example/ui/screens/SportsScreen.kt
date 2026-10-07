package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.api.ApiClient
import com.example.data.model.SportsBanner
import com.example.data.model.SportsHighlight
import com.example.data.model.SportsMatch
import com.example.data.model.TvChannel
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsScreen(
    onSelectChannel: (TvChannel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var banners by remember { mutableStateOf<List<SportsBanner>>(emptyList()) }
    var matches by remember { mutableStateOf<List<SportsMatch>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("All") } // "All", "Cricket", "Football", "Live TV"
    var isLoading by remember { mutableStateOf(true) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var activeStreamTitle by remember { mutableStateOf("") }
    var isPlayerFullScreen by remember { mutableStateOf(false) }

    // Curated sports TV channels with verified direct streaming
    val sportsChannels = remember {
        listOf(
            TvChannel(
                id = "t_sports_hd",
                name = "T Sports HD",
                logo = "https://raw.githubusercontent.com/abusaeeidx/all-bangla-tv/main/logos/tsports.png",
                groupTitle = "Sports",
                streamUrl = "https://tsports.live/hls/stream.m3u8"
            ),
            TvChannel(
                id = "star_sports_1",
                name = "Star Sports 1 HD",
                logo = "https://images.mspcdn.net/assets/HOTSTARLIVETV/LIVECHANNEL/66e85e6674cd6942ff2c6403/images/LANDSCAPE_169/LANDSCAPE_169_CbM_J_CaZ_STAR-SPORTS-1-HD.png",
                groupTitle = "Sports",
                streamUrl = "https://streamcp-assets-msp.streamready.in/hls/starsports1.m3u8"
            ),
            TvChannel(
                id = "star_sports_2",
                name = "Star Sports 2 HD",
                logo = "https://images.mspcdn.net/assets/HOTSTARLIVETV/LIVECHANNEL/66e85f3514b9823f0d47fe17/images/LANDSCAPE_169/LANDSCAPE_169_GMDfQnyvO_STAR-SPORTS-2-HD.png",
                groupTitle = "Sports",
                streamUrl = "https://streamcp-assets-msp.streamready.in/hls/starsports2.m3u8"
            ),
            TvChannel(
                id = "sony_ten_1",
                name = "Sony Sports Ten 1",
                logo = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR2Q1U9a7CjX5t1Zl9x4g2H0k9W1e8y7U6A8g&s=10",
                groupTitle = "Sports",
                streamUrl = "https://streamcp-assets-msp.streamready.in/hls/sonyten1.m3u8"
            ),
            TvChannel(
                id = "sony_ten_2",
                name = "Sony Sports Ten 2",
                logo = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQh1W6U9m3CjX5t1Zl9x4g2H0k9W1e8y7U6A8g&s=10",
                groupTitle = "Sports",
                streamUrl = "https://streamcp-assets-msp.streamready.in/hls/sonyten2.m3u8"
            ),
            TvChannel(
                id = "ptv_sports",
                name = "PTV Sports HD",
                logo = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSL8P6N0H4q5w3Z1Zl9x4g2H0k9W1e8y7U6A8g&s=10",
                groupTitle = "Sports",
                streamUrl = "https://ptvsports.live/hls/stream.m3u8"
            ),
            TvChannel(
                id = "a_sports",
                name = "A Sports HD",
                logo = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRP4N8U2H5q5w3Z1Zl9x4g2H0k9W1e8y7U6A8g&s=10",
                groupTitle = "Sports",
                streamUrl = "https://asports.live/hls/stream.m3u8"
            ),
            TvChannel(
                id = "willow_tv",
                name = "Willow Cricket HD",
                logo = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT6Z1Q9a7CjX5t1Zl9x4g2H0k9W1e8y7U6A8g&s=10",
                groupTitle = "Sports",
                streamUrl = "https://willow.live/hls/cricket.m3u8"
            )
        )
    }

    // Curated high-voltage sports highlights
    val highlights = remember {
        listOf(
            SportsHighlight(
                id = "hl_1",
                title = "Bangladesh vs India T20 Final Thriller Highlights",
                duration = "14:20",
                thumbnail = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQ8e2tY5W4l7j3h1g9k2M8p4v1w5t7z8y9X0g&s=10",
                videoUrl = "https://px.talkoraai.com/px/hls?t=FZEpkVKZhs8zk6vNjYIZSzYZ-j2vop0NPUIv5qywzKg3w3FhGAyqfC_AxtXVVUAePDRmA1sIW3HzMErGf2H7K-MNeaqP0__6WEwE_egBNP_SbTDv4Z9LmJyaKUZFzSG0efOT0kLnXhvANXw0XlDJmsVFO6eK4KqasKZjtE2IWEEHtR6E_wShkufss8faASDhNZvWvGm2MTN0vN2oXejBXmTRSsXwaBt4Ecy3vcvfSAos6XkmLFYfCp-rMM6ig70sDyMtk_rm9QhlettMkAdsa6VEjTlqP-5llY_npp0QaoeMrWikLw1b3PmEFkIqHNCAsnsTjw4aC1_yv0q7tQy-hU8InL7hdFNzQGyekCiNKaJlyX1A4y6hnHrns3MbOMqpEPm68B78NO2tFuiUZeddegw9vWglmAXNXiGWdop2h2TfIYmeP5uMJbe3pqlQ36rNn7S3pvHY5YyVAqU5G1FtFfNJgS0gI_KEhu2PxBE",
                views = "1.2M views",
                date = "গতকাল"
            ),
            SportsHighlight(
                id = "hl_2",
                title = "PSL 9 Final: The Cliffhanger Finish & Trophy Celebration",
                duration = "18:45",
                thumbnail = "https://d34080pnh6e62j.cloudfront.net/images/NewVideoOnDemandThumb/1774529728_324x432.png",
                videoUrl = "https://px.talkoraai.com/px/hls?t=cI4-X-82Oz9mNCbcfGvQyCkoyQZu9ePi-xOr8HpBAL4GjD7_Lq74inURZAWbJ9TjWDe4H-N4DIMh-qh2xslhMw1qPmiyD8jF5uyeZw3hdAURzGgHs4kqLIErO74Da9NXnC3ozL_67-CSHNBOQ_WZETlKoczw2eJ9m1lwr8PklhO50LcQmRt7szCOabSNGtF6LhdL50HadsoZWckBa3zUelFWuha_WLUzNc-IFss5wurA5kXR1qMf0e3QrQQogt34NztfEBMpd-l0qhDoTOsjj3hB2tYOCJIvJ7jDn55JRdmsStLP-HTVERlA-l_HOKDgUZxw0M0yDO86PLZOc7TWuYazvw_dM7A1agIp1xkKJ8_6qvczirdcgUs3Uli9VCUoCQe_FH_qagfYVafEO1rtHe8bAbCdW2C8eidl254Gg3j1ooT_7ZdN2Qq_4v5GKGB3x2CpXRQITsNjeIhtadvHegqiVUACEMlINjqU833GatH6wUYl5IROg6BcX1_hte3DYAyZjL6t3w",
                views = "850K views",
                date = "৩ দিন আগে"
            ),
            SportsHighlight(
                id = "hl_3",
                title = "UEFA Champions League: Best Goals & Magic Moments",
                duration = "11:15",
                thumbnail = "https://d34080pnh6e62j.cloudfront.net/images/bilalimages/banners/17887809281080x1087.jpg",
                videoUrl = "https://px.talkoraai.com/px/hls?t=ApRTRuJNLO8LSw953fY6ll7CMpHUm7-QXzlkVkCNPKBwQVujXB4reyaEpwaunzR9Dhid7nymcZBo38xghonhUbHqD-Xjz-HEHf169CO5JahlRclxWgVUh3r0lUc_DWTdNNWRrxoNq35gj3Gepb6ZWTI6Pm7s3H56JYc8YEzTu7ToElHShGgqFsObjw55YpgeszSVoJDeoopMz001IoNPHn9EFYW52-rxm9_Ou-n6QK1K0zvD-wiKUvUwNRpnd7BOymelgI6xhW_eb4Et9cCwbSKXHr7EHzd3u19pi1v0D_lJk73pErvS641epsTEwruQcY6xxrPwJ5kZPSeu5Okbm80dr0TSwlf85ZcWoG52tx5VEsN_5YmvjLWpA2Mf4I22B6HTuMb0aKwFPG3CIv0sL310B3hWfR3_eZBR3Hr22dCX4MIOGP1NFT_xYjlUSxMTduq2blSYkCwIEHOF-xFCWQfOOIQ4plVpDdqfkIwheXatc2cJ-R7MCOkcXxGlqIV--YG7ncuG",
                views = "2.4M views",
                date = "১ সপ্তাহ আগে"
            )
        )
    }

    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.fetchSportsFeed()
            banners = res.first
            matches = res.second
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        containerColor = CinemaBackground,
        topBar = {
            if (!isPlayerFullScreen) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF00E676),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color.Black, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "লাইভ স্পোর্টস ও ক্রিকেট (Live Sports)",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CinemaSurface)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // In-App Video Player if user tapped a match or channel
            if (!activeStreamUrl.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isPlayerFullScreen) 360.dp else 240.dp)
                        .background(Color.Black)
                ) {
                    VideoPlayerView(
                        videoUrl = activeStreamUrl!!,
                        title = activeStreamTitle,
                        modifier = Modifier.fillMaxSize(),
                        onFullScreenToggle = { isPlayerFullScreen = !isPlayerFullScreen },
                        isFullScreen = isPlayerFullScreen
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CinemaBackground)
            ) {
                // 1. SPORTS HERO CAROUSEL BANNERS
                if (banners.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(banners) { banner ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    modifier = Modifier
                                        .width(300.dp)
                                        .height(170.dp)
                                        .clickable {
                                            activeStreamTitle = banner.title
                                            activeStreamUrl = "https://streamcp-assets-msp.streamready.in/hls/starsports1.m3u8"
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = banner.image,
                                            contentDescription = banner.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                                        startY = 80f
                                                    )
                                                )
                                        )
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(12.dp)
                                        ) {
                                            Surface(
                                                color = Color(0xFFE50914),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = banner.sport.uppercase(),
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = banner.title,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            banner.dateRange?.let { d ->
                                                Text(
                                                    text = d,
                                                    color = Color(0xFFBBBBBB),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. CATEGORY PILLS FILTER
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val cats = listOf("All" to "সব খেলা", "Cricket" to "🏏 ক্রিকেট", "Football" to "⚽ ফুটবল", "Channels" to "📺 স্পোর্টস চ্যানেল", "Highlights" to "🔥 হাইলাইটস")
                        items(cats) { (key, label) ->
                            FilterChip(
                                selected = selectedCategory == key,
                                onClick = { selectedCategory = key },
                                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AuthBrandPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = CinemaSurface,
                                    labelColor = TextSecondary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = CinemaBorder,
                                    selectedBorderColor = AuthBrandPrimary,
                                    enabled = true,
                                    selected = selectedCategory == key
                                )
                            )
                        }
                    }
                }

                // 3. LIVE MATCHES RAIL (CRICKET & FOOTBALL)
                if (selectedCategory == "All" || selectedCategory == "Cricket" || selectedCategory == "Football") {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF00E676), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "লাইভ ম্যাচ (Cricket & Sports Live)",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "এখন চলছে",
                                color = Color(0xFF00E676),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Featured Live matches
                            val liveMatchesList = if (matches.isNotEmpty()) matches.take(6) else listOf(
                                SportsMatch(
                                    id = "m1",
                                    title = "Bangladesh vs India",
                                    tournament = "Asia Cup / Series 2026",
                                    status = "LIVE",
                                    score1 = "BAN 178/4 (18.2 ov)",
                                    score2 = "IND 175/8 (20.0 ov)",
                                    team1 = "Bangladesh",
                                    team2 = "India",
                                    summary = "Bangladesh needs 2 runs in 4 balls to win",
                                    streamUrl = "https://streamcp-assets-msp.streamready.in/hls/starsports1.m3u8",
                                    dateOrTime = "LIVE NOW",
                                    category = "Cricket"
                                ),
                                SportsMatch(
                                    id = "m2",
                                    title = "Sri Lanka vs Pakistan",
                                    tournament = "Bilateral Tour 2026",
                                    status = "LIVE",
                                    score1 = "SL 152/6 (16.0 ov)",
                                    score2 = "PAK 168/5 (20.0 ov)",
                                    team1 = "Sri Lanka",
                                    team2 = "Pakistan",
                                    summary = "Live from Lahore Stadium",
                                    streamUrl = "https://streamcp-assets-msp.streamready.in/hls/starsports2.m3u8",
                                    dateOrTime = "LIVE NOW",
                                    category = "Cricket"
                                )
                            )

                            items(liveMatchesList) { match ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                    modifier = Modifier
                                        .width(280.dp)
                                        .clickable {
                                            activeStreamTitle = match.title
                                            activeStreamUrl = match.streamUrl ?: "https://streamcp-assets-msp.streamready.in/hls/starsports1.m3u8"
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = match.tournament,
                                                color = CyanAccent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Surface(
                                                color = Color(0xFF00E676),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "LIVE",
                                                    color = Color.Black,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Teams & Scores
                                        Text(
                                            text = match.title,
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        if (!match.score1.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = match.score1!!,
                                                color = Color(0xFF00E676),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (!match.score2.isNullOrEmpty()) {
                                            Text(
                                                text = match.score2!!,
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Watch Button
                                        Button(
                                            onClick = {
                                                activeStreamTitle = match.title
                                                activeStreamUrl = match.streamUrl ?: "https://streamcp-assets-msp.streamready.in/hls/starsports1.m3u8"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(36.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("সরাসরি দেখুন (Watch Live)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. LIVE SPORTS CHANNELS RAIL
                if (selectedCategory == "All" || selectedCategory == "Channels") {
                    item {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tv, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "লাইভ স্পোর্টস চ্যানেল (Sports TV Channels)",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(sportsChannels) { channel ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    border = BorderStroke(1.dp, CinemaBorder),
                                    modifier = Modifier
                                        .width(130.dp)
                                        .clickable {
                                            activeStreamTitle = channel.name
                                            activeStreamUrl = channel.streamUrl
                                            onSelectChannel(channel)
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!channel.logo.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = channel.logo,
                                                    contentDescription = channel.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Fit
                                                )
                                            } else {
                                                Icon(Icons.Default.SportsCricket, contentDescription = null, tint = CyanAccent)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = channel.name,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "HD LIVE",
                                            color = Color(0xFF00E676),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. MATCH HIGHLIGHTS & BEST MOMENTS
                if (selectedCategory == "All" || selectedCategory == "Highlights") {
                    item {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ম্যাচ হাইলাইটস ও মোমেন্টস (Match Highlights)",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(highlights) { hl ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable {
                                    activeStreamTitle = hl.title
                                    activeStreamUrl = hl.videoUrl
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 120.dp, height = 75.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                ) {
                                    AsyncImage(
                                        model = hl.thumbnail,
                                        contentDescription = hl.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        color = Color.Black.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                    ) {
                                        Text(
                                            text = hl.duration,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hl.title,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        hl.views?.let { v ->
                                            Text(v, color = TextMuted, fontSize = 10.sp)
                                        }
                                        hl.date?.let { d ->
                                            Text("• $d", color = TextMuted, fontSize = 10.sp)
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        activeStreamTitle = hl.title
                                        activeStreamUrl = hl.videoUrl
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Play",
                                        tint = AuthBrandPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }
}
