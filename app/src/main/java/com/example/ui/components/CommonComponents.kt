package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.CtgMovie
import com.example.ui.theme.*

@Composable
fun MukulPlusLogo(
    modifier: Modifier = Modifier,
    iconSize: Int = 36,
    textSize: Int = 20,
    textColor: Color = TextPrimary
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(iconSize.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(AuthBrandGradientStart, AuthBrandGradientEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Mukul plus Logo",
                tint = Color.White,
                modifier = Modifier.size((iconSize * 0.7f).dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MUKUL",
                color = textColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = textSize.sp,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                color = AuthBrandPrimary,
                shape = RoundedCornerShape(5.dp),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = "PLUS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = (textSize * 0.55f).sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun MoviePosterCard(
    movie: CtgMovie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 130,
    height: Int = 195
) {
    Card(
        modifier = modifier
            .width(width.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height.dp)
                    .background(CinemaSurfaceVariant)
            ) {
                val posterUrl = movie.getFullPosterUrl()
                if (posterUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(posterUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(CinemaSurfaceVariant, CinemaBorder)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Top rating or quality badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val rating = movie.online_rating ?: movie.user_rating
                    if (rating != null && rating > 0.0) {
                        Surface(
                            color = Color(0xCC000000),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = GoldRating,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = String.format("%.1f", rating),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (movie.year != null && movie.year > 0) {
                        Surface(
                            color = Color(0xAA000000),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = movie.year.toString(),
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Title & category
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = movie.Library?.name ?: movie.genre ?: "Movie",
                    color = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (selected) BrandRed else CinemaSurfaceVariant,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
