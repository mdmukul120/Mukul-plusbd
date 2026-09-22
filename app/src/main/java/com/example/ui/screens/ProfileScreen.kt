package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.ApiClient
import com.example.data.model.CtgMovie
import com.example.data.repository.AuthRepository
import com.example.data.repository.MediaRepository
import com.example.data.util.LanguageManager
import com.example.ui.components.MoviePosterCard
import com.example.ui.theme.*

@Composable
fun ProfileScreen(
    authRepository: AuthRepository,
    mediaRepository: MediaRepository,
    onSelectMovie: (Long) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUser by authRepository.currentUser.collectAsState()
    val favorites by mediaRepository.favorites.collectAsState()
    var favoriteMovies by remember { mutableStateOf<List<CtgMovie>>(emptyList()) }
    var isLoadingFavs by remember { mutableStateOf(false) }

    LaunchedEffect(favorites) {
        if (favorites.isNotEmpty()) {
            isLoadingFavs = true
            try {
                val list = mutableListOf<CtgMovie>()
                favorites.take(15).forEach { id ->
                    val movie = ApiClient.fetchCtgMovieDetail(id)
                    if (movie != null) list.add(movie)
                }
                favoriteMovies = list
            } catch (_: Exception) {
            } finally {
                isLoadingFavs = false
            }
        } else {
            favoriteMovies = emptyList()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(AuthBrandGradientStart, AuthBrandGradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentUser?.displayName ?: "User",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Text(
                        text = if (currentUser?.isGuest == true) "গেস্ট মেম্বার (Guest Account)" else (currentUser?.email ?: "প্রিমিয়াম ইউজার"),
                        color = AuthBrandPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${favorites.size}", color = AuthBrandPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Text(text = "ওয়াচলিস্ট", color = TextSecondary, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "10", color = Color(0xFFFFB020), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Text(text = "ভাষা সাপোর্ট", color = TextSecondary, fontSize = 11.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "v1.2", color = Color(0xFF10B981), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                            Text(text = "অটো-আপডেট", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Watchlist Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "আমার ওয়াচলিস্ট (${favorites.size})",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isLoadingFavs) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandRed, strokeWidth = 2.dp)
                }
            }
        } else if (favoriteMovies.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "কোনো মুভি ওয়াচলিস্টে যুক্ত করা হয়নি",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            items(favoriteMovies) { movie ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectMovie(movie.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = BrandRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = movie.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                text = "Year: ${movie.year ?: "N/A"} • Rating: ⭐ ${movie.online_rating ?: "8.5"}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { mediaRepository.toggleFavorite(movie.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = BrandRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Account Settings & Sign Out
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onLogout,
                colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("লগআউট করুন (Sign Out)", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
