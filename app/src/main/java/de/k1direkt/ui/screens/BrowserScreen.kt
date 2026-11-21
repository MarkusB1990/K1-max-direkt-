package de.k1direkt.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import de.k1direkt.sources.ModelSite
import de.k1direkt.ui.AppViewModel
import de.k1direkt.ui.Navigator

/**
 * Eingebauter Browser für alle Modellseiten. Login-Cookies bleiben erhalten,
 * und jeder Datei-Download wird abgefangen und in der Bibliothek gespeichert (ZIPs werden entpackt).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(vm: AppViewModel, nav: Navigator, startUrl: String) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(startUrl) }
    var progress by remember { mutableFloatStateOf(0f) }
    var canGoBack by remember { mutableStateOf(false) }

    val webView = remember(startUrl) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme ?: return false
                    if (scheme == "http" || scheme == "https") return false
                    // intent://, mailto: usw. an das System weitergeben
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    currentUrl = url
                    canGoBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    currentUrl = url
                    canGoBack = view.canGoBack()
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress / 100f
                }

                override fun onReceivedTitle(view: WebView, t: String?) {
                    title = t.orEmpty()
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                vm.downloadFromBrowser(url, userAgent, contentDisposition, mimeType, this.url)
            }
            loadUrl(startUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler(enabled = canGoBack) {
        webView.goBack()
        canGoBack = webView.canGoBack()
    }

    Column(Modifier.fillMaxSize()) {
        OverlayTopBar(
            title = ModelSite.forUrl(currentUrl)?.displayName ?: Uri.parse(currentUrl).host.orEmpty(),
            subtitle = title.ifBlank { currentUrl },
            onBack = { nav.back() },
            backIcon = Icons.Filled.Close,
        ) {
            IconButton(onClick = { webView.goBack() }, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
            }
            IconButton(onClick = { webView.reload() }) { Icon(Icons.Filled.Refresh, "Neu laden") }
            IconButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
            }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Im externen Browser öffnen") }
        }
        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
}
