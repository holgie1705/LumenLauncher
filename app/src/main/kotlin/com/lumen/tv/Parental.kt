package com.lumen.tv

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar

@Immutable
class ParentalState(
    val on: Boolean, val hasPin: Boolean, val fromH: Int, val toH: Int,
    val kids: Boolean, val blocked: Set<String>, val allowed: Set<String>,
)

/**
 * Kindersicherung. Wird sowohl von der App als auch vom Accessibility-Service benutzt
 * (liest direkt aus den SharedPreferences, daher kein Zustand im Objekt).
 */
class Parental(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("parental", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<ParentalState> = _state.asStateFlow()

    private fun set(key: String): Set<String> = sp.getStringSet(key, null)?.toSet() ?: emptySet()

    private fun read() = ParentalState(
        on = sp.getBoolean("on", false), hasPin = sp.contains("hash"),
        fromH = sp.getInt("from", 22), toH = sp.getInt("to", 6),
        kids = sp.getBoolean("kids", false), blocked = set("blocked"), allowed = set("allowed"),
    )

    private fun write(f: android.content.SharedPreferences.Editor.() -> Unit) {
        sp.edit(commit = true) { f() }
        _state.value = read()
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        write { putString("salt", salt); putString("hash", hash(salt, pin)) }
    }

    fun verify(pin: String): Boolean {
        val salt = sp.getString("salt", null) ?: return false
        return hash(salt, pin) == sp.getString("hash", null)
    }

    fun setOn(v: Boolean) = write { putBoolean("on", v) }
    fun setKids(v: Boolean) = write { putBoolean("kids", v) }
    fun setHours(from: Int, to: Int) = write { putInt("from", from); putInt("to", to) }
    fun toggleBlocked(pkg: String) { val s = set("blocked"); write { putStringSet("blocked", if (pkg in s) s - pkg else s + pkg) } }
    fun toggleAllowed(pkg: String) { val s = set("allowed"); write { putStringSet("allowed", if (pkg in s) s - pkg else s + pkg) } }
    fun unlock(minutes: Int) = write { putLong("unlock", System.currentTimeMillis() + minutes * 60_000L) }

    /** Alle Apps, die überhaupt im Launcher auftauchen – damit der Kindermodus nur diese sperrt, keine Systemfenster. */
    fun setLaunchable(pkgs: Set<String>) { if (pkgs != set("launchable")) write { putStringSet("launchable", pkgs) } }

    private fun timeActive(): Boolean {
        val from = sp.getInt("from", 22)
        val to = sp.getInt("to", 6)
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (from == to) true else if (from < to) h in from until to else (h >= from || h < to)
    }

    fun isBlocked(pkg: String): Boolean {
        if (pkg == ctx.packageName) return false
        if (System.currentTimeMillis() < sp.getLong("unlock", 0L)) return false
        if (sp.getBoolean("kids", false) && pkg in set("launchable") && pkg !in set("allowed")) return true
        return sp.getBoolean("on", false) && pkg in set("blocked") && timeActive()
    }
}
