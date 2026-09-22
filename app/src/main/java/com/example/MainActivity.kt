package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.repository.AuthRepository
import com.example.data.repository.MediaRepository
import com.example.data.util.AppLanguage
import com.example.data.util.LanguageManager
import com.example.data.util.ThemeManager
import com.example.data.util.ThemeMode
import com.example.ui.components.MukulPlusLogo
import com.example.ui.screens.*
import com.example.ui.theme.*
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.launch

enum class ScreenTab(val title: String, val icon: ImageVector) {
    HOME("হোম", Icons.Default.Home),
    MOVIES("মুভিজ", Icons.Default.Movie),
    LIVE_TV("লাইভ টিভি", Icons.Default.Tv),
    MUKUL_OTT("Mukul OTT", Icons.Default.Public),
    EXTRACTOR("ডাউনলোড", Icons.Default.CloudDownload),
    PROFILE("প্রোফাইল", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageManager.init(applicationContext)
        ThemeManager.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MukulPlusApp()
            }
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

    val currentUser by authRepository.currentUser.collectAsState()

    var currentTab by remember { mutableStateOf(ScreenTab.HOME) }
    var selectedMovieId by remember { mutableStateOf<Long?>(null) }
    var selectedExtractorPost by remember { mutableStateOf<ExtractorPost?>(null) }
    var selectedTvChannel by remember { mutableStateOf<TvChannel?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

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
        enabled = selectedMovieId != null || selectedExtractorPost != null
    ) {
        when {
            selectedMovieId != null -> selectedMovieId = null
            selectedExtractorPost != null -> selectedExtractorPost = null
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

    // Modal Drawer for Sidebar (Disable gestures on Extractor tab to prevent scroll conflict)
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentTab != ScreenTab.EXTRACTOR,
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

                    // Drawer Navigation Items
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null, tint = if (currentTab == ScreenTab.HOME) BrandRed else TextSecondary) },
                        label = { Text("হোম পেজ (Home)") },
                        selected = currentTab == ScreenTab.HOME,
                        onClick = {
                            currentTab = ScreenTab.HOME
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Movie, contentDescription = null, tint = if (currentTab == ScreenTab.MOVIES) BrandRed else TextSecondary) },
                        label = { Text("মুভি ও সিরিজ ব্রাউজার") },
                        selected = currentTab == ScreenTab.MOVIES,
                        onClick = {
                            currentTab = ScreenTab.MOVIES
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Tv, contentDescription = null, tint = if (currentTab == ScreenTab.LIVE_TV) BrandRed else TextSecondary) },
                        label = { Text("বাংলাদেশী ও BDIX লাইভ টিভি") },
                        selected = currentTab == ScreenTab.LIVE_TV,
                        onClick = {
                            currentTab = ScreenTab.LIVE_TV
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Public, contentDescription = null, tint = if (currentTab == ScreenTab.MUKUL_OTT) BrandRed else TextSecondary) },
                        label = { Text("মুকুল ওটিটি (Mukul OTT Web)") },
                        selected = currentTab == ScreenTab.MUKUL_OTT,
                        onClick = {
                            currentTab = ScreenTab.MUKUL_OTT
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = if (currentTab == ScreenTab.EXTRACTOR) BrandRed else TextSecondary) },
                        label = { Text("ডাউনলোড (Mukul Movies)") },
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
                                Text(if (currentThemeMode == ThemeMode.DARK) "ডার্ক মোড (Dark)" else "লাইট মোড (Light)")
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
                        label = { Text("ভাষা পরিবর্তন (${LanguageManager.currentLanguage.displayName})", color = CyanAccent) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showLanguageDialog = true
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFF10B981)) },
                        label = { Text("অ্যাপ অটো-আপডেট (Firebase)", color = Color(0xFF10B981)) },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showUpdateDialog = true
                        },
                        colors = drawerItemColors()
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = null, tint = if (currentTab == ScreenTab.PROFILE) BrandRed else TextSecondary) },
                        label = { Text("প্রোফাইল ও ওয়াচলিস্ট") },
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
                        Text("লগআউট করুন (Sign Out)", fontSize = 12.sp)
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = CinemaBackground,
            topBar = {
                if (currentTab != ScreenTab.EXTRACTOR && currentTab != ScreenTab.MUKUL_OTT) {
                    TopAppBar(
                        title = {
                            MukulPlusLogo(iconSize = 30, textSize = 18)
                        },
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = "Open Sidebar", tint = TextPrimary)
                            }
                        },
                        actions = {
                            // Dark/Light Theme Toggle Action
                            val topBarThemeMode by ThemeManager.themeMode.collectAsState()
                            IconButton(onClick = { ThemeManager.toggleTheme(context) }) {
                                Icon(
                                    imageVector = if (topBarThemeMode == ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Dark/Light Mode",
                                    tint = if (topBarThemeMode == ThemeMode.DARK) Color(0xFFFFB020) else TextPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Profile Avatar
                            IconButton(onClick = { currentTab = ScreenTab.PROFILE }) {
                                Surface(
                                    color = BrandRed,
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = CinemaSurface)
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = CinemaSurface,
                    tonalElevation = 8.dp
                ) {
                    ScreenTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) BrandRed else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) BrandRed else TextMuted,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = BrandRed.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                            onNavigateToMovies = { currentTab = ScreenTab.MOVIES },
                            onNavigateToLiveTv = { currentTab = ScreenTab.LIVE_TV },
                            onNavigateToExtractor = { currentTab = ScreenTab.EXTRACTOR }
                        )
                    }
                    ScreenTab.MOVIES -> {
                        MoviesScreen(
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id: Long -> selectedMovieId = id }
                        )
                    }
                    ScreenTab.LIVE_TV -> {
                        LiveTvScreen(
                            mediaRepository = mediaRepository,
                            initialChannel = selectedTvChannel
                        )
                    }
                    ScreenTab.MUKUL_OTT -> {
                        MukulOttScreen()
                    }
                    ScreenTab.EXTRACTOR -> {
                        ExtractorScreen()
                    }
                    ScreenTab.PROFILE -> {
                        ProfileScreen(
                            authRepository = authRepository,
                            mediaRepository = mediaRepository,
                            onSelectMovie = { id: Long -> selectedMovieId = id },
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

    // App Update Dialog
    if (showUpdateDialog) {
        AppUpdatesDialog(onDismiss = { showUpdateDialog = false })
    }
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

// -------------------------------------------------------------
// APP UPDATES DIALOG (Firebase & GitHub Remote Updater)
// -------------------------------------------------------------
@Composable
fun AppUpdatesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CinemaSurface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "অটো-আপডেট ও রিমোট কনফিগ",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = Color.White
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = CinemaSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("বর্তমান ভার্সন:", color = TextSecondary, fontSize = 12.sp)
                            Surface(
                                color = BrandRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "v1.2 (Build 2)",
                                    color = BrandRedLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ক্লাউড সার্ভিস:", color = TextSecondary, fontSize = 12.sp)
                            Text("Firebase Remote Config", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("স্বয়ংক্রিয় CI/CD:", color = TextSecondary, fontSize = 12.sp)
                            Text("GitHub Actions APK", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Text(
                    text = "ফায়ারবেস ও গিটহাব অ্যাকশনের মাধ্যমে অ্যাপে নতুন কোনো আপডেট আসলে তা স্বয়ংক্রিয়ভাবে ডাউনলোড ও আপডেট ইনস্টল করার নোটিফিকেশন আসবে।",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com")
                        )
                        context.startActivity(browserIntent)
                    } catch (_: Exception) {}
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AuthBrandPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("আপডেট চেক করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("বন্ধ করুন", fontSize = 12.sp)
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
