package com.captasoluciones.signage.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.request.ImageRequest
import com.captasoluciones.signage.data.model.ContentTypes
import com.captasoluciones.signage.data.model.normalizedType
import com.captasoluciones.signage.player.ExoPlayerHolder
import com.captasoluciones.signage.player.PlayerUiState
import com.captasoluciones.signage.player.Screen

/**
 * Host composable for the actual signage playback: swaps the renderer (video / image /
 * web) based on the current item's type, applies the fade transition between items,
 * and layers the waiting screen / emergency overlay / blackout on top as needed.
 *
 * This is only shown for [Screen.PLAYER] and [Screen.WAITING] - [Screen.SETUP] is
 * handled by a sibling composable in MainActivity.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    exoPlayerHolder: ExoPlayerHolder,
    imageLoader: ImageLoader,
    onVideoEnded: (String) -> Unit,
    onItemError: (String, String) -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (state.screen) {
            Screen.WAITING -> WaitingScreen(state = state)

            Screen.SETUP -> {
                // Not rendered here; MainActivity shows SetupScreen instead.
            }

            Screen.PLAYER -> {
                val item = state.currentItem
                if (item != null) {
                    // Preload the next image while the current item plays. Video preload
                    // is handled inside ExoPlayerHolder/VideoRenderer; link items are not
                    // worth preloading (WebView load time is short relative to durationSec).
                    LaunchedEffect(item.id, state.nextItem?.id) {
                        val next = state.nextItem
                        if (next != null && next.normalizedType == ContentTypes.IMAGE) {
                            imageLoader.enqueue(
                                ImageRequest.Builder(context).data(next.url).build()
                            )
                        }
                    }

                    // Keyed on (playbackCycle, item) rather than item alone: a single-item
                    // playlist repeats the same PlaylistItem every loop, and AnimatedContent
                    // (like any state keyed by `==`) would otherwise treat that repeat as "no
                    // change" and never restart playback. See PlayerUiState.playbackCycle.
                    AnimatedContent(
                        targetState = state.playbackCycle to item,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(state.transitionMs)) togetherWith
                                fadeOut(animationSpec = tween(state.transitionMs)))
                        },
                        label = "signage-item-transition"
                    ) { (cycle, animatedItem) ->
                        key(cycle, animatedItem.id) {
                            when (animatedItem.normalizedType) {
                                ContentTypes.VIDEO -> VideoRenderer(
                                    exoPlayerHolder = exoPlayerHolder,
                                    item = animatedItem,
                                    nextItem = state.nextItem,
                                    muted = state.muteVideo,
                                    onEnded = onVideoEnded,
                                    onError = onItemError
                                )
                                ContentTypes.IMAGE -> ImageRenderer(
                                    item = animatedItem,
                                    imageLoader = imageLoader,
                                    onError = onItemError
                                )
                                ContentTypes.LINK -> WebRenderer(
                                    item = animatedItem,
                                    cacheTick = state.webCacheTick,
                                    onError = onItemError
                                )
                                else -> {
                                    // Unsupported type: already filtered out upstream, but
                                    // guarded here too so nothing ever crashes.
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.overlayEnabled && state.overlayText.isNotBlank()) {
            OverlayComposable(text = state.overlayText)
        }

        if (state.blackout) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
    }
}
