package com.captasoluciones.signage.util

import android.app.Activity
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * True fullscreen kiosk mode: hides system bars (immersive sticky), keeps the screen
 * always on, and lets content draw edge-to-edge. Meant to be re-applied on every
 * onResume / onWindowFocusChanged since the system can bring the bars back after
 * certain transient events.
 */
object KioskModeHelper {

    fun enableImmersiveMode(activity: Activity) {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
