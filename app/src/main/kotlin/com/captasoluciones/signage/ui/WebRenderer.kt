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
 * Known limitation (not fixable from this app): a page whose own JS periodically
 * swaps an `<img src=...>` to rotate content (rather than doing a full navigation)
 * can render solid black in Android's WebView/Chromium even though the DOM reports
 * a fully successful load (`img.complete`, correct natural size). Confirmed via
 * Chrome DevTools Protocol against the live WebView: the compositor's own screenshot
 * stays black regardless of `WebView.reload()` or `WebView.invalidate()` calls from
 * here, so the bug is upstream in the renderer, not in anything this app controls.
 * A statically-declared `<img src="...">` (no JS swap) and a full page reload both
 * render correctly, so the fix belongs on the content side: e.g. rotate via
 * `<meta http-equiv="refresh" content="20">` instead of a JS `img.src` swap.
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
                // Some sites (dashboards, embedded analytics tools) special-case or
                // outright refuse to render for the standard WebView user agent
                // (identifiable via its "; wv)" / "Version/4.0" markers), even though
                // JS/CSS support is otherwise equivalent to Chrome. Presenting as a
                // normal desktop Chrome UA avoids that class of failure.
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

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
