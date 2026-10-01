package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.download.DownloadNotificationHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AppItem(
    val id: String = "",
    val name: String = "",
    val packageName: String = "",
    val iconUrl: String = "",
    val downloadUrl: String = "",
    val description: String = "",
    val version: String = "1.0",
    val size: String = "15 MB",
    val category: String = "ইউটিলিটি",
    val rating: Float = 4.9f,
    val downloadsCount: String = "50K+",
    val releaseDate: String = "2026",
    val isInstalled: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "packageName" to packageName,
        "iconUrl" to iconUrl,
        "downloadUrl" to downloadUrl,
        "description" to description,
        "version" to version,
        "size" to size,
        "category" to category,
        "rating" to rating.toDouble(),
        "downloadsCount" to downloadsCount,
        "releaseDate" to releaseDate
    )
}

data class AppUpdateInfo(
    val versionName: String = "1.0.5",
    val versionCode: Int = 5,
    val downloadUrl: String = "https://github.com/mdmukulahmed01/MukulPlus/releases/latest/download/MukulPlus-latest.apk",
    val releaseNotes: String = "• নতুন OTT রেজুলেশন ড্রপডাউন ও ফাস্ট ডাউনলোডার\n• ইউটিউব দ্রুত ডাউনলোড লিঙ্ক ফিক্স\n• অ্যাপ স্টোর ও অ্যাডমিন প্যানেল যুক্ত করা হয়েছে",
    val forceUpdate: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "versionName" to versionName,
        "versionCode" to versionCode,
        "downloadUrl" to downloadUrl,
        "releaseNotes" to releaseNotes,
        "forceUpdate" to forceUpdate,
        "updatedAt" to updatedAt
    )
}

data class AdminNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val message: String = "",
    val imageUrl: String = "",
    val actionUrl: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "title" to title,
        "message" to message,
        "imageUrl" to imageUrl,
        "actionUrl" to actionUrl,
        "timestamp" to timestamp
    )
}

object AppStoreRepository {
    private const val TAG = "AppStoreRepository"
    private const val PREFS_NAME = "mukul_app_store_prefs"
    private const val KEY_APPS_JSON = "cached_apps_json"
    private const val KEY_UPDATE_JSON = "cached_update_json"
    private const val KEY_NOTIFICATIONS_JSON = "cached_notifications_json"

    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Firestore initialization notice: ${e.message}")
            null
        }
    }

    private val _apps = MutableStateFlow<List<AppItem>>(emptyList())
    val apps: StateFlow<List<AppItem>> = _apps.asStateFlow()

    private val _updateInfo = MutableStateFlow(AppUpdateInfo())
    val updateInfo: StateFlow<AppUpdateInfo> = _updateInfo.asStateFlow()

    private val _notifications = MutableStateFlow<List<AdminNotification>>(emptyList())
    val notifications: StateFlow<List<AdminNotification>> = _notifications.asStateFlow()

    // Default curated apps from Mukul Studio
    private val defaultApps = listOf(
        AppItem(
            id = "app_mukul_ott",
            name = "Mukul Plus OTT",
            packageName = "com.aistudio.mukulplus.otthub",
            iconUrl = "https://i.pinimg.com/736x/73/b3/8e/73b38e78e189145a59d422e8b8bd37d4.jpg",
            downloadUrl = "https://github.com/mdmukulahmed01/MukulPlus/releases/latest/download/MukulPlus-latest.apk",
            description = "বাংলার সেরা OTT ও বিনোদন অ্যাপ। ৪K ও Full HD কোয়ালিটিতে নাটক, সিনেমা ও লাইভ টিভি উপভোগ করুন।",
            version = "v1.0.5",
            size = "24 MB",
            category = "ভিডিও প্লেয়ার",
            rating = 4.9f,
            downloadsCount = "100K+"
        ),
        AppItem(
            id = "app_mukul_sports",
            name = "Mukul Sports Live HD",
            packageName = "com.mukul.sports.live",
            iconUrl = "https://images.unsplash.com/photo-1579952363873-27f3bade9f55?w=400&q=80",
            downloadUrl = "https://github.com/mdmukulahmed01/MukulPlus/releases/latest/download/MukulPlus-latest.apk",
            description = "লাইভ ক্রিকেট, ফুটবল, আইপিএল, বিপিএল ও সকল আন্তর্জাতিক খেলার নিরবচ্ছিন্ন লাইভ স্ট্রিমিং ও স্কোর।",
            version = "v2.1",
            size = "18 MB",
            category = "স্পোর্টস",
            rating = 4.8f,
            downloadsCount = "75K+"
        ),
        AppItem(
            id = "app_mukul_bdix_tv",
            name = "Mukul BDIX TV Player",
            packageName = "com.mukul.bdixtv.player",
            iconUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=400&q=80",
            downloadUrl = "https://github.com/mdmukulahmed01/MukulPlus/releases/latest/download/MukulPlus-latest.apk",
            description = "বাফারবিহীন ১০০+ দেশি ও বিদেশি লাইভ টিভি চ্যানেল BDIX আল্ট্রা স্পিডে দেখার প্লেয়ার।",
            version = "v1.3",
            size = "14 MB",
            category = "লাইভ টিভি",
            rating = 4.7f,
            downloadsCount = "60K+"
        ),
        AppItem(
            id = "app_mukul_downloader",
            name = "Mukul Video Downloader Pro",
            packageName = "com.mukul.downloader.pro",
            iconUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&q=80",
            downloadUrl = "https://github.com/mdmukulahmed01/MukulPlus/releases/latest/download/MukulPlus-latest.apk",
            description = "ইউটিউব, ফেসবুক, ইনস্টাগ্রাম ও টিকটক থেকে ১ ক্লিকে ফুল এইচডি ও এমপিথ্রি গান ডাউনলোড করার টুল।",
            version = "v3.0",
            size = "12 MB",
            category = "টুলস ও ডাউনলোডার",
            rating = 4.9f,
            downloadsCount = "90K+"
        ),
        AppItem(
            id = "app_mukul_browser",
            name = "Mukul Fast Secure Browser",
            packageName = "com.mukul.browser.fast",
            iconUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=400&q=80",
            downloadUrl = "https://github.com/mdmukulahmed01/MukulPlus/releases/latest/download/MukulPlus-latest.apk",
            description = "অ্যাড-ব্লকার ও ভিডিও স্নাইফার সহ আল্ট্রা ফাস্ট লাইটওয়েট নিরাপদ ব্রাউজার।",
            version = "v1.8",
            size = "9 MB",
            category = "ব্রাউজার",
            rating = 4.6f,
            downloadsCount = "35K+"
        )
    )

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load local apps cache or default apps
        val savedAppsJson = prefs.getString(KEY_APPS_JSON, null)
        if (!savedAppsJson.isNullOrEmpty()) {
            try {
                val list = parseAppsJson(savedAppsJson)
                _apps.value = if (list.isNotEmpty()) list else defaultApps
            } catch (_: Exception) {
                _apps.value = defaultApps
            }
        } else {
            _apps.value = defaultApps
            saveAppsLocally(context, defaultApps)
        }

        // Load local update info cache
        val savedUpdateJson = prefs.getString(KEY_UPDATE_JSON, null)
        if (!savedUpdateJson.isNullOrEmpty()) {
            try {
                _updateInfo.value = parseUpdateJson(savedUpdateJson)
            } catch (_: Exception) {}
        }

        // Sync with Firebase in background
        syncFromFirebase(context)
    }

    private fun syncFromFirebase(context: Context) {
        val db = firestore ?: return
        try {
            // Listen for Apps updates in Firestore
            db.collection("our_apps").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Firestore apps listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    val remoteApps = snapshot.documents.mapNotNull { doc ->
                        try {
                            AppItem(
                                id = doc.getString("id") ?: doc.id,
                                name = doc.getString("name") ?: "",
                                packageName = doc.getString("packageName") ?: "",
                                iconUrl = doc.getString("iconUrl") ?: "",
                                downloadUrl = doc.getString("downloadUrl") ?: "",
                                description = doc.getString("description") ?: "",
                                version = doc.getString("version") ?: "1.0",
                                size = doc.getString("size") ?: "15 MB",
                                category = doc.getString("category") ?: "ইউটিলিটি",
                                rating = (doc.getDouble("rating") ?: 4.8).toFloat(),
                                downloadsCount = doc.getString("downloadsCount") ?: "10K+",
                                releaseDate = doc.getString("releaseDate") ?: "2026"
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (remoteApps.isNotEmpty()) {
                        _apps.value = remoteApps
                        saveAppsLocally(context, remoteApps)
                    }
                }
            }

            // Listen for App Update Info
            db.collection("app_settings").document("update_info")
                .addSnapshotListener { doc, error ->
                    if (error != null || doc == null || !doc.exists()) return@addSnapshotListener
                    try {
                        val update = AppUpdateInfo(
                            versionName = doc.getString("versionName") ?: "1.0",
                            versionCode = (doc.getLong("versionCode") ?: 1L).toInt(),
                            downloadUrl = doc.getString("downloadUrl") ?: "",
                            releaseNotes = doc.getString("releaseNotes") ?: "",
                            forceUpdate = doc.getBoolean("forceUpdate") ?: false,
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                        _updateInfo.value = update
                        saveUpdateLocally(context, update)
                    } catch (_: Exception) {}
                }

            // Listen for Broadcast Notifications
            db.collection("broadcast_notifications")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val notifs = snapshot.documents.mapNotNull { doc ->
                        try {
                            AdminNotification(
                                id = doc.getString("id") ?: doc.id,
                                title = doc.getString("title") ?: "",
                                message = doc.getString("message") ?: "",
                                imageUrl = doc.getString("imageUrl") ?: "",
                                actionUrl = doc.getString("actionUrl") ?: "",
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            )
                        } catch (_: Exception) { null }
                    }
                    _notifications.value = notifs
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error initiating Firebase sync: ${e.message}")
        }
    }

    // --- Admin Operations ---

    suspend fun addApp(context: Context, app: AppItem): Boolean = withContext(Dispatchers.IO) {
        val appWithId = if (app.id.isBlank()) app.copy(id = "app_${System.currentTimeMillis()}") else app
        val current = _apps.value.toMutableList()
        current.removeAll { it.id == appWithId.id }
        current.add(0, appWithId)
        _apps.value = current
        saveAppsLocally(context, current)

        // Save to Firebase
        val db = firestore
        if (db != null) {
            try {
                db.collection("our_apps").document(appWithId.id)
                    .set(appWithId.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upload app to Firestore: ${e.message}")
            }
        }
        true
    }

    suspend fun updateApp(context: Context, app: AppItem): Boolean = withContext(Dispatchers.IO) {
        addApp(context, app)
    }

    suspend fun deleteApp(context: Context, appId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _apps.value.filter { it.id != appId }
        _apps.value = current
        saveAppsLocally(context, current)

        val db = firestore
        if (db != null) {
            try {
                db.collection("our_apps").document(appId).delete().await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete app from Firestore: ${e.message}")
            }
        }
        true
    }

    suspend fun setAppUpdate(context: Context, update: AppUpdateInfo): Boolean = withContext(Dispatchers.IO) {
        _updateInfo.value = update
        saveUpdateLocally(context, update)

        val db = firestore
        if (db != null) {
            try {
                db.collection("app_settings").document("update_info")
                    .set(update.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set update info in Firestore: ${e.message}")
            }
        }
        true
    }

    suspend fun sendBroadcastNotification(context: Context, notification: AdminNotification): Boolean = withContext(Dispatchers.IO) {
        val current = _notifications.value.toMutableList()
        current.add(0, notification)
        _notifications.value = current

        // Trigger local notification to display rich image & play/action button
        try {
            val posterBitmap = if (notification.imageUrl.isNotBlank()) {
                DownloadNotificationHelper.loadPosterBitmap(context, notification.imageUrl)
            } else null

            DownloadNotificationHelper.showNewMovieNotification(
                context = context,
                title = notification.title,
                posterBitmap = posterBitmap,
                quality = "Update",
                slug = notification.actionUrl.ifEmpty { "app_update" }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error displaying broadcast notification: ${e.message}")
        }

        // Upload to Firebase
        val db = firestore
        if (db != null) {
            try {
                db.collection("broadcast_notifications").document(notification.id)
                    .set(notification.toMap(), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save notification to Firestore: ${e.message}")
            }
        }
        true
    }

    // --- Local Persistence Helpers ---

    private fun saveAppsLocally(context: Context, list: List<AppItem>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { app ->
                val obj = JSONObject(app.toMap())
                jsonArray.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_APPS_JSON, jsonArray.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun parseAppsJson(jsonStr: String): List<AppItem> {
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<AppItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                AppItem(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    packageName = obj.optString("packageName"),
                    iconUrl = obj.optString("iconUrl"),
                    downloadUrl = obj.optString("downloadUrl"),
                    description = obj.optString("description"),
                    version = obj.optString("version", "1.0"),
                    size = obj.optString("size", "15 MB"),
                    category = obj.optString("category", "ইউটিলিটি"),
                    rating = obj.optDouble("rating", 4.8).toFloat(),
                    downloadsCount = obj.optString("downloadsCount", "10K+"),
                    releaseDate = obj.optString("releaseDate", "2026")
                )
            )
        }
        return list
    }

    private fun saveUpdateLocally(context: Context, update: AppUpdateInfo) {
        try {
            val obj = JSONObject(update.toMap())
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_UPDATE_JSON, obj.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun parseUpdateJson(jsonStr: String): AppUpdateInfo {
        val obj = JSONObject(jsonStr)
        return AppUpdateInfo(
            versionName = obj.optString("versionName", "1.0"),
            versionCode = obj.optInt("versionCode", 1),
            downloadUrl = obj.optString("downloadUrl", ""),
            releaseNotes = obj.optString("releaseNotes", ""),
            forceUpdate = obj.optBoolean("forceUpdate", false),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
