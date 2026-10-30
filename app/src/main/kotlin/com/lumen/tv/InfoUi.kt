package com.lumen.tv

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
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

const val APP_VERSION = "2.00"
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
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() }; delay(60); listState.scrollToItem(0) }

    fun openLink(uri: String, action: String = Intent.ACTION_VIEW) {
        val ok = runCatching { ctx.startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        note = if (ok) "" else t(
            "Auf diesem Gerät gibt es keine App zum Öffnen. Adresse: ", "No app on this device can open this. Address: ",
        ) + uri.removePrefix("mailto:")
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.INFO, "Info", "Lumen Launcher $APP_VERSION", width = 820.dp) {
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                state = listState,
                contentPadding = PaddingValues(top = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ---------- Hero-Kopf ----------
                item {
                    var headFocused by remember { mutableStateOf(false) }
                    Column(
                        Modifier.fillMaxWidth()
                            .onFocusChanged { headFocused = it.hasFocus }
                            .focusable()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(NeoViolet.copy(alpha = 0.5f), NeoCyan.copy(alpha = 0.18f), Color(0xE6121627))))
                            .border(1.dp, if (headFocused) NeoYellow else NeoLine, RoundedCornerShape(24.dp))
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            LumenLogo()
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Lumen Launcher", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(
                                    t("Schneller, schlanker Launcher für Android TV", "Fast, lightweight launcher for Android TV"),
                                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
                                )
                                Text(
                                    t("Ohne Konten · ohne Werbung · ohne Tracking", "No accounts · no ads · no tracking"),
                                    fontSize = 12.sp, color = NeoCyan,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InfoChip(t("Version", "Version"), APP_VERSION)
                            InfoChip(t("Speicher", "Memory"), "$ramMb MB")
                            InfoChip("Android", Build.VERSION.RELEASE)
                        }
                    }
                }

                // ---------- Kontakt ----------
                item { SheetSection(t("Kontakt", "Contact")) }
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NeoCard).border(1.dp, NeoLine, RoundedCornerShape(18.dp)).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(painterResource(R.drawable.telegram_avatar), null, Modifier.size(66.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("HOLGIE", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(t("Entwickler von Lumen und VPN-Dot", "Developer of Lumen and VPN-Dot"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
                item { SettingLine("Telegram", "@HOLGeH", Modifier.focusRequester(first)) { openLink(TELEGRAM_URL) } }
                item { SettingLine(t("E-Mail", "Email"), MAIL) { openLink("mailto:$MAIL", Intent.ACTION_SENDTO) } }

                // ---------- Unterstützen ----------
                item { SheetSection(t("Unterstützen", "Support")) }
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFFFDD00).copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))))
                            .border(1.dp, Color(0xFFFFDD00).copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.background(Color(0xFFFFDD00), RoundedCornerShape(10.dp)).padding(8.dp)) {
                            Image(painterResource(R.drawable.bmc_logo), "Buy Me a Coffee", Modifier.size(30.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t("Spendier mir einen Kaffee", "Buy me a coffee"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Text("buymeacoffee.com/holgie", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                        Pill(t("Öffnen", "Open")) { openLink(COFFEE_URL) }
                    }
                }

                // ---------- Datenschutz ----------
                item { SheetSection(t("Datenschutz & Online-Dienste", "Privacy & online services")) }
                item {
                    InfoNote(
                        t(
                            "Lumen hat keine Konten, keine Werbung und keine Analyse. Online wird nur angefragt: Open-Meteo (Wetter und Ortssuche, nur mit aktivem Wetter) und ipapi.co (Land der öffentlichen IP im VPN-Dot-Menü, nur bei aktivem VPN). Alle Einstellungen bleiben auf dem Gerät.",
                            "Lumen has no accounts, no ads and no analytics. The only online requests are Open-Meteo (weather and place search, only when weather is on) and ipapi.co (country of your public IP in the VPN-dot menu, only while a VPN is active). All settings stay on the device.",
                        ),
                    )
                }

                // ---------- Haftung ----------
                item { SheetSection(t("Haftungsausschluss & Nutzung auf eigene Gefahr", "Disclaimer & use at your own risk")) }
                item {
                    InfoNote(
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
                    )
                }
                if (note.isNotEmpty()) item { Text(note, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)) }
                item { Pill(t("Schließen", "Close"), Modifier.padding(top = 4.dp)) { onClose() } }
            }
        }
    }
}

/** Kleine Kennzahl-Kachel (Version, Speicher, Android) im Kopf. */
@Composable
private fun InfoChip(label: String, value: String) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, NeoLine, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(label.uppercase(Lang.locale), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.55f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Längerer Hinweistext in einem dezenten Glaskasten. */
@Composable
private fun InfoNote(text: String) {
    Text(
        text,
        fontSize = 13.sp, color = Color.White.copy(alpha = 0.62f), lineHeight = 19.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.04f)).border(1.dp, NeoLine, RoundedCornerShape(16.dp)).padding(16.dp),
    )
}
