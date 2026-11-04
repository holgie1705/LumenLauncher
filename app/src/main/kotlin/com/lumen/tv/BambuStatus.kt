package com.lumen.tv

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Eine Spule (AMS-Slot oder externer Außenextruder): Id + Material + Farbe + Restmenge % + Gewicht g. */
class BambuTray(val id: String, val type: String, val colorArgb: Long, val remain: Int, val weightG: Int = 0, val external: Boolean = false)

/** Momentaufnahme des Druckerstatus. Felder sind leer/–1, solange nichts bekannt ist. */
data class BambuState(
    val connected: Boolean = false,
    val nozzle: Double = -1.0, val nozzleTarget: Double = -1.0,
    val bed: Double = -1.0, val bedTarget: Double = -1.0,
    val chamber: Double = -1.0,
    val progress: Int = -1, val remainMin: Int = -1,
    val stage: String = "", val job: String = "",
    val layer: Int = -1, val totalLayer: Int = -1,
    val speed: Int = -1, val fan: Int = -1,
    val wifi: String = "", val amsHumidity: Int = -1,
    val printError: Int = 0, val hmsCount: Int = -1,
    val activeTrayId: String = "",   // gerade verwendeter AMS-Slot (ams.tray_now)
    val nozzleType: String = "", val nozzleDia: String = "",
    val light: Int = -1,   // Kammerlicht: 1 an, 0 aus, -1 unbekannt
    val trays: List<BambuTray> = emptyList(),
    val error: String = "",
)

/**
 * Liest den lokalen Druckerstatus eines Bambu-Druckers (X1/X2D/H2-Klasse) per MQTT (Port 8883, TLS,
 * selbstsigniert). Nutzer „bblp", Passwort = Access Code, Topic device/<SN>/report. Ganz ohne Cloud.
 * Reports kommen als Teil-Updates, daher werden bekannte Werte gemerkt und zusammengeführt.
 */
class BambuClient(
    private val ip: String,
    private val sn: String,
    private val code: String,
    private val onState: (BambuState) -> Unit,
) {
    @Volatile private var client: MqttClient? = null
    @Volatile private var stopped = false
    @Volatile private var ok = false
    private var state = BambuState()

    fun start() {
        stopped = false; ok = false
        // Watchdog: hängt die TLS-/MQTT-Aushandlung, nach 12 s klar als Fehler melden (statt ewig „verbinde …").
        Thread {
            Thread.sleep(12_000)
            if (!ok && !stopped) {
                push(state.copy(connected = false, error = t("Keine Antwort (TLS?) – prüfe Code/Modus", "No response (TLS?) – check code/mode")))
                runCatching { client?.disconnectForcibly(200) }
            }
        }.start()
        Thread {
            runCatching {
                val c = MqttClient("ssl://$ip:8883", "lumen-" + System.currentTimeMillis(), MemoryPersistence())
                client = c
                val opts = MqttConnectOptions().apply {
                    userName = "bblp"; password = code.toCharArray()
                    isHttpsHostnameVerificationEnabled = false
                    socketFactory = trustAllFactory()
                    connectionTimeout = 10; keepAliveInterval = 30; isAutomaticReconnect = true; isCleanSession = true
                }
                c.setCallback(object : org.eclipse.paho.client.mqttv3.MqttCallbackExtended {
                    override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                        ok = true
                        android.util.Log.i("BambuMqtt", "connected (reconnect=$reconnect)")
                        runCatching { c.subscribe("device/$sn/report", 0) }
                        // Vollständigen Status anfordern (sonst kommen nur Änderungen).
                        runCatching { c.publish("device/$sn/request", "{\"pushing\":{\"sequence_id\":\"0\",\"command\":\"pushall\"}}".toByteArray(), 0, false) }
                        push(state.copy(connected = true, error = ""))
                    }
                    override fun connectionLost(cause: Throwable?) {
                        android.util.Log.w("BambuMqtt", "connection lost", cause)
                        push(state.copy(connected = false, error = cause?.message ?: ""))
                    }
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        val txt = message?.payload?.toString(Charsets.UTF_8) ?: return
                        runCatching { merge(JSONObject(txt)) }
                    }
                    override fun deliveryComplete(token: org.eclipse.paho.client.mqttv3.IMqttDeliveryToken?) {}
                })
                c.connect(opts)
            }.onFailure { e ->
                android.util.Log.w("BambuMqtt", "connect failed", e)
                val rc = (e as? org.eclipse.paho.client.mqttv3.MqttException)?.reasonCode ?: 0
                val msg = when {
                    e.message?.contains("not authorized", true) == true || rc == 5 || rc == 4 -> t("Zugangscode falsch?", "Wrong access code?")
                    e.message?.contains("timeout", true) == true || rc == 32000 -> t("Timeout – erreichbar?", "Timeout – reachable?")
                    else -> e.message ?: "Fehler ($rc)"
                }
                if (!stopped) push(state.copy(connected = false, error = msg))
            }
        }.start()
    }

    fun stop() {
        stopped = true
        val c = client; client = null
        Thread { runCatching { c?.disconnectForcibly(500) }; runCatching { c?.close() } }.start()
    }

    private fun push(s: BambuState) { state = s; if (!stopped) onState(s) }

    /** Teil-Report in den bekannten Stand einpflegen. */
    private fun merge(o: JSONObject) {
        val p = o.optJSONObject("print") ?: return
        push(bambuMergePrint(state, p))
    }

    private fun trustAllFactory(): javax.net.ssl.SSLSocketFactory {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        // Bambus eingebetteter Broker verträgt TLS 1.3 oft nicht – TLS 1.2 erzwingen.
        return SSLContext.getInstance("TLSv1.2").apply { init(null, arrayOf(tm), SecureRandom()) }.socketFactory
    }
}

/** Parst einen Bambu-„print"-Report (Voll- oder Teil-Update) in den Status. Gemeinsam für MQTT und HTTP-Bridge. */
fun bambuMergePrint(prev: BambuState, p: JSONObject): BambuState {
    var s = prev.copy(connected = true)
    if (p.has("nozzle_temper")) s = s.copy(nozzle = p.optDouble("nozzle_temper", s.nozzle))
    if (p.has("nozzle_target_temper")) s = s.copy(nozzleTarget = p.optDouble("nozzle_target_temper", s.nozzleTarget))
    if (p.has("bed_temper")) s = s.copy(bed = p.optDouble("bed_temper", s.bed))
    if (p.has("bed_target_temper")) s = s.copy(bedTarget = p.optDouble("bed_target_temper", s.bedTarget))
    if (p.has("chamber_temper")) s = s.copy(chamber = p.optDouble("chamber_temper", s.chamber))
    else p.optJSONObject("ctc")?.optJSONObject("info")?.let { if (it.has("temp")) s = s.copy(chamber = it.optDouble("temp", s.chamber)) }
    if (p.has("mc_percent")) s = s.copy(progress = p.optInt("mc_percent", s.progress))
    if (p.has("mc_remaining_time")) s = s.copy(remainMin = p.optInt("mc_remaining_time", s.remainMin))
    if (p.has("gcode_state")) s = s.copy(stage = p.optString("gcode_state", s.stage))
    if (p.has("subtask_name")) s = s.copy(job = p.optString("subtask_name", s.job))
    if (p.has("layer_num")) s = s.copy(layer = p.optInt("layer_num", s.layer))
    if (p.has("total_layer_num")) s = s.copy(totalLayer = p.optInt("total_layer_num", s.totalLayer))
    if (p.has("spd_mag")) s = s.copy(speed = p.optInt("spd_mag", s.speed))
    if (p.has("cooling_fan_speed")) {
        val v = p.optString("cooling_fan_speed", "").toIntOrNull()
        if (v != null) s = s.copy(fan = Math.round(v / 15f * 100f))   // Bambu meldet 0–15 -> %
    }
    if (p.has("wifi_signal")) s = s.copy(wifi = p.optString("wifi_signal", s.wifi))
    if (p.has("print_error")) s = s.copy(printError = p.optInt("print_error", 0))
    p.optJSONArray("hms")?.let { s = s.copy(hmsCount = it.length()) }
    if (p.has("nozzle_type")) s = s.copy(nozzleType = p.optString("nozzle_type", s.nozzleType))
    if (p.has("nozzle_diameter")) s = s.copy(nozzleDia = p.optString("nozzle_diameter", s.nozzleDia))
    p.optJSONArray("lights_report")?.let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("node") == "chamber_light") s = s.copy(light = if (o.optString("mode") == "on") 1 else 0)
        }
    }
    p.optJSONObject("ams")?.optJSONArray("ams")?.let { units ->
        units.optJSONObject(0)?.let { u ->
            (u.optString("humidity_raw", "").toIntOrNull())?.let { s = s.copy(amsHumidity = it) }
        }
    }
    run {
        val amsRoot = p.optJSONObject("ams")
        val now = amsRoot?.optString("tray_now", s.activeTrayId) ?: s.activeTrayId
        if (now.isNotEmpty()) s = s.copy(activeTrayId = now)
        val out = mutableListOf<BambuTray>()
        fun mk(tr: JSONObject, external: Boolean) {
            val id = tr.optString("id", "")
            val type = tr.optString("tray_type", ""); if (type.isEmpty()) return
            val remain = tr.optString("remain", "-1").toIntOrNull() ?: tr.optInt("remain", -1)
            val wg = tr.optString("tray_weight", "0").toIntOrNull() ?: 0
            out += BambuTray(id, type, bambuParseColor(tr.optString("tray_color", "")), remain, wg, external)
        }
        // Alle physisch geladenen AMS-Slots zeigen (tray_exist_bits) – auch ohne RFID/Restmenge.
        amsRoot?.let { root ->
            val exist = root.optString("tray_exist_bits", "").toLongOrNull(16) ?: -1L
            root.optJSONArray("ams")?.let { units ->
                for (u in 0 until units.length()) {
                    units.optJSONObject(u)?.optJSONArray("tray")?.let { trays ->
                        for (j in 0 until trays.length()) {
                            val tr = trays.optJSONObject(j) ?: continue
                            if (tr.optString("tray_type", "").isEmpty()) continue
                            val gidx = u * 4 + (tr.optString("id", "").toIntOrNull() ?: j)
                            val present = exist < 0 || ((exist ushr gidx) and 1L) == 1L
                            if (present || tr.optString("id", "") == now) mk(tr, false)
                        }
                    }
                }
            }
        }
        // Externer Außenextruder (geladen = Material gesetzt).
        p.optJSONObject("vt_tray")?.let { if (it.optString("tray_type", "").isNotEmpty()) mk(it, true) }
        p.optJSONArray("vir_slot")?.let { arr -> for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { if (it.optString("tray_type", "").isNotEmpty()) mk(it, true) } }
        if (out.isNotEmpty()) s = s.copy(trays = out)
    }
    return s
}

/** Bambu-Farbe (RRGGBBAA) -> ARGB. Leer -> grau. */
fun bambuParseColor(hex: String): Long {
    val h = hex.trim().removePrefix("#")
    if (h.length < 6) return 0xFF888888L
    return runCatching {
        val r = h.substring(0, 2).toLong(16); val g = h.substring(2, 4).toLong(16); val b = h.substring(4, 6).toLong(16)
        (0xFFL shl 24) or (r shl 16) or (g shl 8) or b
    }.getOrDefault(0xFF888888L)
}
