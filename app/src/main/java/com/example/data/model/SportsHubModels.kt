package com.example.data.model

/**
 * SportsHub model — Mukul plus-এর নিজস্ব স্পোর্টস সেকশনের ডেটা স্ট্রাকচার।
 *
 * ডিজাইন নীতি:
 *  - কোনো স্ট্রিম URL বা সোর্স এখানে হার্ডকোড করা নেই; সবকিছু সোর্স কনফিগ
 *    (JSON) থেকে আসে, যা ব্যবহারকারী/ডেভেলপার নিজের ব্যাকএন্ডে হোস্ট করে।
 *  - প্রতিটি ম্যাচে একাধিক সার্ভার থাকতে পারে, তাই player-এ fallback সম্ভব।
 */
data class SportsHubSource(
    val id: String,
    val name: String,
    val kind: SportsSourceKind,
    val url: String,
    val enabled: Boolean = true,
    val notes: String = ""
)

enum class SportsSourceKind {
    /** একটি JSON অ্যারে — SportsMatchItem লিস্ট */
    JSON,
    /** সরাসরি .m3u8 / .mp4 লিংকের তালিকা (এক লাইনে একটি) */
    PLAYLIST,
    /** শুধুই HTTP লিংক (embed) — অ্যাপের বাইরে/WebView-এ খুলবে */
    EMBED
}

data class SportsMatch(
    val id: String,
    val title: String,
    val teamA: String = "",
    val teamB: String = "",
    val league: String = "",
    val subtitle: String = "",
    val startTimeEpochMs: Long = 0L,
    val status: SportsMatchStatus = SportsMatchStatus.UPCOMING,
    val posterUrl: String = "",
    val streams: List<SportsStream> = emptyList(),
    val sourceId: String = ""
) {
    val isLive: Boolean get() = status == SportsMatchStatus.LIVE
}

data class SportsStream(
    val label: String,
    val url: String,
    val quality: String = "HD",
    /** true হলে ExoPlayer-এর বদলে বাহ্যিক ব্রাউজার/WebView-এ খুলবে */
    val isEmbed: Boolean = false
)

enum class SportsMatchStatus {
    LIVE, UPCOMING, REPLAY, FINISHED
}
