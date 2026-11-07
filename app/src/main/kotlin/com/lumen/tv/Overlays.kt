package com.lumen.tv

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load

/** Gemeinsame Helfer für Einblendungen über anderen Apps (laufen im Bedienungshilfe-Dienst). */
private fun Context.dp(v: Float) = v * resources.displayMetrics.density
private fun Context.dpi(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun overlayParams(gravity: Int, y: Int) = WindowManager.LayoutParams(
    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT,
).apply { this.gravity = gravity; this.y = y }

private fun Context.label(text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView {
    val face = jakartaFace(this)
    return TextView(this).apply {
        this.text = text; textSize = sizeSp; setTextColor(color); maxLines = 1
        typeface = face; runCatching { setFontVariationSettings(wght(if (bold) 750 else 500)) }
    }
}

// ====================================================================== Lautstärke

/**
 * Futuristischer Neon-Ring: 32 Segmente auf einem 270°-Bogen, je nach Lautstärke von Cyan über Violett nach Pink
 * aufleuchtend (mit Glühen), in der Mitte groß der Wert. Unten in der Lücke: Nachtmodus-Hinweis.
 */
private class NeonVolView(ctx: Context) : View(ctx) {
    private val segs = 32
    private var shown = 0f              // angezeigter Anteil (animiert)
    private var target = 0f
    var muted = false
        set(v) { field = v; invalidate() }
    var capPct = -1                     // Nachtmodus-Grenze in %, -1 = aus
        set(v) { field = v; invalidate() }
    var capHit = false
        set(v) { field = v; invalidate() }
    private var anim: ValueAnimator? = null

    private val d = ctx.resources.displayMetrics.density
    private val bold = jakartaFace(ctx)
    private val med = jakartaFace(ctx)
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f * d; color = 0x224FE3F0 }
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 5.5f * d }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 9f * d
        maskFilter = android.graphics.BlurMaskFilter(9f * d, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold; runCatching { setFontVariationSettings(wght(300)) }; textAlign = Paint.Align.CENTER; color = 0xFFFFFFFF.toInt(); textSize = 50f * d }
    private val numGlow = Paint(num).apply { color = 0xAA4FE3F0.toInt(); maskFilter = android.graphics.BlurMaskFilter(10f * d, android.graphics.BlurMaskFilter.Blur.NORMAL) }
    private val lab = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = med; runCatching { setFontVariationSettings(wght(700)) }; textAlign = Paint.Align.CENTER; textSize = 11f * d; letterSpacing = 0.35f }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }   // nötig für das Glühen (BlurMaskFilter)

    fun setLevel(pct: Float) {
        target = pct.coerceIn(0f, 1f)
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, target).apply {
            duration = 220; addUpdateListener { shown = it.animatedValue as Float; invalidate() }; start()
        }
    }

    /** Farbe entlang des Bogens: Cyan → Violett → Pink. */
    private fun colorAt(f: Float): Int {
        fun mix(a: Int, b: Int, t: Float): Int {
            fun c(x: Int, s: Int) = (x shr s) and 0xFF
            val r = (c(a, 16) + (c(b, 16) - c(a, 16)) * t).toInt(); val g = (c(a, 8) + (c(b, 8) - c(a, 8)) * t).toInt(); val bl = (c(a, 0) + (c(b, 0) - c(a, 0)) * t).toInt()
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
        val cyan = 0x4FE3F0; val violet = 0x8E5CFF; val pink = 0xFF5FA2
        return if (f < 0.6f) mix(cyan, violet, f / 0.6f) else mix(violet, pink, (f - 0.6f) / 0.4f)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) / 2f - 10f * d
        // Glas-Scheibe mit feinem Rand
        disc.shader = android.graphics.RadialGradient(cx, cy - r * 0.3f, r * 1.3f, intArrayOf(0xF2182042.toInt(), 0xF5070A16.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, disc)
        rim.color = if (muted) 0x44FF6B6B else 0x554FE3F0
        c.drawCircle(cx, cy, r, rim)
        c.drawCircle(cx, cy, r * 0.62f, inner)

        // Segmente auf 270° (Lücke unten)
        val ro = r - 12f * d; val ri = ro - 14f * d
        val start = 135.0; val sweep = 270.0
        val lit = if (muted) 0 else Math.round(shown * segs)
        val capSeg = if (capPct in 0..100) Math.round(capPct / 100f * segs) else -1
        for (i in 0 until segs) {
            val a = Math.toRadians(start + sweep * (i + 0.5) / segs)
            val cos = Math.cos(a).toFloat(); val sin = Math.sin(a).toFloat()
            val x1 = cx + ri * cos; val y1 = cy + ri * sin; val x2 = cx + ro * cos; val y2 = cy + ro * sin
            if (i < lit) {
                val col = colorAt(i / (segs - 1f))
                glow.color = (col and 0x00FFFFFF) or 0x99000000.toInt(); c.drawLine(x1, y1, x2, y2, glow)
                seg.color = col
            } else {
                seg.color = if (i == capSeg) 0xCCFFC21A.toInt() else 0x22FFFFFF
            }
            c.drawLine(x1, y1, x2, y2, seg)
        }

        // Wert in der Mitte
        val txt = if (muted) "—" else "${Math.round(target * 100)}"
        val ty = cy + num.textSize * 0.32f
        if (!muted) c.drawText(txt, cx, ty, numGlow)
        c.drawText(txt, cx, ty, num)
        lab.color = if (muted) 0xFFFF6B6B.toInt() else 0xFF4FE3F0.toInt()
        c.drawText(if (muted) t("STUMM", "MUTED") else "VOLUME", cx, ty + 22f * d, lab)
        // Lücke unten: Nachtmodus
        if (capPct in 0..100) {
            lab.color = if (capHit) 0xFFFFC21A.toInt() else 0x99FFFFFF.toInt()
            c.drawText("☾ MAX $capPct", cx, cy + r - 16f * d, lab)
        }
    }
}

/** Neon-Lautstärke rechts mittig über jeder App; poppt kurz auf und blendet weich aus. */
class VolumeOsd(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: NeonVolView? = null
    private val hide = Runnable { fadeOut() }

    fun show(pct: Int, muted: Boolean, capPct: Int?, capHit: Boolean) {
        val fresh = view == null
        val v = view ?: build() ?: return
        v.muted = muted; v.capPct = capPct ?: -1; v.capHit = capHit
        v.setLevel(pct / 100f)
        v.animate().cancel()
        if (fresh) { v.alpha = 0f; v.scaleX = 0.82f; v.scaleY = 0.82f }
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(if (fresh) 200 else 90).start()
        handler.removeCallbacks(hide); handler.postDelayed(hide, 1_900)
    }

    private fun build(): NeonVolView? = runCatching {
        val v = NeonVolView(ctx)
        val size = ctx.dpi(210)
        val lp = overlayParams(Gravity.END or Gravity.CENTER_VERTICAL, 0).apply { width = size; height = size; x = ctx.dpi(44) }
        wm.addView(v, lp)
        view = v
        v
    }.getOrNull()

    private fun fadeOut() {
        val v = view ?: return
        v.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(280).withEndAction { remove() }.start()
    }

    fun remove() {
        handler.removeCallbacks(hide)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}

// ====================================================================== Tor-Alarm

/** Großes Tor-Banner oben mittig über jeder App: Wappen, Ergebnis, pulsierender Rahmen, gleitet ein und aus. */
class GoalOverlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var pulse: ValueAnimator? = null

    fun show(e: GoalEvent) {
        remove()
        runCatching {
            val m = e.match
            val gold = if (e.forMine) 0xFFFFC21A.toInt() else 0xFF9AA3B5.toInt()
            val bg = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xF55A3825.toInt(), 0xF51A1D2B.toInt())).apply {
                cornerRadius = ctx.dp(28f); setStroke(ctx.dpi(3), gold)
            }
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(ctx.dpi(26), ctx.dpi(14), ctx.dpi(28), ctx.dpi(14)); background = bg
            }
            fun crest(url: String) = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (url.isNotEmpty()) load(url) { crossfade(false) } else setImageDrawable(null)
            }
            fun gap(w: Int) = View(ctx).also { card.addView(it, LinearLayout.LayoutParams(ctx.dpi(w), 1)) }
            card.addView(ctx.label(if (e.forMine) "⚽ TOR!" else "⚽ " + t("Gegentor", "Goal against"), 30f, gold, bold = true))
            gap(18)
            card.addView(crest(m.home.icon), LinearLayout.LayoutParams(ctx.dpi(44), ctx.dpi(44)))
            gap(12)
            card.addView(ctx.label("${m.g1 ?: 0}:${m.g2 ?: 0}", 34f, 0xFFFFFFFF.toInt(), bold = true))
            gap(12)
            card.addView(crest(m.away.icon), LinearLayout.LayoutParams(ctx.dpi(44), ctx.dpi(44)))
            gap(16)
            val names = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            names.addView(ctx.label("${m.home.short} – ${m.away.short}", 16f, 0xFFFFFFFF.toInt(), bold = true))
            if (m.lastGoal.isNotEmpty()) names.addView(ctx.label("⚽ " + m.lastGoal, 13f, 0xBFFFFFFF.toInt()))
            card.addView(names)

            wm.addView(card, overlayParams(Gravity.TOP or Gravity.CENTER_HORIZONTAL, ctx.dpi(34)))
            root = card
            card.translationY = -ctx.dp(160f); card.alpha = 0f
            card.animate().translationY(0f).alpha(1f).setDuration(420).start()
            pulse = ValueAnimator.ofInt(90, 255).apply {
                duration = 380; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
                addUpdateListener { a -> bg.setStroke(ctx.dpi(3), (gold and 0x00FFFFFF) or ((a.animatedValue as Int) shl 24)) }
                start()
            }
            handler.postDelayed({
                root?.animate()?.translationY(-ctx.dp(160f))?.alpha(0f)?.setDuration(380)?.withEndAction { remove() }?.start()
            }, 10_000)
        }
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        pulse?.cancel(); pulse = null
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }
}
