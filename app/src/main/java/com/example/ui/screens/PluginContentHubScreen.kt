package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.extension.CloudstreamProviderEngine
import com.example.data.extension.ExtensionDexLoader
import com.example.data.model.DexAnalysisReport
import com.example.data.model.InstalledPlugin
import com.example.data.model.ProviderMediaItem
import com.example.data.model.ProviderSection
import com.example.data.model.ProviderStreamServer
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginContentHubScreen(
    plugin: InstalledPlugin,
    onBack: () -> Unit,
    onPlayStream: (streamUrl: String, title: String, category: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    BackHandler { onBack() }

    var isLoading by remember { mutableStateOf(true) }
    var sections by remember { mutableStateOf<List<ProviderSection>>(emptyList()) }
    var selectedTag by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<ProviderMediaItem>>(emptyList()) }

    var selectedMediaItem by remember { mutableStateOf<ProviderMediaItem?>(null) }
    var showDexInspectDialog by remember { mutableStateOf(false) }
    var dexReport by remember { mutableStateOf<DexAnalysisReport?>(null) }

    // Load sections & analyze DEX bytecode
    LaunchedEffect(plugin.name) {
        isLoading = true
        dexReport = ExtensionDexLoader.analyzeDexFile(context, plugin)
        val loadedSections = CloudstreamProviderEngine.loadMainPageSections(context, plugin)
        sections = loadedSections
        isLoading = false
    }

    // Handle search query
    LaunchedEffect(searchQuery) {
        if (searchQuery.trim().isNotEmpty()) {
            searchResults = CloudstreamProviderEngine.searchPlugin(context, plugin, searchQuery)
        } else {
            searchResults = emptyList()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = CinemaSurface,
                tonalElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "পিছনে যান",
                                tint = TextPrimary
                            )
                        }

                        // Plugin Icon
                        if (!plugin.iconUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = plugin.iconUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BrandRed.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Extension, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = plugin.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFF00E676).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Dex Active ✓",
                                        color = Color(0xFF00E676),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "ক্লাউডস্ট্রিম প্রোভাইডার • v${plugin.version}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        // Search icon button
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(
                                imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "সার্চ",
                                tint = if (isSearchActive) BrandRed else TextSecondary
                            )
                        }

                        // Dex inspect info button
                        IconButton(onClick = { showDexInspectDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "ডেক্স বিশ্লেষণ",
                                tint = CyanAccent
                            )
                        }

                        // Refresh button
                        IconButton(onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                sections = CloudstreamProviderEngine.loadMainPageSections(context, plugin, forceRefresh = true)
                                isLoading = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "রিফ্রেশ",
                                tint = TextSecondary
                            )
                        }
                    }

                    // Search text field if open
                    if (isSearchActive) {
                        Surface(
                            color = CinemaSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.foundation.text.BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                                    singleLine = true,
                                    decorationBox = { innerTextField ->
                                        if (searchQuery.isEmpty()) {
                                            Text("${plugin.name}-এ সার্চ করুন (যেমন: Cricket, Live, Football, Movie)", color = TextMuted, fontSize = 12.sp)
                                        }
                                        innerTextField()
                                    }
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Category filter chips
                    val tags = listOf(
                        "ALL" to "সব কন্টেন্ট",
                        "LIVE" to "🔴 সরাসরি লাইভ",
                        "CRICKET" to "🏏 ক্রিকেট",
                        "FOOTBALL" to "⚽ ফুটবল",
                        "TV" to "📺 লাইভ টিভি",
                        "MOVIES" to "🎬 সিনেমা"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(tags) { (tagKey, label) ->
                            val isSelected = selectedTag == tagKey
                            Surface(
                                color = if (isSelected) BrandRed else CinemaSurfaceVariant,
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) BrandRed else CinemaBorder
                                ),
                                modifier = Modifier.clickable { selectedTag = tagKey }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = CinemaBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = BrandRed)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "${plugin.name} থেকে ক্লাউড কন্টেন্ট লোড হচ্ছে...",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "classes.dex প্রোভাইডার এক্সিকিউট হচ্ছে",
                        color = CyanAccent,
                        fontSize = 11.sp
                    )
                }
            } else if (isSearchActive && searchQuery.isNotEmpty()) {
                // Search Results Grid
                if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "'$searchQuery' এর কোনো ফলাফল পাওয়া যায়নি।",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "সার্চ ফলাফল (${searchResults.size})",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        items(searchResults) { item ->
                            ProviderItemRowCard(
                                item = item,
                                onClick = { selectedMediaItem = item }
                            )
                        }
                    }
                }
            } else {
                // Sections Feed
                val filteredSections = if (selectedTag == "ALL") {
                    sections
                } else {
                    sections.filter { it.tag == selectedTag }
                }

                if (filteredSections.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "এই ক্যাটাগরিতে কোনো কন্টেন্ট পাওয়া যায়নি।",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Header Promo Banner
                        item {
                            Surface(
                                color = CinemaSurfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = BrandRed.copy(alpha = 0.2f),
                                        shape = CircleShape,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = BrandRed, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ক্লাউডস্ট্রিম প্রোভাইডার হাব",
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "যেকোনো আইটেমে ট্যাপ করে সার্ভার ও কোয়ালিটি নির্বাচন করে ফুলস্ক্রিন প্লে করুন।",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Feed Sections
                        items(filteredSections) { section ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = section.title,
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!section.subtitle.isNullOrEmpty()) {
                                            Text(
                                                text = section.subtitle,
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Surface(
                                        color = CinemaSurfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "${section.items.size} আইটেম",
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(section.items) { mediaItem ->
                                        ProviderMediaCard(
                                            item = mediaItem,
                                            onClick = { selectedMediaItem = mediaItem }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Media Details & Stream Selector Dialog
    if (selectedMediaItem != null) {
        val item = selectedMediaItem!!
        var selectedServer by remember(item) { mutableStateOf(item.streamServers.firstOrNull()) }

        AlertDialog(
            onDismissRequest = { selectedMediaItem = null },
            containerColor = CinemaSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = BrandRed, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    if (!item.posterUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = item.posterUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Metadata badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = BrandRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = item.category,
                                color = BrandRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        if (!item.status.isNullOrEmpty()) {
                            Surface(
                                color = if (item.status.contains("LIVE", ignoreCase = true)) Color(0xFFD32F2F) else CyanAccent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = item.status,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (!item.description.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.description,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "উপলব্ধ স্ট্রিম ও সার্ভার নির্বাচন করুন:",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Server selection list
                    if (item.streamServers.isEmpty()) {
                        Text(
                            text = "কোনো সরাসরি সার্ভার পাওয়া যায়নি।",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    } else {
                        item.streamServers.forEach { server ->
                            val isSelected = selectedServer == server
                            Surface(
                                color = if (isSelected) BrandRed.copy(alpha = 0.2f) else CinemaSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) BrandRed else CinemaBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable { selectedServer = server }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedServer = server },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = BrandRed,
                                                unselectedColor = TextMuted
                                            ),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = server.serverName,
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    Surface(
                                        color = if (isSelected) BrandRed else CinemaBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = server.quality,
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeServer = selectedServer ?: item.streamServers.firstOrNull()
                        val streamUrl = activeServer?.streamUrl ?: item.url
                        if (streamUrl.isNotEmpty()) {
                            selectedMediaItem = null
                            onPlayStream(streamUrl, item.title, item.category)
                        } else {
                            Toast.makeText(context, "স্ট্রিম লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("প্লে করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    OutlinedButton(
                        onClick = {
                            val activeServer = selectedServer ?: item.streamServers.firstOrNull()
                            val streamUrl = activeServer?.streamUrl ?: item.url
                            clipboardManager.setText(AnnotatedString(streamUrl))
                            Toast.makeText(context, "স্ট্রিম লিংক কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("কপি", fontSize = 11.sp, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(onClick = { selectedMediaItem = null }) {
                        Text("বন্ধ করুন", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        )
    }

    // Dex Inspect Report Dialog
    if (showDexInspectDialog && dexReport != null) {
        val report = dexReport!!
        AlertDialog(
            onDismissRequest = { showDexInspectDialog = false },
            containerColor = CinemaSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = CyanAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DEX বাইটকোড বিশ্লেষণ", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color(0xFF00E676).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(report.summary, color = Color(0xFF00E676), fontSize = 12.sp)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CinemaSurfaceVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text("প্রোভাইডার ক্লাস: ${report.className}", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("বাইটকোড সাইজ: ${report.rawBytecodeSize / 1024} KB", color = TextSecondary, fontSize = 11.sp)
                        if (report.mainUrl != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("এন্ডপয়েন্ট URL: ${report.mainUrl}", color = CyanAccent, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("সাপোর্টেড টাইপ: ${report.supportedTypes.joinToString(", ")}", color = BrandRed, fontSize = 11.sp)
                    }

                    Text("শনাক্তকৃত মেথডসমূহ (MainAPI Methods):", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(report.methodsFound) { method ->
                            Surface(
                                color = CinemaSurfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                            ) {
                                Text(method, color = CyanAccent, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDexInspectDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ঠিক আছে", fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun ProviderMediaCard(
    item: ProviderMediaItem,
    onClick: () -> Unit
) {
    Surface(
        color = CinemaSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier
            .width(170.dp)
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(Color(0xFF1A1A24))
            ) {
                if (!item.posterUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.type) {
                                "Sports" -> Icons.Default.SportsCricket
                                "Movie" -> Icons.Default.Movie
                                else -> Icons.Default.Tv
                            },
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Overlay Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )

                // Status Badge (Live or HD)
                if (!item.status.isNullOrEmpty()) {
                    Surface(
                        color = if (item.status.contains("LIVE", ignoreCase = true)) Color(0xFFD32F2F) else Color(0xFF00E676),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = item.status,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                // Play Icon Button on Bottom End
                Surface(
                    color = BrandRed,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.category,
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.streamServers.size} সার্ভার",
                        color = CyanAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderItemRowCard(
    item: ProviderMediaItem,
    onClick: () -> Unit
) {
    Surface(
        color = CinemaSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!item.posterUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BrandRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = BrandRed, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.category} • ${item.status ?: "স্ট্রিম"}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                if (!item.description.isNullOrEmpty()) {
                    Text(
                        text = item.description,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                color = BrandRed,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("ওপেন", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
