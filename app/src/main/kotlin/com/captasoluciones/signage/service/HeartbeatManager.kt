package com.captasoluciones.signage.service

import com.captasoluciones.signage.data.local.EventLogBuffer
import com.captasoluciones.signage.data.model.HeartbeatPayload
import com.captasoluciones.signage.data.remote.PlaylistApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Sends a fire-and-forget heartbeat POST every 5 minutes. The heartbeat endpoint is
 * derived from the single configured baseUrl as `{baseUrl}/heartbeat` (documented in
 * README.md) since only one base URL is configured on-device.
 *
 * A heartbeat failure is swallowed here and only logged — it must never interrupt
 * playback.
 */
class HeartbeatManager(
    private val api: PlaylistApi,
    private val json: Json,
    private val eventLog: EventLogBuffer
) {

    suspend fun send(baseUrl: String, payload: HeartbeatPayload) {
        try {
            val url = buildHeartbeatUrl(baseUrl)
            val bodyJson = json.encodeToString(HeartbeatPayload.serializer(), payload)
            val body = bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            val response = api.postHeartbeat(url, body)
            if (!response.isSuccessful) {
                eventLog.log("Heartbeat rechazado por el servidor: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            eventLog.log("Heartbeat falló (se ignora, no afecta la reproducción): ${e.message}")
        }
    }

    private fun buildHeartbeatUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return "$trimmed/heartbeat"
    }
}
