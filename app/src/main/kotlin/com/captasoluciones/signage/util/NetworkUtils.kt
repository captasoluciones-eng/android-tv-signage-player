package com.captasoluciones.signage.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkUtils {

    /** Best-effort connectivity check used to short-circuit a poll attempt quickly
     *  (avoiding a full OkHttp connect-timeout wait) when there is clearly no network. */
    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            // If we can't determine connectivity, don't block polling on it.
            true
        }
    }
}
