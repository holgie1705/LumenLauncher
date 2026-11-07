package com.lumen.tv

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

val APP_VERSION = "2.08"   // bewusst kein const (wird sonst beim inkrementellen Bauen in anderen Dateien „eingebacken“)
private const val TELEGRAM_URL = "https://t.me/HOLGeH"
private const val MAIL = "holgie4711@proton.me"
private const val COFFEE_URL = "https://buymeacoffee.com/holgie"
private const val GITHUB_URL = "https://github.com/holgie1705/LumenLauncher"

/** Einheitlicher Seiteneinzug aller Karten und Zeilen im Info-Dialog (passt zu SettingLine). */
private val EDGE = 12.dp

@Composable
fun InfoDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    var note by remember { mutableStateOf("") }
    var showPrivacy by remember { mutableStateOf(false) }
    var showLegal by remember { mutableStateOf(false) }
    val ramMb = remember {
        runCatching {
            val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            (am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).first().totalPss / 1024)
        }.getOrDefault(0)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() }; delay(60); listState.scrollToItem(0) }

    fun openLink(uri: String, action: String = Intent.ACTION_VIEW) {
        val ok = runCatching { ctx.startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        note = if (ok) "" else t(
            "Auf diesem Gerät gibt es keine App zum Öffnen. Adresse: ", "No app on this device can open this. Address: ",
        ) + uri.removePrefix("mailto:")
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.also { spatialBlur(it) }?.setDimAmount(0f)
        NeoSheet(Ico.INFO, "Info", t("Über Lumen Launcher", "About Lumen Launcher"), width = 760.dp, solid = true) {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ---------- App-Karte: Logo, ein Satz, eine Zeile Kennzahlen ----------
                item { AppCard(ramMb) }

                // ---------- Entwickler ----------
                item { SheetSection(t("Entwickler", "Developer")) }
                item { DevCard() }

                // ---------- Kontakt & Links: alle Zeilen gleich ----------
                item { SheetSection(t("Kontakt & Links", "Contact & links")) }
                item {
                    LinkRow(Color(0xFF2AABEE), { TelegramGlyph() }, "Telegram", "@HOLGeH", Modifier.focusRequester(first)) { openLink(TELEGRAM_URL) }
                }
                item {
                    LinkRow(Color(0xFF7C9CFF), { Text("@", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }, t("E-Mail", "Email"), MAIL) {
                        openLink("mailto:$MAIL", Intent.ACTION_SENDTO)
                    }
                }
                item {
                    LinkRow(Color(0xFF3A3F4B), { Text("</>", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White) }, "GitHub", "holgie1705/LumenLauncher") {
                        openLink(GITHUB_URL)
                    }
                }
                item {
                    LinkRow(Color(0xFFFFDD00), { Image(painterResource(R.drawable.bmc_logo), null, Modifier.size(20.dp)) }, t("Kaffee spendieren", "Buy me a coffee"), "buymeacoffee.com/holgie") {
                        openLink(COFFEE_URL)
                    }
                }
                if (note.isNotEmpty()) item {
                    Text(note, fontSize = 13.sp, color = NeoYellow, modifier = Modifier.padding(horizontal = EDGE + 4.dp, vertical = 2.dp))
                }

                // ---------- Rechtliches: eingeklappt ----------
                item { SheetSection(t("Rechtliches", "Legal")) }
                item {
                    FoldRow(t("Datenschutz & Online-Dienste", "Privacy & online services"), showPrivacy) { showPrivacy = !showPrivacy }
                }
                if (showPrivacy) item {
                    InfoNote(
                        t(
                            "Lumen hat keine Konten, keine Werbung und keine Analyse. Online-Dienste werden nur von den Funktionen angefragt, die du selbst einschaltest – z. B. Wetter (Open-Meteo), Fußball (OpenLigaDB, ESPN), Radio und Wallpaper-Galerie sowie ipapi.co für das Land der öffentlichen IP im VPN-Dot-Menü. Alle Einstellungen bleiben auf dem Gerät.",
                            "Lumen has no accounts, no ads and no analytics. Online services are only contacted by features you switch on yourself – e.g. weather (Open-Meteo), football (OpenLigaDB, ESPN), radio and the wallpaper gallery, plus ipapi.co for your public IP's country in the VPN-dot menu. All settings stay on the device.",
                        ),
                    )
                }
                item {
                    FoldRow(t("Haftungsausschluss", "Disclaimer"), showLegal) { showLegal = !showLegal }
                }
                if (showLegal) item {
                    InfoNote(
                        t(
                            "Lumen Launcher ist ein privates, kostenloses Hobbyprojekt und wird ohne jede Gewährleistung bereitgestellt, so „wie er ist“. Die Nutzung erfolgt auf eigene Gefahr. " +
                                "Soweit gesetzlich zulässig, übernimmt der Entwickler keine Haftung für Schäden, Datenverlust, Fehlfunktionen oder sonstige Folgen der Nutzung. " +
                                "Unberührt bleibt die Haftung für Vorsatz und grobe Fahrlässigkeit, für Schäden an Leben, Körper und Gesundheit sowie nach zwingenden gesetzlichen Vorschriften.\n\n" +
                                "Funktionen wie Root-/ADB-Deaktivierung von Apps, Neustart greifen in dein Gerät ein; nutze sie nur, wenn du die Folgen kennst. " +
                                "Für Inhalte Dritter (Radiostreams, Webseiten in Web-Widgets, heruntergeladene Dateien, Wallpaper) ist allein der Nutzer verantwortlich, ebenso für die Einhaltung von Urheberrecht und Nutzungsbedingungen. " +
                                "Die Kindersicherung ist eine Hilfe und kein garantierter Schutz.\n\n" +
                                "Lumen Launcher steht in keiner Verbindung zu Google, Amazon, AFTVnews, Projectivy, Netflix, Disney+, ESPN oder anderen genannten Marken; alle Namen und Marken gehören ihren jeweiligen Inhabern.",
                            "Lumen Launcher is a private, free hobby project provided without any warranty, “as is”. You use it at your own risk. " +
                                "To the extent permitted by law, the developer accepts no liability for damage, data loss, malfunction or any other consequence of use. " +
                                "Liability for intent and gross negligence, for injury to life, body or health, and under mandatory statutory provisions remains unaffected.\n\n" +
                                "Features such as root/ADB app disabling, restart interfere with your device; use them only if you understand the consequences. " +
                                "The user alone is responsible for third-party content (radio streams, web pages in web widgets, downloaded files, wallpapers) and for complying with copyright and terms of use. " +
                                "Parental controls are an aid, not guaranteed protection.\n\n" +
                                "Lumen Launcher is not affiliated with Google, Amazon, AFTVnews, Projectivy, Netflix, Disney+, ESPN or any other mentioned brand; all names and trademarks belong to their respective owners.",
                        ),
                    )
                }

                item {
                    Row(Modifier.fillMaxWidth().padding(start = EDGE, end = EDGE, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("© Holger Schmidt · GPL-3.0", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f), modifier = Modifier.weight(1f))
                        Pill(t("Schließen", "Close")) { onClose() }
                    }
                }
            }
        }
    }
}

/** Ruhige App-Karte: Logo links, Kurzbeschreibung rechts, darunter eine Zeile Kennzahlen. Nicht fokussierbar. */
@Composable
private fun AppCard(ramMb: Int) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.padding(horizontal = EDGE).fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f))))
            .border(1.dp, NeoLine, shape)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LumenLogo()
            Spacer(Modifier.width(22.dp))
            Box(Modifier.width(1.dp).height(48.dp).background(NeoLine))
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    t("Schneller, schlanker Launcher für Android TV", "Fast, lightweight launcher for Android TV"),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 2,
                )
                Text(
                    t("Ohne Konten · ohne Werbung · ohne Tracking", "No accounts · no ads · no tracking"),
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(t("Version", "Version"), APP_VERSION, accent = true)
            Stat("Android", Build.VERSION.RELEASE)
            Stat(t("Speicher", "Memory"), "$ramMb MB")
        }
    }
}

/** Kleine Kennzahl als flache Kapsel: „VERSION 2.04". */
@Composable
private fun Stat(label: String, value: String, accent: Boolean = false) {
    Row(
        Modifier.background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
            .border(1.dp, if (accent) NeoCyan.copy(alpha = 0.45f) else NeoLine, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label.uppercase(Lang.locale), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.5f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (accent) NeoCyan else Color.White)
    }
}

/** Entwickler-Karte: Avatar, Name, Rolle. Nicht fokussierbar. */
@Composable
private fun DevCard() {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.padding(horizontal = EDGE).fillMaxWidth().clip(shape).background(NeoCard).border(1.dp, NeoLine, shape).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.telegram_avatar), null,
            Modifier.size(52.dp).clip(CircleShape).border(2.dp, NeoCyan.copy(alpha = 0.6f), CircleShape),
            contentScale = ContentScale.Crop,
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("HOLGIE", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(t("Entwickler von Lumen und VPN-Dot", "Developer of Lumen and VPN-Dot"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
    }
}

/** Link-Zeile: farbiges Symbol links, Titel, Wert rechts, Pfeil. Alle Kontakt-Zeilen sehen gleich aus. */
@Composable
private fun LinkRow(
    tint: Color, icon: @Composable () -> Unit, label: String, value: String,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = EDGE).fillMaxWidth().height(56.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = NeoCard, focusedContainerColor = NeoFocusFill),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, NeoLine), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, NeoYellow), shape = shape),
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(14.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            Text(value, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Text("›", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = NeoCyan)
        }
    }
}

/** Auf-/zuklappbare Zeile für längere Texte (Datenschutz, Haftung). */
@Composable
private fun FoldRow(label: String, open: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = EDGE).fillMaxWidth().height(50.dp),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = NeoCard, focusedContainerColor = NeoFocusFill),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, NeoLine), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, NeoYellow), shape = shape),
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                if (open) t("Ausblenden", "Hide") else t("Anzeigen", "Show"),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NeoCyan,
            )
        }
    }
}

/** Längerer Hinweistext in einem dezenten Glaskasten. */
@Composable
private fun InfoNote(text: String) {
    Text(
        text,
        fontSize = 13.sp, color = Color.White.copy(alpha = 0.62f), lineHeight = 19.sp,
        modifier = Modifier.padding(horizontal = EDGE).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f)).border(1.dp, NeoLine, RoundedCornerShape(16.dp)).padding(16.dp),
    )
}

/** Kleiner Papierflieger (Telegram-artig), vektoriell gezeichnet. */
@Composable
private fun TelegramGlyph() {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.05f, h * 0.48f)
            lineTo(w * 0.95f, h * 0.10f)
            lineTo(w * 0.78f, h * 0.90f)
            lineTo(w * 0.50f, h * 0.68f)
            lineTo(w * 0.36f, h * 0.86f)
            lineTo(w * 0.34f, h * 0.62f)
            close()
        }
        drawPath(p, Color.White)
        drawLine(Color(0xFF2AABEE), Offset(w * 0.34f, h * 0.62f), Offset(w * 0.80f, h * 0.24f), strokeWidth = w * 0.06f)
    }
}
