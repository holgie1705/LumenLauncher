package com.lumen.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest

fun tileDims(style: TileStyle, scale: Float): Pair<Dp, Dp> {
    val (w, h) = when (style) {
        TileStyle.BANNER -> 192 to 108
        TileStyle.WIDE -> 256 to 144
        TileStyle.COMPACT -> 144 to 81
        TileStyle.ICON -> 104 to 104
        TileStyle.ROUND -> 96 to 96
        TileStyle.POSTER -> 132 to 198
        TileStyle.CARD -> 224 to 126
    }
    return (w * scale).dp to (h * scale).dp
}

@Composable
fun AppTile(
    app: TvApp, style: TileStyle, scale: Float, vm: HomeViewModel, modifier: Modifier,
    onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit,
) {
    val art by produceState<Art?>(null, app.pkg) { value = vm.art(app) }
    val (wd, ht) = tileDims(style, scale)
    val iconOnly = style == TileStyle.ICON || style == TileStyle.ROUND
    val accent = art?.accent ?: DefaultAccent
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusCard(
            wd, ht, accent, modifier, onFocus, onClick, onLong,
            shape = if (style == TileStyle.ROUND) CircleShape else RoundedCornerShape(12.dp),
            bg = tileAlphaGlobal,
        ) {
            val a = art
            if (a != null) {
                val b = a.banner
                if (!iconOnly && b != null && style in setOf(TileStyle.BANNER, TileStyle.WIDE, TileStyle.COMPACT)) {
                    Image(b, contentDescription = app.label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = tileAlphaGlobal)
                } else {
                    Column(
                        Modifier.fillMaxSize()
                            .then(if (style == TileStyle.ROUND) Modifier.background(accent.copy(alpha = 0.35f * tileAlphaGlobal)) else if (style == TileStyle.POSTER || style == TileStyle.CARD) Modifier.background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.32f * tileAlphaGlobal), Color.Black.copy(alpha = 0.30f * tileAlphaGlobal)))) else Modifier)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val iconSize = when (style) {
                            TileStyle.ICON -> 64
                            TileStyle.ROUND -> 56
                            TileStyle.WIDE -> 56
                            TileStyle.POSTER -> 84
                            TileStyle.CARD -> 56
                            else -> 44
                        }
                        Image(a.icon, contentDescription = null, modifier = Modifier.size((iconSize * scale).dp))
                        if (!iconOnly) {
                            Spacer(Modifier.height(4.dp))
                            Text(app.label, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
        if (iconOnly) {
            Spacer(Modifier.height(8.dp))
            Text(
                app.label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.width(wd + 16.dp),
            )
        }
    }
}

@Composable
fun InputTile(input: InputSource, scale: Float, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusCard((176 * scale).dp, (72 * scale).dp, DefaultAccent, modifier, onFocus, onClick) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(input.label, fontSize = 16.sp, color = Color.White, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun WatchTile(item: WatchItem, scale: Float, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val ctx = LocalContext.current
    FocusCard((192 * scale).dp, (108 * scale).dp, DefaultAccent, modifier, onFocus, onClick) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(item.art).size(384, 216).crossfade(true).build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.8f)))
            )
        )
        Text(
            item.title, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        )
        if (item.progress > 0f) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(item.progress).height(3.dp)
                    .background(Color.White.copy(alpha = 0.9f))
            )
        }
    }
}

@Composable
fun NowTile(item: NowItem, scale: Float, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    FocusCard((192 * scale).dp, (108 * scale).dp, DefaultAccent, modifier, onFocus, onClick, onLong) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(if (item.playing) t("Läuft", "Playing") else t("Pausiert", "Paused"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            Column {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
