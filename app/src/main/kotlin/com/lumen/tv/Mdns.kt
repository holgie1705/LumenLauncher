package com.lumen.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Gerätenamen im Heimnetz über mDNS/Bonjour (ohne Root, ohne Konto). Viele Geräte geben hier einen Namen bekannt:
 * Chromecast/Android-TV, Apple-TV/HomePod, iPhones/iPads, Drucker, Sonos, NAS, PCs.
 * Liefert IP -> Name. Best effort: Geräte, die nichts bekanntgeben, erscheinen nicht.
 */
object Mdns {
    private val TYPES = listOf(
        "_googlecast._tcp.", "_androidtvremote2._tcp.", "_airplay._tcp.", "_raop._tcp.",
        "_companion-link._tcp.", "_apple-mobdev2._tcp.", "_spotify-connect._tcp.", "_sonos._tcp.",
        "_printer._tcp.", "_ipp._tcp.", "_workstation._tcp.", "_hap._tcp.", "_smb._tcp.", "_http._tcp.",
    )

    private fun attr(info: NsdServiceInfo, key: String): String? =
        runCatching { info.attributes?.get(key)?.let { String(it, Charsets.UTF_8) } }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun clean(name: String): String {
        var n = name.substringBefore(".").trim()
        // "AABBCCDDEEFF@Wohnzimmer" (AirPlay/raop) -> hinterer Teil
        if (n.contains('@')) n = n.substringAfter('@')
        n = n.replace(Regex("\\\\([0-9]{3})")) { m -> (m.groupValues[1].toIntOrNull() ?: 32).toChar().toString() }  // \032 -> Leerzeichen
        return n.trim().take(28)
    }

    private fun nameOf(info: NsdServiceInfo): String {
        // Chromecast/Google: Freundlichername im TXT-Feld "fn"
        attr(info, "fn")?.let { return clean(it) }
        attr(info, "md")?.let { md -> return clean(info.serviceName).ifEmpty { clean(md) } }
        return clean(info.serviceName)
    }

    @Suppress("DEPRECATION")   // resolveService ist veraltet, funktioniert aber ab Android 8; die neue API gibt es erst ab 14
    suspend fun names(ctx: Context, totalMs: Long = 4500): Map<String, String> = withContext(Dispatchers.IO) {
        val nsd = runCatching { ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull() ?: return@withContext emptyMap()
        val found = ConcurrentLinkedQueue<NsdServiceInfo>()
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()
        TYPES.forEach { type ->
            val l = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(s: String?, e: Int) {}
                override fun onStopDiscoveryFailed(s: String?, e: Int) {}
                override fun onDiscoveryStarted(s: String?) {}
                override fun onDiscoveryStopped(s: String?) {}
                override fun onServiceFound(info: NsdServiceInfo) { found.add(info) }
                override fun onServiceLost(info: NsdServiceInfo) {}
            }
            if (runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, l) }.isSuccess) listeners.add(l)
        }
        // Suchfenster, dann Auflösung (resolve ist seriell, daher nacheinander)
        delay((totalMs * 0.55).toLong())
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }

        val out = LinkedHashMap<String, String>()
        val deadline = System.currentTimeMillis() + (totalMs * 0.45).toLong()
        val seen = HashSet<String>()
        for (info in found) {
            if (System.currentTimeMillis() > deadline) break
            val id = info.serviceName + "|" + info.serviceType
            if (!seen.add(id)) continue
            val done = CompletableDeferred<Pair<String, String>?>()
            val rl = object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo?, e: Int) { done.complete(null) }
                override fun onServiceResolved(s: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val ip = (s.host as? Inet4Address)?.hostAddress
                    done.complete(if (ip != null) ip to nameOf(s) else null)
                }
            }
            if (runCatching { nsd.resolveService(info, rl) }.isFailure) continue
            val r = withTimeoutOrNull(700) { done.await() }
            if (r != null && r.second.isNotBlank() && out[r.first].isNullOrBlank()) out[r.first] = r.second
        }
        out
    }
}
