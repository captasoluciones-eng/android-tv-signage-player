package com.captasoluciones.signage.data.model

import kotlinx.serialization.Serializable

/**
 * Body sent to POST {baseUrl}/heartbeat every 5 minutes. See HeartbeatManager for the
 * URL derivation rule and README.md for the documented contract.
 */
@Serializable
data class HeartbeatPayload(
    val deviceId: String,
    val deviceName: String,
    val appVersion: String,
    val itemActual: String,
    val uptimeMs: Long,
    val ultimoError: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val pairingCode: String,
    val linked: Boolean
)
