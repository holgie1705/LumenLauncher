package com.lumen.tv

import android.content.Context
import android.os.PowerManager
import android.accessibilityservice.AccessibilityService
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * Gerät neu starten. Android erlaubt normalen Apps das nicht direkt, daher der Reihe nach:
 * 1) Root (su -c reboot)  2) PowerManager.reboot (nur mit Systemrechten)  3) Android-Ausschaltmenü über den Bedienungshilfen-Dienst.
 */
object DeviceRestart {
    fun run(ctx: Context) {
        val app = ctx.applicationContext
        thread(name = "restart") {
            val root = runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot")).waitFor() == 0 }.getOrDefault(false)
            if (root) return@thread
            val sys = runCatching { (app.getSystemService(Context.POWER_SERVICE) as PowerManager).reboot(null); true }.getOrDefault(false)
            if (sys) return@thread
            val svc = OverrideService.instance
            val menu = svc?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) == true
            if (menu) android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ svc?.autoClickRestart() }, 180)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(
                    app,
                    if (menu) t("Neustart wird ausgelöst …", "Restarting …")
                    else t("Neustart nicht möglich: Bedienungshilfe „Lumen“ aktivieren (Einstellungen → System) oder Gerät rooten.",
                        "Restart not possible: enable the Lumen accessibility service (Settings → System) or root the device."),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
