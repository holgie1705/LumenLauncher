package com.lumen.tv

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.DOT, "VPN-Dot", t("Zeigt dir in jeder App, ob dein VPN läuft", "Shows in every app whether your VPN is on")) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // ---------- Links: großer Status ----------
                val stateColor = if (vpn) DOT_GREEN else DOT_RED
                val pulse by produceState(0f, prefs.dotOn) {
                    while (true) { value = (System.currentTimeMillis() % 2000L) / 2000f; delay(80) }
                }
                Column(
                    Modifier.width(300.dp).fillMaxHeight().clip(RoundedCornerShape(26.dp))
                        .background(Brush.verticalGradient(listOf(stateColor.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))))
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                        // pulsierender Ring um den Punkt
                        Box(Modifier.size((70 + 55 * pulse).dp).background(stateColor.copy(alpha = 0.30f * (1f - pulse)), CircleShape))
                        Box(Modifier.size(70.dp).background(Brush.radialGradient(listOf(stateColor, stateColor.copy(alpha = 0.7f))), CircleShape))
                    }
                    Text(
                        if (vpn) t("VPN verbunden", "VPN connected") else t("Kein VPN", "No VPN"),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                    if (vpn) {
                        val c = country
                        when {
                            !lookupDone -> Text(t("Land wird ermittelt …", "Looking up country …"), fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                            c == null || (c.name.isEmpty() && c.ip.isEmpty()) -> Text(t("Land nicht ermittelbar", "Country unavailable"), fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                            else -> {
                                Text(c.flag, fontSize = 40.sp)
                                Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                        if (c != null && c.ip.isNotEmpty()) Box(Modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("IP " + c.ip, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            !prefs.dotOn -> t("Der Punkt ist ausgeschaltet.", "The dot is switched off.")
                            !overlay -> t("Berechtigung fehlt – siehe rechts.", "Permission missing – see right.")
                            else -> t("Grün = VPN aktiv · Rot = kein VPN", "Green = VPN on · Red = no VPN")
                        },
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f),
                    )
                }

                // ---------- Rechts: Einstellungen ----------
                LazyColumn(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item { SheetSection(t("Allgemein", "General")) }
                    item {
                        SettingLine(t("VPN-Dot aktiv", "VPN dot enabled"), if (prefs.dotOn) t("An", "On") else t("Aus", "Off"), Modifier.focusRequester(first)) {
                            val on = !prefs.dotOn
                            vm.update { it.copy(dotOn = on) }
                            if (on && !VpnDot.canOverlay(ctx)) overlaySettings()
                        }
                    }
                    item { SettingLine(t("Autostart nach dem Booten", "Auto-start after boot"), if (prefs.dotAuto) t("An", "On") else t("Aus", "Off")) { vm.update { it.copy(dotAuto = !it.dotAuto) } } }
                    item {
                        SettingLine(t("Über anderen Apps einblenden", "Display over other apps"),
                            if (overlay) t("erteilt", "granted") else t("öffnen", "open")) { overlaySettings() }
                    }
                    item { SheetSection(t("Darstellung", "Appearance")) }
                    item { SettingLine(t("Punktgröße", "Dot size"), "${prefs.dotSize} px") { vm.update { it.copy(dotSize = VpnDot.SIZES.cycle(it.dotSize)) } } }
                    item { SettingLine(t("Position", "Position"), VpnDot.positionName(prefs.dotPos)) { vm.update { it.copy(dotPos = (it.dotPos + 1) % 4) } } }
                    item { SettingLine(t("Blinkintervall", "Blink interval"), secs(prefs.dotInterval)) { vm.update { it.copy(dotInterval = VpnDot.INTERVAL_OPTIONS.cycle(it.dotInterval)) } } }
                    item { SettingLine(t("Anzeigedauer", "Display duration"), secs(prefs.dotDuration)) { vm.update { it.copy(dotDuration = VpnDot.DURATION_OPTIONS.cycle(it.dotDuration)) } } }
                    item {
                        Row(Modifier.padding(start = 12.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pill("▶ " + t("Punkt testen", "Test dot"), selected = true) {
                                if (!VpnDot.canOverlay(ctx)) overlaySettings()
                                else scope.launch {
                                    VpnDot.start(ctx)
                                    delay((vm.prefs.value.dotDuration + 1) * 1000L)
                                    if (!vm.prefs.value.dotOn) VpnDot.stop(ctx)
                                }
                            }
                            Pill(t("Schließen", "Close")) { onClose() }
                        }
                    }
                    item {
                        Text(
                            t("Das Land wird über die öffentliche IP (ipapi.co) ermittelt – nur solange dieses Menü offen und ein VPN aktiv ist. GPS wird nicht benutzt.",
                                "The country is looked up from your public IP (ipapi.co) – only while this menu is open and a VPN is active. GPS is not used."),
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(start = 14.dp, top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
