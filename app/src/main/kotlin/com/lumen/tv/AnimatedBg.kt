package com.lumen.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import java.time.LocalDate
import kotlin.math.cos
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/** Namen der gezeichneten Hintergründe (0 = aus). */
fun animBgName(i: Int): String = when (i) {
    1 -> t("Sternenhimmel", "Starry sky"); 2 -> t("Polarlicht", "Northern lights"); 3 -> t("Farbfluss", "Color flow")
    4 -> "Bokeh"; 5 -> t("Regen", "Rain"); 6 -> t("Schnee", "Snow")
    7 -> "Halloween"; 8 -> t("Weihnachten", "Christmas"); 9 -> t("Silvester", "New Year's Eve"); 10 -> t("Ostern", "Easter")
    else -> t("Aus", "Off")
}

/** Gleichmäßige Pseudo-Zufallszahl 0..1 je Index (ohne Speicher pro Teilchen). */
private fun rnd(i: Int, salt: Int): Float {
    var x = i * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7fffffff) / 2147483647f
}

/** Zeit in Sekunden, ~12 Bilder pro Sekunde – sieht flüssig aus und schont schwache TV-Boxen. */
@Composable
private fun ticker(): Float {
    val t by produceState(0f) { val t0 = System.currentTimeMillis(); while (true) { value = (System.currentTimeMillis() - t0) / 1000f; delay(83) } }
    return t
}

/** Gezeichneter, animierter Hintergrund (liegt über dem Wallpaper bzw. allein auf Schwarz). */
@Composable
fun AnimatedBackground(kind: Int, accent: Color) {
    if (kind <= 0) return
    val t = ticker()
    Canvas(Modifier.fillMaxSize()) {
        when (kind) {
            1 -> stars(t)
            2 -> northernLights(t)
            3 -> colorFlow(t, accent)
            4 -> bokeh(t, accent)
            5 -> rain(t, 170, 1f)
            6 -> snow(t, 140)
            7 -> halloween(t)
            8 -> christmas(t)
            9 -> fireworks(t)
            10 -> easter(t)
        }
    }
}

/** Wetter-Effekt über dem Hintergrund (Regen, Schnee, Nebel, Gewitterblitz) plus Tageszeit-Tönung. */
@Composable
fun WeatherEffect(code: Int?) {
    val hour = java.time.LocalTime.now().hour
    val animated = code != null && (code in 51..67 || code in 80..82 || code in 71..77 || code == 85 || code == 86 || code in 95..99)
    val t = if (animated) ticker() else 0f
    Canvas(Modifier.fillMaxSize()) {
        // Tageszeit: nachts dunkler und kühler, abends warm, morgens hell-warm
        when {
            hour >= 21 || hour < 6 -> { drawRect(Ink.copy(alpha = 0.28f)); drawRect(Color(0xFF1A2A6C).copy(alpha = 0.12f)) }
            hour >= 17 -> drawRect(Brush.verticalGradient(listOf(Color(0xFFFF8A3D).copy(alpha = 0.10f), Color.Transparent)))
            hour < 10 -> drawRect(Brush.verticalGradient(listOf(Color(0xFFFFE0A3).copy(alpha = 0.08f), Color.Transparent)))
        }
        when (code) {
            null -> {}
            45, 48 -> drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.16f))))
            in 71..77, 85, 86 -> snow(t, 110)
            in 95..99 -> { rain(t, 190, 1.3f); if ((t % 9f) < 0.12f || (t % 9f) in 0.25f..0.32f) drawRect(Color.White.copy(alpha = 0.22f)) }
            in 51..57 -> rain(t, 70, 0.7f)
            in 58..67, in 80..82 -> rain(t, 150, 1f)
        }
    }
}

private fun DrawScope.stars(t: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until 150) {
        val x = rnd(i, 1) * w; val y = rnd(i, 2) * h * 0.85f
        val tw = 0.25f + 0.75f * abs(sin(t * (0.6f + rnd(i, 3) * 1.6f) + rnd(i, 4) * 6.28f))
        drawCircle(Color.White.copy(alpha = tw * (0.4f + 0.6f * rnd(i, 5))), radius = 0.8f + rnd(i, 6) * 2.2f, center = Offset(x, y))
    }
    // Sternschnuppe etwa alle 11 Sekunden
    val p = (t % 11f) / 1.2f
    if (p < 1f) {
        val n = (t / 11f).toInt()
        val sx = w * (0.15f + 0.6f * rnd(n, 7)); val sy = h * (0.05f + 0.25f * rnd(n, 8))
        val ex = sx + w * 0.25f * p; val ey = sy + h * 0.12f * p
        drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f * (1f - p))), Offset(sx, sy), Offset(ex, ey)), Offset(sx, sy), Offset(ex, ey), strokeWidth = 2.5f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.northernLights(t: Float) {
    val w = size.width; val h = size.height
    val colors = listOf(Color(0xFF3CFFB0), Color(0xFF2FB8FF), Color(0xFFB57BFF))
    colors.forEachIndexed { k, c ->
        val step = 14f
        var x = 0f
        while (x < w) {
            val u = x / w
            val wave = sin(u * 6.28f * (1.2f + k * 0.4f) + t * (0.25f + k * 0.1f)) * 0.5f + sin(u * 15f - t * 0.4f + k) * 0.15f
            val top = h * (0.12f + 0.12f * k + 0.10f * wave)
            val len = h * (0.22f + 0.10f * abs(sin(u * 9f + t * 0.3f + k)))
            val a = (0.10f + 0.12f * abs(sin(u * 4f + t * 0.5f + k * 2f))) * (1f - abs(u - 0.5f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = a), Color.Transparent), startY = top, endY = top + len), Offset(x, top), Size(step + 1f, len))
            x += step
        }
    }
}

private fun DrawScope.colorFlow(t: Float, accent: Color) {
    val w = size.width; val h = size.height
    val cs = listOf(accent, NeoViolet, NeoCyan)
    cs.forEachIndexed { k, c ->
        val cx = w * (0.5f + 0.35f * sin(t * (0.05f + k * 0.02f) + k * 2f))
        val cy = h * (0.5f + 0.3f * sin(t * (0.04f + k * 0.015f) + k))
        val r = size.maxDimension * (0.45f + 0.1f * k)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.30f), Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
    }
}

private fun DrawScope.bokeh(t: Float, accent: Color) {
    val w = size.width; val h = size.height
    for (i in 0 until 28) {
        val r = 18f + rnd(i, 1) * 70f
        val speed = 8f + rnd(i, 2) * 18f
        val y = h + r - ((rnd(i, 3) * (h + 2 * r) + t * speed) % (h + 2 * r))
        val x = rnd(i, 4) * w + sin(t * 0.2f + i) * 30f
        val c = if (i % 3 == 0) NeoCyan else if (i % 3 == 1) accent else NeoViolet
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.22f), c.copy(alpha = 0.05f), Color.Transparent), center = Offset(x, y), radius = r), radius = r, center = Offset(x, y))
    }
}

private fun DrawScope.rain(t: Float, n: Int, strength: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until n) {
        val len = (14f + rnd(i, 1) * 22f) * strength
        val speed = (700f + rnd(i, 2) * 500f) * strength
        val y = (rnd(i, 3) * h + t * speed) % (h + len) - len
        val x = (rnd(i, 4) * (w + 200f) - 100f + y * 0.12f) % w
        drawLine(Color(0xFFBFD9FF).copy(alpha = 0.18f + 0.25f * rnd(i, 5)), Offset(x, y), Offset(x + len * 0.12f, y + len), strokeWidth = 1.4f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.snow(t: Float, n: Int) {
    val w = size.width; val h = size.height
    for (i in 0 until n) {
        val r = 1.2f + rnd(i, 1) * 3.2f
        val speed = 25f + rnd(i, 2) * 55f
        val y = (rnd(i, 3) * h + t * speed) % (h + 10f) - 5f
        val x = rnd(i, 4) * w + sin(t * (0.5f + rnd(i, 5)) + i) * 18f
        drawCircle(Color.White.copy(alpha = 0.45f + 0.45f * rnd(i, 6)), radius = r, center = Offset(x, y))
    }
}

// ============================================================== Saisonale Themes

/** Ostersonntag (Gauß/Meeus, gregorianisch). */
fun easterSunday(year: Int): LocalDate {
    val a = year % 19; val b = year / 100; val c = year % 100; val d = b / 4; val e = b % 4
    val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(year, month, day)
}

/** Passender Saison-Hintergrund für heute (7 Halloween, 8 Weihnachten, 9 Silvester, 10 Ostern) oder null. */
fun seasonBg(today: LocalDate = LocalDate.now()): Int? {
    val md = today.monthValue * 100 + today.dayOfMonth
    val easter = easterSunday(today.year)
    return when {
        md in 1024..1101 -> 7
        md in 1201..1228 -> 8
        md >= 1229 || md <= 101 -> 9
        !today.isBefore(easter.minusDays(7)) && !today.isAfter(easter.plusDays(1)) -> 10
        else -> null
    }
}

private fun DrawScope.pumpkin(c: Offset, r: Float, glow: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A1E).copy(alpha = 0.30f * glow), Color.Transparent), center = c, radius = r * 2.4f), radius = r * 2.4f, center = c)
    val dark = Color(0xFFC24A00); val body = Color(0xFFFF7A1A); val mid = Color(0xFFFF8F2E)
    drawOval(dark, Offset(c.x - r * 1.05f, c.y - r * 0.80f), Size(r * 0.95f, r * 1.6f))
    drawOval(dark, Offset(c.x + r * 0.10f, c.y - r * 0.80f), Size(r * 0.95f, r * 1.6f))
    drawOval(body, Offset(c.x - r * 0.98f, c.y - r * 0.77f), Size(r * 0.88f, r * 1.54f))
    drawOval(body, Offset(c.x + r * 0.10f, c.y - r * 0.77f), Size(r * 0.88f, r * 1.54f))
    drawOval(mid, Offset(c.x - r * 0.46f, c.y - r * 0.82f), Size(r * 0.92f, r * 1.64f))
    drawRoundRect(Color(0xFF5E7A2C), Offset(c.x - r * 0.09f, c.y - r * 1.08f), Size(r * 0.18f, r * 0.34f), CornerRadius(r * 0.06f))
    val face = Color(0xFFFFE07A).copy(alpha = 0.55f + 0.45f * glow)
    fun tri(x: Float, y: Float, s: Float) = Path().apply { moveTo(x, y - s); lineTo(x + s, y + s * 0.6f); lineTo(x - s, y + s * 0.6f); close() }
    drawPath(tri(c.x - r * 0.38f, c.y - r * 0.18f, r * 0.17f), face)
    drawPath(tri(c.x + r * 0.38f, c.y - r * 0.18f, r * 0.17f), face)
    val mouth = Path().apply {
        moveTo(c.x - r * 0.55f, c.y + r * 0.22f)
        lineTo(c.x - r * 0.35f, c.y + r * 0.34f); lineTo(c.x - r * 0.18f, c.y + r * 0.24f); lineTo(c.x, c.y + r * 0.36f)
        lineTo(c.x + r * 0.18f, c.y + r * 0.24f); lineTo(c.x + r * 0.35f, c.y + r * 0.34f); lineTo(c.x + r * 0.55f, c.y + r * 0.22f)
        lineTo(c.x + r * 0.30f, c.y + r * 0.52f); lineTo(c.x - r * 0.30f, c.y + r * 0.52f); close()
    }
    drawPath(mouth, face)
}

private fun DrawScope.bat(c: Offset, s: Float, flap: Float) {
    val up = s * 0.45f * flap
    val p = Path().apply {
        moveTo(c.x, c.y)
        cubicTo(c.x - s * 0.3f, c.y - s * 0.25f - up, c.x - s * 0.7f, c.y - s * 0.35f - up, c.x - s, c.y - s * 0.15f - up)
        cubicTo(c.x - s * 0.8f, c.y, c.x - s * 0.6f, c.y + s * 0.05f, c.x - s * 0.45f, c.y + s * 0.15f)
        cubicTo(c.x - s * 0.3f, c.y + s * 0.05f, c.x - s * 0.15f, c.y + s * 0.1f, c.x, c.y + s * 0.22f)
        cubicTo(c.x + s * 0.15f, c.y + s * 0.1f, c.x + s * 0.3f, c.y + s * 0.05f, c.x + s * 0.45f, c.y + s * 0.15f)
        cubicTo(c.x + s * 0.6f, c.y + s * 0.05f, c.x + s * 0.8f, c.y, c.x + s, c.y - s * 0.15f - up)
        cubicTo(c.x + s * 0.7f, c.y - s * 0.35f - up, c.x + s * 0.3f, c.y - s * 0.25f - up, c.x, c.y)
        close()
    }
    drawPath(p, Color(0xFF0A0A10).copy(alpha = 0.9f))
    drawCircle(Color(0xFF0A0A10), s * 0.13f, Offset(c.x, c.y + s * 0.06f))
}

private fun DrawScope.halloween(t: Float) {
    val w = size.width; val h = size.height
    // Abendrot-Schimmer unten, Vollmond oben rechts
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF6A00).copy(alpha = 0.16f)), startY = h * 0.55f, endY = h))
    val moon = Offset(w * 0.86f, h * 0.16f); val mr = h * 0.075f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1C9).copy(alpha = 0.28f), Color.Transparent), center = moon, radius = mr * 3.5f), radius = mr * 3.5f, center = moon)
    drawCircle(Color(0xFFFFF3D6).copy(alpha = 0.85f), mr, moon)
    drawCircle(Color(0xFFE8D9B0).copy(alpha = 0.5f), mr * 0.18f, Offset(moon.x - mr * 0.3f, moon.y - mr * 0.2f))
    drawCircle(Color(0xFFE8D9B0).copy(alpha = 0.4f), mr * 0.12f, Offset(moon.x + mr * 0.35f, moon.y + mr * 0.3f))
    // schwebende Kürbisse
    for (i in 0 until 7) {
        val r = h * (0.035f + 0.03f * rnd(i, 1))
        val x = w * (0.06f + 0.88f * rnd(i, 2)) + sin(t * 0.3f + i) * w * 0.015f
        val y = h * (0.55f + 0.38f * rnd(i, 3)) + sin(t * (0.5f + rnd(i, 4) * 0.4f) + i * 2f) * h * 0.02f
        pumpkin(Offset(x, y), r, 0.6f + 0.4f * abs(sin(t * (1.2f + rnd(i, 5)) + i)))
    }
    // Fledermäuse fliegen quer durchs Bild
    for (i in 0 until 6) {
        val s = h * (0.025f + 0.02f * rnd(i, 6))
        val speed = 0.035f + 0.03f * rnd(i, 7)
        val p = (t * speed + rnd(i, 8)) % 1.3f - 0.15f
        val x = w * p
        val y = h * (0.08f + 0.35f * rnd(i, 9)) + sin(t * 1.3f + i * 3f) * h * 0.03f
        bat(Offset(x, y), s, sin(t * (9f + rnd(i, 10) * 4f) + i))
    }
}

private fun DrawScope.christmas(t: Float) {
    val w = size.width; val h = size.height
    snow(t, 120)
    // Lichterkette in zwei Bögen am oberen Rand
    val colors = listOf(Color(0xFFFF4D4D), Color(0xFFFFD34D), Color(0xFF4DFF88), Color(0xFF4DB8FF), Color(0xFFFF8AD8))
    val n = 34
    for (seg in 0 until 2) {
        val x0 = w * seg / 2f; val x1 = w * (seg + 1) / 2f
        var prev: Offset? = null
        for (k in 0..60) {
            val u = k / 60f
            val pt = Offset(x0 + (x1 - x0) * u, h * 0.035f + h * 0.07f * 4f * u * (1f - u))
            prev?.let { drawLine(Color(0xFF1E3A22), it, pt, strokeWidth = 2f) }
            prev = pt
        }
    }
    for (i in 0 until n) {
        val gx = (i + 0.5f) / n
        val seg = if (gx < 0.5f) 0 else 1
        val u = (gx - seg * 0.5f) * 2f
        val pos = Offset(w * gx, h * 0.035f + h * 0.07f * 4f * u * (1f - u) + 9f)
        val c = colors[i % colors.size]
        val on = 0.45f + 0.55f * abs(sin(t * (1.1f + rnd(i, 3) * 1.5f) + i))
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f * on), Color.Transparent), center = pos, radius = 34f), radius = 34f, center = pos)
        drawOval(c.copy(alpha = 0.6f + 0.4f * on), Offset(pos.x - 5f, pos.y - 7f), Size(10f, 15f))
    }
    // ein paar funkelnde Sterne
    for (i in 0 until 12) {
        val p = Offset(w * rnd(i, 11), h * (0.18f + 0.5f * rnd(i, 12)))
        val a = abs(sin(t * (0.8f + rnd(i, 13)) + i * 1.7f))
        val l = 6f + 10f * a
        drawLine(Color.White.copy(alpha = 0.6f * a), Offset(p.x - l, p.y), Offset(p.x + l, p.y), strokeWidth = 1.5f)
        drawLine(Color.White.copy(alpha = 0.6f * a), Offset(p.x, p.y - l), Offset(p.x, p.y + l), strokeWidth = 1.5f)
    }
}

private fun DrawScope.fireworks(t: Float) {
    val w = size.width; val h = size.height
    val palette = listOf(Color(0xFFFFD24A), Color(0xFFFF5E7E), Color(0xFF6EE7FF), Color(0xFFB57BFF), Color(0xFF7CFF9A), Color(0xFFFFFFFF))
    val every = 1.1f
    val newest = (t / every).toInt()
    for (n in (newest - 3)..newest) {
        if (n < 0) continue
        val age = t - n * every
        val cx = w * (0.12f + 0.76f * rnd(n, 21)); val cy = h * (0.12f + 0.35f * rnd(n, 22))
        val c = palette[(rnd(n, 23) * palette.size).toInt().coerceIn(0, palette.lastIndex)]
        val rise = 0.55f
        if (age < rise) {
            // Rakete steigt
            val p = age / rise
            val y = h + (cy - h) * p
            drawLine(Brush.verticalGradient(listOf(c.copy(alpha = 0.8f), Color.Transparent), startY = y, endY = y + h * 0.08f), Offset(cx, y), Offset(cx, y + h * 0.08f), strokeWidth = 2.5f)
        } else {
            val e = age - rise
            if (e > 2.6f) continue
            val fade = (1f - e / 2.6f).coerceIn(0f, 1f)
            val radius = h * 0.22f * (1f - (1f - (e / 2.6f).coerceAtMost(1f)).let { it * it * it })
            if (e < 0.25f) drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.5f), Color.Transparent), center = Offset(cx, cy), radius = h * 0.12f), radius = h * 0.12f, center = Offset(cx, cy))
            for (k in 0 until 42) {
                val ang = k / 42f * 6.2832f + rnd(n, 24)
                val sp = 0.75f + 0.25f * rnd(n * 50 + k, 25)
                val px = cx + cos(ang) * radius * sp
                val py = cy + sin(ang) * radius * sp + e * e * h * 0.025f
                val tw = if (rnd(n * 50 + k, 26) > 0.6f) (0.5f + 0.5f * abs(sin(t * 18f + k))) else 1f
                drawCircle(c.copy(alpha = fade * tw), 2.6f * (0.6f + 0.4f * fade), Offset(px, py))
            }
        }
    }
}

private fun DrawScope.easter(t: Float) {
    val w = size.width; val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF9BE37A).copy(alpha = 0.14f)), startY = h * 0.6f, endY = h))
    val pastel = listOf(Color(0xFFFFB3C7), Color(0xFFB3E5FF), Color(0xFFFFF0A6), Color(0xFFC9B3FF), Color(0xFFB8F5C8))
    // schwebende Eier mit Zickzack-Band
    for (i in 0 until 9) {
        val ew = h * (0.045f + 0.025f * rnd(i, 1)); val eh = ew * 1.3f
        val speed = 10f + rnd(i, 2) * 14f
        val y = h + eh - ((rnd(i, 3) * (h + 2 * eh) + t * speed) % (h + 2 * eh))
        val x = w * rnd(i, 4) + sin(t * 0.4f + i) * 24f
        val c = pastel[i % pastel.size]
        rotate(sin(t * 0.6f + i) * 14f, Offset(x, y)) {
            drawOval(c.copy(alpha = 0.75f), Offset(x - ew / 2, y - eh / 2), Size(ew, eh))
            val band = Path().apply {
                val by = y; val steps = 6
                moveTo(x - ew * 0.48f, by)
                for (s in 1..steps) lineTo(x - ew * 0.48f + ew * 0.96f * s / steps, by + if (s % 2 == 1) -ew * 0.1f else ew * 0.1f)
            }
            drawPath(band, Color.White.copy(alpha = 0.8f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            drawCircle(Color.White.copy(alpha = 0.6f), ew * 0.06f, Offset(x - ew * 0.15f, y - eh * 0.22f))
            drawCircle(Color.White.copy(alpha = 0.6f), ew * 0.06f, Offset(x + ew * 0.15f, y + eh * 0.24f))
        }
    }
    // fallende Blütenblätter
    for (i in 0 until 40) {
        val speed = 30f + rnd(i, 5) * 40f
        val y = (rnd(i, 6) * h + t * speed) % (h + 20f) - 10f
        val x = rnd(i, 7) * w + sin(t * (0.6f + rnd(i, 8)) + i) * 30f
        rotate(t * 40f * (rnd(i, 9) - 0.5f) + i * 30f, Offset(x, y)) {
            drawOval(Color(0xFFFFC6DA).copy(alpha = 0.55f + 0.3f * rnd(i, 10)), Offset(x - 5f, y - 3f), Size(10f, 6f))
        }
    }
}
