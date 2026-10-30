package com.lumen.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
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

/** Fokus-Stil der Kacheln (0 Rahmen, 1 Leuchten, 2 Anheben); wird vom Startbildschirm gesetzt. */
var focusStyleGlobal by androidx.compose.runtime.mutableIntStateOf(0)
/** Gewähltes Icon-Pack (Paketname, leer = keins). */
var iconPackGlobal by androidx.compose.runtime.mutableStateOf("")
/** Deckkraft der App-Kacheln (1 = voll, kleiner = durchsichtiger). */
var tileAlphaGlobal by androidx.compose.runtime.mutableFloatStateOf(1f)

/**
 * Einheitliche fokussierbare Karte. Fokus-Stile:
 *  0 = Rahmen: klarer weißer Ring, leicht größer, weicher Schatten
 *  1 = Neon: Farbverlaufs-Ring mit Leuchten in der App-Farbe
 *  2 = Anheben (3D): Kachel steigt sichtbar nach oben, wird deutlich größer, tiefer Schatten
 *  3 = Glas: helles Milchglas mit Lichtkante oben
 *  4 = Unterstrich: Kachel bleibt ruhig, darunter ein farbiger Balken (wie bei Streaming-Apps)
 * Kein Wackeln, keine Farbwechsel im Hintergrund.
 */
@Composable
fun FocusCard(
    w: Dp, h: Dp, glow: Color, modifier: Modifier = Modifier,
    onFocus: () -> Unit, onClick: () -> Unit, onLong: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    bg: Float = tileAlphaGlobal,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val style = focusStyleGlobal
    val lift by androidx.compose.animation.core.animateDpAsState(if (focused && style == 2) (-12).dp else 0.dp, label = "lift")
    Surface(
        onClick = onClick,
        onLongClick = onLong,
        modifier = modifier.size(w, h)
            .then(if (style == 2) Modifier.graphicsLayer { translationY = lift.toPx() } else Modifier)
            .onFocusChanged { focused = it.hasFocus; if (it.hasFocus) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f * bg),
            focusedContainerColor = Color.White.copy(alpha = (if (style == 3) 0.26f else 0.14f) * bg.coerceAtLeast(0.6f)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = when (style) { 1 -> 1.07f; 2 -> 1.14f; 3 -> 1.05f; 4 -> 1.02f; else -> 1.06f }),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = shape),
            focusedBorder = when (style) {
                1 -> Border(BorderStroke(3.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(NeoCyan, NeoViolet, NeoYellow))), shape = shape)
                2 -> Border(BorderStroke(2.5.dp, Color.White), shape = shape)
                3 -> Border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f)), shape = shape)
                4 -> Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), shape = shape)
                else -> Border(BorderStroke(3.dp, Color.White), shape = shape)
            },
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = when (style) {
            1 -> Glow(elevationColor = glow.copy(alpha = 0.85f), elevation = 26.dp)
            2 -> Glow(elevationColor = Color.Black.copy(alpha = 0.9f), elevation = 40.dp)
            3 -> Glow(elevationColor = Color.White.copy(alpha = 0.30f), elevation = 16.dp)
            4 -> Glow(elevationColor = Color.Transparent, elevation = 0.dp)
            else -> Glow(elevationColor = Color.Black.copy(alpha = 0.55f), elevation = 18.dp)
        }),
    ) {
        content()
        if (focused) when (style) {
            4 -> Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(NeoYellow))
            3 -> Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.White.copy(alpha = 0.32f), 0.18f to Color.White.copy(alpha = 0.10f), 1f to Color.Transparent),
                ),
            )
            else -> Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.linearGradient(0f to Color.White.copy(alpha = 0.20f), 0.45f to Color.White.copy(alpha = 0.04f), 1f to Color.Transparent),
                ),
            )
        }
    }
}

/** Neon-Glas-Pille: ruhig im Glas, im Fokus gelber Ring; "selected" = aktive Auswahl (Cyan). */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) NeoCyan else NeoGlass,
            focusedContainerColor = if (selected) NeoCyan else NeoGlassHi,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (selected) Color.Transparent else NeoLine), shape = shape),
            focusedBorder = Border(BorderStroke(2.5.dp, NeoYellow), shape = shape),
        ),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color(0xFF071318) else Color.White, maxLines = 1)
        }
    }
}

/** Einstellungs-Zeile als Glaskarte; der Wert steht als kleine Cyan-Kapsel rechts. */
@Composable
fun SettingLine(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().height(52.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NeoCard,
            focusedContainerColor = NeoGlassHi,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, NeoLine), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, NeoYellow), shape = shape),
        ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            val on = value == t("An", "On")
            if (on || value == t("Aus", "Off")) {
                Spacer(Modifier.width(16.dp))
                NeoSwitch(on)
            } else if (value.isNotEmpty()) {
                // Mehrfach-Werte als Kapsel mit Pfeilen – OK schaltet weiter
                Spacer(Modifier.width(16.dp))
                Row(
                    Modifier.background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(12.dp)).border(1.dp, NeoCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(value, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("›", fontSize = 16.sp, color = NeoCyan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Chip(text: String) {
    Box(Modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

/** Netflix-artiger Rund-Button nur mit Symbol; beim Fokus klappt die Beschriftung aus. */
@Composable
fun IconPill(icon: Ico, label: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) Color(0xFF1DB954).copy(alpha = 0.35f) else Color(0x5C0E1424),
            focusedContainerColor = NeoYellow,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, NeoLine), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelIcon(icon, Modifier.size(20.dp), if (focused) Color(0xFF14161C) else Color.White)
            if (focused) Text(label, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color(0xFF14161C), maxLines = 1)
        }
    }
}

/** Seitenabstand der Startseite (Uhr, Reihen, Statusleiste). */
val SIDE_PAD = 26.dp

// ---------------------------------------------------------------- Neo-Glas-Farben (Einstellungen, Radio, Menüs)
class MenuTheme(val de: String, val en: String, val top: Long, val mid: Long, val bottom: Long, val accent: Long, val focus: Long, val accent2: Long) {
    val label: String get() = t(de, en)
}
/** Farbvarianten für Einstellungen, Menüs und Radio. */
val MENU_THEMES = listOf(
    MenuTheme("Nachtblau", "Night blue", 0xF21A2140, 0xF2121731, 0xF20D1124, 0xFF4FE3F0, 0xFFFFE14D, 0xFF8E5CFF),
    MenuTheme("Graphit", "Graphite", 0xF2303338, 0xF2232529, 0xF2181A1D, 0xFF9AD0FF, 0xFFFFFFFF, 0xFF6B7CFF),
    MenuTheme("Mokka & Weiß", "Mocha & white", 0xF2603E2B, 0xF2472D1F, 0xF2301E14, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFC8A27A),
    MenuTheme("Violett", "Violet", 0xF2382065, 0xF2241544, 0xF2160D2C, 0xFFFF7AC6, 0xFFFFE14D, 0xFF8E5CFF),
    MenuTheme("Smaragd", "Emerald", 0xF20F3D35, 0xF20B2B26, 0xF2071C19, 0xFF5CF2B5, 0xFFFFE14D, 0xFF2FB8FF),
    MenuTheme("Bordeaux", "Bordeaux", 0xF25C1A2A, 0xF240121E, 0xF22A0B13, 0xFFFFC6D0, 0xFFFFE14D, 0xFFFF6B8B),
    MenuTheme("Mitternacht", "Midnight", 0xF20B0B0F, 0xF2070709, 0xF2000000, 0xFF4FE3F0, 0xFFFFE14D, 0xFF8E5CFF),
    // Platzhalter: wird zur Laufzeit aus den Farben des aktuellen Wallpapers berechnet
    MenuTheme("Aus dem Wallpaper", "From wallpaper", 0xF21A2140, 0xF2121731, 0xF20D1124, 0xFF4FE3F0, 0xFFFFE14D, 0xFF8E5CFF),
)
const val THEME_FROM_WALL = 7

/** Farben des aktuellen Wallpapers (lebendig, dunkel), gesetzt vom Startbildschirm. */
var wallColorsGlobal by androidx.compose.runtime.mutableStateOf<Pair<Int, Int>?>(null)

private fun mix(a: Int, b: Int, f: Float): Long {
    fun ch(x: Int, s: Int) = (x shr s) and 0xFF
    val r = (ch(a, 16) * (1 - f) + ch(b, 16) * f).toInt(); val g = (ch(a, 8) * (1 - f) + ch(b, 8) * f).toInt(); val bl = (ch(a, 0) * (1 - f) + ch(b, 0) * f).toInt()
    return (0xF2L shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or bl.toLong()
}
/** Gewählte Menüfarbe (wird vom Startbildschirm aus den Einstellungen gesetzt). */
var menuThemeGlobal by androidx.compose.runtime.mutableIntStateOf(0)
private val curTheme: MenuTheme
    get() {
        val i = menuThemeGlobal.coerceIn(0, MENU_THEMES.lastIndex)
        val wc = wallColorsGlobal
        if (i != THEME_FROM_WALL || wc == null) return MENU_THEMES[i]
        val (vib, dark) = wc
        val black = 0xFF05060A.toInt()
        // Panel: dunkle Wallpaper-Farbe stark abgedunkelt; Akzent: lebendige Farbe aufgehellt
        val accent = (0xFFL shl 24) or (mix(vib, 0xFFFFFFFF.toInt(), 0.25f) and 0xFFFFFFL)
        return MenuTheme("", "", mix(dark, black, 0.55f), mix(dark, black, 0.70f), mix(dark, black, 0.82f), accent, 0xFFFFE14D, (0xFFL shl 24) or (mix(vib, black, 0.2f) and 0xFFFFFFL))
    }

/** Akzent (Werte, aktive Auswahl). */
val NeoCyan: Color get() = Color(curTheme.accent)
/** Fokus-Ring. */
val NeoYellow: Color get() = Color(curTheme.focus)
/** Zweiter Akzent (Verläufe). */
val NeoViolet: Color get() = Color(curTheme.accent2)
val NeoGlass = Color(0x1FFFFFFF)
val NeoGlassHi = Color(0x33FFFFFF)
val NeoCard = Color(0x12FFFFFF)
val NeoLine = Color(0x26FFFFFF)
/** Hintergrund der schwebenden Panels. */
val NeoPanelBrush: androidx.compose.ui.graphics.Brush
    get() = curTheme.let { th -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(th.top), Color(th.mid), Color(th.bottom))) }

fun focusStyleName(i: Int): String = when (i) {
    1 -> t("Neon-Leuchten", "Neon glow"); 2 -> t("Anheben (3D)", "Lift (3D)"); 3 -> t("Glas", "Glass"); 4 -> t("Unterstrich", "Underline")
    else -> t("Rahmen", "Frame")
}

/** Moderner Schalter: Verlaufs-Spur, weißer Knopf mit Schatten und Häkchen, gleitet weich hin und her. */
@Composable
fun NeoSwitch(on: Boolean) {
    val x by androidx.compose.animation.core.animateDpAsState(if (on) 26.dp else 0.dp, label = "knob")
    Box(
        Modifier.size(58.dp, 32.dp)
            .background(
                if (on) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(NeoViolet, NeoCyan))
                else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.35f))),
                RoundedCornerShape(16.dp),
            )
            .border(1.dp, if (on) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .padding(3.dp),
    ) {
        Box(
            Modifier.offset(x = x).size(26.dp)
                .graphicsLayer { shadowElevation = 6f; shape = androidx.compose.foundation.shape.CircleShape; clip = true }
                .background(if (on) Color.White else Color(0xFFB8BCC8), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B2140))
        }
    }
}

/** Fokussierbare Listenzeile: dezent, im Fokus gelber Rand – damit man mit der Fernbedienung durch lange Listen scrollen kann. */
@Composable
fun FocusRow(modifier: Modifier = Modifier, highlight: Boolean = false, onClick: () -> Unit = {}, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlight) NeoCyan.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.05f),
            focusedContainerColor = if (highlight) NeoCyan.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.14f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, NeoYellow), shape = shape)),
    ) { content() }
}
