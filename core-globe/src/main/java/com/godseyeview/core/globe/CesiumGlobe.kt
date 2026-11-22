package com.godseyeview.core.globe

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

/** The Cesium globe. This composable renders ONLY the globe (plan Q5);
 *  every panel, card and control around it is native Compose. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CesiumGlobe(
    controller: GlobeController,
    initialView: CameraViewState,
    initialBasemap: Basemap = Basemap.ESRI,
    initialQualityId: String = "balanced",
    initialIonToken: String? = null,
    initialGoogleKey: String? = null,
    onCameraChanged: (CameraViewState) -> Unit,
    onBasemapChanged: (Basemap) -> Unit,
    onGlobeError: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDiagnostics: (String) -> Unit = {},
    onConsoleMessage: (String) -> Unit = {},
    onEntityPicked: (layerId: String, entityId: String?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val assetLoader = remember {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    val bridge = remember {
        object {
            @JavascriptInterface
            fun onGlobeReady() {
                controller.onPageReady(initialView, initialBasemap, initialQualityId, initialIonToken, initialGoogleKey)
            }

            @JavascriptInterface
            fun onCameraChanged(json: String) {
                CameraViewState.fromJson(json)?.let(onCameraChanged)
            }

            @JavascriptInterface
            fun onBasemapChanged(id: String) {
                onBasemapChanged(Basemap.fromId(id))
            }

            @JavascriptInterface
            fun onError(message: String) {
                onGlobeError(message)
            }

            @JavascriptInterface
            fun onDiagnostics(json: String) {
                onDiagnostics(json)
            }

            @JavascriptInterface
            fun onEntityPicked(layerId: String, entityId: String) {
                onEntityPicked(layerId, entityId.ifEmpty { null })
            }
        }
    }
    // Stage 5 (issue #55): if the WebView renderer process dies, the view
    // can never draw again - bump the generation to swap in a fresh one.
    var webViewGeneration by remember { mutableStateOf(0) }
    val webView = remember(webViewGeneration) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? =
                    TerrainTileProxy.fetch(request.url) ?: assetLoader.shouldInterceptRequest(request.url)

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: android.webkit.RenderProcessGoneDetail,
                ): Boolean {
                    // The renderer died (OOM, crash). Detach the dead view
                    // so controller calls queue up, report the error, and
                    // recreate the WebView instead of a permanent blank.
                    controller.detach(view)
                    onGlobeError("WebView renderer process ended; reloading the globe")
                    webViewGeneration++
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    onConsoleMessage(
                        "${consoleMessage.messageLevel()}: ${consoleMessage.message()}",
                    )
                    return true
                }
            }
            addJavascriptInterface(bridge, "AndroidBridge")
            controller.attach(this)
            loadUrl("https://appassets.androidplatform.net/assets/globe/index.html")
        }
    }
    DisposableEffect(webView) {
        onDispose {
            controller.detach(webView)
            webView.removeJavascriptInterface("AndroidBridge")
            webView.destroy()
        }
    }
    key(webViewGeneration) {
        AndroidView(factory = { webView }, modifier = modifier)
    }
}
