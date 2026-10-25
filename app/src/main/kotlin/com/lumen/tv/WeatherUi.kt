package com.lumen.tv

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
fun ClockWeatherWidget(prefs: Prefs, weather: State<WeatherInfo?>, onClick: () -> Unit) {
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
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClockBlock(prefs.clockStyle, now, prefs.is24(ctx))
            if (prefs.weather > 0) WeatherBlock(prefs, weather.value)
        }
    }
}

@Composable
private fun ClockBlock(style: Int, now: LocalDateTime, is24: Boolean) {
    val time = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Lang.locale))
    val date = now.format(DateTimeFormatter.ofPattern(t("EEEE, d. MMMM", "EEEE, MMMM d"), Lang.locale))
    when (style) {
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
private fun WeatherBlock(prefs: Prefs, w: WeatherInfo?) {
    val hasPlace = prefs.weatherLat != 0.0 || prefs.weatherLon != 0.0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            !hasPlace -> Text(t("Wetter einrichten", "Set up weather"), fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))
            w == null -> Text(t("Wetter lädt …", "Loading weather …"), fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(weatherIcon(w.code, w.isDay), fontSize = 22.sp)
                    Column {
                        Text("${w.temp}°", fontSize = 22.sp, fontWeight = FontWeight.W300, color = Color.White, maxLines = 1)
                        Text(weatherText(w.code), fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                    }
                }
                if (prefs.weather == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        w.days.drop(1).take(3).forEach { d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                                Text(weatherIcon(d.code, true), fontSize = 13.sp)
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
            Modifier.width(520.dp).background(Color(0xFF14161C), RoundedCornerShape(18.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(prefs.weatherName, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White)
            if (w == null) {
                Text(t("Wetter lädt …", "Loading weather …"), fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(weatherIcon(w.code, w.isDay), fontSize = 48.sp)
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
                        Text(weatherIcon(d.code, true), fontSize = 20.sp)
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
            Modifier.width(560.dp).background(Color(0xFF14161C), RoundedCornerShape(18.dp)).padding(24.dp),
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
