package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import com.example.data.model.CtgMovie
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HeroSlider(
    movies: List<CtgMovie>,
    onMovieClick: (CtgMovie) -> Unit,
    onWatchlistToggle: (CtgMovie) -> Unit,
    isFavorite: (Long) -> Boolean,
    modifier: Modifier = Modifier
) {
    if (movies.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(CinemaSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(36.dp))
        }
        return
    }

    val featuredMovies = remember(movies) { movies.take(6) }
    val pagerState = rememberPagerState(pageCount = { featuredMovies.size })

    // Auto advance carousel
    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000)
            if (featuredMovies.isNotEmpty()) {
                val nextPage = (pagerState.currentPage + 1) % featuredMovies.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
        ) { page ->
            val movie = featuredMovies[page]
            val favorite = isFavorite(movie.id)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onMovieClick(movie) }
            ) {
                // Background Poster / Backdrop
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(movie.getFullBackdropUrl())
                        .crossfade(true)
                        .build(),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark cinematic gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x22000000),
                                    Color(0x88090C15),
                                    CinemaBackground
                                )
                            )
                        )
                )

                // Content info at bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Top tags row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = BrandRed,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "FEATURED",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        val rating = movie.online_rating ?: movie.user_rating
                        if (rating != null && rating > 0.0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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

                        if (movie.year != null) {
                            Text(
                                text = "• ${movie.year}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        val genreName = movie.Library?.name ?: movie.genre
                        if (!genreName.isNullOrEmpty()) {
                            Text(
                                text = "• $genreName",
                                color = CyanAccent,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Buttons row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onMovieClick(movie) },
                            colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("এখন দেখুন", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { onWatchlistToggle(movie) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (favorite) GoldRating else Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (favorite) GoldRating else Color(0x66FFFFFF)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (favorite) Icons.Default.Check else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (favorite) "লিস্টে আছে" else "আমার লিস্ট",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Pager indicators (dots)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(featuredMovies.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(if (isSelected) 18.dp else 6.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) BrandRed else TextMuted.copy(alpha = 0.5f))
                )
            }
        }
    }
}
