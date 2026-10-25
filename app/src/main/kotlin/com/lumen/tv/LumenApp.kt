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
    }
}

/** Liest die gespeicherten Einstellungen ohne ViewModel (für Services und Receiver). */
fun loadPrefs(ctx: Context): Prefs = runCatching {
    val sp = ctx.getSharedPreferences("lumen", Context.MODE_PRIVATE)
    prefsFromJson(JSONObject(sp.getString("prefs", "{}") ?: "{}"))
}.getOrDefault(Prefs())
