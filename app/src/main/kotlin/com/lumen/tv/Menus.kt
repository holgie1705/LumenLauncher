package com.lumen.tv

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MenuDialog(spec: MenuSpec, onDismiss: () -> Unit) {
    val first = remember(spec) { FocusRequester() }
    // Das Menü öffnet sich, während die OK-Taste noch gedrückt ist. Bis zum Loslassen (oder max. 0,9 s)
    // und bei Tasten-Wiederholungen ignorieren wir OK, damit nicht sofort der erste Eintrag auslöst.
    var armed by remember(spec) { mutableStateOf(false) }
    LaunchedEffect(spec) { delay(900); armed = true }
    Dialog(onDismissRequest = onDismiss) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0.15f)
        LaunchedEffect(spec) { delay(80); runCatching { first.requestFocus() } }
        Column(
            Modifier.width(420.dp)
                .onPreviewKeyEvent { e ->
                    val k = e.key
                    if (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter) {
                        if (!armed || e.nativeKeyEvent.repeatCount > 0) {
                            if (e.type == KeyEventType.KeyUp) armed = true
                            true
                        } else false
                    } else false
                }
                .clip(RoundedCornerShape(26.dp)).background(NeoPanelBrush).border(1.dp, NeoLine, RoundedCornerShape(26.dp)).padding(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(spec.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, modifier = Modifier.padding(horizontal = 26.dp, vertical = 4.dp))
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                itemsIndexed(spec.items) { i, item ->
                    SettingLine(item.label, "", if (i == 0) Modifier.focusRequester(first) else Modifier) { item.action() }
                }
            }
        }
    }
}

/** PIN-Eingabe per Zahlenfeld. Im "set"-Modus wird die neue PIN zweimal abgefragt. */
@Composable
fun PinHost(req: PinRequest, vm: HomeViewModel) {
    var entry by remember(req) { mutableStateOf("") }
    var firstPin by remember(req) { mutableStateOf<String?>(null) }
    var error by remember(req) { mutableStateOf("") }
    val focus = remember(req) { FocusRequester() }

    fun submit() {
        val pin = entry
        entry = ""
        if (!req.set) {
            if (vm.verifyPin(pin)) { vm.cancelPin(); req.onOk() } else error = t("Falsche PIN", "Wrong PIN")
        } else if (firstPin == null) {
            firstPin = pin
            error = ""
        } else if (firstPin == pin) {
            vm.setPin(pin); vm.cancelPin(); req.onOk()
        } else {
            error = t("PINs stimmen nicht überein", "PINs do not match")
            firstPin = null
        }
    }

    fun onKey(k: String) {
        when (k) {
            "<" -> entry = entry.dropLast(1)
            "X" -> vm.cancelPin()
            else -> if (entry.length < 4) { entry += k; if (entry.length == 4) submit() }
        }
    }

    Dialog(onDismissRequest = { vm.cancelPin() }) {
        LaunchedEffect(req) { delay(80); runCatching { focus.requestFocus() } }
        Column(
            Modifier.width(340.dp).clip(RoundedCornerShape(26.dp)).background(NeoPanelBrush).border(1.dp, NeoLine, RoundedCornerShape(26.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (req.set) (if (firstPin == null) t("Neue PIN eingeben", "Enter new PIN") else t("PIN wiederholen", "Repeat PIN")) else req.title,
                fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1,
            )
            Text("●".repeat(entry.length) + "○".repeat(4 - entry.length), fontSize = 28.sp, color = Color.White)
            Text(error.ifEmpty { " " }, fontSize = 14.sp, color = Color(0xFFFF6B6B))
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "<", "0", "X").chunked(3).forEach { keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { k ->
                        Pill(k, Modifier.width(92.dp).then(if (k == "1") Modifier.focusRequester(focus) else Modifier)) { onKey(k) }
                    }
                }
            }
        }
    }
}

private val IDLE_OPTIONS = listOf(0, 5, 10, 20)
private val SLIDE_OPTIONS = listOf(0, 5, 15, 30, 60)
private val STANDBY_OPTIONS = listOf(0, 60, 120, 180, 240)
private fun <T> List<T>.after(v: T): T = this[(indexOf(v) + 1) % size]
private fun minutes(m: Int) = if (m <= 0) t("Aus", "Off") else "$m ${t("Min.", "min")}"
private fun hours(m: Int) = if (m <= 0) t("Aus", "Off") else "${m / 60} ${t("Std.", "h")}"
private fun hh(h: Int) = "%02d:00".format(h)

private fun a11yEnabled(ctx: android.content.Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.contains(ctx.packageName + "/") == true

@Composable
fun SettingsDialog(
    vm: HomeViewModel, prefs: Prefs, overrideOn: Boolean, pst: ParentalState,
    apps: List<TvApp>, inputs: List<InputSource>, onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var note by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var sub by remember { mutableStateOf<MenuSpec?>(null) }
    var placeOpen by remember { mutableStateOf(false) }
    var vpnOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var webMgrOpen by remember { mutableStateOf(false) }
    var storeOpen by remember { mutableStateOf(false) }
    var perfOpen by remember { mutableStateOf(false) }
    var widgetsOpen by remember { mutableStateOf(false) }
    var updOpen by remember { mutableStateOf(false) }
    var learnOpen by remember { mutableStateOf(false) }
    var keypadOpen by remember { mutableStateOf(false) }
    var wizardOpen by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val a11y = remember { a11yEnabled(ctx) }
    val notif = remember { vm.notificationAccess() }
    LaunchedEffect(page) { delay(100); runCatching { first.requestFocus() } }
    // Hinweise als Toast zeigen (die Liste kann so lang sein, dass man einen Text am Ende nicht sieht)
    LaunchedEffect(note) {
        if (note.isNotEmpty()) { android.widget.Toast.makeText(ctx, note, android.widget.Toast.LENGTH_LONG).show(); note = "" }
    }

    val roleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { }
    /** Lumen offiziell als Standard-Launcher eintragen lassen (Android-Rolle „Home“), damit die Wahl dauerhaft bleibt. */
    fun requestHomeRole() {
        val done = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
                when {
                    rm == null || !rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) -> false
                    rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) -> { note = t("Lumen ist bereits Standard-Launcher.", "Lumen is already the default launcher."); true }
                    else -> { roleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)); true }
                }
            } else false
        }.getOrDefault(false)
        if (!done) openSettingsPage(ctx, Settings.ACTION_HOME_SETTINGS)?.let { note = it }
    }

    val epgLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { vm.refresh() }
    fun openSystem(action: String) {
        openSettingsPage(ctx, action)?.let { note = it }
    }

    // ----- Fernbedienungs-Shortcut-Assistent (Taste -> Druckart -> Aktion -> Ziel) -----
    fun addShortcut(s: Shortcut) {
        vm.update { p -> p.copy(shortcuts = p.shortcuts.filterNot { it.keyCode == s.keyCode && it.long == s.long } + s) }
        sub = null
    }
    fun pickApp(code: Int, long: Boolean) {
        sub = MenuSpec(t("App wählen", "Choose app"), apps.map { a -> MenuItemSpec(a.label) { addShortcut(Shortcut(code, long, ActionType.APP, a.pkg, a.label)) } })
    }
    fun pickInput(code: Int, long: Boolean) {
        sub = if (inputs.isEmpty()) MenuSpec(t("Keine Eingänge gefunden", "No inputs found"), listOf(MenuItemSpec(t("Zurück", "Back")) { sub = null }))
        else MenuSpec(t("Eingang wählen", "Choose input"), inputs.map { i -> MenuItemSpec(i.label) { addShortcut(Shortcut(code, long, ActionType.INPUT, i.id, i.label)) } })
    }
    fun pickAction(code: Int, long: Boolean) {
        sub = MenuSpec(t("Aktion", "Action"), ActionType.entries.map { act ->
            MenuItemSpec(act.label) {
                when (act) {
                    ActionType.APP -> pickApp(code, long)
                    ActionType.INPUT -> pickInput(code, long)
                    else -> addShortcut(Shortcut(code, long, act))
                }
            }
        })
    }
    fun pickPress(code: Int) {
        sub = MenuSpec(keyName(code), listOf(
            MenuItemSpec(t("Kurzer Druck", "Short press")) { pickAction(code, false) },
            MenuItemSpec(t("Langer Druck", "Long press")) { pickAction(code, true) },
        ))
    }
    fun pickKey() {
        sub = MenuSpec(t("Taste wählen", "Choose key"), shortcutKeys().map { (code, name) -> MenuItemSpec(name) { pickPress(code) } })
    }

    val dens = LocalDensity.current
    val sizeF = listOf(0.85f, 1f, 1.2f, 1.35f)[prefs.menuSize.coerceIn(0, 3)]
    val title = when (page) {
        "look" -> t("Aussehen", "Appearance")
        "power" -> t("Standby & Energie", "Standby & power")
        "rows" -> t("Reihen", "Rows")
        "remote" -> t("Fernbedienung", "Remote control")
        "kids" -> t("Kindersicherung", "Parental controls")
        "system" -> "System"
        else -> "Lumen Launcher"
    }
    val subtitle = when (page) {
        "look" -> t("Layout, Kacheln, Farben & Schrift", "Layout, tiles, colors & font")
        "power" -> t("Bildschirmschoner, Timer & Standby", "Screensaver, timer & standby")
        "rows" -> t("Reihen ein-/ausblenden & sortieren", "Show, hide & sort rows")
        "remote" -> t("Tasten & Shortcuts", "Keys & shortcuts")
        "kids" -> t("Sperrzeiten, PIN & Kindermodus", "Lock times, PIN & kids mode")
        "system" -> t("Launcher, Dienste & Sicherung", "Launcher, services & backup")
        else -> ""
    }

    Dialog(
        onDismissRequest = { if (page != null) page = null else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FontScope(prefs.menuFont) {
        CompositionLocalProvider(LocalDensity provides Density(dens.density, dens.fontScale * sizeF)) {
        // Kein Systemabdunkeln hinter dem Fenster: der Startbildschirm bleibt sichtbar.
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        // Nur ein sanfter Verlauf hinter dem Panel statt eines schwarzen Vollbild-Schleiers.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), endX = 1400f))) {
            // Schwebendes Glas-Panel mit runden Ecken und feinem Rand
            Column(
                Modifier.padding(start = 20.dp, top = 20.dp, bottom = 20.dp).width(500.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(30.dp)).background(NeoPanelBrush)
                    .border(1.dp, NeoLine, RoundedCornerShape(30.dp)),
            ) {
                PanelHeader(title, subtitle, when (page) { "look" -> Ico.BRUSH; "power" -> Ico.POWER; "rows" -> Ico.LIST; "remote" -> Ico.REMOTE; "kids" -> Ico.LOCK; else -> Ico.GEAR }, if (page == null) "v$APP_VERSION" else "")
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (page == null) {
                        Updater.available.value?.let { u ->
                            item { TileRow({ m -> PanelTile(Ico.DOWNLOAD, t("Update verfügbar", "Update available"), "Lumen ${u.version} – " + t("jetzt installieren", "install now"), m) { updOpen = true } }) }
                        }
                        // Kachel-Raster: je zwei große farbige Kacheln pro Zeile
                        item {
                            TileRow(
                                { m -> PanelTile(Ico.BRUSH, t("Aussehen", "Appearance"), t("Layout, Farben, Schrift", "Layout, colors, font"), m.focusRequester(first)) { page = "look" } },
                                { m -> PanelTile(Ico.LIST, t("Reihen", "Rows"), t("Ein-/ausblenden, sortieren", "Show, hide, sort"), m) { page = "rows" } },
                            )
                        }
                        item {
                            TileRow(
                                { m -> PanelTile(Ico.POWER, t("Standby", "Standby"), t("Schoner, Timer, Energie", "Saver, timer, power"), m) { page = "power" } },
                                { m -> PanelTile(Ico.REMOTE, t("Fernbedienung", "Remote"), t("Tasten & Shortcuts", "Keys & shortcuts"), m) { page = "remote" } },
                            )
                        }
                        item {
                            TileRow(
                                { m -> PanelTile(Ico.LOCK, t("Kindersicherung", "Parental"), t("PIN & Sperrzeiten", "PIN & lock times"), m) { page = "kids" } },
                                { m -> PanelTile(Ico.GEAR, "System", t("Launcher & Dienste", "Launcher & services"), m) { page = "system" } },
                            )
                        }
                        item {
                            TileRow(
                                { m -> PanelTile(Ico.DOT, "VPN-Dot", onOff(prefs.dotOn), m) { vpnOpen = true } },
                                { m -> PanelTile(Ico.GLOBE, "Web-Widgets", "${prefs.webWidgets.size} " + t("aktiv", "active"), m) { webMgrOpen = true } },
                            )
                        }
                        item {
                            TileRow(
                                { m -> PanelTile(Ico.GAUGE, t("Leistung", "Performance"), t("RAM, Speicher, Boost", "RAM, storage, boost"), m) { perfOpen = true } },
                                { m -> PanelTile(Ico.GLOBE, t("Sprache", "Language"), listOf("Deutsch", "English", "System")[prefs.lang], m) { vm.update { it.copy(lang = (it.lang + 1) % 3) } } },
                            )
                        }
                        item {
                            TileRow(
                                { m -> PanelTile(Ico.TIMER, t("Info-Widgets", "Info widgets"), t("Fußball, News, Countdown …", "Football, news, countdown …"), m) { widgetsOpen = true } },
                                { m -> PanelTile(Ico.INFO, "Info", "Version $APP_VERSION", m) { infoOpen = true } },
                            )
                        }
                        item { PanelSection("Android") }
                        item {
                            TileRow(
                                { m -> PanelTile(Ico.PHONE, t("Einstellungen", "Settings"), "Android", m, small = true) { openSystem(Settings.ACTION_SETTINGS) } },
                                { m -> PanelTile(Ico.WIFI, t("Netzwerk", "Network"), "WLAN / LAN", m, small = true) { openSystem(Settings.ACTION_WIFI_SETTINGS) } },
                            )
                        }
                        item {
                            TileRow(
                                { m -> PanelTile(Ico.SOUND, t("Ton", "Sound"), "Audio", m, small = true) { openSystem(Settings.ACTION_SOUND_SETTINGS) } },
                                { m -> PanelTile(Ico.GRID, "Apps", t("verwalten", "manage"), m, small = true) { openSystem(Settings.ACTION_APPLICATION_SETTINGS) } },
                            )
                        }
                    } else {
                        item { PanelRow(Ico.BACK, t("Zurück", "Back"), "", Modifier.focusRequester(first)) { page = null } }
                        when (page) {
                            "look" -> {
                                item { SettingLine(t("Text zur App (Titel & Info)", "App text (title & info)"), listOf(t("Aus", "Off"), t("Klein", "Small"), t("Groß", "Large"))[prefs.heroMode]) { vm.update { it.copy(heroMode = (it.heroMode + 1) % 3) } } }
                                item { SettingLine(t("Position der Kacheln", "Tile position"), if (prefs.rowsPos == 0) t("Oben", "Top") else t("Unten", "Bottom")) { vm.update { it.copy(rowsPos = 1 - it.rowsPos) } } }
                                item { SettingLine(t("Position der Kopfzeile", "Top bar position"), if (prefs.barPos == 0) t("Oben", "Top") else t("Unten", "Bottom")) { vm.update { it.copy(barPos = 1 - it.barPos) } } }
                        item { SettingLine(t("Icon-Pack", "Icon pack"), vm.iconPacks().firstOrNull { it.first == prefs.iconPack }?.second ?: t("Standard", "Default")) {
                            val packs = vm.iconPacks()
                            sub = MenuSpec(
                                t("Icon-Pack", "Icon pack"),
                                listOf(MenuItemSpec(t("Standard (App-Icons)", "Default (app icons)")) { vm.setIconPack(""); sub = null }) +
                                    (if (packs.isEmpty()) listOf(MenuItemSpec(t("Keine Icon-Packs installiert", "No icon packs installed")) { sub = null }) else packs.map { p -> MenuItemSpec(p.second) { vm.setIconPack(p.first); sub = null } }),
                            )
                        } }
                        item {
                            val mpNames = listOf(t("Aus", "Off"), t("Oben links", "Top left"), t("Oben rechts", "Top right"), t("Unten links", "Bottom left"), t("Unten rechts", "Bottom right"))
                            SettingLine(t("Uhr & Wetter in Apps", "Clock & weather in apps"), if (prefs.miniOverlay) mpNames[prefs.miniPos + 1] else mpNames[0]) {
                                sub = MenuSpec(t("Uhr & Wetter in Apps", "Clock & weather in apps"), mpNames.mapIndexed { i, n -> MenuItemSpec(n) {
                                    vm.update { if (i == 0) it.copy(miniOverlay = false) else it.copy(miniOverlay = true, miniPos = i - 1) }; sub = null
                                } })
                            }
                        }
                        item { SettingLine(t("Kachel-Transparenz", "Tile transparency"), listOf("0 %", "25 %", "50 %", "70 %")[prefs.tileGlass.coerceIn(0, 3)]) { vm.update { it.copy(tileGlass = (it.tileGlass + 1) % 4) } } }
                        item { SettingLine(t("Fokus-Stil der Kacheln", "Tile focus style"), focusStyleName(prefs.focusStyle)) { vm.update { it.copy(focusStyle = (it.focusStyle + 1) % 5) } } }
                        item { SettingLine(t("Animierter Hintergrund", "Animated background"), animBgName(prefs.animBg)) { vm.update { it.copy(animBg = (it.animBg + 1) % 11) } } }
                        item { SettingLine(t("Saisonale Themes (Halloween, Weihnachten, Silvester, Ostern)", "Seasonal themes (Halloween, Christmas, New Year, Easter)"), onOff(prefs.seasonal)) { vm.update { it.copy(seasonal = !it.seasonal) } } }
                        item { SettingLine(t("Hintergrund nach Tageszeit & Wetter", "Background by time & weather"), onOff(prefs.wxBg)) { vm.update { it.copy(wxBg = !it.wxBg) } } }
                        item { SettingLine(t("Farbe der Menüs", "Menu color"), MENU_THEMES[prefs.menuTheme.coerceIn(0, MENU_THEMES.lastIndex)].label) { vm.update { it.copy(menuTheme = (it.menuTheme + 1) % MENU_THEMES.size) } } }
                        item { SettingLine("Look", prefs.look.label) { vm.update { it.copy(look = it.look.next()) } } }
                        item { SettingLine(t("Akzentfarbe", "Accent color"), accentName(prefs.accent)) { vm.update { it.copy(accent = (it.accent + 1) % ACCENTS.size) } } }
                        item { SettingLine(t("Kachel-Stil", "Tile style"), prefs.tile.label) { vm.update { it.copy(tile = it.tile.next()) } } }
                        item { SettingLine(t("Kachel-Größe", "Tile size"), "${(TILE_SCALES[prefs.tileScale.coerceIn(0, 6)] * 100).toInt()} %") { vm.update { it.copy(tileScale = (it.tileScale + 1) % TILE_SCALES.size) } } }
                        item { SettingLine(t("Abstände", "Spacing"), "${TILE_GAPS[prefs.tileGap.coerceIn(0, 4)]} dp") { vm.update { it.copy(tileGap = (it.tileGap + 1) % TILE_GAPS.size) } } }
                        item { SettingLine("Layout", prefs.layout.label) { vm.update { it.copy(layout = it.layout.next()) } } }
                        if (prefs.layout == Layout.FREE) {
                            item { Hint(t("Frei: zeigt deine Favoriten (sonst die erste App-Reihe). Lange auf eine Kachel drücken → „Frei verschieben“.", "Free: shows your favorites (otherwise the first app row). Long-press a tile → “Move freely”.")) }
                            item { SettingLine(t("Freie Anordnung zurücksetzen", "Reset free arrangement"), "") { vm.freeReset(); note = t("Zurückgesetzt.", "Reset.") } }
                        }
                        item { SettingLine(t("Uhr-Stil", "Clock style"), clockStyleName(prefs.clockStyle)) { vm.update { it.copy(clockStyle = (it.clockStyle + 1) % 6) } } }
                        item {
                            val cpNames = listOf(t("Oben links", "Top left"), t("Oben rechts", "Top right"), t("Unten links", "Bottom left"), t("Unten rechts", "Bottom right"))
                            SettingLine(t("Uhr & Wetter Position", "Clock & weather position"), cpNames[prefs.clockPos.coerceIn(0, 3)]) {
                                sub = MenuSpec(t("Uhr & Wetter Position", "Clock & weather position"), cpNames.mapIndexed { i, n -> MenuItemSpec(n) { vm.update { it.copy(clockPos = i) }; sub = null } })
                            }
                        }
                        item { SettingLine(t("Zeitformat", "Time format"), listOf(t("24 Stunden", "24 hours"), "System", t("12 Stunden (AM/PM)", "12 hours (AM/PM)"))[prefs.timeFormat]) { vm.update { it.copy(timeFormat = (it.timeFormat + 1) % 3) } } }
                        item { SettingLine(t("Wetter", "Weather"), listOf(t("Aus", "Off"), t("Kompakt", "Compact"), t("Mit Vorschau", "With forecast"))[prefs.weather]) { vm.update { it.copy(weather = (it.weather + 1) % 3) } } }
                        item { SettingLine(t("Wetter-Ort", "Weather place"), prefs.weatherName.ifEmpty { t("nicht gesetzt", "not set") }) { placeOpen = true } }
                        item { SettingLine(t("Unwetterwarnungen (DWD)", "Severe weather alerts (DWD)"), onOff(prefs.weatherAlerts)) { vm.update { it.copy(weatherAlerts = !it.weatherAlerts) } } }
                        item { SettingLine(t("Temperatur-Einheit", "Temperature unit"), if (prefs.tempF) "°F / mph" else "°C / km/h") { vm.update { it.copy(tempF = !it.tempF) } } }
                        item {
                            SettingLine(t("Schriftart", "Font"), fontLabel(prefs.font)) {
                                val opts = mutableListOf("ui" to fontLabel("ui"), "outfit" to fontLabel("outfit"), "jakarta" to fontLabel("jakarta"), "manrope" to fontLabel("manrope"), "sora" to fontLabel("sora"), "system" to fontLabel("system"), "serif" to fontLabel("serif"), "mono" to fontLabel("mono"), "condensed" to fontLabel("condensed"))
                                vm.fontFiles().forEach { opts += "file:$it" to it }
                                sub = MenuSpec(t("Schriftart", "Font"), opts.map { (k, l) -> MenuItemSpec(l) { vm.update { p -> p.copy(font = k) }; sub = null } })
                            }
                        }
                        item { SettingLine(t("Hintergrund abdunkeln", "Dim background"), "${prefs.dim * 25} %") { vm.update { it.copy(dim = (it.dim + 1) % 4) } } }
                        item { SettingLine(t("Hintergrund-Unschärfe", "Background blur"), listOf(t("Aus", "Off"), t("Leicht", "Light"), t("Mittel", "Medium"), t("Stark", "Strong"))[prefs.blur]) { vm.update { it.copy(blur = (it.blur + 1) % 4) } } }
                        item { SettingLine(t("Wallpaper-Wechsel", "Wallpaper change"), minutes(prefs.slideshowMin)) { vm.update { it.copy(slideshowMin = SLIDE_OPTIONS.after(it.slideshowMin)) } } }
                        item { SettingLine(t("Wallpaper herunterladen (Galerie)", "Download wallpapers (gallery)"), "") { storeOpen = true } }
                        item { SettingLine(t("Wallpaper-Ordner neu einlesen", "Rescan wallpaper folder"), "") { vm.reloadWallpapers(); note = t("Wallpaper neu geladen.", "Wallpapers reloaded.") } }
                        item { SettingLine(t("Statusleiste", "Status bar"), onOff(prefs.showStatus)) { vm.update { it.copy(showStatus = !it.showStatus) } } }
                        item { SettingLine(t("Neustart-Knopf in der Kopfzeile", "Restart button in top bar"), onOff(prefs.restartBtn)) { vm.update { it.copy(restartBtn = !it.restartBtn) } } }
                        item { SettingLine(t("Radio-Reihe auf Startseite", "Radio row on home"), onOff(prefs.radioRow)) { vm.update { it.copy(radioRow = !it.radioRow) } } }
                        item {
                            val npNames = listOf(t("Unter der Uhr", "Under the clock"), t("Oben Mitte", "Top center"), t("Oben rechts", "Top right"), t("Mitte", "Center"),
                                t("Unten Mitte", "Bottom center"), t("Unten links", "Bottom left"), t("Unten rechts", "Bottom right"))
                            SettingLine(t("Radio-Anzeige Position", "Radio display position"), npNames[prefs.npPos.coerceIn(0, 6)]) {
                                sub = MenuSpec(t("Radio-Anzeige Position", "Radio display position"), npNames.mapIndexed { i, n -> MenuItemSpec(n) { vm.update { it.copy(npPos = i) }; sub = null } })
                            }
                        }
                        item { SettingLine(t("Radio-Player", "Radio player"), onOff(prefs.radioOn)) {
                            if (prefs.radioOn) Radio.stop(ctx)
                            vm.update { it.copy(radioOn = !it.radioOn) }
                        } }
                        item { SettingLine(t("Alle Apps sortieren nach", "Sort all apps by"), listOf("A–Z", t("Zuletzt genutzt", "Recently used"), t("Häufigkeit", "Frequency"), t("Eigene Reihenfolge", "Custom order"))[prefs.allSort]) { vm.update { it.copy(allSort = (it.allSort + 1) % 4) } } }
                        item { SettingLine(t("Aktuelles Aussehen als Theme speichern", "Save current look as theme"), "") { note = t("Gespeichert", "Saved") + ": " + vm.saveTheme() } }
                        item {
                            SettingLine(t("Theme laden", "Load theme"), "") {
                                val files = vm.themeFiles()
                                if (files.isEmpty()) note = t("Keine Themes in /Android/data/com.lumen.tv/files/themes/ gefunden.", "No themes found in /Android/data/com.lumen.tv/files/themes/.")
                                else sub = MenuSpec(t("Theme laden", "Load theme"), files.map { f -> MenuItemSpec(f.nameWithoutExtension) { note = if (vm.applyTheme(f)) t("Theme geladen.", "Theme loaded.") else t("Theme ungültig.", "Invalid theme."); sub = null } })
                            }
                        }
                        item {
                            SettingLine(t("Menü-Schrift", "Menu font"), if (prefs.menuFont.isEmpty()) t("wie Schriftart", "same as font") else fontLabel(prefs.menuFont)) {
                                val opts = mutableListOf("" to t("wie Schriftart", "same as font"), "ui" to fontLabel("ui"), "outfit" to fontLabel("outfit"), "jakarta" to fontLabel("jakarta"), "manrope" to fontLabel("manrope"), "sora" to fontLabel("sora"), "system" to fontLabel("system"), "serif" to fontLabel("serif"), "mono" to fontLabel("mono"), "condensed" to fontLabel("condensed"))
                                vm.fontFiles().forEach { opts += "file:$it" to it }
                                sub = MenuSpec(t("Menü-Schrift", "Menu font"), opts.map { (k, l) -> MenuItemSpec(l) { vm.update { p -> p.copy(menuFont = k) }; sub = null } })
                            }
                        }
                        item { SettingLine(t("Menü-Schriftgröße", "Menu text size"), listOf(t("Klein", "Small"), t("Normal", "Normal"), t("Groß", "Large"), t("Sehr groß", "Extra large"))[prefs.menuSize.coerceIn(0, 3)]) { vm.update { it.copy(menuSize = (it.menuSize + 1) % 4) } } }
                        item { Hint(t("Eigene Schriften (ttf/otf) kopierst du nach /Android/data/com.lumen.tv/files/fonts/ – danach erscheinen sie in der Schriftart-Auswahl. Die Menü-Schrift gilt für dieses Menü und die Kopfzeile.", "Copy your own fonts (ttf/otf) to /Android/data/com.lumen.tv/files/fonts/ – they then appear in the font list. The menu font applies to this menu and the top bar.")) }
                            }
                            "power" -> {
                        item { SettingLine(t("Bildschirmschoner nach", "Screensaver after"), minutes(prefs.idleMin)) { vm.update { it.copy(idleMin = IDLE_OPTIONS.after(it.idleMin)) } } }
                        item { SettingLine(t("Bildschirmschoner", "Screensaver"), listOf(t("Uhr (OLED-schonend)", "Clock (OLED-friendly)"), t("Eigene Fotos", "Own photos"), t("Galerie aus dem Netz + Uhr & Wetter", "Web gallery + clock & weather"))[prefs.idleStyle.coerceIn(0, 2)]) { vm.update { it.copy(idleStyle = (it.idleStyle + 1) % 3) } } }
                        item { SettingLine(t("Auto-Standby des TV nach", "TV auto-standby after"), hours(prefs.standbyMin)) { vm.update { it.copy(standbyMin = STANDBY_OPTIONS.after(it.standbyMin)) } } }
                        item { Hint(t("Auto-Standby und Einschlaf-Timer brauchen die Bedienungshilfe. Läuft Ton, wird nicht abgeschaltet. Ob der TV dabei wirklich in Standby geht, hängt vom Gerät ab.", "Auto-standby and the sleep timer need the accessibility service. Nothing is switched off while audio is playing. Whether the TV really enters standby depends on the device.")) }
                            }
                            "rows" -> {
                        itemsIndexed(prefs.rows, key = { _, r -> r.id }) { _, r ->
                            Row(Modifier.padding(end = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SettingLine(r.shownTitle(), onOff(r.visible), Modifier.weight(1f)) { vm.toggleRow(r.id) }
                                Pill(t("Hoch", "Up")) { vm.moveRow(r.id, -1) }
                                Pill(t("Runter", "Down")) { vm.moveRow(r.id, 1) }
                                if (r.kind == RowKind.CUSTOM && r.id != "favs") Pill(t("Löschen", "Delete")) { vm.deleteRow(r.id) }
                            }
                        }
                        item { Hint(t("Favoriten und eigene Reihen legst du per langem Druck auf eine App an.", "Create favorites and your own rows by long-pressing an app.")) }
                        item {
                            val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, "android.permission.READ_TV_LISTINGS") == android.content.pm.PackageManager.PERMISSION_GRANTED
                            SettingLine(t("Weiter ansehen: Zugriff erlauben", "Continue watching: allow access"), if (granted) t("erteilt", "granted") else t("anfragen", "request")) {
                                if (granted) { vm.refresh(); note = t("Zugriff ist schon erteilt – Liste wird neu geladen.", "Access already granted – reloading the list.") }
                                else runCatching { epgLauncher.launch("android.permission.READ_TV_LISTINGS") }.onFailure { note = t("Anfrage nicht möglich. Per ADB: pm grant com.lumen.tv android.permission.READ_TV_LISTINGS", "Request not possible. Via ADB: pm grant com.lumen.tv android.permission.READ_TV_LISTINGS") }
                            }
                        }
                        item { Hint(t("„Weiter ansehen“ zeigt nur etwas, wenn Android TV Einträge dafür freigibt. Auf manchen Boxen darf nur der System-Launcher diese Liste lesen, dann bleibt die Reihe leer und wird ausgeblendet.", "“Continue watching” only shows something if Android TV shares entries. On some boxes only the system launcher may read this list; then the row stays empty and is hidden.")) }
                            }
                            "remote" -> {
                        itemsIndexed(prefs.shortcuts) { _, s ->
                            Row(Modifier.padding(end = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SettingLine(
                                    keyName(s.keyCode) + if (s.long) t(" · lang", " · long") else t(" · kurz", " · short"),
                                    s.action.label.removeSuffix(" …") + if (s.argLabel.isNotEmpty()) ": ${s.argLabel}" else "",
                                    Modifier.weight(1f),
                                ) { }
                                Pill(t("Löschen", "Delete")) { vm.update { p -> p.copy(shortcuts = p.shortcuts - s) } }
                            }
                        }
                        item {
                            SettingLine("📱 " + t("Handy als Fernbedienung", "Phone as remote"), onOff(prefs.phoneRemote)) {
                                vm.update { it.copy(phoneRemote = !it.phoneRemote, remoteCode = it.remoteCode.ifEmpty { (1000..9999).random().toString() }) }
                                if (!prefs.phoneRemote && OverrideService.instance == null) note = t("Dafür die Bedienungshilfe „Lumen Home-Taste“ einschalten.", "Turn on the “Lumen Home-Taste” accessibility service for this.")
                            }
                        }
                        if (prefs.phoneRemote && prefs.remoteCode.isNotEmpty()) item { PhoneRemoteInfo(prefs.remoteCode, OverrideService.instance != null) }
                        if (prefs.phoneRemote) item { SettingLine(t("Neuen Code erzeugen (alte Handys abmelden)", "New code (sign out old phones)"), prefs.remoteCode) { vm.update { it.copy(remoteCode = (1000..9999).random().toString()) } } }
                        item { SettingLine("🎯 " + t("Taste anlernen – einfach auf der Fernbedienung drücken", "Learn a button – just press it on the remote"), "") { learnOpen = true } }
                        item { SettingLine(t("Neue Tastenbelegung aus Liste …", "New key assignment from list …"), "") { pickKey() } }
                        item {
                            val ri = remember { RemoteDetect.detect() }
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t("Erkannt: ", "Detected: ") + ri.box, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text(t("Fernbedienung: ", "Remote: ") + ri.remotes.joinToString(", ").ifEmpty { "–" }, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                                Text(t("Sondertasten: ", "Extra keys: ") + ri.keys.joinToString(", ") { it.second }.ifEmpty { t("keine gemeldet – „Taste anlernen“ nutzen", "none reported – use “Learn a button”") }, fontSize = 12.sp, color = NeoCyan)
                            }
                        }
                        item {
                            val ri = remember { RemoteDetect.detect() }
                            val pre = remember(ri) { RemoteDetect.preset(ri) }
                            if (pre.isNotEmpty()) SettingLine(t("Empfohlene Belegung für die Farbtasten übernehmen", "Apply recommended color-key layout"), "${pre.size}") {
                                vm.update { p -> p.copy(shortcuts = p.shortcuts.filterNot { s -> pre.any { it.keyCode == s.keyCode && !s.long } } + pre) }
                                note = t("Übernommen: Rot = Lumen, Grün = Schlaftimer, Gelb = Standby, Blau = Sprachsuche.", "Applied: red = Lumen, green = sleep timer, yellow = standby, blue = voice search.")
                            }
                        }
                        item { Hint(t("Shortcuts brauchen die Bedienungshilfe (siehe System). Belegte Tasten verlieren ihre normale Funktion.", "Shortcuts need the accessibility service (see System). Assigned keys lose their normal function.")) }
                            }
                            "kids" -> {
                        item { SettingLine(t("Sperrzeit", "Lock schedule"), onOff(pst.on)) { vm.toggleParental() } }
                        item { SettingLine(t("Gesperrt von", "Locked from"), hh(pst.fromH)) { vm.cycleFrom() } }
                        item { SettingLine(t("Gesperrt bis", "Locked until"), hh(pst.toH)) { vm.cycleTo() } }
                        item { SettingLine(t("Kindermodus (nur freigegebene Apps)", "Kids mode (approved apps only)"), onOff(pst.kids)) { vm.toggleKids() } }
                        item { SettingLine(t("PIN ändern", "Change PIN"), if (pst.hasPin) t("gesetzt", "set") else t("nicht gesetzt", "not set")) { vm.changePin() } }
                        item { Hint(t("Apps sperrst du oder gibst sie für Kinder frei per langem Druck auf die App. Durchgesetzt wird das auch außerhalb von Lumen – dafür muss die Bedienungshilfe aktiv sein.", "Lock apps or approve them for kids by long-pressing the app. This is enforced outside Lumen too – the accessibility service must be active.")) }
                            }
                            "system" -> {
                        item { SettingLine(t("Ausgeblendete Apps wieder anzeigen", "Show hidden apps again"), "${prefs.hidden.size}") { vm.unhideAll() } }
                        item { SettingLine(t("Lumen beim Systemstart öffnen", "Open Lumen at system start"), onOff(prefs.bootStart)) { vm.update { it.copy(bootStart = !it.bootStart) } } }
                        item { SettingLine(t("Autostart-App (beim Start von Lumen)", "Autostart app (when Lumen starts)"), apps.firstOrNull { it.pkg == prefs.autostart }?.label ?: prefs.autostart.ifEmpty { t("Aus", "Off") }) {
                            sub = MenuSpec(
                                t("Autostart-App", "Autostart app"),
                                listOf(MenuItemSpec(t("Aus (keine App)", "Off (no app)")) { vm.setAutostart(""); sub = null }) +
                                    apps.sortedBy { it.label.lowercase() }.map { a -> MenuItemSpec(a.label) { vm.setAutostart(a.pkg); sub = null } },
                            )
                        } }
                        item { SettingLine(t("Als Standard-Launcher festlegen", "Set as default launcher"), "") { requestHomeRole() } }
                        item { SettingLine(t("Stock-Launcher überschreiben", "Override stock launcher"), onOff(overrideOn)) { vm.setOverride(!overrideOn) } }
                        item { SettingLine(t("Bedienungshilfe „Lumen Home-Taste“", "Accessibility service “Lumen Home-Taste”"), if (a11y) t("aktiv", "active") else t("öffnen", "open")) { openSystem(Settings.ACTION_ACCESSIBILITY_SETTINGS) } }
                        item { SettingLine(t("Zugriff auf laufende Medien (Reihe „Läuft gerade“)", "Access to playing media (row “Now playing”)"), if (notif) t("aktiv", "active") else t("öffnen", "open")) { openSystem(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) } }
                        item { SettingLine(t("Ziffernblock (ADB) – Zahlen am TV eingeben", "Number pad (ADB) – enter numbers on TV"), "") { keypadOpen = true } }
                        item { SettingLine(t("Stock-Launcher einfrieren (Root)", "Freeze stock launcher (root)"), "") { vm.freezeStock(true) { ok -> note = if (ok) t("Stock-Launcher eingefroren.", "Stock launcher frozen.") else t("Fehlgeschlagen (kein Root?).", "Failed (no root?).") } } }
                        item { SettingLine(t("Stock-Launcher wieder aktivieren (Root)", "Re-enable stock launcher (root)"), "") { vm.freezeStock(false) { ok -> note = if (ok) t("Stock-Launcher aktiviert.", "Stock launcher enabled.") else t("Fehlgeschlagen (kein Root?).", "Failed (no root?).") } } }
                        item { SettingLine(t("Nach Updates suchen", "Check for updates"), "v$APP_VERSION") {
                            scope.launch { val u = Updater.check(force = true); if (u != null) updOpen = true else note = t("Lumen ist aktuell.", "Lumen is up to date.") }
                        } }
                        item { SettingLine(t("Einrichtungs-Assistent erneut starten", "Run setup assistant again"), "") { wizardOpen = true } }
                        item { SettingLine(t("Einstellungen exportieren", "Export settings"), "") { note = t("Gespeichert", "Saved") + ": " + vm.exportSettings() } }
                        item { SettingLine(t("Einstellungen importieren", "Import settings"), "") { note = if (vm.importSettings()) t("Importiert.", "Imported.") else t("Keine gültige lumen-settings.json gefunden.", "No valid lumen-settings.json found.") } }
                        item { Hint(t("Ordner (unter /Android/data/com.lumen.tv/files/): wallpapers (Bild, GIF, Video), fonts (ttf/otf), themes (json).", "Folders (under /Android/data/com.lumen.tv/files/): wallpapers (image, GIF, video), fonts (ttf/otf), themes (json).")) }
                            }
                        }
                    }
                    if (note.isNotEmpty()) item { Text(note, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(start = 28.dp, top = 10.dp)) }
                }
            }
            sub?.let { MenuDialog(it) { sub = null } }
            if (placeOpen) PlaceDialog(vm) { placeOpen = false }
            if (vpnOpen) VpnDotDialog(vm, prefs) { vpnOpen = false }
            if (infoOpen) InfoDialog { infoOpen = false }
            if (webMgrOpen) WebWidgetsDialog(vm, prefs) { webMgrOpen = false }
            if (storeOpen) WallpaperStoreDialog(vm) { storeOpen = false }
            if (perfOpen) PerformanceDialog(vm, prefs, apps) { perfOpen = false }
            if (widgetsOpen) WidgetSettingsDialog(vm) { widgetsOpen = false }
            if (updOpen) Updater.available.value?.let { u -> UpdateDialog(u) { updOpen = false } }
            if (wizardOpen) SetupWizard(vm, apps) { wizardOpen = false }
            if (learnOpen) KeyLearnDialog(onKey = { code -> learnOpen = false; pickPress(code) }, onCancel = { learnOpen = false })
            if (keypadOpen) KeypadSettingsDialog(vm) { keypadOpen = false }
        }
        }
        }
    }
}

private fun onOff(v: Boolean) = if (v) t("An", "On") else t("Aus", "Off")

@Composable
private fun Section(title: String) {
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp))
}
