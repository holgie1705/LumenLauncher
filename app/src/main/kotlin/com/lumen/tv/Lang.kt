package com.lumen.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Globale Sprachumschaltung. Der Zustand ist ein Compose-Snapshot-State: Jede Stelle, die
 * [t] in einer Composable liest, wird beim Umschalten automatisch neu gezeichnet.
 */
object Lang {
    private var enState by mutableStateOf(false)
    val en: Boolean get() = enState
    val locale: Locale get() = if (enState) Locale.ENGLISH else Locale.GERMAN
    val code: String get() = if (enState) "en" else "de"

    /** mode: 0 = Deutsch, 1 = English, 2 = System */
    fun apply(mode: Int) {
        val v = when (mode) {
            1 -> true
            2 -> Locale.getDefault().language != "de"
            else -> false
        }
        if (v != enState) enState = v
    }
}

fun t(de: String, en: String): String = if (Lang.en) en else de
