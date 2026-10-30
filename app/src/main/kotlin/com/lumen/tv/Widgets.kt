package com.lumen.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Xml
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.io.File
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// ============================================================== Einstellungen

@Immutable class Countdown(val name: String, val date: String, val yearly: Boolean = false)

/** Einstellungen der Info-Widgets. Wird als eigenes JSON-Objekt in den Prefs gespeichert. */
@Immutable
data class WidgetCfg(
    val football: Boolean = false,
    val league: String = "bl2",
    val teamName: String = "FC St. Pauli",
    val teamId: Int = 0,
    val ticker: Boolean = false,
    val tickerPos: Int = 0,                 // 0 = unten, 1 = oben
    val tickerSrc: Set<String> = setOf("tagesschau"),
    val weatherBig: Boolean = false,
    val countdown: Boolean = false,
    val countdowns: List<Countdown> = emptyList(),
    val note: Boolean = false,
    val noteText: String = "",
    val net: Boolean = false,
    val routerIp: String = "",              // optional von Hand, falls die Box den Router nicht meldet
    val goalAlert: Boolean = true,          // Tor-Alarm für den eigenen Verein
    val tv: Boolean = false,                // „Jetzt im TV“
    val tvChannels: List<String> = TV_DEFAULT,
    val epgUrl: String = "",                // leer = epgshare01 Deutschland
    val sun: Boolean = false,               // Sonne & Mond
) {
    /** Reihenfolge der Kacheln in der Reihe „Infos“. */
    val kinds: List<String> get() = listOfNotNull(
        "football".takeIf { football }, "weather".takeIf { weatherBig }, "countdown".takeIf { countdown },
        "note".takeIf { note }, "net".takeIf { net }, "tv".takeIf { tv }, "sun".takeIf { sun },
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("football", football); put("league", league); put("teamName", teamName); put("teamId", teamId)
        put("ticker", ticker); put("tickerPos", tickerPos); put("tickerSrc", JSONArray(tickerSrc.toList()))
        put("weatherBig", weatherBig); put("countdown", countdown)
        put("countdowns", JSONArray().apply { countdowns.forEach { put(JSONObject().put("name", it.name).put("date", it.date).put("yearly", it.yearly)) } })
        put("note", note); put("noteText", noteText); put("net", net); put("routerIp", routerIp)
        put("goalAlert", goalAlert); put("tv", tv); put("tvChannels", JSONArray(tvChannels)); put("epgUrl", epgUrl); put("sun", sun)
    }
}

fun widgetCfgFromJson(o: JSONObject): WidgetCfg {
    val d = WidgetCfg()
    fun strs(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }
    return WidgetCfg(
        football = o.optBoolean("football", d.football), league = o.optString("league", d.league),
        teamName = o.optString("teamName", d.teamName), teamId = o.optInt("teamId", d.teamId),
        ticker = o.optBoolean("ticker", d.ticker), tickerPos = o.optInt("tickerPos", d.tickerPos).coerceIn(0, 1),
        tickerSrc = o.optJSONArray("tickerSrc")?.let { strs(it).toSet() } ?: d.tickerSrc,
        weatherBig = o.optBoolean("weatherBig", d.weatherBig), countdown = o.optBoolean("countdown", d.countdown),
        countdowns = o.optJSONArray("countdowns")?.let { a ->
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Countdown(it.optString("name"), it.optString("date"), it.optBoolean("yearly")) } }
        } ?: emptyList(),
        note = o.optBoolean("note", d.note), noteText = o.optString("noteText", d.noteText), net = o.optBoolean("net", d.net),
        routerIp = o.optString("routerIp", ""),
        goalAlert = o.optBoolean("goalAlert", d.goalAlert), tv = o.optBoolean("tv", d.tv),
        tvChannels = o.optJSONArray("tvChannels")?.let { strs(it) }?.takeIf { it.isNotEmpty() } ?: d.tvChannels,
        epgUrl = o.optString("epgUrl", d.epgUrl), sun = o.optBoolean("sun", d.sun),
    )
}

// ============================================================== Netz-Hilfe

internal object Http {
    fun get(url: String, timeout: Int = 10_000): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeout; c.readTimeout = timeout
        c.setRequestProperty("User-Agent", WallSources.userAgent())
        c.setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml, */*")
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
    }.getOrNull()
}

// ============================================================== Fußball (OpenLigaDB, frei, ohne Schlüssel)

@Immutable class FbTeam(val id: Int, val name: String, val short: String, val icon: String)
@Immutable class FbMatch(
    val id: Int, val time: LocalDateTime, val home: FbTeam, val away: FbTeam, val finished: Boolean,
    val g1: Int?, val g2: Int?, val matchday: String, val lastGoal: String,
) {
    /** Läuft gerade: begonnen, nicht beendet, nicht älter als 2,5 Stunden. */
    val live: Boolean get() = !finished && time.isBefore(LocalDateTime.now()) && time.plusMinutes(150).isAfter(LocalDateTime.now())
}
@Immutable class FbRow(val pos: Int, val team: FbTeam, val played: Int, val diff: Int, val points: Int)
@Immutable class FbData(val team: FbTeam?, val last: FbMatch?, val next: FbMatch?, val live: FbMatch?, val table: List<FbRow>, val upcoming: List<FbMatch>, val recent: List<FbMatch>)

val LEAGUES = listOf("bl1" to "1. Bundesliga", "bl2" to "2. Bundesliga", "bl3" to "3. Liga")

object Football {
    private const val API = "https://api.openligadb.de"
    private var cache: Pair<String, Pair<Long, FbData>>? = null

    fun season(): Int = LocalDate.now().let { if (it.monthValue >= 7) it.year else it.year - 1 }

    private fun team(o: JSONObject?) = FbTeam(o?.optInt("teamId") ?: 0, o?.optString("teamName").orEmpty(), o?.optString("shortName").orEmpty().ifEmpty { o?.optString("teamName").orEmpty() }, o?.optString("teamIconUrl").orEmpty())

    private fun match(o: JSONObject): FbMatch? = runCatching {
        val utc = o.optString("matchDateTimeUTC")
        val time = if (utc.isNotEmpty()) OffsetDateTime.parse(utc).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
        else LocalDateTime.parse(o.getString("matchDateTime"))
        val finished = o.optBoolean("matchIsFinished")
        var g1: Int? = null; var g2: Int? = null
        val res = o.optJSONArray("matchResults")
        if (res != null && res.length() > 0) {
            val end = (0 until res.length()).map { res.getJSONObject(it) }.maxByOrNull { it.optInt("resultOrderID") }
            if (end != null) { g1 = end.optInt("pointsTeam1"); g2 = end.optInt("pointsTeam2") }
        }
        val goals = o.optJSONArray("goals")
        var lastGoal = ""
        if (goals != null && goals.length() > 0) {
            val g = goals.getJSONObject(goals.length() - 1)
            if (!finished) { g1 = g.optInt("scoreTeam1"); g2 = g.optInt("scoreTeam2") }
            lastGoal = g.optString("goalGetterName").let { n -> if (n.isNotEmpty()) "$n ${g.optInt("matchMinute").takeIf { it > 0 }?.let { "$it'" }.orEmpty()}".trim() else "" }
        }
        if (!finished && time.isBefore(LocalDateTime.now()) && g1 == null) { g1 = 0; g2 = 0 }
        FbMatch(o.optInt("matchID"), time, team(o.optJSONObject("team1")), team(o.optJSONObject("team2")), finished, g1, g2, o.optJSONObject("group")?.optString("groupName").orEmpty(), lastGoal)
    }.getOrNull()

    suspend fun teams(league: String): List<FbTeam> = withContext(Dispatchers.IO) {
        val txt = Http.get("$API/getavailableteams/$league/${season()}") ?: return@withContext emptyList()
        runCatching { JSONArray(txt).let { a -> (0 until a.length()).map { team(a.getJSONObject(it)) } }.sortedBy { it.name } }.getOrDefault(emptyList())
    }

    /** Spiele und Tabelle für den Verein. Zwischengespeichert: 10 Minuten, während eines Spiels 1 Minute. */
    suspend fun load(cfg: WidgetCfg, force: Boolean = false): FbData? = withContext(Dispatchers.IO) {
        val key = "${cfg.league}|${cfg.teamId}|${cfg.teamName}"
        val c = cache
        if (!force && c != null && c.first == key) {
            val age = System.currentTimeMillis() - c.second.first
            if (age < (if (c.second.second.live != null) 60_000L else 600_000L)) return@withContext c.second.second
        }
        val s = season()
        val all = Http.get("$API/getmatchdata/${cfg.league}/$s", 15_000)?.let { txt ->
            runCatching { JSONArray(txt).let { a -> (0 until a.length()).mapNotNull { match(a.getJSONObject(it)) } } }.getOrNull()
        } ?: return@withContext c?.second?.second
        val tid = cfg.teamId.takeIf { it > 0 }
            ?: all.flatMap { listOf(it.home, it.away) }.firstOrNull { it.name.equals(cfg.teamName, true) || it.name.contains(cfg.teamName, true) || it.short.equals(cfg.teamName, true) }?.id
        val mine = all.filter { it.home.id == tid || it.away.id == tid }.sortedBy { it.time }
        val now = LocalDateTime.now()
        val live = mine.firstOrNull { it.live }
        val last = mine.lastOrNull { it.finished }
        val next = mine.firstOrNull { !it.finished && it.time.isAfter(now) }
        val table = Http.get("$API/getbltable/${cfg.league}/$s")?.let { txt ->
            runCatching {
                val a = JSONArray(txt)
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    FbRow(i + 1, FbTeam(o.optInt("teamInfoId"), o.optString("teamName"), o.optString("shortName").ifEmpty { o.optString("teamName") }, o.optString("teamIconUrl")),
                        o.optInt("matches"), o.optInt("goalDiff"), o.optInt("points"))
                }
            }.getOrNull()
        } ?: emptyList()
        val teamObj = mine.firstOrNull()?.let { if (it.home.id == tid) it.home else it.away }
        val data = FbData(teamObj, last, next, live, table, mine.filter { !it.finished }.take(5), mine.filter { it.finished }.takeLast(5).reversed())
        cache = key to (System.currentTimeMillis() to data)
        data
    }
}

// ============================================================== Nachrichten (RSS)

// Vorwiegend deutsche Quellen, einzeln an-/abwählbar (die Einstellung baut die Schalter automatisch).
val NEWS_SOURCES = listOf(
    // Nachrichten
    Triple("tagesschau", "Tagesschau", "https://www.tagesschau.de/index~rss2.xml"),
    Triple("spiegel", "SPIEGEL", "https://www.spiegel.de/schlagzeilen/index.rss"),
    Triple("zeit", "ZEIT ONLINE", "https://newsfeed.zeit.de/index"),
    Triple("sueddeutsche", "Süddeutsche", "https://rss.sueddeutsche.de/rss/Topthemen"),
    Triple("faz", "FAZ", "https://www.faz.net/rss/aktuell/"),
    Triple("welt", "WELT", "https://www.welt.de/feeds/latest.rss"),
    Triple("ntv", "n-tv", "https://www.n-tv.de/rss"),
    Triple("focus", "FOCUS Online", "https://rss.focus.de/fol/XML/rss_folnews.xml"),
    Triple("tagesspiegel", "Tagesspiegel", "https://www.tagesspiegel.de/contentexport/feed/home"),
    Triple("stern", "stern", "https://www.stern.de/feed/standard/alle-nachrichten/"),
    // Sport
    Triple("sportschau", "Sportschau", "https://www.sportschau.de/index~rss2.xml"),
    Triple("kicker", "kicker", "https://newsfeed.kicker.de/news/aktuell"),
    Triple("sport1", "SPORT1", "https://www.sport1.de/news.rss"),
    // Technik & Digital
    Triple("heise", "heise online", "https://www.heise.de/rss/heise-top-atom.xml"),
    Triple("golem", "Golem.de", "https://rss.golem.de/rss.php?feed=RSS2.0"),
    Triple("t3n", "t3n", "https://t3n.de/rss.xml"),
    // International (englisch)
    Triple("bbc", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml"),
)

@Immutable class Headline(val source: String, val title: String)

object News {
    private var cache: Pair<Set<String>, Pair<Long, List<Headline>>>? = null

    private fun parse(xml: String, source: String): List<Headline> = runCatching {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(xml.reader())
        val out = mutableListOf<Headline>()
        var inItem = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT && out.size < 10) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "item", "entry" -> inItem = true
                    "title" -> if (inItem) p.nextText().trim().replace(Regex("\\s+"), " ").takeIf { it.isNotEmpty() }?.let { out += Headline(source, it) }
                }
            } else if (ev == XmlPullParser.END_TAG && (p.name == "item" || p.name == "entry")) inItem = false
            ev = p.next()
        }
        out
    }.getOrDefault(emptyList())

    /** Schlagzeilen der gewählten Quellen, abwechselnd gemischt. Cache 15 Minuten. */
    suspend fun load(src: Set<String>): List<Headline> = coroutineScope {
        val c = cache
        if (c != null && c.first == src && System.currentTimeMillis() - c.second.first < 15 * 60_000L) return@coroutineScope c.second.second
        val lists = NEWS_SOURCES.filter { it.first in src }.map { (_, name, url) ->
            async(Dispatchers.IO) { Http.get(url)?.let { parse(it, name) } ?: emptyList() }
        }.awaitAll()
        val mixed = mutableListOf<Headline>()
        for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) lists.forEach { l -> l.getOrNull(i)?.let { mixed += it } }
        if (mixed.isNotEmpty()) cache = src to (System.currentTimeMillis() to mixed)
        mixed.ifEmpty { c?.second?.second ?: emptyList() }
    }
}

// ============================================================== Wetter stündlich (Open-Meteo)

@Immutable class HourWx(val hour: Int, val temp: Int, val rain: Int, val code: Int)

object HourlyWeather {
    private var cache: Pair<String, Pair<Long, List<HourWx>>>? = null

    suspend fun load(lat: Double, lon: Double): List<HourWx> = withContext(Dispatchers.IO) {
        val key = "$lat,$lon"
        val c = cache
        if (c != null && c.first == key && System.currentTimeMillis() - c.second.first < 20 * 60_000L) return@withContext c.second.second
        val txt = Http.get("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&hourly=temperature_2m,precipitation_probability,weather_code&forecast_days=2&timezone=auto")
            ?: return@withContext c?.second?.second ?: emptyList()
        val list = runCatching {
            val h = JSONObject(txt).getJSONObject("hourly")
            val times = h.getJSONArray("time"); val temps = h.getJSONArray("temperature_2m")
            val rain = h.getJSONArray("precipitation_probability"); val codes = h.getJSONArray("weather_code")
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
            (0 until times.length()).mapNotNull { i ->
                val t = LocalDateTime.parse(times.getString(i))
                if (t.isBefore(now)) null else HourWx(t.hour, Math.round(temps.optDouble(i)).toInt(), rain.optInt(i), codes.optInt(i))
            }.take(24)
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) cache = key to (System.currentTimeMillis() to list)
        list
    }

    /** Animiertes Regenradar Deutschland (Deutscher Wetterdienst). */
    const val RADAR_URL = "https://www.dwd.de/DWD/wetter/radar/radfilm_brd_akt.gif"
}

// ============================================================== Countdown

fun Countdown.daysLeft(): Long? = runCatching {
    var d = LocalDate.parse(date)
    val today = LocalDate.now()
    if (yearly) { d = d.withYear(today.year); if (d.isBefore(today)) d = d.plusYears(1) }
    ChronoUnit.DAYS.between(today, d)
}.getOrNull()

/** "24.12.2026" oder "2026-12-24" -> ISO-Datum. */
fun parseDateInput(s: String): String? = runCatching {
    val t = s.trim()
    if (t.contains('-')) LocalDate.parse(t).toString()
    else {
        val p = t.split('.', '/', ' ').filter { it.isNotBlank() }.map { it.toInt() }
        val y = p.getOrNull(2)?.let { if (it < 100) 2000 + it else it } ?: LocalDate.now().year
        LocalDate.of(y, p[1], p[0]).toString()
    }
}.getOrNull()

// ============================================================== Heimnetz

@Immutable class SpeedResult(val down: Double, val up: Double?, val ping: Double?, val jitter: Double?, val mb: Long, val note: String = "") {
    /** Einordnung für den Alltag. */
    val verdict: String get() = when {
        down <= 0.0 && note.isNotEmpty() -> t("Download fehlgeschlagen (", "Download failed (") + note + ")"
        down >= 60 -> t("Top – mehrere 4K-Streams gleichzeitig möglich", "Excellent – several 4K streams at once")
        down >= 25 -> t("Sehr gut – 4K-Streaming läuft flüssig", "Very good – smooth 4K streaming")
        down >= 15 -> t("Gut – Full-HD problemlos, 4K grenzwertig", "Good – Full HD fine, 4K borderline")
        down >= 6 -> t("Reicht für HD", "Enough for HD")
        else -> t("Knapp – nur SD-Qualität flüssig", "Tight – only SD plays smoothly")
    }
}

@Immutable class NetInfo(val type: String, val ip: String, val gateway: String, val routerMs: Int?, val internetMs: Int?)

object HomeNet {
    var lastSpeed: Double? = null
    var lastDevices: List<String> = emptyList()
    var lastDeviceList: List<Device> = emptyList()
    /** Das echte Heimnetz (WLAN/LAN) – nicht das VPN. Wird von info() gesetzt und für Ping/Gerätesuche benutzt. */
    @Volatile private var lan: android.net.Network? = null

    /** Zeit für einen TCP-Verbindungsaufbau über das Heimnetz; „Verbindung abgelehnt“ zählt auch als erreichbar (für Router-/Internet-Ping). */
    private fun connectMs(host: String, port: Int, timeout: Int, viaLan: Boolean = true): Int? {
        val t0 = System.nanoTime()
        // Bei VPN ohne „lokales Netz erlauben“ verweigert Android das Binden ans WLAN (EPERM) -> dann normaler Socket.
        val sock = (if (viaLan) runCatching { lan?.socketFactory?.createSocket() }.getOrNull() else null) ?: Socket()
        return try {
            sock.use { it.connect(InetSocketAddress(host, port), timeout) }
            ((System.nanoTime() - t0) / 1_000_000).toInt()
        } catch (e: java.net.ConnectException) {
            ((System.nanoTime() - t0) / 1_000_000).toInt().takeIf { it < timeout }
        } catch (e: Exception) { null }
    }

    /** Für die Gerätesuche: NUR eine echt zustande gekommene Verbindung zählt (kein „abgelehnt“ – sonst flutet ein VPN die Liste). */
    private fun tcpOpen(host: String, port: Int, timeout: Int): Boolean {
        val sock = runCatching { lan?.socketFactory?.createSocket() }.getOrNull() ?: Socket()
        return try { sock.use { it.connect(InetSocketAddress(host, port), timeout) }; true } catch (e: Exception) { false }
    }

    /** Stand wie 1.72 (lief ohne Probleme), nur zusätzlich gegen Fehler abgesichert. */
    suspend fun info(ctx: Context, manualRouter: String = ""): NetInfo = withContext(Dispatchers.IO) {
        runCatching {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            val all = cm.allNetworks.toList()
            fun caps(n: android.net.Network) = cm.getNetworkCapabilities(n)
            val home = all.firstOrNull { caps(it)?.let { c -> !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } == true }
                ?: all.firstOrNull { caps(it)?.let { c -> !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } == true }
                ?: cm.activeNetwork
            lan = home
            val hc = home?.let { caps(it) }
            val vpn = all.any { caps(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
            val lp = home?.let { cm.getLinkProperties(it) }
            val type = when {
                hc == null -> "Offline"
                hc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "LAN"
                hc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
                else -> "Online"
            } + if (vpn) " + VPN" else ""
            val ip = lp?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress.orEmpty()
            var gw = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress.orEmpty()
            if (gw.isEmpty() || gw == "0.0.0.0") gw = lp?.routes?.mapNotNull { r -> r.gateway?.takeIf { it is Inet4Address && !it.isAnyLocalAddress }?.hostAddress }?.firstOrNull().orEmpty()
            val router = if (gw.isNotEmpty()) listOf(80, 53, 443, 8080, 49000).firstNotNullOfOrNull { connectMs(gw, it, 1200) } else null
            val inet = connectMs("1.1.1.1", 443, 2500, viaLan = false)
            NetInfo(type, ip, gw, router, inet)
        }.getOrElse { NetInfo("?", "", "", null, null) }
    }

    var lastResult: SpeedResult? = null

    /**
     * Ausführlicher Speedtest (Cloudflare): Ping & Jitter (10 Verbindungen), Download (max. 10 s), Upload (max. 8 s).
     * live(phase, Mbit/s) meldet den Zwischenstand für die Anzeige.
     */
    suspend fun speedtest(live: (String, Double) -> Unit = { _, _ -> }): SpeedResult? = withContext(Dispatchers.IO) {
        runCatching {
            // Ping & Jitter
            live("ping", 0.0)
            val pings = (1..10).mapNotNull { connectMs("speed.cloudflare.com", 443, 2000, viaLan = false) }
            val ping = if (pings.isEmpty()) null else pings.sorted().take(8).average()
            val jitter = if (pings.size < 2) null else pings.zipWithNext { a, b -> kotlin.math.abs(a - b) }.average()
            // Download: bis zu 10 s Daten von Cloudflare laden; bei Bedarf neue Verbindung (langsamer Start / Paket zu Ende)
            live("down", 0.0)
            var dlBytes = 0L
            var dlErr: String? = null
            val dlStart = System.nanoTime()
            run {
                val buf = ByteArray(64 * 1024); var lastUi = dlStart
                while (System.nanoTime() - dlStart < 10_000_000_000L) {
                    val c = URL("https://speed.cloudflare.com/__down?bytes=50000000").openConnection() as HttpURLConnection
                    c.connectTimeout = 6000; c.readTimeout = 6000
                    // Ohne richtigen User-Agent / mit gzip liefert Cloudflare den Download-Endpunkt teils gar nicht aus
                    c.setRequestProperty("User-Agent", WallSources.userAgent())
                    c.setRequestProperty("Accept-Encoding", "identity")
                    try {
                        val code = c.responseCode
                        if (code != 200) { dlErr = "HTTP $code"; (c.errorStream ?: c.inputStream)?.use { it.readBytes() }; c.disconnect(); break }
                        c.inputStream.use { inp ->
                            while (true) {
                                val r = inp.read(buf); if (r < 0) break
                                dlBytes += r
                                val now = System.nanoTime()
                                if (now - lastUi > 400_000_000L) { lastUi = now; live("down", dlBytes * 8 / 1e6 / ((now - dlStart) / 1e9)) }
                                if (now - dlStart > 10_000_000_000L) break
                            }
                        }
                    } catch (e: Exception) {
                        if (dlBytes == 0L) dlErr = e.message ?: e.javaClass.simpleName
                    } finally { c.disconnect() }
                }
            }
            val dl = dlBytes * 8 / 1e6 / ((System.nanoTime() - dlStart) / 1e9)
            // Upload: Testdaten in Blöcken senden, bis 8 s oder 25 MB
            var ulBytes = 0L
            val ul = runCatching {
                val c = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 8000
                c.setChunkedStreamingMode(64 * 1024)
                c.setRequestProperty("User-Agent", WallSources.userAgent())
                c.setRequestProperty("Content-Type", "application/octet-stream")
                val chunk = ByteArray(64 * 1024) { (it * 31).toByte() }
                val t0 = System.nanoTime(); var lastUi = t0
                c.outputStream.use { out ->
                    while (ulBytes < 25_000_000L && System.nanoTime() - t0 < 8_000_000_000L) {
                        out.write(chunk); ulBytes += chunk.size
                        val now = System.nanoTime()
                        if (now - lastUi > 400_000_000L) { lastUi = now; live("up", ulBytes * 8 / 1e6 / ((now - t0) / 1e9)) }
                    }
                }
                runCatching { c.inputStream.use { it.readBytes() } }
                c.disconnect()
                ulBytes * 8 / 1e6 / ((System.nanoTime() - t0) / 1e9)
            }.getOrNull()
            SpeedResult(dl, ul, ping, jitter, (dlBytes + ulBytes) / 1_000_000, dlErr.orEmpty()).also { lastResult = it; lastSpeed = dl }
        }.getOrNull()
    }

    /** Ein gefundenes Gerät: IP, optional Name und Hersteller (aus der MAC). */
    @Immutable class Device(val ip: String, val name: String = "", val vendor: String = "") {
        val shown: String get() = name.ifEmpty { vendor }
        val label: String get() = if (shown.isEmpty()) ip else "$shown  ·  $ip"
    }

    suspend fun scan(ctx: Context, ip: String): List<String> = scanDevices(ctx, ip).map { it.label }

    /**
     * Geräte im Heimnetz finden: Ports anklopfen (nur echte Verbindungen zählen), zusätzlich die ARP-Tabelle.
     * Namen: zuerst mDNS/Bonjour (Chromecast, Apple-TV, Drucker, Sonos, NAS, PCs …), sonst Hersteller aus der MAC, sonst Reverse-DNS/NetBIOS.
     */
    suspend fun scanDevices(ctx: Context, ip: String): List<Device> = runCatching { scanInner(ctx, ip) }.getOrDefault(emptyList())

    private suspend fun scanInner(ctx: Context, ip: String): List<Device> = coroutineScope {
        if (!ip.contains('.')) return@coroutineScope emptyList()
        val base = ip.substringBeforeLast('.')
        val gate = Semaphore(64)
        // mDNS parallel zum Port-Scan starten (liefert die echten Gerätenamen)
        val mdnsJob = async(Dispatchers.IO) { runCatching { Mdns.names(ctx) }.getOrDefault(emptyMap()) }
        // typische Ports: Web, Chromecast, iPhone/Mac, Windows, AirPlay, Kameras, Android-TV (adb), Sonos, Drucker, FRITZ!Box, SSH, Smart-Home
        val ports = listOf(80, 443, 8008, 8009, 62078, 445, 139, 7000, 554, 5555, 1400, 631, 49000, 22, 8080, 8443, 5000, 9000, 1883, 8123)
        val hits = (1..254).map { h ->
            async(Dispatchers.IO) {
                val host = "$base.$h"
                if (host == ip) return@async null
                // Erst ein schneller „Lebenszeichen“-Port, nur bei Treffer die restlichen prüfen (spart Zeit, vermeidet Fehlalarme)
                gate.withPermit { if (ports.any { p -> tcpOpen(host, p, 350) }) host else null }
            }
        }.awaitAll().filterNotNull().toMutableSet()

        // ARP-Tabelle: echte Nachbarn (IP ⇄ MAC), die die Box kennt – oft zuverlässiger als der Port-Scan
        val arp = readArp(base)          // ip -> mac
        arp.keys.forEach { hits.add(it) }
        val mdns = mdnsJob.await()        // ip -> Name
        mdns.keys.forEach { if (it.startsWith("$base.")) hits.add(it) }

        val devices = hits.map { host ->
            async(Dispatchers.IO) {
                val mac = arp[host]
                val name = mdns[host] ?: gate.withPermit { deviceName(host) }
                Device(host, name, mac?.let { vendorOf(it) } ?: "")
            }
        }.awaitAll()
            .filter { it.ip != ip }
            .sortedBy { it.ip.substringAfterLast('.').toIntOrNull() ?: 0 }
        lastDevices = devices.map { it.label }
        lastDeviceList = devices
        devices
    }

    /** /proc/net/arp lesen: nur vollständige Einträge (Flag 0x2) im eigenen /24. Auf neuem Android evtl. gesperrt -> leer. */
    private fun readArp(base: String): Map<String, String> = runCatching {
        val out = LinkedHashMap<String, String>()
        File("/proc/net/arp").readLines().drop(1).forEach { line ->
            val p = line.trim().split(Regex("\\s+"))
            if (p.size >= 4) {
                val ip = p[0]; val flags = p[2]; val mac = p[3]
                if (ip.startsWith("$base.") && flags == "0x2" && mac != "00:00:00:00:00:00") out[ip] = mac.uppercase()
            }
        }
        out
    }.getOrDefault(emptyMap())

    /** Hersteller aus den ersten drei MAC-Bytes (OUI). Nur gängige Heim-Marken; sonst leer. */
    private fun vendorOf(mac: String): String {
        val oui = mac.replace(":", "").replace("-", "").uppercase().take(6)
        return OUI[oui] ?: ""
    }

    /** Name eines Geräts: zuerst Reverse-DNS (vom Router), dann NetBIOS (Windows/Samba). Leer, wenn unbekannt. */
    private fun deviceName(host: String): String {
        runCatching {
            val canon = java.net.InetAddress.getByName(host).canonicalHostName
            if (canon != host && !canon.equals(host, true)) return cleanName(canon)
        }
        return runCatching { netbiosName(host) }.getOrDefault("")
    }

    private fun cleanName(n: String): String =
        n.substringBefore('.').removeSuffix("$").trim()
            .takeIf { it.isNotEmpty() && !it.all { c -> c.isDigit() } }
            ?.let { if (it.length > 24) it.take(24) else it } ?: ""

    /** NetBIOS-Namensabfrage (UDP 137) – liefert bei Windows-PCs und manchen NAS den Rechnernamen. */
    private fun netbiosName(host: String): String {
        val query = byteArrayOf(
            0xA2.toByte(), 0x48, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x20, 0x43, 0x4B, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41,
            0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x41, 0x00, 0x00, 0x21, 0x00, 0x01,
        )
        java.net.DatagramSocket().use { s ->
            s.soTimeout = 600
            s.send(java.net.DatagramPacket(query, query.size, java.net.InetAddress.getByName(host), 137))
            val resp = java.net.DatagramPacket(ByteArray(512), 512)
            s.receive(resp)
            val d = resp.data
            val count = d[56].toInt()
            if (count <= 0) return ""
            for (i in 0 until count) {
                val off = 57 + i * 18
                if (off + 16 > resp.length) break
                val flags = d[off + 15].toInt()
                // Bit 7 = Gruppenname -> überspringen; wir wollen den eindeutigen Rechnernamen
                if (flags and 0x80 != 0) continue
                val name = String(d, off, 15, Charsets.US_ASCII).trim()
                if (name.isNotEmpty() && name[0].code != 0) return cleanName(name)
            }
        }
        return ""
    }

    /** Gängige Hersteller-Präfixe (OUI, erste 3 MAC-Bytes). Kleine Auswahl für Heimgeräte – kein Anspruch auf Vollständigkeit. */
    private val OUI: Map<String, String> = buildMap {
        listOf(
            // AVM (FRITZ!Box)
            "AVM" to listOf("3810D5", "C0C1C0", "E0286D", "244427", "9C C7A6", "7C2EBD", "38D547"),
            "Apple" to listOf("F0D1A9", "A4D1D2", "3C0754", "D0E140", "AC87A3", "F82793", "7CD1C3", "94E96A", "B8E856", "DCA904", "68FEF7"),
            "Samsung" to listOf("FCA13E", "5CF6DC", "8425DB", "E8508B", "D0C1B1", "C06599", "F409D8", "347E5C", "BC8CCD", "001632"),
            "Amazon" to listOf("68543D", "74C246", "F0272D", "44650D", "84D6D0", "0C47C9", "FC65DE", "B47C9C", "40B4CD"),
            "Google" to listOf("54604C", "F4F5E8", "A4778C", "6466B3", "3C5AB4", "DA A119", "F8 8FCA", "1CF29A"),
            "Sony" to listOf("FCF152", "D8D43C", "30F9ED", "AC9B0A", "104FA8"),
            "LG" to listOf("00E091", "C4366C", "A039F7", "ACF1DF", "641666"),
            "Philips/Hue" to listOf("001788", "ECB5FA", "00178A"),
            "Sonos" to listOf("B8E937", "5CAAFD", "949F3E", "347E5C"),
            "Intel" to listOf("3C7C3F", "A0A8CD", "8086F2", "94E6F7", "7C7A91"),
            "Raspberry Pi" to listOf("B827EB", "DCA632", "E45F01", "2CCF67"),
            "Espressif" to listOf("240AC4", "A020A6", "3C6105", "84F3EB", "D8A01D", "7CDFA1"),
            "AVM Repeater" to listOf("1C ED6F"),
            "Xiaomi" to listOf("286C07", "F8A45F", "64B473", "7802F8"),
            "Huawei" to listOf("002568", "48435A", "D4612E"),
            "TP-Link" to listOf("50C7BF", "EC086B", "A842A1", "003192"),
            "AVM FRITZ" to listOf("C0 25 06"),
            "Nvidia Shield" to listOf("00044B", "48B02D", "E0631D"),
            "Microsoft" to listOf("7CED8D", "582F40", "000D3A"),
        ).forEach { (name, ps) -> ps.forEach { put(it.replace(" ", "").uppercase().take(6), name) } }
    }
}
