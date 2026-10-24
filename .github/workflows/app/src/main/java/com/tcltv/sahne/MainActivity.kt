package com.tcltv.sahne

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import com.tcltv.sahne.data.AppLanguage
import com.tcltv.sahne.screensaver.ScreensaverActivity
import com.tcltv.sahne.screensaver.ScreensaverPrefs

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private val resumeTick = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One time: the city for weather and prayer times becomes Iğdır, unless the user has picked one since.
        val migrations = getSharedPreferences("migrations", Context.MODE_PRIVATE)
        if (!migrations.getBoolean("city_igdir", false)) {
            migrations.edit().putBoolean("city_igdir", true).apply()
            viewModel.update { setWeatherLocation(com.tcltv.sahne.data.LauncherSettings.DEFAULT_LOCATION) }
        }
        com.tcltv.sahne.ui.ThemeStore.load(this)
        setContent {
            SahneApp(viewModel = viewModel, resumeTick = resumeTick.intValue)
        }
    }

    private val idleHandler = Handler(Looper.getMainLooper())
    private val startScreensaver = Runnable {
        startActivity(Intent(this, ScreensaverActivity::class.java))
    }

    /** (Re)starts the idle countdown; does nothing when the screensaver timer is off. */
    private fun rearmIdleTimer() {
        idleHandler.removeCallbacks(startScreensaver)
        val minutes = ScreensaverPrefs.idleMinutes(this)
        if (minutes > 0) idleHandler.postDelayed(startScreensaver, minutes * 60_000L)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        rearmIdleTimer()
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        rearmIdleTimer()
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        rearmIdleTimer()
        viewModel.onHomeResumed()
        resumeTick.intValue++
    }

    override fun onPause() {
        idleHandler.removeCallbacks(startScreensaver)
        viewModel.gate.endAdminSession()
        viewModel.onHomePaused()
        super.onPause()
    }
}
