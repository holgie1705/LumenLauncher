package com.lumen.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Globale Sprachumschaltung. Der Zustand ist ein Compose-Snapshot-State: Jede Stelle, die
 * [t] in einer Composable liest, wird beim Umschalten automatisch neu gezeichnet.
 *
 * Deutsch und Englisch stehen direkt im Code (t("de", "en")). Italienisch, Französisch und
 * Spanisch kommen aus assets/i18n/<code>.tsv (Schlüssel = deutscher Text). Fehlt ein Eintrag,
 * wird Englisch angezeigt.
 */
object Lang {
    /** Sprachen in der Reihenfolge, in der die Einstellung durchgeschaltet wird (Wert = Prefs.lang). */
    val ORDER = listOf(0, 1, 3, 4, 5, 2)
    fun name(mode: Int): String = when (mode) {
        0 -> "Deutsch"; 1 -> "English"; 3 -> "Italiano"; 4 -> "Français"; 5 -> "Español"
        else -> t("System", "System")
    }
    fun next(mode: Int): Int = ORDER[(ORDER.indexOf(mode).coerceAtLeast(0) + 1) % ORDER.size]

    private var codeState by mutableStateOf("de")
    val code: String get() = codeState
    /** true für jede Sprache außer Deutsch (Fallback-Texte sind Englisch). */
    val en: Boolean get() = codeState != "de"
    val locale: Locale get() = when (codeState) {
        "de" -> Locale.GERMAN; "en" -> Locale.ENGLISH; "it" -> Locale.ITALIAN; "fr" -> Locale.FRENCH
        else -> Locale(codeState)
    }

    @Volatile private var ctx: Context? = null
    fun init(c: Context) { ctx = c.applicationContext }

    /** mode: 0 = Deutsch, 1 = English, 2 = System, 3 = Italiano, 4 = Français, 5 = Español */
    fun apply(mode: Int) {
        val v = when (mode) {
            0 -> "de"; 1 -> "en"; 3 -> "it"; 4 -> "fr"; 5 -> "es"
            2 -> Locale.getDefault().language.let { if (it in listOf("de", "en", "it", "fr", "es")) it else "en" }
            else -> "de"
        }
        if (v != codeState) {
            synchronized(this) { cache.clear() }
            codeState = v
        }
    }

    // ------------------------------------------------------------ Übersetzungstabellen
    private class Tpl(val re: Regex, val names: List<String>, val out: String)
    private class Table(val map: Map<String, String>, val tpls: List<Tpl>)

    private val PH = Regex("""\$\{(?:[^{}]|\{[^{}]*\})*\}|\$[A-Za-z_][A-Za-z0-9_]*""")
    private val tables = HashMap<String, Table>()
    private val cache = HashMap<String, String>()

    private fun unesc(s: String): String {
        if (!s.contains('\\')) return s
        val b = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 'n' -> b.append('\n'); 't' -> b.append('\t'); '\\' -> b.append('\\'); else -> { b.append(c); b.append(s[i + 1]) } }
                i += 2
            } else { b.append(c); i++ }
        }
        return b.toString()
    }

    private fun load(code: String): Table {
        tables[code]?.let { return it }
        val map = HashMap<String, String>(1200)
        val tpls = ArrayList<Tpl>()
        runCatching {
            ctx?.assets?.open("i18n/$code.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
                lines.forEach { line ->
                    val tab = line.indexOf('\t')
                    if (tab > 0) {
                        val k = unesc(line.substring(0, tab)); val v = unesc(line.substring(tab + 1))
                        map[k] = v
                        val ph = PH.findAll(k).map { it.value }.toList()
                        if (ph.isNotEmpty()) {
                            val parts = k.split(PH)
                            val pat = parts.joinToString("(.*?)") { Regex.escape(it) }
                            tpls += Tpl(Regex("^$pat$", RegexOption.DOT_MATCHES_ALL), ph, v)
                        }
                    }
                }
            }
        }
        // Erst bei vorhandenem Kontext merken – sonst später erneut versuchen.
        val t = Table(map, tpls)
        if (ctx != null) tables[code] = t
        return t
    }

    @Synchronized
    fun lookup(code: String, de: String): String? {
        cache[de]?.let { return it }
        val tb = load(code)
        var r = tb.map[de]
        if (r == null) {
            for (tp in tb.tpls) {
                val m = tp.re.matchEntire(de) ?: continue
                val vals = HashMap<String, String>()
                tp.names.forEachIndexed { i, n -> vals.putIfAbsent(n, m.groupValues[i + 1]) }
                r = PH.replace(tp.out) { vals[it.value] ?: it.value }
                break
            }
        }
        if (r != null) {
            if (cache.size > 3000) cache.clear()
            cache[de] = r
        }
        return r
    }
}

fun t(de: String, en: String): String = when (val c = Lang.code) {
    "de" -> de
    "en" -> en
    else -> Lang.lookup(c, de) ?: en
}
