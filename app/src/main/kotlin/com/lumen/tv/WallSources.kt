package com.lumen.tv

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/** Ein Bild aus einer öffentlichen Wallpaper-Quelle. [full] kann leer sein, dann wird es beim Download aufgelöst. */
class RemoteWall(val id: String, val thumb: String, val full: String, val title: String, val credit: String, val source: WallSource)

/** Öffentliche Quellen ohne Konto und ohne API-Schlüssel. */
enum class WallSource(private val de: String, private val en: String, val query: String = "") {
    BING("Bing Tagesbild", "Bing picture of the day"),
    WIKI("Wikimedia (Qualitätsbilder)", "Wikimedia (featured pictures)"),
    NASA("NASA (Weltall)", "NASA (space)"),
    PICSUM("Fotos (Unsplash)", "Photos (Unsplash)"),
    // Wallhaven: große, kuratierte Wallpaper-Sammlung (ohne Konto/Schlüssel, nur jugendfrei)
    WH_NATURE("Natur", "Nature", "nature landscape"),
    WH_SPACE("Weltall & Sterne", "Space & stars", "space galaxy nebula"),
    WH_CITY("Städte & Architektur", "Cities & architecture", "city skyline architecture"),
    WH_MINIMAL("Minimalistisch", "Minimal", "minimal"),
    WH_ABSTRACT("Abstrakt & Muster", "Abstract & patterns", "abstract gradient"),
    WH_DARK("Dunkel & Neon", "Dark & neon", "dark neon synthwave"),
    WH_ANIMALS("Tiere", "Animals", "animals wildlife"),
    WH_CARS("Autos & Technik", "Cars & tech", "cars vehicles"),
    WH_FANTASY("Fantasy & Digital Art", "Fantasy & digital art", "fantasy digital art"),
    WH_SEASONS("Jahreszeiten", "Seasons", "autumn winter forest");
    val label: String get() = t(de, en)
}

object WallSources {
    private const val UA = "Lumen-TV-Launcher/1.2 (Android TV; personal wallpaper browser)"

    fun userAgent() = UA

    private fun open(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000; requestMethod = method
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json,image/*;q=0.9,*/*;q=0.5")
            instanceFollowRedirects = true
        }

    private fun getText(url: String): String? = runCatching {
        val c = open(url)
        try { if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
    }.getOrNull()

    /** Anzahl Seiten, die die Quelle sinnvoll hergibt (Bing zeigt nur die letzten Tage). */
    fun pages(src: WallSource) = if (src == WallSource.BING) 1 else 50

    suspend fun list(src: WallSource, page: Int): List<RemoteWall> = withContext(Dispatchers.IO) {
        runCatching {
            when (src) {
                WallSource.BING -> bing()
                WallSource.WIKI -> wiki(page)
                WallSource.NASA -> nasa(page)
                WallSource.PICSUM -> picsum(page)
                else -> wallhaven(src, page)   // alle WH_*-Quellen
            }
        }.getOrDefault(emptyList())
    }

    /** Wallhaven-Suche (frei, kein Schlüssel; categories=general, purity=sfw, mind. 1920×1080). */
    private fun wallhaven(src: WallSource, page: Int): List<RemoteWall> {
        val q = URLEncoder.encode(src.query, "UTF-8")
        val txt = getText("https://wallhaven.cc/api/v1/search?q=$q&categories=100&purity=100&atleast=1920x1080&sorting=toplist&page=${page + 1}") ?: return emptyList()
        val arr = JSONObject(txt).optJSONArray("data") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val full = o.optString("path"); if (full.isEmpty()) return@mapNotNull null
            val thumb = o.optJSONObject("thumbs")?.optString("small").orEmpty().ifEmpty { full }
            RemoteWall("wh_" + o.optString("id"), thumb, full, src.label, "Wallhaven · " + o.optString("resolution", ""), src)
        }
    }

    private fun bing(): List<RemoteWall> {
        val mkt = if (Lang.en) "en-US" else "de-DE"
        val txt = getText("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=$mkt") ?: return emptyList()
        val arr = JSONObject(txt).getJSONArray("images")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val base = "https://www.bing.com" + o.getString("urlbase")
            RemoteWall(
                o.optString("hsh", base.hashCode().toString()), base + "_320x180.jpg", base + "_1920x1080.jpg",
                o.optString("title", "Bing"), o.optString("copyright", "Bing"), WallSource.BING,
            )
        }
    }

    private fun picsum(page: Int): List<RemoteWall> {
        val txt = getText("https://picsum.photos/v2/list?page=${page + 1}&limit=24") ?: return emptyList()
        val arr = JSONArray(txt)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            RemoteWall(
                "picsum_$id", "https://picsum.photos/id/$id/320/180", "https://picsum.photos/id/$id/1920/1080",
                t("Foto", "Photo") + " #$id", o.optString("author", "Unsplash") + " · Unsplash License", WallSource.PICSUM,
            )
        }
    }

    private fun nasa(page: Int): List<RemoteWall> {
        val q = URLEncoder.encode("galaxy nebula", "UTF-8")
        val txt = getText("https://images-api.nasa.gov/search?q=$q&media_type=image&page=${page + 1}") ?: return emptyList()
        val items = JSONObject(txt).getJSONObject("collection").getJSONArray("items")
        val out = mutableListOf<RemoteWall>()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val d = o.getJSONArray("data").getJSONObject(0)
            val thumb = o.optJSONArray("links")?.optJSONObject(0)?.optString("href").orEmpty()
            if (thumb.isEmpty()) continue
            out += RemoteWall(d.getString("nasa_id"), thumb, "", d.optString("title", "NASA"), "NASA", WallSource.NASA)
            if (out.size >= 24) break
        }
        return out
    }

    private fun wiki(page: Int): List<RemoteWall> {
        val start = LocalDate.of(2020, 1, 1).plusMonths(page * 2L)
        val url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=categorymembers" +
            "&gcmtitle=Category:Featured_pictures_on_Wikimedia_Commons&gcmtype=file&gcmsort=timestamp&gcmdir=newer" +
            "&gcmstart=${start}T00:00:00Z&gcmlimit=40&prop=imageinfo&iiprop=url|size|mime|extmetadata" +
            "&iiextmetadatafilter=Artist|LicenseShortName&iiurlwidth=1920"
        val pages = JSONObject(getText(url) ?: return emptyList()).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
        val out = mutableListOf<RemoteWall>()
        pages.keys().forEach { k ->
            val p = pages.getJSONObject(k)
            val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@forEach
            val mime = ii.optString("mime")
            if (mime != "image/jpeg" && mime != "image/png") return@forEach
            if (ii.optInt("width") < 1920 || ii.optInt("height") < 1000) return@forEach
            val full = ii.optString("thumburl")
            if (!full.contains("/1920px-")) return@forEach
            val meta = ii.optJSONObject("extmetadata")
            val artist = meta?.optJSONObject("Artist")?.optString("value").orEmpty().replace(Regex("<[^>]*>"), "").trim()
            val lic = meta?.optJSONObject("LicenseShortName")?.optString("value").orEmpty()
            val title = p.optString("title").removePrefix("File:").substringBeforeLast('.').replace('_', ' ')
            out += RemoteWall(
                "wiki_" + p.optLong("pageid"), full.replace("/1920px-", "/500px-"), full, title,
                listOf(artist, lic, "Wikimedia Commons").filter { it.isNotEmpty() }.joinToString(" · "), WallSource.WIKI,
            )
        }
        return out.take(24)
    }

    /** NASA: beste brauchbare Datei aus dem Asset-Verzeichnis wählen (Original, wenn nicht riesig). */
    private fun nasaFull(id: String): String? {
        val txt = getText("https://images-api.nasa.gov/asset/${URLEncoder.encode(id, "UTF-8")}") ?: return null
        val items = JSONObject(txt).getJSONObject("collection").getJSONArray("items")
        val urls = (0 until items.length()).map { items.getJSONObject(it).optString("href").replace("http://", "https://") }
        val orig = urls.firstOrNull { it.contains("~orig.jpg", true) }
        if (orig != null) {
            val size = runCatching { val c = open(orig, "HEAD"); try { c.contentLengthLong } finally { c.disconnect() } }.getOrDefault(-1L)
            if (size in 1..12_000_000L) return orig
        }
        return urls.firstOrNull { it.contains("~large.jpg", true) } ?: urls.firstOrNull { it.contains("~medium.jpg", true) } ?: orig
    }

    /** Dateiname-Schlüssel (ohne Endung), den [download] für dieses Bild vergibt. */
    private fun key(w: RemoteWall): String = "dl_" + w.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60)

    /** Entfernt ein bereits geladenes Bild wieder aus dem Wallpaper-Ordner. Rückgabe: true = etwas gelöscht. */
    suspend fun remove(ctx: Context, w: RemoteWall): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dir = ctx.getExternalFilesDir("wallpapers") ?: return@runCatching false
            val k = key(w)
            var any = false
            dir.listFiles()?.forEach { if (File(it.name).nameWithoutExtension == k && it.delete()) any = true }
            any
        }.getOrDefault(false)
    }

    /** Lädt das Bild in den Wallpaper-Ordner. Rückgabe: Datei oder null. */
    suspend fun download(ctx: Context, w: RemoteWall): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = ctx.getExternalFilesDir("wallpapers") ?: return@runCatching null
            dir.mkdirs()
            val url = w.full.ifEmpty { nasaFull(w.id) } ?: return@runCatching null
            val ext = url.substringBefore('?').substringAfterLast('.', "jpg").lowercase().let { if (it in setOf("jpg", "jpeg", "png", "webp")) it else "jpg" }
            val name = "dl_" + w.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(60) + "." + ext
            val target = File(dir, name)
            if (target.exists() && target.length() > 0) return@runCatching target
            val tmp = File(dir, "$name.part")
            val c = open(url)
            try {
                if (c.responseCode != 200) return@runCatching null
                c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally { c.disconnect() }
            if (tmp.length() < 10_000 || !tmp.renameTo(target)) { tmp.delete(); return@runCatching null }
            target
        }.getOrNull()
    }
}
