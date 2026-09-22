package com.captasoluciones.signage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.compose.AsyncImage
import com.captasoluciones.signage.data.model.PlaylistItem

/**
 * Renders an "imagen" item for `durationSec` seconds (the actual timing is driven by
 * PlayerViewModel's completion timer; this composable is purely visual). `scale`
 * maps "fill"/"cover" -> Crop and anything else (default "fit") -> Fit.
 */
@Composable
fun ImageRenderer(
    item: PlaylistItem,
    imageLoader: ImageLoader,
    onError: (String, String) -> Unit
) {
    val scale = item.scale.trim().lowercase()
    val contentScale = if (scale == "fill" || scale == "cover") ContentScale.Crop else ContentScale.Fit

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = item.url,
            contentDescription = null,
            imageLoader = imageLoader,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onError = { state ->
                onError(item.id, state.result.throwable.message ?: "no se pudo cargar la imagen")
            }
        )
    }
}
