package com.lumen.tv

import android.app.Application
import android.content.Context
import org.json.JSONObject

class LumenApp : Application(), coil.ImageLoaderFactory {
    /** Globaler Bild-Lader mit kleinem Cache (8 % statt 25 % des App-Speichers) und 16-Bit-Bitmaps -> deutlich weniger RAM. */
    override fun newImageLoader() = coil.ImageLoader.Builder(this)
        .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.08).build() }
        .allowRgb565(true)
        .components { add(coil.decode.SvgDecoder.Factory()) }
        .crossfade(false)
        .build()

    override fun onCreate() {
        super.onCreate()
        // Sprache früh setzen, damit auch Service/Toasts die richtige Sprache nutzen.
        Lang.apply(loadPrefs(this).lang)
        GoalWatch.start(this)
    }

    /** Wenn Android Speicher braucht: Bild-Cache freigeben (lädt bei Bedarf neu – keine schlechtere Qualität). */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Ab „moderat“ (App im Hintergrund) oder laufender Speichernot den Bild-Cache leeren.
        if (level >= TRIM_MEMORY_RUNNING_LOW || level >= TRIM_MEMORY_BACKGROUND) {
            runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
        }
    }
}

/** Liest die gespeicherten Einstellungen ohne ViewModel (für Services und Receiver). */
fun loadPrefs(ctx: Context): Prefs = runCatching {
    val sp = ctx.getSharedPreferences("lumen", Context.MODE_PRIVATE)
    prefsFromJson(JSONObject(sp.getString("prefs", "{}") ?: "{}"))
}.getOrDefault(Prefs())
