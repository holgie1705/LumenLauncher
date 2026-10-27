package com.lumen.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlin.math.cos
import kotlin.math.sin

private val Good = Color(0xFF3DDC84)
private val Warn = Color(0xFFFFB84D)
private val Bad = Color(0xFFFF6B6B)
private val Cool = Color(0xFF7DB3FF)

/** Eine ruhige Glas-Leiste mit gezeichneten Symbolen statt Text-Chips. */
private fun netLabel(net: String) = if (net == "WLAN") t("WLAN", "Wi-Fi") else net

@Composable
fun StatusBar(s: StatusInfo?, timerMin: Int) {
    if (s == null && timerMin <= 0) return
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.background(Color.White.copy(alpha = 0.08f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(horizontal = 13.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s != null) {
            StatusItem(netLabel(s.net)) { NetIcon(s.net, s.wifiLevel) }
            Divider()
            StatusItem(if (s.vpn) "VPN" else t("kein VPN", "no VPN"), highlight = s.vpn) { VpnIcon(s.vpn) }
            Divider()
            StatusItem("RAM ${100 - s.ramFreePct} %") { Ring(1f - s.ramFreePct / 100f) }
            Divider()
            StatusItem("%.0f GB ${t("frei", "free")}".format(s.storageGb)) { Ring(1f - s.storageFreePct / 100f) }
        }
        if (timerMin > 0) {
            if (s != null) Divider()
            StatusItem("Timer $timerMin ${t("Min.", "min")}") { TimerIcon() }
        }
    }
}

@Composable
private fun StatusItem(text: String, highlight: Boolean = false, icon: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp)) { icon() }
        Text(text, fontSize = 12.sp, color = if (highlight) Good else Color.White.copy(alpha = 0.85f), maxLines = 1)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.size(1.dp, 15.dp).background(Color.White.copy(alpha = 0.15f)))
}

@Composable
private fun NetIcon(net: String, level: Int) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        if (net == "LAN") {
            val top = Offset(w / 2, h * 0.22f)
            val l = Offset(w * 0.2f, h * 0.78f)
            val r = Offset(w * 0.8f, h * 0.78f)
            drawLine(Color.White.copy(alpha = 0.85f), top, l, stroke)
            drawLine(Color.White.copy(alpha = 0.85f), top, r, stroke)
            listOf(top, l, r).forEach { drawCircle(Color.White, 2.6.dp.toPx(), it) }
        } else {
            val lit = if (net == "Offline") 0 else level.coerceIn(1, 3)
            val c = Offset(w / 2, h * 0.92f)
            for (i in 0 until 3) {
                val r = h * (0.26f + 0.22f * i)
                drawArc(
                    color = if (i < lit) Color.White else Color.White.copy(alpha = 0.25f),
                    startAngle = 225f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            drawCircle(if (net == "Offline") Color.White.copy(alpha = 0.4f) else Color.White, 1.8.dp.toPx(), c)
            if (net == "Offline") drawLine(Bad, Offset(w * 0.15f, h * 0.1f), Offset(w * 0.85f, h * 0.9f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun VpnIcon(on: Boolean) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width * 0.8f
        val h = size.height
        val dx = size.width * 0.1f
        val shield = Path().apply {
            moveTo(dx + w * 0.5f, 0f)
            lineTo(dx + w, h * 0.18f)
            lineTo(dx + w, h * 0.5f)
            cubicTo(dx + w, h * 0.78f, dx + w * 0.75f, h * 0.92f, dx + w * 0.5f, h)
            cubicTo(dx + w * 0.25f, h * 0.92f, dx, h * 0.78f, dx, h * 0.5f)
            lineTo(dx, h * 0.18f)
            close()
        }
        val col = if (on) Good else Color.White.copy(alpha = 0.45f)
        if (on) drawPath(shield, Good.copy(alpha = 0.25f), style = Fill)
        drawPath(shield, col, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
        if (on) drawCircle(Good, 2.8.dp.toPx(), Offset(size.width / 2, h * 0.48f))
    }
}

@Composable
private fun Ring(used: Float) {
    Canvas(Modifier.size(18.dp)) {
        val u = used.coerceIn(0f, 1f)
        val stroke = 3.dp.toPx()
        val inset = stroke / 2
        val tl = Offset(inset, inset)
        val sz = Size(size.width - stroke, size.height - stroke)
        val col = when {
            u > 0.92f -> Bad
            u > 0.80f -> Warn
            else -> Cool
        }
        drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, tl, sz, style = Stroke(stroke))
        drawArc(col, -90f, 360f * u, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun TimerIcon() {
    Canvas(Modifier.size(18.dp)) {
        val c = center
        val r = size.minDimension / 2 - 1.5.dp.toPx()
        val stroke = 1.8.dp.toPx()
        drawCircle(Color.White.copy(alpha = 0.85f), r, c, style = Stroke(stroke))
        drawLine(Color.White, c, Offset(c.x, c.y - r * 0.65f), stroke, StrokeCap.Round)
        val a = Math.toRadians(30.0)
        drawLine(Color.White, c, Offset(c.x + (cos(a) * r * 0.5f).toFloat(), c.y + (sin(a) * r * 0.5f).toFloat()), stroke, StrokeCap.Round)
    }
}
