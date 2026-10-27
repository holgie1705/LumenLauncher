package com.lumen.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
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
fun RadioTile(s: Station, style: TileStyle, scale: Float, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val now by Radio.now.collectAsState()
    val playing by Radio.playing.collectAsState()
    val active = now?.uuid == s.uuid
    val base = stationColor(s.name)
    val (wd, ht) = wideDims(style, scale)
    FocusCard(wd, ht, base, modifier, onFocus, onClick, onLong) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(base.copy(alpha = tileAlphaGlobal), base.copy(alpha = 0.45f * tileAlphaGlobal), Color(0xFF111319).copy(alpha = tileAlphaGlobal))))) {
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
fun RadioAddTile(style: TileStyle, scale: Float, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val (wd, ht) = wideDims(style, scale)
    FocusCard(wd, ht, DefaultAccent, modifier, onFocus, onClick) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            PanelIcon(Ico.RADIO, Modifier.size((34 * scale).dp))
            Text(t("Sender suchen", "Find stations"), fontSize = 13.sp, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** Radio: links „Jetzt läuft“, rechts Favoriten / Beliebt / Suche als Raster mit Senderlogos. */
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

    fun search(q: String) {
        favMode = false
        info = t("Suche …", "Searching …")
        scope.launch {
            val found = RadioApi.search(q)
            results = if (q.isBlank()) prefs.stations + found.filter { f -> prefs.stations.none { it.uuid == f.uuid } } else found
            info = if (results.isEmpty()) t("Nichts gefunden – oder der Senderdienst ist gerade nicht erreichbar.", "Nothing found – or the station service is unreachable.") else ""
        }
    }
    fun toggleSave(s: Station) {
        if (s.uuid in saved) { vm.removeStation(s.uuid); android.widget.Toast.makeText(ctx, t("Aus Favoriten entfernt: ", "Removed: ") + s.name, android.widget.Toast.LENGTH_SHORT).show() }
        else { vm.addStation(s); android.widget.Toast.makeText(ctx, "★ " + t("Gespeichert: ", "Saved: ") + s.name, android.widget.Toast.LENGTH_SHORT).show() }
    }
    val firstBtn = remember { FocusRequester() }
    LaunchedEffect(Unit) { search(""); favMode = prefs.stations.isNotEmpty(); delay(150); runCatching { firstBtn.requestFocus() } }
    LaunchedEffect(editing) { if (editing) { delay(80); runCatching { field.requestFocus() } } }

    val glow = now?.let { stationColor(it.name) } ?: NeoViolet
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFA0B0F1E))
                .background(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(260f, 220f), radius = 900f)),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                // ---------- Links: Jetzt läuft ----------
                Column(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(Color(0xFFFF8A5B).copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
                            PanelIcon(Ico.RADIO, Modifier.size(22.dp), Color(0xFFFF8A5B))
                        }
                        Column {
                            Text(t("Radio", "Radio"), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(t("Live-Sender aus aller Welt", "Live stations from around the world"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                            .background(Brush.verticalGradient(listOf(glow.copy(alpha = 0.45f), Color(0xFF151A30))))
                            .border(1.dp, NeoLine, RoundedCornerShape(28.dp)).padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val st = now
                        if (st == null) {
                            Box(Modifier.size(150.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                                PanelIcon(Ico.RADIO, Modifier.size(64.dp), Color.White.copy(alpha = 0.35f))
                            }
                            Text(t("Gerade läuft nichts", "Nothing playing"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(t("Wähle rechts einen Sender.", "Pick a station on the right."), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                        } else {
                            StationLogo(st, Modifier.size(150.dp), 40.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.background(Color(0xFFFF4D5E), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                                    Text("LIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Equalizer(playing, NeoCyan, Modifier.size(22.dp, 18.dp))
                            }
                            Text(st.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Text(
                                when { buffering && !playing -> t("Verbinde …", "Connecting …"); track.isNotEmpty() -> "♪ $track"; else -> st.info.ifEmpty { "Live" } },
                                fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Pill("■ " + t("Stopp", "Stop")) { Radio.stop(ctx) }
                                Pill(if (st.uuid in saved) "✓ " + t("Favorit", "Favorite") else "★ " + t("Speichern", "Save"), selected = st.uuid in saved) { toggleSave(st) }
                            }
                        }
                    }
                    if (err.isNotEmpty()) Text(err, fontSize = 13.sp, color = Color(0xFFFFB37A))
                    Spacer(Modifier.weight(1f))
                    Text(
                        t("OK spielt einen Sender ab – auch weiter, wenn du eine andere App öffnest.\nLange drücken = als Favorit speichern oder entfernen.",
                            "OK plays a station – it keeps playing when you open another app.\nLong-press = save or remove as favorite."),
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f),
                    )
                }

                // ---------- Rechts: Auswahl ----------
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("★ " + t("Favoriten", "Favorites") + " (${prefs.stations.size})", Modifier.focusRequester(firstBtn), selected = favMode) { favMode = true }
                        Pill(t("Beliebt", "Popular"), selected = !favMode && query.isBlank()) { query = ""; search("") }
                        Pill("⌕ " + query.ifEmpty { t("Sender suchen …", "Search stations …") }, selected = !favMode && query.isNotBlank()) { editing = true }
                        Spacer(Modifier.weight(1f))
                        Pill(t("Schließen", "Close")) { onClose() }
                    }
                    if (editing) BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                        cursorBrush = SolidColor(NeoCyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { editing = false; search(query); runCatching { firstBtn.requestFocus() } }),
                        modifier = Modifier.fillMaxWidth().background(NeoGlass, RoundedCornerShape(16.dp)).border(2.dp, NeoYellow, RoundedCornerShape(16.dp))
                            .padding(16.dp).focusRequester(field),
                    )
                    Text(
                        when {
                            favMode && prefs.stations.isEmpty() -> t("Noch keine Favoriten – wähle „Beliebt“ oder suche einen Sender und drück lange zum Speichern.", "No favorites yet – choose “Popular” or search and long-press to save.")
                            favMode -> "★ " + t("Deine gespeicherten Sender", "Your saved stations")
                            info.isNotEmpty() -> info
                            query.isNotBlank() -> t("Ergebnisse für „$query“", "Results for “$query”")
                            else -> t("Beliebte Sender", "Popular stations")
                        },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (favMode) Color(0xFFFFD36B) else Color.White.copy(alpha = 0.75f),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(250.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        gridItems(shown, key = { it.uuid }) { s ->
                            StationCard(s, active = now?.uuid == s.uuid, playing = playing, isFav = s.uuid in saved, onClick = { Radio.toggle(ctx, s) }, onLong = { toggleSave(s) })
                        }
                    }
                }
            }
        }
    }
}

/** Senderkarte im Auswahl-Raster: Logo, Name, Info; läuft = Equalizer, Favorit = Stern. */
@Composable
private fun StationCard(s: Station, active: Boolean, playing: Boolean, isFav: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val tint = stationColor(s.name)
    Surface(
        onClick = onClick, onLongClick = onLong,
        modifier = Modifier.fillMaxWidth().height(82.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (active) tint.copy(alpha = 0.30f) else NeoCard,
            focusedContainerColor = if (active) tint.copy(alpha = 0.42f) else NeoGlassHi,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (active) 1.5.dp else 1.dp, if (active) NeoCyan.copy(alpha = 0.8f) else NeoLine), shape = shape),
            focusedBorder = Border(BorderStroke(2.5.dp, NeoYellow), shape = shape),
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StationLogo(s, Modifier.size(56.dp), 18.sp)
            Column(Modifier.weight(1f)) {
                Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.info.ifEmpty { "Live" }, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (active) Equalizer(playing, NeoCyan, Modifier.size(20.dp, 18.dp))
            else if (isFav) Text("★", fontSize = 18.sp, color = Color(0xFFFFD36B))
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

/** Spektrum-Analyzer wie am Hi-Fi-Verstärker: Balken wachsen von unten, Verlauf Cyan → Violett, mit kleinen Spitzen-Strichen. ~8 Bilder/s. */
@Composable
private fun SpectrumBars(active: Boolean, modifier: Modifier = Modifier, bars: Int = 22) {
    val phase by androidx.compose.runtime.produceState(0f, active) {
        while (active) { value = (System.currentTimeMillis() % 3000L) / 3000f; delay(120) }
    }
    androidx.compose.foundation.Canvas(modifier) {
        val slot = size.width / bars
        val bw = slot * 0.62f
        val brush = Brush.verticalGradient(listOf(NeoViolet, NeoCyan), startY = 0f, endY = size.height)
        for (i in 0 until bars) {
            val x = i.toFloat() / bars
            // Bässe links etwas höher, wie bei echter Musik
            val envelope = 1f - 0.45f * x
            val a = phase * 2f * Math.PI.toFloat() * 2f
            val wobble = kotlin.math.abs(kotlin.math.sin(a + i * 0.9f) * 0.6f + kotlin.math.sin(a * 1.7f + i * 0.35f) * 0.4f)
            val f = if (active) (0.12f + 0.88f * wobble * envelope).coerceIn(0.08f, 1f) else 0.08f
            val h = size.height * f
            drawRoundRect(brush, androidx.compose.ui.geometry.Offset(i * slot, size.height - h), androidx.compose.ui.geometry.Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 3f), alpha = if (active) 0.95f else 0.4f)
            if (active) drawRect(Color.White.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(i * slot, (size.height - h - 3f).coerceAtLeast(0f)), androidx.compose.ui.geometry.Size(bw, 1.5f))
        }
    }
}

/** „Live Radio“-Karte auf dem Startbildschirm: Glas mit Senderfarbe, Logo, Sender, Song, Wellenform und runder Stopp-Taste. OK öffnet das Radio. */
@Composable
fun NowPlayingCard(enabled: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val now by Radio.now.collectAsState()
    val playing by Radio.playing.collectAsState()
    val track by Radio.track.collectAsState()
    val st = now
    if (!enabled || st == null) return
    val base = stationColor(st.name)
    val shape = RoundedCornerShape(20.dp)
    Row(modifier.padding(start = 14.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onOpen,
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, NeoLine), shape = shape),
                focusedBorder = Border(BorderStroke(2.5.dp, NeoYellow), shape = shape),
            ),
        ) {
            Row(
                Modifier.width(350.dp)
                    .background(Brush.horizontalGradient(listOf(base.copy(alpha = 0.55f), Color(0xD9141A33), Color(0xD90D1124))))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StationLogo(st, Modifier.size(52.dp), 16.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PanelIcon(Ico.SOUND, Modifier.size(12.dp), if (playing) NeoCyan else Color.White.copy(alpha = 0.5f))
                        Text("LIVE RADIO", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, color = NeoCyan)
                    }
                    Text(st.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (!playing) t("Verbinde …", "Connecting …") else track.ifEmpty { st.info.ifEmpty { "Live" } },
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    SpectrumBars(playing, Modifier.fillMaxWidth().height(18.dp).padding(top = 3.dp))
                }
            }
        }
        // Runde Stopp-Taste
        Surface(
            onClick = { Radio.stop(ctx) },
            modifier = Modifier.size(42.dp),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xCC141A33), focusedContainerColor = NeoYellow),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(border = Border(BorderStroke(1.dp, NeoLine), shape = CircleShape)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(12.dp).background(Color(0xFFFF5A5F), RoundedCornerShape(3.dp)))
            }
        }
    }
}

/** Senderlogos aus dem TuneIn-Verzeichnis (quadratisch, gute Qualität). Ergebnis wird im Speicher gemerkt. */
object StationLogos {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Sendername ohne Zusätze wie „SWR3 - Popmusik“ oder „1LIVE | WDR“. */
    private fun clean(name: String) = name.split(" - ", " | ", " – ", "(").first().trim()

    suspend fun tunein(name: String): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val q = clean(name)
        if (q.length < 2) return@withContext null
        val key = q.lowercase()
        cache[key]?.let { return@withContext it.ifEmpty { null } }
        val url = runCatching {
            val c = java.net.URL("https://opml.radiotime.com/Search.ashx?render=json&query=" + java.net.URLEncoder.encode(q, "UTF-8")).openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 6000; c.readTimeout = 8000
            c.setRequestProperty("User-Agent", WallSources.userAgent())
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val items = mutableListOf<org.json.JSONObject>()
            fun walk(x: Any?) {
                when (x) {
                    is org.json.JSONArray -> for (i in 0 until x.length()) walk(x.opt(i))
                    is org.json.JSONObject -> {
                        if (x.optString("type") == "audio" && x.optString("image").startsWith("http")) items += x
                        walk(x.opt("body")); walk(x.opt("outline")); walk(x.opt("children"))
                    }
                }
            }
            walk(org.json.JSONObject(txt))
            val best = items.firstOrNull { it.optString("text").lowercase().startsWith(key) }
                ?: items.firstOrNull { it.optString("text").lowercase().contains(key) }
                ?: items.firstOrNull()
            best?.optString("image")?.replace("http://", "https://")
        }.getOrNull()
        cache[key] = url ?: ""
        url
    }
}

/** Logo-Kandidaten: TuneIn-Logo, gemeldetes Favicon (auch als https), dann das Seiten-Icon der Sender-Homepage bzw. der Stream-Domain. */
private fun logoCandidates(s: Station, tune: String? = null): List<String> {
    val out = mutableListOf<String>()
    if (!tune.isNullOrEmpty()) out += tune
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
    val tune by androidx.compose.runtime.produceState<String?>(null, s.uuid) { value = StationLogos.tunein(s.name) }
    val cands = remember(s.uuid, s.favicon, s.home, tune) { logoCandidates(s, tune) }
    var idx by remember(s.uuid, tune) { mutableStateOf(0) }
    val base = stationColor(s.name)
    val shape = RoundedCornerShape(14.dp)
    if (idx < cands.size) {
        val full = cands[idx] == tune   // TuneIn-Logos sind randlos und quadratisch -> füllend anzeigen
        Box(modifier.clip(shape).background(Color(0xFFF1F3F8)).padding(if (full) 0.dp else 7.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(cands[idx]).addHeader("User-Agent", WallSources.userAgent()).size(256, 256).build(),
                contentDescription = null, contentScale = if (full) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                onError = { idx++ },
            )
        }
    } else {
        Box(modifier.background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.55f))), shape), contentAlignment = Alignment.Center) {
            PanelIcon(Ico.RADIO, Modifier.fillMaxSize(0.55f), Color.White)
        }
    }
}
