package com.lumen.tv

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

enum class Ico { GRID, LIST, GEAR, SCREEN, WIFI, SOUND, INFO, DOT, GLOBE, POWER, REMOTE, LOCK, BRUSH, PHONE, BACK, SEARCH, TIMER, RADIO, RESTART, DOWNLOAD }

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

val PanelBody = Color(0xE6363941)
val PanelBand = Color(0xFF2B2C31)
val PanelFocus = Color(0x38FFFFFF)

@Composable
fun PanelHeader(title: String) {
    Column {
        Box(
            Modifier.fillMaxWidth().height(120.dp)
                .background(Brush.horizontalGradient(listOf(Color(0xFF3B4C9B), Color(0xFF2D3040)))),
        ) {
            LumenLogo(Modifier.align(Alignment.CenterStart).padding(start = 28.dp))
            Text(
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEE dd.MM.yyyy", Lang.locale)),
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().background(PanelBand).padding(horizontal = 28.dp, vertical = 14.dp)) {
            Text(title, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PanelSection(title: String) {
    Text(
        title.uppercase(Lang.locale), fontSize = 12.sp, letterSpacing = 1.5.sp, color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Zeile der Hauptliste: abgerundeter Fokus-Balken mit Rahmen, Symbol in kleiner Kachel. */
@Composable
fun PanelRow(icon: Ico, label: String, value: String = "", modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 1.dp).fillMaxWidth().height(54.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = PanelFocus,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.45f)), shape = shape),
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                PanelIcon(icon, Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(16.dp))
                Text(value, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }
}
