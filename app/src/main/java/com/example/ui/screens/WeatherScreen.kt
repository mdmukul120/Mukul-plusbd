package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.WeatherApiClient
import com.example.data.model.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    onNavigateHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedLocation by remember { mutableStateOf(WeatherApiClient.defaultLocations.first()) }
    var weatherReport by remember { mutableStateOf<CurrentWeatherReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCelsius by remember { mutableStateOf(true) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<WeatherLocation>>(emptyList()) }
    var showSearchSheet by remember { mutableStateOf(false) }

    BackHandler {
        onNavigateHome()
    }

    fun loadWeather(location: WeatherLocation) {
        selectedLocation = location
        isLoading = true
        coroutineScope.launch {
            try {
                weatherReport = WeatherApiClient.fetchWeather(context, location)
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedLocation) {
        loadWeather(selectedLocation)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CinemaBackground,
        topBar = {
            Surface(
                color = CinemaSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(52.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateHome) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BrandRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB020),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "আবহাওয়া বার্তা",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedLocation.nameBangla} (${selectedLocation.nameEnglish})",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Unit toggle (°C / °F)
                    Surface(
                        onClick = { isCelsius = !isCelsius },
                        shape = RoundedCornerShape(8.dp),
                        color = CinemaSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "°C",
                                color = if (isCelsius) BrandRedLight else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isCelsius) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = " / ",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "°F",
                                color = if (!isCelsius) BrandRedLight else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (!isCelsius) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Search button
                    IconButton(
                        onClick = { showSearchSheet = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Refresh button
                    IconButton(
                        onClick = { loadWeather(selectedLocation) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading && weatherReport == null) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "${selectedLocation.nameBangla} এর আবহাওয়া লোড হচ্ছে...",
                        color = TextSecondary,
                        fontSize = 13.5.sp
                    )
                }
            } else {
                val report = weatherReport
                if (report != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Division / City Quick Chips
                        item {
                            CitySelectionRow(
                                locations = WeatherApiClient.defaultLocations,
                                selected = selectedLocation,
                                onSelect = { loc ->
                                    if (loc != selectedLocation) {
                                        loadWeather(loc)
                                    }
                                }
                            )
                        }

                        // 2. Main Hero Weather Card
                        item {
                            MainHeroWeatherCard(
                                report = report,
                                isCelsius = isCelsius
                            )
                        }

                        // 3. Hourly Forecast (২৪ ঘণ্টার ঘণ্টাওয়ারি পূর্বাভাস)
                        item {
                            HourlyForecastSection(
                                hourlyList = report.hourlyForecast,
                                isCelsius = isCelsius
                            )
                        }

                        // 4. 7-Day Extended Forecast (৭ দিনের পূর্বাভাস)
                        item {
                            DailyForecastSection(
                                dailyList = report.dailyForecast,
                                isCelsius = isCelsius
                            )
                        }

                        // 5. Weather Key Metrics Grid (আবহাওয়ার বিশদ তথ্য)
                        item {
                            WeatherMetricsGrid(report = report)
                        }

                        // 6. Smart Advisories & Health Alerts (পরামর্শ ও সতর্কতা)
                        if (report.advisories.isNotEmpty()) {
                            item {
                                WeatherAdvisorySection(advisories = report.advisories)
                            }
                        }
                    }
                }
            }
        }
    }

    // City Search Bottom Sheet
    if (showSearchSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSearchSheet = false
                searchQuery = ""
                searchResults = emptyList()
            },
            containerColor = CinemaSurface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextMuted) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "শহর বা জেলা খুঁজুন (Search City)",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { q ->
                        searchQuery = q
                        if (q.length >= 2) {
                            coroutineScope.launch {
                                isSearching = true
                                searchResults = WeatherApiClient.searchCities(q)
                                isSearching = false
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    placeholder = { Text("যেমন: Dhaka, Chittagong, Sylhet...", color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; searchResults = emptyList() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandRed,
                        unfocusedBorderColor = CinemaBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandRed, modifier = Modifier.size(28.dp))
                    }
                } else if (searchResults.isNotEmpty()) {
                    Text(
                        text = "ফলাফল (${searchResults.size}):",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(searchResults) { loc ->
                            Surface(
                                onClick = {
                                    showSearchSheet = false
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    loadWeather(loc)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = CinemaSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = BrandRed, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = loc.nameBangla, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text(text = "${loc.nameEnglish}, ${loc.country}", color = TextMuted, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                } else if (searchQuery.length >= 2) {
                    Text(
                        text = "কোন শহর খুঁজে পাওয়া যায়নি",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Horizontal Quick City Selector Chips
 */
@Composable
private fun CitySelectionRow(
    locations: List<WeatherLocation>,
    selected: WeatherLocation,
    onSelect: (WeatherLocation) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        locations.forEach { loc ->
            val isCurrent = loc.latitude == selected.latitude && loc.longitude == selected.longitude
            Surface(
                onClick = { onSelect(loc) },
                shape = RoundedCornerShape(20.dp),
                color = if (isCurrent) BrandRed else CinemaSurfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCurrent) BrandRedLight else CinemaBorder
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = loc.nameBangla,
                        color = if (isCurrent) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * Main Hero Weather Card with dynamic gradients
 */
@Composable
private fun MainHeroWeatherCard(
    report: CurrentWeatherReport,
    isCelsius: Boolean
) {
    val code = report.weatherCode
    val isDay = report.isDay

    val cardBrush = remember(code, isDay) {
        when {
            code in 95..99 -> Brush.verticalGradient(
                listOf(Color(0xFF2C1947), Color(0xFF1B1B2F), Color(0xFF161626))
            )
            code in 51..67 || code in 80..82 -> Brush.verticalGradient(
                listOf(Color(0xFF0F3057), Color(0xFF00587A), Color(0xFF1A1A24))
            )
            code in 45..48 -> Brush.verticalGradient(
                listOf(Color(0xFF393E46), Color(0xFF222831), Color(0xFF141419))
            )
            !isDay -> Brush.verticalGradient(
                listOf(Color(0xFF141E30), Color(0xFF243B55), Color(0xFF0F172A))
            )
            else -> Brush.verticalGradient(
                listOf(Color(0xFF1D5AAB), Color(0xFF2D7CD8), Color(0xFF0F2038))
            )
        }
    }

    val displayTemp = formatTemp(if (isCelsius) report.tempC else cToF(report.tempC), isCelsius)
    val displayFeelsLike = formatTemp(if (isCelsius) report.feelsLikeC else cToF(report.feelsLikeC), isCelsius)
    val displayMin = formatTemp(if (isCelsius) report.minTempC else cToF(report.minTempC), isCelsius)
    val displayMax = formatTemp(if (isCelsius) report.maxTempC else cToF(report.maxTempC), isCelsius)

    Card(
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBrush)
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Row: Location & Live Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFFFB020),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${report.location.nameBangla} (${report.location.nameEnglish})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF10B981),
                                modifier = Modifier.size(6.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "লাইভ",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Center Big Temperature & Condition
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = displayTemp,
                            color = Color.White,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = report.conditionBangla,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "অনুভূত তাপমাত্রা: $displayFeelsLike",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 12.sp
                        )
                    }

                    // Weather Icon & Temp Range Capsule
                    Column(horizontalAlignment = Alignment.End) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = getWeatherIconVector(report.weatherCode, report.isDay),
                                    contentDescription = report.conditionEnglish,
                                    tint = if (report.weatherCode in 0..1 && report.isDay) Color(0xFFFFB020) else Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "সর্বনিম্ন: $displayMin • সর্বোচ্চ: $displayMax",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))

                // Quick metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickMetricItem(
                        icon = Icons.Default.WaterDrop,
                        label = "বৃষ্টির সম্ভাবনা",
                        value = "${report.precipitationProbability}%"
                    )
                    QuickMetricItem(
                        icon = Icons.Default.Cloud,
                        label = "আর্দ্রতা",
                        value = "${report.humidity}%"
                    )
                    QuickMetricItem(
                        icon = Icons.Default.Air,
                        label = "বাতাস",
                        value = "${report.windSpeedKmh.toInt()} কিমি/ঘ"
                    )
                    QuickMetricItem(
                        icon = Icons.Default.WbSunny,
                        label = "ইউভি",
                        value = String.format(Locale.US, "%.1f", report.uvIndex)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickMetricItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.5.sp
        )
    }
}

/**
 * 24-Hour Forecast Horizontal Carousel
 */
@Composable
private fun HourlyForecastSection(
    hourlyList: List<HourlyWeather>,
    isCelsius: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "২৪ ঘণ্টার ঘণ্টাওয়ারি পূর্বাভাস",
                color = TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(hourlyList) { item ->
                val tempStr = formatTemp(if (isCelsius) item.tempC else cToF(item.tempC), isCelsius)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CinemaSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
                    modifier = Modifier.width(76.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.timeFormatted,
                            color = TextSecondary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Icon(
                            imageVector = getWeatherIconVector(item.weatherCode, item.isDay),
                            contentDescription = null,
                            tint = if (item.weatherCode in 0..1 && item.isDay) Color(0xFFFFB020) else CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tempStr,
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.rainProbability > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WaterDrop, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(9.dp))
                                Text(
                                    text = "${item.rainProbability}%",
                                    color = CyanAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 7-Day Extended Weather Forecast
 */
@Composable
private fun DailyForecastSection(
    dailyList: List<DailyWeather>,
    isCelsius: Boolean
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "৭ দিনের বিস্তারিত পূর্বাভাস",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            dailyList.forEachIndexed { index, day ->
                val minStr = formatTemp(if (isCelsius) day.minTempC else cToF(day.minTempC), isCelsius)
                val maxStr = formatTemp(if (isCelsius) day.maxTempC else cToF(day.maxTempC), isCelsius)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = day.dayNameBangla,
                        color = if (index == 0) BrandRedLight else TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.width(78.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = getWeatherIconVector(day.weatherCode, true),
                            contentDescription = null,
                            tint = if (day.weatherCode in 0..1) Color(0xFFFFB020) else CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = day.conditionBangla,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (day.rainProbability > 10) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(42.dp)
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(10.dp))
                            Text(
                                text = "${day.rainProbability}%",
                                color = CyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(42.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(88.dp)
                    ) {
                        Text(
                            text = minStr,
                            color = TextMuted,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = " — ",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Text(
                            text = maxStr,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (index < dailyList.size - 1) {
                    HorizontalDivider(color = CinemaBorder.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

/**
 * 2-Column Grid of In-Depth Weather Indicators
 */
@Composable
private fun WeatherMetricsGrid(report: CurrentWeatherReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Analytics, contentDescription = null, tint = BrandRed, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "আবহাওয়া বিশদ সূচক",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 1: Humidity & Wind
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                icon = Icons.Default.WaterDrop,
                iconTint = CyanAccent,
                title = "আর্দ্রতা (Humidity)",
                value = "${report.humidity}%",
                subtitle = if (report.humidity > 70) "স্যাঁতসেঁতে পরিবেশ" else "আরামদায়ক আবহাওয়া",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                icon = Icons.Default.Air,
                iconTint = Color(0xFF64B5F6),
                title = "বাতাসের গতি",
                value = "${report.windSpeedKmh.toInt()} কিমি/ঘণ্টা",
                subtitle = "দিক: ${getWindDirectionLabel(report.windDirectionDeg)}",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: UV Index & AQI
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                icon = Icons.Default.WbSunny,
                iconTint = Color(0xFFFFB020),
                title = "ইউভি সূচক (UV)",
                value = String.format(Locale.US, "%.1f", report.uvIndex),
                subtitle = getUvAdviceLabel(report.uvIndex),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                icon = Icons.Default.Spa,
                iconTint = getAqiColor(report.aqi),
                title = "বায়ুমান সূচক (AQI)",
                value = "${report.aqi}",
                subtitle = report.aqiStatusBangla,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 3: Pressure & Sunrise/Sunset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                icon = Icons.Default.Compress,
                iconTint = Color(0xFF90CAF9),
                title = "বায়ুমণ্ডলীয় চাপ",
                value = "${report.pressureHpa.toInt()} hPa",
                subtitle = "স্বাভাবিক সমুদ্রতল চাপ",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                icon = Icons.Default.WbTwilight,
                iconTint = Color(0xFFFF8A65),
                title = "সূর্যোদয় ও সূর্যাস্ত",
                value = report.sunrise,
                subtitle = "সূর্যাস্ত: ${report.sunset}",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = iconTint.copy(alpha = 0.15f),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = TextMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Smart Weather Advice & Recommendations Cards
 */
@Composable
private fun WeatherAdvisorySection(advisories: List<WeatherAdvice>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.TipsAndUpdates, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "আবহাওয়া ভিত্তিক পরামর্শ ও সতর্কতা",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        advisories.forEach { adv ->
            val icon = when (adv.category) {
                "UMBRELLA" -> Icons.Default.Umbrella
                "CLOTHING" -> Icons.Default.Checkroom
                "HEALTH" -> Icons.Default.Favorite
                "TRAVEL" -> Icons.Default.DirectionsCar
                else -> Icons.Default.Info
            }

            val badgeColor = when (adv.alertLevel) {
                AlertLevel.SEVERE -> BrandRed
                AlertLevel.WARNING -> Color(0xFFFF9800)
                AlertLevel.INFO -> CyanAccent
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CinemaSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = CircleShape,
                        color = badgeColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = adv.title,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = adv.description,
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Helper formatting & icons
// -------------------------------------------------------------

private fun cToF(celsius: Double): Double = (celsius * 9.0 / 5.0) + 32.0

private fun formatTemp(temp: Double, isCelsius: Boolean): String {
    return "${temp.toInt()}°${if (isCelsius) "C" else "F"}"
}

private fun getWeatherIconVector(code: Int, isDay: Boolean): ImageVector {
    return when (code) {
        0 -> if (isDay) Icons.Default.WbSunny else Icons.Default.NightsStay
        1, 2 -> if (isDay) Icons.Default.WbSunny else Icons.Default.Nightlight
        3 -> Icons.Default.Cloud
        45, 48 -> Icons.Default.Dehaze
        51, 53, 55 -> Icons.Default.Grain
        61, 63, 65, 80, 81, 82 -> Icons.Default.WaterDrop
        71, 73, 75 -> Icons.Default.AcUnit
        95, 96, 99 -> Icons.Default.FlashOn
        else -> Icons.Default.WbCloudy
    }
}

private fun getWindDirectionLabel(deg: Int): String {
    return when (deg) {
        in 338..360, in 0..22 -> "উত্তর (North)"
        in 23..67 -> "উত্তর-পূর্ব (NE)"
        in 68..112 -> "পূর্ব (East)"
        in 113..157 -> "দক্ষিণ-পূর্ব (SE)"
        in 158..202 -> "দক্ষিণ (South)"
        in 203..247 -> "দক্ষিণ-পশ্চিম (SW)"
        in 248..292 -> "পশ্চিম (West)"
        in 293..337 -> "উত্তর-পশ্চিম (NW)"
        else -> "শান্ত বাতাস"
    }
}

private fun getUvAdviceLabel(uv: Double): String {
    return when {
        uv < 3.0 -> "নিরাপদ মাত্রা (Low)"
        uv < 6.0 -> "মাঝারি - সানগ্লাস পরুন"
        uv < 8.0 -> "উচ্চ ঝুঁকি - ছাতা ব্যবহার করুন"
        else -> "অত্যন্ত বিপজ্জনক রোদ"
    }
}

private fun getAqiColor(aqi: Int): Color {
    return when {
        aqi <= 50 -> Color(0xFF10B981)
        aqi <= 100 -> Color(0xFFFFB020)
        aqi <= 150 -> Color(0xFFFF9800)
        else -> BrandRed
    }
}
