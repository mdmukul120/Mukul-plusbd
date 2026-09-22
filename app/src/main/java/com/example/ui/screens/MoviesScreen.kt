package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
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
import com.example.ui.theme.*

@Composable
fun MoviesScreen(
    mediaRepository: MediaRepository,
    onSelectMovie: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var movies by remember { mutableStateOf<List<CtgMovie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalPages by remember { mutableIntStateOf(1) }
    var searchQuery by remember { mutableStateOf("") }

    // Filter states
    val menusData by mediaRepository.menusData.collectAsState()
    var selectedCategory by remember { mutableStateOf<CtgCategoryItem?>(null) }
    var isBongoSelected by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var selectedSort by remember { mutableStateOf("createdAt") }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Initial load menus
    LaunchedEffect(Unit) {
        mediaRepository.getMenus()
    }

    LaunchedEffect(selectedCategory, isBongoSelected, selectedYear, selectedGenre, selectedSort, currentPage, searchQuery) {
        isLoading = true
        if (isBongoSelected) {
            val allBongo = mediaRepository.getBongoVideos()
            movies = if (searchQuery.isNotBlank()) {
                allBongo.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.casts?.contains(searchQuery, ignoreCase = true) == true) ||
                    (it.genre?.contains(searchQuery, ignoreCase = true) == true)
                }
            } else {
                allBongo
            }
            totalPages = 1
        } else {
            val res = ApiClient.fetchCtgMovies(
                library = selectedCategory?.id ?: 1,
                page = currentPage,
                sort = selectedSort,
                sortOrder = "DESC",
                search = searchQuery.ifBlank { null },
                year = selectedYear,
                genre = selectedGenre
            )
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
        // Search & Filter header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        currentPage = 1
                    },
                    placeholder = { Text("মুভি খুঁজুন (Search Movies)", color = TextMuted, fontSize = 12.sp) },
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
                    modifier = Modifier.size(50.dp)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Category Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChipItem(
                        label = "🔥 Bongo BD (বঙ্গ)",
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
        }

        // Active Filter Banner if any
        if (selectedYear != null || selectedGenre != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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

        // Movies Grid
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BrandRed)
            }
        } else if (movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো মুভি পাওয়া যায়নি", color = TextSecondary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(movies) { movie ->
                    MoviePosterCard(
                        movie = movie,
                        onClick = { onSelectMovie(movie.id) },
                        width = 115,
                        height = 170
                    )
                }

                // Pagination
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
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("Previous")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = "Page $currentPage / $totalPages",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentPage < totalPages) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { currentPage += 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                            ) {
                                Text("Next")
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
