package com.tcltv.sahne.info

import android.content.Context
import androidx.annotation.StringRes
import com.tcltv.sahne.R
import com.tcltv.sahne.data.WeatherLocation
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Prayer(val jsonKey: String, @StringRes val label: Int) {
    FAJR("Fajr", R.string.prayer_fajr),
    SUNRISE("Sunrise", R.string.prayer_sunrise),
    DHUHR("Dhuhr", R.string.prayer_dhuhr),
    ASR("Asr", R.string.prayer_asr),
    MAGHRIB("Maghrib", R.string.prayer_maghrib),
    ISHA("Isha", R.string.prayer_isha),
}

data class NextPrayer(val prayer: Prayer, val at: LocalDateTime)

data class MatchInfo(
    val startMs: Long,
    val home: String,
    val away: String,
    val homeScore: String?,
    val awayScore: String?,
    val completed: Boolean,
)

data class TeamMatches(val last: MatchInfo?, val next: MatchInfo?)

data class TableRow(val rank: Int, val team: String, val teamId: String, val played: Int, val points: Int)

data class FootballData(
    val galatasaray: TeamMatches?,
    val erzurumspor: TeamMatches?,
    val table: List<TableRow>?,
    val logos: Map<String, String> = emptyMap(),
) {
    fun logoUrl(teamId: String): String = logos[teamId] ?: "https://a.espncdn.com/i/teamlogos/soccer/500/$teamId.png"
}

/**
 * Network side of the prayer-times strip and the match card. All calls block, so callers run them off the main thread.
 * Prayer times come from Aladhan (Diyanet method), matches and the table from ESPN's public Süper Lig data. The last good
 * answer is kept, so a failed refresh still shows something.
 */
object InfoClient {
    const val GALATASARAY_ID = "432"
    const val ERZURUM_ID = "19267"
    private const val ESPN = "https://site.api.espn.com/apis"
    private const val FOOTBALL_TTL_MS = 15L * 60L * 1000L
    private const val CACHE = "info_cache"

    private fun cache(context: Context) = context.applicationContext.getSharedPreferences(CACHE, Context.MODE_PRIVATE)

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "Matinee-Launcher")
            if (connection.responseCode != 200) throw java.io.IOException("HTTP " + connection.responseCode)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Fresh copy when the saved one is older than [ttlMs]; the saved one when the network fails. */
    private fun cachedGet(context: Context, key: String, url: String, ttlMs: Long): String? {
        val prefs = cache(context)
        val saved = prefs.getString(key, null)
        val savedAt = prefs.getLong(key + "_at", 0L)
        if (saved != null && System.currentTimeMillis() - savedAt < ttlMs) return saved
        return try {
            val body = httpGet(url)
            prefs.edit().putString(key, body).putLong(key + "_at", System.currentTimeMillis()).apply()
            body
        } catch (e: Exception) {
            saved
        }
    }

    // ---- prayer times ----

    private fun prayerDay(context: Context, location: WeatherLocation, date: LocalDate): Map<Prayer, LocalTime>? {
        val day = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val url = String.format(
            Locale.US,
            "https://api.aladhan.com/v1/timings/%s?latitude=%.4f&longitude=%.4f&method=13",
            day, location.latitude, location.longitude,
        )
        val key = String.format(Locale.US, "prayer_%s_%.2f_%.2f", date, location.latitude, location.longitude)
        // A day's times never change, so a saved answer is good for the whole day.
        val body = cachedGet(context, key, url, 24L * 60L * 60L * 1000L) ?: return null
        val timings = runCatching { JSONObject(body).getJSONObject("data").getJSONObject("timings") }.getOrNull() ?: return null
        val out = LinkedHashMap<Prayer, LocalTime>()
        for (prayer in Prayer.entries) {
            val text = timings.optString(prayer.jsonKey, "")
            if (text.length >= 5) out[prayer] = runCatching { LocalTime.parse(text.substring(0, 5)) }.getOrNull() ?: continue
        }
        return out.takeIf { it.size == Prayer.entries.size }
    }

    fun prayersToday(context: Context, location: WeatherLocation): Map<Prayer, LocalTime>? =
        prayerDay(context, location, LocalDate.now())

    fun nextPrayer(context: Context, location: WeatherLocation, now: LocalDateTime = LocalDateTime.now()): NextPrayer? {
        val today = prayerDay(context, location, now.toLocalDate())
        today?.forEach { (prayer, time) ->
            val at = now.toLocalDate().atTime(time)
            if (at.isAfter(now)) return NextPrayer(prayer, at)
        }
        val tomorrow = prayerDay(context, location, now.toLocalDate().plusDays(1)) ?: return null
        val fajr = tomorrow[Prayer.FAJR] ?: return null
        return NextPrayer(Prayer.FAJR, now.toLocalDate().plusDays(1).atTime(fajr))
    }

    // ---- football ----

    /** ESPN spells Turkish names without accents. */
    fun displayName(raw: String, id: String? = null): String = when {
        id == ERZURUM_ID || raw == "Erzurum BB" -> "Erzurumspor"
        raw == "Besiktas" -> "Beşiktaş"
        raw == "Fenerbahce" -> "Fenerbahçe"
        raw == "Goztepe" -> "Göztepe"
        raw == "Genclerbirligi" -> "Gençlerbirliği"
        raw == "Kasimpasa" -> "Kasımpaşa"
        raw == "Istanbul Basaksehir" -> "Başakşehir"
        raw == "Caykur Rizespor" -> "Çaykur Rizespor"
        raw == "Eyupspor" -> "Eyüpspor"
        raw == "Amed SFK" -> "Amedspor"
        else -> raw
    }

    private fun score(competitor: JSONObject): String? {
        val raw = competitor.opt("score")
        return when (raw) {
            is JSONObject -> raw.optString("displayValue").takeIf { it.isNotEmpty() }
            is String -> raw.takeIf { it.isNotEmpty() }
            is Number -> raw.toInt().toString()
            else -> null
        }
    }

    private fun parseEvents(body: String): List<MatchInfo> {
        val events: JSONArray = runCatching { JSONObject(body).getJSONArray("events") }.getOrNull() ?: return emptyList()
        val out = ArrayList<MatchInfo>()
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val competition = event.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val competitors = competition.optJSONArray("competitors") ?: continue
            var home: String? = null
            var away: String? = null
            var homeScore: String? = null
            var awayScore: String? = null
            for (j in 0 until competitors.length()) {
                val c = competitors.optJSONObject(j) ?: continue
                val team = c.optJSONObject("team") ?: continue
                val name = displayName(team.optString("displayName"), team.optString("id"))
                if (c.optString("homeAway") == "home") {
                    home = name
                    homeScore = score(c)
                } else {
                    away = name
                    awayScore = score(c)
                }
            }
            val start = runCatching { OffsetDateTime.parse(event.optString("date")).toInstant().toEpochMilli() }.getOrNull() ?: continue
            val statusType = competition.optJSONObject("status")?.optJSONObject("type")
            val completed = (statusType?.optBoolean("completed", false) ?: false) || statusType?.optString("state") == "post"
            if (home != null && away != null) out.add(MatchInfo(start, home, away, homeScore, awayScore, completed))
        }
        return out
    }

    private fun teamMatches(context: Context, teamId: String): TeamMatches? {
        val played = cachedGet(context, "fb_played_$teamId", "$ESPN/site/v2/sports/soccer/tur.1/teams/$teamId/schedule", FOOTBALL_TTL_MS)
        val fixtures = cachedGet(context, "fb_fixtures_$teamId", "$ESPN/site/v2/sports/soccer/tur.1/teams/$teamId/schedule?fixture=true", FOOTBALL_TTL_MS)
        if (played == null && fixtures == null) return null
        val now = System.currentTimeMillis()
        val last = played?.let(::parseEvents)?.filter { it.completed || it.startMs < now - 2L * 60L * 60L * 1000L }?.maxByOrNull { it.startMs }
        // The fixture list can still call finished games "scheduled", so the date decides what is still to come.
        val next = fixtures?.let(::parseEvents)?.filter { !it.completed && it.startMs > now - 2L * 60L * 60L * 1000L }?.minByOrNull { it.startMs }
        return TeamMatches(last, next)
    }

    private fun table(context: Context, logos: MutableMap<String, String>): List<TableRow>? {
        val body = cachedGet(context, "fb_table", "$ESPN/v2/sports/soccer/tur.1/standings", FOOTBALL_TTL_MS) ?: return null
        val entries = runCatching { JSONObject(body).getJSONArray("children").getJSONObject(0).getJSONObject("standings").getJSONArray("entries") }
            .getOrNull() ?: return null
        val rows = ArrayList<TableRow>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val team = entry.optJSONObject("team") ?: continue
            val stats = entry.optJSONArray("stats") ?: continue
            val values = HashMap<String, Double>()
            for (j in 0 until stats.length()) {
                val stat = stats.optJSONObject(j) ?: continue
                values[stat.optString("name")] = stat.optDouble("value", 0.0)
            }
            val id = team.optString("id")
            val logo = team.optString("logo").ifEmpty { team.optJSONArray("logos")?.optJSONObject(0)?.optString("href").orEmpty() }
            if (logo.isNotEmpty()) logos[id] = logo.replace("http://", "https://")
            rows.add(
                TableRow(
                    rank = (values["rank"] ?: (i + 1).toDouble()).toInt(),
                    team = displayName(team.optString("displayName"), id),
                    teamId = id,
                    played = (values["gamesPlayed"] ?: 0.0).toInt(),
                    points = (values["points"] ?: 0.0).toInt(),
                ),
            )
        }
        return rows.sortedBy { it.rank }.takeIf { it.isNotEmpty() }
    }

    fun football(context: Context): FootballData? {
        val logos = HashMap<String, String>()
        val data = FootballData(
            galatasaray = runCatching { teamMatches(context, GALATASARAY_ID) }.getOrNull(),
            erzurumspor = runCatching { teamMatches(context, ERZURUM_ID) }.getOrNull(),
            table = runCatching { table(context, logos) }.getOrNull(),
            logos = logos,
        )
        return data.takeIf { it.galatasaray != null || it.erzurumspor != null || it.table != null }
    }
}
