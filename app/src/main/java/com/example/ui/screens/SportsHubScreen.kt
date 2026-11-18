package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.data.model.SportsMatch
import com.example.data.model.SportsMatchStatus
import com.example.data.model.SportsStream
import com.example.data.repository.SportsHubRepository
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SportsHubScreen — Mukul plus-এর নিজস্ব স্পোর্টস হাব।
 *
 * তিনটি ধাপ: তালিকা → ম্যাচ ডিটেইল/সার্ভার সিলেক্ট → ইন-অ্যাপ প্লেয়ার।
 * সব ডেটা আসে [SportsHubRepository] থেকে, অর্থাৎ ব্যবহারকারীর নিজের কনফিগার করা ফিড থেকে।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportsHubScreen(
    repository: SportsHubRepository,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val matches by repository.matches.collectAsState()
    val isLoading by repository.isLoading.collectAsState()
    val errors by repository.errors.collectAsState()

    var selected by remember { mutableStateOf<SportsMatch?>(null) }
    var playing by remember { mutableStateOf<Pair<SportsMatch, SportsStream>?>(null) }
    var filter by remember { mutableStateOf(SportsFilter.ALL) }
    var showSettings by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(repository.hasConfiguredSource()) }

    LaunchedEffect(configured) {
        if (configured) repository.refresh()
    }

    // -------------------------------------------------------------- player view
    val activePlayback = playing
    if (activePlayback != null) {
        val (match, stream) = activePlayback
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            VideoPlayerView(
                videoUrl = stream.url,
                title = if (match.isLive) "🔴 LIVE • ${match.title}" else match.title,
                modifier = Modifier.fillMaxSize(),
                onClose = { playing = null }
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { playing = null }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("ফিরে যান", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {

        // ------------------------------------------------------- match detail sheet
        selected?.let { match ->
            MatchDetailSheet(
                match = match,
                onDismiss = { selected = null },
                onPlay = { stream ->
                    if (stream.isEmbed) {
                        openExternally(context, stream.url)
                    } else {
                        playing = match to stream
                    }
                    selected = null
                }
            )
        }

        Column(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
            // ------------------------------------------------------------- top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CinemaSurface)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "ফিরে যান", tint = TextPrimary)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "স্পোর্টস হাব",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "ক্রিকেট • ফুটবল • লাইভ স্ট্রিম",
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Tune, "সোর্স সেটিংস", tint = CyanAccent)
                }
                IconButton(onClick = {
                    configured = repository.hasConfiguredSource()
                    scope.launch { repository.refresh(force = true) }
                }) {
                    Icon(Icons.Default.Refresh, "রিফ্রেশ", tint = TextSecondary)
                }
            }

            // -------------------------------------------------------- empty / states
            when {
                !configured -> EmptyState(
                    title = "কোনো সোর্স এখনো যোগ করা হয়নি",
                    message = "নিজের স্পোর্টস ফিডের URL যোগ করলেই এখানে লাইভ ম্যাচ, " +
                        "স্কোর ও স্ট্রিম দেখা যাবে। ডেমো ডেটা দেখানো হয় না।",
                    actionLabel = "সোর্স যোগ করুন",
                    onAction = { showSettings = true }
                )

                isLoading && matches.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = BrandRed) }

                matches.isEmpty() -> EmptyState(
                    title = "এই মুহূর্তে কোনো ম্যাচ নেই",
                    message = if (errors.isEmpty()) "আপনার ফিডে এখন কোনো এন্ট্রি নেই।" 
                    else errors.joinToString("\n"),
                    actionLabel = "আবার চেষ্টা করুন",
                    onAction = { scope.launch { repository.refresh(force = true) } }
                )

                else -> {
                    val visible = remember(matches, filter) {
                        when (filter) {
                            SportsFilter.ALL -> matches
                            SportsFilter.LIVE -> matches.filter { it.status == SportsMatchStatus.LIVE }
                            SportsFilter.UPCOMING -> matches.filter { it.status == SportsMatchStatus.UPCOMING }
                            SportsFilter.REPLAY -> matches.filter {
                                it.status == SportsMatchStatus.REPLAY || it.status == SportsMatchStatus.FINISHED
                            }
                        }
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CinemaSurface)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SportsFilter.values().toList()) { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = {
                                    Text(
                                        f.label,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (filter == f) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandRed.copy(alpha = 0.18f),
                                    selectedLabelColor = BrandRed,
                                    labelColor = TextSecondary
                                )
                            )
                        }
                    }

                    if (errors.isNotEmpty()) {
                        Text(
                            "⚠️ ${errors.joinToString("; ")}",
                            color = GoldRating,
                            fontSize = 10.5.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visible, key = { it.id }) { match ->
                            MatchCard(match) { selected = match }
                        }
                    }
                }
            }
        }

    }

    if (showSettings) {
        SourceSettingsDialog(
            repository = repository,
            onDismiss = {
                showSettings = false
                configured = repository.hasConfiguredSource()
            }
        )
    }
}

private enum class SportsFilter(val label: String) {
    ALL("সব"), LIVE("🔴 লাইভ"), UPCOMING("আসন্ন"), REPLAY("রিপ্লে")
}

// --------------------------------------------------------------------- widgets

@Composable
private fun MatchCard(match: SportsMatch, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CinemaSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(CinemaSurfaceVariant, CinemaSurface)
                        )
                    )
            ) {
                if (match.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = match.posterUrl,
                        contentDescription = match.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                StatusBadge(match.status, Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    match.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (match.league.isNotBlank()) {
                    Text(
                        match.league,
                        color = TextMuted,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (match.streams.isEmpty()) "স্ট্রিম নেই" 
                        else "${match.streams.size}টি সার্ভার",
                        color = if (match.streams.isEmpty()) TextMuted else GreenSuccess,
                        fontSize = 9.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (match.startTimeEpochMs > 0) {
                        Text(
                            formatStart(match.startTimeEpochMs),
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: SportsMatchStatus, modifier: Modifier = Modifier) {
    val (text, bg) = when (status) {
        SportsMatchStatus.LIVE -> "LIVE" to Color(0xFFE11D48)
        SportsMatchStatus.UPCOMING -> "আসন্ন" to Color(0xFF2563EB)
        SportsMatchStatus.REPLAY -> "রিপ্লে" to Color(0xFF7C3AED)
        SportsMatchStatus.FINISHED -> "শেষ" to Color(0xFF4B5563)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchDetailSheet(
    match: SportsMatch,
    onDismiss: () -> Unit,
    onPlay: (SportsStream) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurface
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(match.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    match.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (match.league.isNotBlank() || match.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    listOf(match.league, match.subtitle).filter { it.isNotBlank() }.joinToString(" • "),
                    color = TextMuted,
                    fontSize = 11.5.sp
                )
            }
            if (match.teamA.isNotBlank() || match.teamB.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamChip(match.teamA.ifBlank { "—" }, Modifier.weight(1f, fill = false))
                    Text("  vs  ", color = TextMuted, fontSize = 12.sp)
                    TeamChip(match.teamB.ifBlank { "—" }, Modifier.weight(1f, fill = false))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("স্ট্রিম সার্ভার", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (match.streams.isEmpty()) {
                Text(
                    "এই ম্যাচের জন্য কোনো স্ট্রিম লিংক দেওয়া হয়নি।",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )
            } else {
                match.streams.forEach { stream ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CinemaSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onPlay(stream) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (stream.isEmbed) Icons.Default.OpenInNew else Icons.Default.PlayCircle,
                                null,
                                tint = BrandRed
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stream.label, color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Text(stream.quality, color = TextMuted, fontSize = 10.sp)
                            }
                            Text(
                                if (stream.isEmbed) "ব্রাউজারে" else "প্লে",
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun TeamChip(name: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CinemaSurfaceVariant,
        modifier = modifier
    ) {
        Text(
            name,
            color = TextSecondary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = BrandRed.copy(alpha = 0.12f), modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SportsCricket, null, tint = BrandRed, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(message, color = TextMuted, fontSize = 11.5.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
            ) { Text(actionLabel, fontSize = 12.5.sp) }
        }
    }
}

@Composable
private fun SourceSettingsDialog(
    repository: SportsHubRepository,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(repository.feedUrl) }
    var enabled by remember { mutableStateOf(repository.isHubEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurface,
        title = {
            Text("স্পোর্টস ফিড সোর্স", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "নিজের JSON ফিডের URL দিন। ফরম্যাট:\n" +
                        "[{\"title\":\"…\",\"status\":\"LIVE\",\"streams\":[{\"label\":\"HD\",\"url\":\"…\"}]}]",
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("ফিড URL", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CinemaBorder,
                        focusedLabelColor = BrandRed,
                        unfocusedLabelColor = TextMuted
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = BrandRed)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("স্পোর্টস হাব চালু", color = TextSecondary, fontSize = 11.5.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                repository.feedUrl = url
                repository.isHubEnabled = enabled
                onDismiss()
            }) { Text("সেভ করুন", color = BrandRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল", color = TextMuted) }
        }
    )
}

// --------------------------------------------------------------------- helpers

private fun formatStart(epochMs: Long): String = try {
    SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(epochMs))
} catch (_: Exception) {
    ""
}

private fun openExternally(context: android.content.Context, url: String) {
    try {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}
