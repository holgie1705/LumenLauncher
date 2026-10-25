package com.lumen.tv

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DOT_GREEN = Color(0xFF35E07F)
private val DOT_RED = Color(0xFFEB3737)

private fun <T> List<T>.cycle(v: T): T = this[(indexOf(v) + 1) % size]
private fun secs(n: Int) = "$n ${t("Sek.", "s")}"

/** VPN-Dot: kleiner Punkt in der Bildschirmecke, der den VPN-Status zeigt (grün = VPN aktiv, rot = kein VPN). */
@Composable
fun VpnDotDialog(vm: HomeViewModel, prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    var country by remember { mutableStateOf<VpnCountry?>(null) }
    var lookupDone by remember { mutableStateOf(false) }

    // Status alle 2 s prüfen (VPN verbunden? Overlay-Berechtigung erteilt?)
    val vpn by produceState(VpnDot.vpnActive(ctx)) { while (true) { value = VpnDot.vpnActive(ctx); delay(2_000) } }
    val overlay by produceState(VpnDot.canOverlay(ctx)) { while (true) { value = VpnDot.canOverlay(ctx); delay(1_000) } }

    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }
    // Dienst folgt den Einstellungen (auch wenn die Berechtigung erst jetzt erteilt wird)
    LaunchedEffect(prefs.dotOn, prefs.dotSize, prefs.dotInterval, prefs.dotDuration, prefs.dotPos, overlay) {
        VpnDot.sync(ctx, prefs)
    }
    // Land der öffentlichen IP nur abfragen, solange ein VPN aktiv ist
    LaunchedEffect(vpn, Lang.en) {
        if (!vpn) { country = null; lookupDone = false; return@LaunchedEffect }
        lookupDone = false
        country = VpnDot.country()
        lookupDone = true
    }

    fun overlaySettings() {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isFailure) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.98f))) {
            LazyColumn(
                Modifier.align(Alignment.Center).width(800.dp).fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Text("VPN-Dot", fontSize = 30.sp, fontWeight = FontWeight.W300, color = Color.White) }
                item {
                    Column(
                        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(16.dp).background(if (vpn) DOT_GREEN else DOT_RED, CircleShape))
                            Text(
                                if (vpn) t("VPN ist verbunden", "VPN is connected") else t("Kein VPN erkannt", "No VPN detected"),
                                fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Color.White,
                            )
                        }
                        val sub = when {
                            !prefs.dotOn -> t("Der Punkt ist ausgeschaltet.", "The dot is switched off.")
                            !overlay -> t("Berechtigung „Über anderen Apps einblenden“ fehlt.", "Permission “Display over other apps” is missing.")
                            else -> t("Der Punkt blinkt im eingestellten Intervall.", "The dot appears at the configured interval.")
                        }
                        Text(sub, fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f))
                        if (vpn) {
                            val c = country
                            val line = when {
                                !lookupDone -> t("VPN-Land: wird ermittelt …", "VPN country: looking up …")
                                c == null || (c.name.isEmpty() && c.ip.isEmpty()) -> t("VPN-Land: nicht ermittelbar", "VPN country: unavailable")
                                else -> t("VPN-Land: ", "VPN country: ") + c.name + " " + c.flag
                            }
                            Text(line, fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f))
                            if (c != null && c.ip.isNotEmpty()) Text(t("VPN-IP: ", "VPN IP: ") + c.ip, fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f))
                        }
                    }
                }
                item {
                    SettingLine(t("VPN-Dot aktiv", "VPN dot enabled"), if (prefs.dotOn) t("An", "On") else t("Aus", "Off"), Modifier.focusRequester(first)) {
                        val on = !prefs.dotOn
                        vm.update { it.copy(dotOn = on) }
                        if (on && !VpnDot.canOverlay(ctx)) overlaySettings()
                    }
                }
                item {
                    SettingLine(t("Berechtigung „Über anderen Apps einblenden“", "Permission “Display over other apps”"),
                        if (overlay) t("erteilt", "granted") else t("öffnen", "open")) { overlaySettings() }
                }
                item { SettingLine(t("Punktgröße", "Dot size"), "${prefs.dotSize} px") { vm.update { it.copy(dotSize = VpnDot.SIZES.cycle(it.dotSize)) } } }
                item { SettingLine(t("Blinkintervall", "Blink interval"), secs(prefs.dotInterval)) { vm.update { it.copy(dotInterval = VpnDot.INTERVAL_OPTIONS.cycle(it.dotInterval)) } } }
                item { SettingLine(t("Anzeigedauer", "Display duration"), secs(prefs.dotDuration)) { vm.update { it.copy(dotDuration = VpnDot.DURATION_OPTIONS.cycle(it.dotDuration)) } } }
                item { SettingLine(t("Position", "Position"), VpnDot.positionName(prefs.dotPos)) { vm.update { it.copy(dotPos = (it.dotPos + 1) % 4) } } }
                item { SettingLine(t("Autostart nach dem Booten", "Auto-start after boot"), if (prefs.dotAuto) t("An", "On") else t("Aus", "Off")) { vm.update { it.copy(dotAuto = !it.dotAuto) } } }
                item {
                    SettingLine(t("Punkt jetzt testen", "Test the dot now"), "") {
                        if (!VpnDot.canOverlay(ctx)) overlaySettings()
                        else scope.launch {
                            VpnDot.start(ctx)
                            delay((vm.prefs.value.dotDuration + 1) * 1000L)
                            if (!vm.prefs.value.dotOn) VpnDot.stop(ctx)
                        }
                    }
                }
                item {
                    Text(
                        t(
                            "Grün = VPN aktiv, rot = kein VPN. Das Land wird über die öffentliche IP-Adresse (ipapi.co) ermittelt – nur solange dieses Menü offen und ein VPN aktiv ist. GPS wird nicht benutzt.",
                            "Green = VPN active, red = no VPN. The country is looked up from your public IP address (ipapi.co) – only while this menu is open and a VPN is active. GPS is not used.",
                        ),
                        fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f),
                    )
                }
                item { Pill(t("Schließen", "Close")) { onClose() } }
            }
        }
    }
}
