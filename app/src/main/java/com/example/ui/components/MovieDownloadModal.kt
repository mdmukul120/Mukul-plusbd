package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.DownloadLink
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDownloadModal(
    title: String,
    poster: String,
    downloadLinks: List<DownloadLink>,
    fallbackStreamUrl: String? = null,
    onDismiss: () -> Unit,
    onDownloadUrl: (url: String, fileName: String) -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("All") } // "All", "1080p", "720p", "480p", "Cloud"

    val filters = listOf("All" to "সব কোয়ালিটি", "1080p" to "1080p Full HD", "720p" to "720p HD", "480p" to "480p SD", "Cloud" to "ক্লাউড মিরর")

    val allLinks = remember(downloadLinks, fallbackStreamUrl) {
        val list = mutableListOf<DownloadLink>()
        if (downloadLinks.isNotEmpty()) {
            list.addAll(downloadLinks)
        }
        if (list.isEmpty() && !fallbackStreamUrl.isNullOrEmpty()) {
            list.add(
                DownloadLink(
                    title = "$title (Direct Web-DL)",
                    link = fallbackStreamUrl,
                    quality = "1080p Full HD",
                    type = "Direct Server"
                )
            )
            list.add(
                DownloadLink(
                    title = "$title (Fast Cloud Mirror)",
                    link = fallbackStreamUrl,
                    quality = "720p HD",
                    type = "Fast Cloud"
                )
            )
        }
        list
    }

    val filteredLinks = remember(selectedFilter, allLinks) {
        when (selectedFilter) {
            "1080p" -> allLinks.filter { it.quality?.contains("1080", ignoreCase = true) == true || it.title.contains("1080", ignoreCase = true) }
            "720p" -> allLinks.filter { it.quality?.contains("720", ignoreCase = true) == true || it.title.contains("720", ignoreCase = true) }
            "480p" -> allLinks.filter { it.quality?.contains("480", ignoreCase = true) == true || it.title.contains("480", ignoreCase = true) }
            "Cloud" -> allLinks.filter { it.type?.contains("Cloud", ignoreCase = true) == true || it.title.contains("Cloud", ignoreCase = true) || it.link.contains("drive") || it.link.contains("hub") }
            else -> allLinks
        }.ifEmpty { allLinks }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            containerColor = CinemaBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "ডাউনলোড কেন্দ্র (Download Center)",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = title,
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CinemaSurface)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Header Banner
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (poster.isNotEmpty()) {
                                AsyncImage(
                                    model = poster,
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 65.dp, height = 95.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ডাউনলোড ফাইল ও হাই-স্পিড মিরর লিংক",
                                    color = CyanAccent,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${allLinks.size} টি ডাউনলোড লিংক প্রস্তুত",
                                        color = Color(0xFF00E676),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Filter Chips
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filters) { (key, label) ->
                            FilterChip(
                                selected = selectedFilter == key,
                                onClick = { selectedFilter = key },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                                    selected = selectedFilter == key
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Download Links List
                if (filteredLinks.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = GoldRating)
                                Text(
                                    text = "এই ফিল্টারে কোন ডাউনলোড লিংক পাওয়া যায়নি।",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                } else {
                    items(filteredLinks) { dl ->
                        val qualityLabel = dl.quality ?: "Full HD"
                        val is1080p = qualityLabel.contains("1080", ignoreCase = true)
                        val badgeColor = if (is1080p) AuthBrandPrimary else Color(0xFF00E676)
                        val estimatedSize = if (is1080p) "2.1 GB" else if (qualityLabel.contains("720")) "1.1 GB" else "480 MB"

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                            border = BorderStroke(1.dp, CinemaBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Surface(
                                        color = badgeColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = qualityLabel,
                                            color = badgeColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    Text(
                                        text = "আনুমানিক সাইজ: $estimatedSize",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = dl.title,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Primary Download Button
                                    Button(
                                        onClick = {
                                            val fileName = "${title}_${qualityLabel}.mp4".replace(" ", "_")
                                            onDownloadUrl(dl.link, fileName)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1.5f).height(40.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ডাউনলোড শুরু করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Copy Link Button
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Download Link", dl.link)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "ডাউনলোড লিংক কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        border = BorderStroke(1.dp, CinemaBorder),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("কপি", fontSize = 11.sp)
                                    }

                                    // Open in browser
                                    IconButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(dl.link))
                                                context.startActivity(intent)
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Browser", tint = CyanAccent)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}
