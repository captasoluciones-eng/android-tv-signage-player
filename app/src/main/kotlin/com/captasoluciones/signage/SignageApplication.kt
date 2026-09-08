package com.captasoluciones.signage

import android.app.Application
import android.webkit.WebView
import com.captasoluciones.signage.data.AppContainer

class SignageApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // TODO: gate behind a debug flag before the final production release --
        // left unconditional for now while diagnosing the WebView black-screen bug
        // (lets chrome://inspect attach to the in-app WebView).
        WebView.setWebContentsDebuggingEnabled(true)
        container = AppContainer(applicationContext)
    }
}
