package com.lumen.tv

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.zip.GZIPInputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private fun hhmm(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(hm)

// ============================================================== Sonne & Mond

@Immutable class SunDay(val date: LocalDate, val rise: LocalDateTime, val set: LocalDateTime)

object SunTimes {
    private var cache: Pair<String, Pair<Long, List<SunDay>>>? = null

    /** Sonnenauf- und -untergang für heute und die nächsten Tage (Open-Meteo, Ortszeit). Cache 6 Stunden. */
    suspend fun load(lat: Double, lon: Double): List<SunDay> = withContext(Dispatchers.IO) {
        val key = "$lat,$lon|${LocalDate.now()}"
        val c = cache
        if (c != null && c.first == key && System.currentTimeMillis() - c.second.first < 6 * 3_600_000L) return@withContext c.second.second
        val txt = Http.get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&daily=sunrise,sunset&forecast_days=3&timezone=auto")
            ?: return@withContext c?.second?.second ?: emptyList()
        val list = runCatching {
            val d = JSONObject(txt).getJSONObject("daily")
            val days = d.getJSONArray("time"); val r = d.getJSONArray("sunrise"); val s = d.getJSONArray("sunset")
            (0 until days.length()).map { i -> SunDay(LocalDate.parse(days.getString(i)), LocalDateTime.parse(r.getString(i)), LocalDateTime.parse(s.getString(i))) }
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) cache = key to (System.currentTimeMillis() to list)
        list
    }
}

object Moon {
    private const val SYN = 29.530588853
    private const val REF = 947_182_440_000L   // Neumond 06.01.2000 18:14 UTC

    fun age(ms: Long = System.currentTimeMillis()): Double { val d = (ms - REF) / 86_400_000.0; return ((d % SYN) + SYN) % SYN }
    fun illum(age: Double): Int = Math.round((1 - cos(2 * PI * age / SYN)) / 2 * 100).toInt()
    private fun idx(age: Double) = ((age / SYN * 8 + 0.5).toInt()) % 8
    fun emoji(age: Double) = listOf("🌑", "🌒", "🌓", "🌔", "🌕", "🌖", "🌗", "🌘")[idx(age)]
    fun name(age: Double) = listOf(
        t("Neumond", "New moon"), t("Zunehmende Sichel", "Waxing crescent"), t("Erstes Viertel", "First quarter"), t("Zunehmender Mond", "Waxing gibbous"),
        t("Vollmond", "Full moon"), t("Abnehmender Mond", "Waning gibbous"), t("Letztes Viertel", "Last quarter"), t("Abnehmende Sichel", "Waning crescent"),
    )[idx(age)]

    /** Nächster Neumond (target 0) bzw. Vollmond (target SYN/2). */
    fun next(full: Boolean): LocalDate {
        val now = System.currentTimeMillis()
        var dd = (if (full) SYN / 2 else 0.0) - age(now)
        if (dd <= 0.3) dd += SYN
        return Instant.ofEpochMilli(now + (dd * 86_400_000).toLong()).atZone(ZoneId.systemDefault()).toLocalDate()
    }
}

/** Sonnenbogen: gestrichelter Halbbogen mit Sonne an der aktuellen Position (nachts der Mond). */
@Composable
private fun SunArc(today: SunDay?, modifier: Modifier) {
    val now = LocalDateTime.now()
    val f = if (today == null) -1f else {
        val total = ChronoUnit.SECONDS.between(today.rise, today.set).toFloat()
        val done = ChronoUnit.SECONDS.between(today.rise, now).toFloat()
        if (total <= 0f) -1f else done / total
    }
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val base = h * 0.92f
        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, base), Offset(w, base), strokeWidth = 1.5f)
        drawArc(
            Color.White.copy(alpha = 0.35f), 180f, 180f, false, Offset(w * 0.08f, base - h * 0.84f), Size(w * 0.84f, h * 1.68f),
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
        )
        if (f in 0f..1f) {
            val ang = PI * (1 - f)
            val p = Offset(w / 2 + cos(ang).toFloat() * w * 0.42f, base - sin(ang).toFloat() * h * 0.84f)
            drawArc(Color(0xFFFFC94A).copy(alpha = 0.55f), 180f, 180f * f, false, Offset(w * 0.08f, base - h * 0.84f), Size(w * 0.84f, h * 1.68f), style = Stroke(width = 3f, cap = StrokeCap.Round))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD86B).copy(alpha = 0.6f), Color.Transparent), center = p, radius = h * 0.45f), radius = h * 0.45f, center = p)
            drawCircle(Color(0xFFFFD25A), h * 0.13f, p)
        } else {
            val p = Offset(w * 0.5f, base - h * 0.55f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFDDE6FF).copy(alpha = 0.4f), Color.Transparent), center = p, radius = h * 0.4f), radius = h * 0.4f, center = p)
            drawCircle(Color(0xFFE8EEFF), h * 0.13f, p)
            drawCircle(Color(0xFF151A2C), h * 0.11f, Offset(p.x + h * 0.06f, p.y - h * 0.03f))
        }
    }
}

@Composable
fun SunMoonCard(prefs: Prefs, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val days by produceState<List<SunDay>>(emptyList(), prefs.weatherLat, prefs.weatherLon) {
        while (true) { if (prefs.weatherLat != 0.0 || prefs.weatherLon != 0.0) value = SunTimes.load(prefs.weatherLat, prefs.weatherLon); delay(30 * 60_000L) }
    }
    val tick by produceState(0) { while (true) { delay(60_000); value++ } }
    InfoCard(t("Sonne & Mond", "Sun & moon"), "🌅", Color(0xFFFF9F43), modifier, onFocus, onClick) {
        val today = days.firstOrNull { it.date == LocalDate.now() }
        if (days.isEmpty()) Text(t("Ort im Wetter einstellen", "Set a place in weather"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
        else {
            androidx.compose.runtime.key(tick) { SunArc(today, Modifier.fillMaxWidth().height(if (roomy()) 44.dp else 30.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("↑ " + (today?.rise?.format(hm) ?: "–"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD25A), maxLines = 1)
                Text("↓ " + (today?.set?.format(hm) ?: "–"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9F68), maxLines = 1)
            }
            val a = Moon.age()
            Text("${Moon.emoji(a)} ${Moon.illum(a)} % · ${Moon.name(a)}", fontSize = 9.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SunMoonDialog(prefs: Prefs, onClose: () -> Unit) {
    val days by produceState<List<SunDay>>(emptyList()) { value = SunTimes.load(prefs.weatherLat, prefs.weatherLon) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
    val dateFmt = DateTimeFormatter.ofPattern(t("EEEE, d. MMMM", "EEEE, MMMM d"), Lang.locale)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        dlgNoDim(androidx.compose.ui.platform.LocalView.current)
        NeoSheet(Ico.GLOBE, t("Sonne & Mond", "Sun & moon"), prefs.weatherName.ifEmpty { t("Dein Ort", "Your place") }, width = 860.dp) {
            val today = days.firstOrNull { it.date == LocalDate.now() }
            val tomorrow = days.firstOrNull { it.date == LocalDate.now().plusDays(1) }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetSection(t("Sonne heute", "Sun today"))
                    SunArc(today, Modifier.fillMaxWidth().height(120.dp))
                    if (today != null) {
                        val len = ChronoUnit.MINUTES.between(today.rise, today.set)
                        DetailLine(t("Sonnenaufgang", "Sunrise"), today.rise.format(hm))
                        DetailLine(t("Sonnenuntergang", "Sunset"), today.set.format(hm))
                        DetailLine(t("Tageslänge", "Day length"), "${len / 60} h ${len % 60} min")
                        DetailLine(t("Goldene Stunde", "Golden hour"), today.set.minusHours(1).format(hm) + " – " + today.set.format(hm))
                        if (tomorrow != null) {
                            val diff = ChronoUnit.MINUTES.between(tomorrow.rise, tomorrow.set) - len
                            DetailLine(t("Morgen", "Tomorrow"), "↑ ${tomorrow.rise.format(hm)}  ↓ ${tomorrow.set.format(hm)}  (" + (if (diff >= 0) "+" else "") + "$diff min)")
                        }
                    } else Text(t("Keine Daten – Ort im Wetter einstellen.", "No data – set a place in weather."), color = Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetSection(t("Mond", "Moon"))
                    val a = Moon.age()
                    Text(Moon.emoji(a), fontSize = 64.sp)
                    Text(Moon.name(a), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    DetailLine(t("Beleuchtet", "Illuminated"), "${Moon.illum(a)} %")
                    DetailLine(t("Nächster Vollmond", "Next full moon"), Moon.next(true).format(dateFmt))
                    DetailLine(t("Nächster Neumond", "Next new moon"), Moon.next(false).format(dateFmt))
                }
            }
            Spacer(Modifier.height(6.dp))
            Pill(t("Schließen", "Close"), Modifier.focusRequester(first)) { onClose() }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

// ============================================================== Wetterwarnungen (DWD über BrightSky)

@Immutable class WxAlert(val severity: String, val event: String, val headline: String, val onset: String, val expires: String) {
    /** 3 = Extrem, 2 = Schwer, 1 = Mäßig, 0 = Gering. */
    val rank: Int get() = when (severity.lowercase()) { "extreme" -> 3; "severe" -> 2; "moderate" -> 1; else -> 0 }
    val color: Long get() = when (rank) { 3 -> 0xFFFF3B3B; 2 -> 0xFFFF6B2E; 1 -> 0xFFFFB648; else -> 0xFFFFE04A }
}

object WeatherAlerts {
    private var cache: Pair<String, Pair<Long, List<WxAlert>>>? = null

    /** Amtliche Warnungen für den Ort (DWD via BrightSky). Cache 20 Minuten. */
    suspend fun load(lat: Double, lon: Double): List<WxAlert> = withContext(Dispatchers.IO) {
        if (lat == 0.0 && lon == 0.0) return@withContext emptyList()
        val key = "$lat,$lon"
        val c = cache
        if (c != null && c.first == key && System.currentTimeMillis() - c.second.first < 20 * 60_000L) return@withContext c.second.second
        val tz = java.util.TimeZone.getDefault().id
        val txt = Http.get("https://api.brightsky.dev/alerts?lat=$lat&lon=$lon&tz=$tz") ?: return@withContext c?.second?.second ?: emptyList()
        val list = runCatching {
            val arr = JSONObject(txt).optJSONArray("alerts") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WxAlert(o.optString("severity"), o.optString("event_de").ifEmpty { o.optString("event_en") },
                    o.optString("headline_de").ifEmpty { o.optString("headline_en") }, o.optString("onset"), o.optString("expires"))
            }.sortedByDescending { it.rank }
        }.getOrDefault(emptyList())
        cache = key to (System.currentTimeMillis() to list)
        list
    }
}

/** Kompakte Warn-Zeile unter Uhr/Wetter; nichts, wenn keine Warnung aktiv ist. */
@Composable
fun WeatherAlertChip(prefs: Prefs, right: Boolean = false) {
    if (prefs.weather <= 0 || !prefs.weatherAlerts) return
    val alerts by produceState<List<WxAlert>>(emptyList(), prefs.weatherLat, prefs.weatherLon) {
        while (true) { value = WeatherAlerts.load(prefs.weatherLat, prefs.weatherLon); delay(20 * 60_000L) }
    }
    val a = alerts.firstOrNull() ?: return
    val blink by rememberInfiniteTransition(label = "warn").animateFloat(0.6f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "b")
    Row(
        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color(a.color).copy(alpha = 0.18f))
            .border(1.dp, Color(a.color).copy(alpha = blink), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("⚠", fontSize = 12.sp, color = Color(a.color))
        Text(a.event.ifEmpty { a.headline }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (alerts.size > 1) Text("+${alerts.size - 1}", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
    }
}

// ============================================================== TV-Programm (XMLTV, z. B. epgshare01)

@Immutable class EpgShow(val title: String, val start: Long, val stop: Long, val info: String)
@Immutable class EpgChannel(val name: String, val shows: List<EpgShow>) {
    fun now(t: Long) = shows.firstOrNull { it.start <= t && it.stop > t }
    fun next(t: Long) = shows.firstOrNull { it.start > t }
}

/** Sender mit Suchnamen (klein, ohne Sonderzeichen). norm() entfernt zusätzlich Endungen wie hd/de/sd. */
val TV_CHANNELS: List<Pair<String, List<String>>> = listOf(
    // Öffentlich-rechtlich
    "Das Erste" to listOf("daserste", "ard", "erste"), "ZDF" to listOf("zdf"), "3sat" to listOf("3sat"),
    "arte" to listOf("arte"), "ZDFneo" to listOf("zdfneo"), "ZDFinfo" to listOf("zdfinfo"), "ONE" to listOf("one", "ardone"),
    "ARD alpha" to listOf("ardalpha", "alpha", "bralpha"), "tagesschau24" to listOf("tagesschau24"), "phoenix" to listOf("phoenix"),
    "KiKA" to listOf("kika"), "funk" to listOf("funk"),
    // Dritte Programme
    "NDR" to listOf("ndr", "ndrfernsehen"), "WDR" to listOf("wdr", "wdrfernsehen"),
    "BR" to listOf("br", "brfernsehen", "bayerischesfernsehen"), "SWR" to listOf("swr", "swrfernsehen"),
    "SWR RP" to listOf("swrrp", "swrrheinlandpfalz"), "MDR" to listOf("mdr", "mdrfernsehen"), "hr" to listOf("hr", "hrfernsehen"),
    "rbb" to listOf("rbb", "rbbfernsehen"), "Radio Bremen TV" to listOf("radiobrementv", "rbtv"), "SR Fernsehen" to listOf("srfernsehen", "sr"),
    // Private Vollprogramme
    "RTL" to listOf("rtl", "rtltelevision"), "SAT.1" to listOf("sat1"), "ProSieben" to listOf("prosieben", "pro7"),
    "VOX" to listOf("vox"), "kabel eins" to listOf("kabeleins", "kabel1"), "RTLZWEI" to listOf("rtlzwei", "rtl2"),
    // Private Spartensender
    "sixx" to listOf("sixx"), "ProSieben MAXX" to listOf("prosiebenmaxx", "pro7maxx"), "SAT.1 Gold" to listOf("sat1gold"),
    "kabel eins Doku" to listOf("kabeleinsdoku", "kabel1doku"), "VOXup" to listOf("voxup"), "NITRO" to listOf("nitro", "rtlnitro"),
    "RTLup" to listOf("rtlup", "rtlplus"), "Super RTL" to listOf("superrtl"), "Toggo plus" to listOf("toggoplus"),
    "Comedy Central" to listOf("comedycentral"), "Tele 5" to listOf("tele5"), "DMAX" to listOf("dmax"),
    "TLC" to listOf("tlc"), "Nickelodeon" to listOf("nickelodeon", "nick"), "DISNEY CHANNEL" to listOf("disneychannel"),
    // Nachrichten
    "n-tv" to listOf("ntv"), "WELT" to listOf("welt", "n24welt"), "BILD" to listOf("bild", "bildtv"), "phoenix HD" to listOf("phoenixhd"),
    // Doku & Wissen
    "ServusTV" to listOf("servustv", "servus"), "ZDFneo HD" to listOf("zdfneohd"), "Welt der Wunder" to listOf("weltderwunder"),
    "N24 Doku" to listOf("n24doku"), "Spiegel Geschichte" to listOf("spiegelgeschichte"), "Crime + Investigation" to listOf("crimeinvestigation", "crimeandinvestigation"),
    "History" to listOf("historychannel", "history"), "National Geographic" to listOf("nationalgeographic", "natgeo"), "Discovery Channel" to listOf("discoverychannel", "discovery"),
    // Sport (frei)
    "Sport1" to listOf("sport1"), "Sport1+" to listOf("sport1plus"), "Eurosport 1" to listOf("eurosport1", "eurosport"),
    // Sky / Pay-TV
    "Sky One" to listOf("skyone"), "Sky Atlantic" to listOf("skyatlantic"), "Sky Replay" to listOf("skyreplay"),
    "Sky Krimi" to listOf("skykrimi"), "Sky Comedy" to listOf("skycomedy"), "Sky Nature" to listOf("skynature"), "Sky Documentaries" to listOf("skydocumentaries"),
    "Sky Cinema Premieren" to listOf("skycinemapremieren", "skycinemapremiere"), "Sky Cinema Best Of" to listOf("skycinemabestof"),
    "Sky Cinema Action" to listOf("skycinemaaction"), "Sky Cinema Family" to listOf("skycinemafamily"),
    "Sky Cinema Classics" to listOf("skycinemaclassics"), "Sky Cinema Thriller" to listOf("skycinemathriller"),
    "Sky Sport Bundesliga" to listOf("skysportbundesliga", "skysportbundesliga1"), "Sky Sport Top Event" to listOf("skysporttopevent"),
    "Sky Sport Mix" to listOf("skysportmix"), "Sky Sport News" to listOf("skysportnews", "skysportnewshd"),
    "Sky Sport 1" to listOf("skysport1"), "Sky Sport 2" to listOf("skysport2"), "Sky Sport F1" to listOf("skysportf1", "skysportformel1"),
    "Sky Sport Tennis" to listOf("skysporttennis"), "Sky Sport Golf" to listOf("skysportgolf"),
    "WOW / Sky Show" to listOf("skyshowcase", "wow"), "Sky Krimi HD" to listOf("skykrimihd"),
    "DAZN 1" to listOf("dazn1"), "DAZN 2" to listOf("dazn2"), "Eurosport 2" to listOf("eurosport2"),
    // Musik & Kinder (Pay/frei)
    "MTV" to listOf("mtv"), "Deluxe Music" to listOf("deluxemusic"), "DISNEY JUNIOR" to listOf("disneyjunior"),
    "Cartoon Network" to listOf("cartoonnetwork"), "Boomerang" to listOf("boomerang"), "JuniorTV" to listOf("junior"),
    // Film (frei)
    "ProSieben FUN" to listOf("prosiebenfun"), "RTL Crime" to listOf("rtlcrime"), "RTL Passion" to listOf("rtlpassion"),
    "Universal TV" to listOf("universaltv", "universalchannel"), "13th Street" to listOf("13thstreet", "13street"), "Syfy" to listOf("syfy"),
    "AXN" to listOf("axn"), "FIX&FOXI" to listOf("fixundfoxi", "fixfoxi"),
)
val TV_DEFAULT = listOf("Das Erste", "ZDF", "RTL", "SAT.1", "ProSieben", "VOX", "kabel eins", "RTLZWEI", "NITRO", "n-tv", "ARD alpha", "arte")
const val EPG_DEFAULT_URL = "https://epgshare01.online/epgshare01/epg_ripper_DE1.xml.gz"

object Epg {
    private val lock = Mutex()
    private var mem: Triple<String, Long, List<EpgChannel>>? = null   // Schlüssel, geladen um, Daten
    var lastError: String = ""
        private set

    private fun norm(s: String): String {
        var n = s.lowercase().replace("&", "und").replace("+", "plus").filter { it in 'a'..'z' || it in '0'..'9' }
        repeat(3) { for (suf in listOf("hd", "de", "deutschland", "germany", "sd")) if (n.length > suf.length + 1 && n.endsWith(suf)) n = n.removeSuffix(suf) }
        return n
    }

    /** Ordnet einen Namen aus der Programmdatei einem unserer Sender zu (genau, sonst Regionalvariante „ndrfernsehen…“). */
    private fun match(names: List<String>, wanted: List<Pair<String, List<String>>>): String? {
        val ns = names.map { norm(it) }
        wanted.firstOrNull { (_, al) -> ns.any { it in al } }?.let { return it.first }
        return wanted.firstOrNull { (_, al) -> ns.any { n -> al.any { a -> a.endsWith("fernsehen") && n.startsWith(a) } } }?.first
    }

    private val tsFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    private fun ts(s: String?): Long? = runCatching {
        val p = s!!.trim()
        val base = LocalDateTime.parse(p.take(14), tsFmt)
        val off = p.drop(14).trim()
        val zo = if (off.length >= 5) ZoneOffset.of(off.substring(0, 3) + ":" + off.substring(3, 5)) else ZoneOffset.UTC
        base.toInstant(zo).toEpochMilli()
    }.getOrNull()

    private fun file(ctx: Context) = File(ctx.cacheDir, "epg.json")

    private fun save(ctx: Context, key: String, at: Long, list: List<EpgChannel>) = runCatching {
        val o = JSONObject().put("key", key).put("at", at).put("ch", JSONArray().apply {
            list.forEach { c -> put(JSONObject().put("n", c.name).put("s", JSONArray().apply { c.shows.forEach { s -> put(JSONArray().put(s.title).put(s.start).put(s.stop).put(s.info)) } })) }
        })
        file(ctx).writeText(o.toString())
    }

    private fun restore(ctx: Context): Triple<String, Long, List<EpgChannel>>? = runCatching {
        val o = JSONObject(file(ctx).readText())
        val ch = o.getJSONArray("ch")
        Triple(o.getString("key"), o.getLong("at"), (0 until ch.length()).map { i ->
            val c = ch.getJSONObject(i); val s = c.getJSONArray("s")
            EpgChannel(c.getString("n"), (0 until s.length()).map { k -> s.getJSONArray(k).let { a -> EpgShow(a.getString(0), a.getLong(1), a.getLong(2), a.optString(3)) } })
        })
    }.getOrNull()

    /** Programm der gewählten Sender: aus dem Speicher, sonst aus der Datei, sonst neu laden (alle 6 Stunden). */
    suspend fun load(ctx: Context, url: String, channels: List<String>, force: Boolean = false): List<EpgChannel> = withContext(Dispatchers.IO) {
        lock.withLock {
            val src = url.ifBlank { EPG_DEFAULT_URL }
            val key = src + "|" + channels.joinToString(",")
            val now = System.currentTimeMillis()
            if (mem == null) mem = restore(ctx)
            val m = mem
            val fresh = m != null && m.first == key && now - m.second < 6 * 3_600_000L &&
                m.third.any { c -> c.shows.any { it.stop > now + 2 * 3_600_000L } }
            if (fresh && !force) return@withLock order(m!!.third, channels)
            // mehrere Quellen (Leerzeichen/Zeilenumbruch getrennt) zusammenführen – z. B. frei + Sky
            val sources = src.split(Regex("[\\s]+")).filter { it.isNotBlank() }
            val merged = LinkedHashMap<String, MutableList<EpgShow>>()
            channels.forEach { merged[it] = mutableListOf() }
            var any = false
            sources.forEach { u ->
                download(u, channels, now)?.forEach { ch ->
                    if (ch.shows.isNotEmpty()) { merged[ch.name]?.addAll(ch.shows); any = true }
                }
            }
            val loaded = channels.map { n -> EpgChannel(n, merged[n].orEmpty().sortedBy { it.start }.distinctBy { it.start }) }
            if (any) {
                mem = Triple(key, now, loaded); save(ctx, key, now, loaded); lastError = ""
                order(loaded, channels)
            } else order(m?.third ?: emptyList(), channels)
        }
    }

    private fun order(list: List<EpgChannel>, channels: List<String>) = channels.mapNotNull { n -> list.firstOrNull { it.name == n } }

    /** Lädt die XMLTV-Datei als Strom (gz wird erkannt) und behält nur die gewählten Sender der nächsten 12 Stunden. */
    private fun download(src: String, channels: List<String>, now: Long): List<EpgChannel>? = runCatching {
        val wanted = TV_CHANNELS.filter { it.first in channels }
        val c = URL(src).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", WallSources.userAgent())
        try {
            if (c.responseCode != 200) { lastError = "HTTP ${c.responseCode}"; return@runCatching null }
            val raw = BufferedInputStream(c.inputStream, 64 * 1024)
            raw.mark(4)
            val b1 = raw.read(); val b2 = raw.read()
            raw.reset()
            val input = if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(raw, 64 * 1024), 64 * 1024) else raw
            val p = android.util.Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)
            val idMap = HashMap<String, String>()          // XMLTV-Kanal-ID -> unser Sendername
            val shows = HashMap<String, MutableList<EpgShow>>()
            val until = now + 12 * 3_600_000L
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "channel") {
                    val id = p.getAttributeValue(null, "id").orEmpty()
                    val names = mutableListOf(id)
                    while (!(p.next() == XmlPullParser.END_TAG && p.name == "channel")) {
                        if (p.eventType == XmlPullParser.START_TAG && p.name == "display-name") names += p.nextText()
                        if (p.eventType == XmlPullParser.END_DOCUMENT) break
                    }
                    match(names, wanted)?.let { idMap[id] = it }
                } else if (ev == XmlPullParser.START_TAG && p.name == "programme") {
                    val ch = idMap[p.getAttributeValue(null, "channel").orEmpty()]
                    val start = ts(p.getAttributeValue(null, "start")); val stop = ts(p.getAttributeValue(null, "stop"))
                    if (ch != null && start != null && stop != null && stop > now && start < until) {
                        var title = ""; var info = ""
                        while (!(p.next() == XmlPullParser.END_TAG && p.name == "programme")) {
                            if (p.eventType == XmlPullParser.START_TAG) when (p.name) {
                                "title" -> if (title.isEmpty()) title = p.nextText().trim()
                                "sub-title" -> info = p.nextText().trim()
                                "category" -> if (info.isEmpty()) info = p.nextText().trim()
                            }
                            if (p.eventType == XmlPullParser.END_DOCUMENT) break
                        }
                        if (title.isNotEmpty()) shows.getOrPut(ch) { mutableListOf() } += EpgShow(title, start, stop, info)
                    }
                }
                ev = p.next()
            }
            if (idMap.isEmpty()) lastError = t("Keine passenden Sender in der Programmdatei", "No matching channels in the guide file")
            wanted.map { (n, _) -> EpgChannel(n, shows[n].orEmpty().sortedBy { it.start }.distinctBy { it.start }) }
        } finally { c.disconnect() }
    }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
}

/** Öffnet waipu.tv (oder die erste installierte App mit „waipu“ im Namen). */
fun openWaipu(ctx: Context): Boolean {
    val pm = ctx.packageManager
    val pkgs = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { cat ->
        runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0).map { it.activityInfo.packageName } }.getOrDefault(emptyList())
    }.distinct()
    val pkg = pkgs.firstOrNull { it.contains("waipu", true) } ?: return false
    val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
    return runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
}

@Composable
private fun ShowProgress(s: EpgShow, now: Long, modifier: Modifier) {
    val f = ((now - s.start).toFloat() / (s.stop - s.start).coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(NeoCyan))
    }
}

@Composable
fun TvCard(cfg: WidgetCfg, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val list by produceState<List<EpgChannel>?>(null, cfg.tvChannels, cfg.epgUrl) {
        while (true) { value = Epg.load(ctx.applicationContext, cfg.epgUrl, cfg.tvChannels); delay(15 * 60_000L) }
    }
    // alle 8 Sekunden der nächste Sender
    val step by produceState(0) { while (true) { delay(8_000); value++ } }
    InfoCard(t("Jetzt im TV", "On TV now"), "📺", Color(0xFF3D7BFF), modifier, onFocus, onClick) {
        val now = System.currentTimeMillis()
        val withNow = list.orEmpty().filter { it.now(now) != null }
        when {
            list == null -> Text(t("Lade Programm …", "Loading guide …"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
            withNow.isEmpty() -> Text(t("Kein Programm – OK für Details", "No guide – OK for details"), fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
            else -> {
                val ch = withNow[step % withNow.size]
                val cur = ch.now(now)!!
                Text(ch.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = NeoCyan, maxLines = 1)
                Text(cur.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = if (roomy()) 2 else 1, overflow = TextOverflow.Ellipsis)
                ShowProgress(cur, now, Modifier.fillMaxWidth().padding(vertical = 2.dp))
                ch.next(now)?.let { n -> Text(t("Danach ", "Next ") + hhmm(n.start) + " " + n.title, fontSize = 9.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
fun TvGuideDialog(cfg: WidgetCfg, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var force by remember { mutableStateOf(0) }
    val list by produceState<List<EpgChannel>?>(null, force) { value = Epg.load(ctx.applicationContext, cfg.epgUrl, cfg.tvChannels, force = force > 0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(list) { delay(150); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        dlgNoDim(androidx.compose.ui.platform.LocalView.current)
        NeoSheet(Ico.SCREEN, t("TV-Programm", "TV guide"), t("Jetzt & gleich · OK öffnet waipu.tv", "Now & next · OK opens waipu.tv"), width = 1060.dp) {
            val now = System.currentTimeMillis()
            val l = list
            when {
                l == null -> Text(t("Lade Programm … (beim ersten Mal bis zu einer Minute)", "Loading guide … (first time up to a minute)"), color = Color.White)
                l.all { it.shows.isEmpty() } -> {
                    Text(t("Kein Programm gefunden.", "No guide found.") + (if (Epg.lastError.isNotEmpty()) " (" + Epg.lastError + ")" else ""), color = Color.White)
                    Pill(t("Erneut laden", "Reload"), Modifier.focusRequester(first)) { force++ }
                }
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(l) { i, ch ->
                        val cur = ch.now(now); val nx = ch.next(now)
                        FocusRow(if (i == 0) Modifier.focusRequester(first) else Modifier, onClick = {
                            if (!openWaipu(ctx)) Toast.makeText(ctx, t("waipu.tv ist nicht installiert.", "waipu.tv is not installed."), Toast.LENGTH_SHORT).show()
                        }) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(ch.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeoCyan, modifier = Modifier.width(150.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    if (cur != null) {
                                        Text(hhmm(cur.start) + "  " + cur.title + (if (cur.info.isNotEmpty()) " · " + cur.info else ""), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        ShowProgress(cur, now, Modifier.fillMaxWidth())
                                    } else Text(t("Keine Daten", "No data"), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                                    if (nx != null) Text(hhmm(nx.start) + "  " + nx.title, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (cur != null) Text(t("noch ", "") + ((cur.stop - now) / 60_000).coerceAtLeast(0) + t(" Min.", " min left"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            }
                        }
                    }
                    item { Text(t("Programmdaten: epgshare01 (XMLTV). Sender wählst du unter Info-Widgets.", "Guide data: epgshare01 (XMLTV). Pick channels under Info widgets."), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
    }
}

// ============================================================== Tor-Alarm (Live-Ticker)

@Immutable class GoalEvent(val match: FbMatch, val forMine: Boolean, val at: Long)

/**
 * Prüft im Hintergrund (solange Lumen läuft, also auch neben anderen Apps dank Bedienungshilfe)
 * die Spiele des eigenen Vereins: während eines Spiels jede Minute, sonst alle 10 Minuten.
 * Bei einem Tor: Banner in Lumen bzw. Hinweis über anderen Apps.
 */
object GoalWatch {
    val event = MutableStateFlow<GoalEvent?>(null)
    @Volatile var foreground = false
    private var job: Job? = null
    private val scores = HashMap<Int, Pair<Int, Int>>()

    fun start(ctx: Context) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                var wait = 10 * 60_000L
                runCatching {
                    val cfg = loadPrefs(app).widgets
                    if (cfg.football && cfg.goalAlert) {
                        val d = Football.load(cfg)
                        val m = d?.live
                        val soon = d?.next?.time?.let { it.isBefore(LocalDateTime.now().plusMinutes(15)) } == true
                        if (m != null || soon) wait = 60_000L
                        if (m != null && m.g1 != null && m.g2 != null) {
                            val prev = scores[m.id]
                            val cur: Pair<Int, Int> = (m.g1 ?: 0) to (m.g2 ?: 0)
                            if (prev != null && (cur.first > prev.first || cur.second > prev.second)) {
                                val myHome = m.home.id == d?.team?.id
                                val mine = if (cur.first > prev.first) myHome else !myHome
                                fire(app, GoalEvent(m, mine, System.currentTimeMillis()))
                            }
                            scores[m.id] = cur
                        }
                    }
                }
                delay(wait)
            }
        }
    }

    private fun fire(ctx: Context, e: GoalEvent) {
        event.value = e
        if (!foreground) Handler(Looper.getMainLooper()).post {
            val m = e.match
            val txt = (if (e.forMine) "⚽ TOR! " else "⚽ " + t("Gegentor: ", "Goal against: ")) + "${m.home.short} ${m.g1}:${m.g2} ${m.away.short}" + (if (m.lastGoal.isNotEmpty()) " – ${m.lastGoal}" else "")
            Toast.makeText(ctx, txt, Toast.LENGTH_LONG).show()
        }
    }
}

/** Tor-Banner oben in der Mitte, blinkt ein paar Sekunden. */
@Composable
fun GoalBanner(modifier: Modifier = Modifier) {
    val e by GoalWatch.event.collectAsState()
    var visible by remember { mutableStateOf<GoalEvent?>(null) }
    LaunchedEffect(e) {
        val ev = e ?: return@LaunchedEffect
        if (System.currentTimeMillis() - ev.at > 60_000) return@LaunchedEffect
        visible = ev; delay(10_000); visible = null
    }
    val ev = visible ?: return
    val pulse by rememberInfiniteTransition(label = "goal").animateFloat(0.55f, 1f, infiniteRepeatable(tween(350), RepeatMode.Reverse), label = "p")
    val m = ev.match
    val col = if (ev.forMine) Color(0xFFFFC21A) else Color(0xFF9AA3B5)
    Row(
        modifier.clip(RoundedCornerShape(26.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF5A3825).copy(alpha = 0.96f), Color(0xFF1A1D2B).copy(alpha = 0.96f))))
            .border(3.dp, col.copy(alpha = pulse), RoundedCornerShape(26.dp)).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (ev.forMine) "TOR!" else t("Gegentor", "Goal against"), fontSize = 30.sp, fontWeight = FontWeight.Black, color = col.copy(alpha = pulse))
        TeamIcon(m.home.icon, 40.dp, m.home.name)
        Text("${m.g1}:${m.g2}", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        TeamIcon(m.away.icon, 40.dp, m.away.name)
        Column {
            Text("${m.home.short} – ${m.away.short}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            if (m.lastGoal.isNotEmpty()) Text("⚽ " + m.lastGoal, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}
