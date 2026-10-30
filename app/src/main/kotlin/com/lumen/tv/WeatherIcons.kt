package com.lumen.tv

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

/** Selbst gezeichnete Wetter-Symbole – unabhängig von Emoji-Fonts (die auf vielen TV-Boxen fehlen). */
@Composable
fun WeatherGlyph(code: Int, day: Boolean, modifier: Modifier) {
    ComposeCanvas(modifier) {
        drawIntoCanvas { c -> drawWeather(c.nativeCanvas, size.width, size.height, code, day) }
    }
}

private const val SUN = 0xFFFFC53D.toInt()
private const val MOON = 0xFFE9EEFF.toInt()
private const val CLOUD = 0xFFE3E9F2.toInt()
private const val CLOUD_DARK = 0xFFB7C1D2.toInt()
private const val RAIN = 0xFF57A6FF.toInt()
private const val SNOW = 0xFFFFFFFF.toInt()
private const val BOLT = 0xFFFFD23B.toInt()
private const val FOG = 0xFFC6D0DE.toInt()

private fun paint(c: Int, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = c
    if (stroke > 0f) { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND } else style = Paint.Style.FILL
}

/** Zeichnet das Wetter-Symbol zum Open-Meteo-Code in die Fläche (w × h). Nutzbar in Compose und für Bitmaps. */
fun drawWeather(c: Canvas, w: Float, h: Float, code: Int, day: Boolean) {
    val s = minOf(w, h)
    val cx = w / 2f; val cy = h / 2f
    when (code) {
        0 -> if (day) sun(c, cx, cy, s * 0.30f) else moon(c, cx, cy, s * 0.34f)
        1 -> if (day) { sun(c, cx - s * 0.12f, cy - s * 0.10f, s * 0.24f); cloud(c, cx + s * 0.05f, cy + s * 0.08f, s * 0.46f, CLOUD) }
             else { moon(c, cx - s * 0.10f, cy - s * 0.08f, s * 0.26f); cloud(c, cx + s * 0.06f, cy + s * 0.08f, s * 0.44f, CLOUD) }
        2 -> { if (day) sun(c, cx - s * 0.16f, cy - s * 0.14f, s * 0.22f) else moon(c, cx - s * 0.14f, cy - s * 0.12f, s * 0.24f); cloud(c, cx + s * 0.04f, cy + s * 0.06f, s * 0.52f, CLOUD) }
        3 -> { cloud(c, cx - s * 0.08f, cy - s * 0.04f, s * 0.46f, CLOUD_DARK); cloud(c, cx + s * 0.06f, cy + s * 0.06f, s * 0.54f, CLOUD) }
        45, 48 -> fog(c, cx, cy, s)
        in 51..57 -> { cloud(c, cx, cy - s * 0.12f, s * 0.58f, CLOUD); drops(c, cx, cy + s * 0.22f, s, 2, short = true) }
        in 61..67, in 80..82 -> { cloud(c, cx, cy - s * 0.12f, s * 0.58f, CLOUD); drops(c, cx, cy + s * 0.22f, s, 3, short = false) }
        in 71..77, 85, 86 -> { cloud(c, cx, cy - s * 0.12f, s * 0.58f, CLOUD); flakes(c, cx, cy + s * 0.24f, s) }
        95, 96, 99 -> { cloud(c, cx, cy - s * 0.12f, s * 0.58f, CLOUD_DARK); bolt(c, cx, cy + s * 0.10f, s) }
        else -> { if (day) sun(c, cx - s * 0.14f, cy - s * 0.12f, s * 0.22f) else moon(c, cx - s * 0.12f, cy - s * 0.10f, s * 0.24f); cloud(c, cx + s * 0.05f, cy + s * 0.07f, s * 0.5f, CLOUD) }
    }
}

private fun sun(c: Canvas, cx: Float, cy: Float, r: Float) {
    val ray = paint(SUN, r * 0.22f)
    for (i in 0 until 8) {
        val a = i * Math.PI / 4
        val x1 = cx + cos(a).toFloat() * r * 1.35f; val y1 = cy + sin(a).toFloat() * r * 1.35f
        val x2 = cx + cos(a).toFloat() * r * 1.95f; val y2 = cy + sin(a).toFloat() * r * 1.95f
        c.drawLine(x1, y1, x2, y2, ray)
    }
    c.drawCircle(cx, cy, r, paint(SUN))
}

private fun moon(c: Canvas, cx: Float, cy: Float, r: Float) {
    val save = c.saveLayer(cx - r * 1.5f, cy - r * 1.5f, cx + r * 1.5f, cy + r * 1.5f, null)
    c.drawCircle(cx, cy, r, paint(MOON))
    // versetzter „Ausstanz“-Kreis erzeugt die Sichel
    val cut = paint(MOON).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    c.drawCircle(cx + r * 0.55f, cy - r * 0.25f, r * 0.92f, cut)
    c.restoreToCount(save)
}

private fun cloud(c: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
    val p = paint(color)
    val w = size; val h = size * 0.62f
    val baseTop = cy + h * 0.10f
    c.drawCircle(cx - w * 0.22f, baseTop, h * 0.34f, p)
    c.drawCircle(cx + w * 0.02f, cy - h * 0.06f, h * 0.44f, p)
    c.drawCircle(cx + w * 0.26f, baseTop - h * 0.02f, h * 0.36f, p)
    c.drawRoundRect(RectF(cx - w * 0.42f, baseTop, cx + w * 0.42f, baseTop + h * 0.34f), h * 0.3f, h * 0.3f, p)
}

private fun drops(c: Canvas, cx: Float, cy: Float, s: Float, n: Int, short: Boolean) {
    val p = paint(RAIN, s * 0.055f)
    val len = if (short) s * 0.10f else s * 0.17f
    val span = s * 0.5f
    for (i in 0 until n) {
        val x = cx - span / 2 + span * i / (n - 1).coerceAtLeast(1)
        c.drawLine(x + len * 0.3f, cy, x - len * 0.2f, cy + len, p)
    }
}

private fun flakes(c: Canvas, cx: Float, cy: Float, s: Float) {
    val p = paint(SNOW)
    val span = s * 0.5f
    for (i in 0 until 3) {
        val x = cx - span / 2 + span * i / 2f
        val y = cy + (if (i == 1) s * 0.10f else 0f)
        c.drawCircle(x, y, s * 0.05f, p)
    }
}

private fun bolt(c: Canvas, cx: Float, cy: Float, s: Float) {
    val p = paint(BOLT)
    val path = Path().apply {
        moveTo(cx + s * 0.06f, cy - s * 0.16f)
        lineTo(cx - s * 0.14f, cy + s * 0.08f)
        lineTo(cx - s * 0.01f, cy + s * 0.08f)
        lineTo(cx - s * 0.08f, cy + s * 0.28f)
        lineTo(cx + s * 0.16f, cy - s * 0.02f)
        lineTo(cx + s * 0.02f, cy - s * 0.02f)
        close()
    }
    c.drawPath(path, p)
}

private fun fog(c: Canvas, cx: Float, cy: Float, s: Float) {
    cloud(c, cx, cy - s * 0.14f, s * 0.52f, CLOUD)
    val p = paint(FOG, s * 0.07f)
    for (i in 0 until 3) {
        val y = cy + s * 0.18f + i * s * 0.14f
        val inset = s * (0.10f + i * 0.06f)
        c.drawLine(cx - s * 0.34f + inset, y, cx + s * 0.34f - inset, y, p)
    }
}

/** Wetter-Symbol als kleines Bitmap (für Nicht-Compose-Ansichten wie die Mini-Uhr-Überlagerung). */
fun weatherBitmap(code: Int, day: Boolean, px: Int): android.graphics.Bitmap {
    val b = android.graphics.Bitmap.createBitmap(px.coerceAtLeast(8), px.coerceAtLeast(8), android.graphics.Bitmap.Config.ARGB_8888)
    drawWeather(Canvas(b), px.toFloat(), px.toFloat(), code, day)
    return b
}
