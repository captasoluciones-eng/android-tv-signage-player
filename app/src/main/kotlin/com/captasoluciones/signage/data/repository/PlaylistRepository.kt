package com.captasoluciones.signage.data.repository

import android.net.Uri
import com.captasoluciones.signage.data.local.EventLogBuffer
import com.captasoluciones.signage.data.model.PlaylistResponse
import com.captasoluciones.signage.data.model.RegisterRequest
import com.captasoluciones.signage.data.model.RegisterResponse
import com.captasoluciones.signage.data.remote.PlaylistApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

sealed class PlaylistFetchResult {
    data class Updated(val playlist: PlaylistResponse) : PlaylistFetchResult()
    data class NotModified(val playlist: PlaylistResponse?) : PlaylistFetchResult()
    data class Failed(val message: String, val lastKnown: PlaylistResponse?) : PlaylistFetchResult()
}

sealed class RegisterResult {
    data class Success(val response: RegisterResponse) : RegisterResult()
    data class Failed(val message: String) : RegisterResult()
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

    suspend fun fetchPlaylist(baseUrl: String, deviceId: String, deviceKey: String?): PlaylistFetchResult {
        val url = buildPlaylistUrl(baseUrl, deviceId)
        return try {
            val response = api.getPlaylist(url, deviceKey)
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
        val origin = baseUrl.trimEnd('/')
        return "$origin/playlist?deviceId=${Uri.encode(deviceId)}"
    }

    /**
     * POST {baseUrl}/register -- idempotent; safe to call repeatedly (e.g. once per
     * poll cycle while unpaired). The server is authoritative for `pairingCode` and
     * `estado`; callers should persist those into DeviceDataStore rather than trusting
     * the locally-generated placeholder pairing code once this succeeds even once.
     */
    suspend fun registerDevice(baseUrl: String, deviceId: String, screenWidth: Int?, screenHeight: Int?): RegisterResult {
        val url = "${baseUrl.trimEnd('/')}/register"
        return try {
            val payload = RegisterRequest(deviceId, screenWidth, screenHeight)
            val bodyJson = json.encodeToString(RegisterRequest.serializer(), payload)
            val body = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val response = api.postJson(url, body)
            if (response.isSuccessful) {
                val bodyStr = response.body()?.use { it.string() }.orEmpty()
                val parsed = json.decodeFromString(RegisterResponse.serializer(), bodyStr)
                eventLog.log("Registro OK: estado=${parsed.estado}, pairingCode=${parsed.pairingCode}")
                RegisterResult.Success(parsed)
            } else {
                val msg = "HTTP ${response.code()}"
                eventLog.log("Registro falló: $msg")
                RegisterResult.Failed(msg)
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName ?: "error de red"
            eventLog.log("Registro falló (red): $msg")
            RegisterResult.Failed(msg)
        }
    }
}
