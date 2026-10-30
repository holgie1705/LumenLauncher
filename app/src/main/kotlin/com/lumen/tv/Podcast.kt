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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.runtime.Immutable
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URLEncoder

@Immutable class Podcast(val title: String, val feed: String, val artwork: String = "", val author: String = "")
@Immutable class Episode(val title: String, val audio: String, val date: String = "", val durationSec: Int = 0, val desc: String = "", val image: String = "")

/** Globaler Zustand des Podcast-Players. Die UI liest nur die Flows. */
object Podcasts {
    val current = MutableStateFlow<Episode?>(null)
    val show = MutableStateFlow("")
    val playing = MutableStateFlow(false)
    val buffering = MutableStateFlow(false)
    val position = MutableStateFlow(0L)    // ms
    val duration = MutableStateFlow(0L)     // ms
    val error = MutableStateFlow("")

    fun play(ctx: Context, ep: Episode, showTitle: String) {
        Radio.stop(ctx)   // nur eine Audioquelle gleichzeitig
        error.value = ""; current.value = ep; show.value = showTitle; buffering.value = true; playing.value = false
        position.value = 0; duration.value = 0
        val start = loadPos(ctx, ep.audio)
        val i = Intent(ctx, PodcastService::class.java).setAction("play")
            .putExtra("url", ep.audio).putExtra("title", ep.title).putExtra("show", showTitle).putExtra("start", start)
        if (runCatching { ctx.startForegroundService(i) }.isFailure) { current.value = null; buffering.value = false }
    }

    fun pause(ctx: Context) = send(ctx, "pause")
    fun resume(ctx: Context) = send(ctx, "resume")
    fun seek(ctx: Context, deltaMs: Long) = runCatching { ctx.startService(Intent(ctx, PodcastService::class.java).setAction("seek").putExtra("delta", deltaMs)) }.let {}
    fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, PodcastService::class.java)) }; reset() }
    fun toggle(ctx: Context) { if (playing.value) pause(ctx) else resume(ctx) }

    private fun send(ctx: Context, action: String) { runCatching { ctx.startService(Intent(ctx, PodcastService::class.java).setAction(action)) } }
    internal fun reset() { current.value = null; show.value = ""; playing.value = false; buffering.value = false; position.value = 0; duration.value = 0 }

    // ---- Fortschritt merken (Datei im App-Speicher) ----
    private fun file(ctx: Context) = File(ctx.filesDir, "podpos.json")
    private fun all(ctx: Context): JSONObject = runCatching { JSONObject(file(ctx).readText()) }.getOrDefault(JSONObject())
    fun savePos(ctx: Context, url: String, sec: Int) = runCatching {
        val o = all(ctx); if (sec <= 2) o.remove(url) else o.put(url, sec); file(ctx).writeText(o.toString())
    }.let {}
    fun loadPos(ctx: Context, url: String): Int = all(ctx).optInt(url, 0)
}

/** Podcast-Wiedergabe im Vordergrunddienst (läuft weiter, wenn man eine andere App öffnet) – mit Spulen & Fortsetzen. */
class PodcastService : Service() {
    private var player: ExoPlayer? = null
    private val h = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            player?.let { p ->
                Podcasts.position.value = p.currentPosition.coerceAtLeast(0)
                Podcasts.duration.value = p.duration.coerceAtLeast(0)
                if (p.isPlaying) Podcasts.current.value?.let { Podcasts.savePos(this@PodcastService, it.audio, (p.currentPosition / 1000).toInt()) }
            }
            h.postDelayed(this, 1000)
        }
    }
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { Podcasts.playing.value = isPlaying }
        override fun onPlaybackStateChanged(state: Int) {
            Podcasts.buffering.value = state == Player.STATE_BUFFERING
            if (state == Player.STATE_ENDED) { Podcasts.current.value?.let { Podcasts.savePos(this@PodcastService, it.audio, 0) }; Podcasts.reset(); stopSelf() }
        }
        override fun onPlayerError(error: PlaybackException) {
            Podcasts.error.value = t("Diese Folge lässt sich gerade nicht abspielen.", "This episode can't be played right now.")
            Podcasts.reset(); stopSelf()
        }
    }

    private fun foreground(title: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("podcast", "Podcast", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 2, Intent(this, PodcastService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, "podcast").setContentTitle(title).setContentText("Lumen Podcast").setSmallIcon(R.drawable.ic_lumen).setOngoing(true)
            .addAction(Notification.Action.Builder(null, t("Stopp", "Stop"), stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1003, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(1003, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "stop" -> { saveNow(); Podcasts.reset(); stopSelf(); return START_NOT_STICKY }
            "pause" -> { player?.pause(); saveNow(); return START_NOT_STICKY }
            "resume" -> { player?.play(); return START_NOT_STICKY }
            "seek" -> { player?.let { it.seekTo((it.currentPosition + (intent.getLongExtra("delta", 0))).coerceAtLeast(0)) }; return START_NOT_STICKY }
            "play" -> {
                val url = intent.getStringExtra("url") ?: return START_NOT_STICKY.also { stopSelf() }
                foreground(intent.getStringExtra("title") ?: "Podcast")
                val p = player ?: ExoPlayer.Builder(this)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
                    .setHandleAudioBecomingNoisy(true).build().also { it.addListener(listener); player = it }
                p.setMediaItem(MediaItem.fromUri(url))
                p.prepare()
                val start = intent.getIntExtra("start", 0)
                if (start > 2) p.seekTo(start * 1000L)
                p.playWhenReady = true
                h.removeCallbacks(ticker); h.post(ticker)
            }
        }
        return START_NOT_STICKY
    }

    private fun saveNow() { player?.let { p -> Podcasts.current.value?.let { Podcasts.savePos(this, it.audio, (p.currentPosition / 1000).toInt()) } } }

    override fun onDestroy() { h.removeCallbacks(ticker); saveNow(); player?.release(); player = null; Podcasts.reset(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

/** Apple-Podcast-Suche (frei, ohne Schlüssel) + RSS-Feed-Parser. */
object PodcastApi {
    suspend fun search(term: String): List<Podcast> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()
        val txt = Http.get("https://itunes.apple.com/search?media=podcast&limit=30&country=DE&term=${URLEncoder.encode(term.trim(), "UTF-8")}") ?: return@withContext emptyList()
        runCatching {
            val arr = JSONObject(txt).optJSONArray("results") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val feed = o.optString("feedUrl"); if (feed.isEmpty()) return@mapNotNull null
                Podcast(o.optString("collectionName").ifEmpty { o.optString("trackName") }, feed,
                    o.optString("artworkUrl600").ifEmpty { o.optString("artworkUrl100") }, o.optString("artistName"))
            }.distinctBy { it.feed }
        }.getOrDefault(emptyList())
    }

    private fun dur(s: String): Int {
        val t = s.trim()
        if (t.isEmpty()) return 0
        if (!t.contains(':')) return t.toIntOrNull() ?: 0
        return t.split(':').map { it.toIntOrNull() ?: 0 }.fold(0) { acc, v -> acc * 60 + v }
    }

    /** Feed einlesen: Episoden (neueste zuerst) mit Audio-URL, Dauer, Datum. */
    suspend fun episodes(feed: String): Pair<Podcast, List<Episode>>? = withContext(Dispatchers.IO) {
        val xml = Http.get(feed, 15_000) ?: return@withContext null
        runCatching {
            val p = android.util.Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(xml.reader())
            var chTitle = ""; var chImage = ""; var chAuthor = ""
            val eps = mutableListOf<Episode>()
            var inItem = false
            var eTitle = ""; var eUrl = ""; var eDate = ""; var eDur = 0; var eDesc = ""; var eImg = ""
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT && eps.size < 200) {
                when (ev) {
                    XmlPullParser.START_TAG -> when (p.name.lowercase()) {
                        "item" -> { inItem = true; eTitle = ""; eUrl = ""; eDate = ""; eDur = 0; eDesc = ""; eImg = "" }
                        "title" -> { val v = p.nextText().trim(); if (inItem) eTitle = v else if (chTitle.isEmpty()) chTitle = v }
                        "enclosure" -> if (inItem && eUrl.isEmpty()) p.getAttributeValue(null, "url")?.let { eUrl = it }
                        "pubdate" -> if (inItem) eDate = p.nextText().trim()
                        "itunes:duration" -> if (inItem) eDur = dur(p.nextText())
                        "description", "itunes:summary" -> if (inItem && eDesc.isEmpty()) eDesc = p.nextText().trim().replace(Regex("<[^>]*>"), "").take(500)
                        "itunes:image" -> { val href = p.getAttributeValue(null, "href").orEmpty(); if (inItem) { if (eImg.isEmpty()) eImg = href } else if (chImage.isEmpty()) chImage = href }
                        "itunes:author" -> if (!inItem && chAuthor.isEmpty()) chAuthor = p.nextText().trim()
                    }
                    XmlPullParser.END_TAG -> if (p.name.equals("item", true)) {
                        if (eUrl.isNotEmpty() && eTitle.isNotEmpty()) eps += Episode(eTitle, eUrl, eDate, eDur, eDesc, eImg.ifEmpty { chImage })
                        inItem = false
                    }
                }
                ev = p.next()
            }
            Podcast(chTitle.ifEmpty { "Podcast" }, feed, chImage, chAuthor) to eps
        }.getOrNull()
    }
}
