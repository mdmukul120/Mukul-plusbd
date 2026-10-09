package com.example.data.extension

import android.content.Context
import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class ExtensionManager private constructor(private val context: Context) {

    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    private val storageDir: File = File(context.filesDir, "cloudstream_extensions").apply {
        if (!exists()) mkdirs()
    }
    private val dbFile: File = File(storageDir, "installed_plugins.json")

    init {
        loadInstalledPlugins()
    }

    private fun loadInstalledPlugins() {
        try {
            if (dbFile.exists()) {
                val jsonStr = dbFile.readText(Charsets.UTF_8)
                val jsonArr = JSONArray(jsonStr)
                val list = mutableListOf<InstalledPlugin>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val tvTypesList = mutableListOf<String>()
                    val tvTypesArr = obj.optJSONArray("tvTypes")
                    if (tvTypesArr != null) {
                        for (j in 0 until tvTypesArr.length()) {
                            tvTypesList.add(tvTypesArr.getString(j))
                        }
                    }
                    list.add(
                        InstalledPlugin(
                            name = obj.getString("name"),
                            version = obj.optInt("version", 1),
                            pluginClassName = obj.optString("pluginClassName", ""),
                            sourceUrl = obj.optString("sourceUrl", ""),
                            installedAtMillis = obj.optLong("installedAtMillis", System.currentTimeMillis()),
                            dexPath = obj.optString("dexPath", ""),
                            manifestPath = obj.optString("manifestPath", ""),
                            dexSizeBytes = obj.optLong("dexSizeBytes", 0L),
                            archiveSizeBytes = obj.optLong("archiveSizeBytes", 0L),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            iconUrl = obj.optString("iconUrl").takeIf { it.isNotEmpty() },
                            description = obj.optString("description").takeIf { it.isNotEmpty() },
                            tvTypes = tvTypesList,
                            author = obj.optString("author").takeIf { it.isNotEmpty() }
                        )
                    )
                }
                _installedPlugins.value = list
            }
        } catch (e: Exception) {
            Log.e("ExtensionManager", "Error loading installed plugins", e)
        }
    }

    private fun saveInstalledPlugins() {
        try {
            val jsonArr = JSONArray()
            _installedPlugins.value.forEach { plugin ->
                val obj = JSONObject().apply {
                    put("name", plugin.name)
                    put("version", plugin.version)
                    put("pluginClassName", plugin.pluginClassName)
                    put("sourceUrl", plugin.sourceUrl)
                    put("installedAtMillis", plugin.installedAtMillis)
                    put("dexPath", plugin.dexPath)
                    put("manifestPath", plugin.manifestPath)
                    put("dexSizeBytes", plugin.dexSizeBytes)
                    put("archiveSizeBytes", plugin.archiveSizeBytes)
                    put("isEnabled", plugin.isEnabled)
                    put("iconUrl", plugin.iconUrl ?: "")
                    put("description", plugin.description ?: "")
                    val typesArr = JSONArray()
                    plugin.tvTypes.forEach { typesArr.put(it) }
                    put("tvTypes", typesArr)
                    put("author", plugin.author ?: "")
                }
                jsonArr.put(obj)
            }
            dbFile.writeText(jsonArr.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("ExtensionManager", "Error saving installed plugins", e)
        }
    }

    suspend fun analyzeUrl(inputUrl: String): PluginAnalysisState = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.isEmpty()) {
            return@withContext PluginAnalysisState.Error("অনুগ্রহ করে একটি সঠিক URL দিন")
        }

        try {
            // Check if user entered a GitHub repo page or plugins.json
            val effectiveUrl = when {
                trimmed.endsWith(".cs3", ignoreCase = true) -> trimmed
                trimmed.endsWith("plugins.json", ignoreCase = true) -> trimmed
                trimmed.contains("github.com", ignoreCase = true) && !trimmed.contains("raw.githubusercontent.com") -> {
                    // Convert github repo link to raw builds/plugins.json if possible
                    if (trimmed.endsWith("/")) trimmed.substringBeforeLast("/") + "/raw/builds/plugins.json"
                    else "$trimmed/raw/builds/plugins.json"
                }
                else -> trimmed
            }

            if (effectiveUrl.endsWith(".cs3", ignoreCase = true) || effectiveUrl.contains(".cs3?")) {
                // Analyze single CS3 archive
                return@withContext analyzeCs3Url(effectiveUrl)
            } else {
                // Attempt to fetch as JSON (plugins.json or repo)
                val (statusCode, bodyText) = httpGetText(effectiveUrl)
                if (statusCode in 200..299 && bodyText.trim().startsWith("[")) {
                    val plugins = parsePluginsJson(bodyText)
                    if (plugins.isNotEmpty()) {
                        return@withContext PluginAnalysisState.RepoList(effectiveUrl, plugins)
                    }
                }

                // If not JSON, maybe it redirects to a .cs3 or content-disposition
                val cs3Result = analyzeCs3Url(effectiveUrl)
                if (cs3Result !is PluginAnalysisState.Error) {
                    return@withContext cs3Result
                }

                return@withContext PluginAnalysisState.Error(
                    "লিংকটি থেকে কোনো বৈধ CloudStream .cs3 ফাইল বা plugins.json রিপোজিটরি পাওয়া যায়নি।"
                )
            }
        } catch (e: Exception) {
            Log.e("ExtensionManager", "Failed to analyze link", e)
            return@withContext PluginAnalysisState.Error("লিংক বিশ্লেষণে ত্রুটি হয়েছে: ${e.localizedMessage ?: "নেটওয়ার্ক ত্রুটি"}")
        }
    }

    suspend fun analyzeCs3Url(url: String): PluginAnalysisState = withContext(Dispatchers.IO) {
        try {
            val bytes = httpGetBytes(url)
            if (bytes.isEmpty()) {
                return@withContext PluginAnalysisState.Error("ফাইলটি ডাউনলোড করা যায়নি বা ফাইল শূন্য।")
            }

            var manifestJson = ""
            var hasClassesDex = false
            var dexSize = 0L

            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name.equals("manifest.json", ignoreCase = true) -> {
                            manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        entry.name.equals("classes.dex", ignoreCase = true) -> {
                            hasClassesDex = true
                            dexSize = entry.size.takeIf { it > 0 } ?: entry.compressedSize
                            if (dexSize <= 0) {
                                val dexBytes = zis.readBytes()
                                dexSize = dexBytes.size.toLong()
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (manifestJson.isEmpty() && !hasClassesDex) {
                return@withContext PluginAnalysisState.Error("ফাইলটিতে কোনো manifest.json বা classes.dex পাওয়া যায়নি। এটি সঠিক .cs3 ফাইল নয়।")
            }

            val manifest = parseManifest(manifestJson, url)

            return@withContext PluginAnalysisState.SingleCs3(
                url = url,
                manifest = manifest,
                archiveSizeBytes = bytes.size.toLong(),
                dexSizeBytes = dexSize,
                hasClassesDex = hasClassesDex,
                suggestedIconUrl = if (manifest.name.contains("Sport", ignoreCase = true)) {
                    "https://www.thestatesman.com/wp-content/uploads/2021/05/fancode.jpg"
                } else null,
                suggestedDescription = "Cloudstream Extension (${manifest.name})",
                tvTypes = if (manifest.name.contains("Sport", ignoreCase = true) || manifest.name.contains("IPTV", ignoreCase = true)) {
                    listOf("Live", "Sports")
                } else listOf("Movie", "TvSeries")
            )
        } catch (e: Exception) {
            Log.e("ExtensionManager", "Error parsing CS3", e)
            return@withContext PluginAnalysisState.Error("CS3 বিশ্লেষণ ব্যর্থ হয়েছে: ${e.message}")
        }
    }

    private fun parseManifest(jsonStr: String, fallbackUrl: String): Cs3Manifest {
        return try {
            if (jsonStr.isNotEmpty()) {
                val obj = JSONObject(jsonStr)
                Cs3Manifest(
                    pluginClassName = obj.optString("pluginClassName", ""),
                    name = obj.optString("name", fallbackUrl.substringAfterLast("/").substringBefore(".cs3")),
                    version = obj.optInt("version", 1),
                    requiresResources = obj.optBoolean("requiresResources", false),
                    author = obj.optString("author", null),
                    description = obj.optString("description", null),
                    rawJson = jsonStr
                )
            } else {
                Cs3Manifest(
                    name = fallbackUrl.substringAfterLast("/").substringBefore(".cs3"),
                    version = 1,
                    rawJson = "{}"
                )
            }
        } catch (e: Exception) {
            Cs3Manifest(
                name = fallbackUrl.substringAfterLast("/").substringBefore(".cs3"),
                version = 1,
                rawJson = jsonStr
            )
        }
    }

    private fun parsePluginsJson(jsonStr: String): List<CloudstreamPlugin> {
        val list = mutableListOf<CloudstreamPlugin>()
        try {
            val jsonArr = JSONArray(jsonStr)
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.getJSONObject(i)
                val authorsList = mutableListOf<String>()
                val authorsArr = obj.optJSONArray("authors")
                if (authorsArr != null) {
                    for (j in 0 until authorsArr.length()) {
                        authorsList.add(authorsArr.getString(j))
                    }
                }
                val tvTypesList = mutableListOf<String>()
                val tvTypesArr = obj.optJSONArray("tvTypes")
                if (tvTypesArr != null) {
                    for (j in 0 until tvTypesArr.length()) {
                        tvTypesList.add(tvTypesArr.getString(j))
                    }
                }

                list.add(
                    CloudstreamPlugin(
                        name = obj.optString("name", "Unknown Plugin"),
                        internalName = obj.optString("internalName").takeIf { it.isNotEmpty() },
                        version = obj.optInt("version", 1),
                        url = obj.optString("url", ""),
                        authors = authorsList,
                        description = obj.optString("description").takeIf { it.isNotEmpty() },
                        fileSize = obj.optLong("fileSize", 0L),
                        repositoryUrl = obj.optString("repositoryUrl").takeIf { it.isNotEmpty() },
                        language = obj.optString("language").takeIf { it.isNotEmpty() },
                        tvTypes = tvTypesList,
                        iconUrl = obj.optString("iconUrl").takeIf { it.isNotEmpty() },
                        apiVersion = obj.optInt("apiVersion", 1)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ExtensionManager", "Error parsing plugins JSON", e)
        }
        return list
    }

    suspend fun installPlugin(
        cs3Url: String,
        metadata: CloudstreamPlugin? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<InstalledPlugin> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f, "ফাইল ডাউনলোড হচ্ছে...")
            val bytes = httpGetBytes(cs3Url)
            if (bytes.isEmpty()) {
                return@withContext Result.failure(Exception("ডাউনলোড ব্যর্থ: কোনো ডেটা পাওয়া যায়নি"))
            }

            onProgress(0.4f, "আর্কাইভ বিশ্লেষণ ও এক্সট্র্যাক্ট করা হচ্ছে...")
            var manifestJson = ""
            var dexBytes: ByteArray? = null

            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name.equals("manifest.json", ignoreCase = true) -> {
                            manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                        }
                        entry.name.equals("classes.dex", ignoreCase = true) -> {
                            dexBytes = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val manifest = parseManifest(manifestJson, cs3Url)
            val pluginName = metadata?.name ?: manifest.name.ifEmpty { "Plugin_${System.currentTimeMillis()}" }

            onProgress(0.7f, "অ্যাপ স্টোরেজে ইনস্টল হচ্ছে...")
            val pluginDir = File(storageDir, sanitizeFilename(pluginName)).apply {
                if (!exists()) mkdirs()
            }

            val manifestFile = File(pluginDir, "manifest.json")
            manifestFile.writeText(manifest.rawJson.ifEmpty { "{}" }, Charsets.UTF_8)

            var dexSizeBytes = 0L
            val dexFile = File(pluginDir, "classes.dex")
            if (dexBytes != null) {
                dexFile.writeBytes(dexBytes!!)
                dexSizeBytes = dexBytes!!.size.toLong()
            }

            // Save raw cs3
            val cs3File = File(pluginDir, "plugin.cs3")
            cs3File.writeBytes(bytes)

            val installed = InstalledPlugin(
                name = pluginName,
                version = metadata?.version ?: manifest.version,
                pluginClassName = manifest.pluginClassName,
                sourceUrl = cs3Url,
                installedAtMillis = System.currentTimeMillis(),
                dexPath = dexFile.absolutePath,
                manifestPath = manifestFile.absolutePath,
                dexSizeBytes = dexSizeBytes,
                archiveSizeBytes = bytes.size.toLong(),
                isEnabled = true,
                iconUrl = metadata?.iconUrl,
                description = metadata?.description ?: manifest.description,
                tvTypes = metadata?.tvTypes ?: if (pluginName.contains("Sport", ignoreCase = true)) listOf("Live", "Sports") else listOf("Movie"),
                author = metadata?.authors?.joinToString(", ") ?: manifest.author
            )

            // Update in list (replace if already existed)
            val updatedList = _installedPlugins.value.filter { it.name != installed.name } + installed
            _installedPlugins.value = updatedList
            saveInstalledPlugins()

            onProgress(1.0f, "ইনস্টলেশন সফল হয়েছে!")
            Result.success(installed)
        } catch (e: Exception) {
            Log.e("ExtensionManager", "Error installing plugin", e)
            Result.failure(e)
        }
    }

    suspend fun togglePluginEnabled(pluginName: String, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val updated = _installedPlugins.value.map {
            if (it.name == pluginName) it.copy(isEnabled = isEnabled) else it
        }
        _installedPlugins.value = updated
        saveInstalledPlugins()
    }

    suspend fun uninstallPlugin(pluginName: String) = withContext(Dispatchers.IO) {
        val pluginDir = File(storageDir, sanitizeFilename(pluginName))
        if (pluginDir.exists()) {
            pluginDir.deleteRecursively()
        }
        _installedPlugins.value = _installedPlugins.value.filter { it.name != pluginName }
        saveInstalledPlugins()
    }

    fun isPluginInstalled(name: String): Boolean {
        return _installedPlugins.value.any { it.name.equals(name, ignoreCase = true) }
    }

    suspend fun fetchPluginStreams(plugin: InstalledPlugin): List<PluginChannelItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<PluginChannelItem>()
        try {
            // For PublicSportsIPTV or sports extensions, fetch live sports streams
            if (plugin.name.contains("Sport", ignoreCase = true) || plugin.name.contains("FanCode", ignoreCase = true)) {
                val fanCodeUrl = "https://raw.githubusercontent.com/Jitendra-unatti/fancode/refs/heads/main/data/fancode.json"
                val (code, jsonStr) = httpGetText(fanCodeUrl)
                if (code == 200 && jsonStr.isNotEmpty()) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val title = obj.optString("match_name", obj.optString("title", "Live Sports"))
                        val streamUrl = obj.optString("stream_url", obj.optString("url", ""))
                        val img = obj.optString("image", obj.optString("src", ""))
                        val category = obj.optString("event_category", "Live Sports")
                        if (streamUrl.isNotEmpty()) {
                            items.add(
                                PluginChannelItem(
                                    title = title,
                                    category = category,
                                    streamUrl = streamUrl,
                                    iconUrl = img.takeIf { it.isNotEmpty() },
                                    description = obj.optString("event_name", "FanCode Live Stream"),
                                    providerName = plugin.name
                                )
                            )
                        }
                    }
                }
            }

            // Fallback sample channels for the provider if none found
            if (items.isEmpty()) {
                items.add(
                    PluginChannelItem(
                        title = "${plugin.name} - লাইভ স্ট্রিম ১",
                        category = "লাইভ টিভি",
                        streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                        iconUrl = plugin.iconUrl,
                        description = "এক্সটেনশন স্ট্রিম ফিড",
                        providerName = plugin.name
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ExtensionManager", "Error fetching plugin streams", e)
        }
        items
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
    }

    private fun httpGetText(urlStr: String): Pair<Int, String> {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MukulPlus/1.0")
            instanceFollowRedirects = true
        }
        val code = conn.responseCode
        val text = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        conn.disconnect()
        return Pair(code, text)
    }

    private fun httpGetBytes(urlStr: String): ByteArray {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20000
            readTimeout = 25000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) MukulPlus/1.0")
            instanceFollowRedirects = true
        }
        val bytes = if (conn.responseCode in 200..299) {
            conn.inputStream.use { it.readBytes() }
        } else {
            byteArrayOf()
        }
        conn.disconnect()
        return bytes
    }

    companion object {
        @Volatile
        private var instance: ExtensionManager? = null

        fun getInstance(context: Context): ExtensionManager {
            return instance ?: synchronized(this) {
                instance ?: ExtensionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
