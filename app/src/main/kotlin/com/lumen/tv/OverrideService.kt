package com.lumen.tv

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.tv.TvContract
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Die "Bedienungshilfe" von Lumen. Sie erledigt alles, was außerhalb der eigenen Oberfläche passieren muss:
 *  - Fernbedienungs-Shortcuts (Tastendrücke systemweit)
 *  - Auto-Standby und Einschlaf-Timer
 *  - Kindersicherung durchsetzen (gesperrte App im Vordergrund -> zurück zu Lumen mit PIN-Abfrage)
 *  - Stock-Launcher überschreiben
 *  - Kleine Uhr/Wetter-Anzeige über anderen Apps
 * Bildschirminhalte werden nur für den Neustart-Klick im Ausschaltmenü gelesen, nichts wird gespeichert oder gesendet.
 */
class OverrideService : AccessibilityService() {
    // Alle anderen Launcher auf dem Gerät (auch der der waipu-Box) werden automatisch erkannt.
    // Nur der eigentliche Startbildschirm (die HOME-Activity) zählt – nicht jedes Fenster einer App,
    // die zufällig auch Startbildschirm sein kann (z. B. waipu-Apps auf der waipu-Box).
    private var homeCache: Map<String, Set<String>> = emptyMap()
    private var stockAt = 0L
    private fun homeActivities(): Map<String, Set<String>> {
        val now = SystemClock.elapsedRealtime()
        if (homeCache.isEmpty() || now - stockAt > 60_000) {
            homeCache = runCatching {
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                    .map { it.activityInfo }
                    .filter { it.packageName != packageName }
                    .groupBy({ it.packageName }, { it.name })
                    .mapValues { it.value.toSet() }
            }.getOrDefault(emptyMap())
            stockAt = now
        }
        return homeCache
    }
    /**
     * true, wenn das Fenster ein Launcher-/Startbildschirm ist – egal welcher (Thomson, waipu-Home, Google …),
     * nur Lumen nicht. Dort soll das Uhr/Wetter-Overlay NICHT erscheinen (genauso wenig wie in Lumen selbst),
     * sondern nur über echten App-Inhalten. Anders als isStockHome ohne waipu/exaring-Ausnahme.
     */
    private fun isLauncherHome(pkg: String, cls: String?): Boolean {
        if (pkg == packageName) return true
        // waipu/exaring melden eigene Home-Activities, sind aber die App, in der das Overlay erscheinen SOLL -> nie als Launcher werten.
        if (pkg.contains("waipu", true) || pkg.contains("exaring", true)) return false
        val acts = homeActivities()[pkg]
        if (acts != null) return cls != null && cls in acts
        return pkg in STOCK_PKGS || pkg == "com.amazon.tv.launcher"
    }

    /** true nur, wenn wirklich der Stock-Launcher-Startbildschirm im Vordergrund ist. */
    private fun isStockHome(pkg: String, cls: String?): Boolean {
        if (pkg.contains("waipu", true) || pkg.contains("exaring", true) || pkg.contains("settings", true)) return false
        val acts = homeActivities()[pkg]
        if (acts != null) return cls != null && cls in acts
        // Bekannte Stock-Launcher, die sich nicht als HOME melden (z. B. eingefroren/anders registriert)
        return pkg in STOCK_PKGS || pkg == "com.amazon.tv.launcher"
    }
    private val handler = Handler(Looper.getMainLooper())
    private val sp by lazy { getSharedPreferences("lumen", MODE_PRIVATE) }
    private val parental by lazy { Parental(this) }

    private var prefs = Prefs()
    private var lastKey = SystemClock.elapsedRealtime()
    private var lastLaunch = 0L
    private var lastAppPkg = ""           // zuletzt im Vordergrund gewesene echte App (für Exit-Kill/Auto-Boost)
    private var lastBoost = 0L            // Drosselung des Auto-Boosts
    private var lastBambuStage = ""       // letzter bekannter Druckzustand (für Benachrichtigung)
    private var bambuInit = false         // erster Bambu-Poll: nur merken, nicht melden
    private var standbyPending = false
    private val swallowed = mutableSetOf<Int>()

    // Schnellwechsel: die letzten beiden echten Apps (ohne Lumen/Launcher)
    private var curApp = ""
    private var prevApp = ""
    private var inRealApp = false
    private var lastHomeDown = 0L
    private var homeTarget = ""
    private var swallowHomeUp = false

    // Einblendungen über anderen Apps
    private val volOsd by lazy { VolumeOsd(this) }
    private var lastOwnVol = 0L       // eigene Änderung (Nachtmodus-Begrenzung) – Meldung dazu nicht doppelt auswerten

    /**
     * Auf dieser Art Box verarbeitet Android die Lautstärketasten selbst, bevor eine Bedienungshilfe sie sieht.
     * Deshalb hört Lumen auf die System-Meldung „Lautstärke geändert" und zeigt daraufhin die eigene Anzeige.
     */
    private val volReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            if (!prefs.volOsd && !prefs.volNight) return
            val stream = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", AudioManager.STREAM_MUSIC)
            if (stream != AudioManager.STREAM_MUSIC) return
            if (SystemClock.elapsedRealtime() - lastOwnVol < 350) return
            handler.post { runCatching { volumeChanged() } }
        }
    }
    private val goalOverlay by lazy { GoalOverlay(this) }
    private val longTimers = mutableMapOf<Int, Runnable>()

    private val pendingStandby = Runnable { standbyPending = false; standbyNow() }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "prefs") loadPrefs() }
    private val tick = object : Runnable {
        override fun run() { checkStandby(); pollBambu(); handler.postDelayed(this, 30_000) }
    }

    companion object {
        @Volatile var instance: OverrideService? = null
    }

    /** Lumen ist im Vordergrund (Hauptmenü/Einstellungen/Schoner) -> Uhr/Wetter-Einblendung weg. */
    fun lumenResumed() {
        miniWanted = false; hideMini()
        // Zurück im Hauptmenü: die gerade verlassene App gezielt räumen (Exit-Kill) und bei Bedarf Auto-Boost.
        val left = lastAppPkg; lastAppPkg = ""
        maybeExitKill(left, packageName)
        maybeAutoBoost()
    }
    /** Lumen ist in den Hintergrund gegangen (eine andere App ist vorn). Das Anzeigen übernimmt das Fenster-Event. */
    fun lumenPaused() {}

    override fun onServiceConnected() {
        instance = this
        loadPrefs()
        // Zweiter Weg zum Start nach dem Booten: Der Dienst startet mit dem System und darf Lumen nach vorn holen.
        // Kurz nach dem Booten das Uhr/Wetter-Overlay unterdrücken, damit es nicht über Boot-Splash/SystemUI aufblitzt,
        // bevor Lumen als Vordergrund gemeldet ist.
        if (SystemClock.elapsedRealtime() < 240_000L) miniMuteUntil = SystemClock.elapsedRealtime() + 20_000L
        // Nach dem Booten Lumen früh und mehrfach nach vorn holen, damit der Stock-Launcher möglichst kurz zu sehen ist.
        if (prefs.bootStart && SystemClock.elapsedRealtime() < 240_000L) {
            longArrayOf(800, 1_600, 3_000, 5_000).forEach { d -> handler.postDelayed({ bringLumen(force = true) }, d) }
        }
        sp.registerOnSharedPreferenceChangeListener(prefListener)
        runCatching {
            val f = android.content.IntentFilter().apply {
                addAction("android.media.VOLUME_CHANGED_ACTION"); addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
            }
            androidx.core.content.ContextCompat.registerReceiver(this, volReceiver, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        }
        handler.postDelayed(tick, 30_000)
        handler.postDelayed(overlayTick, 4_000)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacksAndMessages(null)
        hideMini()
        runCatching { unregisterReceiver(volReceiver) }
        runCatching { volOsd.remove(); goalOverlay.remove() }
        phone?.stop(); phone = null
        closeKeypad(); runCatching { svcScope.cancel() }
        return super.onUnbind(intent)
    }

    private fun loadPrefs() {
        val oldPos = prefs.miniPos
        val oldLayout = prefs.layout
        val oldGlass = prefs.miniGlass
        prefs = runCatching { prefsFromJson(JSONObject(sp.getString("prefs", "{}") ?: "{}")) }.getOrDefault(Prefs())
        if (oldPos != prefs.miniPos && miniView != null) hideMini()   // neue Ecke: Overlay neu aufbauen
        if (oldLayout != prefs.layout && miniView != null) hideMini()  // anderer Look (Spatial-Pille): neu aufbauen
        if (oldGlass != prefs.miniGlass && miniView != null) hideMini() // Glas <-> transparent: neu aufbauen
        if (!prefs.miniOverlay) hideMini() else if (miniWanted) showMini()
        syncPhoneRemote()
    }

    // ---------- Handy als Fernbedienung ----------
    private var phone: PhoneRemoteServer? = null

    private fun syncPhoneRemote() {
        if (prefs.phoneRemote && prefs.remoteCode.isNotEmpty()) {
            if (phone?.running != true) phone = PhoneRemoteServer({ prefs.remoteCode }) { a, p -> remoteAction(a, p) }.also { it.start() }
        } else { phone?.stop(); phone = null }
    }

    private fun onMain(block: () -> Unit) = handler.post { runCatching(block) }

    private fun mediaKey(code: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Fokus im Vordergrund-Fenster bewegen bzw. anklicken (für Android < 13 ohne Steuerkreuz-Aktionen). */
    private fun dpadFallback(keyCode: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName == packageName) { RemoteBus.keys.tryEmit(keyCode); return true }
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            var n = f
            while (n != null && !n.isClickable) n = n.parent
            return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        }
        if (f == null) {
            // noch nichts fokussiert: erstes fokussierbares Element wählen
            val q = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var seen = 0
            while (q.isNotEmpty() && seen < 400) {
                val n = q.removeFirst(); seen++
                if (n.isFocusable && n.isVisibleToUser) return n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
            }
            return false
        }
        val dir = when (keyCode) { KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP; KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN; KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT; else -> View.FOCUS_RIGHT }
        return f.focusSearch(dir)?.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ?: false
    }

    private fun dpad(keyCode: Int) {
        if (Build.VERSION.SDK_INT >= 33) {
            val g = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> GLOBAL_ACTION_DPAD_UP; KeyEvent.KEYCODE_DPAD_DOWN -> GLOBAL_ACTION_DPAD_DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> GLOBAL_ACTION_DPAD_LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> GLOBAL_ACTION_DPAD_RIGHT
                else -> GLOBAL_ACTION_DPAD_CENTER
            }
            if (performGlobalAction(g)) return
        }
        dpadFallback(keyCode)
    }

    /** Fokussiertes Eingabefeld (oder das erste sichtbare). */
    private fun editField(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        val q = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var seen = 0
        while (q.isNotEmpty() && seen < 500) {
            val n = q.removeFirst(); seen++
            if (n.isEditable && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return null
    }

    /** Wird vom Handy-Webserver aufgerufen. Rückgabe: null = ok, String = Fehlermeldung, JSONArray = Liste. */
    private fun remoteAction(a: String, p: Map<String, String>): Any? {
        lastKey = SystemClock.elapsedRealtime()
        when (a) {
            "up" -> onMain { dpad(KeyEvent.KEYCODE_DPAD_UP) }
            "down" -> onMain { dpad(KeyEvent.KEYCODE_DPAD_DOWN) }
            "left" -> onMain { dpad(KeyEvent.KEYCODE_DPAD_LEFT) }
            "right" -> onMain { dpad(KeyEvent.KEYCODE_DPAD_RIGHT) }
            "ok" -> onMain { dpad(KeyEvent.KEYCODE_DPAD_CENTER) }
            "back" -> onMain { performGlobalAction(GLOBAL_ACTION_BACK) }
            "home" -> onMain { performGlobalAction(GLOBAL_ACTION_HOME) }
            "recents" -> onMain { performGlobalAction(GLOBAL_ACTION_RECENTS) }
            "standby" -> onMain { standbyNow() }
            "play" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            "next" -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            "prev" -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "rew" -> mediaKey(KeyEvent.KEYCODE_MEDIA_REWIND)
            "ffwd" -> mediaKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
            "volup", "voldown", "mute" -> {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                val dir = when (a) { "volup" -> AudioManager.ADJUST_RAISE; "voldown" -> AudioManager.ADJUST_LOWER; else -> AudioManager.ADJUST_TOGGLE_MUTE }
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
            }
            "text" -> {
                val txt = p["t"].orEmpty()
                val f = editField() ?: return t("Kein Eingabefeld am TV gefunden – erst ein Textfeld auswählen.", "No text field on the TV – select one first.")
                val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, txt) }
                if (!f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return t("Dieses Feld nimmt keinen Text an.", "This field does not accept text.")
            }
            "enter" -> {
                val f = editField()
                val ok = Build.VERSION.SDK_INT >= 30 && f != null && f.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                if (!ok) onMain { dpad(KeyEvent.KEYCODE_DPAD_CENTER) }
            }
            "app" -> {
                val pkg = p["p"].orEmpty()
                val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg) ?: return t("App nicht gefunden.", "App not found.")
                onMain { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            "apps" -> {
                val pm = packageManager
                val seen = HashSet<String>()
                val list = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { cat ->
                    runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0) }.getOrDefault(emptyList())
                }.filter { it.activityInfo.packageName != packageName && seen.add(it.activityInfo.packageName) }
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .sortedBy { it.second.lowercase() }
                return org.json.JSONArray().apply { list.forEach { (pkg, label) -> put(JSONObject().put("pkg", pkg).put("label", label)) } }
            }
            else -> return t("Unbekannte Aktion", "Unknown action")
        }
        return null
    }

    // ---------- Kleine Uhr + Wetter oben links, solange eine andere App läuft ----------
    private var miniView: LinearLayout? = null
    private var miniClock: TextView? = null
    private var miniWeather: TextView? = null
    private var miniLine: View? = null          // Spatial: feine Leuchtlinie in der Farbe der laufenden App
    private val accentCache = HashMap<String, Int>()

    /** Kräftige Farbe aus Banner bzw. Icon einer App (für die Leuchtlinie der Uhr-Pille). */
    private fun appAccent(pkg: String): Int = accentCache.getOrPut(pkg) {
        runCatching {
            val dr = packageManager.getApplicationBanner(pkg) ?: packageManager.getApplicationIcon(pkg)
            val bmp = androidx.core.graphics.drawable.DrawableCompat.wrap(dr).let { d ->
                val b = android.graphics.Bitmap.createBitmap(96, 54, android.graphics.Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(b); d.setBounds(0, 0, 96, 54); d.draw(cv); b
            }
            val p = androidx.palette.graphics.Palette.from(bmp).generate()
            p.getVibrantColor(p.getLightVibrantColor(p.getDominantColor(0xFF4C6FFF.toInt())))
        }.getOrDefault(0xFF4C6FFF.toInt())
    }
    private var miniWanted = false
    private var miniMuteUntil = 0L        // kurz nach dem Booten das Overlay unterdrücken (Boot-Splash blitzt sonst durch)
    private var lastWeatherFetch = 0L
    private val miniTick = object : Runnable {
        override fun run() { updateMini(); handler.postDelayed(this, 15_000) }
    }
    // Wächter: gleicht das Overlay dauerhaft an die echte Vordergrund-App an und baut es neu auf,
    // falls das System unser Fenster still verworfen hat. Läuft immer (unabhängig von Fenster-Events).
    private val overlayTick = object : Runnable {
        override fun run() { ensureOverlay(); handler.postDelayed(this, 4_000) }
    }

    /** Launcher-/Startbildschirm nur anhand des Pakets (für den Wächter, der keine Fensterklasse kennt). */
    private fun isLauncherPkg(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg.contains("waipu", true) || pkg.contains("exaring", true)) return false
        return homeActivities().containsKey(pkg) || pkg in STOCK_PKGS || pkg == "com.amazon.tv.launcher"
    }

    private fun ensureOverlay() {
        if (!prefs.miniOverlay) { if (miniView != null) hideMini(); return }
        // Soll-Zustand neu bestimmen – robust gegen verpasste Fenster-Events.
        if (GoalWatch.foreground) {
            miniWanted = false   // Lumen ist vorn
        } else {
            val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            if (pkg != null && pkg != packageName) {
                val transient = pkg.contains("systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard")
                if (!transient) miniWanted = !isLauncherPkg(pkg)
            }
        }
        // Overlay an den Soll-Zustand angleichen.
        if (miniWanted) {
            val v = miniView
            if (v == null) showMini() else if (!v.isAttachedToWindow) { hideMini(); showMini() }
        } else if (miniView != null) hideMini()
    }

    private fun clockText(): String {
        val pattern = when (prefs.timeFormat) {
            2 -> "h:mm a"
            1 -> (android.text.format.DateFormat.getTimeFormat(this) as? SimpleDateFormat)?.toPattern() ?: "HH:mm"
            else -> "HH:mm"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    private fun updateMini() {
        miniClock?.text = clockText()
        miniLine?.let { line ->
            val c = appAccent(curApp)
            line.background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(c and 0x00FFFFFF, c, c and 0x00FFFFFF),
            )
        }
        if (prefs.weather == 0) { miniWeather?.visibility = View.GONE; return }
        if (prefs.weatherLat == 0.0 && prefs.weatherLon == 0.0) { miniWeather?.visibility = View.GONE; return }
        val repo = WeatherRepo(this)
        val w = repo.cached(prefs.weatherLat, prefs.weatherLon, prefs.tempF)
        if (w != null) {
            miniWeather?.apply {
                val px = (textSize * 1.2f).toInt().coerceAtLeast(18)
                val icon = android.graphics.drawable.BitmapDrawable(resources, weatherBitmap(w.code, w.isDay, px)).apply { setBounds(0, 0, px, px) }
                setCompoundDrawables(icon, null, null, null)
                compoundDrawablePadding = (4 * resources.displayMetrics.density).toInt()
                text = " " + w.temp + "°"
                visibility = View.VISIBLE
            }
        } else miniWeather?.visibility = View.GONE
        val now = SystemClock.elapsedRealtime()
        if (now - lastWeatherFetch > 30 * 60_000L) {
            lastWeatherFetch = now
            val lat = prefs.weatherLat; val lon = prefs.weatherLon; val f = prefs.tempF
            Thread {
                val fresh = kotlinx.coroutines.runBlocking { repo.fetch(lat, lon, f) }
                if (fresh != null) handler.post { if (miniView != null) updateMini() }
            }.start()
        }
    }

    private fun showMini() {
        if (!prefs.miniOverlay) return
        if (SystemClock.elapsedRealtime() < miniMuteUntil) return    // Boot-Phase: noch nicht einblenden (gegen Flackern)
        if (miniView == null) {   // immer die neue Glas-Pille (unabhängig vom Layout)
            // Spatial: Glas-Pille mit Uhr | Wetter und Leuchtlinie, gleitet von oben/unten ein
            val d = resources.displayMetrics.density
            val right = prefs.miniPos == 1 || prefs.miniPos == 3
            val bottom = prefs.miniPos >= 2
            val font = jakartaFace(this)
            val clock = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f; typeface = font; runCatching { setFontVariationSettings(wght(400)) } }
            val weather = TextView(this).apply { setTextColor(Color.WHITE); textSize = 12f; typeface = font; runCatching { setFontVariationSettings(wght(700)) } }
            val sep = View(this).apply { setBackgroundColor(0x38FFFFFF) }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((13 * d).toInt(), 0, (12 * d).toInt(), 0)
                addView(clock)
                addView(sep, LinearLayout.LayoutParams((1 * d).toInt(), (14 * d).toInt()).apply { leftMargin = (9 * d).toInt(); rightMargin = (9 * d).toInt() })
                addView(weather)
            }
            val line = View(this)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 17 * d; setColor(0x8C0A0C12.toInt()); setStroke((1 * d).toInt(), 0x29FFFFFF)
                }
                addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (32 * d).toInt()))
                addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (2 * d).toInt()).apply { leftMargin = (14 * d).toInt(); rightMargin = (14 * d).toInt(); bottomMargin = (2 * d).toInt() })
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = (if (bottom) Gravity.BOTTOM else Gravity.TOP) or (if (right) Gravity.END else Gravity.START)
                x = (26 * d).toInt(); y = ((if (bottom) 22 else 20) * d).toInt()
            }
            val ok = runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(box, lp) }.isSuccess
            if (!ok) return
            if (!prefs.miniGlass) {
                // Transparent: kein Glas, keine Farblinie – nur Schrift mit weichem Schatten
                box.background = null
                line.visibility = View.GONE
                listOf(clock, weather).forEach { it.setShadowLayer(6f * d, 0f, 1f * d, Color.argb(200, 0, 0, 0)) }
                sep.setBackgroundColor(0x66FFFFFF)
            }
            miniView = box; miniClock = clock; miniWeather = weather; miniLine = if (prefs.miniGlass) line else null
            box.alpha = 0f; box.translationY = (if (bottom) 16 else -16) * d
            box.animate().alpha(1f).translationY(0f).setDuration(420).start()
            handler.postDelayed({ if (miniView != null) updateMini() }, 400)   // Farbe der gerade geöffneten App nachziehen
        }
        updateMini()
        handler.removeCallbacks(miniTick)
        handler.postDelayed(miniTick, 15_000)
    }

    private fun hideMini() {
        handler.removeCallbacks(miniTick)
        miniView?.let { v -> runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } }
        miniView = null; miniClock = null; miniWeather = null; miniLine = null
    }

    // ---------- Ziffernblock-Overlay (sendet Zahlen per ADB an die laufende App, z. B. waipu) ----------
    private val svcScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private var padView: View? = null
    private var padBuffer = StringBuilder()
    private var padSel = 0
    private val padTiles = mutableListOf<TextView>()
    private var padBufView: TextView? = null
    private var padHint: TextView? = null
    private val padKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
    val keypadOpen get() = padView != null
    private var padDeadline = 0L
    private val padAutoTick = object : Runnable {
        override fun run() {
            if (padView == null) return
            val left = padDeadline - SystemClock.elapsedRealtime()
            if (left <= 0) { sendPad(); return }
            padHint?.text = t("sendet in ${left / 1000 + 1}s · OK sofort · Zurück bricht ab", "sends in ${left / 1000 + 1}s · OK now · Back cancels")
            handler.postDelayed(this, 200)
        }
    }
    private val padHintDefault get() = t("Pfeiltasten wählen · OK übernimmt · Zurück schließt", "Arrows pick · OK confirms · Back closes")

    private fun cancelAutoSend() { handler.removeCallbacks(padAutoTick) }
    /** Startet/erneuert den „nach X Sek. automatisch senden"-Countdown (0 = aus). Bei leerem Puffer nur zurücksetzen. */
    private fun scheduleAutoSend() {
        cancelAutoSend()
        val secs = prefs.keypadAutoSend
        if (secs <= 0 || padBuffer.isEmpty()) { padHint?.text = padHintDefault; return }
        padDeadline = SystemClock.elapsedRealtime() + secs * 1000L
        handler.post(padAutoTick)
    }

    fun toggleKeypad() { if (padView == null) openKeypad() else closeKeypad() }

    private fun openKeypad() {
        runCatching {
            val d = resources.displayMetrics.density
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            padTiles.clear()
            fun dp(v: Int) = (v * d).toInt()
            padBufView = TextView(this).apply {
                setTextColor(Color.WHITE); textSize = 30f; typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER; text = "—"
                setPadding(0, dp(4), 0, dp(10))
            }
            padHint = TextView(this).apply {
                setTextColor(Color.argb(200, 255, 255, 255)); textSize = 11f; gravity = Gravity.CENTER
                text = padHintDefault
                setPadding(0, dp(8), 0, 0)
            }
            val grid = android.widget.GridLayout(this).apply { columnCount = 3; rowCount = 4 }
            padKeys.forEachIndexed { i, label ->
                val tv = TextView(this).apply {
                    text = label; setTextColor(Color.WHITE); textSize = 22f; gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    val lp = android.widget.GridLayout.LayoutParams().apply {
                        width = dp(66); height = dp(54)
                        setMargins(dp(5), dp(5), dp(5), dp(5))
                        rowSpec = android.widget.GridLayout.spec(i / 3)
                        columnSpec = android.widget.GridLayout.spec(i % 3)
                    }
                    layoutParams = lp
                }
                padTiles.add(tv); grid.addView(tv)
            }
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(14), dp(18), dp(12))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(22).toFloat(); setColor(Color.argb(240, 16, 20, 34))
                    setStroke(dp(1), Color.argb(90, 255, 255, 255))
                }
                addView(padBufView); addView(grid); addView(padHint)
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // nicht fokussierbar: die laufende App (waipu) bleibt im Vordergrund und empfängt später die Ziffern
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.BOTTOM or Gravity.END; x = dp(40); y = dp(60) }
            if (!runCatching { wm.addView(box, lp) }.isSuccess) return
            padView = box; padBuffer = StringBuilder(); padSel = 0
            renderKeypad()
        }
    }

    private fun renderKeypad() {
        padBufView?.text = if (padBuffer.isEmpty()) "—" else padBuffer.toString()
        padTiles.forEachIndexed { i, tv ->
            val sel = i == padSel
            tv.setBackgroundColor(if (sel) Color.argb(255, 79, 227, 240) else Color.argb(40, 255, 255, 255))
            tv.setTextColor(if (sel) Color.BLACK else Color.WHITE)
        }
    }

    private fun closeKeypad() {
        cancelAutoSend()
        padView?.let { v -> runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } }
        padView = null; padTiles.clear(); padBufView = null; padHint = null; padBuffer = StringBuilder()
    }

    /** Die gesammelte Zahl senden (von OK oder vom Auto-Countdown). */
    private fun sendPad() {
        cancelAutoSend()
        val num = padBuffer.toString()
        if (num.isEmpty()) { closeKeypad(); return }
        closeKeypad()
        svcScope.launch {
            val ok = AdbCtl.sendNumber(this@OverrideService, prefs, num, prefs.keypadEnter)
            if (!ok) toast(t("Konnte die Zahl nicht senden – ADB-Netzwerk/Einstellungen prüfen.", "Could not send the number – check ADB network/settings."))
        }
    }

    /** Taste im Ziffernblock verarbeiten. Gibt true zurück, wenn der Tastendruck verbraucht wurde. */
    private fun keypadKey(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { padSel = (padSel + 11) % 12; renderKeypad() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { padSel = (padSel + 1) % 12; renderKeypad() }
            KeyEvent.KEYCODE_DPAD_UP -> { padSel = (padSel + 9) % 12; renderKeypad() }
            KeyEvent.KEYCODE_DPAD_DOWN -> { padSel = (padSel + 3) % 12; renderKeypad() }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A -> activateKeypad()
            KeyEvent.KEYCODE_BACK -> closeKeypad()
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { padBuffer.append(('0' + (code - KeyEvent.KEYCODE_0))); renderKeypad(); scheduleAutoSend() }
            KeyEvent.KEYCODE_DEL -> { if (padBuffer.isNotEmpty()) padBuffer.deleteCharAt(padBuffer.length - 1); renderKeypad(); scheduleAutoSend() }
            else -> return false
        }
        return true
    }

    private fun activateKeypad() {
        when (val k = padKeys[padSel]) {
            "⌫" -> { if (padBuffer.isNotEmpty()) padBuffer.deleteCharAt(padBuffer.length - 1); renderKeypad(); scheduleAutoSend() }
            "OK" -> sendPad()
            else -> { padBuffer.append(k); renderKeypad(); scheduleAutoSend() }
        }
    }

    // ---------- Fenster: Kindersicherung + Stock-Launcher ----------
    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        // Eigene Fenster (inkl. der Overlay-Einblendung selbst) ignorieren – sonst würde sich das Overlay sofort selbst ausblenden.
        // Ob Lumen im Vordergrund ist, steuert zuverlässig der Activity-Lebenszyklus (lumenResumed/lumenPaused).
        if (pkg == packageName) return
        val cls = e.className?.toString()
        // Flüchtige Fenster (Lautstärke-Leiste = SystemUI, Tastatur) dürfen das Overlay NICHT ausschalten –
        // sonst verschwindet Uhr/Wetter mitten in einer App und kommt erst beim nächsten Fensterwechsel wieder.
        val transient = pkg.contains("systemui") || pkg.contains("inputmethod") || pkg.contains("keyboard")
        when {
            transient -> { /* Zustand unverändert lassen */ }
            isLauncherHome(pkg, cls) -> { miniWanted = false; hideMini(); inRealApp = false }
            else -> {
                miniWanted = true; showMini()
                inRealApp = true
                if (pkg != curApp) { if (curApp.isNotEmpty()) prevApp = curApp; curApp = pkg }
                // Echter App-Wechsel: die vorher offene App gezielt aus dem Hintergrund räumen (Exit-Kill).
                if (pkg != lastAppPkg) {
                    maybeExitKill(lastAppPkg, pkg)
                    lastAppPkg = pkg
                }
            }
        }
        if (parental.isBlocked(pkg)) {
            bringLumen { putExtra(EXTRA_LOCKED, pkg) }
        } else if (sp.getBoolean("override", false) && isStockHome(pkg, cls) &&
            System.currentTimeMillis() > sp.getLong("graceUntil", 0L)) {
            bringLumen()
        }
    }

    private fun bringLumen(force: Boolean = false, extra: Intent.() -> Unit = {}) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastLaunch < 1_500) return
        lastLaunch = now
        // Lumen kommt nach vorn -> kleine Uhr/Wetter-Einblendung ausblenden
        miniWanted = false; hideMini()
        runCatching {
            // Ohne Übergangs-Animation nach vorn holen – sonst blitzt auf schwachen Boxen kurz der Stock-Launcher durch.
            val opts = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .apply(extra),
                opts,
            )
        }
    }

    // ---------- Tasten: Shortcuts + Aktivitätserkennung ----------
    override fun onKeyEvent(e: KeyEvent): Boolean {
        onInput()
        val code = e.keyCode
        // Ziffernblock offen: alle Tasten lenken ihn (nicht die laufende App); die zugewiesene Taste schließt wieder.
        if (keypadOpen) {
            if (e.action == KeyEvent.ACTION_DOWN) {
                if (prefs.shortcuts.any { it.keyCode == code && it.action == ActionType.KEYPAD }) closeKeypad()
                else keypadKey(code)
            }
            return true
        }
        // Tasten-Anlernen aus den Einstellungen: nächste Sondertaste melden und verschlucken
        if (KeyLearn.waiting.value && code !in KeyLearn.reserved) {
            if (e.action == KeyEvent.ACTION_DOWN) { KeyLearn.waiting.value = false; swallowed += code; KeyLearn.captured.value = code }
            return true
        }
        if (code == KeyEvent.KEYCODE_HOME && prefs.homeDouble) return homeKey(e)
        if ((prefs.volOsd || prefs.volNight) && (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN || code == KeyEvent.KEYCODE_VOLUME_MUTE)) {
            if (volumeKey(e)) return true
        }
        val mine = prefs.shortcuts.filter { it.keyCode == code }
        if (mine.isEmpty()) return e.action == KeyEvent.ACTION_UP && swallowed.remove(code)
        val long = mine.firstOrNull { it.long }
        val short = mine.firstOrNull { !it.long }
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount == 0 && long != null) {
                    val r = Runnable { swallowed += code; perform(long) }
                    longTimers[code] = r
                    handler.postDelayed(r, 600)
                }
                return short != null || code in swallowed
            }
            KeyEvent.ACTION_UP -> {
                longTimers.remove(code)?.let { handler.removeCallbacks(it) }
                if (swallowed.remove(code)) return true
                if (short != null) { perform(short); return true }
                return false
            }
        }
        return false
    }

    // ---------- Schnellwechsel zur letzten App ----------
    /** Ziel des Wechsels: aus einer App heraus die vorherige, aus Lumen heraus die zuletzt benutzte. */
    private fun switchTarget(): String =
        if (!GoalWatch.foreground && inRealApp && curApp.isNotEmpty()) prevApp else curApp

    private fun openApp(pkg: String) {
        if (pkg.isBlank()) return
        if (parental.isBlocked(pkg)) { bringLumen(force = true) { putExtra(EXTRA_LOCKED, pkg) }; return }
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) { toast(t("Keine App zum Wechseln.", "No app to switch to.")); return }
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
    }

    fun switchApp() {
        val target = switchTarget()
        if (target.isBlank()) toast(t("Noch keine andere App geöffnet.", "No other app opened yet.")) else openApp(target)
    }

    /** Home: erster Druck geht ganz normal durch, ein zweiter innerhalb von 0,45 s wechselt zur letzten App. */
    private fun homeKey(e: KeyEvent): Boolean {
        when (e.action) {
            KeyEvent.ACTION_DOWN -> {
                if (e.repeatCount > 0) return swallowHomeUp
                val now = SystemClock.elapsedRealtime()
                if (now - lastHomeDown < 450 && homeTarget.isNotEmpty()) {
                    lastHomeDown = 0L; swallowHomeUp = true
                    val target = homeTarget; homeTarget = ""
                    handler.postDelayed({ openApp(target) }, 120)
                    return true
                }
                lastHomeDown = now; homeTarget = switchTarget(); swallowHomeUp = false
                return false
            }
            KeyEvent.ACTION_UP -> { if (swallowHomeUp) { swallowHomeUp = false; return true }; return false }
        }
        return false
    }

    // ---------- Eigene Lautstärke-Anzeige + Nachtmodus ----------
    private fun nightNow(): Boolean {
        if (!prefs.volNight) return false
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val from = prefs.volNightFrom; val to = prefs.volNightTo
        return if (from == to) true else if (from < to) h in from until to else h >= from || h < to
    }

    /** true = Taste erledigt. Bei fester Lautstärke (HDMI/TV regelt selbst) bleibt alles beim System. */
    private fun volumeKey(e: KeyEvent): Boolean {
        val am = getSystemService(AudioManager::class.java) ?: return false
        if (am.isVolumeFixed) return false
        val code = e.keyCode
        if (e.action == KeyEvent.ACTION_UP) return true
        if (e.action != KeyEvent.ACTION_DOWN) return false
        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        // Ohne eigene Anzeige den Android-Balken weiter zeigen, nur begrenzen.
        val flags = if (prefs.volOsd) 0 else AudioManager.FLAG_SHOW_UI
        when (code) {
            KeyEvent.KEYCODE_VOLUME_UP -> am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, flags)
            KeyEvent.KEYCODE_VOLUME_DOWN -> am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, flags)
            else -> if (e.repeatCount == 0) am.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, flags)
        }
        lastOwnVol = SystemClock.elapsedRealtime()
        volumeChanged(own = true)
        return true
    }

    /** Nach jeder Änderung: Nachtmodus durchsetzen und die Neon-Anzeige zeigen. */
    private fun volumeChanged(own: Boolean = false) {
        val am = getSystemService(AudioManager::class.java) ?: return
        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
        var capHit = false
        val night = nightNow()
        val capVol = if (night) (max * prefs.volNightMax + 99) / 100 else max
        if (night && am.getStreamVolume(stream) > capVol) {
            lastOwnVol = SystemClock.elapsedRealtime()
            am.setStreamVolume(stream, capVol, if (prefs.volOsd || !own) 0 else AudioManager.FLAG_SHOW_UI)
            capHit = true
        }
        if (prefs.volOsd) {
            val muted = runCatching { am.isStreamMute(stream) }.getOrDefault(false)
            val pct = am.getStreamVolume(stream) * 100 / max
            volOsd.show(pct, muted, if (night) prefs.volNightMax else null, capHit)
        }
    }

    // ---------- Tor-Alarm über anderen Apps ----------
    fun showGoal(e: GoalEvent) = handler.post { runCatching { goalOverlay.show(e) } }

    private fun onInput() {
        lastKey = SystemClock.elapsedRealtime()
        if (standbyPending) {
            handler.removeCallbacks(pendingStandby)
            standbyPending = false
            toast(t("Standby abgebrochen", "Standby cancelled"))
        }
    }

    private fun perform(s: Shortcut) {
        when (s.action) {
            ActionType.LUMEN -> bringLumen(force = true)
            ActionType.STANDBY -> standbyNow()
            ActionType.APP -> {
                if (parental.isBlocked(s.arg)) {
                    bringLumen(force = true) { putExtra(EXTRA_LOCKED, s.arg) }
                } else {
                    (packageManager.getLeanbackLaunchIntentForPackage(s.arg) ?: packageManager.getLaunchIntentForPackage(s.arg))
                        ?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }
            }
            ActionType.INPUT -> runCatching {
                startActivity(
                    Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(s.arg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            ActionType.TIMER -> {
                val base = maxOf(System.currentTimeMillis(), sp.getLong("sleep_until", 0L))
                sp.edit { putLong("sleep_until", base + 30 * 60_000L) }
                toast(t("Einschlaf-Timer: +30 Minuten", "Sleep timer: +30 minutes"))
            }
            ActionType.ASSIST -> runCatching { startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            ActionType.KEYPAD -> handler.post { toggleKeypad() }
            ActionType.SWITCH -> switchApp()
        }
    }

    // ---------- Gründlicher RAM-Boost: „Beenden erzwingen“ in den App-Infos automatisch drücken (wie andere App-Killer) ----------
    @Volatile var forceStopRunning = false
        private set

    private val FORCE_LABELS = listOf("Beenden erzwingen", "Stopp erzwingen", "Erzwungen beenden", "Force stop", "Force Stop", "FORCE STOP")
    private val OK_LABELS = listOf("OK", "Ok", "Beenden erzwingen", "Force stop", "Ja", "Yes")

    /** Für jede App die App-Info öffnen, „Beenden erzwingen“ + „OK“ drücken. Läuft im Hintergrund-Thread. */
    fun forceStop(pkgs: List<String>, progress: (Int, Int, String) -> Unit, done: (Int) -> Unit) {
        if (forceStopRunning || pkgs.isEmpty()) { done(0); return }
        forceStopRunning = true
        Thread {
            var ok = 0
            pkgs.forEachIndexed { i, p ->
                handler.post { progress(i + 1, pkgs.size, p) }
                runCatching {
                    startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$p"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
                    )
                    Thread.sleep(500)
                    if (clickLabel(FORCE_LABELS, 3500)) {
                        Thread.sleep(350)
                        clickLabel(OK_LABELS, 2500, listOf("android:id/button1"))
                        ok++
                    }
                }
                Thread.sleep(250)
            }
            forceStopRunning = false
            handler.post { bringLumen(force = true); done(ok) }
        }.start()
    }

    /** Sucht einen Knopf/Listeneintrag mit einem der Texte und klickt ihn. false, wenn nicht gefunden oder ausgegraut (App läuft schon nicht). */
    private fun clickLabel(labels: List<String>, timeoutMs: Long, ids: List<String> = emptyList()): Boolean {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < end) {
            val root = runCatching { rootInActiveWindow }.getOrNull()
            if (root != null) {
                val nodes = ids.flatMap { runCatching { root.findAccessibilityNodeInfosByViewId(it) }.getOrDefault(emptyList()) } +
                    labels.flatMap { l -> runCatching { root.findAccessibilityNodeInfosByText(l) }.getOrDefault(emptyList()) }
                        .filter { n -> labels.any { n.text?.toString()?.trim().equals(it, true) } }
                val target = nodes.firstOrNull { it.isEnabled }
                if (target != null) {
                    var c: android.view.accessibility.AccessibilityNodeInfo? = target
                    while (c != null && !c.isClickable) c = c.parent
                    if ((c ?: target).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
                }
                if (nodes.isNotEmpty() && nodes.none { it.isEnabled }) return false
            }
            Thread.sleep(150)
        }
        return false
    }

    // ---------- Auto-Boost & Exit-Kill (gezieltes RAM-Management) ----------
    private fun musicActive() = runCatching { getSystemService(AudioManager::class.java)?.isMusicActive == true }.getOrDefault(false)

    /** App, die gerade verlassen wurde, aus dem Hintergrund räumen – nur wenn aktiviert und kein Ton mehr läuft. */
    private fun maybeExitKill(old: String, newPkg: String) {
        if (!prefs.exitKill || old.isBlank() || old == newPkg) return
        if (musicActive()) return   // noch Ton (Musik/Video-Audio) -> nicht beenden
        Thread { runCatching { SysTools.killApp(this, old) } }.start()
    }

    /** Beim Zurückkehren ins Hauptmenü aufräumen – nur wenn RAM wirklich knapp ist (Schwelle) und gedrosselt. */
    private fun maybeAutoBoost() {
        if (!prefs.autoBoost) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBoost < 30_000) return
        if (musicActive()) return
        Thread {
            if (SysTools.usedPct(this) < prefs.autoBoostPct) return@Thread
            lastBoost = SystemClock.elapsedRealtime()
            runCatching { kotlinx.coroutines.runBlocking { SysTools.boost(this@OverrideService, emptyList(), prefs.boostExclude) } }
        }.start()
    }

    // ---------- Bambu-Drucker: Benachrichtigung bei Druck fertig/Fehler (Hintergrund-Poll der Bridge) ----------
    private fun pollBambu() {
        if (!prefs.bambuNotify || prefs.bambuHttp.isBlank()) return
        val url = prefs.bambuHttp.trim()
        Thread {
            val txt = runCatching {
                val c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
                c.inputStream.bufferedReader().use { it.readText() }.also { runCatching { c.disconnect() } }
            }.getOrNull() ?: return@Thread
            val print = runCatching { JSONObject(txt).optJSONObject("print") }.getOrNull() ?: return@Thread
            val stage = print.optString("gcode_state", "")
            if (stage.isEmpty()) return@Thread
            val job = print.optString("subtask_name", "").ifBlank { t("Druck", "Print") }
            val prev = lastBambuStage
            lastBambuStage = stage
            if (!bambuInit) { bambuInit = true; return@Thread }   // erster Poll: nur Zustand merken
            if (stage == prev) return@Thread
            when {
                stage == "FINISH" && prev != "FINISH" -> notifyBambu(t("✅ Druck fertig", "✅ Print finished"), job)
                stage == "FAILED" && prev != "FAILED" -> notifyBambu(t("⚠️ Druckfehler", "⚠️ Print error"), job)
            }
        }.start()
    }

    private fun notifyBambu(title: String, body: String) {
        handler.post {
            runCatching {
                val nm = getSystemService(NotificationManager::class.java) ?: return@runCatching
                val chId = "bambu"
                if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(chId) == null) {
                    nm.createNotificationChannel(NotificationChannel(chId, t("Drucker", "Printer"), NotificationManager.IMPORTANCE_HIGH))
                }
                val name = prefs.bambuName.ifBlank { "Bambu" }
                val tap = PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                val n = Notification.Builder(this, chId)
                    .setSmallIcon(R.drawable.ic_lumen)
                    .setContentTitle("$name · $title")
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(tap)
                    .build()
                nm.notify(4711, n)
            }
        }
    }

    // ---------- Standby ----------
    private fun checkStandby() {
        val until = sp.getLong("sleep_until", 0L)
        if (until > 0 && System.currentTimeMillis() >= until) {
            sp.edit { putLong("sleep_until", 0L) }
            warnThenStandby()
            return
        }
        val m = prefs.standbyMin
        if (m > 0 && !standbyPending && SystemClock.elapsedRealtime() - lastKey > m * 60_000L) {
            // Läuft Ton (Film, Musik), wird nicht abgeschaltet.
            if (getSystemService(AudioManager::class.java)?.isMusicActive != true) warnThenStandby()
        }
    }

    private fun warnThenStandby() {
        if (standbyPending) return
        standbyPending = true
        toast(t("Standby in 60 Sekunden – beliebige Taste zum Abbrechen", "Standby in 60 seconds – press any key to cancel"))
        handler.postDelayed(pendingStandby, 60_000)
    }

    /** Erst schwarze Standby-Anzeige, dann (ab Android 9) Bildschirmsperre – ob der TV darauf schlafen geht, hängt vom Gerät ab. */
    private fun standbyNow() {
        lastKey = SystemClock.elapsedRealtime()
        bringLumen(force = true) { putExtra(EXTRA_IDLE, true) }
        if (Build.VERSION.SDK_INT >= 28) {
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }, 1_200)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    /** Im Ausschaltmenü automatisch „Neu starten“ anklicken (Menü ist per Fernbedienung oft nicht bedienbar). */
    fun autoClickRestart(tries: Int = 24) {
        val words = Regex("neu\\s*start|restart|reboot|neu\\s*booten", RegexOption.IGNORE_CASE)
        fun find(n: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (n == null) return null
            val txt = (n.text?.toString().orEmpty() + " " + n.contentDescription?.toString().orEmpty())
            if (words.containsMatchIn(txt)) return n
            for (i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        val hit = runCatching { find(rootInActiveWindow) }.getOrNull()
        if (hit != null) {
            var p: android.view.accessibility.AccessibilityNodeInfo? = hit
            while (p != null && !p.isClickable) p = p.parent
            if ((p ?: hit).performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return
        }
        if (tries > 0) handler.postDelayed({ autoClickRestart(tries - 1) }, 80)
    }

    override fun onInterrupt() {}
}
