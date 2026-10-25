package com.lumen.tv

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaController
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

const val EXTRA_LOCKED = "locked_pkg"
const val EXTRA_IDLE = "force_idle"

@Immutable
class TvApp(
    val pkg: String, val label: String, val component: ComponentName, val intent: Intent,
    val isTv: Boolean, val installed: Long, val system: Boolean,
)

@Immutable class InputSource(val id: String, val label: String, val intent: Intent)
@Immutable class WebWidget(val id: String, val name: String, val url: String, val refreshMin: Int = 0, val size: Int = 0, val on: Boolean = true)
@Immutable class WatchItem(val title: String, val art: String?, val intent: Intent?, val progress: Float)
@Immutable class Art(val banner: ImageBitmap?, val icon: ImageBitmap, val accent: Color, val bytes: Int)
@Immutable class Usage(val count: Map<String, Int>, val last: Map<String, Long>, val slot: Map<String, Int>)
@Immutable class StatusInfo(
    val net: String, val vpn: Boolean, val ramFreePct: Int, val storageGb: Float,
    val wifiLevel: Int, val storageFreePct: Int,
)
@Stable class NowItem(val pkg: String, val title: String, val subtitle: String, val playing: Boolean, val controller: MediaController)
@Immutable class PluginRow(val id: String, val title: String, val items: List<WatchItem>)

sealed interface Wall {
    class Still(val bmp: ImageBitmap) : Wall
    class Anim(val file: File) : Wall
    class Video(val file: File) : Wall
}

enum class RowKind { WATCH, PLAYING, NEW, NOW, FREQ, INPUTS, PLUGINS, CUSTOM, ALL, RADIO, WEB }
enum class TileStyle(private val de: String, private val en: String) {
    BANNER("Banner", "Banner"), WIDE("Breit", "Wide"), COMPACT("Kompakt", "Compact"), ICON("Icons", "Icons"), ROUND("Rund", "Round"),
    POSTER("Poster (hoch)", "Poster (tall)"), CARD("Karte", "Card");
    val label: String get() = t(de, en)
}
enum class Layout(private val de: String, private val en: String) {
    SHELVES("Reihen", "Shelves"), GRID("Raster", "Grid"), SIDEBAR("Seitenleiste", "Sidebar");
    val label: String get() = t(de, en)
}
enum class Look(private val de: String, private val en: String) {
    PURE("Wallpaper pur", "Wallpaper only"), AURORA("Aurora", "Aurora"), GLASS("Glas", "Glass"), MIDNIGHT("Midnight", "Midnight"), OLED("OLED-Schwarz", "OLED black");
    val label: String get() = t(de, en)
}
enum class ActionType(private val de: String, private val en: String) {
    LUMEN("Lumen öffnen", "Open Lumen"), STANDBY("Standby", "Standby"), APP("App öffnen …", "Open app …"),
    INPUT("Eingang wechseln …", "Switch input …"), TIMER("Einschlaf-Timer +30 Min.", "Sleep timer +30 min"),
    ASSIST("Sprachsuche", "Voice search");
    val label: String get() = t(de, en)
}

inline fun <reified T : Enum<T>> T.next(): T = enumValues<T>().let { it[(ordinal + 1) % it.size] }

@Immutable
class Shortcut(val keyCode: Int, val long: Boolean, val action: ActionType, val arg: String = "", val argLabel: String = "")

fun shortcutKeys(): List<Pair<Int, String>> = listOf(
    183 to t("Rote Taste", "Red button"), 184 to t("Grüne Taste", "Green button"),
    185 to t("Gelbe Taste", "Yellow button"), 186 to t("Blaue Taste", "Blue button"),
    82 to t("Menü", "Menu"), 172 to "Guide", 165 to "Info", 178 to t("TV-Eingang", "TV input"),
    174 to t("Lesezeichen", "Bookmark"), 176 to t("Einstellungen", "Settings"),
    166 to t("Kanal +", "Channel +"), 167 to t("Kanal –", "Channel –"), 84 to t("Suche", "Search"),
)
fun keyName(code: Int) = shortcutKeys().firstOrNull { it.first == code }?.second ?: t("Taste $code", "Key $code")

@Immutable
data class RowCfg(
    val id: String, val kind: RowKind, val title: String,
    val visible: Boolean = true, val pkgs: List<String> = emptyList(),
)

val DEFAULT_ROWS = listOf(
    RowCfg("favs", RowKind.CUSTOM, "Favoriten"),
    RowCfg("watch", RowKind.WATCH, "Weiter ansehen"),
    RowCfg("playing", RowKind.PLAYING, "Läuft gerade"),
    RowCfg("new", RowKind.NEW, "Neu installiert"),
    RowCfg("now", RowKind.NOW, "Jetzt passend"),
    RowCfg("freq", RowKind.FREQ, "Häufig genutzt"),
    RowCfg("radio", RowKind.RADIO, "Radio"),
    RowCfg("web", RowKind.WEB, "Widgets"),
    RowCfg("inputs", RowKind.INPUTS, "Eingänge"),
    RowCfg("plugins", RowKind.PLUGINS, "Plugins"),
    RowCfg("all", RowKind.ALL, "Alle Apps"),
)
fun presetRows() = listOf(
    t("Favoriten", "Favorites"), t("Streaming", "Streaming"), t("Spiele", "Games"),
    t("Musik", "Music"), t("Tools", "Tools"), t("Kinder", "Kids"),
)

private val DEFAULT_ROW_EN = mapOf(
    "favs" to "Favorites", "radio" to "Radio", "web" to "Widgets",
    "watch" to "Continue watching", "playing" to "Now playing", "new" to "Recently installed",
    "now" to "Right now", "freq" to "Frequently used", "inputs" to "Inputs",
    "plugins" to "Plugins", "all" to "All apps",
)

/** Titel der Standard-Reihen werden übersetzt, selbst umbenannte/angelegte Reihen bleiben wie sie sind. */
fun RowCfg.shownTitle(): String {
    val def = DEFAULT_ROWS.firstOrNull { it.id == id } ?: return title
    return if (title == def.title) t(def.title, DEFAULT_ROW_EN[id] ?: def.title) else title
}

@Immutable
data class Prefs(
    val look: Look = Look.PURE,
    val tile: TileStyle = TileStyle.BANNER,
    val layout: Layout = Layout.SHELVES,
    val clockStyle: Int = 0,     // 0 = Digital, 1 = Minimal, 2 = Analog
    val timeFormat: Int = 0,     // 0 = 24 Std., 1 = System, 2 = 12 Std.
    val weather: Int = 2,        // 0 = Aus, 1 = Kompakt, 2 = mit Vorschau
    val weatherName: String = "",
    val weatherLat: Double = 0.0,
    val weatherLon: Double = 0.0,
    val dim: Int = 1,            // 0..3 -> 0/25/50/75 %
    val blur: Int = 0,           // 0..3
    val focusStyle: Int = 0,     // 0 Rahmen, 1 Leuchten, 2 Anheben
    val miniOverlay: Boolean = true, // kleine Uhr + Wetter oben links in anderen Apps
    val iconPack: String = "",       // Paketname des gewählten Icon-Packs, leer = App-Icons
    val tileGlass: Int = 0,      // 0..3 -> Kacheln 100/75/50/30 % deckend
    val accent: Int = 1,         // Index in ACCENTS, 0 = automatisch (wechselt mit der App), 1 = festes Blau
    val font: String = "ui",
    val scale: Int = 1,          // 0..2 -> 85/100/120 %
    val slideshowMin: Int = 15,  // 0 = aus
    val idleMin: Int = 10,       // Standby-Anzeige, 0 = aus
    val idleStyle: Int = 0,      // 0 = Uhr, 1 = Fotos
    val standbyMin: Int = 0,     // Auto-Standby des TV, 0 = aus
    val allSort: Int = 0,        // 0 = A-Z, 1 = zuletzt, 2 = häufig, 3 = eigene Reihenfolge
    val appOrder: List<String> = emptyList(), // Reihenfolge für allSort = 3
    val rowsPos: Int = 0,        // Reihen: 0 = oben (Standard), 1 = unten
    val showStatus: Boolean = true,
    val npPos: Int = 0,                      // Radio-Karte: 0 unter Uhr, 1 oben Mitte, 2 oben rechts, 3 Mitte, 4 unten Mitte, 5 unten links, 6 unten rechts
    val bootStart: Boolean = true,          // Lumen beim Systemstart öffnen
    val restartBtn: Boolean = true,         // Neustart-Knopf in der Kopfzeile
    val autostart: String = "",
    val hidden: Set<String> = emptySet(),
    val rows: List<RowCfg> = DEFAULT_ROWS,
    val shortcuts: List<Shortcut> = emptyList(),
    val radioRow: Boolean = false,          // Radio-Reihe zusätzlich auf dem Startbildschirm
    val radioOn: Boolean = true,            // Radio (Reihe + Knopf) anzeigen
    val webWidgets: List<WebWidget> = emptyList(), // Web-Widgets (Reihe "Widgets")
    val stations: List<Station> = emptyList(), // gespeicherte Radiosender
    val heroMode: Int = 1,       // Text zur fokussierten App: 0 = aus, 1 = klein, 2 = groß
    val tempF: Boolean = false,  // Wetter in °F/mph statt °C/km/h
    val barPos: Int = 0,         // Kopfzeile (Uhr, Wetter, Status): 0 = oben, 1 = unten
    val menuFont: String = "",   // eigene Schrift für Menüs/Kopfzeile, "" = wie Schriftart
    val menuSize: Int = 1,       // 0..2 -> 85/100/120 % Schriftgröße im Einstellungs-Menü
    val lang: Int = 0,           // 0 = Deutsch, 1 = English, 2 = System
    val dotOn: Boolean = false,  // VPN-Dot (Overlay) aktiv
    val dotSize: Int = 12,       // 8..20 dp
    val dotInterval: Int = 30,   // Sekunden zwischen Einblendungen
    val dotDuration: Int = 2,    // Sekunden sichtbar
    val dotPos: Int = 0,         // 0 oben rechts, 1 oben links, 2 unten links, 3 unten rechts
    val dotAuto: Boolean = true, // nach dem Booten automatisch starten
)

val Prefs.scaleF: Float get() = listOf(0.85f, 1f, 1.2f)[scale.coerceIn(0, 2)]

fun Prefs.toJson(): JSONObject = JSONObject().apply {
    put("lookV", 2); put("focus", focusStyle); put("glass", tileGlass); put("iconPack", iconPack); put("mini", miniOverlay)
    put("look", look.name); put("tile", tile.name); put("layout", layout.name)
    put("clock", clockStyle); put("timeFmt", timeFormat); put("weather", weather)
    put("wName", weatherName); put("wLat", weatherLat); put("wLon", weatherLon)
    put("dim", dim); put("blur", blur); put("accent", accent); put("accentV", 2); put("font", font); put("scale", scale)
    put("slide", slideshowMin); put("idle", idleMin); put("idleStyle", idleStyle)
    put("standby", standbyMin); put("allSort", allSort); put("appOrder", JSONArray(appOrder)); put("rowsPos", rowsPos)
    put("status", showStatus); put("restartBtn2", restartBtn); put("bootStart", bootStart); put("npPos", npPos); put("auto", autostart)
    put("radioOn", radioOn); put("radioRow", radioRow)
    put("webs", JSONArray().apply { webWidgets.forEach { w -> put(JSONObject().apply { put("id", w.id); put("name", w.name); put("url", w.url); put("min", w.refreshMin); put("size", w.size); put("on", w.on) }) } })
    put("stations", JSONArray().apply {
        stations.forEach { s -> put(JSONObject().apply { put("id", s.uuid); put("name", s.name); put("url", s.url); put("icon", s.favicon); put("info", s.info); put("home", s.home) }) }
    })
    put("heroMode", heroMode); put("tempF", tempF); put("barPos", barPos); put("menuFont", menuFont); put("menuSize", menuSize); put("lang", lang); put("dotOn", dotOn); put("dotSize", dotSize); put("dotInt", dotInterval)
    put("dotDur", dotDuration); put("dotPos", dotPos); put("dotAuto", dotAuto)
    put("hidden", JSONArray(hidden.toList()))
    put("rows", JSONArray().apply {
        rows.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id); put("kind", r.kind.name); put("title", r.title)
                put("vis", r.visible); put("pkgs", JSONArray(r.pkgs))
            })
        }
    })
    put("shortcuts", JSONArray().apply {
        shortcuts.forEach { s ->
            put(JSONObject().apply {
                put("key", s.keyCode); put("long", s.long); put("action", s.action.name)
                put("arg", s.arg); put("label", s.argLabel)
            })
        }
    })
}

fun prefsFromJson(o: JSONObject): Prefs {
    val d = Prefs()
    fun strs(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }
    val parsed = o.optJSONArray("rows")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val r = arr.getJSONObject(i)
                RowCfg(
                    r.getString("id"), RowKind.valueOf(r.getString("kind")), r.getString("title"),
                    r.optBoolean("vis", true), strs(r.optJSONArray("pkgs")),
                )
            }.getOrNull()
        }
    }?.takeIf { it.isNotEmpty() } ?: d.rows
    // Neue Standard-Reihen (aus Updates) an der passenden Stelle ergänzen.
    val rows = parsed.toMutableList()
    DEFAULT_ROWS.forEachIndexed { di, def ->
        if (rows.none { it.id == def.id }) {
            val nextId = DEFAULT_ROWS.drop(di + 1).firstOrNull { n -> rows.any { it.id == n.id } }?.id
            val at = rows.indexOfFirst { it.id == nextId }.let { if (it < 0) rows.size else it }
            rows.add(at, def)
        }
    }
    val shortcuts = o.optJSONArray("shortcuts")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            runCatching {
                val s = a.getJSONObject(i)
                Shortcut(s.getInt("key"), s.getBoolean("long"), ActionType.valueOf(s.getString("action")), s.optString("arg", ""), s.optString("label", ""))
            }.getOrNull()
        }
    } ?: emptyList()
    return Prefs(
        look = if (o.optInt("lookV", 0) < 2) d.look else runCatching { Look.valueOf(o.getString("look")) }.getOrDefault(d.look),
        focusStyle = o.optInt("focus", d.focusStyle).coerceIn(0, 2),
        tileGlass = o.optInt("glass", d.tileGlass).coerceIn(0, 3),
        iconPack = o.optString("iconPack", ""),
        miniOverlay = o.optBoolean("mini", d.miniOverlay),
        tile = runCatching { TileStyle.valueOf(o.getString("tile")) }.getOrDefault(d.tile),
        layout = runCatching { Layout.valueOf(o.getString("layout")) }.getOrDefault(d.layout),
        clockStyle = o.optInt("clock", d.clockStyle).coerceIn(0, 2),
        timeFormat = o.optInt("timeFmt", d.timeFormat).coerceIn(0, 2),
        weather = o.optInt("weather", d.weather).coerceIn(0, 2),
        weatherName = o.optString("wName", ""),
        weatherLat = o.optDouble("wLat", 0.0),
        weatherLon = o.optDouble("wLon", 0.0),
        dim = (if (o.optInt("lookV", 0) < 2) d.dim else o.optInt("dim", d.dim)).coerceIn(0, 3),
        blur = o.optInt("blur", d.blur).coerceIn(0, 3),
        accent = (if (o.optInt("accentV", 0) < 2) d.accent else o.optInt("accent", d.accent)).coerceIn(0, ACCENTS.lastIndex),
        font = o.optString("font", "ui").ifEmpty { "ui" },
        scale = o.optInt("scale", d.scale).coerceIn(0, 2),
        slideshowMin = o.optInt("slide", d.slideshowMin),
        idleMin = o.optInt("idle", d.idleMin),
        idleStyle = o.optInt("idleStyle", d.idleStyle).coerceIn(0, 1),
        standbyMin = o.optInt("standby", d.standbyMin),
        allSort = o.optInt("allSort", d.allSort).coerceIn(0, 3),
        appOrder = strs(o.optJSONArray("appOrder")),
        rowsPos = o.optInt("rowsPos", d.rowsPos).coerceIn(0, 1),
        showStatus = o.optBoolean("status", d.showStatus),
        restartBtn = o.optBoolean("restartBtn2", d.restartBtn),
        bootStart = o.optBoolean("bootStart", d.bootStart),
        npPos = o.optInt("npPos", d.npPos).coerceIn(0, 6),
        autostart = o.optString("auto", ""),
        hidden = strs(o.optJSONArray("hidden")).toSet(),
        rows = rows,
        shortcuts = shortcuts,
        radioOn = o.optBoolean("radioOn", d.radioOn),
        radioRow = o.optBoolean("radioRow", d.radioRow),
        webWidgets = o.optJSONArray("webs")?.let { a ->
            (0 until a.length()).mapNotNull { i -> runCatching { a.getJSONObject(i).let { WebWidget(it.getString("id"), it.getString("name"), it.getString("url"), it.optInt("min", 0), it.optInt("size", 0).coerceIn(0, 2), it.optBoolean("on", true)) } }.getOrNull() }
        } ?: emptyList(),
        stations = o.optJSONArray("stations")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                runCatching { a.getJSONObject(i).let { Station(it.getString("id"), it.getString("name"), it.getString("url"), it.optString("icon"), it.optString("info"), it.optString("home")) } }.getOrNull()
            }
        } ?: emptyList(),
        heroMode = o.optInt("heroMode", d.heroMode).coerceIn(0, 2),
        tempF = o.optBoolean("tempF", d.tempF),
        barPos = o.optInt("barPos", d.barPos).coerceIn(0, 1),
        menuFont = o.optString("menuFont", ""),
        menuSize = o.optInt("menuSize", d.menuSize).coerceIn(0, 2),
        lang = o.optInt("lang", d.lang).coerceIn(0, 2),
        dotOn = o.optBoolean("dotOn", d.dotOn),
        dotSize = o.optInt("dotSize", d.dotSize).coerceIn(8, 20),
        dotInterval = o.optInt("dotInt", d.dotInterval).coerceIn(5, 3600),
        dotDuration = o.optInt("dotDur", d.dotDuration).coerceIn(1, 60),
        dotPos = o.optInt("dotPos", d.dotPos).coerceIn(0, 3),
        dotAuto = o.optBoolean("dotAuto", d.dotAuto),
    )
}

/** Nur das Aussehen – das, was ein "Theme-Paket" enthält. */
fun Prefs.styleJson(): JSONObject = JSONObject().apply {
    put("look", look.name); put("tile", tile.name); put("layout", layout.name); put("clock", clockStyle)
    put("dim", dim); put("blur", blur)
    put("accent", accent); put("font", font); put("scale", scale); put("idleStyle", idleStyle)
}

fun Prefs.withStyle(o: JSONObject): Prefs = copy(
    look = runCatching { Look.valueOf(o.getString("look")) }.getOrDefault(look),
    tile = runCatching { TileStyle.valueOf(o.getString("tile")) }.getOrDefault(tile),
    layout = runCatching { Layout.valueOf(o.getString("layout")) }.getOrDefault(layout),
    clockStyle = o.optInt("clock", clockStyle).coerceIn(0, 2),
    dim = o.optInt("dim", dim).coerceIn(0, 3),
    blur = o.optInt("blur", blur).coerceIn(0, 3),
    accent = o.optInt("accent", accent).coerceIn(0, ACCENTS.lastIndex),
    font = o.optString("font", font),
    scale = o.optInt("scale", scale).coerceIn(0, 2),
    idleStyle = o.optInt("idleStyle", idleStyle).coerceIn(0, 1),
)

sealed interface RowUi { val id: String; val title: String }
@Immutable class AppRowUi(override val id: String, override val title: String, val apps: List<TvApp>, val custom: Boolean) : RowUi
@Immutable class InputRowUi(override val id: String, override val title: String, val inputs: List<InputSource>) : RowUi
@Immutable class WatchRowUi(override val id: String, override val title: String, val items: List<WatchItem>) : RowUi
@Immutable class NowRowUi(override val id: String, override val title: String, val items: List<NowItem>) : RowUi
@Immutable class WebRowUi(override val id: String, override val title: String, val widgets: List<WebWidget>) : RowUi
@Immutable class RadioRowUi(override val id: String, override val title: String, val stations: List<Station>) : RowUi
@Immutable class HomeState(val rows: List<RowUi> = emptyList())

@Immutable class HeroInfo(val key: String, val title: String, val extra: String = "", val app: TvApp? = null)

class MenuItemSpec(val label: String, val action: () -> Unit)
class MenuSpec(val title: String, val items: List<MenuItemSpec>)
class PinRequest(val title: String, val set: Boolean, val onOk: () -> Unit)
