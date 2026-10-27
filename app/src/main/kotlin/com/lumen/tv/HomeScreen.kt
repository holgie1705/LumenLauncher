package com.lumen.tv

import android.os.SystemClock
import android.widget.Toast
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val wall by vm.wallpaper.collectAsStateWithLifecycle()
    val overrideOn by vm.overrideOn.collectAsStateWithLifecycle()
    val pst by vm.parentalState.collectAsStateWithLifecycle()
    val pin by vm.pinRequest.collectAsStateWithLifecycle()
    val forceIdle by vm.forceIdle.collectAsStateWithLifecycle()
    val appList by vm.appList.collectAsStateWithLifecycle()
    val inputList by vm.inputList.collectAsStateWithLifecycle()
    val status = vm.status.collectAsStateWithLifecycle()
    val sleepUntil = vm.sleepUntil.collectAsStateWithLifecycle()
    val weather = vm.weather.collectAsStateWithLifecycle()
    val inputPlay = vm.inputPlay.collectAsStateWithLifecycle()

    // Fokus-Info als State-Objekt: nur Hero und Ambient lesen es -> Layouts rekomponieren beim Navigieren nicht.
    val hero = remember { mutableStateOf<HeroInfo?>(null) }
    var menu by remember { mutableStateOf<MenuSpec?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var weatherOpen by remember { mutableStateOf(false) }
    androidx.compose.runtime.SideEffect { iconPackGlobal = prefs.iconPack; focusStyleGlobal = prefs.focusStyle; menuThemeGlobal = prefs.menuTheme; tileAlphaGlobal = listOf(1f, 0.75f, 0.5f, 0.3f)[prefs.tileGlass.coerceIn(0, 3)] }
    var radioOpen by remember { mutableStateOf(false) }
    var webOpen by remember { mutableStateOf<WebWidget?>(null) }
    var placeOpen by remember { mutableStateOf(false) }
    var idle by remember { mutableStateOf(false) }
    var lastInput by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val idleMin by rememberUpdatedState(prefs.idleMin)
    val firstFocus = remember { FocusRequester() }
    val hasPlace = prefs.weatherLat != 0.0 || prefs.weatherLon != 0.0
    val homeCtx = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    BackHandler { }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            if (idleMin > 0 && !idle && SystemClock.elapsedRealtime() - lastInput > idleMin * 60_000L) idle = true
        }
    }
    LaunchedEffect(forceIdle) { if (forceIdle) { idle = true; vm.clearForceIdle() } }
    LaunchedEffect(state.rows.isNotEmpty()) {
        if (state.rows.isNotEmpty()) { delay(200); runCatching { firstFocus.requestFocus() } }
    }

    fun closeMenu() { menu = null }

    fun addToRowMenu(app: TvApp): MenuSpec {
        val items = mutableListOf<MenuItemSpec>()
        prefs.rows.filter { it.kind == RowKind.CUSTOM }.forEach { r ->
            items += MenuItemSpec(r.shownTitle()) { vm.addToRow(r.id, app.pkg); closeMenu() }
        }
        presetRows().forEach { name -> items += MenuItemSpec("${t("Neue Reihe", "New row")}: $name") { vm.newRow(name, app.pkg); closeMenu() } }
        return MenuSpec(t("Zu Reihe hinzufügen", "Add to row"), items)
    }

    /** Verschiebe-Modus: Links/Rechts schiebt die Kachel, OK oder Zurück beendet. */
    var moving by remember { mutableStateOf<Pair<String, String>?>(null) }
    var movingSince by remember { mutableLongStateOf(0L) }
    var freeMoving by remember { mutableStateOf<String?>(null) }   // Layout "Frei": Kachel wird gerade verschoben
    val fcx = remember { FreeCtx() }
    var swallowOk by remember { mutableStateOf(false) }   // OK-Taste nach dem Bestätigen noch bis zum Loslassen verschlucken
    fun startMove(rowId: String, pkg: String) {
        closeMenu(); moving = rowId to pkg; movingSince = SystemClock.elapsedRealtime()
    }
    fun moveStep(d: Int) {
        val (rowId, pkg) = moving ?: return
        if (rowId == "all") {
            val order = state.rows.filterIsInstance<AppRowUi>().firstOrNull { it.id == "all" }?.apps?.map { it.pkg } ?: return
            vm.moveInAll(order, pkg, d)
        } else vm.moveApp(rowId, pkg, d)
    }

    fun appMenu(app: TvApp, row: AppRowUi): MenuSpec {
        val items = mutableListOf<MenuItemSpec>()
        items += MenuItemSpec(t("Öffnen", "Open")) { closeMenu(); vm.open(app) }
        if (prefs.layout == Layout.FREE) {
            items += MenuItemSpec(t("Frei verschieben (Pfeiltasten)", "Move freely (arrow keys)")) { closeMenu(); freeMoving = app.pkg; movingSince = SystemClock.elapsedRealtime() }
            items += MenuItemSpec(t("Position zurücksetzen", "Reset position")) { vm.freeReset(app.pkg); closeMenu() }
        }
        val isFav = prefs.rows.firstOrNull { it.id == "favs" }?.pkgs?.contains(app.pkg) == true
        items += if (isFav) MenuItemSpec(t("Aus Favoriten entfernen", "Remove from favorites")) { vm.removeFromRow("favs", app.pkg); closeMenu() }
        else MenuItemSpec(t("Zu Favoriten hinzufügen", "Add to favorites")) { vm.addToRow("favs", app.pkg); closeMenu() }
        if (row.custom) {
            items += MenuItemSpec(t("Verschieben (Pfeiltasten)", "Move (arrow keys)")) { startMove(row.id, app.pkg) }
            items += MenuItemSpec(t("Aus Reihe entfernen", "Remove from row")) { vm.removeFromRow(row.id, app.pkg); closeMenu() }
        }
        if (!row.custom && row.id == "all") {
            items += MenuItemSpec(t("Verschieben (Pfeiltasten)", "Move (arrow keys)")) { startMove(row.id, app.pkg) }
        }
        items += MenuItemSpec(t("Zu Reihe hinzufügen …", "Add to row …")) { menu = addToRowMenu(app) }
        items += MenuItemSpec(t("Als Autostart-App festlegen", "Set as autostart app")) { vm.setAutostart(app.pkg); closeMenu() }
        items += MenuItemSpec(if (app.pkg in pst.blocked) t("Sperrzeit aufheben", "Remove from lock schedule") else t("In Sperrzeit sperren", "Lock during schedule")) {
            closeMenu(); vm.requirePin(t("Kindersicherung", "Parental controls")) { vm.toggleBlocked(app.pkg) }
        }
        items += MenuItemSpec(if (app.pkg in pst.allowed) t("Für Kinder entfernen", "Remove from kids mode") else t("Für Kinder freigeben", "Allow for kids")) {
            closeMenu(); vm.requirePin(t("Kindersicherung", "Parental controls")) { vm.toggleAllowed(app.pkg) }
        }
        items += MenuItemSpec(t("App-Info", "App info")) { closeMenu(); vm.details(app) }
        if (app.system) items += MenuItemSpec(t("App deaktivieren (Root/ADB)", "Disable app (root/ADB)")) {
            closeMenu(); vm.disableApp(app) { msg -> Toast.makeText(homeCtx, msg, Toast.LENGTH_LONG).show() }
        }
        items += MenuItemSpec(t("Ausblenden", "Hide")) { vm.hide(app.pkg); closeMenu() }
        items += MenuItemSpec(t("Deinstallieren", "Uninstall")) { closeMenu(); vm.uninstall(app) }
        return MenuSpec(app.label, items)
    }

    fun timerMenu() = MenuSpec(
        t("Einschlaf-Timer", "Sleep timer"),
        listOf(0, 15, 30, 60, 90, 120).map { m ->
            MenuItemSpec(if (m == 0) t("Aus", "Off") else "$m ${t("Min.", "min")}") { vm.setSleepTimer(m); closeMenu() }
        },
    )

    val lc = LayoutCtx(
        style = prefs.tile, scale = prefs.scaleF, vm = vm,
        onHero = { hero.value = it },
        onMenu = { app, r -> menu = appMenu(app, r) },
        onRadioSearch = { radioOpen = true },
        onWebOpen = { webOpen = it },
        onWebMenu = { w ->
            menu = MenuSpec(w.name, listOf(
                MenuItemSpec(t("Groß öffnen", "Open large")) { closeMenu(); webOpen = w },
                MenuItemSpec(t("Widget entfernen", "Remove widget")) { vm.removeWidget(w.id); closeMenu() },
            ))
        },
        onStationMenu = { st ->
            menu = MenuSpec(st.name, listOf(
                MenuItemSpec(t("Abspielen / Stopp", "Play / stop")) { Radio.toggle(homeCtx, st); closeMenu() },
                MenuItemSpec(t("Aus Favoriten entfernen", "Remove from saved")) { vm.removeStation(st.uuid); closeMenu() },
            ))
        },
    )

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            lastInput = SystemClock.elapsedRealtime()
            val okKey = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (swallowOk && okKey) {
                if (e.type == KeyEventType.KeyUp) swallowOk = false
                true
            } else if (moving != null) {
                val k = e.key
                val ready = SystemClock.elapsedRealtime() - movingSince > 500
                when (k) {
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (e.type == KeyEventType.KeyDown) moveStep(if (k == Key.DirectionLeft) -1 else 1)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back -> {
                        if (e.type == KeyEventType.KeyDown && ready) { moving = null; if (okKey) swallowOk = true }
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
            } else if (freeMoving != null) {
                val k = e.key
                val ready = SystemClock.elapsedRealtime() - movingSince > 500
                val pk = freeMoving!!
                when (k) {
                    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown -> {
                        if (e.type == KeyEventType.KeyDown) {
                            val st = 14
                            val dx = if (k == Key.DirectionLeft) -st else if (k == Key.DirectionRight) st else 0
                            val dy = if (k == Key.DirectionUp) -st else if (k == Key.DirectionDown) st else 0
                            vm.freeNudge(pk, dx, dy, fcx.defaults[pk] ?: (0 to 0), fcx.maxX, fcx.maxY)
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back -> {
                        if (e.type == KeyEventType.KeyDown && ready) { freeMoving = null; if (okKey) swallowOk = true }
                        true
                    }
                    else -> false
                }
            } else if (idle) { idle = false; true } else false
        }
    ) {
        if (!idle) AmbientHost(hero, vm, prefs, wall)

        val barBottom = prefs.barPos == 1
        val bar: @Composable () -> Unit = {
            FontScope(prefs.menuFont) { TopBar(
                prefs = prefs, status = status, sleepUntil = sleepUntil, weather = weather,
                onWidget = { if (prefs.weather > 0) { if (hasPlace) weatherOpen = true else placeOpen = true } },
                onSearch = { vm.assist() },
                onTimer = { menu = timerMenu() },
                onSettings = { vm.requirePinIfLocked { settingsOpen = true } },
                onAndroid = { openAndroidSettings(homeCtx) },
                onRadio = { radioOpen = true },
                onRestart = { menu = MenuSpec(t("Gerät neu starten?", "Restart device?"), listOf(
                    MenuItemSpec(t("Ja, neu starten", "Yes, restart")) { closeMenu(); DeviceRestart.run(homeCtx) },
                    MenuItemSpec(t("Abbrechen", "Cancel")) { closeMenu() },
                )) },
            ) }
        }
        Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = if (barBottom) 16.dp else 0.dp)) {
            if (!barBottom) bar()
            when (prefs.layout) {
                Layout.SHELVES -> if (prefs.rowsPos == 1) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        ShelfLayout(
                            state.rows, lc, firstFocus, Modifier.fillMaxSize(),
                            bottomAnchor = true, heroMode = prefs.heroMode,
                        ) { m -> Hero(hero, vm, prefs.heroMode, false, m) }
                    }
                } else {
                    Spacer(Modifier.weight(0.1f))
                    Hero(hero, vm, prefs.heroMode, false, Modifier.padding(horizontal = 14.dp))
                    ShelfLayout(state.rows, lc, firstFocus, Modifier.weight(1f))
                }
                Layout.GRID -> {
                    Hero(hero, vm, prefs.heroMode, true, Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    GridLayout(state.rows, lc, firstFocus, Modifier.weight(1f))
                }
                Layout.FREE -> FreeLayout(state.rows, lc, prefs.freePos, fcx, firstFocus, freeMoving, Modifier.weight(1f))
                Layout.SIDEBAR -> {
                    Hero(hero, vm, prefs.heroMode, true, Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                    SidebarLayout(state.rows, lc, firstFocus, Modifier.weight(1f))
                }
            }
            if (barBottom) bar()
        }

        if (idle) { if (prefs.idleStyle == 2) NetScreensaver(wall, weather.value, prefs.weatherName, prefs.is24(homeCtx)) else IdleScreen(wall, prefs.idleStyle, prefs.is24(homeCtx)) }
        menu?.let { MenuDialog(it) { menu = null } }
        if (settingsOpen) SettingsDialog(vm, prefs, overrideOn, pst, appList, inputList) { settingsOpen = false }
        if (prefs.npPos != 0 && !idle) {
            val (al, pad) = when (prefs.npPos) {
                1 -> Alignment.TopCenter to Modifier.padding(top = 84.dp)
                2 -> Alignment.TopEnd to Modifier.padding(top = 118.dp, end = 24.dp)
                3 -> Alignment.Center to Modifier
                4 -> Alignment.BottomCenter to Modifier.padding(bottom = 28.dp)
                5 -> Alignment.BottomStart to Modifier.padding(start = 24.dp, bottom = 28.dp)
                else -> Alignment.BottomEnd to Modifier.padding(end = 24.dp, bottom = 28.dp)
            }
            NowPlayingCard(prefs.radioOn, { radioOpen = true }, Modifier.align(al).then(pad))
        }
        if (moving != null) Text(
            "◀ ▶  " + t("Mit den Pfeiltasten verschieben · OK = fertig", "Move with arrow keys · OK = done"),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                .background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
        )
        if (freeMoving != null) Text(
            "◀ ▲ ▼ ▶  " + t("Mit den Pfeiltasten verschieben · OK = fertig", "Move with arrow keys · OK = done"),
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                .background(Color.White, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
        )
        inputPlay.value?.let { InputPlayerDialog(it) { vm.closeInput() } }
        webOpen?.let { WebFullDialog(it) { webOpen = null } }
        if (radioOpen) RadioSearchDialog(vm, prefs) { radioOpen = false }
        if (weatherOpen) WeatherDialog(prefs, weather.value, onPlace = { weatherOpen = false; placeOpen = true }) { weatherOpen = false }
        if (placeOpen) PlaceDialog(vm) { placeOpen = false }
        pin?.let { PinHost(it, vm) }
    }
}

@Composable
private fun TopBar(
    prefs: Prefs, status: State<StatusInfo?>, sleepUntil: State<Long>, weather: State<WeatherInfo?>,
    onWidget: () -> Unit, onSearch: () -> Unit, onTimer: () -> Unit, onSettings: () -> Unit, onAndroid: () -> Unit,
    onRadio: () -> Unit, onRestart: () -> Unit,
) {
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(30_000) } }
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = SIDE_PAD),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            ClockWeatherWidget(prefs, weather, onWidget)
            if (prefs.npPos == 0) NowPlayingCard(prefs.radioOn, onRadio)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val radioNow by Radio.now.collectAsState()
                if (prefs.radioOn) IconPill(Ico.RADIO, radioNow?.name ?: "Radio", active = radioNow != null, onClick = onRadio)
                IconPill(Ico.SEARCH, t("Suche", "Search"), onClick = onSearch)
                IconPill(Ico.TIMER, "Timer", onClick = onTimer)
                IconPill(Ico.GEAR, t("Einstellungen", "Settings"), onClick = onSettings)
                IconPill(Ico.PHONE, "Android", onClick = onAndroid)
                if (prefs.restartBtn) IconPill(Ico.RESTART, t("Neustart", "Restart"), onClick = onRestart)
            }
            val left = ((sleepUntil.value - now) / 60_000L).toInt()
            StatusBar(if (prefs.showStatus) status.value else null, if (sleepUntil.value > 0 && left >= 0) left + 1 else 0)
        }
    }
}

@Composable
private fun Hero(hero: State<HeroInfo?>, vm: HomeViewModel, mode: Int, compact: Boolean, modifier: Modifier) {
    if (mode == 0) { Spacer(modifier.height(8.dp)); return }
    val small = mode == 1
    AnimatedContent(
        targetState = hero.value,
        modifier = modifier.heightIn(min = if (small) 48.dp else if (compact) 64.dp else 92.dp),
        transitionSpec = { (fadeIn(tween(220, delayMillis = 130)) + slideInVertically(tween(220, delayMillis = 130)) { it / 8 }) togetherWith fadeOut(tween(120)) },  // erst alter Name raus, dann neuer rein: nie zwei Namen übereinander
        label = "hero",
    ) { h ->
        Column {
            Text(
                h?.title.orEmpty(), fontSize = if (small) 26.sp else if (compact) 34.sp else 50.sp, fontWeight = FontWeight.W300,
                letterSpacing = (-1).sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (h != null) {
                val sub = if (h.app != null) "" else h.extra
                if (sub.isNotEmpty()) Text(sub, fontSize = if (small) 13.sp else if (compact) 14.sp else 16.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Standby-Anzeige: schwarz mit wandernder Uhr, optional mit langsam zoomendem Foto (Ken Burns). */
@Composable
private fun IdleScreen(wall: Wall?, style: Int, is24: Boolean) {
    val now by produceState(LocalDateTime.now()) { while (true) { value = LocalDateTime.now(); delay(15_000) } }
    val pattern = if (is24) "HH:mm" else "h:mm a"
    val seed = now.hour * 60 + now.minute
    val bx = ((seed * 53) % 100) / 100f * 1.4f - 0.7f
    val by = ((seed * 29) % 100) / 100f * 1.4f - 0.7f
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val still = (wall as? Wall.Still)?.bmp
        if (style == 1 && still != null) {
            val t = rememberInfiniteTransition(label = "kenburns")
            val s = t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
            Image(
                still, null,
                Modifier.fillMaxSize().graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = 0.55f },
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            now.format(DateTimeFormatter.ofPattern(pattern)),
            fontSize = 64.sp, fontWeight = FontWeight.W200, color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.align(BiasAlignment(bx, by)),
        )
    }
}
