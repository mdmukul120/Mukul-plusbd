package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.ApiClient
import com.example.data.model.CtgCategoryItem
import com.example.data.model.CtgMovie
import com.example.data.repository.MediaRepository
import com.example.ui.components.FilterBottomSheet
import com.example.ui.components.FilterChipItem
import com.example.ui.components.MoviePosterCard
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*

@Composable
fun MoviesScreen(
    mediaRepository: MediaRepository,
    onSelectMovie: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var movies by remember { mutableStateOf<List<CtgMovie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var totalOttCount by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }

    // Filter states
    val menusData by mediaRepository.menusData.collectAsState()
    var selectedCategory by remember { mutableStateOf<CtgCategoryItem?>(null) }
    var isBongoSelected by remember { mutableStateOf(true) } // Default to OTT Bangla movies!
    var selectedBongoGenre by remember { mutableStateOf("সব (All)") }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var selectedSort by remember { mutableStateOf("createdAt") }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Active in-screen Video Player
    var activePlayingMovie by remember { mutableStateOf<CtgMovie?>(null) }

    // Debounce search query
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            debouncedQuery = ""
        } else {
            kotlinx.coroutines.delay(300)
            debouncedQuery = searchQuery.trim()
        }
        currentPage = 1
    }

    // Initial load menus
    LaunchedEffect(Unit) {
        mediaRepository.getMenus()
    }

    LaunchedEffect(selectedCategory, isBongoSelected, selectedBongoGenre, selectedYear, selectedGenre, selectedSort, currentPage, debouncedQuery) {
        isLoading = true
        if (isBongoSelected) {
            val allBongo = mediaRepository.getBongoVideos()
            var filtered = allBongo
            if (debouncedQuery.isNotBlank()) {
                val q = debouncedQuery.trim()
                filtered = filtered.filter {
                    it.title.contains(q, ignoreCase = true) ||
                    (it.casts?.contains(q, ignoreCase = true) == true) ||
                    (it.genre?.contains(q, ignoreCase = true) == true) ||
                    (it.overview?.contains(q, ignoreCase = true) == true)
                }
            }
            if (selectedBongoGenre != "সব (All)") {
                filtered = filtered.filter {
                    it.genre?.contains(selectedBongoGenre, ignoreCase = true) == true ||
                    it.title.contains(selectedBongoGenre, ignoreCase = true) ||
                    it.original_title?.contains(selectedBongoGenre, ignoreCase = true) == true
                }
            }
            totalOttCount = filtered.size
            val pageSize = 30
            val computedPages = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
            totalPages = computedPages
            val pageIndex = (currentPage - 1).coerceIn(0, computedPages - 1)
            movies = filtered.drop(pageIndex * pageSize).take(pageSize)
        } else {
            val targetLibrary = if (debouncedQuery.isNotBlank() && selectedCategory == null) null else (selectedCategory?.id ?: 1)
            var res = ApiClient.fetchCtgMovies(
                library = targetLibrary,
                page = currentPage,
                sort = selectedSort,
                sortOrder = "DESC",
                search = debouncedQuery.ifBlank { null },
                year = selectedYear,
                genre = selectedGenre
            )
            if (res.data.isEmpty() && debouncedQuery.isNotBlank() && targetLibrary != null) {
                res = ApiClient.fetchCtgMovies(
                    library = null,
                    page = currentPage,
                    sort = selectedSort,
                    sortOrder = "DESC",
                    search = debouncedQuery.ifBlank { null },
                    year = selectedYear,
                    genre = selectedGenre
                )
            }
            movies = res.data
            totalPages = res.pages
        }
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        // Active In-Screen Video Player Banner (Plays directly with VLC Video Player!)
        AnimatedVisibility(visible = activePlayingMovie != null) {
            activePlayingMovie?.let { movie ->
                val streamUrl = movie.getFullStreamUrl() ?: ""
                if (streamUrl.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        VideoPlayerView(
                            videoUrl = streamUrl,
                            title = movie.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            onClose = { activePlayingMovie = null }
                        )
                        Surface(
                            color = CinemaSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = movie.title,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "VLC প্লেয়ারে চলছে • ${movie.genre ?: "বাংলা সিনেমা"}",
                                        color = CyanAccent,
                                        fontSize = 11.sp
                                    )
                                }
                                Button(
                                    onClick = { onSelectMovie(movie.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("বিস্তারিত (Details)", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Search & Filter header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        currentPage = 1
                    },
                    placeholder = { Text("মুভি, নাটক বা অভিনেতার নাম খুঁজুন...", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; currentPage = 1 }) {
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

                // Filter Button
                Surface(
                    onClick = { showFilterSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedCategory != null || selectedYear != null || selectedGenre != null) AuthBrandPrimary else CinemaSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedCategory != null || selectedYear != null || selectedGenre != null) AuthBrandPrimary else CinemaBorder),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Category Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChipItem(
                        label = "🔥 বাংলা ওটিটি (OTT)",
                        selected = isBongoSelected,
                        onClick = {
                            isBongoSelected = true
                            selectedCategory = null
                            currentPage = 1
                        }
                    )
                }
                item {
                    FilterChipItem(
                        label = "English",
                        selected = !isBongoSelected && (selectedCategory == null || selectedCategory?.id == 1),
                        onClick = {
                            isBongoSelected = false
                            selectedCategory = CtgCategoryItem(1, "English Movies", "MOVIE")
                            currentPage = 1
                        }
                    )
                }
                item {
                    FilterChipItem(
                        label = "Bollywood",
                        selected = !isBongoSelected && selectedCategory?.id == 4,
                        onClick = {
                            isBongoSelected = false
                            selectedCategory = CtgCategoryItem(4, "Bollywood Movies", "MOVIE")
                            currentPage = 1
                        }
                    )
                }
                item {
                    FilterChipItem(
                        label = "Bangla",
                        selected = !isBongoSelected && selectedCategory?.id == 6,
                        onClick = {
                            isBongoSelected = false
                            selectedCategory = CtgCategoryItem(6, "Bangla Movies", "MOVIE")
                            currentPage = 1
                        }
                    )
                }
                item {
                    FilterChipItem(
                        label = "South Indian",
                        selected = !isBongoSelected && selectedCategory?.id == 7,
                        onClick = {
                            isBongoSelected = false
                            selectedCategory = CtgCategoryItem(7, "South Indian Movies", "MOVIE")
                            currentPage = 1
                        }
                    )
                }
                item {
                    FilterChipItem(
                        label = "Anime & Asian",
                        selected = !isBongoSelected && selectedCategory?.id == 5,
                        onClick = {
                            isBongoSelected = false
                            selectedCategory = CtgCategoryItem(5, "Asian & Anime", "MOVIE")
                            currentPage = 1
                        }
                    )
                }
            }

            // OTT Sub-Category Chips (shown when OTT is selected)
            if (isBongoSelected) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val ottSubGenres = listOf("সব (All)", "সিনেমা", "নাটক", "ওয়েব সিরিজ", "কমেডি", "থ্রিলার", "রোমান্স", "অ্যাকশন")
                    ottSubGenres.forEach { subGenre ->
                        val isSelected = selectedBongoGenre == subGenre
                        Surface(
                            onClick = {
                                selectedBongoGenre = subGenre
                                currentPage = 1
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) CyanAccent else CinemaSurfaceVariant,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = subGenre,
                                color = if (isSelected) Color.Black else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Active Filter Banner
        if (selectedYear != null || selectedGenre != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ফিল্টার সক্রিয়:", color = TextMuted, fontSize = 11.sp)
                selectedYear?.let { y ->
                    Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "Year: $y",
                            color = GoldRating,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                selectedGenre?.let { g ->
                    Surface(color = CinemaSurfaceVariant, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "Genre: $g",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Movies Grid & Content
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandRed)
            }
        } else if (movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MovieFilter, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("কোনো মুভি পাওয়া যায়নি", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(movies, key = { it.id }) { movie ->
                    MoviePosterCard(
                        movie = movie,
                        onClick = {
                            if (isBongoSelected) {
                                // Instantly start VLC playback in top banner or open detail
                                activePlayingMovie = movie
                            } else {
                                onSelectMovie(movie.id)
                            }
                        },
                        width = 115,
                        height = 170
                    )
                }

                // Pagination Row
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentPage > 1) {
                            OutlinedButton(
                                onClick = { currentPage -= 1 },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("আগের পেজ", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = "পেজ $currentPage / $totalPages",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentPage < totalPages) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { currentPage += 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("পরের পেজ", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (showFilterSheet) {
        FilterBottomSheet(
            menusData = menusData,
            selectedCategory = selectedCategory,
            selectedYear = selectedYear,
            selectedGenre = selectedGenre,
            selectedSort = selectedSort,
            onCategoryChange = { selectedCategory = it },
            onYearChange = { selectedYear = it },
            onGenreChange = { selectedGenre = it },
            onSortChange = { selectedSort = it },
            onApply = { currentPage = 1 },
            onReset = {
                selectedCategory = null
                selectedYear = null
                selectedGenre = null
                selectedSort = "createdAt"
                currentPage = 1
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}
