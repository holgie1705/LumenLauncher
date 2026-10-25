package com.lumen.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.tv.material3.Text
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.memory.MemoryCache
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun stationColor(name: String): Color {
    val palette = listOf(0xFF3B5BDB, 0xFF7048E8, 0xFF0CA678, 0xFFE8590C, 0xFFD6336C, 0xFF1098AD, 0xFFC92A2A, 0xFF5F3DC4)
    return Color(palette[(name.hashCode() and 0x7fffffff) % palette.size])
}

/** Kachel eines gespeicherten Senders: Logo (falls vorhanden) auf Farbverlauf, ♪-Anzeige bei laufender Wiedergabe. */
@Composable
fun RadioTile(s: Station, scale: Float, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val now by Radio.now.collectAsState()
    val playing by Radio.playing.collectAsState()
    val active = now?.uuid == s.uuid
    val base = stationColor(s.name)
    FocusCard((192 * scale).dp, (108 * scale).dp, base, modifier, onFocus, onClick, onLong) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.45f), Color(0xFF111319))))) {
            StationLogo(s, Modifier.align(Alignment.Center).size((64 * scale).dp), (28 * scale).sp)
            Text(
                s.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            )
            if (active) Text(
                if (playing) "♪ " + t("läuft", "playing") else "… " + t("lädt", "loading"),
                fontSize = 11.sp, color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Erste Kachel der Radio-Reihe: öffnet die Sendersuche. */
@Composable
fun RadioAddTile(scale: Float, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    FocusCard((192 * scale).dp, (108 * scale).dp, DefaultAccent, modifier, onFocus, onClick) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PanelIcon(Ico.RADIO, Modifier.size((34 * scale).dp))
            Text(t("Sender suchen", "Find stations"), fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** Sendersuche: Name eintippen (z. B. SWR3, SR1, Radio Bollerwagen, Malle), OK = abspielen, "Speichern" legt ihn in die Radio-Reihe. */
@Composable
fun RadioSearchDialog(vm: HomeViewModel, prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var favMode by remember { mutableStateOf(prefs.stations.isNotEmpty()) }
    var results by remember { mutableStateOf<List<Station>>(emptyList()) }
    var info by remember { mutableStateOf(t("Lade beliebte deutsche Sender …", "Loading popular German stations …")) }
    val field = remember { FocusRequester() }
    val now by Radio.now.collectAsState()
    val playing by Radio.playing.collectAsState()
    val buffering by Radio.buffering.collectAsState()
    val track by Radio.track.collectAsState()
    val err by Radio.error.collectAsState()
    val saved = prefs.stations.map { it.uuid }.toSet()
    val shown = if (favMode) prefs.stations else results

    val loader = remember { ImageLoader.Builder(ctx).memoryCache { MemoryCache.Builder(ctx).maxSizePercent(0.03).build() }.build() }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }

    fun search(q: String) {
        favMode = false
        info = t("Suche …", "Searching …")
        scope.launch {
            val found = RadioApi.search(q)
            // Ohne Suchbegriff stehen die gespeicherten Sender ganz oben.
            results = if (q.isBlank()) prefs.stations + found.filter { f -> prefs.stations.none { it.uuid == f.uuid } } else found
            info = if (results.isEmpty()) t("Nichts gefunden – oder der Senderdienst ist gerade nicht erreichbar.", "Nothing found – or the station service is unreachable.") else ""
        }
    }
    val firstBtn = remember { FocusRequester() }
    LaunchedEffect(Unit) { search(""); favMode = prefs.stations.isNotEmpty(); delay(150); runCatching { firstBtn.requestFocus() } }
    LaunchedEffect(editing) { if (editing) { delay(80); runCatching { field.requestFocus() } } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.98f))) {
            Column(Modifier.align(Alignment.Center).width(820.dp).fillMaxSize().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Radio", "Radio"), fontSize = 26.sp, fontWeight = FontWeight.W300, color = Color.White)
                if (!editing) {
                    SettingLine(
                        t("Suchen …", "Search …"),
                        query.ifEmpty { t("OK drücken, um einen Sendernamen einzutippen", "Press OK to type a station name") },
                    ) { editing = true }
                } else BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { editing = false; search(query); runCatching { firstBtn.requestFocus() } }),
                    modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(14.dp).focusRequester(field),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("★ " + t("Favoriten", "Favorites") + " (${prefs.stations.size})", Modifier.focusRequester(firstBtn)) { favMode = true }
                    Pill(t("Suchen", "Search")) { editing = false; search(query) }
                    Pill(t("Beliebt", "Popular")) { query = ""; search("") }
                    if (now != null) Pill("■ " + t("Stopp", "Stop")) { Radio.stop(ctx) }
                    Pill(t("Schließen", "Close")) { onClose() }
                }
                now?.let {
                    Text(
                        "♪ ${it.name}" + when { buffering && !playing -> "  (" + t("lädt …", "loading …") + ")"; track.isNotEmpty() -> "  ·  $track"; else -> "" },
                        fontSize = 15.sp, color = Color(0xFF7CE0A3), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (err.isNotEmpty()) Text(err, fontSize = 14.sp, color = Color(0xFFFFB37A))
                if (favMode) Text(
                    if (prefs.stations.isEmpty()) t("Noch keine Favoriten – suche einen Sender und wähle „Speichern“.", "No favorites yet – search a station and choose “Save”.")
                    else "★ " + t("Deine gespeicherten Sender", "Your saved stations"),
                    fontSize = 14.sp, color = Color(0xFFFFD36B),
                )
                if (info.isNotEmpty() && !favMode) Text(info, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    itemsIndexed(shown, key = { _, s -> s.uuid }) { _, s ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                SettingLine((if (now?.uuid == s.uuid) "♪ " else "") + s.name, s.info) { Radio.toggle(ctx, s) }
                            }
                            if (s.uuid in saved) Pill("✓ " + t("Gespeichert", "Saved")) { vm.removeStation(s.uuid) }
                            else Pill("★ " + t("Speichern", "Save")) { vm.addStation(s) }
                        }
                    }
                }
                Text(
                    t("OK auf einen Sender spielt ihn ab – auch weiter, wenn du eine andere App öffnest. Gespeicherte Sender liegen in der Reihe „Radio“ auf dem Startbildschirm.",
                        "OK on a station plays it – it keeps playing when you open another app. Saved stations appear in the “Radio” row on the home screen."),
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/** Kleiner animierter Equalizer (3 Balken) – läuft nur, solange wirklich gespielt wird. */
@Composable
private fun Equalizer(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val phase by androidx.compose.runtime.produceState(0f, active) {
        while (active) { value = (System.currentTimeMillis() % 900L) / 900f; delay(120) }
    }
    androidx.compose.foundation.Canvas(modifier) {
        val bw = size.width / 5f
        for (i in 0..2) {
            val f = if (active) 0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin((phase + i * 0.33f) * Math.PI.toFloat() * 2f)) else 0.3f
            val h = size.height * f
            drawRoundRect(color, androidx.compose.ui.geometry.Offset(i * bw * 2f, size.height - h), androidx.compose.ui.geometry.Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2f))
        }
    }
}

/** Moderne Mini-Karte „Jetzt läuft“: Logo, Sendername, Song, Equalizer und Stopp-Knopf. OK öffnet den Radio-Dialog. */
@Composable
fun NowPlayingCard(enabled: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val now by Radio.now.collectAsState()
    val playing by Radio.playing.collectAsState()
    val track by Radio.track.collectAsState()
    val st = now
    if (!enabled || st == null) return
    val base = stationColor(st.name)
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.padding(start = 14.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.tv.material3.Surface(
            onClick = onOpen,
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = shape),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.40f),
                focusedContainerColor = Color.White.copy(alpha = 0.20f),
            ),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), shape = shape),
                focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, Color.White), shape = shape),
            ),
        ) {
            Row(Modifier.padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp).widthIn(max = 380.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StationLogo(st, Modifier.size(46.dp), 16.sp)
                Column(Modifier.weight(1f, fill = false)) {
                    Text(st.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (!playing) t("Verbinde …", "Connecting …") else track.ifEmpty { t("Live", "Live") },
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Equalizer(playing, Color(0xFF7CE0A3), Modifier.size(20.dp, 18.dp))
            }
        }
        androidx.tv.material3.Surface(
            onClick = { Radio.stop(ctx) },
            modifier = Modifier.size(40.dp),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = androidx.compose.foundation.shape.CircleShape),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.40f),
                focusedContainerColor = Color.White,
            ),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).background(Color(0xFFFF5A5F), RoundedCornerShape(2.dp)))
            }
        }
    }
}

/** Logo-Kandidaten: gemeldetes Favicon (auch als https), dann das Seiten-Icon der Sender-Homepage bzw. der Stream-Domain. */
private fun logoCandidates(s: Station): List<String> {
    val out = mutableListOf<String>()
    if (s.favicon.startsWith("http")) {
        if (s.favicon.startsWith("http://")) out += "https://" + s.favicon.removePrefix("http://")
        out += s.favicon
    }
    fun host(u: String) = runCatching { java.net.URI(u).host }.getOrNull()?.removePrefix("www.")
    fun root(h: String) = h.split('.').takeLast(2).joinToString(".")
    listOfNotNull(host(s.home), host(s.url)?.let { root(it) }).distinct().forEach { out += "https://www.google.com/s2/favicons?domain=$it&sz=128" }
    return out.distinct()
}

/** Senderlogo auf hellem, abgerundetem Grund; fällt Schritt für Schritt auf Alternativen und zuletzt auf Anfangsbuchstaben zurück. */
@Composable
fun StationLogo(s: Station, modifier: Modifier, initialSize: androidx.compose.ui.unit.TextUnit) {
    val ctx = LocalContext.current
    val cands = remember(s.uuid, s.favicon, s.home) { logoCandidates(s) }
    var idx by remember(s.uuid) { mutableStateOf(0) }
    val base = stationColor(s.name)
    val shape = RoundedCornerShape(14.dp)
    if (idx < cands.size) {
        Box(modifier.background(Color(0xFFF1F3F8), shape).padding(7.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(cands[idx]).addHeader("User-Agent", WallSources.userAgent()).size(192, 192).build(),
                contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                onError = { idx++ },
            )
        }
    } else {
        Box(modifier.background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.55f))), shape), contentAlignment = Alignment.Center) {
            PanelIcon(Ico.RADIO, Modifier.fillMaxSize(0.55f), Color.White)
        }
    }
}
