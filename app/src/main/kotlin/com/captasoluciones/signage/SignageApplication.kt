package com.captasoluciones.signage

import android.app.Application
import com.captasoluciones.signage.data.AppContainer

class SignageApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}
