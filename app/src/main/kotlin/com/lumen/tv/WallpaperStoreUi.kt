package com.lumen.tv

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Wallpaper-Galerie: öffentliche Quellen durchstöbern, mit OK direkt in den Wallpaper-Ordner laden. */
@Composable
fun WallpaperStoreDialog(vm: HomeViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(WallSource.BING) }
    var page by remember { mutableIntStateOf(0) }
    var walls by remember { mutableStateOf<List<RemoteWall>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf<RemoteWall?>(null) }
    val first = remember { FocusRequester() }
    val owned = remember { mutableStateOf(ownedNames(ctx)) }

    // Eigener, kleiner Bild-Cache nur für diese Galerie; wird beim Schließen freigegeben.
    val loader = remember {
        ImageLoader.Builder(ctx).memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.05).build() }.build()
    }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }

    LaunchedEffect(source, page) {
        loading = true; status = ""
        walls = WallSources.list(source, page)
        loading = false
        if (walls.isEmpty()) status = t(
            "Keine Bilder geladen – ist der TV online? Quelle kurz nicht erreichbar? Probier eine andere Quelle.",
            "No pictures loaded – is the TV online? Source may be down. Try another source.",
        )
    }
    LaunchedEffect(walls) { if (walls.isNotEmpty()) { delay(150); runCatching { first.requestFocus() } } }

    fun download(w: RemoteWall) {
        scope.launch {
            status = t("Lade herunter …", "Downloading …")
            val f = WallSources.download(ctx, w)
            if (f != null) {
                owned.value = ownedNames(ctx)
                vm.reloadWallpapers()
                status = t("Gespeichert – wird jetzt als Wallpaper verwendet (Wechsel siehe Wallpaper-Wechsel).", "Saved – now part of your wallpapers (see wallpaper change interval).")
            } else status = t("Download fehlgeschlagen.", "Download failed.")
        }
    }

    fun removeWall(w: RemoteWall) {
        scope.launch {
            status = t("Entferne …", "Removing …")
            val ok = WallSources.remove(ctx, w)
            owned.value = ownedNames(ctx)
            vm.reloadWallpapers()
            status = if (ok) t("Entfernt – wird nicht mehr als Wallpaper genutzt.", "Removed – no longer used as wallpaper.")
            else t("Konnte nicht entfernt werden.", "Could not remove it.")
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.BRUSH, t("Wallpaper-Galerie", "Wallpaper gallery"), t("Bing, NASA, Wikimedia & mehr", "Bing, NASA, Wikimedia & more"), width = 1120.dp) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WallSource.entries.forEach { s ->
                        Pill((if (s == source) "● " else "") + s.label) { if (s != source) { source = s; page = 0 } }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(walls, key = { it.id }) { w ->
                            val have = "dl_" + w.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60) in owned.value
                            val mod = if (w === walls.firstOrNull()) Modifier.focusRequester(first) else Modifier
                            Surface(
                                onClick = { if (have) removeWall(w) else download(w) },
                                modifier = mod.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { if (it.isFocused) focused = w },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                                colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.06f)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                                border = ClickableSurfaceDefaults.border(
                                    focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), shape = RoundedCornerShape(12.dp)),
                                ),
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(ctx).data(w.thumb).addHeader("User-Agent", WallSources.userAgent()).size(320, 180).build(),
                                        imageLoader = loader, contentDescription = w.title,
                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                                    )
                                    if (have) Text(
                                        "✓", fontSize = 18.sp, color = Color.White,
                                        modifier = Modifier.padding(8.dp).background(Color(0xCC2FBF71), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (loading) Text(t("Lade Bilder …", "Loading pictures …"), fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(8.dp))
                }
                focused?.let {
                    val fHave = "dl_" + it.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60) in owned.value
                    Text(
                        (if (fHave) t("✓ aktiv · OK = entfernen  ·  ", "✓ active · OK = remove  ·  ") else t("OK = hinzufügen  ·  ", "OK = add  ·  ")) + it.title,
                        fontSize = 15.sp, color = if (fHave) Color(0xFF7CE0A6) else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(it.credit, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (status.isNotEmpty()) Text(status, fontSize = 14.sp, color = Color(0xFFFFD27A))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (WallSources.pages(source) > 1) {
                        Pill(t("‹ Zurück", "‹ Back")) { if (page > 0) page-- }
                        Pill(t("Weiter ›", "Next ›")) { if (page < WallSources.pages(source) - 1) page++ }
                    }
                    Pill(t("Schließen", "Close")) { onClose() }
                }
                Text(
                    t(
                        "OK fügt ein Bild hinzu – bei einem Bild mit ✓ nimmt OK es wieder heraus. Bilder gehören ihren Urhebern (Lizenz siehe Zeile oben) – gedacht für den privaten Gebrauch auf diesem TV.",
                        "OK adds a picture – on one marked ✓ it removes it again. Pictures belong to their authors (license shown above) – meant for private use on this TV.",
                    ),
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}

private fun ownedNames(ctx: Context): Set<String> =
    ctx.getExternalFilesDir("wallpapers")?.listFiles()?.map { File(it.name).nameWithoutExtension }?.toSet().orEmpty()
