package com.lumen.tv

import android.accessibilityservice.AccessibilityService
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
    private var standbyPending = false
    private val swallowed = mutableSetOf<Int>()
    private val longTimers = mutableMapOf<Int, Runnable>()

    private val pendingStandby = Runnable { standbyPending = false; standbyNow() }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "prefs") loadPrefs() }
    private val tick = object : Runnable {
        override fun run() { checkStandby(); handler.postDelayed(this, 30_000) }
    }

    companion object {
        @Volatile var instance: OverrideService? = null
    }

    override fun onServiceConnected() {
        instance = this
        loadPrefs()
        // Zweiter Weg zum Start nach dem Booten: Der Dienst startet mit dem System und darf Lumen nach vorn holen.
        if (prefs.bootStart && SystemClock.elapsedRealtime() < 240_000L) handler.postDelayed({ bringLumen(force = true) }, 2_500)
        sp.registerOnSharedPreferenceChangeListener(prefListener)
        handler.postDelayed(tick, 30_000)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacksAndMessages(null)
        hideMini()
        return super.onUnbind(intent)
    }

    private fun loadPrefs() {
        prefs = runCatching { prefsFromJson(JSONObject(sp.getString("prefs", "{}") ?: "{}")) }.getOrDefault(Prefs())
        if (!prefs.miniOverlay) hideMini() else if (miniWanted) showMini()
    }

    // ---------- Kleine Uhr + Wetter oben links, solange eine andere App läuft ----------
    private var miniView: LinearLayout? = null
    private var miniClock: TextView? = null
    private var miniWeather: TextView? = null
    private var miniWanted = false
    private var lastWeatherFetch = 0L
    private val miniTick = object : Runnable {
        override fun run() { updateMini(); handler.postDelayed(this, 15_000) }
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
        if (prefs.weather == 0) { miniWeather?.visibility = View.GONE; return }
        if (prefs.weatherLat == 0.0 && prefs.weatherLon == 0.0) { miniWeather?.visibility = View.GONE; return }
        val repo = WeatherRepo(this)
        val w = repo.cached(prefs.weatherLat, prefs.weatherLon, prefs.tempF)
        if (w != null) {
            miniWeather?.text = weatherIcon(w.code, w.isDay) + " " + w.temp + "°"
            miniWeather?.visibility = View.VISIBLE
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
        if (miniView == null) {
            val d = resources.displayMetrics.density
            fun tv(size: Float, bold: Boolean) = TextView(this).apply {
                setTextColor(Color.WHITE); textSize = size
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setShadowLayer(6f * d, 0f, 1f * d, Color.argb(200, 0, 0, 0))
            }
            val clock = tv(17f, true)
            val weather = tv(13f, false)
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(clock); addView(weather)
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START; x = (26 * d).toInt(); y = (18 * d).toInt() }
            val ok = runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(box, lp) }.isSuccess
            if (!ok) return
            miniView = box; miniClock = clock; miniWeather = weather
        }
        updateMini()
        handler.removeCallbacks(miniTick)
        handler.postDelayed(miniTick, 15_000)
    }

    private fun hideMini() {
        handler.removeCallbacks(miniTick)
        miniView?.let { v -> runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } }
        miniView = null; miniClock = null; miniWeather = null
    }

    // ---------- Fenster: Kindersicherung + Stock-Launcher ----------
    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) {
            if (e.className?.toString()?.endsWith("MainActivity") == true) { miniWanted = false; hideMini() }
            return
        }
        if (!pkg.contains("systemui") && !pkg.contains("inputmethod") && !pkg.contains("keyboard")) {
            miniWanted = true; showMini()
        }
        if (parental.isBlocked(pkg)) {
            bringLumen { putExtra(EXTRA_LOCKED, pkg) }
        } else if (sp.getBoolean("override", false) && isStockHome(pkg, e.className?.toString()) &&
            System.currentTimeMillis() > sp.getLong("graceUntil", 0L)) {
            bringLumen()
        }
    }

    private fun bringLumen(force: Boolean = false, extra: Intent.() -> Unit = {}) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastLaunch < 1_500) return
        lastLaunch = now
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .apply(extra)
            )
        }
    }

    // ---------- Tasten: Shortcuts + Aktivitätserkennung ----------
    override fun onKeyEvent(e: KeyEvent): Boolean {
        onInput()
        val code = e.keyCode
        val mine = prefs.shortcuts.filter { it.keyCode == code }
        if (mine.isEmpty()) return false
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
    fun autoClickRestart(tries: Int = 14) {
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
        if (tries > 0) handler.postDelayed({ autoClickRestart(tries - 1) }, 350)
    }

    override fun onInterrupt() {}
}
