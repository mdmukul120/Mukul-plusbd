package com.example.data.model

data class CloudstreamPlugin(
    val name: String,
    val internalName: String? = null,
    val version: Int = 1,
    val url: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val fileSize: Long = 0L,
    val repositoryUrl: String? = null,
    val language: String? = null,
    val tvTypes: List<String> = emptyList(),
    val iconUrl: String? = null,
    val apiVersion: Int = 1
)

data class Cs3Manifest(
    val pluginClassName: String = "",
    val name: String = "",
    val version: Int = 1,
    val requiresResources: Boolean = false,
    val author: String? = null,
    val description: String? = null,
    val rawJson: String = ""
)

data class InstalledPlugin(
    val name: String,
    val version: Int = 1,
    val pluginClassName: String = "",
    val sourceUrl: String = "",
    val installedAtMillis: Long = System.currentTimeMillis(),
    val dexPath: String = "",
    val manifestPath: String = "",
    val dexSizeBytes: Long = 0L,
    val archiveSizeBytes: Long = 0L,
    val isEnabled: Boolean = true,
    val iconUrl: String? = null,
    val description: String? = null,
    val tvTypes: List<String> = emptyList(),
    val author: String? = null
)

sealed class PluginAnalysisState {
    object Idle : PluginAnalysisState()
    object Loading : PluginAnalysisState()
    data class SingleCs3(
        val url: String,
        val manifest: Cs3Manifest,
        val archiveSizeBytes: Long,
        val dexSizeBytes: Long,
        val hasClassesDex: Boolean,
        val suggestedIconUrl: String? = null,
        val suggestedDescription: String? = null,
        val tvTypes: List<String> = emptyList()
    ) : PluginAnalysisState()
    data class RepoList(
        val repoUrl: String,
        val plugins: List<CloudstreamPlugin>
    ) : PluginAnalysisState()
    data class Error(val message: String) : PluginAnalysisState()
}

data class PluginChannelItem(
    val title: String,
    val category: String,
    val streamUrl: String,
    val iconUrl: String? = null,
    val description: String? = null,
    val providerName: String
)

data class ProviderStreamServer(
    val serverName: String,
    val streamUrl: String,
    val quality: String = "1080p",
    val headers: Map<String, String> = emptyMap()
)

data class ProviderMediaItem(
    val id: String,
    val title: String,
    val url: String = "",
    val posterUrl: String? = null,
    val category: String = "সাধারণ",
    val type: String = "Sports",
    val description: String? = null,
    val status: String? = null,
    val providerName: String = "",
    val streamServers: List<ProviderStreamServer> = emptyList()
)

data class ProviderSection(
    val title: String,
    val subtitle: String? = null,
    val tag: String = "ALL",
    val items: List<ProviderMediaItem> = emptyList()
)

data class DexAnalysisReport(
    val loadedSuccessfully: Boolean,
    val className: String,
    val methodsFound: List<String> = emptyList(),
    val mainUrl: String? = null,
    val supportedTypes: List<String> = emptyList(),
    val rawBytecodeSize: Long = 0L,
    val summary: String = ""
)
