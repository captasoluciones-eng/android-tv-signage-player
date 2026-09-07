package com.captasoluciones.signage.player

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.captasoluciones.signage.data.local.DeviceDataStore
import com.captasoluciones.signage.data.local.EventLogBuffer
import com.captasoluciones.signage.data.local.LogEntry
import com.captasoluciones.signage.data.model.ContentTypes
import com.captasoluciones.signage.data.model.HeartbeatPayload
import com.captasoluciones.signage.data.model.PlaylistItem
import com.captasoluciones.signage.data.model.PlaylistResponse
import com.captasoluciones.signage.data.model.RemoteCommands
import com.captasoluciones.signage.data.model.normalizedType
import com.captasoluciones.signage.data.repository.PlaylistFetchResult
import com.captasoluciones.signage.data.repository.PlaylistRepository
import com.captasoluciones.signage.data.repository.RegisterResult
import com.captasoluciones.signage.service.HeartbeatManager
import com.captasoluciones.signage.util.NetworkUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

private const val DEFAULT_IMAGE_SECONDS = 10
private const val DEFAULT_LINK_SECONDS = 20
private const val MIN_POLL_FAILURE_RETRY_MS = 5_000L
private const val HEARTBEAT_INTERVAL_MS = 5 * 60_000L

class PlayerViewModel(
    app: Application,
    private val repository: PlaylistRepository,
    private val dataStore: DeviceDataStore,
    private val eventLog: EventLogBuffer,
    private val heartbeatManager: HeartbeatManager
) : AndroidViewModel(app) {

    private val appContext: Context get() = getApplication<Application>().applicationContext

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

    val logEntries: StateFlow<List<LogEntry>> = eventLog.entries

    private var activePlaylist: PlaylistResponse? = null
    private var pendingPlaylist: PlaylistResponse? = null
    private var pollFailureCount = 0
    @Volatile private var forceImmediatePoll = false
    private var playbackIndex = 0
    private var playbackCycle = 0
    private val startTimeMs = System.currentTimeMillis()

    @Volatile private var currentItemId: String = ""
    @Volatile private var completionSignal: CompletableDeferred<Unit>? = null

    private var settings: DeviceSettingsSnapshot = DeviceSettingsSnapshot()

    init {
        viewModelScope.launch {
            val (id, code) = dataStore.ensureDeviceIdentity()
            _uiState.update { it.copy(deviceId = id, pairingCode = code, appVersion = getAppVersion()) }
        }
        viewModelScope.launch {
            dataStore.settingsFlow.collect { s ->
                settings = DeviceSettingsSnapshot(
                    deviceId = s.deviceId,
                    baseUrl = s.baseUrl,
                    deviceName = s.deviceName,
                    pollMinutes = s.pollMinutes,
                    muteVideo = s.muteVideo,
                    lastCommandId = s.lastCommandId
                )
                _uiState.update {
                    it.copy(
                        deviceId = if (s.deviceId.isNotBlank()) s.deviceId else it.deviceId,
                        pairingCode = if (s.pairingCode.isNotBlank()) s.pairingCode else it.pairingCode,
                        deviceName = s.deviceName,
                        baseUrl = s.baseUrl,
                        baseUrlConfigured = s.baseUrl.isNotBlank(),
                        pollMinutes = s.pollMinutes,
                        muteVideo = s.muteVideo,
                        linked = s.linked,
                        lastSyncTime = s.lastSyncTime,
                        lastError = s.lastError,
                        itemCount = s.itemCount
                    )
                }
            }
        }
        startPollingLoop()
        startPlaybackLoop()
        startHeartbeatLoop()
    }

    // ---------------------------------------------------------------------
    // Setup screen actions
    // ---------------------------------------------------------------------

    fun openSetup() {
        _uiState.update { it.copy(manualSetupOpen = true) }
    }

    fun closeSetup() {
        _uiState.update { it.copy(manualSetupOpen = false) }
    }

    fun saveSetup(baseUrl: String, deviceName: String, pollMinutes: Int, muteVideo: Boolean) {
        viewModelScope.launch {
            dataStore.updateBaseSettings(baseUrl.trim(), deviceName.trim(), pollMinutes, muteVideo)
            eventLog.log("Configuración guardada (baseUrl=${baseUrl.trim()}, pollMinutes=$pollMinutes)")
            forceImmediatePoll = true
            _uiState.update { it.copy(manualSetupOpen = false) }
        }
    }

    // ---------------------------------------------------------------------
    // Renderer callbacks (video ended/error, image error, web error)
    // ---------------------------------------------------------------------

    fun signalItemFinishedNaturally(itemId: String) {
        if (itemId == currentItemId) {
            completionSignal?.let { if (!it.isCompleted) it.complete(Unit) }
        }
    }

    fun signalItemError(itemId: String, message: String) {
        eventLog.log("Item $itemId falló: $message (se omite)")
        if (itemId == currentItemId) {
            completionSignal?.let { if (!it.isCompleted) it.complete(Unit) }
        }
    }

    // ---------------------------------------------------------------------
    // Polling loop
    // ---------------------------------------------------------------------

    private fun startPollingLoop() {
        viewModelScope.launch {
            while (isActive) {
                val baseUrl = settings.baseUrl
                if (baseUrl.isBlank()) {
                    delay(2_000)
                    continue
                }

                if (!NetworkUtils.isOnline(appContext)) {
                    handleFetchResult(PlaylistFetchResult.Failed("Sin conexión de red", repository.getLastGoodPlaylist()))
                } else {
                    val deviceId = settings.deviceId.ifBlank { _uiState.value.deviceId }
                    if (!_uiState.value.linked) {
                        registerWithServer(deviceId)
                    }
                    val result = repository.fetchPlaylist(baseUrl, deviceId)
                    handleFetchResult(result)
                }

                val delayMs = computeNextDelayMs()
                var waited = 0L
                while (waited < delayMs && !forceImmediatePoll && isActive) {
                    delay(1_000)
                    waited += 1_000
                }
                forceImmediatePoll = false
            }
        }
    }

    /**
     * POST {baseUrl}/register so the server actually knows this device exists --
     * required before pairing can ever succeed, since the panel's "Vincular
     * dispositivo" flow looks up a device by pairingCode server-side. Idempotent,
     * so calling it once per poll cycle while unpaired is safe; stops mattering once
     * `linked` is true (the caller only invokes this while unlinked).
     */
    private suspend fun registerWithServer(deviceId: String) {
        val dm = appContext.resources.displayMetrics
        when (val result = repository.registerDevice(settings.baseUrl, deviceId, dm.widthPixels, dm.heightPixels)) {
            is RegisterResult.Success -> {
                val linked = result.response.estado == "activo"
                dataStore.applyServerRegistration(result.response.pairingCode, linked)
            }
            is RegisterResult.Failed -> {
                // Logged inside PlaylistRepository already; nothing else to do here --
                // the next poll cycle will simply retry.
            }
        }
    }

    private fun computeNextDelayMs(): Long {
        val normal = settings.pollMinutes.coerceIn(1, 1440) * 60_000L
        if (pollFailureCount == 0) return normal
        val backoff = MIN_POLL_FAILURE_RETRY_MS * (1L shl min(pollFailureCount, 6))
        return min(backoff, normal.coerceAtLeast(5 * 60_000L))
    }

    private suspend fun handleFetchResult(result: PlaylistFetchResult) {
        when (result) {
            is PlaylistFetchResult.Updated -> {
                pollFailureCount = 0
                if (activePlaylist == null) {
                    activePlaylist = result.playlist
                    recomputeHasContent()
                }
                pendingPlaylist = result.playlist
                applyServerDrivenState(result.playlist)
                // NOTE: "linked" is NOT set here -- an unpaired device also gets a
                // successful (200, items: []) /playlist response per the contract, so
                // that alone can't mean "paired". It's set by registerWithServer()
                // instead, driven by the server's authoritative `estado` field.
                dataStore.setSyncStatus(System.currentTimeMillis(), "", result.playlist.items.size)
                _uiState.update {
                    it.copy(
                        lastError = "",
                        lastSyncTime = System.currentTimeMillis(),
                        itemCount = result.playlist.items.size
                    )
                }
            }
            is PlaylistFetchResult.NotModified -> {
                pollFailureCount = 0
                if (activePlaylist == null && result.playlist != null) {
                    activePlaylist = result.playlist
                    recomputeHasContent()
                }
                result.playlist?.let { applyServerDrivenState(it) }
            }
            is PlaylistFetchResult.Failed -> {
                pollFailureCount++
                dataStore.setSyncStatus(_uiState.value.lastSyncTime, result.message, _uiState.value.itemCount)
                _uiState.update { it.copy(lastError = result.message) }
            }
        }
    }

    private suspend fun applyServerDrivenState(playlist: PlaylistResponse) {
        _uiState.update {
            it.copy(
                overlayEnabled = playlist.overlay.enabled,
                overlayText = playlist.overlay.text,
                transitionMs = playlist.settings.transitionMs.coerceIn(0, 5_000)
            )
        }
        maybeExecuteCommand(playlist.commandId, playlist.command)
    }

    private suspend fun maybeExecuteCommand(commandId: String, command: String) {
        if (commandId.isBlank() || commandId == settings.lastCommandId) return

        eventLog.log("Ejecutando comando remoto '$command' (commandId=$commandId)")
        when (command) {
            RemoteCommands.RELOAD -> {
                forceImmediatePoll = true
            }
            RemoteCommands.RESTART -> {
                _events.tryEmit(PlayerEvent.RestartApp)
            }
            RemoteCommands.CLEAR_WEB_CACHE -> {
                _uiState.update { it.copy(webCacheTick = it.webCacheTick + 1) }
            }
            RemoteCommands.BLACKOUT -> {
                _uiState.update { it.copy(blackout = true) }
            }
            else -> {
                // "none" or an unrecognized value: no dedicated action.
            }
        }
        // Any distinct command other than "blackout" itself clears an active blackout.
        if (command != RemoteCommands.BLACKOUT) {
            _uiState.update { it.copy(blackout = false) }
        }

        dataStore.setLastCommandId(commandId)
        settings = settings.copy(lastCommandId = commandId)
    }

    // ---------------------------------------------------------------------
    // Playback sequencing loop
    // ---------------------------------------------------------------------

    private fun startPlaybackLoop() {
        viewModelScope.launch {
            while (isActive) {
                val playlist = activePlaylist
                if (playlist == null) {
                    delay(1_000)
                    continue
                }

                val items = playlist.items
                    .filter { it.activo && it.normalizedType in ContentTypes.SUPPORTED }
                    .sortedBy { it.orden }

                if (items.isEmpty()) {
                    if (_uiState.value.hasContent) {
                        eventLog.log("La playlist activa no tiene items reproducibles")
                    }
                    _uiState.update { it.copy(hasContent = false, currentItem = null, nextItem = null) }
                    delay(3_000)
                    applyPendingPlaylistIfAny()
                    continue
                }

                _uiState.update { it.copy(hasContent = true) }

                if (playbackIndex >= items.size) playbackIndex = 0
                val item = items[playbackIndex]
                val next = items[(playbackIndex + 1) % items.size]

                currentItemId = item.id
                playbackCycle++
                _uiState.update { it.copy(currentItem = item, nextItem = next, playbackCycle = playbackCycle) }
                eventLog.log("Reproduciendo item ${item.id} (${item.normalizedType})")

                waitForItemCompletion(item)

                playbackIndex++
                applyPendingPlaylistIfAny()
            }
        }
    }

    private fun applyPendingPlaylistIfAny() {
        val pending = pendingPlaylist ?: return
        if (pending !== activePlaylist) {
            activePlaylist = pending
            playbackIndex = 0
            eventLog.log("Playlist actualizada aplicada al finalizar el item en curso")
        }
        pendingPlaylist = null
    }

    private fun recomputeHasContent() {
        val hasContent = activePlaylist
            ?.items
            ?.any { it.activo && it.normalizedType in ContentTypes.SUPPORTED } == true
        _uiState.update { it.copy(hasContent = hasContent) }
    }

    private suspend fun waitForItemCompletion(item: PlaylistItem) {
        val deferred = CompletableDeferred<Unit>()
        completionSignal = deferred

        val timerJob = viewModelScope.launch {
            val seconds = item.durationSec
            if (item.normalizedType == ContentTypes.VIDEO && seconds == null) {
                // Null durationSec on a video means "play to its natural end" -
                // no timer, only the onEnded/onError signal completes it.
                return@launch
            }
            val effectiveSeconds = seconds ?: when (item.normalizedType) {
                ContentTypes.IMAGE -> DEFAULT_IMAGE_SECONDS
                ContentTypes.LINK -> DEFAULT_LINK_SECONDS
                else -> DEFAULT_IMAGE_SECONDS
            }
            delay((effectiveSeconds.toLong() * 1_000L).coerceAtLeast(1_000L))
            if (!deferred.isCompleted) deferred.complete(Unit)
        }

        deferred.await()
        timerJob.cancel()
    }

    // ---------------------------------------------------------------------
    // Heartbeat loop
    // ---------------------------------------------------------------------

    private fun startHeartbeatLoop() {
        viewModelScope.launch {
            delay(5_000)
            while (isActive) {
                val s = settings
                if (s.baseUrl.isNotBlank()) {
                    val dm = appContext.resources.displayMetrics
                    val payload = HeartbeatPayload(
                        deviceId = _uiState.value.deviceId,
                        deviceName = s.deviceName,
                        appVersion = _uiState.value.appVersion,
                        itemActual = currentItemId,
                        uptimeMs = System.currentTimeMillis() - startTimeMs,
                        ultimoError = _uiState.value.lastError,
                        screenWidth = dm.widthPixels,
                        screenHeight = dm.heightPixels,
                        pairingCode = _uiState.value.pairingCode,
                        linked = _uiState.value.linked
                    )
                    // Fire-and-forget: HeartbeatManager already catches its own errors.
                    heartbeatManager.send(s.baseUrl, payload)
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    private fun getAppVersion(): String = try {
        val pm = appContext.packageManager
        pm.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }
}

/** Minimal mutable snapshot of settings needed by the loops above, refreshed from DataStore. */
private data class DeviceSettingsSnapshot(
    val deviceId: String = "",
    val baseUrl: String = "",
    val deviceName: String = "",
    val pollMinutes: Int = 5,
    val muteVideo: Boolean = true,
    val lastCommandId: String = ""
)
