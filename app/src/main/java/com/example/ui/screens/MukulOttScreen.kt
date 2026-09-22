package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.util.DownloadUtils
import com.example.ui.theme.AuthBrandPrimary
import com.example.ui.theme.BrandRed
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.TextPrimary

private const val MUKUL_OTT_URL = "https://mukul-ott.ai.studio/"

/**
 * JavaScript interface that bridges download clicks inside the WebView
 * directly to Android Chrome downloader.
 */
class ChromeDownloadBridge(private val onDownloadRequested: (String) -> Unit) {
    @JavascriptInterface
    fun downloadInChrome(url: String?) {
        if (!url.isNullOrBlank()) {
            onDownloadRequested(url)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MukulOttScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var currentWebUrl by remember { mutableStateOf(MUKUL_OTT_URL) }

    // System / Hardware back button navigates inside the WebView history
    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBackground)
    ) {
        // Embedded Fullscreen In-App Browser for Mukul OTT
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = true
                    overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    isNestedScrollingEnabled = true

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        allowFileAccess = true
                        allowContentAccess = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = settings.userAgentString + " MukulPlusApp/1.0"
                    }

                    // JavaScript Bridge to catch all client-side download clicks
                    addJavascriptInterface(
                        ChromeDownloadBridge { targetUrl ->
                            post {
                                DownloadUtils.openDownloadInChrome(ctx, targetUrl)
                            }
                        },
                        "AndroidDownloader"
                    )

                    // Native WebView Download Listener -> Open in Chrome
                    setDownloadListener { url, _, _, _, _ ->
                        DownloadUtils.openDownloadInChrome(ctx, url)
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            currentWebUrl = url ?: MUKUL_OTT_URL
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            currentWebUrl = url ?: MUKUL_OTT_URL
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true

                            // Inject DOM click interceptor for download links & buttons
                            val jsInjector = """
                                (function() {
                                    if (window.__mukulDownloadInterceptorAttached) return;
                                    window.__mukulDownloadInterceptorAttached = true;
                                    document.addEventListener('click', function(e) {
                                        var target = e.target.closest('a, button, [role="button"]');
                                        if (!target) return;
                                        var href = target.getAttribute('href') || target.getAttribute('data-href') || target.getAttribute('data-url') || '';
                                        var text = (target.innerText || target.textContent || '').toLowerCase();
                                        var isDl = target.hasAttribute('download') ||
                                                   text.indexOf('download') !== -1 ||
                                                   text.indexOf('ডাউনলোড') !== -1 ||
                                                   href.indexOf('download') !== -1 ||
                                                   href.indexOf('dl=1') !== -1 ||
                                                   href.indexOf('mediafire.com') !== -1 ||
                                                   href.indexOf('drive.google.com') !== -1 ||
                                                   href.indexOf('pixeldrain.com') !== -1 ||
                                                   href.indexOf('mega.nz') !== -1 ||
                                                   href.indexOf('gofile.io') !== -1 ||
                                                   href.match(/\.(mp4|mkv|zip|apk|rar|7z|tar|gz|iso)(\?|$)/i);
                                        if (isDl && href && href.indexOf('javascript:') !== 0) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            if (window.AndroidDownloader && window.AndroidDownloader.downloadInChrome) {
                                                window.AndroidDownloader.downloadInChrome(href);
                                            } else {
                                                window.location.href = href;
                                            }
                                        }
                                    }, true);
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(jsInjector, null)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUrl = request?.url?.toString() ?: return false

                            // If it's a download link, route straight to Chrome browser
                            if (DownloadUtils.isDownloadUrl(targetUrl)) {
                                DownloadUtils.openDownloadInChrome(ctx, targetUrl)
                                return true
                            }

                            // Handle mailto/tel/intent schemes gracefully
                            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                    ctx.startActivity(intent)
                                } catch (_: Exception) {}
                                return true
                            }

                            return false // Browse in-app
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                            if (newProgress == 100) {
                                isLoading = false
                            }
                        }
                    }

                    loadUrl(MUKUL_OTT_URL)
                    webViewInstance = this
                }
            },
            update = { view ->
                webViewInstance = view
            }
        )

        // Slim top progress bar when loading
        if (isLoading && progress < 100) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = AuthBrandPrimary,
                trackColor = Color.Transparent
            )
        }

        // Floating In-Page Navigation & Chrome Launcher Controls
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = CinemaSurface.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canGoBack) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (canGoForward) {
                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Forward",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val activeUrl = webViewInstance?.url ?: currentWebUrl
                        DownloadUtils.openDownloadInChrome(context, activeUrl)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Open in Chrome",
                        tint = BrandRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
