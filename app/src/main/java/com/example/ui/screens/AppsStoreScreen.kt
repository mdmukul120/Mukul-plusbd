package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.download.DownloadStatus
import com.example.data.download.InAppDownloader
import com.example.data.repository.AppItem
import com.example.data.repository.AppStoreRepository
import com.example.data.util.ApkInstallerHelper
import com.example.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsStoreScreen(
    onOpenAdminPanel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apps by AppStoreRepository.apps.collectAsState()
    val allTasks by InAppDownloader.tasks.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("সব") }
    var showAdminAuthDialog by remember { mutableStateOf(false) }
    var adminPasswordInput by remember { mutableStateOf("") }
    var adminAuthError by remember { mutableStateOf(false) }

    val categories = listOf("সব", "ভিডিও প্লেয়ার", "স্পোর্টস", "লাইভ টিভি", "টুলস ও ডাউনলোডার", "ব্রাউজার")

    val filteredApps = remember(apps, searchQuery, selectedCategory) {
        apps.filter { app ->
            val matchesCategory = if (selectedCategory == "সব") true else app.category.contains(selectedCategory, ignoreCase = true)
            val matchesQuery = if (searchQuery.isBlank()) true else {
                app.name.contains(searchQuery, ignoreCase = true) ||
                    app.description.contains(searchQuery, ignoreCase = true) ||
                    app.category.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesQuery
        }
    }

    LaunchedEffect(Unit) {
        AppStoreRepository.init(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        // App Store Top Header
        Surface(
            color = CinemaSurface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = BrandRed.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Mukul Plus অ্যাপস",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "আমাদের তৈরি অফিসিয়াল অ্যাপ্লিকেশন",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Admin Panel Button
                    FilledTonalButton(
                        onClick = { showAdminAuthDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CinemaSurfaceVariant,
                            contentColor = CyanAccent
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("অ্যাডমিন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("অ্যাপ খুঁজুন...", fontSize = 12.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CinemaSurfaceVariant,
                        unfocusedContainerColor = CinemaSurfaceVariant,
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CinemaBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category Chips (Horizontal Scroll)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) BrandRed else CinemaBorder),
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Install Permission Banner (if not granted on Android 8+)
        if (!ApkInstallerHelper.canInstallPackages(context)) {
            Surface(
                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "অ্যাপ ইনস্টল করতে পারমিশন দিন",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Button(
                        onClick = { ApkInstallerHelper.openInstallPermissionSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("অনুমতি দিন", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Apps List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredApps, key = { it.id }) { app ->
                AppCardItem(
                    app = app,
                    downloadTasks = allTasks,
                    onDownloadClick = { targetApp ->
                        if (targetApp.downloadUrl.isBlank()) {
                            Toast.makeText(context, "ডাউনলোড লিঙ্ক পাওয়া যায়নি!", Toast.LENGTH_SHORT).show()
                            return@AppCardItem
                        }
                        InAppDownloader.startDownload(
                            context = context,
                            movieSlug = targetApp.id,
                            title = targetApp.name,
                            poster = targetApp.iconUrl,
                            quality = "APK",
                            downloadUrl = targetApp.downloadUrl
                        )
                        Toast.makeText(context, "${targetApp.name} ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    onInstallClick = { targetApp, apkFile ->
                        ApkInstallerHelper.installApk(context, apkFile)
                    }
                )
            }
        }
    }

    // Admin Auth Dialog
    if (showAdminAuthDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminAuthDialog = false
                adminPasswordInput = ""
                adminAuthError = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("অ্যাডমিন প্যানেল প্রবেশ", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("অ্যাডমিন পিন / পাসওয়ার্ড প্রবেশ করান (ডিফল্ট: admin123)", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adminPasswordInput,
                        onValueChange = {
                            adminPasswordInput = it
                            adminAuthError = false
                        },
                        label = { Text("পাসওয়ার্ড") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = adminAuthError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (adminAuthError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("ভুল পাসওয়ার্ড! আবার চেষ্টা করুন।", color = BrandRed, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (adminPasswordInput == "admin123" || adminPasswordInput == "1234" || adminPasswordInput.trim().lowercase() == "mukul") {
                            showAdminAuthDialog = false
                            adminPasswordInput = ""
                            adminAuthError = false
                            onOpenAdminPanel()
                        } else {
                            adminAuthError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) {
                    Text("প্রবেশ করুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAdminAuthDialog = false
                    adminPasswordInput = ""
                    adminAuthError = false
                }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
fun AppCardItem(
    app: AppItem,
    downloadTasks: Map<String, com.example.data.download.DownloadTask>,
    onDownloadClick: (AppItem) -> Unit,
    onInstallClick: (AppItem, File) -> Unit
) {
    val context = LocalContext.current
    val activeTask = downloadTasks.values.firstOrNull {
        it.movieSlug == app.id && (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED)
    }

    val completedTask = InAppDownloader.getCompletedMovie(app.id)
    val downloadedApkFile = completedTask?.filePath?.let { File(it) }?.takeIf { it.exists() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // App Icon
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(app.iconUrl.ifEmpty { "https://i.pinimg.com/736x/73/b3/8e/73b38e78e189145a59d422e8b8bd37d4.jpg" })
                        .crossfade(true)
                        .build(),
                    contentDescription = app.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CinemaSurfaceVariant)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // App Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = app.name,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = BrandRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = app.version,
                                color = BrandRedLight,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Mukul Plus Official",
                        color = CyanAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(text = app.rating.toString(), color = TextPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(text = "•", color = TextMuted, fontSize = 10.sp)
                        Text(text = app.size, color = TextSecondary, fontSize = 10.5.sp)
                        Text(text = "•", color = TextMuted, fontSize = 10.sp)
                        Text(text = "${app.downloadsCount} ডাউনলোড", color = TextSecondary, fontSize = 10.5.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = app.description,
                color = TextSecondary,
                fontSize = 11.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action / Download / Progress Row
            if (activeTask != null) {
                // Live Download Progress Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ডাউনলোড হচ্ছে: ${activeTask.progressPercent}%",
                            color = BrandRedLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activeTask.speedText.isNotEmpty()) {
                                Text(
                                    text = activeTask.speedText,
                                    color = CyanAccent,
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            TextButton(
                                onClick = { InAppDownloader.cancelDownload(activeTask.id) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("বাতিল", color = BrandRed, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { activeTask.progressPercent / 100f },
                        color = BrandRed,
                        trackColor = CinemaSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            } else if (downloadedApkFile != null) {
                // Downloaded -> Ready to install
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ডাউনলোড সম্পন্ন", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onInstallClick(app, downloadedApkFile) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ইন্সটল করুন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Ready to download
                Button(
                    onClick = { onDownloadClick(app) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ডাউনলোড ও ইন্সটল (${app.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
