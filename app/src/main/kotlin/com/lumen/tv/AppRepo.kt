package com.lumen.tv

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

val STOCK_PKGS = listOf(
    "com.google.android.tvlauncher",
    "com.google.android.apps.tv.launcherx",
    "com.google.android.leanbacklauncher",
)

class AppRepo(private val ctx: Context) {
    private val pm = ctx.packageManager
    private val usagePrefs = ctx.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private val accentPrefs = ctx.getSharedPreferences("accents", Context.MODE_PRIVATE)

    private val cache = object : LruCache<String, Art>(24 * 1024) {
        override fun sizeOf(key: String, value: Art): Int = (value.bytes / 1024).coerceAtLeast(1)
    }

    // ---------- Apps ----------
    suspend fun loadApps(): List<TvApp> = withContext(Dispatchers.Default) {
        fun query(cat: String) = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvPkgs = tv.map { it.activityInfo.packageName }.toSet()
        (tv + query(Intent.CATEGORY_LAUNCHER))
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != ctx.packageName }
            .mapNotNull { ri ->
                runCatching {
                    val pkg = ri.activityInfo.packageName
                    val launch = launchIntent(pkg) ?: return@runCatching null
                    val installed = runCatching { pm.getPackageInfo(pkg, 0).firstInstallTime }.getOrDefault(0L)
                    val system = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    TvApp(pkg, ri.loadLabel(pm).toString(), ComponentName(pkg, ri.activityInfo.name), launch, pkg in tvPkgs, installed, system)
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launchIntent(pkg: String): Intent? = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)

    // ---------- Icon-Packs (Standard der Launcher-Welt: ADW/Nova/Apex, appfilter.xml) ----------
    private val packActions = listOf(
        "org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.anddoes.launcher.THEME",
        "com.fede.launcher.THEME_ICONPACK", "com.gau.go.launcherex.theme",
    )

    /** Installierte Icon-Packs als (Paketname, Name). */
    fun iconPacks(): List<Pair<String, String>> {
        val seen = LinkedHashMap<String, String>()
        for (a in packActions) {
            runCatching {
                pm.queryIntentActivities(Intent(a), 0).forEach {
                    val p = it.activityInfo.packageName
                    if (p != ctx.packageName) seen.putIfAbsent(p, it.loadLabel(pm).toString())
                }
            }
        }
        return seen.map { it.key to it.value }.sortedBy { it.second.lowercase() }
    }

    private var packLoaded = ""
    private var packMap: Map<String, String> = emptyMap()   // "pkg/activity" -> Drawable-Name
    private var packByPkg: Map<String, String> = emptyMap() // "pkg" -> Drawable-Name (erster Treffer)
    private var packBacks: List<String> = emptyList()
    private var packMask: String? = null
    private var packUpon: String? = null
    private var packScale = 1f

    private fun loadPack(pack: String) {
        if (pack == packLoaded) return
        packLoaded = pack
        packMap = emptyMap(); packByPkg = emptyMap(); packBacks = emptyList(); packMask = null; packUpon = null; packScale = 1f
        if (pack.isEmpty()) return
        runCatching {
            val res = pm.getResourcesForApplication(pack)
            val parser: org.xmlpull.v1.XmlPullParser = run {
                val id = res.getIdentifier("appfilter", "xml", pack)
                if (id != 0) res.getXml(id)
                else org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser().also {
                    it.setInput(res.assets.open("appfilter.xml"), null)
                }
            }
            val full = HashMap<String, String>()
            val byPkg = HashMap<String, String>()
            val backs = ArrayList<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f
            var ev = parser.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "iconback" -> for (i in 0 until parser.attributeCount) if (parser.getAttributeName(i).startsWith("img")) backs += parser.getAttributeValue(i)
                        "iconmask" -> mask = parser.getAttributeValue(null, "img1")
                        "iconupon" -> upon = parser.getAttributeValue(null, "img1")
                        "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                    }
                }
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val dr = parser.getAttributeValue(null, "drawable")
                    val m = comp?.let { Regex("ComponentInfo\\{([^/}]+)/([^}]+)\\}").find(it) }
                    if (m != null && dr != null) {
                        val (p, act) = m.destructured
                        val cls = if (act.startsWith(".")) p + act else act
                        full["$p/$cls"] = dr
                        byPkg.putIfAbsent(p, dr)
                    }
                }
                ev = parser.next()
            }
            packMap = full; packByPkg = byPkg
            packBacks = backs; packMask = mask; packUpon = upon; packScale = scale.coerceIn(0.3f, 1.2f)
        }
    }

    private fun packIcon(app: TvApp, pack: String): Bitmap? {
        if (pack.isEmpty()) return null
        loadPack(pack)
        val name = packMap[app.component.packageName + "/" + app.component.className] ?: packByPkg[app.pkg]
        return runCatching {
            val res = pm.getResourcesForApplication(pack)
            fun bmp(n: String?): Bitmap? {
                if (n.isNullOrEmpty()) return null
                val id = res.getIdentifier(n, "drawable", pack).takeIf { it != 0 } ?: res.getIdentifier(n, "mipmap", pack)
                return if (id == 0) null else androidx.core.content.res.ResourcesCompat.getDrawable(res, id, null)?.toBitmap(128, 128)
            }
            bmp(name) ?: themedFallback(app, bmp(packBacks.getOrNull(Math.floorMod(app.pkg.hashCode(), packBacks.size.coerceAtLeast(1)))), bmp(packMask), bmp(packUpon))
        }.getOrNull()
    }

    /** Apps ohne Eintrag im Pack: Original-Icon verkleinern, auf den Pack-Hintergrund setzen, mit der Maske zuschneiden. */
    private fun themedFallback(app: TvApp, back: Bitmap?, mask: Bitmap?, upon: Bitmap?): Bitmap? {
        if (back == null && mask == null && upon == null) return null
        val orig = runCatching { pm.getActivityIcon(app.component).toBitmap(128, 128) }.getOrNull() ?: return null
        val out = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        back?.let { c.drawBitmap(it, 0f, 0f, p) }
        val s = (128 * packScale).toInt().coerceAtLeast(16)
        val o = (128 - s) / 2
        c.drawBitmap(orig, null, android.graphics.Rect(o, o, o + s, o + s), p)
        if (mask != null) {
            val mp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            mp.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
            c.drawBitmap(mask, 0f, 0f, mp)
        }
        upon?.let { c.drawBitmap(it, 0f, 0f, p) }
        return out
    }

    suspend fun art(app: TvApp, pack: String = ""): Art {
        val key = "$pack|${app.pkg}"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val themed = packIcon(app, pack)
            val banner: Bitmap? = if (themed != null) null else runCatching {
                (pm.getActivityBanner(app.component) ?: pm.getApplicationBanner(app.pkg))?.toBitmap(384, 216)
            }.getOrNull()
            val icon: Bitmap = themed ?: runCatching { pm.getActivityIcon(app.component).toBitmap(128, 128) }
                .getOrNull() ?: Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val cached = accentPrefs.getInt(app.pkg, 0)
            val argb = if (cached != 0) cached else {
                val p = Palette.from(banner ?: icon).maximumColorCount(12).generate()
                tune(p.getVibrantColor(p.getDominantColor(0xFF4C6FFF.toInt()))).also {
                    accentPrefs.edit { putInt(app.pkg, it) }
                }
            }
            val art = Art(banner?.asImageBitmap(), icon.asImageBitmap(), Color(argb), (banner?.byteCount ?: 0) + icon.byteCount)
            cache.put(key, art)
            art
        }
    }

    private fun tune(argb: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[1] = hsl[1].coerceIn(0.45f, 0.9f)
        hsl[2] = hsl[2].coerceIn(0.40f, 0.60f)
        return ColorUtils.HSLToColor(hsl)
    }

    // ---------- Nutzung ----------
    private fun slotNow() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) / 6

    fun usage(): Usage {
        val all = usagePrefs.all
        val slot = slotNow()
        return Usage(
            count = all.filterKeys { it.startsWith("n:") }.entries.associate { it.key.removePrefix("n:") to (it.value as Int) },
            last = all.filterKeys { it.startsWith("t:") }.entries.associate { it.key.removePrefix("t:") to (it.value as Long) },
            slot = all.filterKeys { it.startsWith("s$slot:") }.entries.associate { it.key.removePrefix("s$slot:") to (it.value as Int) },
        )
    }

    fun bump(pkg: String) {
        val s = slotNow()
        usagePrefs.edit {
            putInt("n:$pkg", usagePrefs.getInt("n:$pkg", 0) + 1)
            putLong("t:$pkg", System.currentTimeMillis())
            putInt("s$s:$pkg", usagePrefs.getInt("s$s:$pkg", 0) + 1)
        }
    }

    // ---------- Eingänge ----------
    fun inputs(): List<InputSource> = runCatching {
        val tim = ctx.getSystemService(TvInputManager::class.java)
        tim.tvInputList.filter { it.isPassthroughInput }.map {
            val label = it.loadCustomLabel(ctx)?.toString() ?: it.loadLabel(ctx).toString()
            InputSource(
                it.id, label,
                Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(it.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }.getOrDefault(emptyList())

    // ---------- Intents aus fremden Quellen (Watch Next, Plugins) entschärfen ----------
    private fun safeIntent(s: String?): Intent? = s?.let {
        runCatching {
            Intent.parseUri(it, Intent.URI_INTENT_SCHEME).apply { selector = null; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }.getOrNull()
    }

    // ---------- Weiter ansehen ----------
    suspend fun watchNext(): List<WatchItem> = withContext(Dispatchers.IO) {
        runCatching {
            val cols = arrayOf("title", "poster_art_uri", "thumbnail_uri", "intent_uri", "duration_millis", "last_playback_position_millis")
            val out = mutableListOf<WatchItem>()
            ctx.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, cols, null, null, "last_engagement_time_utc_millis DESC")?.use { c ->
                while (c.moveToNext() && out.size < 20) {
                    val dur = c.getLong(4)
                    val pos = c.getLong(5)
                    out += WatchItem(
                        c.getString(0).orEmpty(), c.getString(2) ?: c.getString(1), safeIntent(c.getString(3)),
                        if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    )
                }
            }
            out.toList()
        }.getOrDefault(emptyList())
    }

    // ---------- Läuft gerade (Medien-Sitzungen) ----------
    fun notificationAccess(): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    private fun appLabel(pkg: String): String =
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    fun nowPlaying(): List<NowItem> = runCatching {
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java)).map { c ->
            val md = c.metadata
            val label = appLabel(c.packageName)
            NowItem(
                c.packageName,
                md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: label,
                md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: label,
                c.playbackState?.state == PlaybackState.STATE_PLAYING,
                c,
            )
        }
    }.getOrDefault(emptyList())

    // ---------- Plugins ----------
    /**
     * Plugin-Protokoll: Eine App deklariert einen exportierten ContentProvider mit
     * <intent-filter><action android:name="com.lumen.tv.PLUGIN"/></intent-filter>
     * und liefert unter content://<authority>/rows die Spalten: row, title, image, intent.
     */
    suspend fun plugins(): List<PluginRow> = withContext(Dispatchers.IO) {
        runCatching {
            val out = mutableListOf<PluginRow>()
            pm.queryIntentContentProviders(Intent("com.lumen.tv.PLUGIN"), 0).forEach { ri ->
                val auth = ri.providerInfo.authority.split(";").first()
                val groups = linkedMapOf<String, MutableList<WatchItem>>()
                runCatching {
                    ctx.contentResolver.query(Uri.parse("content://$auth/rows"), arrayOf("row", "title", "image", "intent"), null, null, null)?.use { c ->
                        var n = 0
                        while (c.moveToNext() && n < 80) {
                            val row = c.getString(0) ?: ri.loadLabel(pm).toString()
                            groups.getOrPut(row) { mutableListOf() } += WatchItem(c.getString(1).orEmpty(), c.getString(2), safeIntent(c.getString(3)), 0f)
                            n++
                        }
                    }
                }
                groups.forEach { (t, items) -> out += PluginRow("plugin:$auth:$t", t, items.take(20)) }
            }
            out.toList()
        }.getOrDefault(emptyList())
    }

    // ---------- Status ----------
    fun status(): StatusInfo {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        @Suppress("DEPRECATION")
        val vpn = cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        val net = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "LAN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WLAN"
            else -> "Online"
        }
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val ram = if (mi.totalMem > 0) (mi.availMem * 100 / mi.totalMem).toInt() else 0
        val st = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val gb = st?.let { it.availableBytes / 1e9f } ?: 0f
        val freePct = st?.let { if (it.totalBytes > 0) (it.availableBytes * 100 / it.totalBytes).toInt() else 0 } ?: 0
        @Suppress("DEPRECATION")
        val level = runCatching {
            val wm = ctx.applicationContext.getSystemService(WifiManager::class.java)
            WifiManager.calculateSignalLevel(wm.connectionInfo.rssi, 4)
        }.getOrDefault(3)
        return StatusInfo(net, vpn, ram, gb, level, freePct)
    }

    // ---------- Wallpaper (Bilder, GIF, Video) ----------
    private val stillExt = setOf("jpg", "jpeg", "png", "webp")
    private val videoExt = setOf("mp4", "webm", "mkv")

    fun wallFiles(): List<File> =
        ctx.getExternalFilesDir("wallpapers")
            ?.listFiles { f -> f.extension.lowercase().let { it in stillExt || it in videoExt || it == "gif" } }
            ?.sortedBy { it.name } ?: emptyList()

    suspend fun wall(f: File): Wall? = withContext(Dispatchers.IO) {
        when (f.extension.lowercase()) {
            "gif" -> Wall.Anim(f)
            in videoExt -> Wall.Video(f)
            else -> runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                var s = 1
                while (bounds.outWidth / s > 2560) s *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 })
                    ?.asImageBitmap()?.let { Wall.Still(it) }
            }.getOrNull()
        }
    }

    // ---------- Fonts & Themes (Dateien im App-Ordner) ----------
    fun fontFiles(): List<String> =
        ctx.getExternalFilesDir("fonts")?.listFiles { f -> f.extension.lowercase() in setOf("ttf", "otf") }?.map { it.name }?.sorted() ?: emptyList()

    fun themeDir(): File? = ctx.getExternalFilesDir("themes")
    fun themeFiles(): List<File> = themeDir()?.listFiles { f -> f.extension.lowercase() == "json" }?.sortedBy { it.name } ?: emptyList()

    // ---------- Aktionen ----------
    fun start(intent: Intent) {
        runCatching { ctx.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun showDetails(pkg: String) = start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    fun uninstall(pkg: String) = start(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    /** App deaktivieren (reversibel). Ohne Root: Befehle für ADB in Dateien sammeln. */
    suspend fun disablePkg(pkg: String): String = withContext(Dispatchers.IO) {
        val ok = runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", "pm disable-user --user 0 $pkg")).waitFor() == 0 }.getOrDefault(false)
        if (ok) return@withContext t("Deaktiviert: $pkg (rückgängig: pm enable $pkg)", "Disabled: $pkg (undo: pm enable $pkg)")
        val dir = ctx.getExternalFilesDir(null)
        val dis = "adb shell pm disable-user --user 0 $pkg"
        runCatching {
            File(dir, "debloat.bat").appendText("$dis\r\n")
            File(dir, "restore.bat").appendText("adb shell pm enable $pkg\r\n")
        }
        t("Kein Root. Befehl gespeichert (debloat.bat) – am PC ausführen: $dis", "No root. Command saved (debloat.bat) – run on your PC: $dis")
    }

    /** Nur mit Root: Stock-Launcher ein-/ausschalten. */
    suspend fun freezeStock(freeze: Boolean): Boolean = withContext(Dispatchers.IO) {
        STOCK_PKGS.map { pkg ->
            val cmd = if (freeze) "pm disable-user --user 0 $pkg" else "pm enable $pkg"
            runCatching { Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor() == 0 }.getOrDefault(false)
        }.any { it }
    }
}
