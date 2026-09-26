package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MusicAlbum
import com.example.data.model.MusicCategory
import com.example.data.model.MusicPlaylist
import com.example.data.model.MusicTrack
import com.example.data.player.MusicPlayerManager
import com.example.data.repository.MusicRepository
import com.example.data.util.DownloadUtils
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    musicRepository: MusicRepository,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val featuredPlaylist by musicRepository.featuredPlaylist.collectAsState()
    val featuredSongs by musicRepository.featuredSongs.collectAsState()
    val topPlaylists by musicRepository.topPlaylists.collectAsState()
    val popularAlbums by musicRepository.popularAlbums.collectAsState()
    val trendingSongs by musicRepository.trendingSongs.collectAsState()
    val isLoadingHome by musicRepository.isLoading.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchSongs by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var searchAlbums by remember { mutableStateOf<List<MusicAlbum>>(emptyList()) }
    var searchPlaylists by remember { mutableStateOf<List<MusicPlaylist>>(emptyList()) }

    var selectedCategory by remember { mutableStateOf<MusicCategory?>(null) }
    var categoryPlaylist by remember { mutableStateOf<MusicPlaylist?>(null) }
    var categorySongs by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var isLoadingCategory by remember { mutableStateOf(false) }

    // Playlist/Album Detail Dialog state
    var selectedPlaylistDetail by remember { mutableStateOf<Pair<MusicPlaylist?, List<MusicTrack>>?>(null) }
    var selectedAlbumDetail by remember { mutableStateOf<Pair<MusicAlbum?, List<MusicTrack>>?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }

    // Category selection trigger
    fun selectCategory(cat: MusicCategory?) {
        selectedCategory = cat
        if (cat == null) {
            categoryPlaylist = null
            categorySongs = emptyList()
            return
        }
        coroutineScope.launch {
            isLoadingCategory = true
            val res = musicRepository.loadCategorySongs(cat)
            categoryPlaylist = res.first
            categorySongs = res.second
            isLoadingCategory = false
        }
    }

    // Search trigger
    fun performSearch(query: String) {
        if (query.isBlank()) {
            searchSongs = emptyList()
            searchAlbums = emptyList()
            searchPlaylists = emptyList()
            isSearching = false
            return
        }
        coroutineScope.launch {
            isSearching = true
            val (s, a, p) = musicRepository.searchAll(query)
            searchSongs = s
            searchAlbums = a
            searchPlaylists = p
            isSearching = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 130.dp)
        ) {
            // 1. Search Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBack != null) {
                        Surface(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            color = CinemaSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                            modifier = Modifier.size(48.dp)
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

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        color = CinemaSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                    ) {
                        TextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            performSearch(it)
                        },
                        placeholder = {
                            Text(
                                "গান, অ্যালবাম বা শিল্পী খুঁজুন (যেমন: Bindu, Arijit, 90s)...",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = BrandRed
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    performSearch("")
                                }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = TextMuted
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

            // 2. Categories Scroll
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null && searchQuery.isEmpty(),
                            onClick = { selectCategory(null) },
                            label = { Text("সব গান (All)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(musicRepository.categories) { cat ->
                        val isSel = selectedCategory?.id == cat.id
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                if (isSel) selectCategory(null) else selectCategory(cat)
                            },
                            label = { Text(cat.titleBn) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // If active search results exist:
            if (searchQuery.isNotBlank()) {
                if (isSearching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BrandRed)
                        }
                    }
                } else {
                    // Search Songs Results
                    if (searchSongs.isNotEmpty()) {
                        item {
                            SectionHeader(title = "গানসমূহ (${searchSongs.size})")
                        }
                        itemsIndexed(searchSongs) { index, track ->
                            SongItemRow(
                                index = index + 1,
                                track = track,
                                onPlay = {
                                    MusicPlayerManager.playTrack(track, searchSongs)
                                },
                                onDownload = {
                                    val dlUrl = if (track.downloadUrl.isNotEmpty()) track.downloadUrl else track.streamUrl
                                    DownloadUtils.openDownloadInChrome(context, dlUrl)
                                }
                            )
                        }
                    }

                    // Search Albums
                    if (searchAlbums.isNotEmpty()) {
                        item {
                            SectionHeader(title = "অ্যালবাম (${searchAlbums.size})")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(searchAlbums) { album ->
                                    AlbumCard(album = album) {
                                        coroutineScope.launch {
                                            isLoadingDetail = true
                                            val res = musicRepository.getAlbum(album.id)
                                            selectedAlbumDetail = res
                                            isLoadingDetail = false
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Search Playlists
                    if (searchPlaylists.isNotEmpty()) {
                        item {
                            SectionHeader(title = "প্লেলিস্ট (${searchPlaylists.size})")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(searchPlaylists) { pl ->
                                    PlaylistCard(playlist = pl) {
                                        coroutineScope.launch {
                                            isLoadingDetail = true
                                            val res = musicRepository.getPlaylist(pl.id)
                                            selectedPlaylistDetail = res
                                            isLoadingDetail = false
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (searchSongs.isEmpty() && searchAlbums.isEmpty() && searchPlaylists.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "কোনো গান বা অ্যালবাম পাওয়া যায়নি",
                                    color = TextMuted,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            } else if (selectedCategory != null) {
                // Category view
                if (isLoadingCategory) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BrandRed)
                        }
                    }
                } else {
                    item {
                        // Category Header Banner
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = CinemaSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(BrandRed.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val img = categoryPlaylist?.imageUrl ?: categorySongs.firstOrNull()?.imageUrl ?: ""
                                    if (img.isNotEmpty()) {
                                        AsyncImage(
                                            model = img,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = BrandRed,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedCategory?.titleBn ?: "",
                                        color = TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${categorySongs.size} টি গান",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            if (categorySongs.isNotEmpty()) {
                                                MusicPlayerManager.playTrack(categorySongs[0], categorySongs)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("সব গান বাজান", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader(title = "গানসমূহ (${categorySongs.size})")
                    }

                    itemsIndexed(categorySongs) { index, track ->
                        SongItemRow(
                            index = index + 1,
                            track = track,
                            onPlay = {
                                MusicPlayerManager.playTrack(track, categorySongs)
                            },
                            onDownload = {
                                val dlUrl = if (track.downloadUrl.isNotEmpty()) track.downloadUrl else track.streamUrl
                                DownloadUtils.openDownloadInChrome(context, dlUrl)
                            }
                        )
                    }
                }
            } else {
                // Default Home View
                // 3. Featured Hero Card: Hindi 1990s Playlist (User request)
                featuredPlaylist?.let { pl ->
                    item {
                        FeaturedHeroCard(
                            playlist = pl,
                            songs = featuredSongs,
                            onPlayAll = {
                                if (featuredSongs.isNotEmpty()) {
                                    MusicPlayerManager.playTrack(featuredSongs[0], featuredSongs)
                                }
                            },
                            onViewDetails = {
                                selectedPlaylistDetail = Pair(pl, featuredSongs)
                            }
                        )
                    }
                }

                // 4. Top Playlists (Hindi Top Hits, Bollywood, etc.)
                if (topPlaylists.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "জনপ্রিয় প্লেলিস্ট (Hindi Top Hits)",
                            action = "আরও দেখুন"
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(topPlaylists) { pl ->
                                PlaylistCard(playlist = pl) {
                                    coroutineScope.launch {
                                        isLoadingDetail = true
                                        val res = musicRepository.getPlaylist(pl.id)
                                        selectedPlaylistDetail = res
                                        isLoadingDetail = false
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Popular Albums
                if (popularAlbums.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        SectionHeader(
                            title = "জনপ্রিয় অ্যালবাম (Albums)",
                            action = "সকল অ্যালবাম"
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(popularAlbums) { album ->
                                AlbumCard(album = album) {
                                    coroutineScope.launch {
                                        isLoadingDetail = true
                                        val res = musicRepository.getAlbum(album.id)
                                        selectedAlbumDetail = res
                                        isLoadingDetail = false
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Trending Songs (Bindu & Hindi Hits)
                val songsToShow = if (trendingSongs.isNotEmpty()) trendingSongs else featuredSongs
                if (songsToShow.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        SectionHeader(
                            title = "সেরা গানসমূহ (Trending Hits)",
                            action = "প্লে অল",
                            onActionClick = {
                                if (songsToShow.isNotEmpty()) {
                                    MusicPlayerManager.playTrack(songsToShow[0], songsToShow)
                                }
                            }
                        )
                    }
                    itemsIndexed(songsToShow.take(20)) { index, track ->
                        SongItemRow(
                            index = index + 1,
                            track = track,
                            onPlay = {
                                MusicPlayerManager.playTrack(track, songsToShow)
                            },
                            onDownload = {
                                val dlUrl = if (track.downloadUrl.isNotEmpty()) track.downloadUrl else track.streamUrl
                                DownloadUtils.openDownloadInChrome(context, dlUrl)
                            }
                        )
                    }
                }
            }
        }

        // Playlist Details Dialog
        selectedPlaylistDetail?.let { (pl, songs) ->
            if (pl != null) {
                PlaylistDetailDialog(
                    playlist = pl,
                    songs = songs,
                    onDismiss = { selectedPlaylistDetail = null },
                    onPlaySong = { track ->
                        MusicPlayerManager.playTrack(track, songs)
                    },
                    onDownloadSong = { track ->
                        val dlUrl = if (track.downloadUrl.isNotEmpty()) track.downloadUrl else track.streamUrl
                        DownloadUtils.openDownloadInChrome(context, dlUrl)
                    }
                )
            }
        }

        // Album Details Dialog
        selectedAlbumDetail?.let { (album, songs) ->
            if (album != null) {
                AlbumDetailDialog(
                    album = album,
                    songs = songs,
                    onDismiss = { selectedAlbumDetail = null },
                    onPlaySong = { track ->
                        MusicPlayerManager.playTrack(track, songs)
                    },
                    onDownloadSong = { track ->
                        val dlUrl = if (track.downloadUrl.isNotEmpty()) track.downloadUrl else track.streamUrl
                        DownloadUtils.openDownloadInChrome(context, dlUrl)
                    }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        if (action != null) {
            Text(
                text = action,
                color = BrandRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onActionClick?.invoke() }
            )
        }
    }
}

@Composable
private fun FeaturedHeroCard(
    playlist: MusicPlaylist,
    songs: List<MusicTrack>,
    onPlayAll: () -> Unit,
    onViewDetails: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = CinemaSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Background Artwork
            if (playlist.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = playlist.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }

            // Dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black
                            )
                        )
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .align(Alignment.BottomStart)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BrandRed
                ) {
                    Text(
                        text = "ফিচার্ড প্লেলিস্ট (FEATURED)",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )

                if (playlist.description.isNotEmpty()) {
                    Text(
                        text = playlist.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayAll,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "সব বাজান",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onViewDetails,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "তালিকা (${songs.size})",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: MusicPlaylist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(135.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CinemaSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .size(119.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CinemaSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = playlist.imageUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = playlist.name,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (playlist.songCount > 0) {
                Text(
                    text = "${playlist.songCount} টি গান",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: MusicAlbum,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(135.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CinemaSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .size(119.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CinemaSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (album.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = album.imageUrl,
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Album,
                        contentDescription = null,
                        tint = GoldRating,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = album.name,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = if (album.artistNames.isNotEmpty()) album.artistNames else album.year,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SongItemRow(
    index: Int,
    track: MusicTrack,
    onPlay: () -> Unit,
    onDownload: () -> Unit
) {
    val currentPlayingTrack by MusicPlayerManager.currentTrack.collectAsState()
    val isPlaying by MusicPlayerManager.isPlaying.collectAsState()
    val isThisTrack = currentPlayingTrack?.id == track.id

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(14.dp),
        color = if (isThisTrack) BrandRed.copy(alpha = 0.08f) else CinemaSurface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isThisTrack) BrandRed.copy(alpha = 0.4f) else CinemaBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Index / Playing Indicator
            if (isThisTrack) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = BrandRed,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 6.dp)
                )
            } else {
                Text(
                    text = "$index",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(24.dp)
                )
            }

            // Thumbnail
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CinemaSurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (track.imageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = track.imageUrl,
                        contentDescription = track.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = BrandRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & Artists
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    color = if (isThisTrack) BrandRed else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isThisTrack) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = if (track.artistNames.isNotEmpty()) track.artistNames else track.albumName,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            // Duration
            if (track.duration > 0) {
                Text(
                    text = track.durationFormatted,
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            // Download in Chrome Button
            IconButton(
                onClick = onDownload,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Download in Chrome",
                    tint = BrandRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetailDialog(
    playlist: MusicPlaylist,
    songs: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlaySong: (MusicTrack) -> Unit,
    onDownloadSong: (MusicTrack) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = CinemaBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${songs.size} টি গান",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                // Play all button
                if (songs.isNotEmpty()) {
                    Button(
                        onClick = { onPlaySong(songs[0]) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("সব গান বাজান (Play All)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(songs) { idx, s ->
                        SongItemRow(
                            index = idx + 1,
                            track = s,
                            onPlay = { onPlaySong(s) },
                            onDownload = { onDownloadSong(s) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailDialog(
    album: MusicAlbum,
    songs: List<MusicTrack>,
    onDismiss: () -> Unit,
    onPlaySong: (MusicTrack) -> Unit,
    onDownloadSong: (MusicTrack) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = CinemaBackground,
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.name,
                            color = TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${album.artistNames} • ${songs.size} টি গান",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                // Play all button
                if (songs.isNotEmpty()) {
                    Button(
                        onClick = { onPlaySong(songs[0]) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("সব গান বাজান (Play All)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(songs) { idx, s ->
                        SongItemRow(
                            index = idx + 1,
                            track = s,
                            onPlay = { onPlaySong(s) },
                            onDownload = { onDownloadSong(s) }
                        )
                    }
                }
            }
        }
    }
}
