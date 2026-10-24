package com.tcltv.sahne.screensaver

import android.content.Context

/**
 * Screensaver settings, kept in a tiny SharedPreferences file so the screensaver (which the system starts outside the
 * launcher's activity) can read them from anywhere without touching the launcher's DataStore.
 */
object ScreensaverPrefs {
    private const val FILE = "screensaver"
    private const val KEY_IDLE = "idle_minutes"
    private const val KEY_THEME = "theme"
    private const val KEY_CALENDAR_URL = "calendar_url"
    private const val KEY_LAYOUT = "layout"
    private const val KEY_WEATHER = "weather"

    /** Minutes without a key press before the launcher starts the screensaver itself; 0 means never. */
    val IDLE_OPTIONS = intArrayOf(0, 1, 3, 5, 10)
    /** 0-3: flowing colours; 4-7: drawn scenes (mountains, starry night, city, sea). */
    const val THEME_COUNT = 9

    /** Shows one of the photos placed in `assets/screensaver` (or the app's `screensaver` files folder). */
    const val PHOTO_THEME = 8
    const val LAYOUT_CLOCK = 0
    const val LAYOUT_CALENDAR = 1

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun idleMinutes(context: Context): Int = prefs(context).getInt(KEY_IDLE, 0)

    fun setIdleMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_IDLE, minutes).apply()
    }

    fun theme(context: Context): Int = prefs(context).getInt(KEY_THEME, 0).coerceIn(0, THEME_COUNT - 1)

    fun setTheme(context: Context, theme: Int) {
        prefs(context).edit().putInt(KEY_THEME, theme.coerceIn(0, THEME_COUNT - 1)).apply()
    }

    fun calendarUrl(context: Context): String = prefs(context).getString(KEY_CALENDAR_URL, "").orEmpty()

    fun setCalendarUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_CALENDAR_URL, url).apply()
        // A different address must not keep showing the old calendar's events.
        CalendarFeed.clearCache(context)
    }

    fun layout(context: Context): Int = prefs(context).getInt(KEY_LAYOUT, LAYOUT_CLOCK)

    fun setLayout(context: Context, layout: Int) {
        prefs(context).edit().putInt(KEY_LAYOUT, layout).apply()
    }

    fun showWeather(context: Context): Boolean = prefs(context).getBoolean(KEY_WEATHER, true)

    fun setShowWeather(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_WEATHER, show).apply()
    }
}
