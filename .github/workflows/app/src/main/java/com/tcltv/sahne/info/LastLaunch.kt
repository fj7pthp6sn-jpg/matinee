package com.tcltv.sahne.info

import android.content.Context

/**
 * When each app was last opened from the launcher. Many streaming apps publish nothing about what was playing,
 * so this is what "continue" can still show for them.
 */
object LastLaunch {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("last_launch", Context.MODE_PRIVATE)

    fun record(context: Context, packageName: String) {
        prefs(context).edit().putLong(packageName, System.currentTimeMillis()).apply()
    }

    fun get(context: Context, packageName: String): Long = prefs(context).getLong(packageName, 0L)
}
