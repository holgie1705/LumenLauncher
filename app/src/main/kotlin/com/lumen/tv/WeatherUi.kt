package com.lumen.tv

import android.content.Context
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Uhr + Wetter oben links. OK öffnet die Wetter-Details (oder die Ortswahl, falls noch kein Ort gesetzt ist). */
@Composable
fun ClockWeatherWidget(prefs: Prefs, weather: State<WeatherInfo?>, onClick: () -> Unit, right: Boolean = false, bottom: Boolean = false) {
    val ctx = LocalContext.current
    val now by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(10_000) } }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White.copy(alpha = 0.10f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), shape = shape)),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = if (right) Alignment.End else Alignment.Start,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                // Uhr etwas höher als das Wetter (unten: bündig unten); rechts gespiegelt: Wetter links, Uhr außen
                val clockMod = if (bottom) Modifier.align(Alignment.Bottom) else Modifier.align(Alignment.Top).offset(y = (-4).dp)
                if (right && prefs.weather > 0) WeatherBlock(prefs, weather.value, right = true)
                Box(clockMod) { ClockBlock(prefs.clockStyle, now, prefs.is24(ctx)) }
                if (!right && prefs.weather > 0) WeatherBlock(prefs, weather.value)
            }
            WeatherAlertChip(prefs, right)
        }
    }
}

@Composable
private fun ClockBlock(style: Int, now: LocalDateTime, is24: Boolean) {
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Lang.locale))
    val date = now.format(DateTimeFormatter.ofPattern(t("EEEE, d. MMMM", "EEEE, MMMM d"), Lang.locale))
    when (style) {
        3 -> FlipClock(now, is24)
        4 -> Column {
            Text(wordClock(now), fontSize = 20.sp, fontWeight = FontWeight.Light, color = Color.White, maxLines = 2, lineHeight = 22.sp, modifier = Modifier.widthIn(max = 300.dp))
            Text(date, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
        5 -> Column {
            // Neon: farbiges Leuchten durch Schatten in der Menüfarbe
            Text(
                time, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(NeoCyan, androidx.compose.ui.geometry.Offset.Zero, 28f)),
            )
            Text(date, fontSize = 11.sp, color = NeoCyan.copy(alpha = 0.85f), maxLines = 1)
        }
        1 -> Text(time, fontSize = 34.sp, fontWeight = FontWeight.W100, color = Color.White, maxLines = 1)
        2 -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AnalogClock(Modifier.size(58.dp), now)
            Column {
                Text(date.substringBefore(","), fontSize = 13.sp, color = Color.White, maxLines = 1)
                Text(date.substringAfter(", "), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
            }
        }
        else -> Column {
            Text(time, fontSize = 28.sp, fontWeight = FontWeight.W200, color = Color.White, maxLines = 1)
            Text(date, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
        }
    }
}

@Composable
private fun AnalogClock(modifier: Modifier, t: LocalDateTime) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(Color.White.copy(alpha = 0.25f), radius = r, center = c, style = Stroke(2.dp.toPx()))
        fun pt(deg: Double, len: Float): Offset {
            val a = (deg - 90.0) * PI / 180.0
            return Offset(c.x + (cos(a) * len).toFloat(), c.y + (sin(a) * len).toFloat())
        }
        for (i in 0 until 12) {
            val inner = if (i % 3 == 0) 0.80f else 0.90f
            drawLine(Color.White.copy(alpha = 0.7f), pt(i * 30.0, r * inner), pt(i * 30.0, r * 0.96f), strokeWidth = 2.dp.toPx())
        }
        val minute = t.minute + t.second / 60.0
        val hour = (t.hour % 12) + minute / 60.0
        drawLine(Color.White, c, pt(hour * 30.0, r * 0.5f), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Color.White, c, pt(minute * 6.0, r * 0.78f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 3.dp.toPx(), center = c)
    }
}

@Composable
private fun WeatherBlock(prefs: Prefs, w: WeatherInfo?, right: Boolean = false) {
    val hasPlace = prefs.weatherLat != 0.0 || prefs.weatherLon != 0.0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = if (right) Alignment.End else Alignment.Start) {
        when {
            !hasPlace -> Text(t("Wetter einrichten", "Set up weather"), fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))
            w == null -> Text(t("Wetter lädt …", "Loading weather …"), fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    WeatherGlyph(w.code, w.isDay, Modifier.size(26.dp))
                    Column(horizontalAlignment = if (right) Alignment.End else Alignment.Start) {
                        Text("${w.temp}°", fontSize = 22.sp, fontWeight = FontWeight.W300, color = Color.White, maxLines = 1)
                        Text(weatherText(w.code), fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
                if (prefs.weather == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        w.days.drop(1).take(3).forEach { d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                                WeatherGlyph(d.code, true, Modifier.size(18.dp))
                                Text("${d.max}°/${d.min}°", fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherDialog(prefs: Prefs, w: WeatherInfo?, onPlace: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.width(520.dp).clip(RoundedCornerShape(28.dp)).background(NeoPanelBrush).border(1.dp, NeoLine, RoundedCornerShape(28.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(prefs.weatherName, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (prefs.weatherAlerts) {
                val alerts by produceState<List<WxAlert>>(emptyList()) { value = WeatherAlerts.load(prefs.weatherLat, prefs.weatherLon) }
                alerts.take(3).forEach { a ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(a.color).copy(alpha = 0.16f)).border(1.dp, Color(a.color).copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                        Text("⚠ " + a.event, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(a.color))
                        if (a.headline.isNotEmpty()) Text(a.headline, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            }
            if (w == null) {
                Text(t("Wetter lädt …", "Loading weather …"), fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    WeatherGlyph(w.code, w.isDay, Modifier.size(54.dp))
                    Column {
                        Text("${w.temp}°  (${t("gefühlt", "feels like")} ${w.feels}°)", fontSize = 28.sp, fontWeight = FontWeight.W300, color = Color.White)
                        Text("${weatherText(w.code)} · Wind ${w.wind} ${if (w.fahrenheit) "mph" else "km/h"}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f))
                    }
                }
                if (w.sunrise.isNotEmpty() && w.sunset.isNotEmpty()) {
                    Text(
                        "${t("Sonnenaufgang", "Sunrise")} ${w.sunrise}  ·  ${t("Sonnenuntergang", "Sunset")} ${w.sunset}",
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f),
                    )
                }
                w.days.forEach { d ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(d.label, fontSize = 16.sp, color = Color.White, modifier = Modifier.width(70.dp))
                        WeatherGlyph(d.code, true, Modifier.size(24.dp))
                        Text("${d.max}° / ${d.min}°", fontSize = 16.sp, color = Color.White, modifier = Modifier.width(110.dp))
                        Text("${t("Regen", "Rain")} ${d.rain} %", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(100.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(t("Ort ändern", "Change place"), Modifier.focusRequester(first)) { onPlace() }
                Pill(t("Schließen", "Close")) { onClose() }
            }
        }
    }
}

/** Ortssuche: Tippen per Bildschirmtastatur/Spracheingabe oder eine der beliebten Städte wählen. */
@Composable
fun PlaceDialog(vm: HomeViewModel, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(PRESET_PLACES) }
    var info by remember { mutableStateOf(t("Beliebte Städte – oder oben einen Ort eintippen.", "Popular cities – or type a place above.")) }
    val scope = rememberCoroutineScope()
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { field.requestFocus() } }

    fun search() {
        if (query.isBlank()) return
        info = t("Suche …", "Searching …")
        scope.launch {
            results = vm.searchPlaces(query.trim())
            info = if (results.isEmpty()) t("Nichts gefunden.", "Nothing found.") else ""
        }
    }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.width(560.dp).clip(RoundedCornerShape(28.dp)).background(NeoPanelBrush).border(1.dp, NeoLine, RoundedCornerShape(28.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t("Ort für das Wetter", "Place for the weather"), fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
                    .focusRequester(field),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(t("Suchen", "Search")) { search() }
                Pill(t("Beliebte Städte", "Popular cities")) { results = PRESET_PLACES; info = "" }
                Pill(t("Schließen", "Close")) { onClose() }
            }
            if (info.isNotEmpty()) Text(info, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
            LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(results) { _, p ->
                    SettingLine(p.name, p.region) { vm.setPlace(p); onClose() }
                }
            }
        }
    }
}

fun Prefs.is24(ctx: Context): Boolean = when (timeFormat) {
    0 -> true
    2 -> false
    else -> DateFormat.is24HourFormat(ctx)
}


fun clockStyleName(i: Int): String = when (i) {
    1 -> "Minimal"; 2 -> t("Analog", "Analog"); 3 -> t("Flip-Uhr", "Flip clock"); 4 -> t("Wortuhr", "Word clock"); 5 -> "Neon"
    else -> t("Digital", "Digital")
}

/** Wortuhr: „Es ist Viertel nach acht“ (auf 5 Minuten gerundet). */
fun wordClock(now: LocalDateTime): String {
    val de = listOf("zwölf", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf")
    val en = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")
    val m5 = ((now.minute + 2) / 5) * 5
    val h = now.hour + if (m5 == 60) 1 else 0
    val m = m5 % 60
    fun hr(x: Int) = if (Lang.en) en[x % 12] else de[x % 12].let { if (it == "eins" && m == 0) "ein" else it }
    return if (Lang.en) when (m) {
        0 -> "It's ${hr(h)} o'clock"; 15 -> "It's quarter past ${hr(h)}"; 30 -> "It's half past ${hr(h)}"; 45 -> "It's quarter to ${hr(h + 1)}"
        in 5..25 -> "It's $m past ${hr(h)}"; else -> "It's ${60 - m} to ${hr(h + 1)}"
    } else when (m) {
        0 -> "Es ist ${hr(h)} Uhr"; 5 -> "Es ist fünf nach ${hr(h)}"; 10 -> "Es ist zehn nach ${hr(h)}"; 15 -> "Es ist Viertel nach ${hr(h)}"
        20 -> "Es ist zwanzig nach ${hr(h)}"; 25 -> "Es ist fünf vor halb ${hr(h + 1)}"; 30 -> "Es ist halb ${hr(h + 1)}"
        35 -> "Es ist fünf nach halb ${hr(h + 1)}"; 40 -> "Es ist zwanzig vor ${hr(h + 1)}"; 45 -> "Es ist Viertel vor ${hr(h + 1)}"
        50 -> "Es ist zehn vor ${hr(h + 1)}"; else -> "Es ist fünf vor ${hr(h + 1)}"
    }
}

/** Flip-Uhr: jede Ziffer auf einer dunklen Karte mit Trennlinie; wechselnde Ziffern klappen weich um. */
@Composable
private fun FlipClock(now: LocalDateTime, is24: Boolean) {
    val h = if (is24) now.hour else ((now.hour + 11) % 12 + 1)
    val txt = "%02d%02d".format(h, now.minute)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        txt.forEachIndexed { i, ch ->
            if (i == 2) Text(":", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.8f))
            androidx.compose.animation.AnimatedContent(
                ch, label = "flip$i",
                transitionSpec = {
                    (androidx.compose.animation.slideInVertically(tween(350)) { -it / 2 } + androidx.compose.animation.fadeIn(tween(350))) togetherWith
                        (androidx.compose.animation.slideOutVertically(tween(350)) { it / 2 } + androidx.compose.animation.fadeOut(tween(250)))
                },
            ) { c ->
                Box(Modifier.size(30.dp, 42.dp).background(Brush.verticalGradient(listOf(Color(0xFF2A2F3A), Color(0xFF15181F))), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                    Text(c.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.6f)))
                }
            }
        }
    }
}
