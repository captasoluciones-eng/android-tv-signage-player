package com.captasoluciones.signage.ui

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.captasoluciones.signage.data.model.PlaylistItem

/**
 * Renders a "link" item as a fullscreen WebView. A fresh WebView is created for every
 * link item and explicitly destroyed via AndroidView's `onRelease` when leaving it, to
 * avoid leaking WebView instances (per the spec: "created and destroyed per link item").
 *
 * `scale` is interpreted as an integer text-zoom percentage (WebSettings.textZoom),
 * e.g. "150" = 150%. Any non-numeric value is ignored and the platform default is used.
 *
 * Content-side gotcha (not a bug in this app, but worth knowing when debugging a
 * black "link" item): this WebView/Chromium build fails to ever paint an `<img>`
 * whose width/height is set via a relative unit (`%`, `vw`, `vh`) -- the page goes
 * solid black even though the DOM reports a fully successful load (`img.complete`,
 * correct natural size). A/B'd via Chrome DevTools Protocol against the live
 * WebView: the exact same image, same site, renders correctly the moment its
 * `<img>` is sized with fixed pixel dimensions (e.g. computed once from
 * `window.innerWidth`/`innerHeight`) instead of `%`/`vw`/`vh`; JavaScript itself
 * (including a `setInterval` swapping `img.src` to rotate images) was never the
 * problem. Fixed on the `kiosko.bts-captasoluciones.site` content by switching
 * its CSS to pixel-based sizing.
 */
@Composable
fun WebRenderer(
    item: PlaylistItem,
    cacheTick: Int,
    onError: (String, String) -> Unit
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(cacheTick) {
        if (cacheTick > 0) {
            webViewRef.value?.let {
                it.clearCache(true)
                it.clearHistory()
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                setBackgroundColor(android.graphics.Color.BLACK)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                // Do NOT spoof the user agent (a prior version claimed to be a
                // recent desktop Chrome). Confirmed on real hardware running
                // Android 8.1: some servers (Google Apps Script's own web app
                // runtime among them) pick which JS to serve based on the UA,
                // and telling them "modern desktop Chrome" while the actual
                // engine is years older gets back JS the real engine can't run
                // -- content silently never loads (stuck on its own loading
                // state, or blank). Confirmed the other way too: simulating an
                // old-Android UA in a modern desktop Chrome reproduces the same
                // stuck-loading symptom. Leaving the WebView's own accurate UA
                // in place lets each server serve JS the real engine can run.

                item.scale.trim().toIntOrNull()?.let { zoom ->
                    settings.textZoom = zoom.coerceIn(10, 500)
                }

                webViewClient = object : WebViewClient() {
                    @Deprecated("Deprecated in Java")
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        onError(item.id, description ?: "error web ($errorCode)")
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request == null || request.isForMainFrame) {
                            onError(item.id, error?.description?.toString() ?: "error web")
                        }
                    }
                }

                loadUrl(item.url)
                webViewRef.value = this
            }
        },
        onRelease = { webView ->
            webViewRef.value = null
            webView.stopLoading()
            webView.destroy()
        }
    )
}
