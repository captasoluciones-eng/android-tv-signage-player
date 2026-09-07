package com.captasoluciones.signage.data.repository

import android.net.Uri
import com.captasoluciones.signage.data.local.EventLogBuffer
import com.captasoluciones.signage.data.model.PlaylistResponse
import com.captasoluciones.signage.data.remote.PlaylistApi
import kotlinx.serialization.json.Json

sealed class PlaylistFetchResult {
    data class Updated(val playlist: PlaylistResponse) : PlaylistFetchResult()
    data class NotModified(val playlist: PlaylistResponse?) : PlaylistFetchResult()
    data class Failed(val message: String, val lastKnown: PlaylistResponse?) : PlaylistFetchResult()
}

/**
 * Fetches the playlist for a device and keeps the last successfully-parsed response
 * in memory so playback can keep looping it even while the network is down (see
 * README "Fault tolerance" section for the exact behavior this backs).
 */
class PlaylistRepository(
    private val api: PlaylistApi,
    private val json: Json,
    private val eventLog: EventLogBuffer
) {

    @Volatile
    private var lastGoodPlaylist: PlaylistResponse? = null

    fun getLastGoodPlaylist(): PlaylistResponse? = lastGoodPlaylist

    suspend fun fetchPlaylist(baseUrl: String, deviceId: String): PlaylistFetchResult {
        val url = buildPlaylistUrl(baseUrl, deviceId)
        return try {
            val response = api.getPlaylist(url)
            when {
                response.code() == 304 -> {
                    eventLog.log("Playlist sin cambios (304 Not Modified)")
                    PlaylistFetchResult.NotModified(lastGoodPlaylist)
                }
                response.isSuccessful -> {
                    val bodyStr = response.body()?.use { it.string() }.orEmpty()
                    if (bodyStr.isBlank()) {
                        eventLog.log("Respuesta vacía; se mantiene la última playlist válida")
                        PlaylistFetchResult.NotModified(lastGoodPlaylist)
                    } else {
                        val parsed = json.decodeFromString(PlaylistResponse.serializer(), bodyStr)
                        lastGoodPlaylist = parsed
                        eventLog.log("Playlist actualizada: ${parsed.items.size} items (commandId=${parsed.commandId})")
                        PlaylistFetchResult.Updated(parsed)
                    }
                }
                else -> {
                    val msg = "HTTP ${response.code()}"
                    eventLog.log("Error al obtener playlist: $msg")
                    PlaylistFetchResult.Failed(msg, lastGoodPlaylist)
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName ?: "error de red"
            eventLog.log("Fallo de red obteniendo playlist: $msg")
            PlaylistFetchResult.Failed(msg, lastGoodPlaylist)
        }
    }

    private fun buildPlaylistUrl(baseUrl: String, deviceId: String): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        return "$baseUrl${separator}deviceId=${Uri.encode(deviceId)}"
    }
}
