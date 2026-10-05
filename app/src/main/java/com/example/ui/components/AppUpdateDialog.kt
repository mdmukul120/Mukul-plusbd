package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppUpdateInfo
import com.example.data.model.UpdateDownloadProgress
import com.example.data.util.AppUpdateManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val downloadProgress by AppUpdateManager.downloadProgress.collectAsState()
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var needsInstallPermission by remember { mutableStateOf(!AppUpdateManager.canRequestPackageInstalls(context)) }

    // Pulsing icon animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val (installedVersionName, _) = remember { AppUpdateManager.getInstalledVersion(context) }

    Dialog(
        onDismissRequest = {
            if (!downloadProgress.isDownloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !downloadProgress.isDownloading,
            dismissOnClickOutside = !downloadProgress.isDownloading,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CinemaSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
            tonalElevation = 8.dp,
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with pulsing icon and badge
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AuthBrandPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Update",
                                tint = AuthBrandPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Title
                Text(
                    text = "নতুন আপডেট উপলব্ধ!",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Mukul plus ${updateInfo.tagName} পাওয়া গেছে",
                    color = CyanAccent,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Version Comparison Row: Current vs New
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CinemaSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("বর্তমান সংস্করণ", color = TextMuted, fontSize = 10.sp)
                        Text(
                            text = "v$installedVersionName",
                            color = TextSecondary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = AuthBrandPrimary,
                        modifier = Modifier.size(16.dp)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text("নতুন সংস্করণ", color = TextMuted, fontSize = 10.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = BrandRed
                            ) {
                                Text(
                                    text = "LATEST",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = updateInfo.tagName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // File size & date banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "সাইজ: ${updateInfo.getFormattedFileSize()}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "গিটহাব রিলিজ • দ্রুত ডাউনলোড",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Release Notes Card (Scrollable)
                Text(
                    text = "📋 আপডেটের বিবরণ ও নতুন ফিচার:",
                    color = TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CinemaBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(10.dp)
                    ) {
                        val cleanNotes = formatReleaseNotes(updateInfo.releaseNotes)
                        Text(
                            text = cleanNotes,
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ------------------------------------------------------------
                // Download Progress or Status Box
                // ------------------------------------------------------------
                if (downloadProgress.isDownloading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CinemaSurfaceVariant, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("অ্যাপ্লিকেশন ডাউনলোড হচ্ছে...", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            Text(
                                text = "${(downloadProgress.progress * 100).toInt()}%",
                                color = AuthBrandPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { downloadProgress.progress },
                            color = AuthBrandPrimary,
                            trackColor = CinemaBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val downloadedMb = downloadProgress.bytesDownloaded / (1024.0 * 1024.0)
                        val totalMb = downloadProgress.totalBytes / (1024.0 * 1024.0)
                        Text(
                            text = String.format("%.1f MB / %.1f MB", downloadedMb, if (totalMb > 0) totalMb else 28.9),
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                } else if (downloadProgress.isCompleted && downloadedFile != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ডাউনলোড সম্পন্ন! ইনস্টলেশন প্রক্রিয়া শুরু হচ্ছে...",
                                color = Color(0xFF10B981),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else if (downloadProgress.error != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = BrandRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = downloadProgress.error ?: "ডাউনলোড ব্যর্থ হয়েছে",
                                color = BrandRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Unknown source permission notice if needed
                if (needsInstallPermission && (downloadProgress.isCompleted || downloadedFile != null)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFB020).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB020).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "⚠️ অ্যাপ আপডেট ইনস্টল করতে পারমিশন প্রয়োজন:",
                                color = Color(0xFFFFB020),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "সেটিংসে গিয়ে 'Install Unknown Apps' অপশনে এই অ্যাপের পারমিশন চালু করুন।",
                                color = TextSecondary,
                                fontSize = 10.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    AppUpdateManager.openInstallPermissionSettings(context)
                                    needsInstallPermission = false
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("পারমিশন সেটিংস খুলুন", fontSize = 11.sp, color = Color(0xFFFFB020))
                            }
                        }
                    }
                }

                // ------------------------------------------------------------
                // Action Buttons
                // ------------------------------------------------------------
                if (downloadProgress.isCompleted && downloadedFile != null) {
                    // Ready to Install button
                    Button(
                        onClick = {
                            val success = AppUpdateManager.installApk(context, downloadedFile!!)
                            if (!success) {
                                needsInstallPermission = true
                                Toast.makeText(context, "ইনস্টলার শুরু করা সম্ভব হয়নি। পারমিশন দিন।", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("এখনই ইনস্টল করুন (Install Now)", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (!downloadProgress.isDownloading) {
                    // Primary Download & Update Button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val file = AppUpdateManager.downloadApk(context, updateInfo)
                                if (file != null && file.exists()) {
                                    downloadedFile = file
                                    needsInstallPermission = !AppUpdateManager.canRequestPackageInstalls(context)
                                    // Automatically trigger install
                                    val installed = AppUpdateManager.installApk(context, file)
                                    if (!installed && needsInstallPermission) {
                                        AppUpdateManager.openInstallPermissionSettings(context)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ইন-অ্যাপ আপডেট শুরু করুন", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row: Dismiss / Later + GitHub Releases Browser Link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Later Button
                    TextButton(
                        onClick = {
                            AppUpdateManager.dismissUpdateTag(context, updateInfo.tagName)
                            onDismiss()
                        },
                        enabled = !downloadProgress.isDownloading,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("পরে মনে করিয়ে দিন", color = TextMuted, fontSize = 12.sp)
                    }

                    // Direct Browser Link to GitHub Releases
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.htmlUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "ব্রাউজার খোলা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GitHub রিলিজ", color = CyanAccent, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Format markdown release notes into clean displayable text
 */
private fun formatReleaseNotes(notes: String): String {
    if (notes.isBlank()) {
        return "• পারফরম্যান্স উন্নত করা হয়েছে\n• লাইভ টিভি ও ভিডিও স্ট্রিমিং আপডেট\n• বাগ ফিক্স এবং স্টেবিলিটি বৃদ্ধি"
    }

    return notes
        .replace(Regex("##+ *"), "📌 ")
        .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1") // Strip markdown links to plain text
        .replace("**", "")
        .replace("`", "")
        .trim()
}
