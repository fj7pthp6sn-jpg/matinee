package com.tcltv.sahne.info

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Team logos: downloaded once, kept in the app's cache folder and in memory. Blocking; call off the main thread. */
object LogoCache {
    private const val MAX_BYTES = 2_000_000
    private const val MAX_SIDE = 96
    private val memory = LruCache<String, Bitmap>(32)

    /** The last thing that went wrong, shown in settings so a missing badge can be explained. */
    @Volatile
    var lastStatus: String = "-"

    fun load(context: Context, url: String): Bitmap? {
        memory.get(url)?.let { return it }
        val dir = File(context.applicationContext.cacheDir, "logos")
        dir.mkdirs()
        val file = File(dir, Integer.toHexString(url.hashCode()) + ".img")
        if (!file.isFile && !download(url, file)) return null
        val decoded = BitmapFactory.decodeFile(file.path) ?: run {
            lastStatus = "decode failed"
            file.delete()
            return null
        }
        lastStatus = "ok"
        val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            decoded
        }
        memory.put(url, bitmap)
        return bitmap
    }

    private fun download(url: String, target: File): Boolean {
        if (!url.startsWith("https://")) return false
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            // An image CDN can turn away unknown clients, so this identifies as an ordinary browser.
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 Chrome/110 Safari/537.36")
            connection.setRequestProperty("Accept", "image/png,image/*;q=0.8,*/*;q=0.5")
            connection.instanceFollowRedirects = true
            if (connection.responseCode != 200) {
                lastStatus = "HTTP " + connection.responseCode
                return false
            }
            val tmp = File(target.path + ".tmp")
            var total = 0
            connection.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) { tmp.delete(); return false }
                        output.write(buffer, 0, read)
                    }
                }
            }
            tmp.renameTo(target)
        } catch (e: Exception) {
            lastStatus = e.javaClass.simpleName + ": " + (e.message ?: "").take(60)
            false
        } finally {
            connection.disconnect()
        }
    }
}
