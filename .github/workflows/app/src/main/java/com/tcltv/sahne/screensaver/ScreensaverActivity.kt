package com.tcltv.sahne.screensaver

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tcltv.sahne.data.AppLanguage

/**
 * Shows the screensaver from inside the launcher: for the preview in settings and for the idle timer, which works on
 * TVs that do not let apps provide a system screensaver. Any key press closes it.
 */
class ScreensaverActivity : Activity() {

    private var openedAt = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedAt = SystemClock.elapsedRealtime()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(ScreensaverView(this))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The key release that opened the preview can land here first, so the first moments are ignored.
        if (event.action == KeyEvent.ACTION_UP && SystemClock.elapsedRealtime() - openedAt > GRACE_MS) finish()
        return true
    }

    private companion object {
        const val GRACE_MS = 800L
    }
}
