package com.tcltv.sahne.data

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads Android TV's "Watch next" list: the items streaming apps (Netflix, Prime, Disney+ ...) leave there when you stop
 * halfway. Reading it needs the READ_TV_LISTINGS permission, which is given once over ADB:
 * `adb shell pm grant com.yigitcan.matinee android.permission.READ_TV_LISTINGS`.
 */
object WatchNext {
    /** Shown in Settings so a missing permission or an empty list is easy to tell apart. */
    @Volatile
    var status: String = "Henüz okunmadı"

    fun read(context: Context): Map<String, LastPlayed> {
        val app = context.applicationContext
        val result = LinkedHashMap<String, LastPlayed>()
        var rows = 0
        try {
            val projection = arrayOf(
                TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME,
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE,
                TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE,
            )
            val cursor: Cursor? = app.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, projection, null, null, null)
            cursor?.use { c ->
                while (c.moveToNext()) {
                    rows++
                    val pkg = c.getString(0)?.takeIf { it.isNotBlank() } ?: continue
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    // Only "continue watching" items (type 0); "next episode", "new" and "watchlist" are other rows.
                    if (!c.isNull(8) && c.getInt(8) != TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE) continue
                    val engaged = c.getLong(7)
                    val previous = result[pkg]
                    if (previous != null && previous.updatedAt >= engaged) continue
                    val episode = c.getString(2)?.takeIf { it.isNotBlank() }
                    val description = c.getString(3)?.takeIf { it.isNotBlank() }
                    val art = c.getString(4)?.let { saveArtwork(app, pkg, it) }
                    result[pkg] = LastPlayed(
                        packageName = pkg,
                        title = title,
                        subtitle = episode ?: description,
                        artworkPath = art?.path,
                        artworkIsSquare = false,
                        positionMs = if (c.isNull(5)) -1 else c.getLong(5),
                        durationMs = if (c.isNull(6)) -1 else c.getLong(6),
                        updatedAt = if (engaged > 0) engaged else System.currentTimeMillis(),
                    )
                }
            }
            status = if (cursor == null) "Liste açılamadı" else "İzin var, $rows kayıt, ${result.size} devam eden"
        } catch (e: SecurityException) {
            status = "İzin verilmemiş (adb ile verilmeli)"
        } catch (e: Exception) {
            status = "Hata: ${e.javaClass.simpleName}"
        }
        return result
    }

    private fun saveArtwork(context: Context, pkg: String, uri: String): File? = try {
        val bitmap: Bitmap? = when {
            uri.startsWith("http") -> {
                val conn = URL(uri).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            }
            else -> context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        }
        bitmap?.let {
            val dir = File(context.cacheDir, "watchnext").apply { mkdirs() }
            val scale = minOf(1f, 640f / it.width)
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(it, 640, (it.height * scale).toInt().coerceAtLeast(1), true) else it
            File(dir, "$pkg.jpg").also { f -> f.outputStream().use { o -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, o) } }
        }
    } catch (e: Exception) {
        null
    }
}
