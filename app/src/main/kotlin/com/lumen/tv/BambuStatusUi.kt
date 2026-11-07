package com.lumen.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** true, wenn genug für die Statusabfrage eingetragen ist (Bridge-URL ODER direktes MQTT). */
fun bambuConfigured(p: Prefs) = p.bambuHttp.isNotBlank() ||
    (p.bambuIp.isNotBlank() && p.bambuSn.isNotBlank() && p.bambuCode.isNotBlank())

private fun temp(v: Double) = if (v < 0) "–" else v.toInt().toString() + "°"
private fun remainText(min: Int): String {
    if (min < 0) return "–"
    if (min < 60) return "$min min"
    return "${min / 60} h ${(min % 60).toString().padStart(2, '0')} min"
}

/** Voraussichtliche Fertig-Uhrzeit aus der Restzeit. */
private fun etaText(min: Int): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(System.currentTimeMillis() + min * 60_000L))

private fun stageText(s: String): String = when (s.uppercase()) {
    "RUNNING" -> t("Druckt", "Printing")
    "PAUSE" -> t("Pause", "Paused")
    "FINISH" -> t("Fertig", "Done")
    "FAILED" -> t("Fehler", "Failed")
    "PREPARE", "SLICING" -> t("Vorbereiten", "Preparing")
    "IDLE" -> t("Bereit", "Idle")
    "" -> ""
    else -> s
}

/** JSON der Status-Bridge holen (kurzer Timeout). */
private fun bambuHttpFetch(url: String): String? = runCatching {
    val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
    try { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
}.getOrNull()

/** Schmale Statusleiste über dem Kamerabild: Temperaturen, Fortschritt, Restzeit, AMS. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BambuStatusBar(prefs: Prefs, modifier: Modifier = Modifier) {
    if (!bambuConfigured(prefs)) return
    var state by remember { mutableStateOf(BambuState()) }
    if (prefs.bambuHttp.isNotBlank()) {
        // Status über die lokale Bridge (go2rtc-Box) holen – funktioniert auch im Cloud-Modus.
        LaunchedEffect(prefs.bambuHttp) {
            while (true) {
                val txt = withContext(Dispatchers.IO) { bambuHttpFetch(prefs.bambuHttp.trim()) }
                state = if (txt == null) state.copy(connected = false, error = t("Bridge nicht erreichbar", "Bridge unreachable"))
                else runCatching { JSONObject(txt).optJSONObject("print")?.let { bambuMergePrint(state, it) } ?: state.copy(connected = true, error = "") }
                    .getOrDefault(state.copy(connected = false, error = "JSON?"))
                delay(3000)
            }
        }
    } else {
        // Direkt per MQTT zum Drucker (nur im „Nur LAN"-Modus sinnvoll).
        DisposableEffect(prefs.bambuIp, prefs.bambuSn, prefs.bambuCode) {
            val client = BambuClient(prefs.bambuIp.trim(), prefs.bambuSn.trim(), prefs.bambuCode.trim()) { s -> state = s }
            client.start()
            onDispose { client.stop() }
        }
    }

    // Kompakte, schwebende Leiste unten – oben bleibt der Druckkopf frei.
    Row(
        modifier.padding(bottom = 12.dp, start = 12.dp, end = 12.dp).fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            Modifier.weight(1f)
                .background(Color(0xCC0A0D14), RoundedCornerShape(14.dp))
                .border(1.dp, NeoLine, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Kopfzeile: Punkt · Phase · Auftrag · Prozent · Restzeit
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).background(if (state.connected) Color(0xFF7CE0A3) else Color(0xFFE0A14F), CircleShape))
                if (prefs.bambuName.isNotEmpty()) Text(prefs.bambuName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (state.stage.isNotEmpty()) StagePill(state.stage)
                if (state.stage.equals("FAILED", true)) Text(
                    t("⚠ Fehler", "⚠ Error"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE06A6A),
                )
                if (!state.connected) Text(
                    if (state.error.isEmpty()) t("verbinde …", "connecting …") else state.error,
                    fontSize = 11.sp, color = Color(0xFFE0A14F), maxLines = 1,
                )
                if (state.job.isNotEmpty())
                    Text(state.job, fontSize = 13.sp, color = Color.White.copy(alpha = 0.92f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                else Spacer(Modifier.weight(1f))
                if (state.progress >= 0) Text("${state.progress}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeoCyan)
                if (state.remainMin >= 0) Text(remainText(state.remainMin), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
            // Dünner Fortschrittsbalken
            if (state.progress in 0..100) Box(
                Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(2.dp)),
            ) {
                Box(Modifier.fillMaxWidth(state.progress / 100f).height(4.dp)
                    .background(Brush.horizontalGradient(listOf(NeoViolet, NeoCyan)), RoundedCornerShape(2.dp)))
            }
            // Werte-Chips (kompakt, brechen bei Bedarf in die nächste Zeile um)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(t("Düse", "Nozzle"), temp(state.nozzle) + (if (state.nozzleTarget > 0) "→" + temp(state.nozzleTarget) else ""))
                Chip(t("Bett", "Bed"), temp(state.bed) + (if (state.bedTarget > 0) "→" + temp(state.bedTarget) else ""))
                if (state.chamber >= 0) Chip(t("Kammer", "Chamber"), temp(state.chamber))
                if (state.layer >= 0 && state.totalLayer > 0) Chip(t("Schicht", "Layer"), "${state.layer}/${state.totalLayer}")
                if (state.remainMin > 0) Chip(t("Fertig um", "Done at"), etaText(state.remainMin))
                if (state.speed >= 0) Chip(t("Tempo", "Speed"), "${state.speed}%")
                if (state.fan >= 0) Chip(t("Lüfter", "Fan"), "${state.fan}%")
                if (state.nozzleType.isNotEmpty()) Chip("Hotend", state.nozzleType + (if (state.nozzleDia.isNotEmpty()) " ${state.nozzleDia}mm" else ""))
                if (state.wifi.isNotEmpty()) Chip("WLAN", state.wifi)
                if (state.amsHumidity in 0..100) Chip(t("Luftfeuchte", "Humidity"), "${state.amsHumidity}%")
                if (state.light >= 0) Chip(t("Licht", "Light"), if (state.light == 1) t("an", "on") else t("aus", "off"))
                state.trays.sortedByDescending { it.id == state.activeTrayId && state.activeTrayId.isNotEmpty() }.take(6)
                    .forEach { tr -> AmsChip(tr, tr.id == state.activeTrayId && state.activeTrayId.isNotEmpty()) }
            }
        }
    }
}

@Composable
private fun Chip(label: String, value: String) {
    Row(
        Modifier.background(NeoCard, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f))
        Text(value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AmsChip(tr: BambuTray, active: Boolean) {
    Row(
        Modifier.background(if (active) NeoGlassHi else NeoCard, RoundedCornerShape(8.dp))
            .then(if (active) Modifier.border(1.5.dp, NeoCyan, RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(12.dp).background(Color(tr.colorArgb), CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape))
        val grams = if (tr.weightG > 0 && tr.remain in 1..100) " · ${tr.weightG * tr.remain / 100}g" else ""
        Text(
            (if (active) "▶ " else "") + (if (tr.external) t("Extern ", "Ext ") else "") + tr.type + (if (tr.remain in 1..100) " ${tr.remain}%" else "") + grams,
            fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Color.White else Color.White.copy(alpha = 0.9f),
        )
    }
}

@Composable
private fun StagePill(stage: String) {
    val col = when (stage.uppercase()) {
        "RUNNING" -> Color(0xFF7CE0A3)
        "PAUSE" -> Color(0xFFE0C04F)
        "FAILED" -> Color(0xFFE06A6A)
        "FINISH" -> NeoCyan
        else -> Color(0xFF9AA0AC)
    }
    Text(
        stageText(stage), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black,
        modifier = Modifier.background(col, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Einstellungen für den Bambu-Druckerstatus (IP / Seriennummer / Access Code). */
@Composable
fun BambuSettingsDialog(vm: HomeViewModel, onClose: () -> Unit) {
    val prefs by vm.prefs.collectAsState()
    var editing by remember { mutableStateOf(0) }   // 0 nichts, 1 IP, 2 SN, 3 Code
    var text by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(editing) { if (editing != 0) { delay(80); runCatching { field.requestFocus() } } }

    fun save(v: String) {
        when (editing) {
            1 -> vm.update { it.copy(bambuIp = v.trim()) }
            2 -> vm.update { it.copy(bambuSn = v.trim()) }
            3 -> vm.update { it.copy(bambuCode = v.trim()) }
            4 -> vm.update { it.copy(bambuHttp = v.trim()) }
            5 -> vm.update { it.copy(bambuName = v.trim()) }
        }
        editing = 0
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.also { spatialBlur(it) }?.setDimAmount(0f)
        NeoSheet(Ico.INFO, t("3D-Drucker (Bambu)", "3D printer (Bambu)"), t("Live-Status über das lokale Netzwerk (MQTT) – ganz ohne Cloud", "Live status over the local network (MQTT) – no cloud"), width = 860.dp) {
            if (editing != 0) BasicTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 19.sp), cursorBrush = SolidColor(NeoCyan),
                keyboardOptions = KeyboardOptions(keyboardType = if (editing == 1) KeyboardType.Number else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save(text) }),
                modifier = Modifier.fillMaxWidth().background(NeoGlass, RoundedCornerShape(14.dp)).border(2.dp, NeoYellow, RoundedCornerShape(14.dp)).padding(14.dp).focusRequester(field),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { SettingLine(t("Modell / Name", "Model / name"), prefs.bambuName.ifEmpty { "–" }) { text = prefs.bambuName; editing = 5 } }
                item { SettingLine(t("Bridge-URL (empfohlen)", "Bridge URL (recommended)"), prefs.bambuHttp.ifEmpty { "–" }) { text = prefs.bambuHttp; editing = 4 } }
                item { SettingLine(t("— oder direkt: Drucker-IP", "— or direct: Printer IP"), prefs.bambuIp.ifEmpty { "–" }) { text = prefs.bambuIp; editing = 1 } }
                item { SettingLine(t("Seriennummer", "Serial number"), prefs.bambuSn.ifEmpty { "–" }) { text = prefs.bambuSn; editing = 2 } }
                item { SettingLine(t("Access Code", "Access code"), if (prefs.bambuCode.isEmpty()) "–" else "••••••") { text = prefs.bambuCode; editing = 3 } }
                item {
                    SettingLine(
                        t("Benachrichtigung bei Druck fertig/Fehler", "Notify on print finished/error"),
                        if (prefs.bambuHttp.isBlank()) t("nur mit Bridge-URL", "needs bridge URL")
                        else if (prefs.bambuNotify) t("An", "On") else t("Aus", "Off"),
                    ) { if (prefs.bambuHttp.isNotBlank()) vm.update { it.copy(bambuNotify = !it.bambuNotify) } }
                }
                item {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t("So geht's:", "How it works:"), fontSize = 14.sp, color = Color.White)
                        Text(
                            t("Empfohlen (auch im Cloud-Modus): die Status-Bridge. Trag oben die Bridge-URL ein, z. B. http://192.168.0.189:8099/status – dann holt Lumen alle Werte von dort. " +
                                "Alternativ direkt vom Drucker per IP + Seriennummer + Access Code – das funktioniert aber nur im „Nur LAN\"-Modus. " +
                                "Der Status erscheint über dem Kamerabild, wenn du die Kamera-Kachel groß öffnest.",
                                "Recommended (works even in cloud mode): the status bridge. Enter the bridge URL above, e.g. http://192.168.0.189:8099/status – Lumen reads all values from there. " +
                                "Alternatively directly from the printer via IP + serial + access code – but that only works in \"LAN Only\" mode. " +
                                "The status appears over the camera when you open the camera tile fullscreen."),
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                item { Pill(t("Fertig", "Done"), Modifier.padding(start = 12.dp, top = 10.dp)) { onClose() } }
            }
        }
    }
}
