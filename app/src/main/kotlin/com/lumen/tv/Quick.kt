package com.lumen.tv

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ============================================================== Schnellmenü

/** Schnellmenü oben rechts: häufige Schalter als kleine Kacheln. */
@Composable
fun QuickPanel(vm: HomeViewModel, prefs: Prefs, onRadio: () -> Unit, onPerformance: () -> Unit, onSettings: () -> Unit = {}, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val volMax = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val sleep by vm.sleepUntil.collectAsState()
    val radioNow by Radio.now.collectAsState()
    val status by vm.status.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun setVol(d: Int) {
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (d > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
    val sleepMin = if (sleep > System.currentTimeMillis()) ((sleep - System.currentTimeMillis()) / 60_000 + 1).toInt() else 0

    var editOpen by remember { mutableStateOf(false) }
    val appList by vm.appList.collectAsState()
    fun launchApp(pkg: String) {
        onClose()
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (i != null) runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        else toast(t("App nicht gefunden", "App not found"))
    }
    class Q(val key: String, val ico: Ico, val title: String, val value: String, val on: Boolean = false, val act: () -> Unit)
    val all = listOf(
        Q("volDown", Ico.SOUND, t("Leiser", "Volume −"), "${vol * 100 / volMax} %") { setVol(-1) },
        Q("volUp", Ico.SOUND, t("Lauter", "Volume +"), "${vol * 100 / volMax} %") { setVol(1) },
        Q("sleep", Ico.TIMER, t("Schlaftimer", "Sleep timer"), if (sleepMin > 0) "$sleepMin min" else t("Aus", "Off"), sleepMin > 0) {
            val next = listOf(0, 30, 60, 90, 120).firstOrNull { it > sleepMin + 1 } ?: 0
            vm.setSleepTimer(next)
        },
        Q("radio", Ico.RADIO, "Radio", radioNow?.name ?: t("aus", "off"), radioNow != null) { if (radioNow != null) Radio.stop(ctx) else { onClose(); onRadio() } },
        Q("screensaver", Ico.SCREEN, t("Bildschirmschoner", "Screensaver"), t("jetzt", "now")) { onClose(); vm.forceIdle() },
        Q("boost", Ico.GAUGE, t("RAM-Boost", "RAM boost"), status?.let { "${100 - it.ramFreePct} % " + t("belegt", "used") } ?: "") {
            scope.launch {
                val playing = vm.nowSessions().filter { it.playing }.map { it.pkg }.toSet()
                val (mb, n) = SysTools.boost(ctx, vm.appList.value.map { it.pkg }, prefs.boostExclude + playing)
                toast(if (mb > 0) t("⚡ Hintergrund von $n Apps geleert · $mb MB frei", "⚡ Cleared $n apps · $mb MB freed") else t("⚡ Hintergrund von $n Apps geleert", "⚡ Cleared background of $n apps"))
            }
        },
        Q("network", Ico.WIFI, t("Netzwerk", "Network"), status?.net ?: "") { onClose(); openSettingsPage(ctx, Settings.ACTION_WIFI_SETTINGS)?.let { toast(it) } },
        Q("bluetooth", Ico.REMOTE, "Bluetooth", t("Geräte", "devices")) { onClose(); openSettingsPage(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)?.let { toast(it) } },
        Q("performance", Ico.INFO, t("Leistung", "Performance"), t("Details", "details")) { onClose(); onPerformance() },
        Q("settings", Ico.GEAR, t("Einstellungen", "Settings"), "") { onClose(); onSettings() },
        Q("android", Ico.PHONE, "Android", t("System", "system")) { onClose(); openAndroidSettings(ctx) },
    )
    // Auswahl & Reihenfolge; leer = Standard (ohne Lautstärke). "app:<pkg>" startet eine App.
    val order = prefs.quickItems.ifEmpty { listOf("sleep", "radio", "screensaver", "boost", "network", "bluetooth", "performance") }
    val tiles = order.mapNotNull { k ->
        if (k.startsWith("app:")) {
            val pkg = k.removePrefix("app:")
            appList.firstOrNull { it.pkg == pkg }?.let { a -> Q(k, Ico.GRID, a.label, t("App öffnen", "Open app")) { launchApp(pkg) } }
        } else all.firstOrNull { it.key == k }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), startX = 900f))) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 104.dp, end = 32.dp, bottom = 40.dp).width(460.dp)
                    .clip(RoundedCornerShape(28.dp)).background(NeoPanelBrush).border(1.dp, NeoLine, RoundedCornerShape(28.dp)).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Schnellmenü", "Quick menu"), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                    Text(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                }
                // etwas Luft oben, damit der Fokusrahmen der obersten Kacheln nicht am Panelrand klebt
                LazyVerticalGrid(
                    GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 4.dp),
                    modifier = Modifier.height(360.dp),
                ) {
                    items(tiles.size) { i ->
                        val q = tiles[i]
                        QuickTile(q.ico, q.title, q.value, q.on, if (i == 0) Modifier.focusRequester(first) else Modifier, q.act)
                    }
                    item { QuickTile(Ico.GRID, t("Anpassen", "Customize"), t("Kacheln wählen", "choose tiles"), false, Modifier) { editOpen = true } }
                }
            }
        }
        if (editOpen) QuickEditDialog(vm, all.map { it.key to it.title }, appList, order) { editOpen = false }
    }
}

/** Auswahl, welche Kacheln im Schnellmenü erscheinen: Aktionen und – falls gewünscht – Apps. */
@Composable
private fun QuickEditDialog(vm: HomeViewModel, actions: List<Pair<String, String>>, apps: List<TvApp>, current: List<String>, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    val appsSorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    // Reihenfolge nach jedem Umschalten: erst Aktionen (Hauptreihenfolge), dann Apps (alphabetisch)
    fun rebuild(members: Set<String>) = actions.map { it.first }.filter { it in members } + appsSorted.map { "app:" + it.pkg }.filter { it in members }
    fun toggle(key: String) {
        val members = current.toMutableSet().apply { if (!add(key)) remove(key) }
        vm.update { it.copy(quickItems = rebuild(members)) }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.GRID, t("Schnellmenü anpassen", "Customize quick menu"), t("Aktionen und Apps für das Schnellmenü wählen", "Choose actions and apps for the quick menu"), width = 720.dp) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { SheetSection(t("Aktionen", "Actions")) }
                itemsIndexed(actions) { i, (key, title) ->
                    SettingLine(title, if (key in current) t("An", "On") else t("Aus", "Off"), if (i == 0) Modifier.focusRequester(first) else Modifier) { toggle(key) }
                }
                item { SheetSection(t("Apps", "Apps")) }
                itemsIndexed(appsSorted) { _, a ->
                    val key = "app:" + a.pkg
                    SettingLine(a.label, if (key in current) t("An", "On") else t("Aus", "Off")) { toggle(key) }
                }
                item { Pill(t("Fertig", "Done"), Modifier.padding(start = 12.dp, top = 10.dp)) { onClose() } }
            }
        }
    }
}

@Composable
private fun QuickTile(ico: Ico, title: String, value: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val tint = icoTint(ico)
    Surface(
        onClick = onClick, modifier = modifier.height(100.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (on) tint.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f),
            focusedContainerColor = if (on) tint.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.16f),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, NeoLine), shape = shape),
            focusedBorder = Border(BorderStroke(2.5.dp, NeoYellow), shape = shape),
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(32.dp).background(tint.copy(alpha = 0.22f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                PanelIcon(ico, Modifier.size(17.dp), tint)
            }
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ============================================================== Update-Hinweis (GitHub-Releases)

class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

object Updater {
    private const val REPO = "holgie1705/LumenLauncher"
    val available = MutableStateFlow<UpdateInfo?>(null)
    private var lastCheck = 0L

    /** "1.66" < "1.70" ? – vergleicht Zahlen-Teile, ignoriert "v" und Zusätze. */
    fun newer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V").split('.', '-').mapNotNull { it.toIntOrNull() }
        val a = parts(remote); val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Prüft höchstens alle 6 Stunden (oder sofort mit force). */
    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastCheck < 6 * 3600_000L) return@withContext available.value
        lastCheck = System.currentTimeMillis()
        val txt = Http.get("https://api.github.com/repos/$REPO/releases/latest") ?: return@withContext available.value
        val info = runCatching {
            val o = JSONObject(txt)
            val tag = o.optString("tag_name")
            val assets = o.optJSONArray("assets")
            val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url").orEmpty()
            if (apk.isNotEmpty() && newer(tag, APP_VERSION)) UpdateInfo(tag.removePrefix("v"), apk, o.optString("body").take(1500)) else null
        }.getOrNull()
        available.value = info
        info
    }

    /** Lädt die APK herunter und öffnet den Android-Installer. */
    suspend fun install(ctx: Context, u: UpdateInfo, progress: (Int) -> Unit): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.getExternalFilesDir(null), "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, "LumenLauncher-${u.version}.apk")
            val c = URL(u.apkUrl).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true; c.connectTimeout = 15_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", WallSources.userAgent())
            val total = c.contentLengthLong
            c.inputStream.use { inp ->
                f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var done = 0L
                    while (true) {
                        val r = inp.read(buf); if (r < 0) break
                        out.write(buf, 0, r); done += r
                        if (total > 0) progress((done * 100 / total).toInt())
                    }
                }
            }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            withContext(Dispatchers.Main) { ctx.startActivity(i) }
            null
        }.getOrElse { it.message ?: "Fehler" }
    }
}

@Composable
fun UpdateDialog(u: UpdateInfo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pct by remember { mutableIntStateOf(-1) }
    var err by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.DOWNLOAD, t("Update verfügbar", "Update available"), "Lumen Launcher ${u.version} · " + t("installiert", "installed") + " $APP_VERSION", width = 760.dp) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(u.notes.ifEmpty { t("Keine Beschreibung.", "No release notes.") }, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)) }
            }
            if (pct >= 0) Text(t("Lade herunter … $pct %", "Downloading … $pct %"), fontSize = 15.sp, color = NeoCyan)
            if (err.isNotEmpty()) Text(err, fontSize = 13.sp, color = Color(0xFFFFB37A))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("⬇ " + t("Jetzt installieren", "Install now"), Modifier.focusRequester(first), selected = true) {
                    if (pct < 0) { pct = 0; scope.launch { Updater.install(ctx, u) { pct = it }?.let { err = t("Fehler: ", "Error: ") + it }; pct = -1 } }
                }
                Pill(t("Später", "Later")) { onClose() }
            }
            Text(
                t("Beim ersten Mal fragt Android, ob Lumen Apps installieren darf – einmal erlauben. Einstellungen bleiben erhalten.",
                    "The first time Android asks whether Lumen may install apps – allow it once. Your settings are kept."),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}

// ============================================================== Einrichtungs-Assistent

/** Erster Start: Sprache, Wetter-Ort, Lieblings-Apps, Look – in wenigen Schritten. */
@Composable
fun SetupWizard(vm: HomeViewModel, apps: List<TvApp>, onDone: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    var placeOpen by remember { mutableStateOf(false) }
    val first = remember(step) { FocusRequester() }
    LaunchedEffect(step) { delay(150); runCatching { first.requestFocus() } }
    val steps = 5
    fun next() { if (step < steps - 1) step++ else { vm.update { it.copy(setupDone = true) }; onDone() } }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF141B3A), Color(0xFF07080C)))), contentAlignment = Alignment.Center) {
            Column(Modifier.width(900.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Fortschritt
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(steps) { i -> Box(Modifier.weight(1f).height(5.dp).background(if (i <= step) NeoCyan else Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))) }
                }
                when (step) {
                    0 -> {
                        LumenLogo(Modifier.padding(vertical = 8.dp), big = true)
                        Text(t("Willkommen bei Lumen Launcher", "Welcome to Lumen Launcher"), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(t("In fünf kurzen Schritten ist alles eingerichtet. Du kannst alles später in den Einstellungen ändern.", "Five short steps and you're set. You can change everything later in the settings."), fontSize = 16.sp, color = Color.White.copy(alpha = 0.75f))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pill("Deutsch", Modifier.focusRequester(first), selected = prefs.lang == 0) { vm.update { it.copy(lang = 0) } }
                            Pill("English", selected = prefs.lang == 1) { vm.update { it.copy(lang = 1) } }
                        }
                    }
                    1 -> {
                        Text(t("Wo bist du?", "Where are you?"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(t("Für Wetter und Bildschirmschoner.", "For weather and the screensaver."), fontSize = 16.sp, color = Color.White.copy(alpha = 0.75f))
                        SettingLine(t("Ort", "Place"), prefs.weatherName.ifEmpty { t("auswählen", "choose") }, Modifier.focusRequester(first)) { placeOpen = true }
                    }
                    2 -> {
                        Text(t("Deine Lieblings-Apps", "Your favorite apps"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(t("Sie erscheinen ganz oben in der Reihe „Favoriten“.", "They appear in the “Favorites” row."), fontSize = 16.sp, color = Color.White.copy(alpha = 0.75f))
                        val favs = prefs.rows.firstOrNull { it.id == "favs" }?.pkgs.orEmpty()
                        LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(330.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(apps.sortedBy { it.label.lowercase() }, key = { it.pkg }) { a ->
                                val on = a.pkg in favs
                                SettingLine(a.label, if (on) t("An", "On") else t("Aus", "Off"), if (a == apps.minByOrNull { it.label.lowercase() }) Modifier.focusRequester(first) else Modifier) {
                                    if (on) vm.removeFromRow("favs", a.pkg) else vm.addToRow("favs", a.pkg)
                                }
                            }
                        }
                    }
                    3 -> {
                        Text(t("Dein Look", "Your look"), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        SettingLine(t("Farbe der Menüs", "Menu color"), MENU_THEMES[prefs.menuTheme.coerceIn(0, MENU_THEMES.lastIndex)].label, Modifier.focusRequester(first)) { vm.update { it.copy(menuTheme = (it.menuTheme + 1) % MENU_THEMES.size) } }
                        SettingLine(t("Kachel-Stil", "Tile style"), prefs.tile.label) { vm.update { it.copy(tile = it.tile.next()) } }
                        SettingLine(t("Fokus-Stil", "Focus style"), focusStyleName(prefs.focusStyle)) { vm.update { it.copy(focusStyle = (it.focusStyle + 1) % 5) } }
                        SettingLine(t("Uhr", "Clock"), clockStyleName(prefs.clockStyle)) { vm.update { it.copy(clockStyle = (it.clockStyle + 1) % 6) } }
                    }
                    else -> {
                        Text(t("Fertig! 🎉", "All set! 🎉"), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            t("Tipp: Lange auf eine Kachel drücken öffnet Optionen. Info-Widgets (Fußball, News, Countdown …) schaltest du unter Einstellungen → Info-Widgets ein. Damit Lumen immer startet: Home-Taste drücken und Lumen Launcher als „Immer“ wählen.",
                                "Tip: long-press a tile for options. Turn on info widgets (football, news, countdown …) under Settings → Info widgets. To make Lumen your home screen, press Home and choose Lumen Launcher “Always”."),
                            fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (step > 0) Pill("‹ " + t("Zurück", "Back")) { step-- }
                    Pill(if (step == steps - 1) t("Los geht's", "Let's go") else t("Weiter ›", "Next ›"), Modifier.then(if (step == 4) Modifier.focusRequester(first) else Modifier), selected = true) { next() }
                    if (step < steps - 1) Pill(t("Überspringen", "Skip")) { vm.update { it.copy(setupDone = true) }; onDone() }
                }
            }
        }
        if (placeOpen) PlaceDialog(vm) { placeOpen = false }
    }
}
