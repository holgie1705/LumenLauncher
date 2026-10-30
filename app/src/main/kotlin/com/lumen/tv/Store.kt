package com.lumen.tv

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class SettingsStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()

    private val _override = MutableStateFlow(sp.getBoolean("override", false))
    val overrideOn: StateFlow<Boolean> = _override.asStateFlow()

    private val _sleep = MutableStateFlow(sp.getLong("sleep_until", 0L))
    val sleepUntil: StateFlow<Long> = _sleep.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        if (key == "sleep_until") _sleep.value = p.getLong("sleep_until", 0L)
    }

    init {
        sp.registerOnSharedPreferenceChangeListener(listener)
        Lang.apply(_prefs.value.lang)
    }

    private fun load(): Prefs {
        val raw = sp.getString("prefs", null)
        // Frische Installation (noch nie Prefs gespeichert): Sprachauswahl beim Erststart zeigen.
        if (raw == null) return Prefs()
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return Prefs()
        val p = runCatching { prefsFromJson(o) }.getOrDefault(Prefs())
        // Bestehende Installation ohne das Flag -> als „bereits gewählt" behandeln, damit kein Dialog aufpoppt.
        return p.copy(langPicked = o.optBoolean("langPicked", true))
    }

    fun update(f: (Prefs) -> Prefs) {
        val n = f(_prefs.value)
        Lang.apply(n.lang)   // vor dem Flow-Update, damit abgeleitete Listen schon die neue Sprache nutzen
        _prefs.value = n
        sp.edit { putString("prefs", n.toJson().toString()) }
    }

    fun setOverride(v: Boolean) {
        _override.value = v
        sp.edit { putBoolean("override", v) }
    }

    /** Wird vom Accessibility-Service ausgewertet (Einschlaf-Timer). 0 = aus. */
    fun setSleep(untilMs: Long) {
        _sleep.value = untilMs
        sp.edit { putLong("sleep_until", untilMs) }
    }

    fun export(): String = _prefs.value.toJson().toString(2)

    fun import(s: String): Boolean = runCatching { update { prefsFromJson(JSONObject(s)) }; true }.getOrDefault(false)
}
