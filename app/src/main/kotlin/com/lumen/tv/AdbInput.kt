package com.lumen.tv

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Schickt Tastendrücke per ADB an die Box – so, wie es atvtools vom iPhone aus macht.
 * Voraussetzung: „ADB über Netzwerk / Debugging" ist an der Box aktiv und Lumens Schlüssel wurde einmal bestätigt.
 * Ohne Root, ohne Shizuku.
 */
object AdbCtl {
    /** Eigener, dauerhaft gespeicherter ADB-Schlüssel (damit die Box „immer erlauben" merken kann). */
    private fun keyPair(ctx: Context): AdbKeyPair {
        val priv = File(ctx.filesDir, "adbkey"); val pub = File(ctx.filesDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    /** Ziel-Host: eingestellte IP, sonst die eigene WLAN/LAN-IP der Box (localhost klappt bei adbd oft nicht). */
    fun targetHost(prefs: Prefs): String = prefs.adbHost.ifBlank { ownIp() ?: "127.0.0.1" }

    private fun ownIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    /** Führt einen Shell-Befehl über ADB aus. Rückgabe: Ausgabe oder null bei Fehler. */
    suspend fun shell(ctx: Context, prefs: Prefs, cmd: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            Dadb.create(targetHost(prefs), prefs.adbPort.takeIf { it in 1..65535 } ?: 5555, keyPair(ctx)).use { d ->
                d.shell(cmd).output
            }
        }.getOrNull()
    }

    /** Testverbindung: liefert eine kurze Rückmeldung für die Einstellungen. */
    suspend fun test(ctx: Context, prefs: Prefs): String = withContext(Dispatchers.IO) {
        val host = targetHost(prefs)
        runCatching {
            Dadb.create(host, prefs.adbPort.takeIf { it in 1..65535 } ?: 5555, keyPair(ctx)).use { d ->
                d.shell("echo ok").output.trim()
            }
        }.fold(
            onSuccess = { if (it.contains("ok")) t("Verbunden mit $host ✓", "Connected to $host ✓") else t("Antwort: $it", "Response: $it") },
            onFailure = { t("Keine Verbindung zu $host. ADB-Netzwerk an der Box aktiv? Schlüssel bestätigt?", "No connection to $host. Is ADB-over-network on and the key approved?") },
        )
    }

    /** Sendet eine Ziffernfolge (z. B. „155") als echte Tastendrücke, optional mit Enter/OK danach. */
    suspend fun sendNumber(ctx: Context, prefs: Prefs, digits: String, confirm: Boolean): Boolean {
        val codes = digits.filter { it.isDigit() }.map { 7 + (it - '0') }   // KEYCODE_0 = 7 … KEYCODE_9 = 16
        if (codes.isEmpty()) return false
        val seq = (codes + if (confirm) listOf(66) else emptyList()).joinToString(" ")   // 66 = ENTER/OK
        return shell(ctx, prefs, "input keyevent $seq") != null
    }
}
