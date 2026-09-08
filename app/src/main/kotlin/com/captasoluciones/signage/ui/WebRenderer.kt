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
                // Some (mostly cheap/older) Android TV boxes have a GPU driver bug where
                // a hardware-accelerated WebView surface renders solid black -- the page
                // loads fine (no error callback fires) but nothing is ever drawn. Software
                // rendering is slower but reliably avoids that class of bug; fine for a
                // signage dashboard, not a big performance concern here.
                setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

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
