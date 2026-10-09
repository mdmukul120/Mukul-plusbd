package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.data.download.DownloadNotificationHelper
import com.example.data.util.MovieUpdateNotificationManager
import com.example.data.util.VideoPlayerState
import com.example.data.util.MukulOttNavState
import com.example.data.util.findActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExtractorPost
import com.example.data.model.TvChannel
import com.example.data.player.MusicPlayerManager
import com.example.data.repository.AuthRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.MusicRepository
import com.example.data.util.AppLanguage
import com.example.data.util.LanguageManager
import com.example.data.util.ThemeManager
import com.example.data.util.ThemeMode
import com.example.data.model.AppUpdateInfo
import com.example.data.util.AppUpdateManager
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.FullMusicPlayerDialog
import com.example.ui.components.MiniMusicPlayer
import com.example.ui.components.MukulPlusLogo
import com.example.ui.components.PluginManagerDialog
import com.example.data.extension.ExtensionManager
import com.example.ui.screens.*
import com.example.ui.theme.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch

enum class ScreenTab(val title: String, val icon: ImageVector) {
    HOME("হোম", Icons.Default.Home),
    MOVIES("মুভি ও ওটিটি", Icons.Default.Movie),
    SPORTS("স্পোর্টস", Icons.Default.SportsCricket),
    LIVE_TV("টিভি", Icons.Default.Tv),
    BANGLA_OTT("বাংলা ওটিটি", Icons.Default.Subscriptions),
    MUSIC("মিউজিক", Icons.Default.MusicNote),
    YOUTUBE("ইউটিউব", Icons.Default.PlayCircle),
    WEATHER("আবহাওয়া", Icons.Default.WbSunny),
    MUKUL_OTT("ওটিটি", Icons.Default.VideoLibrary),
    EXTRACTOR("ডাউনলোড", Icons.Default.CloudDownload),
    PROFILE("প্রোফাইল", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageManager.init(applicationContext)
        ThemeManager.init(applicationContext)
        MusicPlayerManager.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MukulPlusApp()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (VideoPlayerState.isPlaying || VideoPlayerState.isFullScreen)) {
            try {
                enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build())
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MukulPlusApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val authRepository = remember { AuthRepository(context) }
    val mediaRepository = remember { MediaRepository(context) }
    val musicRepository = remember { MusicRepository(context) }

    val currentUser by authRepository.currentUser.collectAsState()
    val showFullMusicPlayer by MusicPlayerManager.showFullPlayer.collectAsState()

    var currentTab by remember { mutableStateOf(ScreenTab.HOME) }
    var moviesInitialTab by remember { mutableStateOf(MoviesMainTab.MUKUL_OTT) }
    var moviesInitialSlug by remember { mutableStateOf<String?>(null) }
    var selectedMovieId by remember { mutableStateOf<Long?>(null) }
    var selectedExtractorPost by remember { mutableStateOf<ExtractorPost?>(null) }
    var selectedTvChannel by remember { mutableStateOf<TvChannel?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // CloudStream CS3 & Plugins Extension Manager State
    val extensionManager = remember { ExtensionManager.getInstance(context) }
    val installedPluginsList by extensionManager.installedPlugins.collectAsState()
    var showPluginManagerDialog by remember { mutableStateOf(false) }

    // In-App GitHub Releases Update State
    var activeUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showAppUpdateDialog by remember { mutableStateOf(false) }

    // Automatic 3-times-a-day background update scanner (every 8 hours: 24h / 3 = 8h)
    LaunchedEffect(Unit) {
        // 1. Initial scan on app launch (checks if 8 hours passed or first launch)
        try {
            val update = AppUpdateManager.checkForUpdates(context, force = false)
            if (update != null && update.isUpdateAvailable && !AppUpdateManager.isTagDismissed(context, update.tagName)) {
                activeUpdateInfo = update
                showAppUpdateDialog = true
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Initial update scan failed", e)
        }

        // 2. Periodic background scan loop every 8 hours (3 times a day)
        while (true) {
            kotlinx.coroutines.delay(8 * 60 * 60 * 1000L)
            try {
                val update = AppUpdateManager.checkForUpdates(context, force = true)
                if (update != null && update.isUpdateAvailable && !AppUpdateManager.isTagDismissed(context, update.tagName)) {
                    activeUpdateInfo = update
                    showAppUpdateDialog = true
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Periodic update scan failed", e)
            }
        }
    }

    val isPlayerFullScreen = VideoPlayerState.isFullScreen

    // Notification Permission Request (Android 13+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // App Initialization: Notifications & Movie APIs Check & Play Movie intent handling
    val activity = context.findActivity()
    LaunchedEffect(Unit) {
        DownloadNotificationHelper.initChannels(context)
        MovieUpdateNotificationManager.checkForNewMovies(context)

        val intent = activity?.intent
        if (intent?.action == DownloadNotificationHelper.ACTION_PLAY_MOVIE || intent?.hasExtra(DownloadNotificationHelper.EXTRA_MOVIE_SLUG) == true) {
            val slug = intent.getStringExtra(DownloadNotificationHelper.EXTRA_MOVIE_SLUG)
            val title = intent.getStringExtra(DownloadNotificationHelper.EXTRA_MOVIE_TITLE)
            if (!slug.isNullOrEmpty()) {
                currentTab = ScreenTab.MUKUL_OTT
                MukulOttNavState.pendingMovieSlug = slug
                MukulOttNavState.pendingMovieTitle = title
            }
        }
    }

    BackHandler(enabled = currentTab != ScreenTab.HOME) {
        currentTab = ScreenTab.HOME
    }

    var isAppStarting by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1600)
        isAppStarting = false
    }

    if (isAppStarting) {
        MukulSplashScreen()
        return
    }

    // -----------------------------------------------------------
    // FORCED LOGIN GATEWAY:
    // মুভির পেজে ঢুকতেই প্রথমে লগইন অপশন লাগবে (হুবহু ছবির মতো)
    // লগইন বাদে অ্যাপ্লিকেশনে ঢোকা যাবে না।
    // -----------------------------------------------------------
    if (currentUser == null) {
        AuthScreen(
            authRepository = authRepository,
            onAuthSuccess = {
                // currentUser will automatically update via state flow
            }
        )
        return
    }

    // Handle Android system back button
    BackHandler(
        enabled = selectedMovieId != null || selectedExtractorPost != null || currentTab != ScreenTab.HOME
    ) {
        when {
            selectedMovieId != null -> selectedMovieId = null
            selectedExtractorPost != null -> selectedExtractorPost = null
            currentTab != ScreenTab.HOME -> currentTab = ScreenTab.HOME
        }
    }

    // If detail screen is open
    if (selectedMovieId != null || selectedExtractorPost != null) {
        MovieDetailScreen(
            movieId = selectedMovieId,
            extractorLink = selectedExtractorPost?.link,
            extractorProvider = selectedExtractorPost?.provider,
            initialTitle = selectedExtractorPost?.title,
            initialPoster = selectedExtractorPost?.image,
            mediaRepository = mediaRepository,
            onBackClick = {
                selectedMovieId = null
                selectedExtractorPost = null
            },
            onSelectMovie = { newId ->
                selectedMovieId = newId
                selectedExtractorPost = null
            }
        )
        return
    }

    // Modal Drawer for Sidebar (Disable gestures on Extractor/YouTube/Weather/BanglaOtt to prevent scroll conflict)
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || (currentTab != ScreenTab.EXTRACTOR && currentTab != ScreenTab.YOUTUBE && currentTab != ScreenTab.WEATHER && currentTab != ScreenTab.BANGLA_OTT),
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CinemaSurface,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Drawer Brand Header
                    MukulPlusLogo(iconSize = 36, textSize = 22)
                    Spacer(modifier = Modifier.height(14.dp))

                    // User Info Card in Drawer
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CinemaSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = BrandRed,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentUser?.displayName ?: "User",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (currentUser?.isGuest == true) "গেস্ট মেম্বার (Guest)" else (currentUser?.email ?: "প্রিমিয়াম ইউজার"),
                                    color = AuthBrandPrimary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = CinemaBorder)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Drawer Navigation Items (Concise labels)
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null, tint = if (currentTab == ScreenTab.HOME) BrandRed else TextSecondary) },
                        label = { Text("হোম") },
                        selected = currentTab == ScreenTab.HOME,
                        onClick = {
                            currentTab = ScreenTab.HOME
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Movie, contentDescription = null, tint = if (currentTab == ScreenTab.MOVIES) BrandRed else TextSecondary) },
                        label = { Text("মুভি ও সিরিজ") },
                        selected = currentTab == ScreenTab.MOVIES,
                        onClick = {
                            currentTab = ScreenTab.MOVIES
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SportsCricket, contentDescription = null, tint = if (currentTab == ScreenTab.SPORTS) Color(0xFF00E676) else TextSecondary) },
                        label = { Text("লাইভ স্পোর্টস ও ক্রিকেট") },
                        selected = currentTab == ScreenTab.SPORTS,
                        onClick = {
                            currentTab = ScreenTab.SPORTS
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Tv, contentDescription = null, tint = if (currentTab == ScreenTab.LIVE_TV) BrandRed else TextSecondary) },
                        label = { Text("লাইভ টিভি") },
                        selected = currentTab == ScreenTab.LIVE_TV,
                        onClick = {
                            currentTab = ScreenTab.LIVE_TV
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Subscriptions, contentDescription = null, tint = if (currentTab == ScreenTab.BANGLA_OTT) BrandRed else TextSecondary) },
                        label = { Text("বাংলা ওটিটি (Bangla OTT)") },
                        selected = currentTab == ScreenTab.BANGLA_OTT,
                        onClick = {
                            currentTab = ScreenTab.BANGLA_OTT
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = if (currentTab == ScreenTab.MUSIC) BrandRed else TextSecondary) },
                        label = { Text("মিউজিক") },
                        selected = currentTab == ScreenTab.MUSIC,
                        onClick = {
                            currentTab = ScreenTab.MUSIC
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.PlayCircle, contentDescription = null, tint = if (currentTab == ScreenTab.YOUTUBE) BrandRed else TextSecondary) },
                        label = { Text("ইউটিউব") },
                        selected = currentTab == ScreenTab.YOUTUBE,
                        onClick = {
                            currentTab = ScreenTab.YOUTUBE
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.WbSunny, contentDescription = null, tint = if (currentTab == ScreenTab.WEATHER) Color(0xFFFFB020) else TextSecondary) },
                        label = { Text("আবহাওয়া পূর্বাভাস") },
                        selected = currentTab == ScreenTab.WEATHER,
                        onClick = {
                            currentTab = ScreenTab.WEATHER
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = if (currentTab == ScreenTab.MUKUL_OTT) BrandRed else TextSecondary) },
                        label = { Text("মুকুল ওটিটি") },
                        selected = currentTab == ScreenTab.MUKUL_OTT,
                        onClick = {
                            currentTab = ScreenTab.MUKUL_OTT
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = if (currentTab == ScreenTab.EXTRACTOR) BrandRed else TextSecondary) },
                        label = { Text("ডাউনলোড") },
                        selected = currentTab == ScreenTab.EXTRACTOR,
                        onClick = {
                            currentTab = ScreenTab.EXTRACTOR
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    val currentThemeMode by ThemeManager.themeMode.collectAsState()
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = if (currentThemeMode == ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = if (currentThemeMode == ThemeMode.DARK) Color(0xFFFFB020) else BrandRed
                            )
                        },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ডার্ক মোড")
                                Switch(
                                    checked = currentThemeMode == ThemeMode.DARK,
                                    onCheckedChange = { ThemeManager.toggleTheme(context) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = BrandRed
                                    ),
                                    modifier = Modifier.scale(0.8f)
                                )
                            }
                        },
                        selected = false,
                        onClick = { ThemeManager.toggleTheme(context) },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Translate, contentDescription = null, tint = CyanAccent) },
                        label = { Text("ভাষা (${LanguageManager.currentLanguage.displayName})", color = CyanAccent) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showLanguageDialog = true
                        },
                        colors = drawerItemColors()
                    )

                    // App Update Navigation Item with live status badge
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFF10B981)) },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("অ্যাপ আপডেট", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                if (activeUpdateInfo?.isUpdateAvailable == true) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = BrandRed
                                    ) {
                                        Text(
                                            "NEW",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                Toast.makeText(context, "আপডেট স্ক্যান করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                                val update = AppUpdateManager.checkForUpdates(context, force = true)
                                if (update != null) {
                                    activeUpdateInfo = update
                                    showAppUpdateDialog = true
                                } else {
                                    val (vName, _) = AppUpdateManager.getInstalledVersion(context)
                                    Toast.makeText(context, "আপনার অ্যাপ্লিকেশনটি কারেন্ট ভার্সনে রয়েছে (v$vName)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Extension, contentDescription = null, tint = BrandRed) },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("প্লাগইন ও এক্সটেনশন (+)", color = BrandRed, fontWeight = FontWeight.Bold)
                                if (installedPluginsList.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = BrandRed.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            "${installedPluginsList.size}",
                                            color = BrandRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showPluginManagerDialog = true
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = null, tint = if (currentTab == ScreenTab.PROFILE) BrandRed else TextSecondary) },
                        label = { Text("প্রোফাইল") },
                        selected = currentTab == ScreenTab.PROFILE,
                        onClick = {
                            currentTab = ScreenTab.PROFILE
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Logout Button in Drawer
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                authRepository.logout()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRedLight),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("লগআউট", fontSize = 12.sp)
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = CinemaBackground,
            topBar = {
                if (!isPlayerFullScreen && currentTab != ScreenTab.EXTRACTOR && currentTab != ScreenTab.YOUTUBE && currentTab != ScreenTab.WEATHER && currentTab != ScreenTab.BANGLA_OTT && currentTab != ScreenTab.SPORTS) {
                    Surface(
                        color = CinemaSurface,
                        tonalElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(46.dp)
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { coroutineScope.launch { drawerState.open() } },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Sidebar",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                MukulPlusLogo(iconSize = 24, textSize = 15)
                            }

                            // Add CS3 Extension / Plugin Plus (+) Button
                            IconButton(
                                onClick = { showPluginManagerDialog = true },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Surface(
                                    color = BrandRed.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "প্লাগইন ও সিএস৩ এক্সটেনশন যোগ করুন",
                                            tint = BrandRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            val topBarThemeMode by ThemeManager.themeMode.collectAsState()
                            IconButton(
                                onClick = { ThemeManager.toggleTheme(context) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (topBarThemeMode == ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Dark/Light Mode",
                                    tint = if (topBarThemeMode == ThemeMode.DARK) Color(0xFFFFB020) else TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { currentTab = ScreenTab.PROFILE },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Surface(
                                    color = BrandRed,
                                    shape = CircleShape,
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!isPlayerFullScreen) {
                    Column {
                        MiniMusicPlayer()
                        Surface(
                            color = CinemaSurface,
                            tonalElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val bottomBarTabs = listOf(
                                ScreenTab.HOME,
                                ScreenTab.MOVIES,
                                ScreenTab.LIVE_TV,
                                ScreenTab.SPORTS,
                                ScreenTab.MUSIC
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .height(54.dp)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                bottomBarTabs.forEach { tab ->
                                    val isSelected = currentTab == tab || (tab == ScreenTab.MOVIES && (currentTab == ScreenTab.MUKUL_OTT || currentTab == ScreenTab.BANGLA_OTT))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                if (tab == ScreenTab.MOVIES) {
                                                    moviesInitialTab = MoviesMainTab.MUKUL_OTT
                                                    moviesInitialSlug = null
                                                }
                                                currentTab = tab
                                            }
                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            color = if (isSelected) BrandRed.copy(alpha = 0.20f) else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = tab.title,
                                                    tint = if (isSelected) BrandRedLight else TextMuted,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = tab.title,
                                            color = if (isSelected) BrandRedLight else TextMuted,
                                            fontSize = 9.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            val contentModifier = if (isPlayerFullScreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            }
            Box(
                modifier = contentModifier
            ) {
                when (currentTab) {
                    ScreenTab.HOME -> {
                        HomeScreen(
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id -> selectedMovieId = id },
                            onSelectPost = { post -> selectedExtractorPost = post },
                            onSelectChannel = { channel ->
                                selectedTvChannel = channel
                                currentTab = ScreenTab.LIVE_TV
                            },
                            onNavigateToMovies = { targetTab ->
                                moviesInitialTab = targetTab ?: MoviesMainTab.MUKUL_OTT
                                moviesInitialSlug = null
                                currentTab = ScreenTab.MOVIES
                            },
                            onNavigateToLiveTv = { currentTab = ScreenTab.LIVE_TV },
                            onNavigateToExtractor = { currentTab = ScreenTab.EXTRACTOR },
                            onNavigateToMusic = { currentTab = ScreenTab.MUSIC },
                            onNavigateToWeather = { currentTab = ScreenTab.WEATHER },
                            onNavigateToBanglaOtt = {
                                moviesInitialTab = MoviesMainTab.BANGLA_OTT
                                moviesInitialSlug = null
                                currentTab = ScreenTab.MOVIES
                            },
                            onNavigateToSports = { currentTab = ScreenTab.SPORTS },
                            onSelectMukulMovie = { slug ->
                                moviesInitialTab = MoviesMainTab.MUKUL_OTT
                                moviesInitialSlug = slug
                                currentTab = ScreenTab.MOVIES
                            },
                            onOpenPluginManager = { showPluginManagerDialog = true }
                        )
                    }
                    ScreenTab.BANGLA_OTT -> {
                        MoviesScreen(
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id: Long -> selectedMovieId = id },
                            initialTab = MoviesMainTab.BANGLA_OTT,
                            onBack = { currentTab = ScreenTab.HOME }
                        )
                    }
                    ScreenTab.MOVIES -> {
                        MoviesScreen(
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id: Long -> selectedMovieId = id },
                            initialTab = moviesInitialTab,
                            initialSlug = moviesInitialSlug,
                            onBack = { currentTab = ScreenTab.HOME }
                        )
                    }
                    ScreenTab.SPORTS -> {
                        SportsScreen(
                            onSelectChannel = { channel ->
                                selectedTvChannel = channel
                                currentTab = ScreenTab.LIVE_TV
                            }
                        )
                    }
                    ScreenTab.LIVE_TV -> {
                        LiveTvScreen(
                            mediaRepository = mediaRepository,
                            initialChannel = selectedTvChannel
                        )
                    }
                    ScreenTab.MUSIC -> {
                        MusicScreen(musicRepository = musicRepository)
                    }
                    ScreenTab.YOUTUBE -> {
                        YouTubeScreen(
                            onNavigateToDownloads = { currentTab = ScreenTab.EXTRACTOR }
                        )
                    }
                    ScreenTab.WEATHER -> {
                        WeatherScreen(
                            onNavigateHome = { currentTab = ScreenTab.HOME }
                        )
                    }
                    ScreenTab.MUKUL_OTT -> {
                        MoviesScreen(
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id: Long -> selectedMovieId = id },
                            initialTab = MoviesMainTab.MUKUL_OTT,
                            onBack = { currentTab = ScreenTab.HOME }
                        )
                    }
                    ScreenTab.EXTRACTOR -> {
                        ExtractorScreen()
                    }
                    ScreenTab.PROFILE -> {
                        ProfileScreen(
                            authRepository = authRepository,
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id: Long -> selectedMovieId = id },
                            onCheckForUpdates = {
                                coroutineScope.launch {
                                    Toast.makeText(context, "আপডেট স্ক্যান করা হচ্ছে...", Toast.LENGTH_SHORT).show()
                                    val update = AppUpdateManager.checkForUpdates(context, force = true)
                                    if (update != null) {
                                        activeUpdateInfo = update
                                        showAppUpdateDialog = true
                                    } else {
                                        val (vName, _) = AppUpdateManager.getInstalledVersion(context)
                                        Toast.makeText(context, "আপনার অ্যাপ্লিকেশনটি কারেন্ট ভার্সনে রয়েছে (v$vName)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onLogout = {
                                coroutineScope.launch {
                                    authRepository.logout()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Fullscreen Music Player Dialog
    if (showFullMusicPlayer) {
        FullMusicPlayerDialog(
            onDismiss = { MusicPlayerManager.closeFullPlayer() }
        )
    }

    // Language Selector Dialog (10 Languages)
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { lang ->
                LanguageManager.setLanguage(context, lang)
                showLanguageDialog = false
                Toast.makeText(context, "ভাষা পরিবর্তিত: ${lang.displayName}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // In-App GitHub Releases Update Dialog & APK Downloader/Installer
    if (showAppUpdateDialog && activeUpdateInfo != null) {
        AppUpdateDialog(
            updateInfo = activeUpdateInfo!!,
            onDismiss = { showAppUpdateDialog = false }
        )
    }

    // CloudStream CS3 & Plugins Extension Manager Dialog
    PluginManagerDialog(
        isOpen = showPluginManagerDialog,
        onDismiss = { showPluginManagerDialog = false },
        onPlayStream = { streamUrl, title ->
            selectedTvChannel = TvChannel(
                id = "plugin_${System.currentTimeMillis()}",
                name = title,
                logo = null,
                groupTitle = "সিএস৩ এক্সটেনশন",
                streamUrl = streamUrl
            )
            currentTab = ScreenTab.LIVE_TV
        }
    )
}

// -------------------------------------------------------------
// 10 LANGUAGES SELECTION DIALOG
// -------------------------------------------------------------
@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Translate, contentDescription = null, tint = CyanAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ভাষা নির্বাচন করুন (Select Language)", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppLanguage.values().forEach { lang ->
                    val isSelected = LanguageManager.currentLanguage == lang
                    Surface(
                        color = if (isSelected) BrandRed.copy(alpha = 0.2f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(lang) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "${lang.flag}  ${lang.displayName}", color = if (isSelected) CyanAccent else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "Code: ${lang.code.uppercase()}", color = TextMuted, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = CyanAccent)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন (Close)", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun drawerItemColors() = NavigationDrawerItemDefaults.colors(
    selectedContainerColor = CinemaSurfaceVariant,
    unselectedContainerColor = Color.Transparent,
    selectedTextColor = AuthBrandPrimary,
    unselectedTextColor = TextSecondary
)
