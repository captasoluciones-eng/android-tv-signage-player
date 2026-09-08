package com.captasoluciones.signage.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "signage_settings")

/** Immutable snapshot of everything persisted about this device / its configuration. */
data class DeviceSettings(
    val deviceId: String = "",
    val pairingCode: String = "",
    val baseUrl: String = "",
    val deviceKey: String = "",
    val deviceName: String = "",
    val pollMinutes: Int = 5,
    val muteVideo: Boolean = true,
    val lastCommandId: String = "",
    val linked: Boolean = false,
    val lastSyncTime: Long = 0L,
    val lastError: String = "",
    val itemCount: Int = 0
)

/**
 * Thin wrapper around Jetpack Preferences DataStore holding all on-device configuration
 * and status fields described in the spec (deviceId, pairingCode, baseUrl, deviceName,
 * pollMinutes, muteVideo, last executed commandId, linked flag, last sync status).
 */
class DeviceDataStore(private val context: Context) {

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val PAIRING_CODE = stringPreferencesKey("pairing_code")
        val BASE_URL = stringPreferencesKey("base_url")
        val DEVICE_KEY = stringPreferencesKey("device_key")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val POLL_MINUTES = intPreferencesKey("poll_minutes")
        val MUTE_VIDEO = booleanPreferencesKey("mute_video")
        val LAST_COMMAND_ID = stringPreferencesKey("last_command_id")
        val LINKED = booleanPreferencesKey("linked")
        val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        val LAST_ERROR = stringPreferencesKey("last_error")
        val ITEM_COUNT = intPreferencesKey("item_count")
    }

    val settingsFlow: Flow<DeviceSettings> = context.dataStore.data.map { prefs ->
        DeviceSettings(
            deviceId = prefs[Keys.DEVICE_ID] ?: "",
            pairingCode = prefs[Keys.PAIRING_CODE] ?: "",
            baseUrl = prefs[Keys.BASE_URL] ?: "",
            deviceKey = prefs[Keys.DEVICE_KEY] ?: "",
            deviceName = prefs[Keys.DEVICE_NAME] ?: "",
            pollMinutes = prefs[Keys.POLL_MINUTES] ?: 5,
            muteVideo = prefs[Keys.MUTE_VIDEO] ?: true,
            lastCommandId = prefs[Keys.LAST_COMMAND_ID] ?: "",
            linked = prefs[Keys.LINKED] ?: false,
            lastSyncTime = prefs[Keys.LAST_SYNC_TIME] ?: 0L,
            lastError = prefs[Keys.LAST_ERROR] ?: "",
            itemCount = prefs[Keys.ITEM_COUNT] ?: 0
        )
    }

    /** Generates and persists a UUID deviceId + a random 6-digit pairing code on first launch. */
    suspend fun ensureDeviceIdentity(): Pair<String, String> {
        val prefs = context.dataStore.data.first()
        val existingId = prefs[Keys.DEVICE_ID]
        val existingCode = prefs[Keys.PAIRING_CODE]

        if (!existingId.isNullOrBlank() && !existingCode.isNullOrBlank()) {
            return existingId to existingCode
        }

        val id = existingId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val code = existingCode?.takeIf { it.isNotBlank() } ?: (100000..999999).random().toString()

        context.dataStore.edit {
            it[Keys.DEVICE_ID] = id
            it[Keys.PAIRING_CODE] = code
        }
        return id to code
    }

    suspend fun updateBaseSettings(
        baseUrl: String,
        deviceKey: String,
        deviceName: String,
        pollMinutes: Int,
        muteVideo: Boolean
    ) {
        context.dataStore.edit {
            it[Keys.BASE_URL] = baseUrl
            it[Keys.DEVICE_KEY] = deviceKey
            it[Keys.DEVICE_NAME] = deviceName
            it[Keys.POLL_MINUTES] = pollMinutes.coerceIn(1, 1440)
            it[Keys.MUTE_VIDEO] = muteVideo
        }
    }

    suspend fun setLastCommandId(commandId: String) {
        context.dataStore.edit { it[Keys.LAST_COMMAND_ID] = commandId }
    }

    suspend fun setLinked(linked: Boolean) {
        context.dataStore.edit { it[Keys.LINKED] = linked }
    }

    /** Applies the server's authoritative registration state (see PlaylistRepository.
     * registerDevice). `serverPairingCode` overwrites the locally-generated placeholder
     * once the server has actually seen this device; `linked` reflects estado == "activo". */
    suspend fun applyServerRegistration(serverPairingCode: String?, linked: Boolean) {
        context.dataStore.edit {
            if (!serverPairingCode.isNullOrBlank()) {
                it[Keys.PAIRING_CODE] = serverPairingCode
            }
            it[Keys.LINKED] = linked
        }
    }

    suspend fun setSyncStatus(lastSyncTime: Long, lastError: String, itemCount: Int) {
        context.dataStore.edit {
            it[Keys.LAST_SYNC_TIME] = lastSyncTime
            it[Keys.LAST_ERROR] = lastError
            it[Keys.ITEM_COUNT] = itemCount
        }
    }
}
