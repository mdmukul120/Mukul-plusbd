package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.AdminNotification
import com.example.data.repository.AppItem
import com.example.data.repository.AppStoreRepository
import com.example.data.repository.AppUpdateInfo
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class AdminTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    APP_UPDATE("অ্যাপ আপডেট", Icons.Default.SystemUpdate),
    NOTIFICATIONS("নোটিফিকেশন", Icons.Default.Notifications),
    MANAGE_APPS("অ্যাপস যোগ করুন", Icons.Default.AddBox)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AdminTab.APP_UPDATE) }

    val currentUpdateInfo by AppStoreRepository.updateInfo.collectAsState()
    val appsList by AppStoreRepository.apps.collectAsState()

    // App Update Form State
    var updateVersionName by remember(currentUpdateInfo) { mutableStateOf(currentUpdateInfo.versionName) }
    var updateVersionCode by remember(currentUpdateInfo) { mutableStateOf(currentUpdateInfo.versionCode.toString()) }
    var updateApkUrl by remember(currentUpdateInfo) { mutableStateOf(currentUpdateInfo.downloadUrl) }
    var updateNotes by remember(currentUpdateInfo) { mutableStateOf(currentUpdateInfo.releaseNotes) }
    var isForceUpdate by remember(currentUpdateInfo) { mutableStateOf(currentUpdateInfo.forceUpdate) }
    var isSavingUpdate by remember { mutableStateOf(false) }

    // Notification Form State
    var notifTitle by remember { mutableStateOf("") }
    var notifMessage by remember { mutableStateOf("") }
    var notifImageUrl by remember { mutableStateOf("") }
    var notifActionUrl by remember { mutableStateOf("") }
    var isSendingNotif by remember { mutableStateOf(false) }

    // Add App Form State
    var appName by remember { mutableStateOf("") }
    var appPackageName by remember { mutableStateOf("") }
    var appIconUrl by remember { mutableStateOf("") }
    var appDownloadUrl by remember { mutableStateOf("") }
    var appVersion by remember { mutableStateOf("v1.0") }
    var appSize by remember { mutableStateOf("15 MB") }
    var appCategory by remember { mutableStateOf("ইউটিলিটি") }
    var appDescription by remember { mutableStateOf("") }
    var isAddingApp by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        // Admin Top Bar
        Surface(
            color = CinemaSurface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mukul Plus অ্যাডমিন প্যানেল",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Firebase ক্লাউড কনফিগারেশন ও ম্যানেজমেন্ট",
                        color = CyanAccent,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        // Admin Tabs
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = CinemaSurfaceVariant,
            contentColor = BrandRed
        ) {
            AdminTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            text = tab.title,
                            fontSize = 11.5.sp,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedTab) {
                AdminTab.APP_UPDATE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "অ্যাপ্লিকেশন আপডেট লিংক ও কনফিগ",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "এখানে নতুন APK ডাউনলোড লিংক দিলে সকল ইউজারদের অ্যাপে অটোমেটিক আপডেট নোটিফিকেশন যাবে।",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = updateVersionName,
                            onValueChange = { updateVersionName = it },
                            label = { Text("ভার্সন নাম (e.g. v2.0)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = updateVersionCode,
                            onValueChange = { updateVersionCode = it },
                            label = { Text("ভার্সন কোড (e.g. 5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = updateApkUrl,
                            onValueChange = { updateApkUrl = it },
                            label = { Text("APK ডাউনলোড লিঙ্ক (Direct URL / GitHub Release)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = updateNotes,
                            onValueChange = { updateNotes = it },
                            label = { Text("রিলিজ নোট ও নতুন ফিচারসমূহ") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("বাধ্যতামূলক আপডেট (Force Update)", color = TextPrimary, fontSize = 12.sp)
                            Switch(checked = isForceUpdate, onCheckedChange = { isForceUpdate = it })
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isSavingUpdate = true
                                    val code = updateVersionCode.toIntOrNull() ?: 1
                                    val info = AppUpdateInfo(
                                        versionName = updateVersionName,
                                        versionCode = code,
                                        downloadUrl = updateApkUrl,
                                        releaseNotes = updateNotes,
                                        forceUpdate = isForceUpdate,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    AppStoreRepository.setAppUpdate(context, info)
                                    isSavingUpdate = false
                                    Toast.makeText(context, "অ্যাপ আপডেট কনফিগারেশন সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSavingUpdate
                        ) {
                            if (isSavingUpdate) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("আপডেট প্রকাশ করুন (Publish Update)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AdminTab.NOTIFICATIONS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "সকল গ্রাহকদের নোটিফিকেশন যুক্ত ও প্রেরণ করুন",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ছবি, নাম এবং প্লে বাটন সহ গ্রাহকদের ডিভাইসে সরাসরি রিচ পুশ নোটিফিকেশন যাবে।",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = notifTitle,
                            onValueChange = { notifTitle = it },
                            label = { Text("নোটিফিকেশনের শিরোনাম (Title)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notifMessage,
                            onValueChange = { notifMessage = it },
                            label = { Text("বিস্তারিত বার্তা (Message)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notifImageUrl,
                            onValueChange = { notifImageUrl = it },
                            label = { Text("পোস্টার / ব্যানার ছবি লিঙ্ক (Image URL)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notifActionUrl,
                            onValueChange = { notifActionUrl = it },
                            label = { Text("টার্গেট লিঙ্ক বা মুভি স্লাগ (Movie Slug / Action URL)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (notifTitle.isBlank()) {
                                    Toast.makeText(context, "শিরোনাম লিখুন!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutineScope.launch {
                                    isSendingNotif = true
                                    val notification = AdminNotification(
                                        title = notifTitle,
                                        message = notifMessage,
                                        imageUrl = notifImageUrl,
                                        actionUrl = notifActionUrl,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    AppStoreRepository.sendBroadcastNotification(context, notification)
                                    isSendingNotif = false
                                    notifTitle = ""
                                    notifMessage = ""
                                    notifImageUrl = ""
                                    notifActionUrl = ""
                                    Toast.makeText(context, "নোটিফিকেশন সফলভাবে পাঠানো হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSendingNotif
                        ) {
                            if (isSendingNotif) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("নোটিফিকেশন পাঠান (Broadcast Now)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AdminTab.MANAGE_APPS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "আমাদের তৈরি নতুন অ্যাপ্লিকেশন যুক্ত করুন",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = appName,
                                            onValueChange = { appName = it },
                                            label = { Text("অ্যাপের নাম (e.g. Mukul Live Score)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = appPackageName,
                                            onValueChange = { appPackageName = it },
                                            label = { Text("প্যাকেজ নাম (e.g. com.mukul.app)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = appIconUrl,
                                            onValueChange = { appIconUrl = it },
                                            label = { Text("অ্যাপ আইকন URL (Icon Image URL)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = appDownloadUrl,
                                            onValueChange = { appDownloadUrl = it },
                                            label = { Text("APK ডাউনলোড লিঙ্ক (Direct APK URL)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = appVersion,
                                                onValueChange = { appVersion = it },
                                                label = { Text("ভার্সন") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = appSize,
                                                onValueChange = { appSize = it },
                                                label = { Text("সাইজ (e.g. 15 MB)") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        OutlinedTextField(
                                            value = appCategory,
                                            onValueChange = { appCategory = it },
                                            label = { Text("ক্যাটাগরি (e.g. স্পোর্টস, টুলস)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        OutlinedTextField(
                                            value = appDescription,
                                            onValueChange = { appDescription = it },
                                            label = { Text("অ্যাপের বিবরণ (Description)") },
                                            minLines = 2,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Button(
                                            onClick = {
                                                if (appName.isBlank() || appDownloadUrl.isBlank()) {
                                                    Toast.makeText(context, "নাম ও ডাউনলোড লিঙ্ক আবশ্যক!", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                coroutineScope.launch {
                                                    isAddingApp = true
                                                    val newApp = AppItem(
                                                        name = appName,
                                                        packageName = appPackageName,
                                                        iconUrl = appIconUrl,
                                                        downloadUrl = appDownloadUrl,
                                                        version = appVersion,
                                                        size = appSize,
                                                        category = appCategory,
                                                        description = appDescription
                                                    )
                                                    AppStoreRepository.addApp(context, newApp)
                                                    isAddingApp = false
                                                    appName = ""
                                                    appPackageName = ""
                                                    appIconUrl = ""
                                                    appDownloadUrl = ""
                                                    appDescription = ""
                                                    Toast.makeText(context, "অ্যাপ সফলভাবে যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                                            modifier = Modifier.fillMaxWidth(),
                                            enabled = !isAddingApp
                                        ) {
                                            if (isAddingApp) {
                                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                                            } else {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("অ্যাপ স্টোরে যোগ করুন", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "বর্তমান অ্যাপস তালিকা (${appsList.size}):",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }

                            items(appsList, key = { it.id }) { appItem ->
                                Surface(
                                    color = CinemaSurfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = appItem.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "${appItem.category} • ${appItem.size} • ${appItem.version}", color = CyanAccent, fontSize = 10.5.sp)
                                        }
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    AppStoreRepository.deleteApp(context, appItem.id)
                                                    Toast.makeText(context, "${appItem.name} মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = BrandRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
