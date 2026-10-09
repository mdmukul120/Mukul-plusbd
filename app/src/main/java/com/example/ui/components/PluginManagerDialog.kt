package com.example.ui.components

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.extension.ExtensionManager
import com.example.data.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onPlayStream: ((streamUrl: String, title: String) -> Unit)? = null
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val extensionManager = remember { ExtensionManager.getInstance(context) }
    val installedPlugins by extensionManager.installedPlugins.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Add/Analyze, 1: Installed
    var inputUrl by remember { mutableStateOf("") }
    var analysisState by remember { mutableStateOf<PluginAnalysisState>(PluginAnalysisState.Idle) }

    // Installing progress states
    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableFloatStateOf(0f) }
    var installStatusText by remember { mutableStateOf("") }

    // Selected plugin to inspect files or streams
    var inspectingPlugin by remember { mutableStateOf<InstalledPlugin?>(null) }
    var viewingStreamsPlugin by remember { mutableStateOf<InstalledPlugin?>(null) }
    var pluginStreams by remember { mutableStateOf<List<PluginChannelItem>>(emptyList()) }
    var isLoadingStreams by remember { mutableStateOf(false) }

    // Clipboard
    val clipboardManager = LocalClipboardManager.current

    val sampleCs3Url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/PublicSportsIPTV.cs3"
    val sampleRepoUrl = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/plugins.json"

    fun startAnalysis(url: String) {
        if (url.isBlank()) {
            Toast.makeText(context, "একটি বৈধ লিঙ্ক লিখুন বা পেস্ট করুন", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            analysisState = PluginAnalysisState.Loading
            analysisState = extensionManager.analyzeUrl(url)
        }
    }

    fun startInstallCs3(url: String, metadata: CloudstreamPlugin? = null) {
        coroutineScope.launch {
            isInstalling = true
            installProgress = 0.05f
            installStatusText = "ডাউনলোড ও ইন্সটলেশন শুরু হচ্ছে..."

            val res = extensionManager.installPlugin(
                cs3Url = url,
                metadata = metadata,
                onProgress = { p, msg ->
                    installProgress = p
                    installStatusText = msg
                }
            )

            isInstalling = false
            if (res.isSuccess) {
                Toast.makeText(context, "${res.getOrNull()?.name} সফলভাবে ইন্সটল হয়েছে!", Toast.LENGTH_LONG).show()
                selectedTab = 1 // Switch to installed tab
            } else {
                Toast.makeText(context, "ইন্সটলেশন ব্যর্থ: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isInstalling) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = CinemaSurface,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CinemaSurfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BrandRed.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ক্লাউডস্ট্রিম এক্সটেনশন ও প্লাগইন",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = ".cs3 অথবা plugins.json রিপোজিটোরি ইন্সটলার",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CinemaSurface,
                    contentColor = BrandRed,
                    divider = { HorizontalDivider(color = CinemaBorder) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("নতুন যোগ (+)", fontSize = 13.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ইন্সটলড (${installedPlugins.size})", fontSize = 13.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }

                // Installation progress bar overlay
                if (isInstalling) {
                    Surface(
                        color = BrandRed.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = installStatusText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = BrandRed
                                )
                                Text(
                                    text = "${(installProgress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandRed
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { installProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = BrandRed,
                                trackColor = BrandRed.copy(alpha = 0.2f)
                            )
                        }
                    }
                }

                // Main Content Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (selectedTab == 0) {
                        // TAB 0: Add / Analyze Link
                        AddAndAnalyzeContent(
                            inputUrl = inputUrl,
                            onUrlChange = { inputUrl = it },
                            onPaste = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    inputUrl = clip.trim()
                                    Toast.makeText(context, "লিঙ্ক পেস্ট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "ক্লিপবোর্ডে কোনো লিঙ্ক নেই", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onClear = { inputUrl = "" },
                            onSampleCs3 = {
                                inputUrl = sampleCs3Url
                                startAnalysis(sampleCs3Url)
                            },
                            onSampleRepo = {
                                inputUrl = sampleRepoUrl
                                startAnalysis(sampleRepoUrl)
                            },
                            onAnalyze = { startAnalysis(inputUrl) },
                            analysisState = analysisState,
                            isInstalling = isInstalling,
                            onInstallCs3 = { url, meta -> startInstallCs3(url, meta) },
                            isPluginInstalled = { extensionManager.isPluginInstalled(it) }
                        )
                    } else {
                        // TAB 1: Installed Plugins List
                        InstalledPluginsContent(
                            installedPlugins = installedPlugins,
                            onToggleEnabled = { name, enabled ->
                                coroutineScope.launch { extensionManager.togglePluginEnabled(name, enabled) }
                            },
                            onInspect = { inspectingPlugin = it },
                            onViewStreams = { plugin ->
                                viewingStreamsPlugin = plugin
                                coroutineScope.launch {
                                    isLoadingStreams = true
                                    pluginStreams = extensionManager.fetchPluginStreams(plugin)
                                    isLoadingStreams = false
                                }
                            },
                            onUninstall = { name ->
                                coroutineScope.launch {
                                    extensionManager.uninstallPlugin(name)
                                    Toast.makeText(context, "$name আনইন্সটল করা হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onGoToAddTab = { selectedTab = 0 }
                        )
                    }
                }
            }
        }
    }

    // Inspect Plugin Details Dialog
    inspectingPlugin?.let { plugin ->
        InspectPluginDialog(
            plugin = plugin,
            onDismiss = { inspectingPlugin = null }
        )
    }

    // View Streams Dialog
    viewingStreamsPlugin?.let { plugin ->
        PluginStreamsDialog(
            plugin = plugin,
            isLoading = isLoadingStreams,
            streams = pluginStreams,
            onDismiss = { viewingStreamsPlugin = null },
            onPlay = { streamUrl, title ->
                viewingStreamsPlugin = null
                onDismiss()
                onPlayStream?.invoke(streamUrl, title)
            }
        )
    }
}

@Composable
private fun AddAndAnalyzeContent(
    inputUrl: String,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onSampleCs3: () -> Unit,
    onSampleRepo: () -> Unit,
    onAnalyze: () -> Unit,
    analysisState: PluginAnalysisState,
    isInstalling: Boolean,
    onInstallCs3: (url: String, metadata: CloudstreamPlugin?) -> Unit,
    isPluginInstalled: (String) -> Boolean
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Guidance Card
        item {
            Surface(
                color = CinemaSurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "প্লাগইন লিঙ্ক পারমিশন ও ইন্সটল নিয়মাবলী",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "নিচে যেকোনো CloudStream `.cs3` ফাইল লিঙ্ক অথবা সম্পূর্ণ রিপোজিটোরি (`plugins.json`) লিঙ্ক দিন। 'লিংক অ্যানালাইজ' বাটনে চাপ দিলে লিঙ্ক বিশ্লেষণ করে প্যাকেজের সমস্ত তথ্য দেখাবে এবং আপনি অ্যাপের ভিতরেই ইন্সটল করে নিতে পারবেন।",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // URL Input Box
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "সিএস৩ বা রিপোজিটোরি লিঙ্ক লিখুন:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = onUrlChange,
                    placeholder = {
                        Text(
                            text = "https://.../PublicSportsIPTV.cs3 বা plugins.json",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CinemaBorder,
                        focusedContainerColor = CinemaSurfaceVariant,
                        unfocusedContainerColor = CinemaSurfaceVariant,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inputUrl.isNotEmpty()) {
                                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = onPaste, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = BrandRed, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAnalyze() })
                )
            }
        }

        // Quick Preset Suggestions
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "দ্রুত টেস্টিং ও ডেমো লিঙ্ক:",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuggestionChip(
                        onClick = onSampleCs3,
                        label = { Text("⚡ PublicSportsIPTV (.cs3)", fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = BrandRed.copy(alpha = 0.1f),
                            labelColor = BrandRed
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = BrandRed.copy(alpha = 0.4f)
                        )
                    )
                    SuggestionChip(
                        onClick = onSampleRepo,
                        label = { Text("🌐 Phisher Repo (85+ Plugins)", fontSize = 11.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = CinemaSurfaceVariant,
                            labelColor = TextPrimary
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = CinemaBorder
                        )
                    )
                }
            }
        }

        // Action Button: Analyze Link
        item {
            Button(
                onClick = onAnalyze,
                enabled = inputUrl.isNotBlank() && analysisState !is PluginAnalysisState.Loading && !isInstalling,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed,
                    contentColor = Color.White,
                    disabledContainerColor = BrandRed.copy(alpha = 0.4f)
                )
            ) {
                if (analysisState is PluginAnalysisState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("লিংক অ্যানালাইজ করা হচ্ছে...", fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.ManageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("লিংক যাচাই ও অ্যানালাইজ করুন 🔍", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Analysis Result Display
        when (analysisState) {
            is PluginAnalysisState.Idle -> {
                // Nothing yet
            }
            is PluginAnalysisState.Loading -> {
                item {
                    Surface(
                        color = CinemaSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = BrandRed, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "লিংক এবং প্যাকেজ ফাইল ডাউনলোড করে বিশ্লেষণ করা হচ্ছে...",
                                fontSize = 12.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "manifest.json এবং classes.dex যাচাই হচ্ছে",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
            is PluginAnalysisState.Error -> {
                item {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = analysisState.message,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
            is PluginAnalysisState.SingleCs3 -> {
                item {
                    SingleCs3AnalysisCard(
                        analysis = analysisState,
                        isInstalling = isInstalling,
                        isInstalled = isPluginInstalled(analysisState.manifest.name),
                        onInstall = { onInstallCs3(analysisState.url, null) }
                    )
                }
            }
            is PluginAnalysisState.RepoList -> {
                item {
                    Surface(
                        color = CinemaSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "রিপোজিটোরি অ্যানালাইসিস সফল!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "মোট ${analysisState.plugins.size}টি প্লাগইন পাওয়া গেছে",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                items(analysisState.plugins, key = { it.url + it.name }) { plugin ->
                    RepoPluginItemCard(
                        plugin = plugin,
                        isInstalling = isInstalling,
                        isInstalled = isPluginInstalled(plugin.name),
                        onInstall = { onInstallCs3(plugin.url, plugin) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleCs3AnalysisCard(
    analysis: PluginAnalysisState.SingleCs3,
    isInstalling: Boolean,
    isInstalled: Boolean,
    onInstall: () -> Unit
) {
    Surface(
        color = CinemaSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BrandRed.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!analysis.suggestedIconUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = analysis.suggestedIconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = BrandRed, modifier = Modifier.size(26.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = analysis.manifest.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = BrandRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "v${analysis.manifest.version}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandRed,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (analysis.manifest.pluginClassName.isNotEmpty()) {
                        Text(
                            text = analysis.manifest.pluginClassName,
                            fontSize = 10.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = CinemaBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Details Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoBadge(
                    label = "সাইজ",
                    value = "${(analysis.archiveSizeBytes / 1024)} KB",
                    isGood = true
                )
                InfoBadge(
                    label = "classes.dex",
                    value = if (analysis.hasClassesDex) "উপস্থিত ✓" else "নেই ✗",
                    isGood = analysis.hasClassesDex
                )
                InfoBadge(
                    label = "ম্যানিফেস্ট",
                    value = "বৈধ ✓",
                    isGood = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Categories
            if (analysis.tvTypes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    analysis.tvTypes.forEach { type ->
                        Surface(
                            color = CinemaSurface,
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                        ) {
                            Text(
                                text = type,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // User Permission / Confirmation message
            Surface(
                color = CinemaSurface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "প্যাকেজ ফাইলটি যাচাই হয়েছে। এটি নিরাপদভাবে অভ্যন্তরীণ মেমোরিতে ইন্সটল হবে।",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // INSTALL BUTTON
            Button(
                onClick = onInstall,
                enabled = !isInstalling,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInstalled) GreenSuccess else BrandRed,
                    contentColor = Color.White
                )
            ) {
                if (isInstalled) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ইতোমধ্যে ইন্সটলড (পুনরায় ইন্সটল করুন)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.DownloadForOffline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("অ্যাপের ভিতরে ইন্সটল করুন 📥", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RepoPluginItemCard(
    plugin: CloudstreamPlugin,
    isInstalling: Boolean,
    isInstalled: Boolean,
    onInstall: () -> Unit
) {
    Surface(
        color = CinemaSurfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!plugin.iconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = plugin.iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandRed.copy(alpha = 0.15f)),
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "v${plugin.version}",
                        fontSize = 10.sp,
                        color = BrandRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!plugin.description.isNullOrEmpty()) {
                    Text(
                        text = plugin.description,
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (plugin.tvTypes.isNotEmpty()) {
                    Text(
                        text = plugin.tvTypes.joinToString(" • "),
                        fontSize = 9.sp,
                        color = CyanAccent
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onInstall,
                enabled = !isInstalling,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInstalled) GreenSuccess else BrandRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.height(34.dp)
            ) {
                if (isInstalled) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("ইন্সটলড", fontSize = 11.sp)
                } else {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("ইন্সটল", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun InstalledPluginsContent(
    installedPlugins: List<InstalledPlugin>,
    onToggleEnabled: (name: String, enabled: Boolean) -> Unit,
    onInspect: (InstalledPlugin) -> Unit,
    onViewStreams: (InstalledPlugin) -> Unit,
    onUninstall: (name: String) -> Unit,
    onGoToAddTab: () -> Unit
) {
    if (installedPlugins.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ExtensionOff,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "এখনও কোনো এক্সটেনশন ইন্সটল করা হয়নি",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "'নতুন যোগ (+)' ট্যাব থেকে .cs3 লিঙ্ক দিয়ে যেকোনো প্লাগইন ইন্সটল করে নিন।",
                fontSize = 12.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onGoToAddTab,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("নতুন প্লাগইন যোগ করুন", fontSize = 12.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(installedPlugins, key = { it.name }) { plugin ->
                InstalledPluginCard(
                    plugin = plugin,
                    onToggleEnabled = { onToggleEnabled(plugin.name, it) },
                    onInspect = { onInspect(plugin) },
                    onViewStreams = { onViewStreams(plugin) },
                    onUninstall = { onUninstall(plugin.name) }
                )
            }
        }
    }
}

@Composable
private fun InstalledPluginCard(
    plugin: InstalledPlugin,
    onToggleEnabled: (Boolean) -> Unit,
    onInspect: () -> Unit,
    onViewStreams: () -> Unit,
    onUninstall: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Surface(
        color = CinemaSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!plugin.iconUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = plugin.iconUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = BrandRed, modifier = Modifier.size(24.dp))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = plugin.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "v${plugin.version}",
                            fontSize = 10.sp,
                            color = BrandRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!plugin.description.isNullOrEmpty()) {
                        Text(
                            text = plugin.description,
                            fontSize = 11.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (plugin.pluginClassName.isNotEmpty()) {
                        Text(
                            text = plugin.pluginClassName,
                            fontSize = 9.sp,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandRed,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = CinemaSurface
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = CinemaBorder.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View streams button
                FilledTonalButton(
                    onClick = onViewStreams,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = BrandRed.copy(alpha = 0.15f),
                        contentColor = BrandRed
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("চ্যানেল/স্ট্রিম", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Inspect details
                OutlinedButton(
                    onClick = onInspect,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ফাইল তথ্য", fontSize = 11.sp)
                }

                // Delete / Uninstall
                IconButton(
                    onClick = { showConfirmDelete = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Uninstall",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("এক্সটেনশন আনইন্সটল", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text("আপনি কি নিশ্চিত যে '${plugin.name}' এক্সটেনশনটি মুছে ফেলতে চান?", fontSize = 13.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDelete = false
                        onUninstall()
                    }
                ) {
                    Text("হ্যাঁ, আনইন্সটল করুন", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}

@Composable
private fun InfoBadge(label: String, value: String, isGood: Boolean) {
    Surface(
        color = CinemaSurface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$label: ", fontSize = 10.sp, color = TextMuted)
            Text(
                text = value,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGood) GreenSuccess else Color(0xFFEF4444)
            )
        }
    }
}

@Composable
private fun InspectPluginDialog(plugin: InstalledPlugin, onDismiss: () -> Unit) {
    val dateStr = remember(plugin.installedAtMillis) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(plugin.installedAtMillis))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(plugin.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("ভার্সন", "v${plugin.version}")
                DetailRow("ক্লাস নেম", plugin.pluginClassName.ifEmpty { "N/A" })
                DetailRow("ইন্সটল সময়", dateStr)
                DetailRow("DEX সাইজ", "${(plugin.dexSizeBytes / 1024)} KB")
                DetailRow("CS3 প্যাকেজ সাইজ", "${(plugin.archiveSizeBytes / 1024)} KB")
                DetailRow("টাইপ", plugin.tvTypes.joinToString(", ").ifEmpty { "General" })
                DetailRow("স্টোরেজ ডিরেক্টরি", plugin.manifestPath.substringBeforeLast("/"))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন", color = BrandRed)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.sp, color = TextMuted)
        Text(text = value, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PluginStreamsDialog(
    plugin: InstalledPlugin,
    isLoading: Boolean,
    streams: List<PluginChannelItem>,
    onDismiss: () -> Unit,
    onPlay: (streamUrl: String, title: String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(16.dp),
            color = CinemaSurface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CinemaSurfaceVariant)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LiveTv, contentDescription = null, tint = BrandRed, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${plugin.name} - লাইভ স্ট্রিমসমূহ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${streams.size}টি স্ট্রিম চ্যানেল উপলব্ধ",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                // Body
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandRed)
                    }
                } else if (streams.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("কোনো স্ট্রিম পাওয়া যায়নি", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(streams) { stream ->
                            Surface(
                                color = CinemaSurfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlay(stream.streamUrl, stream.title) },
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!stream.iconUrl.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = stream.iconUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(BrandRed.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BrandRed)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stream.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stream.category,
                                            fontSize = 10.sp,
                                            color = BrandRed
                                        )
                                        if (!stream.description.isNullOrEmpty()) {
                                            Text(
                                                text = stream.description,
                                                fontSize = 9.sp,
                                                color = TextMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { onPlay(stream.streamUrl, stream.title) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play", tint = BrandRed, modifier = Modifier.size(24.dp))
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
