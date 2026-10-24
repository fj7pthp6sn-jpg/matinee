@file:OptIn(ExperimentalTextApi::class)

package com.tcltv.sahne.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.tcltv.sahne.R
import com.tcltv.sahne.data.WeatherCondition

/** The colour theme: a base colour for every screen and, for most themes, a fixed glow colour for the background. */
object ThemeStore {
    class Option(val base: Color, val accent: Color?)

    val OPTIONS = listOf(
        Option(Color(0xFF0B0B0E), null),                      // dark (the original)
        Option(Color(0xFF2563B0), Color(0xFF8CC4FF)),         // light blue
        Option(Color(0xFF0A1630), Color(0xFF2F6FD6)),         // night blue
        Option(Color(0xFF07343D), Color(0xFF2FB5A8)),         // ocean
        Option(Color(0xFF2A1233), Color(0xFFE2623A)),         // sunset
        Option(Color(0xFF0C2415), Color(0xFF4CAF6A)),         // forest
        Option(Color(0xFF1D1240), Color(0xFF8E5CF0)),         // purple
        Option(Color(0xFF3A1016), Color(0xFFE0455A)),         // burgundy
    )

    /** The last extra choice: the background follows the current weather and time of day. */
    val AUTO_INDEX = OPTIONS.size

    var index by mutableIntStateOf(0)
        private set

    var weatherCondition by mutableStateOf(WeatherCondition.CLEAR)
        private set
    var weatherIsDay by mutableStateOf(true)
        private set

    val isAuto: Boolean get() = index == AUTO_INDEX

    val base: Color
        get() = if (isAuto) weatherBase(weatherCondition, weatherIsDay) else OPTIONS[index.coerceIn(0, OPTIONS.size - 1)].base
    val accent: Color?
        get() = if (isAuto) weatherAccent(weatherCondition, weatherIsDay) else OPTIONS[index.coerceIn(0, OPTIONS.size - 1)].accent

    private fun weatherBase(c: WeatherCondition, day: Boolean): Color = when (c) {
        WeatherCondition.CLEAR -> if (day) Color(0xFF1B6FD1) else Color(0xFF050B1F)
        WeatherCondition.CLOUDY -> if (day) Color(0xFF4A6580) else Color(0xFF161E2B)
        WeatherCondition.RAIN -> if (day) Color(0xFF2C3E50) else Color(0xFF0F1722)
        WeatherCondition.SNOW -> if (day) Color(0xFF5F7A94) else Color(0xFF1B2636)
        WeatherCondition.STORM -> if (day) Color(0xFF2A2338) else Color(0xFF120F1C)
        WeatherCondition.FOG -> if (day) Color(0xFF5B6670) else Color(0xFF1A1E24)
    }

    private fun weatherAccent(c: WeatherCondition, day: Boolean): Color = when (c) {
        WeatherCondition.CLEAR -> if (day) Color(0xFFFFD27A) else Color(0xFF2F4A9A)
        WeatherCondition.CLOUDY -> Color(0xFFB8C7D9)
        WeatherCondition.RAIN -> Color(0xFF6C8CB0)
        WeatherCondition.SNOW -> Color(0xFFE6F1FB)
        WeatherCondition.STORM -> Color(0xFF8E5CF0)
        WeatherCondition.FOG -> Color(0xFFCBD3DA)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("color_theme", Context.MODE_PRIVATE)

    fun load(context: Context) {
        val p = prefs(context)
        index = p.getInt("index", 0).coerceIn(0, AUTO_INDEX)
        weatherCondition = WeatherCondition.entries.getOrElse(p.getInt("w_cond", 0)) { WeatherCondition.CLEAR }
        weatherIsDay = p.getBoolean("w_day", true)
    }

    fun set(context: Context, value: Int) {
        index = value.coerceIn(0, AUTO_INDEX)
        prefs(context).edit().putInt("index", index).apply()
    }

    /** Called whenever fresh weather arrives; remembered so the first frame after a restart is already right. */
    fun setWeather(context: Context, condition: WeatherCondition, isDay: Boolean) {
        weatherCondition = condition
        weatherIsDay = isDay
        prefs(context).edit().putInt("w_cond", condition.ordinal).putBoolean("w_day", isDay).apply()
    }
}

object Palette {
    val Background: Color get() = ThemeStore.base
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFFFFFFF).copy(alpha = 0.72f)
    val TextMuted = Color(0xFFFFFFFF).copy(alpha = 0.5f)
    val Amber = Color(0xFFF2B134)
    val OnAmber = Color(0xFF1E1606)
    val Warning = Color(0xFFFF5A4E)
    val Online = Color(0xFF3DDC84)
    val ToggleOn = Color(0xFF34C759)
}

private fun inter(weight: Int) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(
    inter(400),
    inter(500),
    inter(600),
    inter(700),
    inter(800),
)

fun interStyle(size: TextUnit, weight: Int, color: Color = Palette.TextPrimary, letterSpacing: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = Inter, fontSize = size, fontWeight = FontWeight(weight), color = color, letterSpacing = letterSpacing)

val LocalReduceMotion = staticCompositionLocalOf { false }
