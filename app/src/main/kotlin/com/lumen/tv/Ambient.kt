package com.lumen.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/** Liest den Fokus selbst aus einem State -> nur dieser Bereich rekomponiert beim Navigieren. */
@Composable
fun AmbientHost(hero: State<HeroInfo?>, vm: HomeViewModel, prefs: Prefs, wall: Wall?) {
    val fixed = ACCENTS[prefs.accent.coerceIn(0, ACCENTS.lastIndex)].second
    val accent by produceState(fixed ?: DefaultAccent, hero.value?.key, prefs.accent) {
        value = fixed ?: hero.value?.app?.let { vm.accent(it) } ?: DefaultAccent
    }
    Ambient(prefs.look, accent, wall, prefs.dim * 0.25f, prefs.blur)
}

@Composable
fun Ambient(look: Look, accent: Color, wall: Wall?, dim: Float, blur: Int) {
    val color = animateColorAsState(accent, tween(900), label = "accent")
    val blurDp = listOf(0, 8, 16, 28)[blur.coerceIn(0, 3)].dp
    Box(Modifier.fillMaxSize().background(Ink)) {
        if (look != Look.OLED) {
            Crossfade(wall, animationSpec = tween(1200), label = "wall") { w ->
                when (w) {
                    is Wall.Still -> Image(w.bmp, null, Modifier.fillMaxSize().blur(blurDp), contentScale = ContentScale.Crop)
                    is Wall.Anim -> AnimatedWall(w.file, blurDp)
                    is Wall.Video -> VideoWall(w.file)
                    null -> Unit
                }
            }
            if (wall != null) Box(Modifier.fillMaxSize().background(Ink.copy(alpha = dim)))
        }
        when (look) {
            Look.AURORA -> Aurora(color, 1f)
            Look.GLASS -> Aurora(color, 0.5f)
            Look.MIDNIGHT -> Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(color.value.copy(alpha = 0.28f), Color.Transparent), endY = size.height * 0.7f))
            }
            Look.OLED, Look.PURE -> Unit
        }
        if (look != Look.OLED) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Ink.copy(alpha = 0.55f), Color.Transparent), endY = size.height * 0.25f))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.9f)), startY = size.height * 0.35f))
            }
        }
    }
}

@Composable
private fun Aurora(color: State<Color>, strength: Float) {
    // Langsame Drift mit nur ~8 Bildern pro Sekunde: sieht gleich aus, spart auf TV-Boxen viel CPU/GPU.
    val phase = androidx.compose.runtime.produceState(0f) {
        while (true) { value = (System.currentTimeMillis() % 40_000L) / 40_000f; kotlinx.coroutines.delay(125) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val c = color.value
        val tri = { x: Float -> 1f - kotlin.math.abs(2f * x - 1f) }
        val av = tri(phase.value)
        val bv = 1f - tri((phase.value * 1.3f) % 1f)
        blob(c, size.width * (0.2f + 0.5f * av), size.height * (0.1f + 0.3f * bv), size.maxDimension * 0.55f, 0.55f * strength)
        blob(c, size.width * (0.95f - 0.45f * bv), size.height * (0.85f - 0.35f * av), size.maxDimension * 0.5f, 0.35f * strength)
    }
}

private fun DrawScope.blob(c: Color, cx: Float, cy: Float, r: Float, alpha: Float) {
    val center = Offset(cx, cy)
    drawCircle(
        brush = Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center = center, radius = r),
        radius = r,
        center = center,
    )
}
