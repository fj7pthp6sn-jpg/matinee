package com.tcltv.sahne.screensaver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Landscapes drawn in code (no photos needed): mountains at sunset, a starry night over a lake, a city skyline at
 * dusk and the sea. Everything moves slowly in a loop that closes seamlessly, so a long run never jumps.
 */
internal class SceneBackdrop(context: Context, private val scene: Int) : View(context) {

    companion object {
        const val MOUNTAINS = 0
        const val NIGHT = 1
        const val CITY = 2
        const val SEA = 3
        const val COUNT = 4
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private var phase = 0f

    private var sky: Shader? = null
    private var glow: Shader? = null
    private var ground: Shader? = null
    private val ridges = Array(3) { Path() }
    private val near = Path()
    private val far = Path()

    // Seeded, so the same scene looks the same every time it starts.
    private val random = Random(7)
    private val starX = FloatArray(150) { random.nextFloat() }
    private val starY = FloatArray(150) { random.nextFloat() }
    private var winX = FloatArray(0)
    private var winY = FloatArray(0)
    private var winSize = 0f

    fun setPhase(value: Float) {
        phase = value
        invalidate()
    }

    private fun linear(h: Float, colors: IntArray, stops: FloatArray) =
        LinearGradient(0f, 0f, 0f, h, colors, stops, Shader.TileMode.CLAMP)

    private fun radial(radius: Float, color: Int) =
        RadialGradient(0f, 0f, radius, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)

    /** A smooth, hilly line from the left to the right edge, closed along the bottom. */
    private fun ridge(target: Path, w: Float, h: Float, base: Float, amp: Float, f1: Float, f2: Float, seed: Float) {
        target.reset()
        target.moveTo(0f, h)
        var x = 0f
        while (x <= w + 12f) {
            val u = x / w
            val y = base - amp * (0.5f + 0.5f * sin(u * f1 * PI.toFloat() + seed)) -
                amp * 0.35f * sin(u * f2 * PI.toFloat() + seed * 1.7f)
            target.lineTo(x, y)
            x += 12f
        }
        target.lineTo(w, h)
        target.close()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fw = w.toFloat()
        val fh = h.toFloat()
        when (scene) {
            MOUNTAINS -> {
                sky = linear(fh * 0.75f, intArrayOf(0xFF2A2F6B.toInt(), 0xFF8E4A8F.toInt(), 0xFFE2623A.toInt(), 0xFFF6B26B.toInt()), floatArrayOf(0f, 0.45f, 0.8f, 1f))
                glow = radial(fh * 0.55f, 0xCCFFD08A.toInt())
                ridge(ridges[0], fw, fh, fh * 0.70f, fh * 0.16f, 3.0f, 9.0f, 0.6f)
                ridge(ridges[1], fw, fh, fh * 0.80f, fh * 0.13f, 4.7f, 12.0f, 2.1f)
                ridge(ridges[2], fw, fh, fh * 0.92f, fh * 0.10f, 6.4f, 15.0f, 4.0f)
            }
            NIGHT -> {
                sky = linear(fh * 0.7f, intArrayOf(0xFF02030A.toInt(), 0xFF0B1433.toInt(), 0xFF1D2B5C.toInt()), floatArrayOf(0f, 0.6f, 1f))
                glow = radial(fh * 0.35f, 0x66DDE6FF.toInt())
                ground = LinearGradient(0f, fh * 0.7f, 0f, fh, 0xFF0B1433.toInt(), 0xFF02030A.toInt(), Shader.TileMode.CLAMP)
                ridge(ridges[0], fw, fh, fh * 0.72f, fh * 0.07f, 2.3f, 7.0f, 1.0f)
            }
            CITY -> {
                sky = linear(fh * 0.8f, intArrayOf(0xFF1B1B3F.toInt(), 0xFF6C3A7A.toInt(), 0xFFE0735A.toInt(), 0xFFF3B27A.toInt()), floatArrayOf(0f, 0.4f, 0.8f, 1f))
                glow = radial(fh * 0.7f, 0x99FFB067.toInt())
                ground = LinearGradient(0f, fh * 0.86f, 0f, fh, 0xFF14101F.toInt(), 0xFF05040C.toInt(), Shader.TileMode.CLAMP)
                buildSkyline(fw, fh)
            }
            SEA -> {
                sky = linear(fh * 0.56f, intArrayOf(0xFF0B3C5D.toInt(), 0xFF2E7D9A.toInt(), 0xFFF4C27A.toInt()), floatArrayOf(0f, 0.65f, 1f))
                glow = radial(fh * 0.5f, 0xBBFFE2A8.toInt())
            }
        }
    }

    private fun buildSkyline(w: Float, h: Float) {
        val groundY = h * 0.86f
        val windowsX = ArrayList<Float>()
        val windowsY = ArrayList<Float>()
        far.reset()
        near.reset()
        var x = 0f
        while (x < w) {
            val bw = w * (0.022f + random.nextFloat() * 0.03f)
            far.addRect(x, groundY - h * (0.12f + random.nextFloat() * 0.16f), x + bw, groundY, Path.Direction.CW)
            x += bw * 0.85f
        }
        winSize = max(2f, h * 0.007f)
        x = 0f
        while (x < w) {
            val bw = w * (0.025f + random.nextFloat() * 0.035f)
            val bh = h * (0.07f + random.nextFloat() * 0.15f)
            near.addRect(x, groundY - bh, x + bw, groundY, Path.Direction.CW)
            var wy = groundY - bh + winSize * 2.5f
            while (wy < groundY - winSize * 3f) {
                var wx = x + winSize * 1.5f
                while (wx < x + bw - winSize * 2f) {
                    windowsX.add(wx)
                    windowsY.add(wy)
                    wx += winSize * 2.6f
                }
                wy += winSize * 3.2f
            }
            x += bw * 1.02f
        }
        // Landmarks: a domed mosque with two minarets and a stone tower with a conical roof.
        val mx = w * 0.64f
        val dome = h * 0.075f
        near.addRect(mx - dome * 1.5f, groundY - h * 0.1f, mx + dome * 1.5f, groundY, Path.Direction.CW)
        near.addCircle(mx, groundY - h * 0.1f, dome, Path.Direction.CW)
        near.addCircle(mx - dome * 1.15f, groundY - h * 0.1f, dome * 0.5f, Path.Direction.CW)
        near.addCircle(mx + dome * 1.15f, groundY - h * 0.1f, dome * 0.5f, Path.Direction.CW)
        for (side in intArrayOf(-1, 1)) {
            val cx = mx + side * dome * 2.2f
            val mw = h * 0.011f
            near.addRect(cx - mw, groundY - h * 0.34f, cx + mw, groundY, Path.Direction.CW)
            near.moveTo(cx - mw * 1.6f, groundY - h * 0.34f)
            near.lineTo(cx, groundY - h * 0.41f)
            near.lineTo(cx + mw * 1.6f, groundY - h * 0.34f)
            near.close()
        }
        val tx = w * 0.2f
        val tw = h * 0.032f
        near.addRect(tx - tw, groundY - h * 0.3f, tx + tw, groundY, Path.Direction.CW)
        near.addRect(tx - tw * 1.35f, groundY - h * 0.32f, tx + tw * 1.35f, groundY - h * 0.28f, Path.Direction.CW)
        near.moveTo(tx - tw * 1.35f, groundY - h * 0.32f)
        near.lineTo(tx, groundY - h * 0.42f)
        near.lineTo(tx + tw * 1.35f, groundY - h * 0.32f)
        near.close()
        winX = windowsX.toFloatArray()
        winY = windowsY.toFloatArray()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = phase * 2f * PI.toFloat()
        when (scene) {
            MOUNTAINS -> drawMountains(canvas, w, h, t)
            NIGHT -> drawNight(canvas, w, h, t)
            CITY -> drawCity(canvas, w, h, t)
            SEA -> drawSea(canvas, w, h, t)
        }
    }

    private fun fill(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, shader: Shader?) {
        paint.shader = shader
        canvas.drawRect(left, top, right, bottom, paint)
        paint.shader = null
    }

    private fun glowAt(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        paint.shader = glow
        canvas.drawCircle(0f, 0f, max(width, height).toFloat(), paint)
        paint.shader = null
        canvas.restore()
    }

    private fun disc(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        paint.color = color
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun drawMountains(canvas: Canvas, w: Float, h: Float, t: Float) {
        fill(canvas, 0f, 0f, w, h, sky)
        val sx = w * (0.62f + 0.02f * sin(t))
        val sy = h * 0.62f
        glowAt(canvas, sx, sy)
        disc(canvas, sx, sy, h * 0.06f, 0xFFFFE2A8.toInt())
        paint.color = Color.WHITE
        for (i in 0 until 3) {
            val x = ((phase + i / 3f) % 1f) * (w * 1.4f) - w * 0.2f
            val y = h * (0.16f + 0.09f * i)
            paint.alpha = 38
            rect.set(x, y, x + w * 0.26f, y + h * 0.05f)
            canvas.drawOval(rect, paint)
        }
        val colors = intArrayOf(0xFF4B3A6B.toInt(), 0xFF33264F.toInt(), 0xFF1B1430.toInt())
        for (i in 0 until 3) {
            paint.alpha = 255
            paint.color = colors[i]
            canvas.drawPath(ridges[i], paint)
        }
    }

    private fun drawNight(canvas: Canvas, w: Float, h: Float, t: Float) {
        fill(canvas, 0f, 0f, w, h * 0.72f, sky)
        paint.color = Color.WHITE
        for (i in starX.indices) {
            val twinkle = 0.5f + 0.5f * sin(t * (1 + i % 3) + i)
            paint.alpha = (70 + 170 * twinkle).toInt().coerceIn(0, 255)
            canvas.drawCircle(starX[i] * w, starY[i] * h * 0.64f, if (i % 5 == 0) 2.4f else 1.3f, paint)
        }
        val mx = w * 0.76f
        val my = h * 0.2f
        glowAt(canvas, mx, my)
        paint.alpha = 255
        disc(canvas, mx, my, h * 0.045f, 0xFFF4F1DE.toInt())
        fill(canvas, 0f, h * 0.72f, w, h, ground)
        // Moonlight on the water.
        for (k in 0 until 16) {
            val y = h * 0.74f + k * h * 0.016f
            val len = w * 0.045f * (1f - k / 20f) + 6f * sin(t * 2f + k)
            paint.color = 0xFFDDE6FF.toInt()
            paint.alpha = max(10, 110 - k * 6)
            canvas.drawRect(mx - len, y, mx + len, y + 2.5f, paint)
        }
        paint.alpha = 255
        paint.color = 0xFF01020A.toInt()
        canvas.drawPath(ridges[0], paint)
    }

    private fun drawCity(canvas: Canvas, w: Float, h: Float, t: Float) {
        fill(canvas, 0f, 0f, w, h * 0.86f, sky)
        glowAt(canvas, w * 0.36f, h * 0.8f + h * 0.01f * sin(t))
        paint.alpha = 255
        paint.color = 0xFF2A2347.toInt()
        canvas.drawPath(far, paint)
        paint.color = 0xFF0F0C1E.toInt()
        canvas.drawPath(near, paint)
        paint.color = 0xFFFFD27A.toInt()
        for (i in winX.indices) {
            // Each window lights up and goes dark on its own slow rhythm.
            if (sin(t * 2f + i * 12.9898f) > -0.15f) {
                paint.alpha = 200
                canvas.drawRect(winX[i], winY[i], winX[i] + winSize, winY[i] + winSize * 1.4f, paint)
            }
        }
        paint.alpha = 255
        fill(canvas, 0f, h * 0.86f, w, h, ground)
        // Lights on the water.
        paint.color = 0xFFFFC27A.toInt()
        for (k in 0 until 10) {
            val y = h * 0.875f + k * h * 0.012f
            val len = w * 0.04f * (1f - k / 14f) + 5f * sin(t * 2f + k)
            paint.alpha = max(10, 100 - k * 8)
            canvas.drawRect(w * 0.36f - len, y, w * 0.36f + len, y + 2.5f, paint)
        }
        paint.alpha = 255
    }

    private fun drawSea(canvas: Canvas, w: Float, h: Float, t: Float) {
        val horizon = h * 0.54f
        fill(canvas, 0f, 0f, w, horizon, sky)
        glowAt(canvas, w * 0.5f, horizon)
        disc(canvas, w * 0.5f, horizon - h * 0.02f, h * 0.05f, 0xFFFFE9B8.toInt())
        val colors = intArrayOf(0xFF1F6F8B.toInt(), 0xFF185B76.toInt(), 0xFF124860.toInt(), 0xFF0B3347.toInt())
        for (i in 0 until 4) {
            val y0 = h * (0.56f + 0.1f * i)
            val amp = h * (0.012f + 0.004f * i)
            val dir = if (i % 2 == 0) 1f else -1f
            path.reset()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 16f) {
                path.lineTo(x, y0 + amp * sin(x / w * 2f * PI.toFloat() * (2 + i) + t * dir * (1 + i)))
                x += 16f
            }
            path.lineTo(w, h)
            path.close()
            paint.color = colors[i]
            paint.alpha = 255
            canvas.drawPath(path, paint)
        }
        // Sun glitter on the first waves.
        paint.color = 0xFFFFE9B8.toInt()
        for (k in 0 until 12) {
            val y = horizon + h * 0.012f + k * h * 0.018f
            val len = w * 0.05f * (0.4f + k / 12f) + 8f * sin(t * 3f + k)
            paint.alpha = max(15, 140 - k * 9)
            canvas.drawRect(w * 0.5f - len, y, w * 0.5f + len, y + 2.5f, paint)
        }
        paint.alpha = 255
    }
}
