package com.lumen.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Einstellungen von einer Lumen-Box direkt im WLAN auf eine andere übertragen.
 * Ziel-Box: „Einstellungen empfangen" öffnen (lauscht nur, solange der Dialog offen ist, und fragt vor dem Übernehmen nach).
 * Quell-Box: „Einstellungen senden" findet die Ziel-Box per mDNS (oder per IP) und schickt die Sicherung.
 * PIN und Kindersicherung sind nicht Teil der Sicherung; gerätespezifische Werte behält die Ziel-Box.
 */
object Transfer {
    const val PORT = 8768
    private const val TYPE = "_lumen._tcp."
    private const val MAX_BODY = 1_000_000

    /** Diese Werte gehören zur Box selbst und werden beim Empfangen nicht überschrieben. */
    private val LOCAL_KEYS = listOf(
        "bambuIp", "bambuSn", "bambuCode", "bambuHttp", "bambuName", "bambuNotify",
        "adbHost", "adbPort", "phoneRemote", "remoteCode",
        "dotOn", "dotSize", "dotInt", "dotDur", "dotPos", "dotAuto",
    )
    private val WEATHER_KEYS = listOf("wName", "wLat", "wLon")

    class Peer(val name: String, val ip: String)

    class Incoming(val from: String, val json: String, val weather: String, val reply: CompletableDeferred<Boolean>)

    fun deviceName(ctx: Context): String =
        runCatching { Settings.Global.getString(ctx.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Lumen"

    /** Eingehende Sicherung mit den gerätespezifischen Werten der Ziel-Box zusammenführen. */
    fun merge(current: JSONObject, incoming: String, takeWeather: Boolean): String? = runCatching {
        val n = JSONObject(incoming)
        (LOCAL_KEYS + if (takeWeather) emptyList() else WEATHER_KEYS).forEach { k ->
            if (current.has(k)) n.put(k, current.get(k)) else n.remove(k)
        }
        n.put("setupDone", true); n.put("langPicked", true)
        n.toString()
    }.getOrNull()

    // ------------------------------------------------------------------ Empfangen (Ziel-Box)

    class Receiver(private val ctx: Context) {
        val incoming = MutableStateFlow<Incoming?>(null)
        @Volatile private var sock: ServerSocket? = null
        private var reg: NsdManager.RegistrationListener? = null
        private val nsd get() = runCatching { ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()

        fun start(): Boolean {
            val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)) } }.getOrNull() ?: return false
            sock = s
            Thread {
                while (!s.isClosed) {
                    val c = runCatching { s.accept() }.getOrNull() ?: break
                    Thread { runCatching { handle(c) }; runCatching { c.close() } }.apply { isDaemon = true }.start()
                }
            }.apply { isDaemon = true; name = "lumen-transfer" }.start()
            runCatching {
                val info = NsdServiceInfo().apply { serviceName = deviceName(ctx); serviceType = "_lumen._tcp"; port = PORT }
                val l = object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(i: NsdServiceInfo?) {}
                    override fun onRegistrationFailed(i: NsdServiceInfo?, e: Int) {}
                    override fun onServiceUnregistered(i: NsdServiceInfo?) {}
                    override fun onUnregistrationFailed(i: NsdServiceInfo?, e: Int) {}
                }
                nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, l); reg = l
            }
            return true
        }

        fun stop() {
            runCatching { reg?.let { nsd?.unregisterService(it) } }; reg = null
            runCatching { sock?.close() }; sock = null
            incoming.value?.reply?.complete(false); incoming.value = null
        }

        private fun send(c: Socket, status: String, body: String) {
            val b = body.toByteArray(Charsets.UTF_8)
            c.getOutputStream().apply {
                write("HTTP/1.1 $status\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray())
                write(b); flush()
            }
        }

        private fun handle(c: Socket) {
            c.soTimeout = 15_000
            val inp = c.getInputStream().buffered()
            fun line(): String { val sb = StringBuilder(); while (sb.length < 8192) { val b = inp.read(); if (b < 0 || b == '\n'.code) break; if (b != '\r'.code) sb.append(b.toChar()) }; return sb.toString() }
            val req = line().split(' ')
            val headers = HashMap<String, String>()
            while (true) { val h = line(); if (h.isEmpty()) break; h.split(':', limit = 2).takeIf { it.size == 2 }?.let { headers[it[0].trim().lowercase()] = it[1].trim() } }
            val method = req.getOrNull(0).orEmpty(); val path = req.getOrNull(1).orEmpty()
            if (method == "GET" && path.startsWith("/ping")) {
                send(c, "200 OK", JSONObject().put("ok", true).put("name", deviceName(ctx)).put("v", APP_VERSION).toString()); return
            }
            if (method != "POST" || !path.startsWith("/transfer")) { send(c, "404 Not Found", "{\"ok\":false}"); return }
            val len = headers["content-length"]?.toIntOrNull() ?: -1
            if (len !in 1..MAX_BODY) { send(c, "413 Payload Too Large", "{\"ok\":false,\"err\":\"size\"}"); return }
            val buf = ByteArray(len); var got = 0
            while (got < len) { val r = inp.read(buf, got, len - got); if (r < 0) break; got += r }
            val json = String(buf, 0, got, Charsets.UTF_8)
            val weather = runCatching { JSONObject(json).optString("wName") }.getOrNull()
                ?: run { send(c, "400 Bad Request", "{\"ok\":false,\"err\":\"json\"}"); return }
            if (incoming.value != null) { send(c, "409 Conflict", "{\"ok\":false,\"err\":\"busy\"}"); return }
            val from = runCatching { URLDecoder.decode(headers["x-lumen-from"].orEmpty(), "UTF-8") }.getOrDefault("").ifBlank { c.inetAddress.hostAddress.orEmpty() }
            val d = CompletableDeferred<Boolean>()
            incoming.value = Incoming(from.take(40), json, weather, d)
            val ok = runBlocking { withTimeoutOrNull(120_000) { d.await() } } == true
            if (incoming.value?.reply === d) incoming.value = null
            send(c, "200 OK", JSONObject().put("ok", ok).apply { if (!ok) put("err", "declined") }.toString())
        }
    }

    // ------------------------------------------------------------------ Senden (Quell-Box)

    @Suppress("DEPRECATION")
    suspend fun discover(ctx: Context, totalMs: Long = 4000): List<Peer> = withContext(Dispatchers.IO) {
        val nsd = runCatching { ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull() ?: return@withContext emptyList()
        val found = ConcurrentLinkedQueue<NsdServiceInfo>()
        val l = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(s: String?, e: Int) {}
            override fun onStopDiscoveryFailed(s: String?, e: Int) {}
            override fun onDiscoveryStarted(s: String?) {}
            override fun onDiscoveryStopped(s: String?) {}
            override fun onServiceFound(info: NsdServiceInfo) { found.add(info) }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }
        if (runCatching { nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.isFailure) return@withContext emptyList()
        delay(totalMs)
        runCatching { nsd.stopServiceDiscovery(l) }
        val own = lanIpv4()
        val out = LinkedHashMap<String, Peer>()
        for (info in found) {
            val d = CompletableDeferred<Peer?>()
            val rl = object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo?, e: Int) { d.complete(null) }
                override fun onServiceResolved(s: NsdServiceInfo) {
                    val ip = (s.host as? Inet4Address)?.hostAddress
                    d.complete(ip?.let { Peer(s.serviceName.orEmpty().ifBlank { it }, it) })
                }
            }
            if (runCatching { nsd.resolveService(info, rl) }.isFailure) continue
            val p = withTimeoutOrNull(1500) { d.await() } ?: continue
            if (p.ip != own) out[p.ip] = p
        }
        out.values.toList()
    }

    /** null = übernommen, sonst Fehlertext. */
    suspend fun send(ctx: Context, ip: String, json: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val b = json.toByteArray(Charsets.UTF_8)
            val c = URL("http://$ip:$PORT/transfer").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 6_000; c.readTimeout = 130_000
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("X-Lumen-From", URLEncoder.encode(deviceName(ctx), "UTF-8"))
            c.setFixedLengthStreamingMode(b.size)
            c.outputStream.use { it.write(b) }
            val code = c.responseCode
            val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
            val o = runCatching { JSONObject(txt) }.getOrNull()
            when {
                o?.optBoolean("ok") == true -> null
                o?.optString("err") == "declined" -> t("Auf der Ziel-Box abgelehnt.", "Declined on the target box.")
                o?.optString("err") == "busy" -> t("Die Ziel-Box ist gerade beschäftigt.", "The target box is busy.")
                else -> t("Fehler", "Error") + " ($code)"
            }
        }.getOrElse { t("Keine Verbindung. Ist auf der Ziel-Box „Einstellungen empfangen“ geöffnet?", "No connection. Is “Receive settings” open on the target box?") }
    }
}

// ====================================================================== Dialoge

@Composable
fun ReceiveSettingsDialog(vm: HomeViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val receiver = remember { Transfer.Receiver(ctx.applicationContext) }
    var started by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf("") }
    val inc by receiver.incoming.collectAsState()
    val prefs by vm.prefs.collectAsState()
    var takeWeather by remember { mutableStateOf(false) }
    val closeFocus = remember { FocusRequester() }
    val okFocus = remember { FocusRequester() }
    val name = remember { Transfer.deviceName(ctx) }
    val ip = remember { lanIpv4() ?: "?" }

    DisposableEffect(Unit) { started = receiver.start(); onDispose { receiver.stop() } }
    LaunchedEffect(inc) {
        if (inc != null) takeWeather = prefs.weatherName.isBlank() && inc!!.weather.isNotBlank()
        delay(120); runCatching { if (inc != null) okFocus.requestFocus() else closeFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.also { spatialBlur(it) }?.setDimAmount(0f)
        NeoSheet(Ico.DOWNLOAD, t("Einstellungen empfangen", "Receive settings"), t("Diese Box", "This box") + ": $name · $ip", width = 720.dp, solid = true) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val i = inc
                when {
                    !started -> item { Body(t("Empfang konnte nicht gestartet werden (Port ${Transfer.PORT} belegt?). Dialog schließen und erneut öffnen.", "Could not start receiving (port ${Transfer.PORT} in use?). Close and reopen the dialog.")) }
                    i != null -> {
                        item { Title(t("Einstellungen von „${i.from}“ übernehmen?", "Apply settings from “${i.from}”?")) }
                        item { Body(t("Layout, Reihen, Favoriten, Widgets, Radio, Podcasts, Farben und Schrift werden ersetzt. PIN, Kindersicherung, VPN-Dot, Drucker und Fernbedienungs-Code dieser Box bleiben unverändert.",
                            "Layout, rows, favorites, widgets, radio, podcasts, colors and font will be replaced. This box keeps its PIN, parental controls, VPN dot, printer and remote code.")) }
                        if (i.weather.isNotBlank()) item {
                            SettingLine(t("Wetter-Ort übernehmen", "Take weather place") + " (${i.weather})", if (takeWeather) t("An", "On") else t("Aus", "Off")) { takeWeather = !takeWeather }
                        }
                        item {
                            Row(Modifier.padding(start = 12.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Pill(t("Übernehmen", "Apply"), Modifier.focusRequester(okFocus), selected = true) {
                                    val ok = vm.applyTransfer(i.json, takeWeather)
                                    done = if (ok) t("✓ Einstellungen von „${i.from}“ übernommen.", "✓ Settings from “${i.from}” applied.") else t("Die Sicherung war ungültig.", "The backup was invalid.")
                                    i.reply.complete(ok)
                                }
                                Pill(t("Ablehnen", "Decline")) { done = ""; i.reply.complete(false) }
                            }
                        }
                    }
                    done.isNotEmpty() -> item { Title(done) }
                    else -> {
                        item { Title(t("Bereit – warte auf eine andere Lumen-Box …", "Ready – waiting for another Lumen box …")) }
                        item { Body(t("Auf der anderen Box: Einstellungen → System → „Einstellungen senden“ und dort „$name“ wählen. Wird diese Box nicht gefunden, dort die IP $ip eingeben.",
                            "On the other box: Settings → System → “Send settings” and pick “$name”. If this box isn't found, enter the IP $ip there.")) }
                    }
                }
            }
            Pill(t("Schließen", "Close"), Modifier.focusRequester(closeFocus)) { onClose() }
        }
    }
}

@Composable
fun SendSettingsDialog(vm: HomeViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var peers by remember { mutableStateOf<List<Transfer.Peer>?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var ipText by remember { mutableStateOf("") }
    var scan by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    val field = remember { FocusRequester() }

    LaunchedEffect(scan) {
        peers = null
        peers = Transfer.discover(ctx)
        delay(80); runCatching { first.requestFocus() }
    }
    LaunchedEffect(manual) { if (manual) { delay(120); runCatching { field.requestFocus() } } }

    fun sendTo(ip: String, label: String) {
        if (busy) return
        busy = true
        status = t("Gesendet an „$label“ – bitte dort mit OK bestätigen …", "Sent to “$label” – please confirm there with OK …")
        scope.launch {
            val err = Transfer.send(ctx, ip, vm.transferPayload())
            status = err ?: t("✓ „$label“ hat die Einstellungen übernommen.", "✓ “$label” applied the settings.")
            busy = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.also { spatialBlur(it) }?.setDimAmount(0f)
        NeoSheet(Ico.WIFI, t("Einstellungen senden", "Send settings"), t("An eine andere Lumen-Box im WLAN", "To another Lumen box on your Wi-Fi"), width = 720.dp, solid = true) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Body(t("Auf der Ziel-Box zuerst Einstellungen → System → „Einstellungen empfangen“ öffnen.", "First open Settings → System → “Receive settings” on the target box.")) }
                item { SheetSection(t("Gefundene Boxen", "Boxes found")) }
                val p = peers
                when {
                    p == null -> item { Body(t("Suche …", "Searching …")) }
                    p.isEmpty() -> item { Body(t("Keine Box gefunden. „Erneut suchen“ oder die IP eingeben.", "No box found. “Search again” or enter the IP.")) }
                    else -> items(p, key = { it.ip }) { peer ->
                        SettingLine(peer.name, peer.ip, if (peer == p.first()) Modifier.focusRequester(first) else Modifier) { sendTo(peer.ip, peer.name) }
                    }
                }
                item { SettingLine(t("Erneut suchen", "Search again"), "", if (p != null && p.isEmpty()) Modifier.focusRequester(first) else Modifier) { scan++ } }
                item { SettingLine(t("IP-Adresse eingeben", "Enter IP address"), ipText.ifEmpty { "" }) { manual = !manual } }
                if (manual) item {
                    BasicTextField(
                        value = ipText, onValueChange = { v -> ipText = v.filter { it.isDigit() || it == '.' }.take(15) }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 19.sp), cursorBrush = SolidColor(NeoCyan),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (ipText.count { it == '.' } == 3) { manual = false; sendTo(ipText, ipText) } }),
                        decorationBox = { inner -> if (ipText.isEmpty()) Text("192.168.0.…", fontSize = 19.sp, color = Color.White.copy(alpha = 0.35f)); inner() },
                        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth()
                            .background(NeoGlass, RoundedCornerShape(16.dp)).border(2.dp, NeoYellow, RoundedCornerShape(16.dp))
                            .padding(16.dp).focusRequester(field),
                    )
                }
                if (status.isNotEmpty()) item { Title(status) }
                item {
                    Body(t("Übertragen werden Layout, Reihen, Favoriten, Widgets, Radio, Podcasts, Farben und Schrift. Nicht übertragen: PIN, Kindersicherung, VPN-Dot, Drucker, Fernbedienungs-Code. Wallpaper-Dateien bleiben auf der jeweiligen Box.",
                        "Transferred: layout, rows, favorites, widgets, radio, podcasts, colors and font. Not transferred: PIN, parental controls, VPN dot, printer, remote code. Wallpaper files stay on each box."))
                }
            }
            Pill(t("Schließen", "Close")) { onClose() }
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))

@Composable
private fun Body(text: String) =
    Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
