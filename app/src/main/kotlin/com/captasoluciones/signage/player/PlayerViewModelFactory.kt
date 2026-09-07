package com.captasoluciones.signage.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.captasoluciones.signage.SignageApplication

class PlayerViewModelFactory(private val app: SignageApplication) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val container = app.container
        return PlayerViewModel(
            app,
            container.playlistRepository,
            container.deviceDataStore,
            container.eventLog,
            container.heartbeatManager
        ) as T
    }
}
