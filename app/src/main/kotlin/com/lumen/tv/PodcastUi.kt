package com.lumen.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000; val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun PodcastDialog(vm: HomeViewModel, prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Podcast>>(emptyList()) }
    var openShow by remember { mutableStateOf<Podcast?>(null) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var info by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val firstBtn = remember { FocusRequester() }
    val cur by Podcasts.current.collectAsState()
    val playing by Podcasts.playing.collectAsState()
    val buffering by Podcasts.buffering.collectAsState()
    val pos by Podcasts.position.collectAsState()
    val dur by Podcasts.duration.collectAsState()
    val err by Podcasts.error.collectAsState()
    val subs = prefs.podcasts

    fun openEpisodes(pc: Podcast) {
        openShow = pc; episodes = emptyList(); info = t("Lade Folgen …", "Loading episodes …")
        scope.launch {
            val r = PodcastApi.episodes(pc.feed)
            episodes = r?.second ?: emptyList()
            info = if (episodes.isEmpty()) t("Keine Folgen gefunden.", "No episodes found.") else ""
        }
    }
    fun search() {
        editing = false
        info = t("Suche …", "Searching …")
        scope.launch { results = PodcastApi.search(query); openShow = null; info = if (results.isEmpty()) t("Nichts gefunden.", "Nothing found.") else "" }
    }
    LaunchedEffect(Unit) { delay(150); runCatching { firstBtn.requestFocus() } }
    LaunchedEffect(editing) { if (editing) { delay(80); runCatching { field.requestFocus() } } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFA0B0F1E)).background(Brush.radialGradient(listOf(NeoViolet.copy(alpha = 0.3f), Color.Transparent), radius = 900f))) {
            Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                // ---------- Links: Player + Abos ----------
                Column(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(NeoViolet.copy(alpha = 0.22f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("🎙", fontSize = 22.sp) }
                        Column {
                            Text("Podcasts", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(t("Suchen, abonnieren, weiterhören", "Search, subscribe, resume"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    // Player
                    val ep = cur
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(NeoViolet.copy(alpha = 0.4f), Color(0xFF151A30)))).border(1.dp, NeoLine, RoundedCornerShape(24.dp)).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (ep == null) {
                            Box(Modifier.size(120.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("🎧", fontSize = 48.sp) }
                            Text(t("Nichts aktiv", "Nothing playing"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(t("Wähle rechts eine Folge.", "Pick an episode on the right."), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                        } else {
                            if (ep.image.isNotEmpty()) AsyncImage(ImageRequest.Builder(ctx).data(ep.image).build(), null, Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                            Text(Podcasts.show.value, fontSize = 12.sp, color = NeoCyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ep.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            // Fortschritt
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.18f))) {
                                val f = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                                Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(NeoCyan))
                            }
                            Text(fmt(pos) + " / " + (if (dur > 0) fmt(dur) else "…"), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Pill("⏪ 30") { Podcasts.seek(ctx, -30_000) }
                                Pill(if (buffering && !playing) "…" else if (playing) "⏸" else "▶", selected = true) { Podcasts.toggle(ctx) }
                                Pill("+30 ⏩") { Podcasts.seek(ctx, 30_000) }
                            }
                            Pill("■ " + t("Stopp", "Stop")) { Podcasts.stop(ctx) }
                        }
                    }
                    if (err.isNotEmpty()) Text(err, fontSize = 13.sp, color = Color(0xFFFFB37A))
                    // Abos
                    Text("★ " + t("Abonniert", "Subscribed") + " (${subs.size})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD36B))
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 6.dp)) {
                        if (subs.isEmpty()) item { Text(t("Noch keine – rechts suchen und mit ★ abonnieren.", "None yet – search on the right and subscribe with ★."), fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f)) }
                        itemsIndexed(subs) { _, pc -> ShowCard(pc, compact = true, onClick = { openEpisodes(pc) }, onFav = { vm.removePodcast(pc.feed) }, isFav = true) }
                    }
                }

                // ---------- Rechts: Suche / Folgen ----------
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("⌕ " + query.ifEmpty { t("Podcast suchen …", "Search podcast …") }, Modifier.focusRequester(firstBtn), selected = openShow == null) { editing = true }
                        openShow?.let { Pill("‹ " + t("Suche", "Search"), selected = false) { openShow = null } }
                        Spacer(Modifier.weight(1f))
                        Pill(t("Schließen", "Close")) { onClose() }
                    }
                    if (editing) BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 20.sp), cursorBrush = SolidColor(NeoCyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search(); runCatching { firstBtn.requestFocus() } }),
                        modifier = Modifier.fillMaxWidth().background(NeoGlass, RoundedCornerShape(16.dp)).border(2.dp, NeoYellow, RoundedCornerShape(16.dp)).padding(16.dp).focusRequester(field),
                    )
                    if (info.isNotEmpty()) Text(info, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))

                    val show = openShow
                    if (show == null) {
                        // Suchergebnisse
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp)) {
                            itemsIndexed(results) { _, pc ->
                                ShowCard(pc, compact = false, onClick = { openEpisodes(pc) }, onFav = {
                                    if (subs.any { it.feed == pc.feed }) vm.removePodcast(pc.feed) else vm.addPodcast(pc)
                                }, isFav = subs.any { it.feed == pc.feed })
                            }
                        }
                    } else {
                        // Folgenliste
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (show.artwork.isNotEmpty()) AsyncImage(ImageRequest.Builder(ctx).data(show.artwork).build(), null, Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f)) {
                                Text(show.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(show.author, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                            }
                            Pill(if (subs.any { it.feed == show.feed }) "✓ " + t("Abonniert", "Subscribed") else "★ " + t("Abonnieren", "Subscribe"), selected = subs.any { it.feed == show.feed }) {
                                if (subs.any { it.feed == show.feed }) vm.removePodcast(show.feed) else vm.addPodcast(show)
                            }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp)) {
                            itemsIndexed(episodes) { _, e ->
                                val saved = Podcasts.loadPos(ctx, e.audio)
                                val active = cur?.audio == e.audio
                                Surface(
                                    onClick = { Podcasts.play(ctx, e, show.title) },
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
                                    colors = ClickableSurfaceDefaults.colors(containerColor = if (active) NeoCyan.copy(alpha = 0.20f) else NeoCard, focusedContainerColor = if (active) NeoCyan.copy(alpha = 0.3f) else NeoGlassHi),
                                    border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, NeoYellow), shape = RoundedCornerShape(14.dp))),
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(if (active && playing) "⏸" else "▶", fontSize = 15.sp, color = NeoCyan)
                                        Column(Modifier.weight(1f)) {
                                            Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            val meta = listOfNotNull(e.date.take(16).ifEmpty { null }, e.durationSec.takeIf { it > 0 }?.let { "${it / 60} min" }, saved.takeIf { it > 2 }?.let { "• " + t("weiter bei ${it / 60}:${"%02d".format(it % 60)}", "resume at ${it / 60}:${"%02d".format(it % 60)}") }).joinToString("  ")
                                            if (meta.isNotEmpty()) Text(meta, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Podcast-Karte (Suche/Abos): Cover, Titel, Autor, Abo-Stern. */
@Composable
private fun ShowCard(pc: Podcast, compact: Boolean, onClick: () -> Unit, onFav: () -> Unit, isFav: Boolean) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick, onLongClick = onFav,
        modifier = Modifier.fillMaxWidth().height(if (compact) 56.dp else 72.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = NeoCard, focusedContainerColor = NeoGlassHi),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, NeoYellow), shape = shape)),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val art = if (compact) 40.dp else 52.dp
            if (pc.artwork.isNotEmpty()) AsyncImage(ImageRequest.Builder(ctx).data(pc.artwork).build(), null, Modifier.size(art).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            else Box(Modifier.size(art).background(NeoViolet.copy(alpha = 0.3f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text("🎙", fontSize = 18.sp) }
            Column(Modifier.weight(1f)) {
                Text(pc.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact && pc.author.isNotEmpty()) Text(pc.author, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (isFav) "★" else "☆", fontSize = 16.sp, color = if (isFav) Color(0xFFFFD36B) else Color.White.copy(alpha = 0.4f))
        }
    }
}
