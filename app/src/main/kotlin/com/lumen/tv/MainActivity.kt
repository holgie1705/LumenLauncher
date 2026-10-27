package com.lumen.tv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val special = intent.hasExtra(EXTRA_LOCKED) || intent.getBooleanExtra(EXTRA_IDLE, false)
        if (savedInstanceState == null) {
            // Autostart-App nur nach dem Einschalten der Box (nicht nach Update/Neuinstallation oder Neustart von Lumen)
            if (!autostartDone && !special) { autostartDone = true; if (android.os.SystemClock.elapsedRealtime() < 5 * 60_000L) runAutostart() }
            handleIntent(intent)
            if (!dotChecked) { dotChecked = true; vm.prefs.value.let { if (it.dotOn) VpnDot.sync(this, it) } }
        }
        setContent {
            val prefs by vm.prefs.collectAsStateWithLifecycle()
            LumenTheme(prefs.font) { HomeScreen(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Vom Service: gesperrte App -> PIN abfragen; Standby -> schwarze Anzeige. */
    private fun handleIntent(i: Intent?) {
        i?.getStringExtra(EXTRA_LOCKED)?.let { vm.lockedApp(it); i.removeExtra(EXTRA_LOCKED) }
        if (i?.getBooleanExtra(EXTRA_IDLE, false) == true) { vm.forceIdle(); i.removeExtra(EXTRA_IDLE) }
    }

    private fun runAutostart() {
        val pkg = SettingsStore(this).prefs.value.autostart
        if (pkg.isEmpty()) return
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
        i?.let { runCatching { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    companion object {
        private var autostartDone = false
        private var dotChecked = false
    }
}
