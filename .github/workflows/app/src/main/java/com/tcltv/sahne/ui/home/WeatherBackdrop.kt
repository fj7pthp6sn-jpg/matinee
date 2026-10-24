package com.tcltv.sahne.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.unit.IntSize
import com.tcltv.sahne.data.WeatherCondition
import kotlin.random.Random

private const val RENDER_WIDTH = 640

private fun alpha(color: Int, a: Float): Int = (color and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)

/**
 * The weather theme's picture, drawn once per weather and time of day (not animated, to keep the TV light):
 * sun and clouds, a starry night, rain, snow, a storm or fog.
 */
internal fun renderWeatherBackdrop(screen: IntSize, condition: WeatherCondition, isDay: Boolean, base: Int, accent: Int): Bitmap {
    val scale = minOf(1f, RENDER_WIDTH.toFloat() / screen.width)
    val w = (screen.width * scale).toInt().coerceAtLeast(1)
    val h = (screen.height * scale).toInt().coerceAtLeast(1)
    val fw = w.toFloat()
    val fh = h.toFloat()
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    val random = Random(11)

    // Sky: the theme's base colour at the top, a little lighter (or warmer at sunset hues) towards the horizon.
    val horizon = when {
        condition == WeatherCondition.CLEAR && isDay -> 0xFF6FB7F0.toInt()
        condition == WeatherCondition.CLEAR -> 0xFF14224F.toInt()
        isDay -> alpha(accent, 0.55f).let { mix(base, accent, 0.3f) }
        else -> mix(base, accent, 0.18f)
    }
    paint.shader = LinearGradient(0f, 0f, 0f, fh, base, horizon, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, fw, fh, paint)
    paint.shader = null

    fun blob(cx: Float, cy: Float, radius: Float, color: Int, a: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        paint.shader = RadialGradient(0f, 0f, radius, alpha(color, a), alpha(color, 0f), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, radius, paint)
        paint.shader = null
        canvas.restore()
    }

    fun clouds(count: Int, color: Int, a: Float, top: Float, bottom: Float) {
        for (i in 0 until count) {
            val cx = fw * random.nextFloat()
            val cy = fh * (top + (bottom - top) * random.nextFloat())
            blob(cx, cy, fh * (0.22f + 0.18f * random.nextFloat()), color, a)
        }
    }

    fun stars(count: Int) {
        paint.color = Color.WHITE
        for (i in 0 until count) {
            paint.alpha = (60 + 190 * random.nextFloat()).toInt()
            canvas.drawCircle(fw * random.nextFloat(), fh * 0.7f * random.nextFloat(), if (i % 9 == 0) 1.8f else 0.9f, paint)
        }
        paint.alpha = 255
    }

    fun rain(count: Int, a: Float) {
        paint.color = alpha(0xFFBFD4F0.toInt(), a)
        paint.strokeWidth = 1.1f
        for (i in 0 until count) {
            val x = fw * random.nextFloat()
            val y = fh * random.nextFloat()
            canvas.drawLine(x, y, x - fh * 0.02f, y + fh * 0.06f, paint)
        }
    }

    when (condition) {
        WeatherCondition.CLEAR -> if (isDay) {
            blob(fw * 0.82f, fh * 0.12f, fh * 0.9f, 0xFFFFD27A.toInt(), 0.75f)
            blob(fw * 0.82f, fh * 0.12f, fh * 0.12f, 0xFFFFF2C4.toInt(), 1f)
            clouds(4, Color.WHITE, 0.16f, 0.2f, 0.6f)
        } else {
            stars(220)
            blob(fw * 0.8f, fh * 0.16f, fh * 0.55f, 0xFFDDE6FF.toInt(), 0.35f)
            blob(fw * 0.8f, fh * 0.16f, fh * 0.055f, 0xFFF4F1DE.toInt(), 1f)
        }
        WeatherCondition.CLOUDY -> {
            clouds(9, if (isDay) Color.WHITE else 0xFF8FA3BD.toInt(), if (isDay) 0.22f else 0.14f, 0.05f, 0.75f)
            if (!isDay) stars(40)
        }
        WeatherCondition.RAIN -> {
            clouds(7, if (isDay) 0xFF9FB4CC.toInt() else 0xFF5A6C85.toInt(), 0.2f, 0.0f, 0.4f)
            rain(260, if (isDay) 0.3f else 0.22f)
        }
        WeatherCondition.SNOW -> {
            clouds(6, Color.WHITE, 0.2f, 0.05f, 0.5f)
            paint.color = Color.WHITE
            for (i in 0 until 170) {
                paint.alpha = (110 + 140 * random.nextFloat()).toInt()
                canvas.drawCircle(fw * random.nextFloat(), fh * random.nextFloat(), 1.2f + 2.2f * random.nextFloat(), paint)
            }
            paint.alpha = 255
        }
        WeatherCondition.STORM -> {
            clouds(8, 0xFF6A5C8C.toInt(), 0.22f, 0.0f, 0.5f)
            blob(fw * 0.7f, fh * 0.3f, fh * 0.7f, 0xFFB79CFF.toInt(), 0.22f)
            rain(220, 0.25f)
        }
        WeatherCondition.FOG -> {
            for (i in 0 until 6) {
                val y = fh * (0.1f + 0.15f * i)
                paint.shader = LinearGradient(0f, y, 0f, y + fh * 0.22f, alpha(Color.WHITE, 0f), alpha(Color.WHITE, 0.12f), Shader.TileMode.MIRROR)
                canvas.drawRect(0f, y, fw, y + fh * 0.22f, paint)
            }
            paint.shader = null
        }
    }

    // The same readability veils as the other themes: darker on the left for the title, melting into the base at the bottom.
    paint.shader = LinearGradient(
        0f, 0f, fw, 0f,
        intArrayOf(alpha(base, 0.85f), alpha(base, 0.45f), Color.TRANSPARENT),
        floatArrayOf(0f, 0.38f, 0.62f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, fw, fh, paint)
    paint.shader = LinearGradient(
        0f, 0f, 0f, fh,
        intArrayOf(Color.TRANSPARENT, alpha(base, 0.8f), base),
        floatArrayOf(0.5f, 0.78f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, fw, fh, paint)
    paint.shader = null
    return bitmap
}

private fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1f - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
}
