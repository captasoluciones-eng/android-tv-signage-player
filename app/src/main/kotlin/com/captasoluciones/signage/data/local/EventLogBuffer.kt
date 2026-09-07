package com.captasoluciones.signage.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LogEntry(val timestampMs: Long, val message: String)

/**
 * In-memory ring buffer holding the last [maxSize] status-panel log lines (fetch
 * results, executed commands, item errors, etc). Kept in memory only (not Room-backed)
 * per the spec's "your call, document it" — this is a diagnostics aid for the status
 * screen, not data that needs to survive a process restart, so the extra persistence
 * layer is not worth the complexity.
 */
class EventLogBuffer(private val maxSize: Int = 200) {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val lock = Any()

    fun log(message: String) {
        synchronized(lock) {
            _entries.value = (_entries.value + LogEntry(System.currentTimeMillis(), message)).takeLast(maxSize)
        }
    }
}
