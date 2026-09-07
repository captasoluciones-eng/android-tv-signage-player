package com.captasoluciones.signage.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Wraps a single, reused ExoPlayer instance shared across all "video" items in the
 * playlist (never recreated per item, per the spec).
 *
 * For video-to-video transitions, [play] appends the *next* item's MediaItem to the
 * player's timeline as item 2 (when the following playlist entry is also a video),
 * which lets ExoPlayer's internal loader start buffering it ahead of time while the
 * current item is still playing -- a pragmatic form of preloading given a single
 * shared player instance. Because ExoPlayer auto-advances into that second timeline
 * item as soon as the first ends, [Player.STATE_ENDED] never fires at the boundary
 * between them -- callers must treat a [Player.Listener.onMediaItemTransition] with
 * [Player.MEDIA_ITEM_TRANSITION_REASON_AUTO] as the "current item finished" signal
 * instead (see VideoRenderer). STATE_ENDED still fires, and is still the right
 * signal, for the last item of a chain (nothing queued after it to auto-advance to).
 */
@OptIn(UnstableApi::class)
class ExoPlayerHolder(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }

    fun play(url: String, preloadNextVideoUrl: String?, muted: Boolean) {
        player.volume = if (muted) 0f else 1f

        val mediaItems = mutableListOf(MediaItem.fromUri(url))
        if (!preloadNextVideoUrl.isNullOrBlank() && preloadNextVideoUrl != url) {
            mediaItems.add(MediaItem.fromUri(preloadNextVideoUrl))
        }

        player.setMediaItems(mediaItems, 0, 0L)
        player.prepare()
        player.playWhenReady = true
    }

    fun setMuted(muted: Boolean) {
        player.volume = if (muted) 0f else 1f
    }

    fun release() {
        player.release()
    }
}
