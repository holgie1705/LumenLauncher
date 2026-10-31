package com.lumen.tv

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext
import java.net.URL
import kotlinx.coroutines.delay

/** Erkennt eine Video-/Kamera-Adresse (RTSP oder HLS), die nativ per ExoPlayer läuft statt im WebView. */
fun isStreamUrl(u: String): Boolean {
    val s = u.trim().lowercase()
    return s.startsWith("rtsp://") || s.startsWith("rtsps://") || s.endsWith(".m3u8") || s.contains(".m3u8?")
}

/** Live-Videostream (z. B. Kamera über go2rtc) direkt mit ExoPlayer – leicht und flüssig, mit automatischem Neuverbinden. */
@Composable
private fun LiveStream(w: WebWidget, modifier: Modifier) {
    val ctx = LocalContext.current
    val player = remember(w.url) {
        ExoPlayer.Builder(ctx).build().apply {
            val uri = Uri.parse(w.url.trim())
            val src = if (w.url.trim().lowercase().startsWith("rtsp")) {
                RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(uri))
            } else {
                HlsMediaSource.Factory(DefaultHttpDataSource.Factory()).createMediaSource(MediaItem.fromUri(uri))
            }
            setMediaSource(src)
            volume = 0f
            // Bei Verbindungsabriss (Kamera kurz weg, Drucker aus) nach kurzer Pause neu verbinden.
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Handler(Looper.getMainLooper()).postDelayed({ runCatching { prepare(); playWhenReady = true } }, 3_000)
                }
            })
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { runCatching { player.release() } } }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            PlayerView(c).apply {
                useController = false
                setBackgroundColor(android.graphics.Color.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
    )
}

private val REFRESH_OPTIONS = listOf(0, 1, 5, 15, 60)
private fun sizeLabel(s: Int) = listOf(t("wie Apps", "like apps"), t("Mittel", "Medium"), t("Groß", "Large"))[s.coerceIn(0, 2)]
private fun refreshLabel(min: Int) = if (min == 0) t("nie", "never") else t("alle $min Min.", "every $min min")

/** Eine WebView. Als Kachel nur zum Anschauen (nicht fokussierbar), im Vollbild bedienbar. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveWeb(w: WebWidget, interactive: Boolean, modifier: Modifier) {
    var view by remember { mutableStateOf<WebView?>(null) }
    LaunchedEffect(view, w.refreshMin) {
        val v = view ?: return@LaunchedEffect
        if (w.refreshMin > 0) while (true) { delay(w.refreshMin * 60_000L); v.reload() }
    }
    DisposableEffect(Unit) { onDispose { view?.let { it.stopLoading(); it.destroy() }; view = null } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mediaPlaybackRequiresUserGesture = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = WebViewClient()
                isFocusable = interactive
                isFocusableInTouchMode = interactive
                isClickable = interactive
                if (!interactive) setOnTouchListener { _, _ -> true }
                loadUrl(w.url)
                if (interactive) post { requestFocus() }
                view = this
            }
        },
    )
}

/** Kachel: zeigt die Webseite live (verkleinert). OK = groß öffnen, langer Druck = Optionen. */
@Composable
fun WebTile(w: WebWidget, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    FocusCard(width, height, DefaultAccent, modifier, onFocus, onClick, onLong) {
        Box(Modifier.fillMaxSize().background(Color(0xFF14161C).copy(alpha = tileAlphaGlobal))) {
            if (isStreamUrl(w.url)) LiveStream(w, Modifier.fillMaxSize().graphicsLayer { alpha = tileAlphaGlobal })
            else LiveWeb(w, false, Modifier.fillMaxSize().graphicsLayer { alpha = tileAlphaGlobal })
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
            Text(
                w.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
        }
    }
}

/** Vollbild-Ansicht: bedienbar mit der Fernbedienung, Zurück schließt. */
@Composable
fun WebFullDialog(w: WebWidget, prefs: Prefs, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0C10))) {
            if (isStreamUrl(w.url)) LiveStream(w, Modifier.fillMaxSize())
            else LiveWeb(w, true, Modifier.fillMaxSize())
            // Bei einem Kamera-Stream zusätzlich den Bambu-Druckerstatus unten einblenden (oben bleibt der Druckkopf frei).
            if (isStreamUrl(w.url) && bambuConfigured(prefs)) {
                BambuStatusBar(prefs, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            }
        }
    }
}

/** Verwaltung der Web-Widgets (Einstellungen): hinzufügen, Aktualisierung wählen, entfernen. */
@Composable
fun WebWidgetsDialog(vm: HomeViewModel, prefs: Prefs, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<String?>(null) }   // != null -> bestehendes Widget bearbeiten statt neu anlegen
    var editing by remember { mutableStateOf(0) }   // 0 = nichts, 1 = Name, 2 = Adresse
    var sub by remember { mutableStateOf<MenuSpec?>(null) }
    var msg by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
    LaunchedEffect(editing) { if (editing != 0) { delay(80); runCatching { field.requestFocus() } } }

    fun add() {
        val u = url.trim()
        if (u.isEmpty()) { msg = t("Bitte zuerst eine Adresse eingeben.", "Please enter an address first."); return }
        val keepScheme = listOf("http://", "https://", "rtsp://", "rtsps://").any { u.startsWith(it, ignoreCase = true) }
        val full = if (keepScheme) u else "https://$u"
        val nm = name.trim().ifEmpty { runCatching { if (isStreamUrl(full)) Uri.parse(full).host ?: full else URL(full).host }.getOrDefault(full) }
        val id = editId
        if (id != null) {
            vm.updateWidget(id, nm, full)
            msg = t("Gespeichert.", "Saved.")
        } else {
            vm.addWidget(WebWidget("w_${System.currentTimeMillis()}", nm, full, 0))
            msg = t("Hinzugefügt – die Reihe „Widgets“ steht auf dem Startbildschirm.", "Added – the “Widgets” row is on the home screen.")
        }
        name = ""; url = ""; editing = 0; editId = null
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.GLOBE, "Web-Widgets", t("Webseiten als Live-Kacheln", "Web pages as live tiles"), width = 800.dp) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("Jede Webadresse wird als Kachel in der Reihe „Widgets“ live angezeigt (z. B. Home Assistant, Grafana, Wetterradar, Börsenticker). Auch Kamera-Streams gehen: eine rtsp://-Adresse (z. B. 3D-Drucker über go2rtc) oder ein .m3u8-Stream wird direkt als Video abgespielt. OK auf der Kachel öffnet sie groß. Jedes Widget braucht Arbeitsspeicher – wenige reichen.",
                        "Any web address is shown live as a tile in the “Widgets” row (e.g. Home Assistant, Grafana, weather radar, stock ticker). Camera streams work too: an rtsp:// address (e.g. a 3D printer via go2rtc) or an .m3u8 stream plays directly as video. OK on a tile opens it large. Each widget needs memory – a few are enough."),
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f),
                )
                SettingLine(t("Name (optional)", "Name (optional)"), name.ifEmpty { "–" }, Modifier.focusRequester(first)) { editing = 1 }
                SettingLine(t("Adresse", "Address"), url.ifEmpty { "https://…" }) { editing = 2 }
                if (editing != 0) BasicTextField(
                    value = if (editing == 1) name else url,
                    onValueChange = { if (editing == 1) name = it else url = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 20.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = if (editing == 2) KeyboardType.Uri else KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { editing = 0 }),
                    modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)).padding(14.dp).focusRequester(field),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (editId != null) t("Speichern", "Save") else t("Hinzufügen", "Add")) { add() }
                    if (editId != null) Pill(t("Abbrechen", "Cancel")) { name = ""; url = ""; editing = 0; editId = null; msg = "" }
                    Pill(t("Schließen", "Close")) { onClose() }
                }
                if (msg.isNotEmpty()) Text(msg, fontSize = 14.sp, color = Color(0xFF7CE0A3))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(prefs.webWidgets, key = { it.id }) { w ->
                        SettingLine(w.name, (if (w.on) "" else t("[aus] ", "[off] ")) + w.url.removePrefix("https://").take(34) + "  ·  " + sizeLabel(w.size) + "  ·  " + refreshLabel(w.refreshMin)) {
                            sub = MenuSpec(w.name, listOf(
                                MenuItemSpec(t("Bearbeiten (Name/Adresse)", "Edit (name/address)")) {
                                    name = w.name; url = w.url; editId = w.id; editing = 0; msg = ""; sub = null
                                },
                                MenuItemSpec(t("Aktualisieren: ", "Refresh: ") + refreshLabel(REFRESH_OPTIONS[(REFRESH_OPTIONS.indexOf(w.refreshMin) + 1) % REFRESH_OPTIONS.size]) + " " + t("(umschalten)", "(switch)")) {
                                    vm.setWidgetRefresh(w.id, REFRESH_OPTIONS[(REFRESH_OPTIONS.indexOf(w.refreshMin) + 1) % REFRESH_OPTIONS.size]); sub = null
                                },
                                MenuItemSpec(t("Größe: ", "Size: ") + sizeLabel(w.size) + " " + t("(umschalten)", "(switch)")) { vm.setWidgetSize(w.id, (w.size + 1) % 3); sub = null },
                                MenuItemSpec(if (w.on) t("Ausblenden", "Hide") else t("Einblenden", "Show")) { vm.toggleWidget(w.id); sub = null },
                                MenuItemSpec(t("Entfernen", "Remove")) { vm.removeWidget(w.id); sub = null },
                            ))
                        }
                    }
                }
            }
        }
    }
    sub?.let { MenuDialog(it) { sub = null } }
}
