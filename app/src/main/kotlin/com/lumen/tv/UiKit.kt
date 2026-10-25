package com.lumen.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** Fokus-Stil der Kacheln (0 Rahmen, 1 Leuchten, 2 Anheben); wird vom Startbildschirm gesetzt. */
var focusStyleGlobal by androidx.compose.runtime.mutableIntStateOf(0)
/** Gewähltes Icon-Pack (Paketname, leer = keins). */
var iconPackGlobal by androidx.compose.runtime.mutableStateOf("")
/** Deckkraft der App-Kacheln (1 = voll, kleiner = durchsichtiger). */
var tileAlphaGlobal by androidx.compose.runtime.mutableFloatStateOf(1f)

/** Einheitliche fokussierbare Karte: ruhiger weißer Rahmen, kein Wackeln, keine Farbwechsel. */
@Composable
fun FocusCard(
    w: Dp, h: Dp, glow: Color, modifier: Modifier = Modifier,
    onFocus: () -> Unit, onClick: () -> Unit, onLong: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    bg: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLong,
        modifier = modifier.size(w, h).onFocusChanged { if (it.hasFocus) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f * bg),
            focusedContainerColor = Color.White.copy(alpha = 0.12f * bg),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = when (focusStyleGlobal) { 1 -> 1.04f; 2 -> 1.10f; else -> 1f }),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(if (focusStyleGlobal == 0) 3.dp else 2.dp, Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = when (focusStyleGlobal) {
            1 -> Glow(elevationColor = Color.White.copy(alpha = 0.45f), elevation = 22.dp)
            2 -> Glow(elevationColor = Color.Black.copy(alpha = 0.65f), elevation = 26.dp)
            else -> Glow(elevationColor = Color.White.copy(alpha = 0.22f), elevation = 12.dp)
        }),
        content = content,
    )
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), shape = shape),
        ),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 15.sp, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
fun SettingLine(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 1.dp).fillMaxWidth().height(50.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = PanelFocus,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.45f)), shape = RoundedCornerShape(14.dp)),
        ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, fontSize = 15.sp, color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(24.dp))
                Text(value, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
fun Chip(text: String) {
    Box(Modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
    }
}

/** Netflix-artiger Rund-Button nur mit Symbol; beim Fokus klappt die Beschriftung aus. */
@Composable
fun IconPill(icon: Ico, label: String, modifier: Modifier = Modifier, active: Boolean = false, onClick: () -> Unit) {
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp).onFocusChanged { focused = it.hasFocus },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) Color(0xFF1DB954).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.28f),
            focusedContainerColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelIcon(icon, Modifier.size(20.dp), if (focused) Color.Black else Color.White)
            if (focused) Text(label, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color.Black, maxLines = 1)
        }
    }
}

/** Seitenabstand der Startseite (Uhr, Reihen, Statusleiste). */
val SIDE_PAD = 26.dp
