package com.tcltv.sahne.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.tcltv.sahne.info.LogoCache
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tcltv.sahne.R
import com.tcltv.sahne.data.WeatherLocation
import com.tcltv.sahne.info.FootballData
import com.tcltv.sahne.info.InfoClient
import com.tcltv.sahne.info.MatchInfo
import com.tcltv.sahne.info.Prayer
import com.tcltv.sahne.info.TeamMatches
import com.tcltv.sahne.ui.Palette
import com.tcltv.sahne.ui.interStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardShape = RoundedCornerShape(14.dp)

/** A glass card the remote can land on, so the page scrolls down to it like it does for the app rows. */
@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(CardShape)
            .background(Color.White.copy(alpha = if (focused) 0.12f else 0.07f))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.1f), CardShape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        content = content,
    )
}

/** Today's six prayer times with the next one highlighted and the time left to it. */
@Composable
internal fun PrayerStrip(location: WeatherLocation, resumeTick: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val times by produceState<Map<Prayer, LocalTime>?>(null, location, resumeTick) {
        while (true) {
            val fresh = withContext(Dispatchers.IO) { runCatching { InfoClient.prayersToday(context, location) }.getOrNull() }
            if (fresh != null) value = fresh
            delay(30L * 60L * 1000L)
        }
    }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000L)
            value = LocalDateTime.now()
        }
    }
    InfoCard(modifier.fillMaxWidth()) {
        val today = times
        if (today == null) {
            BasicText(stringResource(R.string.info_loading_prayer), style = interStyle(12.sp, 500, Palette.TextMuted))
            return@InfoCard
        }
        val upcoming = today.entries.firstOrNull { now.toLocalDate().atTime(it.value).isAfter(now) }
        val nextPrayer = upcoming?.key ?: Prayer.FAJR
        val nextAt = upcoming?.let { now.toLocalDate().atTime(it.value) }
            ?: now.toLocalDate().plusDays(1).atTime(today.getValue(Prayer.FAJR))
        val minutes = Duration.between(now, nextAt).toMinutes().coerceAtLeast(0)
        val left = if (minutes >= 60) stringResource(R.string.duration_h_m, (minutes / 60).toInt(), (minutes % 60).toInt())
        else stringResource(R.string.duration_m, minutes.toInt())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(150.dp)) {
                BasicText(location.name.substringBefore(','), style = interStyle(11.sp, 600, Palette.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(stringResource(nextPrayer.label) + " · " + left, style = interStyle(12.sp, 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((prayer, time) in today) {
                    val isNext = prayer == nextPrayer
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isNext) Color.White else Color.White.copy(alpha = 0.06f))
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(stringResource(prayer.label), style = interStyle(10.sp, 600, if (isNext) Palette.Background else Palette.TextMuted))
                            BasicText(
                                time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                style = interStyle(14.sp, 700, if (isNext) Palette.Background else Palette.TextPrimary),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDay(ms: Long, pattern: String): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

private fun result(match: MatchInfo): String =
    match.home + "  " + (match.homeScore ?: "-") + " – " + (match.awayScore ?: "-") + "  " + match.away

/** Last result and next match of Galatasaray and Erzurumspor, plus a short Süper Lig table. */
@Composable
internal fun MatchesCard(resumeTick: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val data by produceState<FootballData?>(null, resumeTick) {
        while (true) {
            val fresh = withContext(Dispatchers.IO) { runCatching { InfoClient.football(context) }.getOrNull() }
            if (fresh != null) value = fresh
            delay(15L * 60L * 1000L)
        }
    }
    val football = data
    if (football == null) {
        InfoCard(modifier.fillMaxWidth()) {
            BasicText(stringResource(R.string.info_loading_matches), style = interStyle(12.sp, 500, Palette.TextMuted))
        }
        return
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TeamCard("Galatasaray", football.logoUrl(InfoClient.GALATASARAY_ID), football.galatasaray, Modifier.weight(1f))
        TeamCard("Erzurumspor", football.logoUrl(InfoClient.ERZURUM_ID), football.erzurumspor, Modifier.weight(1f))
        TableCard(football, Modifier.weight(1.1f))
    }
}

/** A club badge, loaded in the background; empty until it arrives or when it can't be loaded. */
@Composable
private fun TeamLogo(url: String, size: Dp, fallback: String = "") {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) { runCatching { LogoCache.load(context, url)?.asImageBitmap() }.getOrNull() }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else if (fallback.isNotEmpty()) {
            // Until the badge arrives, or when it can't, a plain circle with the club's first letter.
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                BasicText(fallback.take(1), style = interStyle((size.value * 0.55f).sp, 800))
            }
        }
    }
}

@Composable
private fun TeamCard(name: String, logo: String, matches: TeamMatches?, modifier: Modifier) {
    InfoCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamLogo(logo, 20.dp, name)
            Spacer(Modifier.width(8.dp))
            BasicText(name, style = interStyle(14.sp, 800), maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        MatchLine(stringResource(R.string.match_last), matches?.last, name, withScore = true)
        Spacer(Modifier.height(3.dp))
        MatchLine(stringResource(R.string.match_next), matches?.next, name, withScore = false)
    }
}

/** One short line from the team's point of view: its own score first, the opponent, and (H)ome or (A)way. */
@Composable
private fun MatchLine(label: String, match: MatchInfo?, team: String, withScore: Boolean) {
    val text = if (match == null) {
        stringResource(R.string.match_none)
    } else {
        val athome = match.home == team
        val opponent = (if (athome) match.away else match.home) + " " +
            stringResource(if (athome) R.string.match_home_tag else R.string.match_away_tag)
        if (withScore) {
            val ours = if (athome) match.homeScore else match.awayScore
            val theirs = if (athome) match.awayScore else match.homeScore
            (ours ?: "-") + "–" + (theirs ?: "-") + "  " + opponent
        } else {
            opponent + " · " + formatDay(match.startMs, "d MMM EEE HH:mm")
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = interStyle(10.sp, 700, Palette.TextMuted), modifier = Modifier.width(52.dp))
        BasicText(text, style = interStyle(11.sp, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TableCard(data: FootballData, modifier: Modifier) {
    InfoCard(modifier) {
        BasicText(stringResource(R.string.table_title), style = interStyle(14.sp, 800))
        Spacer(Modifier.height(2.dp))
        val table = data.table
        if (table == null) {
            BasicText(stringResource(R.string.match_none), style = interStyle(12.sp, 500, Palette.TextSecondary))
            return@InfoCard
        }
        // Four rows: the leaders plus our two teams, filled up from the top of the table.
        val ours = table.filter { it.teamId == InfoClient.GALATASARAY_ID || it.teamId == InfoClient.ERZURUM_ID }
        val shown = (table.take(2) + ours + table).distinctBy { it.teamId }.take(4).sortedBy { it.rank }
        shown.forEach { row ->
            val ours = row.teamId == InfoClient.GALATASARAY_ID || row.teamId == InfoClient.ERZURUM_ID
            val color = if (ours) Palette.Amber else Palette.TextPrimary
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(row.rank.toString(), style = interStyle(11.sp, 600, Palette.TextMuted), modifier = Modifier.width(22.dp))
                TeamLogo(data.logoUrl(row.teamId), 14.dp, row.team)
                Spacer(Modifier.width(6.dp))
                BasicText(row.team, style = interStyle(11.sp, if (ours) 800 else 600, color), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                BasicText(row.played.toString(), style = interStyle(11.sp, 500, Palette.TextSecondary).copy(textAlign = TextAlign.End), modifier = Modifier.width(24.dp))
                BasicText(row.points.toString(), style = interStyle(11.sp, 800, color).copy(textAlign = TextAlign.End), modifier = Modifier.width(28.dp))
            }
        }
    }
}
