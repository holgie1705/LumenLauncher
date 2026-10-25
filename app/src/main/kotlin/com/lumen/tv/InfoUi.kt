package com.lumen.tv

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

const val APP_VERSION = "1.43"
private const val TELEGRAM_URL = "https://t.me/HOLGeH"
private const val MAIL = "holgie4711@proton.me"
private const val COFFEE_URL = "https://buymeacoffee.com/holgie"

@Composable
fun InfoDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    var note by remember { mutableStateOf("") }
    val ramMb = remember {
        runCatching {
            val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            (am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).first().totalPss / 1024)
        }.getOrDefault(0)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Fokus auf den ersten Knopf, aber den Kopf (Logo, Version) oben sichtbar lassen.
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() }; delay(60); listState.scrollToItem(0) }

    fun openLink(uri: String, action: String = Intent.ACTION_VIEW) {
        val ok = runCatching { ctx.startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        note = if (ok) "" else t(
            "Auf diesem Gerät gibt es keine App zum Öffnen. Adresse: ", "No app on this device can open this. Address: ",
        ) + uri.removePrefix("mailto:")
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.98f))) {
            LazyColumn(
                Modifier.align(Alignment.Center).width(800.dp).fillMaxHeight(),
                state = listState,
                contentPadding = PaddingValues(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Kopf ist fokussierbar: so scrollt die Liste mit „Hoch“ wirklich bis ganz nach oben.
                item {
                    var headFocused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    Column(
                        Modifier.fillMaxWidth()
                            .onFocusChanged { headFocused = it.hasFocus }
                            .focusable()
                            .background(Color.White.copy(alpha = if (headFocused) 0.12f else 0.06f), RoundedCornerShape(14.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Info", fontSize = 30.sp, fontWeight = FontWeight.W300, color = Color.White)
                        LumenLogo(Modifier.padding(bottom = 8.dp))
                        Text("Version $APP_VERSION", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        Text(
                            t("Arbeitsspeicher von Lumen: ca. $ramMb MB", "Lumen memory use: about $ramMb MB"),
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f),
                        )
                        Text(
                            t("Launcher für Android TV – inklusive VPN-Dot.", "Launcher for Android TV – including the VPN dot."),
                            fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }
                item { Section2(t("Kontakt", "Contact")) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painterResource(R.drawable.telegram_avatar), null,
                            Modifier.size(72.dp).clip(CircleShape), contentScale = ContentScale.Crop,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("HOLGIE", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text(t("Entwickler von Lumen und VPN-Dot", "Developer of Lumen and VPN-Dot"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                item { SettingLine("Telegram", "@HOLGeH", Modifier.focusRequester(first)) { openLink(TELEGRAM_URL) } }
                item { SettingLine(t("E-Mail", "Email"), MAIL) { openLink("mailto:$MAIL", Intent.ACTION_SENDTO) } }
                item { Section2(t("Unterstützen", "Support")) }
                item {
                    SettingLine(t("Spendier mir einen Kaffee", "Buy me a coffee"), "buymeacoffee.com/holgie") { openLink(COFFEE_URL) }
                }
                item {
                    Box(Modifier.background(Color(0xFFFFDD00), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Image(painterResource(R.drawable.bmc_logo), "Buy Me a Coffee", Modifier.size(32.dp))
                    }
                }
                item { Section2(t("Haftungsausschluss & Nutzung auf eigene Gefahr", "Disclaimer & use at your own risk")) }
                item {
                    Text(
                        t(
                            "Lumen Launcher ist ein privates, kostenloses Hobbyprojekt und wird ohne jede Gewährleistung bereitgestellt, so „wie er ist“. Die Nutzung erfolgt auf eigene Gefahr. " +
                                "Soweit gesetzlich zulässig, übernimmt der Entwickler keine Haftung für Schäden, Datenverlust, Fehlfunktionen oder sonstige Folgen der Nutzung. " +
                                "Unberührt bleibt die Haftung für Vorsatz und grobe Fahrlässigkeit, für Schäden an Leben, Körper und Gesundheit sowie nach zwingenden gesetzlichen Vorschriften.\n\n" +
                                "Funktionen wie Root-/ADB-Deaktivierung von Apps, Neustart greifen in dein Gerät ein; nutze sie nur, wenn du die Folgen kennst. " +
                                "Für Inhalte Dritter (Radiostreams, Webseiten in Web-Widgets, heruntergeladene Dateien, Wallpaper) ist allein der Nutzer verantwortlich, ebenso für die Einhaltung von Urheberrecht und Nutzungsbedingungen. " +
                                "Die Kindersicherung ist eine Hilfe und kein garantierter Schutz.\n\n" +
                                "Lumen Launcher steht in keiner Verbindung zu Google, Amazon, AFTVnews, Projectivy, Netflix, Disney+ oder anderen genannten Marken; alle Namen und Marken gehören ihren jeweiligen Inhabern.",
                            "Lumen Launcher is a private, free hobby project provided without any warranty, “as is”. You use it at your own risk. " +
                                "To the extent permitted by law, the developer accepts no liability for damage, data loss, malfunction or any other consequence of use. " +
                                "Liability for intent and gross negligence, for injury to life, body or health, and under mandatory statutory provisions remains unaffected.\n\n" +
                                "Features such as root/ADB app disabling, restart interfere with your device; use them only if you understand the consequences. " +
                                "The user alone is responsible for third-party content (radio streams, web pages in web widgets, downloaded files, wallpapers) and for complying with copyright and terms of use. " +
                                "Parental controls are an aid, not guaranteed protection.\n\n" +
                                "Lumen Launcher is not affiliated with Google, Amazon, AFTVnews, Projectivy, Netflix, Disney+ or any other mentioned brand; all names and trademarks belong to their respective owners.",
                        ),
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f),
                    )
                }
                item { Section2(t("Datenschutz & Online-Dienste", "Privacy & online services")) }
                item {
                    Text(
                        t(
                            "Lumen hat keine Konten, keine Werbung und keine Analyse. Online wird nur angefragt: Open-Meteo (Wetter und Ortssuche, nur mit aktivem Wetter) und ipapi.co (Land der öffentlichen IP im VPN-Dot-Menü, nur bei aktivem VPN). Alle Einstellungen bleiben auf dem Gerät.",
                            "Lumen has no accounts, no ads and no analytics. The only online requests are Open-Meteo (weather and place search, only when weather is on) and ipapi.co (country of your public IP in the VPN-dot menu, only while a VPN is active). All settings stay on the device.",
                        ),
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f),
                    )
                }
                if (note.isNotEmpty()) item { Text(note, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)) }
                item { Pill(t("Schließen", "Close")) { onClose() } }
            }
        }
    }
}

@Composable
private fun Section2(title: String) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(top = 14.dp))
}
