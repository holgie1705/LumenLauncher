package com.lumen.tv

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

private fun startSettings(ctx: Context, i: Intent): Boolean {
    // Die Home-Übernahme soll die Einstellungen nicht sofort wieder wegschalten.
    runCatching { ctx.getSharedPreferences("lumen", Context.MODE_PRIVATE).edit().putLong("graceUntil", System.currentTimeMillis() + 120_000).apply() }
    return runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

/** Öffnet die Android-Systemeinstellungen (oder eine bestimmte Unterseite). */
fun openAndroidSettings(ctx: Context, action: String = Settings.ACTION_SETTINGS): Boolean {
    if (startSettings(ctx, Intent(action))) return true
    if (action == Settings.ACTION_APPLICATION_SETTINGS &&
        startSettings(ctx, Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS))) return true
    return false
}

/**
 * Öffnet eine Unterseite der Android-Einstellungen. Viele TV-Boxen haben nicht alle Unterseiten –
 * dann landet man in den Haupt-Einstellungen. Rückgabe: Hinweistext (oder null, wenn alles geklappt hat).
 */
fun openSettingsPage(ctx: Context, action: String): String? {
    if (openAndroidSettings(ctx, action)) return null
    if (action != Settings.ACTION_SETTINGS && openAndroidSettings(ctx)) {
        return t("Diese Unterseite gibt es auf dieser Box nicht – ich habe die Android-Einstellungen geöffnet.",
            "This page does not exist on this box – opened the Android settings instead.")
    }
    for (pkg in listOf("com.android.tv.settings", "com.android.settings")) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: continue
        if (startSettings(ctx, i)) return t("Android-Einstellungen geöffnet.", "Opened the Android settings.")
    }
    return t("Die Android-Einstellungen konnten nicht geöffnet werden.", "Could not open the Android settings.")
}

enum class Ico { GRID, LIST, GEAR, SCREEN, WIFI, SOUND, INFO, DOT, GLOBE, POWER, REMOTE, LOCK, BRUSH, PHONE, BACK, SEARCH, TIMER, RADIO, RESTART, DOWNLOAD, GAUGE }

/** Kleine, selbst gezeichnete Symbole – keine zusätzliche Icon-Bibliothek, kein zusätzlicher Speicher. */
@Composable
fun PanelIcon(kind: Ico, modifier: Modifier = Modifier.size(22.dp), color: Color = Color.White) {
    Canvas(modifier) {
        val s = size.width / 24f
        val sw = Stroke(width = 2f * s, cap = StrokeCap.Round)
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, p(x1, y1), p(x2, y2), 2f * s, StrokeCap.Round)
        when (kind) {
            Ico.GRID -> for (i in 0..2) for (j in 0..2)
                drawRoundRect(color, p(3f + i * 7f, 3f + j * 7f), Size(4.5f * s, 4.5f * s), CornerRadius(1f * s))
            Ico.LIST -> for (i in 0..2) {
                val y = 6f + i * 6f
                drawCircle(color, 1.4f * s, p(4f, y)); line(9f, y, 21f, y)
            }
            Ico.GEAR -> {
                drawCircle(color, 7f * s, p(12f, 12f), style = Stroke(width = 4f * s))
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    drawLine(color, p(12f + (8.5f * cos(a)).toFloat(), 12f + (8.5f * sin(a)).toFloat()),
                        p(12f + (11f * cos(a)).toFloat(), 12f + (11f * sin(a)).toFloat()), 4f * s, StrokeCap.Butt)
                }
            }
            Ico.SCREEN -> {
                drawRoundRect(color, p(3f, 4f), Size(18f * s, 12f * s), CornerRadius(2f * s), style = sw)
                line(12f, 16f, 12f, 20f); line(8f, 20f, 16f, 20f)
            }
            Ico.WIFI -> {
                listOf(5f, 9f, 13f).forEach { r ->
                    drawArc(color, 225f, 90f, false, p(12f - r, 19f - r), Size(2 * r * s, 2 * r * s), style = sw)
                }
                drawCircle(color, 1.5f * s, p(12f, 19f))
            }
            Ico.SOUND -> {
                val path = Path().apply {
                    moveTo(4f * s, 9f * s); lineTo(8f * s, 9f * s); lineTo(13f * s, 5f * s)
                    lineTo(13f * s, 19f * s); lineTo(8f * s, 15f * s); lineTo(4f * s, 15f * s); close()
                }
                drawPath(path, color, style = Fill)
                drawArc(color, -50f, 100f, false, p(8f, 7f), Size(10f * s, 10f * s), style = sw)
            }
            Ico.INFO -> {
                drawCircle(color, 9f * s, p(12f, 12f), style = sw)
                line(12f, 11f, 12f, 17f); drawCircle(color, 1.3f * s, p(12f, 7.5f))
            }
            Ico.DOT -> drawCircle(Color(0xFF35E07F), 5.5f * s, p(12f, 12f))
            Ico.GLOBE -> {
                drawCircle(color, 9f * s, p(12f, 12f), style = sw)
                drawOval(color, p(8f, 3f), Size(8f * s, 18f * s), style = Stroke(1.6f * s))
                line(3f, 12f, 21f, 12f)
            }
            Ico.POWER -> {
                drawArc(color, -50f, 280f, false, p(5f, 5f), Size(14f * s, 14f * s), style = sw)
                line(12f, 3f, 12f, 11f)
            }
            Ico.REMOTE -> {
                drawRoundRect(color, p(7f, 2f), Size(10f * s, 20f * s), CornerRadius(3f * s), style = sw)
                drawCircle(color, 2.2f * s, p(12f, 8f), style = Stroke(1.6f * s))
                drawCircle(color, 1f * s, p(12f, 14f)); drawCircle(color, 1f * s, p(12f, 18f))
            }
            Ico.LOCK -> {
                drawRoundRect(color, p(5f, 11f), Size(14f * s, 10f * s), CornerRadius(2f * s))
                drawArc(color, 180f, 180f, false, p(8f, 3f), Size(8f * s, 10f * s), style = sw)
            }
            Ico.BRUSH -> {
                drawCircle(color, 9f * s, p(12f, 12f), style = sw)
                drawCircle(color, 1.5f * s, p(8f, 10f)); drawCircle(color, 1.5f * s, p(12f, 7f)); drawCircle(color, 1.5f * s, p(16f, 10f))
            }
            Ico.PHONE -> {
                drawRoundRect(color, p(7f, 2.5f), Size(10f * s, 19f * s), CornerRadius(2.5f * s), style = sw)
                drawCircle(color, 1.1f * s, p(12f, 18.5f))
            }
            Ico.BACK -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
            Ico.RESTART -> {
                drawArc(color, -60f, 300f, false, p(5f, 6f), Size(14f * s, 14f * s), style = sw)
                line(12f, 2.5f, 12f, 9f)
            }
            Ico.DOWNLOAD -> { line(12f, 3f, 12f, 15f); line(6.5f, 10f, 12f, 15.5f); line(17.5f, 10f, 12f, 15.5f); line(4f, 20f, 20f, 20f) }
            Ico.GAUGE -> {
                drawArc(color, 160f, 220f, false, p(3f, 5f), Size(18f * s, 18f * s), style = sw)
                line(12f, 14f, 16.5f, 8.5f); drawCircle(color, 1.8f * s, p(12f, 14f))
            }
            Ico.SEARCH -> { drawCircle(color, 6.5f * s, p(10f, 10f), style = sw); line(15f, 15f, 21f, 21f) }
            Ico.TIMER -> {
                drawCircle(color, 8f * s, p(12f, 13f), style = sw)
                line(12f, 13f, 12f, 8f); line(9f, 2.5f, 15f, 2.5f)
            }
            Ico.RADIO -> {
                drawRoundRect(color, p(3f, 8f), Size(18f * s, 12f * s), CornerRadius(2.5f * s), style = sw)
                line(7f, 8f, 17f, 3f); drawCircle(color, 2.6f * s, p(8.5f, 14f), style = Stroke(1.6f * s))
                line(14f, 12.5f, 18f, 12.5f); line(14f, 16f, 18f, 16f)
            }
        }
    }
}

/** Eigenes Logo: leuchtende Kugel mit Ring + Schriftzug. Wird vektoriell gezeichnet. */
@Composable
fun LumenLogo(modifier: Modifier = Modifier, big: Boolean = true) {
    val icon = if (big) 52.dp else 34.dp
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Canvas(Modifier.size(icon)) {
            val c = center
            val r = size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(Color(0xFFB4C3FF), Color(0xFF4C6FFF), Color(0x004C6FFF)), c, r), r)
            drawCircle(Color.White, r * 0.26f, c)
            drawArc(
                Color.White.copy(alpha = 0.9f), 205f, 250f, false,
                Offset(c.x - r * 0.6f, c.y - r * 0.6f), Size(r * 1.2f, r * 1.2f),
                style = Stroke(r * 0.09f, cap = StrokeCap.Round),
            )
        }
        Column {
            Text("LUMEN", fontSize = if (big) 30.sp else 20.sp, fontWeight = FontWeight.Bold, letterSpacing = if (big) 5.sp else 3.sp, color = Color.White, maxLines = 1)
            Text("launcher", fontSize = if (big) 13.sp else 10.sp, letterSpacing = 3.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

val PanelBody = Color(0xF2141A33)
val PanelBand = Color(0xFF2B2C31)
val PanelFocus = Color(0x38FFFFFF)

/** Akzentfarbe je Symbol – bunte Icon-Kreise wie in modernen TV-Oberflächen. */
fun icoTint(i: Ico): Color = when (i) {
    Ico.BRUSH -> Color(0xFFFF7AC6); Ico.POWER -> Color(0xFFFFB648); Ico.LIST, Ico.GRID -> Color(0xFF7C9CFF)
    Ico.REMOTE -> Color(0xFF59E3A7); Ico.LOCK -> Color(0xFFFF6B6B); Ico.GEAR -> NeoCyan; Ico.GLOBE -> Color(0xFF66C7FF)
    Ico.DOT -> Color(0xFF7CE0A3); Ico.INFO -> NeoCyan; Ico.PHONE -> Color(0xFFB794FF); Ico.WIFI -> Color(0xFF66C7FF)
    Ico.SOUND -> Color(0xFFFFD36B); Ico.RADIO -> Color(0xFFFF8A5B); Ico.BACK -> Color.White; Ico.GAUGE -> Color(0xFFFF5FA2); Ico.TIMER -> Color(0xFFB57BFF)
    else -> NeoCyan
}

/** Kopf des schwebenden Panels: Zahnrad im Cyan-Kreis, Titel, Untertitel, Datum. */
@Composable
fun PanelHeader(title: String, subtitle: String = "", icon: Ico = Ico.GEAR, badge: String = "") {
    val tint = if (icon == Ico.GEAR) NeoCyan else icoTint(icon)
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(54.dp).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.18f))), androidx.compose.foundation.shape.CircleShape)
                .border(1.5.dp, tint.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) { PanelIcon(icon, Modifier.size(26.dp), Color.White) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge.isNotEmpty()) Box(
            Modifier.background(NeoCyan.copy(alpha = 0.16f), RoundedCornerShape(12.dp)).border(1.dp, NeoCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) { Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeoCyan) }
    }
}

@Composable
fun PanelSection(title: String) {
    // Gleiche Optik wie die Abschnitts-Überschriften in den Dialogen (SheetSection).
    Text(title.uppercase(Lang.locale), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = NeoCyan, modifier = Modifier.padding(start = 26.dp, top = 14.dp, bottom = 2.dp))
}

/** Zeile der Hauptliste: Glaskarte, buntes Symbol im Kreis, im Fokus gelber Ring. */
@Composable
fun PanelRow(icon: Ico, label: String, value: String = "", modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val tint = icoTint(icon)
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 14.dp, vertical = 3.dp).fillMaxWidth().height(58.dp),
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
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.18f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                PanelIcon(icon, Modifier.size(19.dp), tint)
            }
            Spacer(Modifier.width(14.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Box(Modifier.background(NeoCyan.copy(alpha = 0.16f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeoCyan, maxLines = 1)
                }
            }
        }
    }
}

/** Zwei Kacheln nebeneinander. */
@Composable
fun TileRow(a: @Composable (Modifier) -> Unit, b: (@Composable (Modifier) -> Unit)? = null) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 5.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        a(Modifier.weight(1f)); if (b != null) b(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
    }
}

/** Große Einstellungs-Kachel: Farbverlauf in der Kategorie-Farbe, Symbol im Glaskreis, Titel und kleine Info. Im Fokus: hebt sich, gelber Ring, Glanz. */
@Composable
fun PanelTile(icon: Ico, label: String, info: String, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    val tint = icoTint(icon)
    val shape = RoundedCornerShape(22.dp)
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(if (small) 74.dp else 104.dp).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = shape),
            focusedBorder = Border(BorderStroke(2.5.dp, NeoYellow), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(elevationColor = tint.copy(alpha = 0.55f), elevation = 18.dp)),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(tint.copy(alpha = if (focused) 0.62f else 0.38f), tint.copy(alpha = if (focused) 0.22f else 0.10f), Color.Black.copy(alpha = 0.15f))),
            ).padding(14.dp),
        ) {
            if (small) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).background(Color.White.copy(alpha = 0.16f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        PanelIcon(icon, Modifier.size(19.dp), Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(info, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.16f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    PanelIcon(icon, Modifier.size(21.dp), Color.White)
                }
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(info, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (focused) Box(Modifier.matchParentSize().background(Brush.linearGradient(0f to Color.White.copy(alpha = 0.16f), 0.5f to Color.Transparent)))
        }
    }
}

/**
 * Moderner Rahmen für Unterdialoge: abgedunkelter Hintergrund mit Farbschein, schwebende Glaskarte im Menü-Thema,
 * Kopf mit farbigem Symbolkreis, Titel und Untertitel. Inhalt füllt den Rest.
 */
@Composable
fun NeoSheet(
    icon: Ico, title: String, subtitle: String = "", width: androidx.compose.ui.unit.Dp = 840.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val tint = icoTint(icon)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.28f), Color.Transparent), radius = 1100f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(width).fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(32.dp)).background(NeoPanelBrush)
                .border(1.dp, NeoLine, RoundedCornerShape(32.dp))
                .padding(horizontal = 26.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(54.dp).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.18f))), androidx.compose.foundation.shape.CircleShape)
                        .border(1.5.dp, tint.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) { PanelIcon(icon, Modifier.size(26.dp), Color.White) }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            content()
        }
    }
}

/** Kleine Abschnitts-Überschrift in Unterdialogen. */
@Composable
fun SheetSection(title: String) {
    Text(title.uppercase(Lang.locale), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp, color = NeoCyan, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 2.dp))
}
