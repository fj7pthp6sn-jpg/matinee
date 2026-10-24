package com.tcltv.sahne.info

import android.content.Context

/** Small switches for the prayer-times strip and the match card, kept apart from the launcher's main settings. */
object InfoPrefs {
    private const val FILE = "info"
    private const val KEY_PRAYER = "show_prayer"
    private const val KEY_MATCHES = "show_matches"
    private const val KEY_SAVER_PRAYER = "saver_prayer"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun showPrayer(context: Context): Boolean = prefs(context).getBoolean(KEY_PRAYER, true)
    fun setShowPrayer(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_PRAYER, value).apply()

    fun showMatches(context: Context): Boolean = prefs(context).getBoolean(KEY_MATCHES, true)
    fun setShowMatches(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_MATCHES, value).apply()

    fun saverPrayer(context: Context): Boolean = prefs(context).getBoolean(KEY_SAVER_PRAYER, true)
    fun setSaverPrayer(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_SAVER_PRAYER, value).apply()
}
