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

/** Globaler Zustand des Radios – die UI liest nur diese Flows, abgespielt wird im RadioService. */
object Radio {
    val now = MutableStateFlow<Station?>(null)
    val playing = MutableStateFlow(false)
    val buffering = MutableStateFlow(false)
    val track = MutableStateFlow("")
    val error = MutableStateFlow("")

    fun play(ctx: Context, s: Station) {
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

    internal fun reset() { now.value = null; playing.value = false; buffering.value = false; track.value = "" }
}

/** Spielt den Stream in einem Vordergrunddienst – die Musik läuft weiter, wenn man eine andere App öffnet. */
class RadioService : Service() {
    private var player: ExoPlayer? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { Radio.playing.value = isPlaying }
        override fun onPlaybackStateChanged(state: Int) { Radio.buffering.value = state == Player.STATE_BUFFERING }
        override fun onMediaMetadataChanged(m: MediaMetadata) { Radio.track.value = m.title?.toString().orEmpty() }
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

    override fun onDestroy() {
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

    /** Leere Suche = beliebteste deutsche Sender. */
    suspend fun search(q: String): List<Station> = withContext(Dispatchers.IO) {
        val base = "&hidebroken=true&order=clickcount&reverse=true&limit=40"
        val path = if (q.isBlank()) "/json/stations/search?countrycode=DE$base"
        else "/json/stations/search?name=${URLEncoder.encode(q.trim(), "UTF-8")}$base"
        runCatching {
            val arr = JSONArray(get(path) ?: return@runCatching emptyList<Station>())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
                if (url.isEmpty()) return@mapNotNull null
                val info = listOf(o.optString("countrycode"), o.optString("codec"), o.optInt("bitrate").takeIf { it > 0 }?.let { "$it kbit/s" })
                    .filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")
                Station(o.optString("stationuuid", url), o.optString("name").trim().ifEmpty { "Radio" }, url, o.optString("favicon"), info, o.optString("homepage"))
            }.distinctBy { it.uuid }
        }.getOrDefault(emptyList())
    }

    /** Höflich zurückmelden, dass der Sender gehört wird (hilft der Beliebtheitsliste). */
    fun countClick(uuid: String) {
        Thread { runCatching { get("/json/url/$uuid") } }.start()
    }
}
