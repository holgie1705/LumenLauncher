package com.lumen.tv

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import kotlin.math.abs
import kotlin.math.sign

// =====================================================================================================
//  Layout „Spatial": die gewählte Reihe steht als 3D-Bogen im Raum (mit Spiegelung, Glanz und Leuchtring),
//  die übrigen Reihen liegen flach darunter. Wallpaper wird in der Farbe der gewählten App getönt,
//  alle Menüs werden zu Glas. Alle Reihenarten (Apps, Widgets, Radio, Weiter ansehen, Web) laufen weiter.
// =====================================================================================================

/** Spatial-Look aktiv (Layout „Spatial"): Menüs und Panels werden dann zu Glas. */
var spatialGlobal by mutableStateOf(true)   // neues Design immer aktiv

/** Plus Jakarta Sans (variable Schrift) für klassische Android-Views/Paints – gleiche Schrift wie im Spatial-Design. */
fun jakartaFace(ctx: android.content.Context): android.graphics.Typeface =
    runCatching { ctx.resources.getFont(R.font.jakarta) }.getOrDefault(android.graphics.Typeface.DEFAULT)
fun wght(w: Int) = "'wght' $w"
/** Farbe der gerade gewählten Kachel (aus dem App-Banner), für Tönung und Menü-Akzent. */
var spatialAccentGlobal by mutableStateOf(DefaultAccent)
/** Effekte: 0 = ruhig (ohne Spiegelung, Glanz, Partikel), 1 = voll. */
var spatialFxGlobal by mutableIntStateOf(1)
/** true = im 3D-Bogen den eigenen Kachel-Stil + Abstände nutzen statt der Glas-Kacheln aus dem Entwurf. */
var spatialOwnGlobal by androidx.compose.runtime.mutableStateOf(false)
var spatialTintGlobal by androidx.compose.runtime.mutableStateOf(false)
var spatialSingleGlobal by androidx.compose.runtime.mutableStateOf(true)
var spatialPosGlobal by androidx.compose.runtime.mutableIntStateOf(2)
var spatialDotsGlobal by androidx.compose.runtime.mutableStateOf(false)

/** Glas-Unschärfe hinter Dialogfenstern (Android 12+), nur im Spatial-Look. */
fun spatialBlur(w: Window?, radius: Int = 42) {
    if (w == null || !spatialGlobal || Build.VERSION.SDK_INT < 31) return
    runCatching {
        w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        w.attributes = w.attributes.apply { blurBehindRadius = radius }
    }
}

private class SpatialCell(val key: String, val w: Dp, val h: Dp, val content: @Composable (Modifier) -> Unit)

/** Zellen einer Reihe. Größen kommen aus [c]; die Klick-/Fokus-Aktionen lesen immer den aktuellen Kontext aus [cs]. */
private fun cellsOf(row: RowUi, c: LayoutCtx, cs: State<LayoutCtx>): List<SpatialCell> {
    val (tw, th) = tileDims(c.style, c.scale)
    // Einheitliche Größe für ALLE Kacheln (Apps, Weiter ansehen, Widgets, Radio, Infos …)
    val uw = if (spatialOwnGlobal) tw else (196 * c.scale).dp
    val uh = if (spatialOwnGlobal) th else (122 * c.scale).dp
    val fix = Modifier.size(uw, uh)
    return when (row) {
        is AppRowUi -> if (spatialOwnGlobal) {
            // Eigener Stil: dieselben Kacheln wie in der Hauptversion (Banner, Icons, Rund, Poster …)
            val lbl = if (c.style == TileStyle.ICON || c.style == TileStyle.ROUND) 26.dp else 0.dp
            row.apps.map { a -> SpatialCell(a.pkg, tw, th + lbl) { m -> AppCell(a, row, cs.value, m) } }
        } else {
            val aw = (196 * c.scale).dp; val ah = (122 * c.scale).dp
            row.apps.map { a ->
                SpatialCell(a.pkg, aw, ah) { m ->
                    val cc = cs.value
                    SpatialAppTile(
                        a, cc.vm, aw, ah, m,
                        onFocus = { cc.onHero(HeroInfo(a.pkg, a.label, app = a)) },
                        onClick = { cc.vm.open(a) },
                        onLong = { cc.onMenu(a, row) },
                    )
                }
            }
        }
        is InputRowUi -> row.inputs.map { s -> SpatialCell(s.id, uw, uh) { m -> InputCell(s, cs.value, fix.then(m)) } }
        is WatchRowUi -> row.items.mapIndexed { i, w -> SpatialCell("w$i", uw, uh) { m -> WatchCell(w, row, i, cs.value, fix.then(m)) } }
        is NowRowUi -> row.items.mapIndexed { i, n -> SpatialCell("n$i:${n.pkg}", uw, uh) { m -> NowCell(n, cs.value, fix.then(m)) } }
        is WebRowUi -> row.widgets.map { w -> SpatialCell(w.id, uw, uh) { m -> WebCell(w, cs.value, fix.then(m)) } }
        is InfoRowUi -> row.kinds.map { k -> SpatialCell("info_$k", uw, uh) { m -> val cc = cs.value; InfoWidgetCell(k, cc.vm, fix.then(m), cc.onHero, uw to uh) } }
        is RadioRowUi -> listOf(SpatialCell("radio_add", uw, uh) { m -> SpatialRadioTile(null, uw, uh, m, cs.value) }) +
            row.stations.map { st -> SpatialCell(st.uuid, uw, uh) { m -> SpatialRadioTile(st, uw, uh, m, cs.value) } }
    }
}

private const val FLAT = 0.62f

@Composable
fun SpatialLayout(rows: List<RowUi>, c: LayoutCtx, firstFocus: FocusRequester?, modifier: Modifier) {
    if (rows.isEmpty()) return
    var cur by remember { mutableIntStateOf(0) }
    val cols = remember { mutableStateMapOf<String, Int>() }
    val fx = spatialFxGlobal
    val inf = rememberInfiniteTransition(label = "spatial")
    val sheen = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(6500, easing = LinearEasing)), label = "sheen")
    val curIdx = cur.coerceIn(0, rows.lastIndex)

    BoxWithConstraints(modifier.fillMaxWidth().clipToBounds()) {
        val boxW = maxWidth
        val boxH = maxHeight
        val cs = androidx.compose.runtime.rememberUpdatedState(c)
        val own = spatialOwnGlobal
        val cellLists = remember(rows, c.style, c.scale, own) { rows.map { cellsOf(it, c, cs) } }   // nicht bei jedem Fokuswechsel neu bauen
        val rowH = cellLists.map { l -> l.maxOfOrNull { it.h } ?: 0.dp }

        // Senkrechte Lage jeder Reihe: gewählte Reihe mittig als Bogen, darunter flach, darüber ausgeblendet.
        val tops = Array(rows.size) { 0.dp }
        val single = spatialSingleGlobal
        val hc = rowH[curIdx]
        val arcTop = when (spatialPosGlobal) {   // gilt mit und ohne „Nur gewählte Reihe“
            0 -> 70.dp                                                   // oben: direkt unter der Kopfzeile (Platz für den Reihen-Titel)
            1 -> (boxH - hc) / 2                                         // Mitte
            else -> (boxH - hc * 1.09f - 4.dp).coerceAtLeast(40.dp)      // unten: direkt über dem News-Ticker (Platz für die vergrößerte Fokus-Kachel)
        }
        tops[curIdx] = arcTop
        var y = arcTop + rowH[curIdx] * 1.10f + 62.dp
        for (k in curIdx + 1 until rows.size) { tops[k] = y; y += rowH[k] * FLAT + 54.dp }
        var yu = arcTop
        for (k in curIdx - 1 downTo 0) { yu -= rowH[k] * FLAT + 70.dp; tops[k] = yu }

        rows.forEachIndexed { k, row ->
            key(row.id) {
                val dr = k - curIdx
                val focusRow = dr == 0
                val spD = spring<Dp>(dampingRatio = 1f, stiffness = 210f)
                val spF = spring<Float>(dampingRatio = 1f, stiffness = 210f)
                val top by animateDpAsState(tops[k], spD, label = "top")
                val rowS by animateFloatAsState(if (focusRow) 1f else FLAT, spF, label = "rowS")
                val arcF by animateFloatAsState(if (focusRow) 1f else 0f, spF, label = "arcF")
                val rowA by animateFloatAsState(
                    when { dr == 0 -> 1f; single || dr < 0 -> 0f; dr == 1 -> 1f; dr == 2 -> 0.55f; else -> 0f }, tween(if (single) 300 else 420), label = "rowA",
                )
                val cells = cellLists[k]
                val fc = (cols[row.id] ?: 0).coerceIn(0, (cells.size - 1).coerceAtLeast(0))
                val pitch = (cells.firstOrNull()?.w ?: 0.dp) + if (spatialOwnGlobal) c.gap.coerceAtLeast(8.dp) else 22.dp
                val h = rowH[k]

                // Reihen-Titel (nicht mitskaliert)
                Text(
                    row.title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                    color = Color.White.copy(alpha = 0.85f), maxLines = 1,
                    modifier = Modifier
                        .offset { IntOffset((SIDE_PAD + 14.dp).roundToPx(), (top - (if (focusRow) h * 0.06f + 34.dp else 26.dp)).roundToPx()) }
                        .graphicsLayer { alpha = rowA },
                )

                Box(
                    Modifier.offset { IntOffset(0, top.roundToPx()) }.width(boxW).height(h)
                        .graphicsLayer { scaleX = rowS; scaleY = rowS; transformOrigin = TransformOrigin(0f, 0f); alpha = rowA },
                ) {
                    if (cells.isNotEmpty()) {
                        val lo = if (focusRow) (fc - 5).coerceAtLeast(0) else fc
                        val hi = if (focusRow) (fc + 5).coerceAtMost(cells.lastIndex) else (fc + 7).coerceAtMost(cells.lastIndex)
                        for (i in lo..hi) {
                            val cell = cells[i]
                            key(cell.key) {
                                val d = (i - fc).toFloat()
                                val xTarget = if (focusRow) boxW / 2 - cell.w / 2 + pitch * d else (SIDE_PAD + 14.dp) / FLAT + pitch * d
                                val x by animateDpAsState(xTarget, spD, label = "x")
                                val dA by animateFloatAsState(if (focusRow) d else 0f, spF, label = "d")
                                var focused by remember { mutableStateOf(false) }
                                val pitchPxVal = pitch
                                val refl = fx > 0 && focusRow && abs(d) < 1.5f
                                Box(
                                    Modifier
                                        .offset { IntOffset(x.roundToPx(), ((h - cell.h) / 2).roundToPx()) }
                                        .zIndex(20f - abs(d))
                                        .onFocusChanged {
                                            focused = it.hasFocus
                                            if (it.hasFocus) { cur = k; cols[row.id] = i }
                                        }
                                        .graphicsLayer {
                                            val aa = abs(dA)
                                            rotationY = (-dA * 36f).coerceIn(-62f, 62f) * arcF
                                            cameraDistance = 5.5f * density
                                            // wie im Prototyp: Kacheln weichen nach hinten in den Raum (perspektivisch kleiner und zur Mitte gezogen)
                                            val depth = 1f / (1f + 0.20f * aa)
                                            val pitchPx = pitchPxVal.toPx()
                                            val s = (1.16f - 0.16f * aa.coerceAtMost(1f)) * depth
                                            val sc = 1f + (s - 1f) * arcF
                                            scaleX = sc; scaleY = sc
                                            translationX = (-dA * pitchPx * (1f - depth) - sign(dA) * aa * aa * 10.dp.toPx()) * arcF
                                            alpha = 1f + ((1f - aa * 0.16f).coerceIn(0f, 1f) - 1f) * arcF
                                        }
                                        .drawWithContent {
                                            drawContent()
                                            val w = size.width
                                            val hh = size.height
                                            // Spiegelung auf dem „Boden": in Bändern immer schwächer
                                            if (refl) {
                                                val gap = 10.dp.toPx()
                                                val bands = 2
                                                for (b in 0 until bands) {
                                                    val y0 = hh + gap + hh * 0.45f * b / bands
                                                    val y1 = hh + gap + hh * 0.45f * (b + 1) / bands
                                                    clipRect(left = -w, top = y0, right = w * 2f, bottom = y1) {
                                                        withTransform({ scale(1f, -1f, pivot = Offset(w / 2f, hh + gap / 2f)) }) {
                                                            val p = Paint().apply { alpha = 0.20f * (1f - b / bands.toFloat()) }
                                                            drawContext.canvas.saveLayer(Rect(-w, 0f, w * 2f, hh), p)
                                                            this@drawWithContent.drawContent()
                                                            drawContext.canvas.restore()
                                                        }
                                                    }
                                                }
                                            }
                                            // sanfter Glanzstreifen auf der gewählten Kachel (selten, ohne Pulsieren)
                                            if (focused && focusRow && fx > 0) {
                                                val r = 14.dp.toPx()
                                                val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, hh, CornerRadius(r))) }
                                                clipPath(clip) {
                                                    val sx = (-0.45f + 4.5f * sheen.value) * w   // läuft in den ersten ~40 % durch, dann Ruhe
                                                    drawRect(
                                                        Brush.linearGradient(
                                                            listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
                                                            start = Offset(sx, 0f), end = Offset(sx + w * 0.35f, hh),
                                                        ),
                                                    )
                                                }
                                            }
                                        },
                                ) { cell.content(if (k == 0 && i == 0) focusMod(0, firstFocus) else Modifier) }
                            }
                        }
                    }
                }
            }
        }

    }
}

/** Farbe für Kacheln ohne App-Banner (Widgets, Radio, Weiter ansehen …). */
private fun kindColor(key: String): Color = when {
    key.contains("weather") || key.contains("wetter") -> Color(0xFFFF9D2E)
    key.contains("football") || key.contains("fuss") -> Color(0xFFC1121F)
    key.startsWith("r:") -> Color(0xFFFF6A00)
    key.startsWith("w:") -> Color(0xFF7B2CFF)
    key.startsWith("n:") -> Color(0xFF00C2FF)
    key.startsWith("web:") -> Color(0xFF1F5CFF)
    key.isEmpty() -> DefaultAccent
    else -> listOf(Color(0xFF1F5CFF), Color(0xFF00B4D8), Color(0xFF7B2CFF), Color(0xFF2BD4A0), Color(0xFFFF2F92))[abs(key.hashCode()) % 5]
}

/**
 * Hintergrund-Ebene über dem Wallpaper: Tönung in der Farbe der gewählten Kachel, weiches Licht von unten,
 * Vignette, aufsteigende Lichtpunkte und der Name der Auswahl riesig als feine Kontur.
 */
@Composable
fun SpatialBackdrop(hero: State<HeroInfo?>, vm: HomeViewModel) {
    val h = hero.value
    val target by produceState(spatialAccentGlobal, h?.key) {
        val app = h?.app
        value = if (app != null) runCatching { vm.accent(app) }.getOrNull() ?: DefaultAccent else kindColor(h?.key.orEmpty())
    }
    SideEffect { spatialAccentGlobal = target }
    val col by animateColorAsState(target, tween(1200), label = "tint")
    val tint = spatialTintGlobal
    val dots = spatialDotsGlobal
    val inf = rememberInfiniteTransition(label = "backdrop")
    val tt = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "t")
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.22f))
            if (tint) {
                drawRect(col.copy(alpha = 0.12f), blendMode = BlendMode.Color)
                drawRect(
                    Brush.radialGradient(listOf(col.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width / 2f, size.height * 1.05f), radius = size.width * 0.55f),
                )
            }
            drawRect(
                Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f)), center = Offset(size.width / 2f, size.height * 0.50f), radius = size.width * 0.66f),
            )
        }
        if (dots) Canvas(Modifier.fillMaxSize()) {
            run {
                val t = tt.value
                for (i in 0 until 10) {
                    val ph = (t + i * 0.1f) % 1f
                    val x = size.width * ((i * 0.113f + 0.06f) % 1f)
                    val yy = size.height * (1.02f - ph * 0.9f)
                    val a = (if (ph < 0.15f) ph / 0.15f else 1f - (ph - 0.15f) / 0.85f) * 0.7f
                    drawCircle(Color.White.copy(alpha = a), radius = (1.5f + i % 3) * density, center = Offset(x, yy))
                }
            }
        }
    }
}
