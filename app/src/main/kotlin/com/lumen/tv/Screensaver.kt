package com.lumen.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    // Bildervorrat einmal pro Start zusammenstellen (gemischt aus mehreren Quellen)
    val pool by produceState<List<RemoteWall>>(emptyList()) {
        val parts = listOf(
            WallSources.list(WallSource.BING, 0),
            WallSources.list(WallSource.PICSUM, (0..40).random()),
            WallSources.list(WallSource.WIKI, (0..40).random()),
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

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF101830), Color(0xFF05060A))))) {
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
        // Verlauf unten, damit Uhr und Text immer lesbar sind
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))

        // Uhr, Datum, Wetter
        Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 48.dp)) {
            Text(
                now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", Lang.locale)),
                fontSize = 110.sp, fontWeight = FontWeight.W200, color = Color.White, letterSpacing = (-3).sp,
            )
            Text(
                now.format(DateTimeFormatter.ofPattern(t("EEEE, d. MMMM", "EEEE, MMMM d"), Lang.locale)),
                fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f),
            )
            if (weather != null) Row(
                Modifier.padding(top = 16.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(weatherIcon(weather.code, weather.isDay), fontSize = 30.sp)
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
    }
}
