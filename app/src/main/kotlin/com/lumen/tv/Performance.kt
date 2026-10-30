package com.lumen.tv

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Momentaufnahme der wichtigsten Systemwerte. */
class SysInfo(
    val ramTotal: Long, val ramAvail: Long, val storTotal: Long, val storFree: Long,
    val tempC: Int?, val thermal: Int, val headroom: Float?, val cores: Int, val maxMhz: Int?, val uptimeMs: Long, val lumenMb: Int,
) {
    val ramUsedPct get() = if (ramTotal > 0) ((ramTotal - ramAvail) * 100 / ramTotal).toInt() else 0
    val storUsedPct get() = if (storTotal > 0) ((storTotal - storFree) * 100 / storTotal).toInt() else 0
}

object SysTools {
    fun read(ctx: Context): SysInfo {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val st = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        // Temperatur: höchster plausibler Wert aus Thermal-Zonen bzw. hwmon (auf vielen Boxen von Android gesperrt)
        val paths = (0..15).map { "/sys/class/thermal/thermal_zone$it/temp" } + (0..15).map { "/sys/devices/virtual/thermal/thermal_zone$it/temp" } +
            (0..7).map { "/sys/class/hwmon/hwmon$it/temp1_input" } + listOf("/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp", "/proc/mtktscpu/mtktscpu")
        val temp = paths.mapNotNull { p ->
            runCatching { File(p).readText().trim().split(Regex("\\s+")).first().toInt() }.getOrNull()
                ?.let { if (it > 1000) it / 1000 else it }?.takeIf { it in 15..125 }
        }.maxOrNull()
        // Ersatz ohne Gradzahl: offizieller Wärmestatus von Android (0 = normal … 6 = Abschaltung) und Reserve bis zur Drosselung
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        val thermal = if (Build.VERSION.SDK_INT >= 29) runCatching { pm.currentThermalStatus }.getOrDefault(-1) else -1
        val head = if (Build.VERSION.SDK_INT >= 30) runCatching { pm.getThermalHeadroom(10) }.getOrNull()?.takeIf { !it.isNaN() && it >= 0f } else null
        val mhz = runCatching { File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").readText().trim().toInt() / 1000 }.getOrNull()
        val lumen = runCatching { am.getProcessMemoryInfo(intArrayOf(Process.myPid()))[0].totalPss / 1024 }.getOrDefault(0)
        return SysInfo(mi.totalMem, mi.availMem, st?.totalBytes ?: 0, st?.availableBytes ?: 0, temp, thermal, head, Runtime.getRuntime().availableProcessors(), mhz, SystemClock.elapsedRealtime(), lumen)
    }

    /**
     * Apps, die der Boost nie anfasst: System-Apps, Launcher, Tastatur, Bedienungshilfen, Benachrichtigungs-Zugriffe,
     * VPN-Apps und alles, was nach Systemdienst aussieht.
     */
    fun protectedPkgs(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val out = mutableSetOf(ctx.packageName)
        fun pkgsOf(i: android.content.Intent, service: Boolean) = runCatching {
            if (service) pm.queryIntentServices(i, 0).map { it.serviceInfo.packageName } else pm.queryIntentActivities(i, 0).map { it.activityInfo.packageName }
        }.getOrDefault(emptyList())
        out += pkgsOf(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME), false)
        out += pkgsOf(android.content.Intent("android.net.VpnService"), true)
        val sec = ctx.contentResolver
        fun secure(k: String) = runCatching { Settings.Secure.getString(sec, k) }.getOrNull().orEmpty()
        out += secure("default_input_method").substringBefore('/')
        out += secure(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).split(':').map { it.substringBefore('/') }
        out += secure("enabled_notification_listeners").split(':').map { it.substringBefore('/') }
        return out.filter { it.isNotBlank() }.toSet()
    }

    fun isSystem(ctx: Context, pkg: String): Boolean = runCatching {
        val ai = ctx.packageManager.getApplicationInfo(pkg, 0)
        (ai.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
    }.getOrDefault(true) ||
        listOf("android", "com.android.", "com.google.android.gms", "com.google.android.gsf", "providers", "inputmethod", "launcher", "setupwraith", "systemui", "packageinstaller")
            .any { pkg == it || pkg.startsWith(it) || pkg.contains(".$it") }

    /**
     * Leert den Hintergrund aller Apps (auch vorinstallierter System-Apps wie Netflix/YouTube/waipu) – nur
     * Launcher, Tastatur, Bedienungshilfen, VPN, Lumen, Ausnahmen und gerade spielende Apps bleiben unberührt.
     * killBackgroundProcesses ist ungefährlich: aktive Vordergrund-Apps und echte Systemdienste kann es gar nicht beenden.
     * Gibt (freigegebene MB, Anzahl bearbeiteter Apps) zurück.
     */
    suspend fun boost(ctx: Context, pkgs: List<String>, keep: Set<String>): Pair<Int, Int> = withContext(Dispatchers.Default) {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val prot = protectedPkgs(ctx)
        // Kandidaten: die (startbaren) Apps – auch vorinstallierte System-Apps wie Netflix/YouTube/waipu – ohne Geschützte/Ausnahmen
        val candidates = pkgs.distinct().filter { it != ctx.packageName && it !in keep && it !in prot }
        val before = read(ctx).ramAvail
        var n = 0
        candidates.forEach { p -> runCatching { am.killBackgroundProcesses(p); n++ } }
        delay(1800)
        val after = read(ctx).ramAvail
        (((after - before) / (1024 * 1024)).toInt().coerceAtLeast(0)) to n
    }

    /** Eigenen Cache von Lumen leeren (Bilder, Logos, Vorschauen). Gibt freigegebene MB zurück. */
    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    suspend fun clearOwnCache(ctx: Context): Int = withContext(Dispatchers.IO) {
        val size = ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        ctx.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        runCatching { ctx.imageLoader.memoryCache?.clear(); ctx.imageLoader.diskCache?.clear() }
        (size / (1024 * 1024)).toInt()
    }

    /** Caches aller Apps leeren – geht nur mit Root (sonst per adb vom PC). */
    suspend fun trimAllCaches(): Boolean = withContext(Dispatchers.IO) {
        runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", "pm trim-caches 999G")).waitFor() == 0 }.getOrDefault(false)
    }
}

private fun gb(b: Long) = "%.1f".format(Lang.locale, b / 1e9)
private fun uptime(ms: Long): String {
    val m = ms / 60_000; val d = m / 1440; val h = (m % 1440) / 60; val mm = m % 60
    return if (d > 0) "${d} d ${h} h" else "${h} h ${mm} min"
}

@Composable
fun PerformanceDialog(vm: HomeViewModel, prefs: Prefs, apps: List<TvApp>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var excludeOpen by remember { mutableStateOf(false) }
    val info by produceState(SysTools.read(ctx)) { while (true) { delay(2000); value = withContext(Dispatchers.Default) { SysTools.read(ctx) } } }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

    fun boost() {
        if (busy) return
        busy = true; result = t("RAM-Boost läuft …", "RAM boost running …")
        scope.launch {
            val playing = vm.nowSessions().filter { it.playing }.map { it.pkg }.toSet()
            val (mb, n) = SysTools.boost(ctx, apps.map { it.pkg }, prefs.boostExclude + playing)
            result = if (mb > 0) t("⚡ Hintergrund von $n Apps geleert · $mb MB frei geworden", "⚡ Cleared background of $n apps · freed $mb MB")
                else t("⚡ Hintergrund von $n Apps geleert · es lief kaum etwas im Hintergrund", "⚡ Cleared background of $n apps · little was running in the background")
            busy = false
        }
    }
    fun clean() {
        if (busy) return
        busy = true; result = t("Räume auf …", "Cleaning …")
        scope.launch {
            val own = SysTools.clearOwnCache(ctx)
            val all = SysTools.trimAllCaches()
            result = if (all) t("🧹 Caches aller Apps geleert (Root) · Lumen: $own MB", "🧹 Cleared all app caches (root) · Lumen: $own MB")
            else t("🧹 Lumen-Cache geleert ($own MB). Für alle Apps am PC: adb shell pm trim-caches 999G", "🧹 Lumen cache cleared ($own MB). For all apps on your PC: adb shell pm trim-caches 999G")
            busy = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.GAUGE, t("Leistung", "Performance"), t("RAM, Speicher & System auf einen Blick", "RAM, storage & system at a glance"), width = 1000.dp) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ---------- Anzeigen ----------
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        GaugeCard(
                            t("Arbeitsspeicher", "Memory"), info.ramUsedPct, "${gb(info.ramTotal - info.ramAvail)} / ${gb(info.ramTotal)} GB",
                            t("${gb(info.ramAvail)} GB frei", "${gb(info.ramAvail)} GB free"), Color(0xFF5CF2B5), Color(0xFF2FB8FF), Modifier.weight(1f),
                        )
                        GaugeCard(
                            t("Speicher", "Storage"), info.storUsedPct, "${gb(info.storTotal - info.storFree)} / ${gb(info.storTotal)} GB",
                            t("${gb(info.storFree)} GB frei", "${gb(info.storFree)} GB free"), Color(0xFFFFB648), Color(0xFFFF5FA2), Modifier.weight(1f),
                        )
                        val temp = info.tempC
                        val stateTxt = when (info.thermal) {
                            0 -> t("normal", "normal"); 1 -> t("leicht warm", "light"); 2 -> t("warm", "moderate"); 3 -> t("heiß – drosselt", "severe")
                            4, 5, 6 -> t("kritisch!", "critical!"); else -> t("unbekannt", "unknown")
                        }
                        if (temp != null) GaugeCard(
                            t("Temperatur", "Temperature"), temp.coerceIn(0, 100), "$temp °C",
                            when { temp < 60 -> t("alles kühl", "cool"); temp < 75 -> t("warm", "warm"); else -> t("heiß!", "hot!") },
                            Color(0xFF7C9CFF), Color(0xFFFF6B6B), Modifier.weight(1f),
                        ) else GaugeCard(
                            // Box gibt keine Gradzahl frei: Android-Wärmestatus + Auslastung bis zur Drosselung
                            t("Wärme", "Thermal"), info.headroom?.let { (it * 100).toInt().coerceIn(0, 100) } ?: (info.thermal.coerceAtLeast(0) * 100 / 6),
                            stateTxt, info.headroom?.let { t("bis zur Drosselung", "until throttling") } ?: t("Wärmestatus von Android", "Android thermal status"),
                            Color(0xFF7C9CFF), Color(0xFFFF6B6B), Modifier.weight(1f),
                        )
                    }
                }
                // ---------- Aktionen ----------
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ActionTile(
                            "⚡", t("RAM-Boost", "RAM boost"),
                            t("Beendet sofort alle Hintergrund-Apps · ${prefs.boostExclude.size} Ausnahmen", "Instantly stops all background apps · ${prefs.boostExclude.size} exceptions"),
                            Color(0xFFFF5FA2), Modifier.weight(1f).focusRequester(first),
                        ) { boost() }
                        ActionTile(
                            "🧹", t("Aufräumen", "Clean up"), t("Caches & Restdaten löschen", "Delete caches & leftovers"),
                            NeoCyan, Modifier.weight(1f),
                        ) { clean() }
                    }
                }
                if (result.isNotEmpty()) item {
                    Text(result, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(14.dp))
                }
                item { SheetSection(t("Einstellungen", "Settings")) }
                item { SettingLine(t("Ausnahmen für den RAM-Boost", "RAM boost exceptions"), t("${prefs.boostExclude.size} Apps ausgenommen", "${prefs.boostExclude.size} apps excepted")) { excludeOpen = true } }
                item { SettingLine(t("Android-Speicherverwaltung öffnen", "Open Android storage settings"), "") { openSettingsPage(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS)?.let { toast(it) } } }
                // ---------- Systeminfos ----------
                item { SheetSection(t("System", "System")) }
                item {
                    val rows = listOf(
                        t("Gerät", "Device") to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        "CPU" to "${info.cores} " + t("Kerne", "cores") + (info.maxMhz?.let { " · bis $it MHz" } ?: ""),
                        t("Bildschirm", "Display") to ctx.resources.displayMetrics.let { "${it.widthPixels} × ${it.heightPixels}" },
                        t("Läuft seit", "Uptime") to uptime(info.uptimeMs),
                        t("Lumen braucht", "Lumen uses") to "${info.lumenMb} MB RAM",
                    )
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.05f)).padding(vertical = 6.dp)) {
                        rows.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp)) {
                                Text(k, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                                Text(v, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                item {
                    Text(
                        t("Geschützt sind immer: Launcher, Tastatur, Bedienungshilfen, VPN-Apps, Lumen selbst, deine Ausnahmen und gerade spielende Apps. Alles andere – auch vorinstallierte Apps wie Netflix oder waipu – wird im Hintergrund beendet. „Beenden erzwingen“ gibt es nicht ohne Root; Android startet Apps bei Bedarf selbst wieder. Caches anderer Apps lassen sich ohne Root nur per adb vom PC leeren.",
                            "Always protected: launchers, keyboard, accessibility services, VPN apps, Lumen itself, your exceptions and apps that are playing. Everything else — including preinstalled apps like Netflix or waipu — gets its background cleared. Force-stop isn't possible without root; Android restarts apps when needed. Other apps' caches can only be cleared with root or via adb from a PC."),
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                item { Pill(t("Schließen", "Close"), Modifier.padding(start = 4.dp)) { onClose() } }
            }
        }
        if (excludeOpen) BoostExcludeDialog(vm, prefs, apps) { excludeOpen = false }
    }
}

/** Ringanzeige mit Prozent in der Mitte, Titel und zwei Zeilen Info. */
@Composable
private fun GaugeCard(title: String, pct: Int, line1: String, line2: String, c1: Color, c2: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(c1.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))))
            .border(1.dp, NeoLine, RoundedCornerShape(22.dp)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 9.dp.toPx()
                val inset = w / 2
                val sz = Size(size.width - w, size.height - w)
                drawArc(Color.White.copy(alpha = 0.12f), 135f, 270f, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                drawArc(Brush.sweepGradient(listOf(c1, c2, c1)), 135f, 270f * pct.coerceIn(0, 100) / 100f, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text("$pct%", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text(line1, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            Text(line2, fontSize = 12.sp, color = c1, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Große Aktionskachel (wie RAM-Boost / Aufräumen). */
@Composable
private fun ActionTile(emoji: String, title: String, sub: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick, modifier = modifier.height(118.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.5.dp, tint.copy(alpha = 0.6f)), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, NeoYellow), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(tint.copy(alpha = 0.6f), 20.dp)),
    ) {
        Column(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(tint.copy(alpha = 0.40f), tint.copy(alpha = 0.12f)))).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(emoji, fontSize = 30.sp)
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(sub, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Liste aller Apps mit Schalter: „An“ = wird vom RAM-Boost nie beendet. */
@Composable
private fun BoostExcludeDialog(vm: HomeViewModel, prefs: Prefs, apps: List<TvApp>, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    val ctx = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    // System-Apps sind ohnehin immer geschützt und erscheinen hier nicht. Reihenfolge fest (alphabetisch) – der Schalter bleibt an Ort und Stelle.
    val sorted = remember(apps) { val prot = SysTools.protectedPkgs(ctx); apps.filter { it.pkg !in prot }.sortedBy { it.label.lowercase() } }
    val orphans = (prefs.boostExclude - sorted.map { it.pkg }.toSet())   // Ausnahmen ohne passende App (deinstalliert o. Ä.)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.LOCK, t("Ausnahmen", "Exceptions"), t("„An“ = diese App beendet der RAM-Boost nie · System-Apps sind immer geschützt", "“On” = the RAM boost never stops this app · system apps are always protected"), width = 720.dp) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { SheetSection(t("Ausgenommen", "Excepted") + " (${prefs.boostExclude.size})") }
                // Ausnahmen zu nicht (mehr) installierten Apps – oben, zum Entfernen
                items(orphans.toList(), key = { "o_$it" }) { pkg ->
                    SettingLine(pkg, t("An", "On")) { vm.update { p -> p.copy(boostExclude = p.boostExclude - pkg) } }
                }
                item { SheetSection(t("Alle Apps", "All apps")) }
                itemsIndexed(sorted, key = { _, a -> a.pkg }) { i, a ->
                    val on = a.pkg in prefs.boostExclude
                    SettingLine(a.label, if (on) t("An", "On") else t("Aus", "Off"), if (i == 0) Modifier.focusRequester(first) else Modifier) {
                        vm.update { p -> p.copy(boostExclude = if (on) p.boostExclude - a.pkg else p.boostExclude + a.pkg) }
                    }
                }
                item { Pill(t("Fertig", "Done"), Modifier.padding(start = 12.dp, top = 10.dp)) { onClose() } }
            }
        }
    }
}
