package com.captasoluciones.signage.player

import com.captasoluciones.signage.data.model.PlaylistItem

enum class Screen { PLAYER, WAITING, SETUP }

/**
 * Single source of truth for the UI. [screen] is a *computed* property (not a stored
 * field written from multiple places) so there is exactly one, race-free rule for
 * which of the three screens (setup / waiting / player) is shown:
 *
 *  1. SETUP   - base URL not configured yet, or the user explicitly opened it.
 *  2. PLAYER  - there is at least one valid, active item to show.
 *  3. WAITING - base URL is configured but no content has ever loaded successfully
 *               (or the currently held playlist has no playable items).
 *
 * This guarantees "never a permanent black screen" - it is always exactly one of
 * these three.
 */
data class PlayerUiState(
    val baseUrlConfigured: Boolean = false,
    val manualSetupOpen: Boolean = false,
    val hasContent: Boolean = false,
    val currentItem: PlaylistItem? = null,
    val nextItem: PlaylistItem? = null,
    // Monotonically increasing per playback step. A single-item playlist repeats the
    // *same* PlaylistItem (same id) every loop; without this, `currentItem` would be
    // `equals()` to its previous value, MutableStateFlow would dedupe the update, and
    // the renderer would never be told to restart -- playback would stop after one
    // play-through. Renderers key off (currentItem.id, playbackCycle), not id alone.
    val playbackCycle: Int = 0,
    val muteVideo: Boolean = true,
    val transitionMs: Int = 500,
    val overlayEnabled: Boolean = false,
    val overlayText: String = "",
    val blackout: Boolean = false,
    val webCacheTick: Int = 0,
    val deviceId: String = "",
    val pairingCode: String = "",
    val linked: Boolean = false,
    val deviceName: String = "",
    val baseUrl: String = "",
    val pollMinutes: Int = 5,
    val lastSyncTime: Long = 0L,
    val lastError: String = "",
    val itemCount: Int = 0,
    val appVersion: String = ""
) {
    val screen: Screen
        get() = when {
            manualSetupOpen || !baseUrlConfigured -> Screen.SETUP
            hasContent -> Screen.PLAYER
            else -> Screen.WAITING
        }
}

sealed class PlayerEvent {
    data object RestartApp : PlayerEvent()
}
