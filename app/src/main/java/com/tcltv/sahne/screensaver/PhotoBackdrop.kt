package com.tcltv.sahne.screensaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import java.io.File

/**
 * A still photograph as the screensaver picture: nothing moves, so it costs the TV almost nothing. Photos are the
 * .jpg/.png/.webp files in `assets/screensaver` (added to the project) or in the app's own `files/screensaver` folder.
 * One is picked at random each time the screensaver starts.
 */
internal class PhotoBackdrop private constructor(context: Context, private val bitmap: Bitmap) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint()
    private val matrix = Matrix()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Centre-crop to the screen.
        val scale = maxOf(w / bitmap.width, h / bitmap.height)
        matrix.setScale(scale, scale)
        matrix.postTranslate((w - bitmap.width * scale) / 2f, (h - bitmap.height * scale) / 2f)
        canvas.drawBitmap(bitmap, matrix, paint)
        // Keeps the clock readable on bright photos.
        shade.shader = LinearGradient(0f, h * 0.35f, 0f, h, Color.TRANSPARENT, 0xCC000000.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, shade)
    }

    companion object {
        private val EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp")

        /** Null when there is no photo, so the screensaver falls back to a drawn scene. */
        fun create(context: Context): PhotoBackdrop? {
            val assets = runCatching { context.assets.list("screensaver")?.toList() }.getOrNull().orEmpty()
                .filter { name -> EXTENSIONS.any { name.lowercase().endsWith(it) } }
            val files = File(context.filesDir, "screensaver").listFiles()?.map { it.name }.orEmpty()
                .filter { name -> EXTENSIONS.any { name.lowercase().endsWith(it) } }
            val all = assets.map { "a:$it" } + files.map { "f:$it" }
            if (all.isEmpty()) return null
            val metrics = context.resources.displayMetrics
            val bitmap = all.shuffled().firstNotNullOfOrNull { choice -> decode(context, choice, metrics.widthPixels) } ?: return null
            return PhotoBackdrop(context, bitmap)
        }

        private fun decode(context: Context, choice: String, targetWidth: Int): Bitmap? = runCatching {
            val name = choice.substring(2)
            fun open() = if (choice.startsWith("a:")) context.assets.open("screensaver/$name") else File(File(context.filesDir, "screensaver"), name).inputStream()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            open().use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }
}
