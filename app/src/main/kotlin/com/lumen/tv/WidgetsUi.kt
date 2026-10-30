package com.lumen.tv

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Größe der Widget-Kacheln = Größe der normalen Kacheln (wird von der Reihe gesetzt). */
internal val LocalWDims = androidx.compose.runtime.compositionLocalOf { 192.dp to 108.dp }

internal fun dlgNoDim(view: android.view.View) {
    (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
}

// ============================================================== Kachel-Rahmen

/** Glas-Kachel für Info-Widgets: Farbverlauf, Kopfzeile mit Symbol/Titel, Inhalt. */
@Composable
internal fun InfoCard(title: String, emoji: String, tint: Color, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit, content: @Composable () -> Unit) {
    val (w, h) = LocalWDims.current
    FocusCard(w, h, tint, modifier, onFocus, onClick, bg = 1f) {
        Column(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(tint.copy(alpha = 0.45f), Color(0xCC111526), Color(0xE60B0E1A)))).padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (h >= 96.dp) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(emoji, fontSize = 9.sp)
                Text(title.uppercase(Lang.locale), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            content()
        }
    }
}

/** Kachel ist hoch genug für eine Zusatzzeile. */
@Composable internal fun roomy() = LocalWDims.current.second >= 130.dp

/** Eine Kachel der Reihe „Infos“. */
@Composable
fun InfoWidgetCell(kind: String, vm: HomeViewModel, modifier: Modifier, onHero: (HeroInfo) -> Unit, dims: Pair<Dp, Dp>) {
    androidx.compose.runtime.CompositionLocalProvider(LocalWDims provides dims) { InfoWidgetInner(kind, vm, modifier, onHero) }
}

@Composable
private fun InfoWidgetInner(kind: String, vm: HomeViewModel, modifier: Modifier, onHero: (HeroInfo) -> Unit) {
    val prefs by vm.prefs.collectAsState()
    var open by remember { mutableStateOf(false) }
    val hero = { title: String, extra: String -> onHero(HeroInfo("w:$kind", title, extra)) }
    when (kind) {
        "football" -> FootballCard(prefs.widgets, modifier, { hero(t("Fußball", "Football"), t("OK für Spielplan & Tabelle", "OK for fixtures & table")) }) { open = true }
        "weather" -> WeatherBigCard(prefs, modifier, { hero(t("Wetter", "Weather"), t("OK für Regenradar & Stundenwerte", "OK for rain radar & hourly")) }) { open = true }
        "countdown" -> CountdownCard(prefs.widgets, modifier, { hero("Countdown", t("OK zum Bearbeiten", "OK to edit")) }) { open = true }
        "note" -> NoteCard(prefs.widgets, modifier, { hero(t("Notiz", "Note"), t("OK zum Bearbeiten – auch am Handy", "OK to edit – also on your phone")) }) { open = true }
        "net" -> NetCard(prefs.widgets.routerIp, modifier, { hero(t("Heimnetz", "Home network"), t("OK für Speedtest & Gerätesuche", "OK for speed test & device scan")) }) { open = true }
        "tv" -> TvCard(prefs.widgets, modifier, { hero(t("Jetzt im TV", "On TV now"), t("OK für das Programm aller Sender", "OK for the full guide")) }) { open = true }
        "sun" -> SunMoonCard(prefs, modifier, { hero(t("Sonne & Mond", "Sun & moon"), t("OK für Details", "OK for details")) }) { open = true }
    }
    if (open) when (kind) {
        "football" -> FootballDialog(prefs.widgets) { open = false }
        "weather" -> WeatherBigDialog(prefs) { open = false }
        "countdown", "note" -> WidgetSettingsDialog(vm, if (kind == "note") "note" else "countdown") { open = false }
        "net" -> NetDialog(vm) { open = false }
        "tv" -> TvGuideDialog(prefs.widgets) { open = false }
        "sun" -> SunMoonDialog(prefs) { open = false }
    }
}

// ============================================================== Fußball

@Composable
internal fun TeamIcon(url: String, size: Dp, name: String = "") {
    var failed by remember(url) { mutableStateOf(url.isEmpty()) }
    if (failed) {
        // Ersatz: Kürzel des Vereins im farbigen Kreis
        val short = name.split(' ', '-', '.').filter { it.length > 1 && it.lowercase() !in setOf("fc", "sv", "vfl", "vfb", "tsg", "sc", "fsv", "1.") }.firstOrNull()?.take(3)?.uppercase() ?: name.take(3).uppercase()
        Box(Modifier.size(size).background(Color(0xFF3B4C9B), CircleShape), contentAlignment = Alignment.Center) {
            Text(short, fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        }
    } else AsyncImage(
        // Wikimedia & Co. liefern ohne ordentlichen User-Agent oft nichts aus
        ImageRequest.Builder(LocalContext.current).data(url.replace("http://", "https://")).addHeader("User-Agent", WallSources.userAgent()).size(128, 128).build(), null,
        Modifier.size(size), contentScale = ContentScale.Fit, onError = { failed = true },
    )
}

private val dayFmt get() = DateTimeFormatter.ofPattern(t("EE dd.MM. · HH:mm", "EE MM/dd · HH:mm"), Lang.locale)

@Composable
private fun FootballCard(cfg: WidgetCfg, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val data by produceState<FbData?>(null, cfg.league, cfg.teamId, cfg.teamName) {
        while (true) { value = Football.load(cfg) ?: value; delay(if (value?.live != null) 60_000L else 10 * 60_000L) }
    }
    InfoCard(cfg.teamName.ifEmpty { t("Fußball", "Football") }, "⚽", Color(0xFF7B4A2E), modifier, onFocus, onClick) {
        val d = data
        when {
            d == null -> Text(t("Lade Spiele …", "Loading matches …"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
            d.live != null -> MatchLine(d.live, live = true)
            d.next != null && d.next.time.toLocalDate() == LocalDate.now() -> MatchLine(d.next, live = false)
            d.last != null && (d.next == null || d.last.time.isAfter(java.time.LocalDateTime.now().minusDays(2))) -> MatchLine(d.last, live = false)
            d.next != null -> MatchLine(d.next, live = false)
            else -> Text(t("Keine Spiele – Verein wählen", "No matches – pick a team"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
        }
        val myTeam = d?.team
        val row = if (d == null || myTeam == null) null else d.table.firstOrNull { r -> r.team.id == myTeam.id || r.team.name == myTeam.name }
        if (row != null && roomy()) Text(t("Platz ${row.pos} · ${row.points} Punkte", "Rank ${row.pos} · ${row.points} pts"), fontSize = 9.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

@Composable
private fun MatchLine(m: FbMatch, live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        TeamIcon(m.home.icon, 20.dp, m.home.name)
        Text(if (m.g1 != null) "${m.g1}:${m.g2}" else "–:–", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        TeamIcon(m.away.icon, 20.dp, m.away.name)
        if (live) Box(Modifier.background(Color(0xFFFF3B4E), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp)) {
            Text("LIVE", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
    Text("${m.home.short} – ${m.away.short}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(
        when { live && m.lastGoal.isNotEmpty() -> "⚽ ${m.lastGoal}"; m.finished -> t("Endstand", "Full time"); else -> m.time.format(dayFmt) },
        fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FootballDialog(cfg: WidgetCfg, onClose: () -> Unit) {
    val data by produceState<FbData?>(null) { value = Football.load(cfg, force = true) }
    val first = remember { FocusRequester() }
    LaunchedEffect(data) { delay(150); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        dlgNoDim(androidx.compose.ui.platform.LocalView.current)
        NeoSheet(Ico.GRID, cfg.teamName, LEAGUES.firstOrNull { it.first == cfg.league }?.second.orEmpty() + " · " + "${Football.season()}/${(Football.season() + 1) % 100}", width = 1060.dp) {
            val d = data
            if (d == null) Text(t("Lade …", "Loading …"), color = Color.White)
            else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SheetSection(t("Nächste Spiele", "Upcoming")) }
                    itemsIndexed(d.upcoming) { i, m -> MatchRow(m, if (i == 0) Modifier.focusRequester(first) else Modifier) }
                    item { SheetSection(t("Letzte Spiele", "Recent")) }
                    items(d.recent) { m -> MatchRow(m) }
                    item { Pill(t("Schließen", "Close"), Modifier.padding(top = 8.dp)) { onClose() } }
                }
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item { SheetSection(t("Tabelle", "Table")) }
                    items(d.table) { r ->
                        val me = r.team.id == d.team?.id
                        FocusRow(highlight = me) { Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("${r.pos}.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.width(28.dp))
                            TeamIcon(r.team.icon, 20.dp, r.team.name)
                            Text(r.team.short, fontSize = 13.sp, fontWeight = if (me) FontWeight.Bold else FontWeight.Normal, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("${r.played}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(28.dp))
                            Text((if (r.diff > 0) "+" else "") + r.diff, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(36.dp))
                            Text("${r.points}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(30.dp))
                        } }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchRow(m: FbMatch, modifier: Modifier = Modifier) = FocusRow(modifier) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TeamIcon(m.home.icon, 24.dp, m.home.name)
        Column(Modifier.weight(1f)) {
            Text("${m.home.short} – ${m.away.short}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m.matchday + " · " + m.time.format(dayFmt), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
        TeamIcon(m.away.icon, 24.dp, m.away.name)
        Text(if (m.g1 != null) "${m.g1}:${m.g2}" else "–:–", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (m.live) Color(0xFFFF6B6B) else Color.White)
    }
}

// ============================================================== Wetter groß

@Composable
private fun WeatherBigCard(prefs: Prefs, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val hours by produceState<List<HourWx>>(emptyList(), prefs.weatherLat, prefs.weatherLon) {
        while (true) { if (prefs.weatherLat != 0.0 || prefs.weatherLon != 0.0) value = HourlyWeather.load(prefs.weatherLat, prefs.weatherLon); delay(20 * 60_000L) }
    }
    InfoCard(prefs.weatherName.ifEmpty { t("Wetter", "Weather") }, "🌦", Color(0xFF2F7BFF), modifier, onFocus, onClick) {
        if (hours.isEmpty()) Text(t("Kein Ort / lädt …", "No place / loading …"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
        else HourBars(hours.take(if (roomy()) 10 else 8), Modifier.fillMaxWidth(), compact = true)
    }
}

/** Temperatur je Stunde mit Regenbalken darunter. */
@Composable
private fun HourBars(h: List<HourWx>, modifier: Modifier, compact: Boolean = false) {
    val tH = if (compact) 6f else 10f; val tR = if (compact) 22f else 30f; val rH = if (compact) 2f else 3f; val rR = if (compact) 7f else 14f
    val fs = if (compact) 8.sp else 10.sp; val bw = if (compact) 7.dp else 10.dp
    val maxT = h.maxOf { it.temp }; val minT = h.minOf { it.temp }
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        h.forEachIndexed { i, x ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (i % 2 == 0) Text("${x.temp}°", fontSize = fs, color = Color.White, maxLines = 1) else Text(" ", fontSize = fs)
                val f = if (maxT == minT) 0.5f else (x.temp - minT).toFloat() / (maxT - minT)
                Box(Modifier.width(bw).height((tH + tR * f).dp).background(Brush.verticalGradient(listOf(Color(0xFFFFB648), Color(0xFFFF6B6B))), RoundedCornerShape(4.dp)))
                Box(Modifier.width(bw).height((rH + rR * x.rain / 100f).dp).background(Color(0xFF66C7FF).copy(alpha = 0.3f + 0.7f * x.rain / 100f), RoundedCornerShape(3.dp)))
                if (i % 2 == 0) Text("%02d".format(x.hour), fontSize = fs, color = Color.White.copy(alpha = 0.6f)) else Text(" ", fontSize = fs)
            }
        }
    }
}

@Composable
private fun WeatherBigDialog(prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val hours by produceState<List<HourWx>>(emptyList()) { value = HourlyWeather.load(prefs.weatherLat, prefs.weatherLon) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        dlgNoDim(androidx.compose.ui.platform.LocalView.current)
        NeoSheet(Ico.WIFI, t("Wetter", "Weather") + (if (prefs.weatherName.isNotEmpty()) " · " + prefs.weatherName else ""), t("Regenradar Deutschland & nächste 24 Stunden", "Rain radar Germany & next 24 hours"), width = 1100.dp) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.weight(1.1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        ImageRequest.Builder(ctx).data(HourlyWeather.RADAR_URL + "?t=" + System.currentTimeMillis() / 300_000).addHeader("User-Agent", WallSources.userAgent()).build(),
                        t("Regenradar", "Rain radar"), Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit,
                    )
                    Text("© Deutscher Wetterdienst", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetSection(t("Nächste 24 Stunden", "Next 24 hours"))
                    if (hours.isNotEmpty()) HourBars(hours.take(12), Modifier.fillMaxWidth().height(110.dp))
                    if (hours.size > 12) HourBars(hours.drop(12).take(12), Modifier.fillMaxWidth().height(110.dp))
                    val rainy = hours.take(12).firstOrNull { it.rain >= 50 }
                    Text(
                        if (hours.isEmpty()) t("Kein Ort eingestellt.", "No place set.")
                        else if (rainy != null) t("☔ Regen wahrscheinlich ab ca. %02d Uhr (${rainy.rain} %%)".format(rainy.hour), "☔ Rain likely from about %02d:00 (${rainy.rain} %%)".format(rainy.hour))
                        else t("☀ In den nächsten 12 Stunden bleibt es wohl trocken.", "☀ Probably dry for the next 12 hours."),
                        fontSize = 15.sp, color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                    Pill(t("Schließen", "Close"), Modifier.focusRequester(first)) { onClose() }
                }
            }
        }
    }
}

// ============================================================== Countdown & Notiz

@Composable
private fun CountdownCard(cfg: WidgetCfg, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val list = cfg.countdowns.mapNotNull { c -> c.daysLeft()?.takeIf { it >= 0 }?.let { c to it } }.sortedBy { it.second }
    InfoCard("Countdown", "⏳", Color(0xFFB57BFF), modifier, onFocus, onClick) {
        val top = list.firstOrNull()
        if (top == null) Text(t("OK zum Anlegen", "OK to add"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
        else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (top.second == 0L) t("Heute!", "Today!") else "${top.second}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                if (top.second > 0) Text(if (top.second == 1L) t("Tag", "day") else t("Tage", "days"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(bottom = 5.dp))
            }
            Text(top.first.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (roomy()) list.drop(1).take(1).forEach { (c, d) -> Text("${c.name} · " + t("noch $d Tage", "$d days"), fontSize = 9.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1) }
        }
    }
}

@Composable
private fun NoteCard(cfg: WidgetCfg, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    InfoCard(t("Notiz", "Note"), "📝", Color(0xFFFFB648), modifier, onFocus, onClick) {
        Text(
            cfg.noteText.ifEmpty { t("Leer – OK zum Schreiben", "Empty – OK to write") },
            fontSize = 11.sp, color = Color.White, maxLines = if (roomy()) 6 else 4, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ============================================================== Heimnetz

@Composable
private fun NetCard(routerIp: String, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val info by produceState<NetInfo?>(null, routerIp) { while (true) { value = HomeNet.info(ctx, routerIp); delay(60_000) } }
    InfoCard(t("Heimnetz", "Home network"), "📶", Color(0xFF1FB89A), modifier, onFocus, onClick) {
        val n = info
        if (n == null) Text(t("Messe …", "Measuring …"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
        else {
            Text("${n.type} · ${n.ip.ifEmpty { "–" }}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            Text("Router " + (n.routerMs?.let { "$it ms" } ?: "–") + " · Web " + (n.internetMs?.let { "$it ms" } ?: "–"), fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            Text(
                (HomeNet.lastResult?.let { r -> "⬇ %.0f · ⬆ %s Mbit/s".format(r.down, r.up?.let { "%.0f".format(it) } ?: "–") } ?: t("OK für Speedtest", "OK for speed test")) +
                    (if (HomeNet.lastDevices.isNotEmpty()) " · ${HomeNet.lastDevices.size} " + t("Geräte", "devices") else ""),
                fontSize = 9.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NetDialog(vm: HomeViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<NetInfo?>(null) }
    var result by remember { mutableStateOf(HomeNet.lastResult) }
    var devices by remember { mutableStateOf(HomeNet.lastDeviceList) }
    var busy by remember { mutableStateOf("") }
    var livePhase by remember { mutableStateOf("") }
    var liveVal by remember { mutableStateOf(0.0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { info = HomeNet.info(ctx); delay(100); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        dlgNoDim(androidx.compose.ui.platform.LocalView.current)
        NeoSheet(Ico.WIFI, t("Heimnetz", "Home network"), t("Verbindung, Speedtest & Geräte im Netz", "Connection, speed test & devices"), width = 960.dp) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    val n = info
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NetStat(t("Verbindung", "Connection"), n?.type ?: "…", n?.ip.orEmpty(), Modifier.weight(1f))
                        NetStat("Router", n?.routerMs?.let { "$it ms" } ?: "–", n?.gateway.orEmpty(), Modifier.weight(1f))
                        NetStat("Internet", n?.internetMs?.let { "$it ms" } ?: "–", t("Antwortzeit", "latency"), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("⚡ " + t("Speedtest starten", "Run speed test"), Modifier.focusRequester(first), selected = true) {
                            if (busy.isEmpty()) {
                                busy = "speed"
                                scope.launch {
                                    result = HomeNet.speedtest { ph, v -> scope.launch { livePhase = ph; liveVal = v } } ?: result
                                    busy = ""; livePhase = ""
                                }
                            }
                        }
                        Pill("🔍 " + t("Geräte suchen", "Find devices")) {
                            if (busy.isEmpty()) { busy = "scan"; scope.launch { devices = HomeNet.scanDevices(ctx, info?.ip.orEmpty()); busy = "" } }
                        }
                        Pill(t("Schließen", "Close")) { onClose() }
                    }
                }
                // ---------- Speedtest
                if (busy == "speed" || result != null) item {
                    val r = result
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF1FB89A).copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))))
                            .border(1.dp, NeoLine, RoundedCornerShape(22.dp)).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (busy == "speed") {
                            Text(
                                when (livePhase) { "ping" -> t("Messe Antwortzeit …", "Measuring latency …"); "down" -> "⬇ Download …"; "up" -> "⬆ Upload …"; else -> t("Starte …", "Starting …") },
                                fontSize = 15.sp, color = NeoCyan,
                            )
                            if (livePhase == "down" || livePhase == "up") Text("%.0f Mbit/s".format(liveVal), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        if (r != null && busy != "speed") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                NetStat("⬇ Download", "%.0f".format(r.down), "Mbit/s", Modifier.weight(1f))
                                NetStat("⬆ Upload", r.up?.let { "%.0f".format(it) } ?: "–", "Mbit/s", Modifier.weight(1f))
                                NetStat("Ping", r.ping?.let { "%.0f".format(it) } ?: "–", "ms", Modifier.weight(1f))
                                NetStat("Jitter", r.jitter?.let { "%.0f".format(it) } ?: "–", "ms", Modifier.weight(1f))
                            }
                            Text(r.verdict, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text(t("Verbraucht: ca. ${r.mb} MB · Server: Cloudflare (nächster Standort)", "Data used: about ${r.mb} MB · Server: Cloudflare (nearest location)"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            if (info?.type?.contains("VPN") == true) Text(t("Hinweis: Mit aktivem VPN misst der Test die VPN-Verbindung, nicht deinen Anschluss.", "Note: with VPN active, the test measures the VPN connection, not your line."), fontSize = 12.sp, color = Color(0xFFFFD36B))
                        }
                    }
                }
                // ---------- Geräte
                if (busy == "scan") item { Text(t("Suche Geräte im Netz (ca. 20–30 s) …", "Scanning the network (about 20–30 s) …"), fontSize = 15.sp, color = NeoCyan) }
                if (devices.isNotEmpty()) {
                    item { SheetSection(t("Gefundene Geräte (${devices.size})", "Devices found (${devices.size})")) }
                    items(devices) { dev ->
                        FocusRow {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (dev.ip == info?.gateway) "📡" else if (dev.ip == info?.ip) "📺" else "💻", fontSize = 16.sp)
                                Spacer(Modifier.width(12.dp))
                                if (dev.shown.isNotEmpty()) Column(Modifier.weight(1f)) {
                                    Text(dev.shown, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(dev.ip, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                } else Text(dev.ip, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
                                Text(if (dev.ip == info?.gateway) "Router" else "", fontSize = 13.sp, color = NeoCyan)
                            }
                        }
                    }
                }
                item {
                    Text(
                        t("Die Gerätesuche klopft typische Ports im eigenen Netz an und findet nicht jedes Gerät (z. B. Handys im Stromsparmodus). Der Speedtest misst Antwortzeit, Download (bis 10 s) und Upload (bis 8 s) über Cloudflare.",
                            "The device scan knocks on common ports and won't find every device (e.g. sleeping phones). The speed test measures latency, download (up to 10 s) and upload (up to 8 s) via Cloudflare."),
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NetStat(title: String, value: String, sub: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).border(1.dp, NeoLine, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(sub, fontSize = 11.sp, color = NeoCyan, maxLines = 1)
    }
}

// ============================================================== News-Ticker

/** Schmale Laufzeile mit Schlagzeilen. Aktualisiert sich alle 15 Minuten. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewsTicker(cfg: WidgetCfg, modifier: Modifier = Modifier) {
    if (!cfg.ticker || cfg.tickerSrc.isEmpty()) return
    val news by produceState<List<Headline>>(emptyList(), cfg.tickerSrc) { while (true) { value = News.load(cfg.tickerSrc); delay(15 * 60_000L) } }
    if (news.isEmpty()) return
    val text = news.joinToString("     ✦     ") { "${it.source.uppercase(Lang.locale)}  ${it.title}" }
    Row(
        modifier.padding(horizontal = SIDE_PAD).fillMaxWidth().height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(Color.Black.copy(alpha = 0.45f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(17.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.fillMaxHeight().background(NeoCyan.copy(alpha = 0.85f)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(t("NEWS", "NEWS"), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = Color(0xFF071318))
        }
        Text(
            text, fontSize = 14.sp, color = Color.White, maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).basicMarquee(iterations = Int.MAX_VALUE, velocity = 45.dp),
        )
    }
}

// ============================================================== Einstellungen der Widgets

@Composable
fun WidgetSettingsDialog(vm: HomeViewModel, focus: String = "", onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefs.collectAsState()
    val w = prefs.widgets
    fun upd(f: (WidgetCfg) -> WidgetCfg) = vm.update { it.copy(widgets = f(it.widgets)) }
    var editing by remember { mutableStateOf(if (focus == "note") "note" else "") }   // "note", "cdName", "cdDate"
    var text by remember { mutableStateOf(if (focus == "note") w.noteText else "") }
    var cdName by remember { mutableStateOf("") }
    var cdYearly by remember { mutableStateOf(false) }
    var teamPick by remember { mutableStateOf<List<FbTeam>?>(null) }
    var tvExpand by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf<WidgetWebServer?>(null) }
    val first = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()
    LaunchedEffect(Unit) { delay(120); runCatching { (if (editing.isNotEmpty()) field else first).requestFocus() } }
    LaunchedEffect(editing) { if (editing.isNotEmpty()) { delay(80); runCatching { field.requestFocus() } } }
    DisposableEffect(Unit) { onDispose { server?.stop() } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        dlgNoDim(androidx.compose.ui.platform.LocalView.current)
        NeoSheet(Ico.LIST, t("Info-Widgets", "Info widgets"), t("Erscheinen in der Reihe „Infos“ – verschiebbar unter Reihen", "Shown in the “Info” row – movable under Rows"), width = 860.dp) {
            if (editing.isNotEmpty()) {
                Text(
                    when (editing) { "note" -> t("Notiz (OK = speichern)", "Note (OK = save)"); "cdName" -> t("Name des Countdowns", "Countdown name"); "epg" -> t("EPG-URL(s), mit Leerzeichen getrennt (leer = Standard)", "EPG URL(s), space-separated (empty = default)"); else -> t("Datum, z. B. 24.12.2026", "Date, e.g. 2026-12-24") },
                    fontSize = 14.sp, color = NeoCyan,
                )
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = editing != "note",
                    textStyle = TextStyle(color = Color.White, fontSize = 19.sp), cursorBrush = SolidColor(NeoCyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        when (editing) {
                            "note" -> { upd { it.copy(noteText = text.trim(), note = true) }; editing = "" }
                            "epg" -> { upd { it.copy(epgUrl = text.trim()) }; editing = "" }
                            "cdName" -> { cdName = text.trim(); text = ""; editing = if (cdName.isNotEmpty()) "cdDate" else "" }
                            "cdDate" -> {
                                val iso = parseDateInput(text)
                                if (iso == null) toast(t("Datum nicht erkannt – bitte TT.MM.JJJJ", "Date not recognized – use YYYY-MM-DD"))
                                else { upd { it.copy(countdowns = it.countdowns + Countdown(cdName, iso, cdYearly), countdown = true) }; editing = "" }
                            }
                        }
                    }),
                    modifier = Modifier.fillMaxWidth().background(NeoGlass, RoundedCornerShape(16.dp)).border(2.dp, NeoYellow, RoundedCornerShape(16.dp)).padding(16.dp).focusRequester(field),
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { SheetSection("⚽ " + t("Fußball", "Football")) }
                item { SettingLine(t("Fußball-Widget", "Football widget"), if (w.football) t("An", "On") else t("Aus", "Off"), Modifier.focusRequester(first)) { upd { it.copy(football = !it.football) } } }
                item { SettingLine(t("Liga", "League"), LEAGUES.firstOrNull { it.first == w.league }?.second ?: w.league) {
                    val i = LEAGUES.indexOfFirst { it.first == w.league }
                    upd { it.copy(league = LEAGUES[(i + 1) % LEAGUES.size].first, teamId = 0) }
                } }
                item { SettingLine(t("Tor-Alarm (Banner, auch in anderen Apps)", "Goal alert (banner, also in other apps)"), if (w.goalAlert) t("An", "On") else t("Aus", "Off")) { upd { it.copy(goalAlert = !it.goalAlert) } } }
                item { SettingLine(t("Verein", "Team"), w.teamName) { scope.launch { teamPick = Football.teams(w.league).ifEmpty { toast(t("Vereine konnten nicht geladen werden.", "Could not load teams.")); null } } } }

                item { SheetSection("📰 " + t("News-Ticker", "News ticker")) }
                item { SettingLine(t("Laufzeile mit Schlagzeilen", "Headline ticker"), if (w.ticker) t("An", "On") else t("Aus", "Off")) { upd { it.copy(ticker = !it.ticker) } } }
                item { SettingLine(t("Position", "Position"), if (w.tickerPos == 0) t("Unten", "Bottom") else t("Oben", "Top")) { upd { it.copy(tickerPos = 1 - it.tickerPos) } } }
                items(NEWS_SOURCES) { (id, name, _) ->
                    SettingLine(name, if (id in w.tickerSrc) t("An", "On") else t("Aus", "Off")) { upd { c -> c.copy(tickerSrc = if (id in c.tickerSrc) c.tickerSrc - id else c.tickerSrc + id) } }
                }

                item { SheetSection("🌦 " + t("Wetter groß", "Weather large")) }
                item { SettingLine(t("Wetter-Kachel mit Stundenwerten & Radar", "Weather tile with hourly & radar"), if (w.weatherBig) t("An", "On") else t("Aus", "Off")) { upd { it.copy(weatherBig = !it.weatherBig) } } }

                item { SheetSection("⏳ Countdown") }
                item { SettingLine(t("Countdown-Widget", "Countdown widget"), if (w.countdown) t("An", "On") else t("Aus", "Off")) { upd { it.copy(countdown = !it.countdown) } } }
                item { SettingLine(t("Jährlich wiederholen (für neue Einträge)", "Repeat yearly (new entries)"), if (cdYearly) t("An", "On") else t("Aus", "Off")) { cdYearly = !cdYearly } }
                item { SettingLine("+ " + t("Neuer Countdown", "New countdown"), "") { text = ""; editing = "cdName" } }
                items(w.countdowns) { c ->
                    val d = c.daysLeft()
                    SettingLine("🗑 ${c.name}", (d?.let { t("noch $it Tage", "$it days") } ?: c.date) + if (c.yearly) " ↻" else "") {
                        upd { it.copy(countdowns = it.countdowns - c) }
                        toast(t("Gelöscht: ", "Deleted: ") + c.name)
                    }
                }

                item { SheetSection("📝 " + t("Notiz", "Note")) }
                item { SettingLine(t("Notiz-Widget", "Note widget"), if (w.note) t("An", "On") else t("Aus", "Off")) { upd { it.copy(note = !it.note) } } }
                item { SettingLine(t("Notiz bearbeiten", "Edit note"), w.noteText.take(24)) { text = w.noteText; editing = "note" } }

                item { SheetSection("📺 " + t("TV-Programm", "TV guide")) }
                item { SettingLine(t("„Jetzt im TV“-Widget", "“On TV now” widget"), if (w.tv) t("An", "On") else t("Aus", "Off")) { upd { it.copy(tv = !it.tv) } } }
                item { SettingLine(t("Sender auswählen", "Choose channels") + if (tvExpand) " ▾" else " ▸", "${w.tvChannels.size}") { tvExpand = !tvExpand } }
                if (tvExpand) items(TV_CHANNELS) { (name, _) ->
                    SettingLine("   $name", if (name in w.tvChannels) t("An", "On") else t("Aus", "Off")) {
                        upd { c -> c.copy(tvChannels = if (name in c.tvChannels) c.tvChannels - name else TV_CHANNELS.map { it.first }.filter { it in c.tvChannels || it == name }) }
                    }
                }
                item { SettingLine(t("EPG-Quelle (für Sky/Pay-TV eigene URL)", "EPG source (own URL for Sky/pay TV)"), w.epgUrl.ifEmpty { t("Standard (epgshare01)", "default (epgshare01)") }.take(28)) { text = w.epgUrl; editing = "epg" } }

                item { SheetSection("🌅 " + t("Sonne & Mond", "Sun & moon")) }
                item { SettingLine(t("Sonnenauf-/-untergang & Mondphase", "Sunrise/sunset & moon phase"), if (w.sun) t("An", "On") else t("Aus", "Off")) { upd { it.copy(sun = !it.sun) } } }

                item { SheetSection("📶 " + t("Heimnetz", "Home network")) }
                item { SettingLine(t("Heimnetz-Widget", "Home network widget"), if (w.net) t("An", "On") else t("Aus", "Off")) { upd { it.copy(net = !it.net) } } }

                item { SheetSection("📱 " + t("Am Handy oder PC bearbeiten", "Edit on phone or PC")) }
                item {
                    SettingLine(t("Notiz & Countdowns im Browser bearbeiten", "Edit note & countdowns in a browser"), if (server != null) t("läuft", "running") else t("starten", "start")) {
                        if (server == null) {
                            val srv = WidgetWebServer({ vm.prefs.value.widgets }) { f -> upd(f) }
                            if (srv.start()) server = srv else toast(t("Konnte den Server nicht starten.", "Could not start the server."))
                        } else { server?.stop(); server = null }
                    }
                }
                server?.let { s ->
                    item {
                        Text(t("Öffne im Browser (gleiches WLAN): ", "Open in a browser (same Wi-Fi): ") + s.url() + "   ·   Code: " + s.code,
                            fontSize = 17.sp, color = NeoCyan, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                    }
                }
                item { Pill(t("Fertig", "Done"), Modifier.padding(start = 12.dp, top = 10.dp)) { onClose() } }
            }
        }
        teamPick?.let { teams ->
            MenuDialog(MenuSpec(t("Verein wählen", "Choose team"), teams.map { tm -> MenuItemSpec(tm.name) { upd { it.copy(teamName = tm.name, teamId = tm.id) }; teamPick = null } })) { teamPick = null }
        }
    }
}

// ============================================================== Bearbeiten per Browser

/** Kleiner Webserver (nur solange die Widget-Einstellungen offen sind, mit Code): Notiz und Countdowns am Handy bearbeiten. */
class WidgetWebServer(private val current: () -> WidgetCfg, private val onChange: ((WidgetCfg) -> WidgetCfg) -> Unit) {
    val code = (1000..9999).random().toString()
    private var sock: java.net.ServerSocket? = null
    private val port = 8766

    fun url(): String {
        val ip = runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull() ?: "IP-der-Box"
        return "http://$ip:$port"
    }

    fun start(): Boolean = runCatching {
        val s = java.net.ServerSocket(port).also { sock = it }
        Thread {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                runCatching { handle(c) }
                runCatching { c.close() }
            }
        }.apply { isDaemon = true }.start()
        true
    }.getOrDefault(false)

    fun stop() { runCatching { sock?.close() }; sock = null }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun handle(c: java.net.Socket) {
        c.soTimeout = 10_000
        val inp = c.getInputStream().buffered()
        fun line(): String { val sb = StringBuilder(); while (true) { val b = inp.read(); if (b < 0 || b == '\n'.code) break; if (b != '\r'.code) sb.append(b.toChar()) }; return sb.toString() }
        val req = line()
        var len = 0
        while (true) { val h = line(); if (h.isEmpty()) break; if (h.lowercase().startsWith("content-length:")) len = h.substringAfter(':').trim().toIntOrNull() ?: 0 }
        var msg = ""
        if (req.startsWith("POST")) {
            val buf = ByteArray(len.coerceIn(0, 50_000)); var read = 0
            while (read < buf.size) { val n = inp.read(buf, read, buf.size - read); if (n < 0) break; read += n }
            val form = String(buf, 0, read, Charsets.UTF_8).split('&').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { java.net.URLDecoder.decode(it[0], "UTF-8") to java.net.URLDecoder.decode(it[1], "UTF-8") } }
            val map = form.toMap()
            msg = if (map["code"]?.trim() != code) "Falscher Code." else {
                val note = map["note"]
                val name = map["cdname"].orEmpty().trim(); val date = map["cddate"].orEmpty().trim(); val yearly = map["cdyearly"] != null
                val del = form.filter { it.first == "del" }.map { it.second }.toSet()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onChange { cfg ->
                        var n = cfg
                        if (note != null) n = n.copy(noteText = note.trim(), note = true)
                        if (del.isNotEmpty()) n = n.copy(countdowns = n.countdowns.filterIndexed { i, _ -> i.toString() !in del })
                        if (name.isNotEmpty()) parseDateInput(date)?.let { iso -> n = n.copy(countdowns = n.countdowns + Countdown(name, iso, yearly), countdown = true) }
                        n
                    }
                }
                Thread.sleep(300)
                "Gespeichert."
            }
        }
        val cfg = current()
        val cds = cfg.countdowns.mapIndexed { i, cd -> "<label><input type=\"checkbox\" name=\"del\" value=\"$i\"> löschen: ${esc(cd.name)} (${cd.date}${if (cd.yearly) ", jährlich" else ""})</label><br>" }.joinToString("")
        val html = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Lumen Widgets</title>
<style>body{font-family:system-ui,sans-serif;background:#111318;color:#eee;max-width:600px;margin:30px auto;padding:0 16px}input,textarea{width:100%;padding:12px;margin:6px 0 14px;border-radius:10px;border:1px solid #444;background:#1c1f27;color:#fff;font-size:16px;box-sizing:border-box}textarea{min-height:120px}input[type=checkbox]{width:auto}button{padding:12px 22px;border:0;border-radius:22px;background:#4fe3f0;color:#000;font-size:16px;font-weight:600}.m{color:#7ce0a3}h3{margin-top:26px}</style></head>
<body><h2>Lumen Launcher – Widgets</h2><p class="m">${esc(msg)}</p><form method="post">
<label>Code vom Fernseher</label><input name="code" inputmode="numeric" autocomplete="off">
<h3>📝 Notiz</h3><textarea name="note">${esc(cfg.noteText)}</textarea>
<h3>⏳ Neuer Countdown</h3><label>Name</label><input name="cdname" placeholder="z. B. Urlaub"><label>Datum</label><input name="cddate" type="date"><label><input type="checkbox" name="cdyearly"> jährlich wiederholen (Geburtstag)</label>
<h3>Vorhandene Countdowns</h3>${cds.ifEmpty { "<p>keine</p>" }}
<p><button>Speichern</button></p></form></body></html>"""
        val bytes = html.toByteArray(Charsets.UTF_8)
        c.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() }
    }
}
