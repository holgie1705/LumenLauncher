package com.lumen.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

const val PHONE_REMOTE_PORT = 8767

/** Tasten, die an Lumen selbst gehen sollen (wenn Lumen vorne ist und Android die Steuerkreuz-Aktionen nicht kennt). */
object RemoteBus {
    val keys = MutableSharedFlow<Int>(extraBufferCapacity = 16)
}

fun lanIpv4(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") && !it.name.startsWith("ppp") }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress && !it.hostAddress.orEmpty().startsWith("10.") }
        ?.hostAddress
        ?: NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
}.getOrNull()

fun phoneRemoteUrl(code: String) = "http://${lanIpv4() ?: "IP-der-Box"}:$PHONE_REMOTE_PORT/#$code"

/**
 * Kleiner Webserver für die Handy-Fernbedienung (läuft im Bedienungshilfe-Dienst, solange eingeschaltet).
 * Die Seite selbst ist frei abrufbar, jede Aktion braucht den Code vom Fernseher.
 */
class PhoneRemoteServer(private val code: () -> String, private val exec: (String, Map<String, String>) -> Any?) {
    private var sock: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(3)
    @Volatile private var wrong = 0
    @Volatile private var wrongSince = 0L

    val running get() = sock?.isClosed == false

    fun start(): Boolean = runCatching {
        if (running) return@runCatching true
        val s = ServerSocket(PHONE_REMOTE_PORT).also { sock = it }
        Thread {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { runCatching { handle(c) }; runCatching { c.close() } }
            }
        }.apply { isDaemon = true; name = "lumen-remote" }.start()
        true
    }.getOrDefault(false)

    fun stop() { runCatching { sock?.close() }; sock = null }

    private fun send(c: Socket, status: String, type: String, body: String) {
        val b = body.toByteArray(Charsets.UTF_8)
        c.getOutputStream().apply {
            write("HTTP/1.1 $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${b.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
            write(b); flush()
        }
    }

    private fun handle(c: Socket) {
        c.soTimeout = 8_000
        val inp = c.getInputStream().buffered()
        fun line(): String { val sb = StringBuilder(); while (sb.length < 8192) { val b = inp.read(); if (b < 0 || b == '\n'.code) break; if (b != '\r'.code) sb.append(b.toChar()) }; return sb.toString() }
        val req = line()
        while (line().isNotEmpty()) { /* Kopfzeilen überspringen */ }
        val path = req.split(' ').getOrNull(1) ?: "/"
        if (!path.startsWith("/api")) { send(c, "200 OK", "text/html", PAGE); return }
        val q = path.substringAfter('?', "").split('&').mapNotNull { p ->
            p.split('=', limit = 2).takeIf { it.size == 2 }?.let { URLDecoder.decode(it[0], "UTF-8") to URLDecoder.decode(it[1], "UTF-8") }
        }.toMap()
        val now = System.currentTimeMillis()
        if (now - wrongSince > 60_000) { wrong = 0; wrongSince = now }
        if (wrong > 15) { Thread.sleep(2_000); send(c, "429 Too Many Requests", "application/json", "{\"ok\":false}"); return }
        if (q["c"] != code() || code().isEmpty()) { wrong++; send(c, "403 Forbidden", "application/json", "{\"ok\":false,\"err\":\"code\"}"); return }
        val res = runCatching { exec(q["a"].orEmpty(), q) }.getOrElse { it.message }
        val json = when (res) {
            is JSONArray -> JSONObject().put("ok", true).put("list", res)
            is String -> JSONObject().put("ok", false).put("err", res)
            else -> JSONObject().put("ok", true)
        }
        send(c, "200 OK", "application/json", json.toString())
    }

    companion object {
        private val PAGE = """<!doctype html><html lang="de"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<meta name="theme-color" content="#0d1018"><title>Lumen Fernbedienung</title>
<style>
:root{--bg:#0d1018;--card:#171b26;--line:#2a3142;--txt:#eef1f7;--dim:#8a93a8;--acc:#4fe3f0;--yel:#ffd24a}
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;background:radial-gradient(circle at 50% 0,#1b2236,var(--bg) 60%);color:var(--txt);font-family:system-ui,-apple-system,sans-serif;min-height:100vh;user-select:none}
.wrap{max-width:440px;margin:0 auto;padding:18px 16px 40px}
h1{font-size:18px;margin:4px 0 16px;display:flex;justify-content:space-between;align-items:center}
h1 small{color:var(--dim);font-weight:400;font-size:12px}
.pad{position:relative;width:280px;height:280px;margin:10px auto 22px;border-radius:50%;background:linear-gradient(145deg,#1d2231,#121520);box-shadow:0 18px 40px #0008,inset 0 1px 0 #ffffff14}
.pad button{position:absolute;border:0;background:none;color:var(--txt);font-size:26px}
.pad .u{top:8px;left:95px;width:90px;height:80px}.pad .d{bottom:8px;left:95px;width:90px;height:80px}
.pad .l{left:8px;top:95px;width:80px;height:90px}.pad .r{right:8px;top:95px;width:80px;height:90px}
.pad .ok{left:85px;top:85px;width:110px;height:110px;border-radius:50%;background:linear-gradient(145deg,#2a3247,#1a1f2d);box-shadow:0 6px 16px #0007;font-size:20px;font-weight:700}
.pad button:active{color:var(--acc)}.pad .ok:active{background:var(--acc);color:#000}
.row{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-bottom:10px}
.b{border:1px solid var(--line);background:var(--card);color:var(--txt);border-radius:16px;padding:14px 4px;font-size:15px}
.b:active{background:var(--acc);color:#000}
.b span{display:block;font-size:11px;color:var(--dim);margin-top:3px}
.sec{margin:22px 0 8px;color:var(--dim);font-size:12px;letter-spacing:1px;text-transform:uppercase}
.txt{display:flex;gap:8px}.txt input{flex:1;min-width:0;padding:14px;border-radius:14px;border:1px solid var(--line);background:var(--card);color:var(--txt);font-size:16px}
.txt button{border:0;border-radius:14px;padding:0 16px;background:var(--acc);color:#000;font-weight:700}
.apps{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
.apps button{border:1px solid var(--line);background:var(--card);color:var(--txt);border-radius:12px;padding:12px 6px;font-size:13px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
#msg{position:fixed;left:50%;bottom:22px;transform:translateX(-50%);background:#000c;padding:10px 18px;border-radius:20px;font-size:14px;opacity:0;transition:.3s}
#login{position:fixed;inset:0;background:var(--bg);display:none;align-items:center;justify-content:center;flex-direction:column;gap:14px;padding:24px;text-align:center}
#login input{font-size:28px;letter-spacing:10px;text-align:center;width:200px;padding:12px;border-radius:14px;border:1px solid var(--line);background:var(--card);color:var(--txt)}
#login button{border:0;border-radius:22px;padding:12px 30px;background:var(--acc);font-size:16px;font-weight:700}
</style></head><body>
<div class="wrap">
<h1>Lumen <small id="st">verbunden</small></h1>
<div class="pad">
<button class="u" data-a="up">▲</button><button class="d" data-a="down">▼</button>
<button class="l" data-a="left">◀</button><button class="r" data-a="right">▶</button>
<button class="ok" data-a="ok">OK</button></div>
<div class="row"><button class="b" data-a="back">↩<span>Zurück</span></button><button class="b" data-a="home">⌂<span>Home</span></button><button class="b" data-a="recents">▤<span>Apps</span></button><button class="b" data-a="standby">⏻<span>Standby</span></button></div>
<div class="row"><button class="b" data-a="voldown">🔉<span>Leiser</span></button><button class="b" data-a="mute">🔇<span>Stumm</span></button><button class="b" data-a="volup">🔊<span>Lauter</span></button><button class="b" data-a="play">⏯<span>Play</span></button></div>
<div class="row"><button class="b" data-a="prev">⏮<span>Zurück</span></button><button class="b" data-a="rew">⏪<span>Spulen</span></button><button class="b" data-a="ffwd">⏩<span>Spulen</span></button><button class="b" data-a="next">⏭<span>Weiter</span></button></div>
<div class="sec">Text ins Eingabefeld am TV</div>
<div class="txt"><input id="t" placeholder="Text oder Passwort …" autocomplete="off" autocapitalize="off"><button id="send">Senden</button></div>
<div class="row" style="margin-top:10px;grid-template-columns:1fr 1fr"><button class="b" data-a="enter">↵<span>Eingabe bestätigen</span></button><button class="b" id="clr">⌫<span>Feld leeren</span></button></div>
<div class="sec">Apps starten</div><div class="apps" id="apps"></div>
</div>
<div id="login"><div style="font-size:20px;font-weight:700">Code vom Fernseher</div><div style="color:#8a93a8">Steht in Lumen unter Einstellungen → Fernbedienung</div><input id="code" inputmode="numeric" maxlength="6"><button id="go">Verbinden</button></div>
<div id="msg"></div>
<script>
let code=location.hash.slice(1)||localStorage.getItem('lumenCode')||'';
if(location.hash.length>1){localStorage.setItem('lumenCode',code);history.replaceState(null,'',location.pathname)}
const msg=t=>{const m=document.getElementById('msg');m.textContent=t;m.style.opacity=1;clearTimeout(m._t);m._t=setTimeout(()=>m.style.opacity=0,1600)};
function login(){document.getElementById('login').style.display='flex'}
async function api(a,p={}){
 const q=new URLSearchParams(Object.assign({c:code,a:a},p));
 try{const r=await fetch('/api?'+q);const j=await r.json();
  if(r.status==403){login();return null}
  document.getElementById('st').textContent='verbunden';
  if(!j.ok&&j.err)msg(j.err);return j}
 catch(e){document.getElementById('st').textContent='keine Verbindung';return null}}
function vib(){if(navigator.vibrate)navigator.vibrate(12)}
document.querySelectorAll('[data-a]').forEach(b=>{
 const a=b.dataset.a;const rep=['up','down','left','right','volup','voldown'].includes(a);let t1,t2;
 const stop=()=>{clearTimeout(t1);clearInterval(t2)};
 b.addEventListener('pointerdown',e=>{e.preventDefault();vib();api(a);if(rep){t1=setTimeout(()=>{t2=setInterval(()=>api(a),140)},420)}});
 ['pointerup','pointerleave','pointercancel'].forEach(ev=>b.addEventListener(ev,stop));
});
document.getElementById('send').onclick=async()=>{const v=document.getElementById('t').value;const j=await api('text',{t:v});if(j&&j.ok){msg('Gesendet');document.getElementById('t').value=''}};
document.getElementById('clr').onclick=()=>api('text',{t:''});
document.getElementById('go').onclick=()=>{code=document.getElementById('code').value.trim();localStorage.setItem('lumenCode',code);document.getElementById('login').style.display='none';loadApps()};
async function loadApps(){const j=await api('apps');if(!j||!j.list)return;const box=document.getElementById('apps');box.innerHTML='';
 j.list.forEach(x=>{const b=document.createElement('button');b.textContent=x.label;b.onclick=()=>{vib();api('app',{p:x.pkg});msg(x.label)};box.appendChild(b)})}
if(!code)login();else loadApps();
</script></body></html>"""
    }
}

/** QR-Code als Bitmap (ZXing). */
fun qrBitmap(text: String, px: Int = 360): Bitmap? = runCatching {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, px, px, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
    for (y in 0 until m.height) for (x in 0 until m.width) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    bmp
}.getOrNull()

/** Anzeige in den Einstellungen: QR-Code, Adresse und Code. */
@Composable
fun PhoneRemoteInfo(code: String, running: Boolean) {
    val url = remember(code) { phoneRemoteUrl(code) }
    val qr = remember(url) { qrBitmap(url)?.asImageBitmap() }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.05f)).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (qr != null) Image(qr, null, Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Mit dem Handy scannen (gleiches WLAN)", "Scan with your phone (same Wi-Fi)"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(url.substringBefore('#'), fontSize = 15.sp, color = NeoCyan)
            Text("Code: $code", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NeoYellow)
            Text(
                if (running) t("Steuerkreuz, Lautstärke, Text eintippen und Apps starten – vom Handy aus.", "D-pad, volume, typing text and launching apps – from your phone.")
                else t("Startet, sobald die Bedienungshilfe „Lumen Home-Taste“ aktiv ist.", "Starts once the “Lumen Home-Taste” accessibility service is on."),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f),
            )
        }
    }
}
