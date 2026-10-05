package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppUpdateInfo
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

    val downloadProgress by AppUpdateManager.downloadProgress.collectAsState()
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var needsInstallPermission by remember { mutableStateOf(!AppUpdateManager.canRequestPackageInstalls(context)) }

    val (installedVersionName, _) = remember { AppUpdateManager.getInstalledVersion(context) }
    val isMatched = updateInfo.isCurrentVersionMatched

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
            shape = RoundedCornerShape(22.dp),
            color = CinemaSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
            tonalElevation = 8.dp,
            modifier = modifier
                .fillMaxWidth(0.90f)
                .wrapContentHeight()
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Surface(
                    shape = CircleShape,
                    color = if (isMatched) Color(0xFF10B981).copy(alpha = 0.15f) else AuthBrandPrimary.copy(alpha = 0.15f),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMatched) Icons.Default.CheckCircle else Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = if (isMatched) Color(0xFF10B981) else AuthBrandPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = if (isMatched) "অ্যাপ্লিকেশনটি কারেন্ট ভার্সনে রয়েছে" else "নতুন আপডেট উপলব্ধ!",
                    color = TextPrimary,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Version Info Card
                Surface(
                    color = CinemaSurfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("বর্তমান ভার্সন:", color = TextSecondary, fontSize = 12.sp)
                            Text("v$installedVersionName", color = TextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("রিলিজ ভার্সন:", color = TextSecondary, fontSize = 12.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isMatched) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF10B981)
                                    ) {
                                        Text(
                                            text = "MATCHED",
                                            color = Color.White,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(5.dp))
                                }
                                Text("v${updateInfo.versionName}", color = if (isMatched) Color(0xFF10B981) else CyanAccent, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ------------------------------------------------------------
                // CASE 1: MATCHED (কারেন্ট ভার্সন ম্যাচ করেছে -> ডাউনলোড হবে না)
                // ------------------------------------------------------------
                if (isMatched) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "আপনার অ্যাপটি রিলিজের সাথে ম্যাচ করেছে। নতুন কোনো ডাউনলোডের প্রয়োজন নেই।",
                                color = TextPrimary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text("ঠিক আছে", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                    return@Column
                }

                // ------------------------------------------------------------
                // CASE 2: NOT MATCHED (রিলিজ ম্যাচ করেনি -> ডাউনলোড ও ইনস্টল হবে)
                // ------------------------------------------------------------
                // Download file details & info only
                Surface(
                    color = CinemaBackground,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ডাউনলোড ফাইল:", color = TextMuted, fontSize = 11.5.sp)
                            Text(updateInfo.apkFileName, color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ফাইলের সাইজ:", color = TextMuted, fontSize = 11.5.sp)
                            Text(updateInfo.getFormattedFileSize(), color = CyanAccent, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        if (updateInfo.releaseNotes.isNotBlank()) {
                            HorizontalDivider(
                                color = CinemaBorder,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Text("আপডেট তথ্য:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = updateInfo.releaseNotes,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar while downloading
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
                            Text("ডাউনলোড হচ্ছে...", color = TextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
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
                            text = String.format("%.1f MB / %.1f MB", downloadedMb, if (totalMb > 0) totalMb else 29.3),
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else if (downloadProgress.isCompleted && downloadedFile != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ডাউনলোড সম্পন্ন! ইনস্টল প্রক্রিয়া শুরু হচ্ছে...",
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
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = downloadProgress.error ?: "ডাউনলোড ব্যর্থ হয়েছে",
                                color = BrandRed,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Install permission setting notice if required
                if (needsInstallPermission && (downloadProgress.isCompleted || downloadedFile != null)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFB020).copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB020).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "অ্যাপ ইনস্টল পারমিশন প্রয়োজন:",
                                color = Color(0xFFFFB020),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "সেটিংসে গিয়ে অ্যাপটি ইনস্টল করার পারমিশন চালু করুন।",
                                color = TextSecondary,
                                fontSize = 10.5.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    AppUpdateManager.openInstallPermissionSettings(context)
                                    needsInstallPermission = false
                                },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("পারমিশন সেটিংস", fontSize = 11.sp, color = Color(0xFFFFB020))
                            }
                        }
                    }
                }

                // ------------------------------------------------------------
                // Download and Install Buttons (Only when NOT matched)
                // ------------------------------------------------------------
                if (downloadProgress.isCompleted && downloadedFile != null) {
                    Button(
                        onClick = {
                            val success = AppUpdateManager.installApk(context, downloadedFile!!)
                            if (!success) {
                                needsInstallPermission = true
                                Toast.makeText(context, "ইনস্টলার শুরু করা সম্ভব হয়নি। পারমিশন দিন।", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("এখনই ইনস্টল করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (!downloadProgress.isDownloading) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val file = AppUpdateManager.downloadApk(context, updateInfo)
                                if (file != null && file.exists()) {
                                    downloadedFile = file
                                    needsInstallPermission = !AppUpdateManager.canRequestPackageInstalls(context)
                                    val installed = AppUpdateManager.installApk(context, file)
                                    if (!installed && needsInstallPermission) {
                                        AppUpdateManager.openInstallPermissionSettings(context)
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ডাউনলোড ও আপডেট করুন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Dismiss / Later Button
                TextButton(
                    onClick = {
                        AppUpdateManager.dismissUpdateTag(context, updateInfo.tagName)
                        onDismiss()
                    },
                    enabled = !downloadProgress.isDownloading,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("পরে মনে করিয়ে দিন", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
