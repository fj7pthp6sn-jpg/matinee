package com.tcltv.sahne.screensaver

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class CalendarEvent(val title: String, val startMs: Long, val allDay: Boolean)

/**
 * A small iCalendar (.ics) reader for the screensaver. It handles plain events and simple repeats
 * (daily, weekly, monthly, yearly); anything fancier, such as "every second Tuesday", is shown once at its first date.
 */
object CalendarFeed {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val REFRESH_MS = 60L * 60L * 1000L
    private const val HORIZON_DAYS = 14L
    private const val MAX_STEPS = 4000
    private const val RECENT_GRACE_MS = 30L * 60L * 1000L

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private class Prop(val params: Map<String, String>, val value: String)

    private class Rule(val freq: String, val interval: Long, val until: ZonedDateTime?)

    private fun cacheFile(context: Context) = File(context.applicationContext.cacheDir, "screensaver_calendar.ics")

    fun clearCache(context: Context) {
        runCatching { cacheFile(context).delete() }
    }

    /** Blocking (network and disk): call it from a worker thread. */
    fun upcoming(context: Context, limit: Int): List<CalendarEvent> {
        val url = ScreensaverPrefs.calendarUrl(context)
        if (url.isBlank()) return emptyList()
        val text = textFor(context, url) ?: return emptyList()
        val now = System.currentTimeMillis()
        return runCatching { parse(text, now) }
            .getOrDefault(emptyList())
            .filter { it.allDay || it.startMs >= now - RECENT_GRACE_MS }
            .take(limit)
    }

    private fun textFor(context: Context, url: String): String? {
        val file = cacheFile(context)
        val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < REFRESH_MS
        if (!fresh) {
            val downloaded = runCatching { download(url) }.getOrNull()
            if (downloaded != null) runCatching { file.writeText(downloaded) }
        }
        return if (file.isFile) runCatching { file.readText() }.getOrNull() else null
    }

    private fun download(rawUrl: String): String? {
        val address = rawUrl.trim().replace(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")
        if (!address.startsWith("https://", ignoreCase = true) && !address.startsWith("http://", ignoreCase = true)) return null
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "text/calendar, text/plain;q=0.9, */*;q=0.5")
            if (connection.responseCode !in 200..299) return null
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    out.write(buffer, 0, count)
                    if (out.size() > MAX_BYTES) return null
                }
            }
            return out.toString("UTF-8")
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(text: String, nowMs: Long): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        val events = mutableListOf<CalendarEvent>()
        var current: MutableMap<String, Prop>? = null
        for (line in unfold(text)) {
            if (line.equals("BEGIN:VEVENT", ignoreCase = true)) {
                current = mutableMapOf()
                continue
            }
            if (line.equals("END:VEVENT", ignoreCase = true)) {
                current?.let { events.addAll(expand(it, nowMs, zone)) }
                current = null
                continue
            }
            val target = current ?: continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val head = line.substring(0, colon).split(';')
            val name = head[0].uppercase()
            val params = HashMap<String, String>()
            for (part in head.drop(1)) {
                val eq = part.indexOf('=')
                if (eq > 0) params[part.substring(0, eq).uppercase()] = part.substring(eq + 1).trim('"')
            }
            if (!target.containsKey(name)) target[name] = Prop(params, line.substring(colon + 1))
        }
        return events.sortedBy { it.startMs }
    }

    private fun expand(props: Map<String, Prop>, nowMs: Long, zone: ZoneId): List<CalendarEvent> {
        if (props["STATUS"]?.value.equals("CANCELLED", ignoreCase = true)) return emptyList()
        val title = unescape(props["SUMMARY"]?.value.orEmpty())
        if (title.isBlank()) return emptyList()
        val startProp = props["DTSTART"] ?: return emptyList()
        val allDay = startProp.params["VALUE"].equals("DATE", ignoreCase = true) || startProp.value.trim().length == 8
        val start = parseTime(startProp, zone) ?: return emptyList()
        val rule = props["RRULE"]?.let { parseRule(it.value, zone) }

        val windowStart = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone)
        val windowEnd = windowStart.plusDays(HORIZON_DAYS)

        if (rule == null) {
            val inside = !start.isBefore(windowStart) && start.isBefore(windowEnd)
            return if (inside) listOf(CalendarEvent(title, start.toInstant().toEpochMilli(), allDay)) else emptyList()
        }

        val result = mutableListOf<CalendarEvent>()
        var n = 0L
        if (rule.freq == "DAILY" || rule.freq == "WEEKLY") {
            // A repeat that began long ago would otherwise need thousands of steps to reach today.
            val unitDays = if (rule.freq == "DAILY") 1L else 7L
            val gap = ChronoUnit.DAYS.between(start, windowStart)
            if (gap > 0) n = gap / (unitDays * rule.interval)
        }
        var steps = 0
        while (steps < MAX_STEPS) {
            steps++
            val occurrence = when (rule.freq) {
                "DAILY" -> start.plusDays(n * rule.interval)
                "WEEKLY" -> start.plusWeeks(n * rule.interval)
                "MONTHLY" -> start.plusMonths(n * rule.interval)
                else -> start.plusYears(n * rule.interval)
            }
            if (!occurrence.isBefore(windowEnd)) break
            val until = rule.until
            if (until != null && occurrence.isAfter(until)) break
            if (!occurrence.isBefore(windowStart)) {
                result.add(CalendarEvent(title, occurrence.toInstant().toEpochMilli(), allDay))
            }
            n++
        }
        return result
    }

    private fun parseRule(raw: String, zone: ZoneId): Rule? {
        val map = HashMap<String, String>()
        for (part in raw.split(';')) {
            val eq = part.indexOf('=')
            if (eq > 0) map[part.substring(0, eq).uppercase()] = part.substring(eq + 1)
        }
        val freq = map["FREQ"]?.uppercase() ?: return null
        if (freq != "DAILY" && freq != "WEEKLY" && freq != "MONTHLY" && freq != "YEARLY") return null
        val interval = (map["INTERVAL"]?.toLongOrNull() ?: 1L).coerceAtLeast(1L)
        val until = map["UNTIL"]?.let { parseTime(Prop(emptyMap(), it), zone) }
        return Rule(freq, interval, until)
    }

    private fun parseTime(prop: Prop, zone: ZoneId): ZonedDateTime? {
        val value = prop.value.trim()
        return try {
            when {
                value.length == 8 -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(zone)
                value.endsWith("Z", ignoreCase = true) ->
                    LocalDateTime.parse(value.dropLast(1), STAMP).atZone(ZoneOffset.UTC).withZoneSameInstant(zone)
                else -> {
                    val tz = prop.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
                    LocalDateTime.parse(value, STAMP).atZone(tz).withZoneSameInstant(zone)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split("\r\n", "\n", "\r")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out[out.size - 1] + raw.substring(1)
            } else if (raw.isNotEmpty()) {
                out.add(raw)
            }
        }
        return out
    }

    private fun unescape(value: String): String =
        value.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()
}
