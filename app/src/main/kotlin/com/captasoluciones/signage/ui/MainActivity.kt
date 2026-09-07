package com.captasoluciones.signage.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.captasoluciones.signage.SignageApplication
import com.captasoluciones.signage.player.ExoPlayerHolder
import com.captasoluciones.signage.player.PlayerEvent
import com.captasoluciones.signage.player.PlayerViewModel
import com.captasoluciones.signage.player.PlayerViewModelFactory
import com.captasoluciones.signage.player.Screen
import com.captasoluciones.signage.service.WatchdogService
import com.captasoluciones.signage.ui.theme.SignagePlayerTheme
import com.captasoluciones.signage.util.KioskModeHelper
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModelFactory(application as SignageApplication)
    }

    private val holdHandler = Handler(Looper.getMainLooper())
    private var holdRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        KioskModeHelper.enableImmersiveMode(this)
        ContextCompat.startForegroundService(this, Intent(this, WatchdogService::class.java))

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val s = viewModel.uiState.value
                if (s.screen == Screen.SETUP && s.baseUrl.isNotBlank()) {
                    viewModel.closeSetup()
                }
                // Otherwise: swallow back press entirely - this is a kiosk, it never exits.
            }
        })

        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    PlayerEvent.RestartApp -> performRestart()
                }
            }
        }

        setContent {
            SignagePlayerTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val logEntries by viewModel.logEntries.collectAsStateWithLifecycle()
                val context = LocalContext.current
                val exoPlayerHolder = remember { ExoPlayerHolder(context) }
                val imageLoader = remember { (application as SignageApplication).container.imageLoader }

                DisposableEffect(Unit) {
                    onDispose { exoPlayerHolder.release() }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.screen == Screen.SETUP) {
                        SetupScreen(
                            state = state,
                            logEntries = logEntries,
                            onSave = viewModel::saveSetup,
                            onClose = viewModel::closeSetup
                        )
                    } else {
                        PlayerScreen(
                            state = state,
                            exoPlayerHolder = exoPlayerHolder,
                            imageLoader = imageLoader,
                            onVideoEnded = viewModel::signalItemFinishedNaturally,
                            onItemError = viewModel::signalItemError
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WatchdogService.reportAlive()
        KioskModeHelper.enableImmersiveMode(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            KioskModeHelper.enableImmersiveMode(this)
        }
    }

    /**
     * MENU opens setup immediately. Holding DPAD_CENTER/ENTER for 5 seconds also opens
     * it (while on the player screen only - normal short presses while already in setup
     * are left alone so text fields and buttons behave normally).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                viewModel.openSetup()
                return true
            }
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
            if (viewModel.uiState.value.screen == Screen.SETUP) {
                return super.dispatchKeyEvent(event)
            }
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        val runnable = Runnable { viewModel.openSetup() }
                        holdRunnable = runnable
                        holdHandler.postDelayed(runnable, 5_000L)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    holdRunnable?.let { holdHandler.removeCallbacks(it) }
                    holdRunnable = null
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        holdRunnable?.let { holdHandler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun performRestart() {
        val restartIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 1_500L, pendingIntent)
        finishAffinity()
        Process.killProcess(Process.myPid())
    }
}
