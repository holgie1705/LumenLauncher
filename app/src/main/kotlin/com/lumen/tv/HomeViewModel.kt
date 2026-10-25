package com.lumen.tv

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private class Aux(val inputs: List<InputSource>, val watch: List<WatchItem>, val now: List<NowItem>, val plugins: List<PluginRow>)

class HomeViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = AppRepo(app)
    private val store = SettingsStore(app)
    private val parental = Parental(app)

    val prefs: StateFlow<Prefs> = store.prefs
    val overrideOn: StateFlow<Boolean> = store.overrideOn
    val sleepUntil: StateFlow<Long> = store.sleepUntil
    val parentalState: StateFlow<ParentalState> = parental.state

    private val apps = MutableStateFlow<List<TvApp>>(emptyList())
    private val inputs = MutableStateFlow<List<InputSource>>(emptyList())
    private val watch = MutableStateFlow<List<WatchItem>>(emptyList())
    private val plugins = MutableStateFlow<List<PluginRow>>(emptyList())
    val appList: StateFlow<List<TvApp>> = apps.asStateFlow()
    val inputList: StateFlow<List<InputSource>> = inputs.asStateFlow()

    private val _usage = MutableStateFlow(repo.usage())
    val usage: StateFlow<Usage> = _usage.asStateFlow()
    private val wallTick = MutableStateFlow(0)

    val pinRequest = MutableStateFlow<PinRequest?>(null)
    private val _forceIdle = MutableStateFlow(false)
    val forceIdle: StateFlow<Boolean> = _forceIdle.asStateFlow()

    // Medien-Sitzungen: nur aktiv, solange die UI sichtbar ist (state ist WhileSubscribed).
    private val nowFlow = flow { while (true) { emit(repo.nowPlaying()); delay(5_000) } }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged { a, b -> a.map { "${it.pkg}|${it.title}|${it.playing}" } == b.map { "${it.pkg}|${it.title}|${it.playing}" } }

    private val aux = combine(inputs, watch, nowFlow, plugins) { i, w, n, p -> Aux(i, w, n, p) }

    val state: StateFlow<HomeState> = combine(apps, _usage, prefs, aux, parental.state) { a, u, p, x, ps ->
        build(a, u, p, x, ps)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    val status: StateFlow<StatusInfo?> = flow { while (true) { emit(repo.status()); delay(15_000) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val wallpaper: StateFlow<Wall?> = combine(prefs.map { it.slideshowMin }.distinctUntilChanged(), wallTick) { m, _ -> m }
        .flatMapLatest { minutes ->
            flow {
                val files = repo.wallFiles().shuffled()
                if (files.isEmpty()) { emit(null); return@flow }
                var i = 0
                while (true) {
                    emit(repo.wall(files[i % files.size]))
                    if (minutes <= 0 || files.size == 1) break
                    delay(minutes * 60_000L)
                    i++
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val weatherRepo = WeatherRepo(app)

    /** Wetter nur laden, solange die UI sichtbar ist; letzter Stand kommt sofort aus dem Cache. */
    val weather: StateFlow<WeatherInfo?> = prefs.map { Triple(it.weather, it.weatherLat, it.weatherLon) to it.tempF }
        .distinctUntilChanged()
        .flatMapLatest { (loc, f) ->
            val (mode, lat, lon) = loc
            flow {
                if (mode == 0 || (lat == 0.0 && lon == 0.0)) { emit(null); return@flow }
                weatherRepo.cached(lat, lon, f)?.let { emit(it) }
                while (true) {
                    val w = weatherRepo.fetch(lat, lon, f)
                    if (w != null) emit(w)
                    delay(if (w == null) 5 * 60_000L else 30 * 60_000L)
                }
            }
        }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    suspend fun searchPlaces(q: String): List<Place> = weatherRepo.search(q)
    fun setPlace(p: Place) = store.update {
        it.copy(weatherName = p.name, weatherLat = p.lat, weatherLon = p.lon, weather = if (it.weather == 0) 2 else it.weather)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = refresh()
    }

    init {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    private fun build(a: List<TvApp>, u: Usage, p: Prefs, x: Aux, ps: ParentalState): HomeState {
        val kids = ps.kids
        val visible = a.filter { it.pkg !in p.hidden && (!kids || it.pkg in ps.allowed) }
        val byPkg = visible.associateBy { it.pkg }
        val now = System.currentTimeMillis()
        val rows = p.rows.filter { it.visible }.flatMap<RowCfg, RowUi> { r ->
            if (kids && r.kind in setOf(RowKind.WATCH, RowKind.PLAYING, RowKind.INPUTS, RowKind.PLUGINS, RowKind.RADIO, RowKind.WEB)) return@flatMap emptyList()
            when (r.kind) {
                RowKind.WATCH -> listOfNotNull(x.watch.takeIf { it.isNotEmpty() }?.let { WatchRowUi(r.id, r.shownTitle(), it) })
                RowKind.PLAYING -> listOfNotNull(x.now.takeIf { it.isNotEmpty() }?.let { NowRowUi(r.id, r.shownTitle(), it) })
                RowKind.NEW -> listOfNotNull(
                    visible.filter { !it.system && now - it.installed < 14L * 24 * 3600 * 1000 }
                        .sortedByDescending { it.installed }.take(8)
                        .takeIf { it.isNotEmpty() }?.let { AppRowUi(r.id, r.shownTitle(), it, false) }
                )
                RowKind.NOW -> listOfNotNull(
                    visible.filter { (u.slot[it.pkg] ?: 0) > 0 }.sortedByDescending { u.slot[it.pkg] ?: 0 }.take(10)
                        .takeIf { it.size >= 2 }?.let { AppRowUi(r.id, r.shownTitle(), it, false) }
                )
                RowKind.FREQ -> listOfNotNull(
                    visible.filter { (u.count[it.pkg] ?: 0) > 0 }.sortedByDescending { u.count[it.pkg] ?: 0 }.take(10)
                        .takeIf { it.isNotEmpty() }?.let { AppRowUi(r.id, r.shownTitle(), it, false) }
                )
                RowKind.WEB -> p.webWidgets.filter { it.on }.let { ws -> if (ws.isNotEmpty()) listOf(WebRowUi(r.id, r.shownTitle(), ws)) else emptyList() }
                RowKind.RADIO -> if (p.radioOn && p.radioRow) listOf(RadioRowUi(r.id, r.shownTitle(), p.stations)) else emptyList()
                RowKind.INPUTS -> listOfNotNull(x.inputs.takeIf { it.isNotEmpty() }?.let { InputRowUi(r.id, r.shownTitle(), it) })
                RowKind.PLUGINS -> x.plugins.map { WatchRowUi(it.id, it.title, it.items) }
                RowKind.CUSTOM -> listOfNotNull(
                    r.pkgs.mapNotNull { byPkg[it] }.takeIf { it.isNotEmpty() }?.let { AppRowUi(r.id, r.shownTitle(), it, true) }
                )
                RowKind.ALL -> {
                    val sorted = when (p.allSort) {
                        1 -> visible.sortedWith(compareByDescending<TvApp> { u.last[it.pkg] ?: 0L }.thenBy { it.label.lowercase() })
                        2 -> visible.sortedWith(compareByDescending<TvApp> { u.count[it.pkg] ?: 0 }.thenBy { it.label.lowercase() })
                        3 -> visible.sortedWith(compareBy<TvApp> { a -> p.appOrder.indexOf(a.pkg).let { if (it < 0) Int.MAX_VALUE else it } }.thenBy { it.label.lowercase() })
                        else -> visible
                    }
                    listOf(AppRowUi(r.id, r.shownTitle(), sorted, false))
                }
            }
        }
        return HomeState(rows)
    }

    fun refresh() {
        viewModelScope.launch {
            val n = repo.loadApps()
            if (n.map { it.pkg } != apps.value.map { it.pkg }) {
                apps.value = n
                parental.setLaunchable(n.map { it.pkg }.toSet())
            }
            _usage.value = repo.usage()
            inputs.value = repo.inputs()
            watch.value = repo.watchNext()
            plugins.value = repo.plugins()
        }
    }

    fun reloadWallpapers() { wallTick.value++ }
    suspend fun art(a: TvApp): Art = repo.art(a)
    suspend fun accent(a: TvApp): Color = repo.art(a).accent

    // ---------- Starten ----------
    private fun launch(a: TvApp) { repo.bump(a.pkg); _usage.value = repo.usage(); repo.start(a.intent) }

    fun open(a: TvApp) {
        if (parental.isBlocked(a.pkg)) requirePin("${t("Gesperrt", "Locked")}: ${a.label}") { parental.unlock(30); launch(a) } else launch(a)
    }

    fun openPkg(pkg: String) {
        apps.value.firstOrNull { it.pkg == pkg }?.let { open(it) } ?: repo.launchIntent(pkg)?.let { repo.start(it) }
    }

    fun openInput(i: InputSource) = repo.start(i.intent)
    fun openWatch(w: WatchItem) { w.intent?.let { repo.start(it) } }
    fun toggleNow(n: NowItem) {
        runCatching { if (n.playing) n.controller.transportControls.pause() else n.controller.transportControls.play() }
    }
    fun details(a: TvApp) = repo.showDetails(a.pkg)
    fun uninstall(a: TvApp) = repo.uninstall(a.pkg)
    fun assist() = repo.start(Intent(Intent.ACTION_ASSIST))

    // ---------- Von Service/Activity ----------
    fun lockedApp(pkg: String) {
        val label = apps.value.firstOrNull { it.pkg == pkg }?.label ?: pkg
        requirePin("${t("Gesperrt", "Locked")}: $label") { parental.unlock(30); repo.launchIntent(pkg)?.let { repo.start(it) } }
    }
    fun forceIdle() { _forceIdle.value = true }
    fun clearForceIdle() { _forceIdle.value = false }

    // ---------- PIN / Kindersicherung ----------
    fun requirePin(title: String, onOk: () -> Unit) {
        if (!parental.state.value.hasPin) onOk() else pinRequest.value = PinRequest(title, false, onOk)
    }
    fun requireNewPin(onOk: () -> Unit) { pinRequest.value = PinRequest(t("Neue PIN festlegen", "Set new PIN"), true, onOk) }
    fun requirePinIfLocked(onOk: () -> Unit) {
        val s = parental.state.value
        if (s.hasPin && (s.on || s.kids)) requirePin(t("Einstellungen", "Settings"), onOk) else onOk()
    }
    fun cancelPin() { pinRequest.value = null }
    fun verifyPin(pin: String) = parental.verify(pin)
    fun setPin(pin: String) = parental.setPin(pin)

    fun toggleParental() {
        val s = parental.state.value
        if (s.on) parental.setOn(false) else if (s.hasPin) parental.setOn(true) else requireNewPin { parental.setOn(true) }
    }
    fun toggleKids() {
        val s = parental.state.value
        if (s.kids) parental.setKids(false) else if (s.hasPin) parental.setKids(true) else requireNewPin { parental.setKids(true) }
    }
    fun changePin() = requireNewPin { }
    fun cycleFrom() = parental.state.value.let { parental.setHours((it.fromH + 1) % 24, it.toH) }
    fun cycleTo() = parental.state.value.let { parental.setHours(it.fromH, (it.toH + 1) % 24) }
    fun toggleBlocked(pkg: String) = parental.toggleBlocked(pkg)
    fun toggleAllowed(pkg: String) = parental.toggleAllowed(pkg)

    // ---------- Einstellungen / Layout ----------
    fun update(f: (Prefs) -> Prefs) = store.update(f)
    fun setOverride(v: Boolean) = store.setOverride(v)
    fun setSleepTimer(min: Int) = store.setSleep(if (min <= 0) 0L else System.currentTimeMillis() + min * 60_000L)
    fun hide(pkg: String) = store.update { it.copy(hidden = it.hidden + pkg) }
    fun unhideAll() = store.update { it.copy(hidden = emptySet()) }
    fun setAutostart(pkg: String) = store.update { it.copy(autostart = pkg) }

    fun addToRow(rowId: String, pkg: String) = store.update { p ->
        p.copy(rows = p.rows.map { if (it.id == rowId && pkg !in it.pkgs) it.copy(pkgs = it.pkgs + pkg) else it })
    }
    fun removeFromRow(rowId: String, pkg: String) = store.update { p ->
        p.copy(rows = p.rows.map { if (it.id == rowId) it.copy(pkgs = it.pkgs - pkg) else it })
    }
    fun newRow(title: String, pkg: String) = store.update { p ->
        val at = p.rows.indexOfFirst { it.kind == RowKind.ALL }.let { if (it < 0) p.rows.size else it }
        val row = RowCfg("c_${System.currentTimeMillis()}", RowKind.CUSTOM, title, true, listOf(pkg))
        p.copy(rows = p.rows.toMutableList().apply { add(at, row) })
    }
    fun moveApp(rowId: String, pkg: String, d: Int) = store.update { p ->
        p.copy(rows = p.rows.map { r ->
            val i = r.pkgs.indexOf(pkg)
            val j = i + d
            if (r.id != rowId || i < 0 || j !in r.pkgs.indices) r
            else r.copy(pkgs = r.pkgs.toMutableList().apply { add(j, removeAt(i)) })
        })
    }
    /** Kachel in "Alle Apps" verschieben: schaltet auf "Eigene Reihenfolge" um. */
    fun moveInAll(current: List<String>, pkg: String, d: Int) = store.update { p ->
        val order = current.toMutableList()
        val i = order.indexOf(pkg)
        val j = i + d
        if (i < 0 || j !in order.indices) p else p.copy(allSort = 3, appOrder = order.apply { add(j, removeAt(i)) })
    }
    fun moveRow(id: String, d: Int) = store.update { p ->
        val i = p.rows.indexOfFirst { it.id == id }
        val j = i + d
        if (i < 0 || j !in p.rows.indices) p else p.copy(rows = p.rows.toMutableList().apply { add(j, removeAt(i)) })
    }
    fun toggleRow(id: String) = store.update { p -> p.copy(rows = p.rows.map { if (it.id == id) it.copy(visible = !it.visible) else it }) }
    fun addStation(s: Station) = store.update { p -> if (p.stations.any { it.uuid == s.uuid }) p else p.copy(stations = p.stations + s) }
    fun addWidget(w: WebWidget) = store.update { p -> p.copy(webWidgets = p.webWidgets + w) }
    fun setWidgetSize(id: String, size: Int) = store.update { p -> p.copy(webWidgets = p.webWidgets.map { if (it.id == id) WebWidget(it.id, it.name, it.url, it.refreshMin, size, it.on) else it }) }
    fun toggleWidget(id: String) = store.update { p -> p.copy(webWidgets = p.webWidgets.map { if (it.id == id) WebWidget(it.id, it.name, it.url, it.refreshMin, it.size, !it.on) else it }) }
    fun removeWidget(id: String) = store.update { p -> p.copy(webWidgets = p.webWidgets.filter { it.id != id }) }
    fun setWidgetRefresh(id: String, min: Int) = store.update { p -> p.copy(webWidgets = p.webWidgets.map { if (it.id == id) WebWidget(it.id, it.name, it.url, min, it.size, it.on) else it }) }
    fun removeStation(uuid: String) = store.update { p -> p.copy(stations = p.stations.filter { it.uuid != uuid }) }
    fun deleteRow(id: String) = store.update { p -> p.copy(rows = p.rows.filter { it.id != id }) }

    // ---------- Export, Themes, Fonts, Root ----------
    private fun settingsFile() = File(app.getExternalFilesDir(null), "lumen-settings.json")
    fun exportSettings(): String = runCatching { settingsFile().also { it.writeText(store.export()) }.path }.getOrElse { "${t("Fehler", "Error")}: ${it.message}" }
    fun importSettings(): Boolean = settingsFile().let { it.exists() && store.import(it.readText()) }

    fun fontFiles(): List<String> = repo.fontFiles()
    fun themeFiles(): List<File> = repo.themeFiles()
    fun saveTheme(): String = runCatching {
        val dir = repo.themeDir() ?: error("no folder")
        dir.mkdirs()
        val f = File(dir, "theme-${System.currentTimeMillis() / 1000}.json")
        f.writeText(prefs.value.styleJson().toString(2))
        f.name
    }.getOrElse { "${t("Fehler", "Error")}: ${it.message}" }
    fun applyTheme(f: File): Boolean = runCatching { store.update { it.withStyle(JSONObject(f.readText())) }; true }.getOrDefault(false)

    fun freezeStock(freeze: Boolean, done: (Boolean) -> Unit) {
        viewModelScope.launch { done(repo.freezeStock(freeze)) }
    }
    fun notificationAccess() = repo.notificationAccess()

    /** System-App deaktivieren: mit Root direkt, sonst wird der ADB-Befehl in debloat.bat/restore.bat gesammelt. */
    fun disableApp(a: TvApp, done: (String) -> Unit) {
        viewModelScope.launch {
            val msg = repo.disablePkg(a.pkg)
            refresh()
            done(msg)
        }
    }

    override fun onCleared() {
        runCatching { app.unregisterReceiver(receiver) }
    }
}
