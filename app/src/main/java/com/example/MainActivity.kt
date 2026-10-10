package com.example

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.ApiClient
import com.example.data.model.*
import com.example.data.player.MusicPlayerManager
import com.example.data.util.VideoPlayerState
import com.example.data.repository.*
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
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class ScreenTab(val title: String, val icon: ImageVector) {
    HOME("হোম", Icons.Default.Home),
    MOVIES("মুভি", Icons.Default.Movie),
    LIVE_TV("টিভি", Icons.Default.Tv),
    MUSIC("মিউজিক", Icons.Default.MusicNote),
    YOUTUBE("ইউটিউব", Icons.Default.PlayCircle),
    WEATHER("আবহাওয়া", Icons.Default.WbSunny),
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
    var selectedTvChannel by remember { mutableStateOf<TvChannel?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // In-App GitHub Releases Update State
    var activeUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showAppUpdateDialog by remember { mutableStateOf(false) }

    // Automatic update scan
    LaunchedEffect(Unit) {
        try {
            val update = AppUpdateManager.checkForUpdates(context, force = false)
            if (update != null && update.isUpdateAvailable && !AppUpdateManager.isTagDismissed(context, update.tagName)) {
                activeUpdateInfo = update
                showAppUpdateDialog = true
            }
        } catch (_: Exception) {}
    }

    val isPlayerFullScreen = VideoPlayerState.isFullScreen

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isPlayerFullScreen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CinemaSurface,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header Logo
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MukulPlusLogo(iconSize = 32, textSize = 19)
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            color = BrandRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "v1.0.34",
                                color = BrandRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = CinemaBorder, modifier = Modifier.padding(vertical = 8.dp))

                    // Navigation Drawer Items
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
                            moviesInitialTab = MoviesMainTab.MUKUL_OTT
                            moviesInitialSlug = null
                            currentTab = ScreenTab.MOVIES
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
                        label = { Text("আবহাওয়া") },
                        selected = currentTab == ScreenTab.WEATHER,
                        onClick = {
                            currentTab = ScreenTab.WEATHER
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
                                        checkedThumbColor = BrandRed,
                                        checkedTrackColor = BrandRed.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        },
                        selected = false,
                        onClick = { ThemeManager.toggleTheme(context) },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Translate, contentDescription = null, tint = CyanAccent) },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ভাষা (Language)")
                                Text(
                                    text = LanguageManager.currentLanguage.displayName,
                                    color = CyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        selected = false,
                        onClick = {
                            coroutineScope.launch {
                                drawerState.close()
                                showLanguageDialog = true
                            }
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

                    // Version tag in drawer
                    Surface(
                        color = CinemaSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mukul Plus App", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("v1.0.34", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = CinemaBackground,
            topBar = {
                // TopBar only shown on screens that don't have their own custom header
                if (!isPlayerFullScreen && currentTab == ScreenTab.HOME) {
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
                                onClick = { currentTab = ScreenTab.EXTRACTOR },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Downloads",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
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
                                ScreenTab.MUSIC,
                                ScreenTab.EXTRACTOR
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
                                    val isSelected = currentTab == tab
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
            Box(modifier = contentModifier) {
                when (currentTab) {
                    ScreenTab.HOME -> {
                        HomeScreen(
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id ->
                                selectedMovieId = id
                                moviesInitialTab = MoviesMainTab.BONGO_OTT
                                currentTab = ScreenTab.MOVIES
                            },
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
                            onNavigateToDownloads = { currentTab = ScreenTab.EXTRACTOR },
                            onNavigateToMusic = { currentTab = ScreenTab.MUSIC },
                            onNavigateToWeather = { currentTab = ScreenTab.WEATHER },
                            onNavigateToYoutube = { currentTab = ScreenTab.YOUTUBE },
                            onSelectMukulMovie = { slug ->
                                moviesInitialTab = MoviesMainTab.MUKUL_OTT
                                moviesInitialSlug = slug
                                currentTab = ScreenTab.MOVIES
                            }
                        )
                    }
                    ScreenTab.MOVIES -> {
                        MoviesScreen(
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id: Long -> selectedMovieId = id },
                            initialTab = moviesInitialTab,
                            initialSlug = moviesInitialSlug,
                            onTabChanged = { tab -> moviesInitialTab = tab },
                            onNavigateToDownloads = { currentTab = ScreenTab.EXTRACTOR },
                            onBack = { currentTab = ScreenTab.HOME }
                        )
                    }
                    ScreenTab.LIVE_TV -> {
                        LiveTvScreen(
                            mediaRepository = mediaRepository,
                            initialChannel = selectedTvChannel,
                            onBack = { currentTab = ScreenTab.HOME }
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
                    ScreenTab.EXTRACTOR -> {
                        ExtractorScreen(
                            onBack = { currentTab = ScreenTab.HOME }
                        )
                    }
                    ScreenTab.PROFILE -> {
                        ProfileScreen(
                            authRepository = authRepository,
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id: Long ->
                                selectedMovieId = id
                                moviesInitialTab = MoviesMainTab.BONGO_OTT
                                currentTab = ScreenTab.MOVIES
                            },
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

    // Language Selector Dialog
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

    // In-App GitHub Releases Update Dialog
    if (showAppUpdateDialog && activeUpdateInfo != null) {
        AppUpdateDialog(
            updateInfo = activeUpdateInfo!!,
            onDismiss = { showAppUpdateDialog = false }
        )
    }
}

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
