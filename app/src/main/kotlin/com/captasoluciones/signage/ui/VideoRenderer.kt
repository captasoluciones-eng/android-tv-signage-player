package com.captasoluciones.signage.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.captasoluciones.signage.data.model.ContentTypes
import com.captasoluciones.signage.data.model.PlaylistItem
import com.captasoluciones.signage.data.model.normalizedType
import com.captasoluciones.signage.player.ExoPlayerHolder

/**
 * Renders a "video" item using the single shared ExoPlayer instance (no controls,
 * no user interaction required). Advances via [onEnded] on natural playback
 * completion, or is cut short by the ViewModel's own duration timer when
 * `durationSec` is set - whichever happens first.
 *
 * Completion detection has two cases because [ExoPlayerHolder.play] may queue the
 * next video right behind this one for preloading:
 *  - No next-video queued (last item of a chain, or next item isn't a video):
 *    [Player.STATE_ENDED] fires normally and is the completion signal.
 *  - A next video IS queued: ExoPlayer auto-advances into it the instant this one
 *    ends, so STATE_ENDED never fires here. [Player.Listener.onMediaItemTransition]
 *    with reason [Player.MEDIA_ITEM_TRANSITION_REASON_AUTO] fires instead, and is
 *    the completion signal for *this* (the outgoing) item.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoRenderer(
    exoPlayerHolder: ExoPlayerHolder,
    item: PlaylistItem,
    nextItem: PlaylistItem?,
    muted: Boolean,
    onEnded: (String) -> Unit,
    onError: (String, String) -> Unit
) {
    DisposableEffect(item.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onEnded(item.id)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) onEnded(item.id)
            }

            override fun onPlayerError(error: PlaybackException) {
                onError(item.id, error.message ?: "error de reproducción de video")
            }
        }
        exoPlayerHolder.player.addListener(listener)

        val preloadUrl = nextItem?.takeIf { it.normalizedType == ContentTypes.VIDEO }?.url
        exoPlayerHolder.play(item.url, preloadUrl, muted)

        onDispose {
            exoPlayerHolder.player.removeListener(listener)
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                player = exoPlayerHolder.player
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(true)
            }
        },
        update = { view -> view.player = exoPlayerHolder.player }
    )
}
