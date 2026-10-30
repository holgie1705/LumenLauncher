package com.lumen.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Ein Radiosender (Daten stammen aus dem freien Verzeichnis radio-browser.info). */
class Station(val uuid: String, val name: String, val url: String, val favicon: String = "", val info: String = "", val home: String = "")

/** Ein gehörter Titel im Verlauf. */
class TrackLog(val station: String, val title: String)

/** Globaler Zustand des Radios – die UI liest nur diese Flows, abgespielt wird im RadioService. */
object Radio {
    val now = MutableStateFlow<Station?>(null)
    val playing = MutableStateFlow(false)
    val buffering = MutableStateFlow(false)
    val track = MutableStateFlow("")
    val error = MutableStateFlow("")
    /** Zuletzt gehörte Titel (neueste zuerst), nur im Speicher. */
    val history = MutableStateFlow<List<TrackLog>>(emptyList())
    /** Zeitpunkt, zu dem der Einschlaf-Timer ausschaltet (0 = aus). */
    val sleepUntil = MutableStateFlow(0L)

    fun play(ctx: Context, s: Station) {
        Podcasts.stop(ctx)   // nur eine Audioquelle gleichzeitig
        error.value = ""; track.value = ""
        now.value = s; playing.value = false; buffering.value = true
        val i = Intent(ctx, RadioService::class.java).putExtra("url", s.url).putExtra("name", s.name)
        if (runCatching { ctx.startForegroundService(i) }.isFailure) { now.value = null; buffering.value = false }
        RadioApi.countClick(s.uuid)
    }

    fun stop(ctx: Context) {
        runCatching { ctx.stopService(Intent(ctx, RadioService::class.java)) }
        reset()
    }

    fun toggle(ctx: Context, s: Station) { if (now.value?.uuid == s.uuid) stop(ctx) else play(ctx, s) }

    /** Einschlaf-Timer: in min Minuten sanft ausblenden und stoppen. 0 = aus. */
    fun setSleep(ctx: Context, min: Int) {
        if (now.value == null) return
        sleepUntil.value = if (min <= 0) 0L else System.currentTimeMillis() + min * 60_000L
        runCatching { ctx.startService(Intent(ctx, RadioService::class.java).setAction("sleep").putExtra("min", min)) }
    }

    internal fun logTrack(title: String) {
        if (title.isBlank()) return
        val cur = history.value
        if (cur.firstOrNull()?.title == title) return
        history.value = (listOf(TrackLog(now.value?.name.orEmpty(), title)) + cur).take(40)
    }

    internal fun reset() { now.value = null; playing.value = false; buffering.value = false; track.value = ""; sleepUntil.value = 0L }
}

/** Spielt den Stream in einem Vordergrunddienst – die Musik läuft weiter, wenn man eine andere App öffnet. */
class RadioService : Service() {
    private var player: ExoPlayer? = null
    private val sleepHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var fadeStep: Runnable? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { Radio.playing.value = isPlaying }
        override fun onPlaybackStateChanged(state: Int) { Radio.buffering.value = state == Player.STATE_BUFFERING }
        override fun onMediaMetadataChanged(m: MediaMetadata) {
            val tt = m.title?.toString().orEmpty()
            Radio.track.value = tt
            Radio.logTrack(tt)
        }
        override fun onPlayerError(error: PlaybackException) {
            Radio.error.value = t("Dieser Sender lässt sich gerade nicht abspielen.", "This station cannot be played right now.")
            Radio.reset()
            stopSelf()
        }
    }

    private fun notification(name: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("radio", "Radio", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RadioService::class.java).setAction("stop"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, "radio")
            .setContentTitle(name)
            .setContentText("Lumen Radio")
            .setSmallIcon(R.drawable.ic_lumen)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, t("Stopp", "Stop"), stop).build())
            .build()
    }

    private fun foreground(name: String) {
        val n = notification(name)
        if (Build.VERSION.SDK_INT >= 29) startForeground(1002, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(1002, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { Radio.reset(); stopSelf(); return START_NOT_STICKY }
        if (intent?.action == "sleep") {
            scheduleSleep(intent.getIntExtra("min", 0))
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra("url")
        val name = intent?.getStringExtra("name") ?: "Radio"
        foreground(name)
        if (url == null) { stopSelf(); return START_NOT_STICKY }
        val p = player ?: ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build().also { it.addListener(listener); player = it }
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
        return START_NOT_STICKY
    }

    /** Einschlaf-Timer: kurz vor Ablauf die Lautstärke über ~8 s herunterfahren, dann stoppen. */
    private fun scheduleSleep(min: Int) {
        fadeStep?.let { sleepHandler.removeCallbacks(it) }; fadeStep = null
        player?.volume = 1f
        if (min <= 0) return
        val fadeMs = 8_000L
        val startIn = (min * 60_000L - fadeMs).coerceAtLeast(0L)
        sleepHandler.postDelayed({
            val p = player ?: return@postDelayed
            var v = 1f
            val step = object : Runnable {
                override fun run() {
                    v -= 0.05f
                    if (v <= 0f || player == null) { Radio.reset(); stopSelf() }
                    else { p.volume = v; sleepHandler.postDelayed(this, (fadeMs / 20)) }
                }
            }
            fadeStep = step
            sleepHandler.post(step)
        }, startIn)
    }

    override fun onDestroy() {
        fadeStep?.let { sleepHandler.removeCallbacks(it) }
        player?.release()
        player = null
        Radio.reset()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** radio-browser.info: freie, gemeinschaftlich gepflegte Senderliste (kein Konto, kein Schlüssel). */
object RadioApi {
    private val HOSTS = listOf("de1", "de2", "nl1", "at1").map { "https://$it.api.radio-browser.info" }

    private fun get(path: String): String? {
        for (h in HOSTS) {
            val r = runCatching {
                val c = (URL(h + path).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7_000; readTimeout = 12_000
                    setRequestProperty("User-Agent", WallSources.userAgent())
                }
                try { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
            }.getOrNull()
            if (r != null) return r
        }
        return null
    }

    private const val BASE = "&hidebroken=true&order=clickcount&reverse=true&limit=40"

    private fun parse(txt: String?): List<Station> = runCatching {
        val arr = JSONArray(txt ?: return@runCatching emptyList<Station>())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
            if (url.isEmpty()) return@mapNotNull null
            val info = listOf(o.optString("countrycode"), o.optString("codec"), o.optInt("bitrate").takeIf { it > 0 }?.let { "$it kbit/s" })
                .filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")
            Station(o.optString("stationuuid", url), o.optString("name").trim().ifEmpty { "Radio" }, url, o.optString("favicon"), info, o.optString("homepage"))
        }.distinctBy { it.uuid }
    }.getOrDefault(emptyList())

    /** Leere Suche = beliebteste deutsche Sender. */
    suspend fun search(q: String): List<Station> = withContext(Dispatchers.IO) {
        val path = if (q.isBlank()) "/json/stations/search?countrycode=DE$BASE"
        else "/json/stations/search?name=${URLEncoder.encode(q.trim(), "UTF-8")}$BASE"
        parse(get(path))
    }

    /** Sender nach Genre (tag) und/oder Land (ISO-Code, z. B. DE). Beides leer = beliebt. */
    suspend fun browse(tag: String, country: String): List<Station> = withContext(Dispatchers.IO) {
        val parts = buildList {
            if (country.isNotBlank()) add("countrycode=$country")
            if (tag.isNotBlank()) add("tagList=${URLEncoder.encode(tag, "UTF-8")}&tagExact=false")
        }
        parse(get("/json/stations/search?${parts.joinToString("&")}$BASE"))
    }

    /** Höflich zurückmelden, dass der Sender gehört wird (hilft der Beliebtheitsliste). */
    fun countClick(uuid: String) {
        Thread { runCatching { get("/json/url/$uuid") } }.start()
    }
}
