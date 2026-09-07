package com.captasoluciones.signage.data.model

import kotlinx.serialization.Serializable

/**
 * Root response returned by GET {baseUrl}?deviceId={id}.
 *
 * Every field has a safe default so a partial / malformed payload from the central
 * panel never crashes deserialization (combined with `ignoreUnknownKeys = true` and
 * `coerceInputValues = true` on the [kotlinx.serialization.json.Json] instance used
 * to parse it, see AppContainer).
 */
@Serializable
data class PlaylistResponse(
    val version: Int = 1,
    val updatedAt: String = "",
    val deviceName: String = "",
    val groupId: String = "",
    val commandId: String = "",
    val command: String = "none",
    val overlay: OverlayConfig = OverlayConfig(),
    val settings: PlaylistSettings = PlaylistSettings(),
    val items: List<PlaylistItem> = emptyList()
)

@Serializable
data class OverlayConfig(
    val text: String = "",
    val enabled: Boolean = false
)

@Serializable
data class PlaylistSettings(
    val pollMinutes: Int = 5,
    val muteVideo: Boolean = true,
    val transitionMs: Int = 500
)

@Serializable
data class PlaylistItem(
    val id: String = "",
    val type: String = "",
    val url: String = "",
    val durationSec: Int? = null,
    val scale: String = "fit",
    val orden: Int = 0,
    val activo: Boolean = true
)

/** Content types this player understands. Anything else is silently skipped. */
object ContentTypes {
    const val VIDEO = "video"
    const val IMAGE = "imagen"
    const val LINK = "link"

    val SUPPORTED = setOf(VIDEO, IMAGE, LINK)
}

/** Normalized (trimmed + lower-cased) type, tolerant of case/whitespace variance from the API. */
val PlaylistItem.normalizedType: String
    get() = type.trim().lowercase()

/** Body for POST {baseUrl}/register. */
@Serializable
data class RegisterRequest(
    val deviceId: String,
    val screenWidth: Int? = null,
    val screenHeight: Int? = null
)

/** Response from POST {baseUrl}/register -- the server is the source of truth for
 * `pairingCode` and `estado`; the locally-generated pairing code (DeviceDataStore)
 * is only a placeholder shown before the first successful registration. */
@Serializable
data class RegisterResponse(
    val deviceId: String = "",
    val estado: String = "pendiente",
    val pairingCode: String? = null,
    val created: Boolean = false
)

/** Remote command values, paired with commandId so each command runs exactly once. */
object RemoteCommands {
    const val RELOAD = "reload"
    const val RESTART = "restart"
    const val CLEAR_WEB_CACHE = "clearWebCache"
    const val BLACKOUT = "blackout"
    const val NONE = "none"
}
