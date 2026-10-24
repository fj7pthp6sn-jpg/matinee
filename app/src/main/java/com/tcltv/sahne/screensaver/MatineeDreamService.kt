package com.tcltv.sahne.screensaver

import android.content.Context
import android.service.dreams.DreamService
import com.tcltv.sahne.data.AppLanguage

/** The system screensaver ("Daydream"). TVs that hide the screensaver setting can use the launcher's idle timer instead. */
class MatineeDreamService : DreamService() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(ScreensaverView(this))
    }
}
