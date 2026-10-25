package com.lumen.tv

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import java.io.File

val Ink = Color(0xFF07080C)
val DefaultAccent = Color(0xFF4C6FFF)

val ACCENTS: List<Pair<String, Color?>> = listOf(
    "Auto (App-Farbe)" to null,
    "Blau" to Color(0xFF4C6FFF),
    "Violett" to Color(0xFF8E5CFF),
    "Grün" to Color(0xFF2FBF71),
    "Rot" to Color(0xFFE5484D),
    "Orange" to Color(0xFFF5A524),
    "Pink" to Color(0xFFEC4899),
)

private val ACCENT_EN = listOf("Auto (app color)", "Blue", "Violet", "Green", "Red", "Orange", "Pink")
fun accentName(i: Int): String = if (Lang.en) ACCENT_EN[i.coerceIn(0, ACCENT_EN.lastIndex)] else ACCENTS[i.coerceIn(0, ACCENTS.lastIndex)].first

/** Mitgelieferte Standardschrift (Bai Jamjuree, SIL Open Font License) – eckig-rund, nah am Projectivy-Look. */
val LumenSans = FontFamily(
    Font(R.font.baijamjuree_light, FontWeight.Light),
    Font(R.font.baijamjuree_regular, FontWeight.Normal),
    Font(R.font.baijamjuree_medium, FontWeight.Medium),
    Font(R.font.baijamjuree_bold, FontWeight.Bold),
)

/** Variable Schrift (eine Datei, Gewichte 300–700) – klein im Speicher. */
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(res: Int) = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w -> Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w))) }
)
private val OutfitFamily by lazy { variableFamily(R.font.outfit) }
private val JakartaFamily by lazy { variableFamily(R.font.jakarta) }
private val ManropeFamily by lazy { variableFamily(R.font.manrope) }
private val SoraFamily by lazy { variableFamily(R.font.sora) }

fun fontFamilyFor(ctx: Context, key: String): FontFamily = when {
    key == "outfit" -> OutfitFamily
    key == "jakarta" -> JakartaFamily
    key == "manrope" -> ManropeFamily
    key == "sora" -> SoraFamily
    key == "ui" -> LumenSans
    key == "serif" -> FontFamily.Serif
    key == "mono" -> FontFamily.Monospace
    key == "condensed" -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    key.startsWith("file:") -> runCatching {
        FontFamily(Typeface.createFromFile(File(ctx.getExternalFilesDir("fonts"), key.removePrefix("file:"))))
    }.getOrDefault(FontFamily.Default)
    else -> FontFamily.Default
}

fun fontLabel(key: String): String = when {
    key == "ui" -> "Lumen (Bai Jamjuree)"
    key == "system" -> t("System", "System")
    key == "serif" -> "Serif"
    key == "mono" -> "Monospace"
    key == "outfit" -> "Outfit"
    key == "jakarta" -> "Plus Jakarta Sans"
    key == "manrope" -> "Manrope"
    key == "sora" -> "Sora"
    key == "condensed" -> t("Schmal", "Condensed")
    key.startsWith("file:") -> key.removePrefix("file:")
    else -> t("Standard", "Default")
}

@Composable
fun LumenTheme(font: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val family = remember(font) { fontFamilyFor(ctx, font) }
    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Ink)) {
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = family)),
            content = content,
        )
    }
}

/** Wendet eine eigene Schrift nur auf einen Teilbereich an (z. B. Menüs / Kopfzeile). Leer = unverändert. */
@Composable
fun FontScope(key: String, content: @Composable () -> Unit) {
    if (key.isEmpty()) { content(); return }
    val ctx = LocalContext.current
    val family = remember(key) { fontFamilyFor(ctx, key) }
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = family)),
        content = content,
    )
}
