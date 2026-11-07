package com.lumen.tv

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// =====================================================================================================
//  Spatial-Oberfläche wie im Prototyp: Kopfzeile (Uhr, Datum, Auswahl), runde Glas-Knöpfe, Status-Pille,
//  eigene App-Kacheln und das Einstellungs-Panel rechts mit Kategorien.
// =====================================================================================================

/** App-Kachel im Spatial-Look: großes abgerundetes Banner, Glas-Lichtkante, farbiger Schatten im Fokus. */
@Composable
fun SpatialAppTile(
    app: TvApp, vm: HomeViewModel, w: Dp, h: Dp, modifier: Modifier,
    onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit,
) {
    val art by produceState<Art?>(null, app.pkg, iconPackGlobal) { value = vm.art(app, iconPackGlobal) }
    var focused by remember { mutableStateOf(false) }
    val accent = art?.accent ?: DefaultAccent
    val shape = RoundedCornerShape(22.dp)
    val elev by animateDpAsState(if (focused) 24.dp else 0.dp, label = "elev")
    Surface(
        onClick = onClick,
        onLongClick = onLong,
        modifier = modifier.size(w, h)
            .then(if (elev > 0.5.dp) Modifier.shadow(elev, shape, clip = false, ambientColor = accent, spotColor = accent) else Modifier)
            .onFocusChanged { focused = it.hasFocus; if (it.hasFocus) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF14161C), focusedContainerColor = Color(0xFF14161C)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = shape),
            focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Transparent, 0.dp)),
    ) {
        val a = art
        val b = a?.banner
        if (b != null) {
            Image(b, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.45f), Color(0xFF0C0E14))))) {
                if (a != null) Image(a.icon, null, Modifier.align(Alignment.Center).size(h * 0.42f))
                Text(
                    app.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        // Glas-Lichtkante wie im Prototyp
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(0f to Color.White.copy(alpha = 0.22f), 0.45f to Color.White.copy(alpha = 0.03f), 1f to Color.Black.copy(alpha = 0.22f)),
            ),
        )
    }
}

/** Feine Linien-Symbole wie im Entwurf (SVG-Pfade im 24er-Raster). */
object LineIco {
    const val RADIO = "M3 8h18v12H3z M7 8l10-4 M15.5 14a2.5 2.5 0 1 1 0 .01 M6 12h4 M6 16h4"
    const val SEARCH = "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z M20 20l-3.5-3.5"
    const val TIMER = "M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z M12 9v4l2.5 2 M10 2h4"
    const val GRID = "M5 5h.01 M12 5h.01 M19 5h.01 M5 12h.01 M12 12h.01 M19 12h.01 M5 19h.01 M12 19h.01 M19 19h.01"
    const val SETTINGS = "M4 7h10 M18 7h2 M16 5a2 2 0 1 1 0 4 2 2 0 0 1 0-4z M4 17h4 M12 17h8 M10 15a2 2 0 1 1 0 4 2 2 0 0 1 0-4z"
    const val PHONE = "M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z M12 18h.01"
    const val POWER = "M12 3v8 M6.3 7.3a8 8 0 1 0 11.4 0"
    const val RESTART = "M4 12a8 8 0 1 0 2.5-5.8 M4 4v5h5"
    const val LOOK = "M12 3a9 9 0 1 0 0 18c1 0 1.5-.8 1.5-1.6 0-.5-.2-.9-.5-1.2-.3-.4-.5-.8-.5-1.2 0-.9.7-1.6 1.6-1.6H16a5 5 0 0 0 5-5c0-4-4-7.4-9-7.4z"
    const val ROWS = "M4 6h16 M4 12h16 M4 18h10"
    const val STANDBY = "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"
    const val REMOTE = "M8 2h8a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z M12 7h.01 M10 12h4 M10 16h4"
    const val TOOLS = "M14 7l3-3 3 3-3 3z M4 20l9-9"
    const val LOCK = "M6 11h12v10H6z M8 11V7a4 4 0 0 1 8 0v4"
    const val SYSTEM = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19 12h2 M3 12h2 M12 3v2 M12 19v2 M5.6 5.6l1.4 1.4 M17 17l1.4 1.4 M5.6 18.4L7 17 M17 7l1.4-1.4"
    const val INFO = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 11v5 M12 8h.01"
    const val DOWNLOAD = "M12 4v11 M7 10l5 5 5-5 M5 20h14"
}

/** Zeichnet ein Linien-Symbol aus einem SVG-Pfad (Strich, runde Enden) – scharf in jeder Größe. */
@Composable
fun LineIcon(d: String, color: Color, dim: Dp) {
    val path = remember(d) { androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toPath() }
    androidx.compose.foundation.Canvas(Modifier.size(dim)) {
        val k = size.minDimension / 24f
        withTransformScale(k) {
            drawPath(
                path, color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 1.9f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.withTransformScale(k: Float, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    val pivot = androidx.compose.ui.geometry.Offset.Zero
    drawContext.transform.scale(k, k, pivot)
    block()
    drawContext.transform.scale(1f / k, 1f / k, pivot)
}

/** Runder Glas-Knopf der Kopfzeile: im Fokus weiß gefüllt mit dunklem Symbol. */
@Composable
private fun GlassButton(icon: String, label: String, dot: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(44.dp).onFocusChanged { focused = it.hasFocus },
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.10f), focusedContainerColor = Color.White),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)), shape = CircleShape),
                focusedBorder = Border(BorderStroke(0.dp, Color.Transparent), shape = CircleShape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.White.copy(alpha = 0.35f), 14.dp)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LineIcon(icon, if (focused) Color(0xFF0B0D12) else Color.White, 20.dp)
            }
        }
        if (dot) Box(Modifier.align(Alignment.TopEnd).size(10.dp).background(Color(0xFFFF5F5F), CircleShape))
    }
}

/** Kleiner Balken für RAM / Speicher in der Status-Pille. */
@Composable
private fun MiniBar(frac: Float) {
    Box(Modifier.width(34.dp).height(4.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0.03f, 1f)).background(NeoViolet, RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun StatusDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).shadow(6.dp, CircleShape, ambientColor = color, spotColor = color).background(color, CircleShape))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.9f))
    }
}

/** Kopfzeile wie im Prototyp: links Uhr, Datum + Wetter, darunter die Auswahl; rechts Glas-Knöpfe und Status-Pille. */
@Composable
fun SpatialHeader(
    hero: State<HeroInfo?>, prefs: Prefs, status: State<StatusInfo?>, weather: State<WeatherInfo?>, sleepUntil: State<Long>,
    hasUpdate: Boolean,
    onRadio: () -> Unit, onSearch: () -> Unit, onTimer: () -> Unit, onQuick: () -> Unit,
    onSettings: () -> Unit, onAndroid: () -> Unit, onRestart: () -> Unit,
) {
    val ctx = LocalContext.current
    val now by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(10_000) } }
    val is24 = prefs.is24(ctx)
    Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 6.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")),
                fontSize = 58.sp, fontWeight = FontWeight.W200, letterSpacing = (-2).sp, color = Color.White,
                style = TextStyle(shadow = Shadow(Color.White.copy(alpha = 0.35f), blurRadius = 28f)),
            )
            val w = weather.value
            val date = now.format(DateTimeFormatter.ofPattern(t("EEEE · d. MMMM", "EEEE · MMMM d"), Lang.locale))
            val wx = if (prefs.weather > 0 && w != null) " · ${w.temp}° " + weatherText(w.code) else ""
            Text((date + wx).uppercase(Lang.locale), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            val h = hero.value
            if (h != null) Row(Modifier.padding(top = 22.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(h.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Keine Bedien-Hinweise („OK startet …“) – nur echte Infos wie Untertitel oder Sendung
                val hint = if (h.app != null || h.extra.contains("OK")) "" else h.extra
                if (hint.isNotEmpty()) Text(hint, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (prefs.radioOn) GlassButton(LineIco.RADIO, "Radio", onClick = onRadio)
                GlassButton(LineIco.SEARCH, t("Suche", "Search"), onClick = onSearch)
                GlassButton(LineIco.TIMER, "Timer", onClick = onTimer)
                GlassButton(LineIco.GRID, t("Schnellmenü", "Quick menu"), onClick = onQuick)
                GlassButton(LineIco.SETTINGS, t("Einstellungen", "Settings"), dot = hasUpdate, onClick = onSettings)
                GlassButton(LineIco.PHONE, "Android", onClick = onAndroid)
                if (prefs.restartBtn) GlassButton(LineIco.RESTART, t("Neustart", "Restart"), onClick = onRestart)
            }
            val s = status.value
            val left = ((sleepUntil.value - System.currentTimeMillis()) / 60_000L).toInt()
            if ((prefs.showStatus && s != null) || (sleepUntil.value > 0 && left >= 0)) Row(
                Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0x73060810))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(17.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (prefs.showStatus && s != null) {
                    StatusDot(if (s.net == "Offline") Color(0xFFFF6B6B) else Color.White, if (s.net == "WLAN") t("WLAN", "Wi-Fi") else s.net)
                    StatusDot(if (s.vpn) Color(0xFF6DFFB5) else Color.White.copy(alpha = 0.4f), if (s.vpn) "VPN" else t("kein VPN", "no VPN"))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("RAM ${100 - s.ramFreePct} %", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                        MiniBar(1f - s.ramFreePct / 100f)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("%.0f GB ${t("frei", "free")}".format(s.storageGb), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.9f))
                        MiniBar(1f - s.storageFreePct / 100f)
                    }
                }
                if (sleepUntil.value > 0 && left >= 0) Text("Timer ${left + 1} ${t("Min.", "min")}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeoCyan)
            }
        }
    }
}

// ---------------------------------------------------------------- Einstellungen: Glas-Panel rechts

private class SpatialCat(val key: String, val icon: String, val label: String)

/** Kategorie-Zeile: Symbol im Glas-Quadrat, Name, Pfeil; gewählt bzw. im Fokus in der Akzentfarbe. */
@Composable
private fun SpatialCatRow(cat: SpatialCat, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent,
            focusedContainerColor = NeoViolet.copy(alpha = 0.80f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.dp, NeoCyan), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(NeoViolet.copy(alpha = 0.45f), 18.dp)),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(34.dp).background(Color.White.copy(alpha = if (focused || selected) 0.22f else 0.08f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) { LineIcon(cat.icon, Color.White, 18.dp) }
            Text(cat.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            Text("›", fontSize = 20.sp, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

/**
 * Einstellungen im Spatial-Look: Glas-Panel am rechten Rand mit allen Bereichen. Öffnet man einen Bereich,
 * klappt das Panel nach links auf und zeigt dort dessen Einstellungen (dieselben Einträge wie bisher).
 */
@Composable
fun SpatialSettingsShell(
    page: String?, onPage: (String?) -> Unit, first: FocusRequester, title: String,
    hasUpdate: Boolean, onUpdate: () -> Unit, onInfo: () -> Unit,
    items: LazyListScope.() -> Unit,
) {
    val cats = listOf(
        SpatialCat("look", LineIco.LOOK, t("Aussehen", "Appearance")),
        SpatialCat("rows", LineIco.ROWS, t("Reihen", "Rows")),
        SpatialCat("power", LineIco.STANDBY, t("Standby", "Standby")),
        SpatialCat("remote", LineIco.REMOTE, t("Fernbedienung", "Remote")),
        SpatialCat("tools", LineIco.TOOLS, t("Werkzeuge", "Tools")),
        SpatialCat("kids", LineIco.LOCK, t("Kindersicherung", "Parental")),
        SpatialCat("system", LineIco.SYSTEM, "System"),
        SpatialCat("info", LineIco.INFO, "Info"),
    ) + if (hasUpdate) listOf(SpatialCat("update", LineIco.DOWNLOAD, t("Update verfügbar", "Update available"))) else emptyList()
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.CenterEnd).padding(18.dp).fillMaxHeight()
                .shadow(40.dp, shape, clip = false)
                .clip(shape).background(NeoPanelBrush).border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                .animateContentSize(),
        ) {
            if (page != null) {
                Column(Modifier.width(430.dp).fillMaxHeight().padding(top = 26.dp)) {
                    Text(t("EINSTELLUNGEN", "SETTINGS"), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, color = NeoCyan, modifier = Modifier.padding(start = 26.dp))
                    Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, modifier = Modifier.padding(start = 26.dp, top = 2.dp, bottom = 10.dp))
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 14.dp), content = items)
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.08f)))
            }
            Column(Modifier.width(310.dp).fillMaxHeight().padding(horizontal = 16.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.padding(start = 6.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(34.dp).background(NeoViolet, CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(11.dp).background(Color.White, CircleShape))
                    }
                    Column {
                        Text(t("Einstellungen", "Settings"), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text("Lumen Launcher $APP_VERSION", fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f))
                    }
                }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(cats) { i, cat ->
                        SpatialCatRow(cat, cat.key == page, if (i == 0 && page == null) Modifier.focusRequester(first) else Modifier) {
                            when (cat.key) {
                                "info" -> onInfo()
                                "update" -> onUpdate()
                                else -> onPage(cat.key)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Einstellungs-Zeilen wie im Prototyp

/**
 * Zeile im Spatial-Look (wird von SettingLine/PanelRow benutzt, gilt damit für ALLE Unterseiten und Dialoge):
 * ruhige Glasfläche ohne Rahmen; im Fokus Verlauf in der Akzentfarbe mit Leuchten.
 * An/Aus als kleiner Schalter, sonstige Werte als Text mit Pfeil – wie im Prototyp.
 */
@Composable
fun SpatialSettingLine(label: String, value: String, modifier: Modifier, icon: Ico? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 3.dp).fillMaxWidth().height(56.dp).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f), focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(1.dp, NeoViolet), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(NeoViolet.copy(alpha = 0.40f), 16.dp)),
    ) {
        Row(
            Modifier.fillMaxSize()
                .then(if (focused) Modifier.background(Brush.horizontalGradient(listOf(NeoViolet.copy(alpha = 0.85f), NeoViolet.copy(alpha = 0.30f)))) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Box(
                Modifier.size(32.dp).background(Color.White.copy(alpha = if (focused) 0.25f else 0.10f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { PanelIcon(icon, Modifier.size(17.dp)) }
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val on = value == t("An", "On")
            if (on || value == t("Aus", "Off")) {
                val knob by animateDpAsState(if (on) 23.dp else 3.dp, label = "knob")
                Box(Modifier.width(46.dp).height(26.dp).background(if (on) (if (focused) Color.White.copy(alpha = 0.35f) else NeoViolet) else Color.White.copy(alpha = 0.22f), RoundedCornerShape(13.dp))) {
                    Box(Modifier.padding(start = knob, top = 3.dp).size(20.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
                }
            } else if (value.isNotEmpty()) {
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("›", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
            } else {
                Text("›", fontSize = 20.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

/** Radio-Kachel im Spatial-Layout: gleiche Größe und Form wie die App-Kacheln (station = null → „Radio“-Kachel zum Suchen). */
@Composable
fun SpatialRadioTile(station: Station?, w: Dp, h: Dp, modifier: Modifier, c: LayoutCtx) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val now by Radio.now.collectAsState()
    val active = station != null && now?.uuid == station.uuid
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    val col = if (station == null) Color(0xFFFF6A00)
        else listOf(Color(0xFF1F5CFF), Color(0xFF00B4D8), Color(0xFF7B2CFF), Color(0xFF2BD4A0), Color(0xFFFF2F92), Color(0xFFFF6A00))[kotlin.math.abs(station.name.hashCode()) % 6]
    Surface(
        onClick = { if (station == null) c.onRadioSearch() else Radio.toggle(ctx, station) },
        onLongClick = { if (station != null) c.onStationMenu(station) },
        modifier = modifier.size(w, h).onFocusChanged {
            focused = it.hasFocus
            if (it.hasFocus) {
                if (station == null) c.onHero(HeroInfo("r:add", t("Radiosender suchen", "Find radio stations"), t("Tausende Sender, z. B. SWR3, SR1 – OK zum Suchen", "Thousands of stations – OK to search")))
                else c.onHero(HeroInfo("r:${station.uuid}", station.name, t("Radio · OK zum Abspielen, lang drücken für Optionen", "Radio · OK to play, long-press for options")))
            }
        },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF14161C), focusedContainerColor = Color(0xFF14161C)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (active) 2.dp else 1.dp, if (active) col else Color.White.copy(alpha = 0.10f)), shape = shape),
            focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Color.Transparent, 0.dp)),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(col.copy(alpha = 0.55f), col.copy(alpha = 0.18f), Color(0xFF0C0E14))))) {
            if (station == null) {
                Box(Modifier.align(Alignment.Center)) { LineIcon(LineIco.RADIO, Color.White, h * 0.36f) }
            } else {
                Box(Modifier.align(Alignment.Center).padding(bottom = 14.dp).size(h * 0.52f).clip(RoundedCornerShape(14.dp))) {
                    StationLogo(station, Modifier.fillMaxSize(), 18.sp, round = false)
                }
            }
            Text(
                if (station == null) t("Radio", "Radio") else (if (active) "♪ " else "") + station.name,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(0f to Color.White.copy(alpha = 0.18f), 0.45f to Color.White.copy(alpha = 0.03f), 1f to Color.Black.copy(alpha = 0.22f)),
            ),
        )
    }
}
