package com.tcltv.sahne.screensaver

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.tcltv.sahne.R
import android.graphics.Paint as AndroidPaint
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.tcltv.sahne.data.SettingsStore
import com.tcltv.sahne.data.TemperatureUnit
import com.tcltv.sahne.data.WeatherClient
import com.tcltv.sahne.data.WeatherCondition
import android.graphics.RectF
import android.graphics.Paint.Align
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private class Look(val background: Int, val blobs: IntArray)

private val LOOKS = listOf(
    Look(0xFF05070F.toInt(), intArrayOf(0xFF2B3A67.toInt(), 0xFF5B3FA0.toInt(), 0xFF1D6FA5.toInt())),
    Look(0xFF140A0F.toInt(), intArrayOf(0xFFE2623A.toInt(), 0xFFB03A5B.toInt(), 0xFF6B2D8F.toInt())),
    Look(0xFF03121A.toInt(), intArrayOf(0xFF0E7C86.toInt(), 0xFF1B4F8F.toInt(), 0xFF3AB0A2.toInt())),
    Look(0xFF06120B.toInt(), intArrayOf(0xFF2E7D4F.toInt(), 0xFF8DBB4A.toInt(), 0xFF1B5E5A.toInt())),
)

private val TWO_PI = (2.0 * Math.PI).toFloat()
private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val HOP_MS = 20_000L
private const val EVENTS_REFRESH_MS = 15L * 60L * 1000L
private const val MAX_EVENTS = 5

/**
 * The screensaver picture: a slowly moving backdrop (flowing colours or a drawn landscape) with either a big thin clock
 * or a month calendar beside it, the weather for the chosen city and, when a calendar link is set, the next events.
 * The text drifts a little too, so no pixel stays lit for hours. Plain views (no Compose), because the system starts
 * it inside a DreamService.
 */
class ScreensaverView(context: Context) : FrameLayout(context) {

    private val themeIndex = ScreensaverPrefs.theme(context)
    private val calendarLayout = ScreensaverPrefs.layout(context) == ScreensaverPrefs.LAYOUT_CALENDAR
    private val flow: Backdrop? = if (themeIndex < LOOKS.size) Backdrop(context, LOOKS[themeIndex]) else null
    private val photo: PhotoBackdrop? = if (themeIndex == ScreensaverPrefs.PHOTO_THEME) PhotoBackdrop.create(context) else null
    private val scene: SceneBackdrop? =
        if (photo == null && themeIndex >= LOOKS.size) SceneBackdrop(context, (themeIndex - LOOKS.size).coerceAtMost(SceneBackdrop.COUNT - 1)) else null
    private val content = LinearLayout(context)
    private val time = TextClock(context)
    private val date = TextClock(context)
    private val weather = TextView(context)
    private val prayer = TextView(context)
    private val upcoming = TextView(context)
    private val events = TextView(context)
    private val month = MonthView(context)
    private var hop = 0
    private val step = object : Runnable {
        override fun run() {
            val phase = (hop % 12) / 12f
            flow?.setPhase(phase)
            scene?.setPhase(phase)
            content.translationX = sin(phase * TWO_PI) * dp(24)
            content.translationY = cos(phase * TWO_PI) * dp(16)
            hop++
            postDelayed(this, HOP_MS)
        }
    }
    private var attached = false
    private val refresh = Runnable { loadEvents(); loadWeather(); loadPrayer() }

    init {
        setBackgroundColor(Color.BLACK)
        addView((flow ?: photo ?: scene ?: Backdrop(context, LOOKS[0])) as View, LayoutParams(MATCH, MATCH))

        val thin = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        val light = Typeface.create("sans-serif-light", Typeface.NORMAL)

        time.format12Hour = "h:mm"
        time.format24Hour = "HH:mm"
        time.typeface = thin
        time.setTextColor(Color.WHITE)
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (calendarLayout) 76f else 104f)
        time.includeFontPadding = false

        date.format12Hour = "EEEE, d MMMM"
        date.format24Hour = "EEEE, d MMMM"
        date.typeface = light
        date.setTextColor(0xE6FFFFFF.toInt())
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)

        weather.typeface = light
        weather.setTextColor(0xE6FFFFFF.toInt())
        weather.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        weather.visibility = View.GONE

        prayer.typeface = light
        prayer.setTextColor(0xCCFFFFFF.toInt())
        prayer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        prayer.visibility = View.GONE

        upcoming.text = context.getString(R.string.screensaver_upcoming).uppercase()
        upcoming.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        upcoming.setTextColor(0x99FFFFFF.toInt())
        upcoming.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        upcoming.letterSpacing = 0.12f
        upcoming.visibility = View.GONE

        events.typeface = light
        events.setTextColor(0xCCFFFFFF.toInt())
        events.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        events.setLineSpacing(0f, 1.25f)
        events.visibility = View.GONE

        val left = LinearLayout(context)
        left.orientation = LinearLayout.VERTICAL
        left.addView(time, LinearLayout.LayoutParams(WRAP, WRAP))
        left.addView(date, LinearLayout.LayoutParams(WRAP, WRAP))
        val weatherGap = LinearLayout.LayoutParams(WRAP, WRAP)
        weatherGap.topMargin = dp(10)
        left.addView(weather, weatherGap)
        val prayerGap = LinearLayout.LayoutParams(WRAP, WRAP)
        prayerGap.topMargin = dp(6)
        left.addView(prayer, prayerGap)
        val gap = LinearLayout.LayoutParams(WRAP, WRAP)
        gap.topMargin = dp(28)
        left.addView(upcoming, gap)
        val list = LinearLayout.LayoutParams(WRAP, WRAP)
        list.topMargin = dp(8)
        left.addView(events, list)

        val pad = dp(72)
        content.setPadding(pad, pad, pad, pad)
        if (calendarLayout) {
            content.orientation = LinearLayout.HORIZONTAL
            content.gravity = Gravity.BOTTOM
            content.addView(left, LinearLayout.LayoutParams(0, WRAP, 1f))
            content.addView(month, LinearLayout.LayoutParams(dp(380), dp(330)))
        } else {
            content.orientation = LinearLayout.VERTICAL
            content.addView(left, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        addView(content, LayoutParams(MATCH, WRAP, Gravity.BOTTOM or Gravity.START))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        // Burn-in protection without per-frame work: the picture stays still and the text hops a little every 20 s.
        step.run()
        loadEvents()
        loadWeather()
        loadPrayer()
    }

    override fun onDetachedFromWindow() {
        attached = false
        removeCallbacks(step)
        removeCallbacks(refresh)
        super.onDetachedFromWindow()
    }

    private fun loadEvents() {
        val app = context.applicationContext
        Thread {
            val all = runCatching { CalendarFeed.upcoming(app, 60) }.getOrDefault(emptyList())
            post { if (attached) showEvents(all) }
        }.start()
        removeCallbacks(refresh)
        postDelayed(refresh, EVENTS_REFRESH_MS)
    }

    private fun loadWeather() {
        if (!ScreensaverPrefs.showWeather(context)) return
        val app = context.applicationContext
        Thread {
            val line = runCatching {
                runBlocking {
                    val settings = SettingsStore(app).settings.first()
                    val now = WeatherClient().current(settings.weatherLocation, settings.temperatureUnit)
                    val symbol = if (now.unit == TemperatureUnit.FAHRENHEIT) "°F" else "°C"
                    val label = app.getString(
                        when (now.condition) {
                            WeatherCondition.CLEAR -> if (now.isDay) R.string.weather_clear_day else R.string.weather_clear_night
                            WeatherCondition.CLOUDY -> R.string.weather_cloudy
                            WeatherCondition.FOG -> R.string.weather_fog
                            WeatherCondition.RAIN -> R.string.weather_rain
                            WeatherCondition.SNOW -> R.string.weather_snow
                            WeatherCondition.STORM -> R.string.weather_storm
                        },
                    )
                    now.temperature.toString() + symbol + "   " + label + "   ·   " + settings.weatherLocation.name.substringBefore(',')
                }
            }.getOrNull()
            post {
                if (attached && line != null) {
                    weather.text = line
                    weather.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun loadPrayer() {
        if (!com.tcltv.sahne.info.InfoPrefs.saverPrayer(context)) return
        val app = context.applicationContext
        Thread {
            val line = runCatching {
                runBlocking {
                    val location = SettingsStore(app).settings.first().weatherLocation
                    val next = com.tcltv.sahne.info.InfoClient.nextPrayer(app, location)
                    if (next == null) null
                    else {
                        val minutes = java.time.Duration.between(java.time.LocalDateTime.now(), next.at).toMinutes().coerceAtLeast(0)
                        val left = if (minutes >= 60) app.getString(R.string.duration_h_m, (minutes / 60).toInt(), (minutes % 60).toInt())
                        else app.getString(R.string.duration_m, minutes.toInt())
                        app.getString(R.string.prayer_next, app.getString(next.prayer.label), left) + "   (" +
                            next.at.format(DateTimeFormatter.ofPattern("HH:mm")) + ")"
                    }
                }
            }.getOrNull()
            post {
                if (attached && line != null) {
                    prayer.text = line
                    prayer.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun showEvents(all: List<CalendarEvent>) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        month.setEventDays(all.map { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }.toSet())
        val list = all.take(MAX_EVENTS)
        if (list.isEmpty()) {
            upcoming.visibility = View.GONE
            events.visibility = View.GONE
            return
        }
        val dayFormat = DateTimeFormatter.ofPattern("d MMM")
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
        val lines = list.map { event ->
            val moment = Instant.ofEpochMilli(event.startMs).atZone(zone)
            val day = when (moment.toLocalDate()) {
                today -> context.getString(R.string.screensaver_today)
                today.plusDays(1) -> context.getString(R.string.screensaver_tomorrow)
                else -> moment.format(dayFormat)
            }
            val whenText = if (event.allDay) day else day + "  " + moment.format(timeFormat)
            whenText + "     " + event.title
        }
        events.text = lines.joinToString("\n")
        upcoming.visibility = View.VISIBLE
        events.visibility = View.VISIBLE
    }
}

/** A month grid with today circled and a dot under every day that has an event. Works without any calendar link. */
private class MonthView(context: Context) : View(context) {
    private val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG)
    private val card = RectF()
    private var eventDays: Set<LocalDate> = emptySet()

    fun setEventDays(days: Set<LocalDate>) {
        eventDays = days
        invalidate()
    }

    private fun sp(value: Float) = value * resources.displayMetrics.scaledDensity

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = 20f * resources.displayMetrics.density
        card.set(0f, 0f, w, h)
        paint.style = AndroidPaint.Style.FILL
        paint.color = 0x66000000
        canvas.drawRoundRect(card, radius, radius, paint)
        paint.style = AndroidPaint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = 0x33FFFFFF
        canvas.drawRoundRect(card, radius, radius, paint)
        paint.style = AndroidPaint.Style.FILL

        val locale = Locale.getDefault()
        val today = LocalDate.now()
        val ym = YearMonth.from(today)
        val pad = w * 0.06f
        val cellW = (w - pad * 2) / 7f

        paint.textAlign = Align.LEFT
        paint.color = Color.WHITE
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = sp(20f)
        val title = ym.month.getDisplayName(TextStyle.FULL, locale) + " " + ym.year
        canvas.drawText(title, pad, h * 0.13f, paint)

        paint.textAlign = Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = sp(12f)
        paint.color = 0x99FFFFFF.toInt()
        for (i in 0 until 7) {
            val label = DayOfWeek.MONDAY.plus(i.toLong()).getDisplayName(TextStyle.SHORT, locale)
            canvas.drawText(label, pad + cellW * (i + 0.5f), h * 0.23f, paint)
        }

        val firstColumn = ym.atDay(1).dayOfWeek.value - 1
        val rowH = (h * 0.74f) / 6f
        paint.textSize = sp(15f)
        for (day in 1..ym.lengthOfMonth()) {
            val index = firstColumn + day - 1
            val cx = pad + cellW * (index % 7 + 0.5f)
            val cy = h * 0.28f + rowH * (index / 7) + rowH * 0.5f
            val date = ym.atDay(day)
            if (date == today) {
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy - sp(5f), sp(15f), paint)
                paint.color = 0xFF111111.toInt()
            } else {
                paint.color = if (date.isBefore(today)) 0x88FFFFFF.toInt() else Color.WHITE
            }
            canvas.drawText(day.toString(), cx, cy, paint)
            if (date in eventDays) {
                paint.color = 0xFFFFB067.toInt()
                canvas.drawCircle(cx, cy + sp(10f), sp(2.5f), paint)
            }
        }
    }
}

private class Backdrop(context: Context, private val look: Look) : View(context) {
    private val paints = Array(3) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private var phase = 0f

    fun setPhase(value: Float) {
        phase = value
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val radius = max(w, h) * 0.6f
        for (i in 0 until 3) {
            val color = look.blobs[i]
            paints[i].shader = RadialGradient(0f, 0f, radius, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
            paints[i].alpha = 190
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(look.background)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = max(w, h) * 0.6f
        val t = phase * TWO_PI
        for (i in 0 until 3) {
            val cx = w * (0.5f + 0.38f * sin(t * (i + 1) + i * 2.1f))
            val cy = h * (0.5f + 0.32f * cos(t * (i + 2) + i * 1.3f))
            canvas.save()
            canvas.translate(cx, cy)
            canvas.drawCircle(0f, 0f, radius, paints[i])
            canvas.restore()
        }
    }
}
