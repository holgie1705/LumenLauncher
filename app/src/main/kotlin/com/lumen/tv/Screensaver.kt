package com.lumen.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Bildschirmschoner „Galerie“: schöne Fotos aus dem Netz (Bing-Tagesbilder, Unsplash, Wikimedia-Qualitätsbilder),
 * weicher Überblend- und Zoom-Effekt, große Uhr, Datum und Wetter in einer Glaskarte.
 * Bilder werden vorab geladen; ohne Internet zeigt er das eigene Wallpaper bzw. einen dunklen Verlauf.
 */
@Composable
fun NetScreensaver(wall: Wall?, weather: WeatherInfo?, place: String, is24: Boolean) {
    val ctx = LocalContext.current
    // Bildervorrat einmal pro Start zusammenstellen (gemischt aus mehreren Quellen – inkl. Weltall)
    val pool by produceState<List<RemoteWall>>(emptyList()) {
        val parts = listOf(
            WallSources.list(WallSource.BING, 0),
            WallSources.list(WallSource.PICSUM, (0..40).random()),
            WallSources.list(WallSource.WIKI, (0..40).random()),
            WallSources.list(WallSource.NASA, (0..20).random()),
        )
        value = parts.flatten().filter { it.full.isNotEmpty() }.shuffled()
    }
    var idx by remember { mutableStateOf(0) }
    LaunchedEffect(pool) {
        if (pool.isEmpty()) return@LaunchedEffect
        while (true) {
            // nächstes Bild schon im Hintergrund laden, damit der Wechsel ohne Ruckler passiert
            val next = pool[(idx + 1) % pool.size]
            runCatching { ctx.imageLoader.enqueue(ImageRequest.Builder(ctx).data(next.full).addHeader("User-Agent", WallSources.userAgent()).build()) }
            delay(45_000)
            idx = (idx + 1) % pool.size
        }
    }
    val now by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(5_000) } }
    val zoom = rememberInfiniteTransition(label = "kenburns")
    val s by zoom.animateFloat(1f, 1.10f, infiniteRepeatable(tween(45_000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
    // Fortschrittslinie bis zum nächsten Bild (dezent, am unteren Rand)
    val prog by zoom.animateFloat(0f, 1f, infiniteRepeatable(tween(45_000, easing = LinearEasing), RepeatMode.Restart), label = "prog")
    // Sanftes Einblenden beim Start
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(1400, easing = LinearEasing), label = "appear")
    // Anti-Einbrennen: Uhr/Wetter-Karte wandert mit jedem Bild in der Höhe (unten → Mitte → oben)
    val anchors = remember { listOf(0.82f, 0.05f, -0.80f) }
    val targetBias = anchors[idx % anchors.size]
    val vBias by animateFloatAsState(targetBias, tween(2600, easing = LinearEasing), label = "drift")

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF101830), Color(0xFF05060A)))).graphicsLayer { alpha = appear }) {
        val cur = pool.getOrNull(idx)
        Crossfade(targetState = cur, animationSpec = tween(2500), label = "photo") { w ->
            if (w != null) AsyncImage(
                model = ImageRequest.Builder(ctx).data(w.full).addHeader("User-Agent", WallSources.userAgent()).crossfade(false).build(),
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = s; scaleY = s },
            ) else (wall as? Wall.Still)?.let { st ->
                androidx.compose.foundation.Image(st.bmp, null, Modifier.fillMaxSize().graphicsLayer { scaleX = s; scaleY = s }, contentScale = ContentScale.Crop)
            }
        }
        // Lesbarkeits-Scrim: links (Uhr steht immer links) + unten
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.45f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))))

        val textShadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 18f)
        // Uhr, Datum, Wetter – driftet in der Höhe (Anti-Einbrennen)
        Column(Modifier.align(BiasAlignment(-1f, vBias)).padding(horizontal = 56.dp, vertical = 52.dp)) {
            Text(
                now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", Lang.locale)),
                fontSize = 118.sp, fontWeight = FontWeight.W200, color = Color.White, letterSpacing = (-3).sp,
                style = TextStyle(shadow = textShadow),
            )
            Text(
                now.format(DateTimeFormatter.ofPattern(t("EEEE, d. MMMM", "EEEE, MMMM d"), Lang.locale)),
                fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f),
                style = TextStyle(shadow = textShadow),
            )
            if (weather != null) Row(
                Modifier.padding(top = 16.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WeatherGlyph(weather.code, weather.isDay, Modifier.size(36.dp))
                Text("${weather.temp}°", fontSize = 30.sp, fontWeight = FontWeight.Light, color = Color.White)
                Column {
                    Text(weatherText(weather.code), fontSize = 14.sp, color = Color.White, maxLines = 1)
                    Text(
                        place.ifEmpty { "" } + weather.days.firstOrNull()?.let { d -> (if (place.isNotEmpty()) " · " else "") + "${d.min}° / ${d.max}°" }.orEmpty(),
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1,
                    )
                }
            }
        }
        // Bildnachweis klein unten rechts
        cur?.let { w ->
            Column(Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 36.dp).widthIn(max = 520.dp), horizontalAlignment = Alignment.End) {
                Text(w.title, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(w.credit, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Dezente Fortschrittslinie bis zum nächsten Bild
        if (pool.isNotEmpty()) Box(
            Modifier.align(Alignment.BottomStart).fillMaxWidth(prog.coerceIn(0f, 1f)).height(2.dp)
                .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = 0.3f))))
        )
    }
}
