package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class SportsCategoryType(
    val title: String,
    val endpointUrl: String,
    val icon: ImageVector,
    val subtitle: String,
    val badge: String
) {
    REPLAYS(
        title = "হাইলাইটস (Highlights)",
        endpointUrl = "https://mukul-sports.ai.studio/api/replays/replays.txt",
        icon = Icons.Default.History,
        subtitle = "সকল খেলার হাইলাইটস ও রিপ্লে ম্যাচ",
        badge = "REPLAY"
    ),
    LIVE_CRICKET(
        title = "লাইভ ও আপকামিং ক্রিকেট",
        endpointUrl = "https://raw.githubusercontent.com/srhady/willow-event/refs/heads/main/live_sports.json",
        icon = Icons.Default.SportsCricket,
        subtitle = "লাইভ ও আসন্ন ক্রিকেট ম্যাচের সূচী ও স্ট্রিম",
        badge = "LIVE"
    ),
    LEAGUE_LIVE(
        title = "লীগ লাইভ (League)",
        endpointUrl = "https://livestreamcricket.cc/cdlive-api/show-match-data.php?play=",
        icon = Icons.Default.EmojiEvents,
        subtitle = "আন্তর্জাতিক ও ঘরোয়া টুর্নামেন্ট লাইভ",
        badge = "LEAGUE"
    ),
    FREE_LIVE_SPORTS(
        title = "ফ্রি লাইভ স্পোর্টস",
        endpointUrl = "https://ga-prod-api.powr.tv/v2/sites/freelivesports/live-channels/",
        icon = Icons.Default.LiveTv,
        subtitle = "ফ্রি স্পোর্টস লাইভ টিভি চ্যানেলসমূহ",
        badge = "TV"
    ),
    WILLOW_EVENTS(
        title = "উইলো ইভেন্টস চ্যানেল",
        endpointUrl = "https://pantyflix.com/api/streamed/stream/admin/admin-willow-cricket",
        icon = Icons.Default.FlashOn,
        subtitle = "উইলো ক্রিকেট লাইভ ইভেন্টস স্ট্রিম",
        badge = "WILLOW"
    )
}

data class SportsMatchItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val categoryType: SportsCategoryType,
    val streamUrl: String,
    val posterUrl: String = "",
    val isLive: Boolean = false,
    val statusText: String = "",
    val timeOrDate: String = "",
    val league: String = "",
    val teams: Pair<String, String>? = null,
    val quality: String = "HD",
    val headers: Map<String, String> = emptyMap(),
    val servers: List<Pair<String, String>> = emptyList(),
    val isWebEmbed: Boolean = false,
    val viewers: Int = 0
)
