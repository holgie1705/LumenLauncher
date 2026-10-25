package com.lumen.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Ergebnis der Länderabfrage (öffentliche IP). */
class VpnCountry(val ip: String, val name: String, val code: String) {
    val flag: String
        get() = if (code.matches(Regex("[A-Z]{2}"))) {
            String(Character.toChars(0x1F1E6 + (code[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (code[1] - 'A')))
        } else ""
}

object VpnDot {
    val SIZES = listOf(8, 10, 12, 14, 16, 18, 20)
    val INTERVAL_OPTIONS = listOf(5, 10, 15, 30, 60, 120, 300)
    val DURATION_OPTIONS = listOf(1, 2, 3, 4, 5, 7, 10)

    fun canOverlay(ctx: Context) = Settings.canDrawOverlays(ctx)

    @Suppress("DEPRECATION")
    fun vpnActive(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return cm.allNetworks.any { n -> cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    fun start(ctx: Context) {
        runCatching { ctx.startForegroundService(Intent(ctx, DotService::class.java)) }
    }

    fun stop(ctx: Context) {
        runCatching { ctx.stopService(Intent(ctx, DotService::class.java)) }
    }

    /** Dienst passend zu den Einstellungen starten/stoppen (und bei Änderungen neu aufbauen). */
    fun sync(ctx: Context, p: Prefs) {
        if (p.dotOn && canOverlay(ctx)) start(ctx) else stop(ctx)
    }

    fun positionName(i: Int) = listOf(
        t("Oben rechts", "Top right"), t("Oben links", "Top left"),
        t("Unten links", "Bottom left"), t("Unten rechts", "Bottom right"),
    )[i.coerceIn(0, 3)]

    suspend fun country(): VpnCountry? = withContext(Dispatchers.IO) {
        var c: HttpURLConnection? = null
        runCatching {
            c = (URL("https://ipapi.co/json/").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 6000; readTimeout = 6000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Lumen-VPN-Dot-Android")
            }
            if (c!!.responseCode != 200) return@runCatching null
            val o = JSONObject(c!!.inputStream.bufferedReader().use { it.readText() })
            val code = o.optString("country_code", "").trim().uppercase(Locale.ROOT)
            var name = if (code.isNotEmpty()) Locale("", code).getDisplayCountry(Lang.locale) else ""
            if (name.isBlank()) name = o.optString("country_name", "").trim()
            VpnCountry(o.optString("ip", "").trim(), name, code)
        }.also { c?.disconnect() }.getOrNull()
    }
}

class DotService : Service() {
    private var wm: WindowManager? = null
    private var dot: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val cycle = Runnable { runCycle() }
    private var hide: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1001, notification())
    }

    private fun notification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("vpn_dot", "VPN-Dot", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, "vpn_dot")
            .setContentTitle("Lumen VPN-Dot")
            .setContentText(t("VPN-Statusanzeige läuft", "VPN status indicator is running"))
            .setSmallIcon(R.drawable.ic_lumen)
            .setOngoing(true)
            .build()
    }

    private fun dp(v: Int) = maxOf(1, (v * resources.displayMetrics.density + 0.5f).toInt())

    private fun cornerGravity(pos: Int) = when (pos) {
        1 -> Gravity.TOP or Gravity.LEFT
        2 -> Gravity.BOTTOM or Gravity.LEFT
        3 -> Gravity.BOTTOM or Gravity.RIGHT
        else -> Gravity.TOP or Gravity.RIGHT
    }

    private fun paint() {
        val d = dot ?: return
        d.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (VpnDot.vpnActive(this@DotService)) Color.rgb(53, 224, 127) else Color.rgb(235, 55, 55))
        }
    }

    private fun removeDot() {
        dot?.let { v -> runCatching { wm?.removeView(v) } }
        dot = null
    }

    private fun createDot(p: Prefs) {
        if (dot != null || !Settings.canDrawOverlays(this)) return
        wm = getSystemService(WINDOW_SERVICE) as? WindowManager ?: return
        val v = View(this)
        dot = v
        paint()
        val size = dp(p.dotSize.coerceIn(8, 20))
        val lp = WindowManager.LayoutParams(
            size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = cornerGravity(p.dotPos); x = dp(14); y = dp(14) }
        try {
            wm?.addView(v, lp)
        } catch (e: Exception) {
            Log.e("LumenDot", "Overlay fehlgeschlagen", e)
            dot = null
        }
    }

    private fun restart() {
        handler.removeCallbacksAndMessages(null)
        hide = null
        removeDot()   // Größe/Position könnten sich geändert haben
        handler.post(cycle)
    }

    private fun runCycle() {
        val p = loadPrefs(this)
        if (!Settings.canDrawOverlays(this)) { handler.postDelayed(cycle, 3000L); return }
        if (dot == null) createDot(p)
        val d = dot
        if (d == null) { handler.postDelayed(cycle, 3000L); return }
        paint()
        d.visibility = View.VISIBLE
        val duration = maxOf(1, p.dotDuration) * 1000L
        val interval = maxOf(5, p.dotInterval) * 1000L
        val h = Runnable {
            dot?.visibility = View.INVISIBLE
            handler.postDelayed(cycle, maxOf(0L, interval - duration))
        }
        hide = h
        handler.postDelayed(h, duration)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        restart()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeDot()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** Startet den Punkt nach dem Booten bzw. nach einem App-Update. */
class DotBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val p = loadPrefs(context)
        if (p.dotOn && p.dotAuto && Settings.canDrawOverlays(context)) VpnDot.start(context)
        // Lumen beim Systemstart öffnen (wie ein Standard-Launcher). Ab Android 10 braucht das die Überlagerungs-Berechtigung.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && p.bootStart &&
            (android.os.Build.VERSION.SDK_INT < 29 || Settings.canDrawOverlays(context))) {
            runCatching {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
        }
    }
}
