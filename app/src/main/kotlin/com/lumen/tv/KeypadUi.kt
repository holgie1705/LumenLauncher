package com.lumen.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Einstellungen für den TV-Ziffernblock, der Zahlen per ADB an die laufende App schickt (wie atvtools). */
@Composable
fun KeypadSettingsDialog(vm: HomeViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefs.collectAsState()
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(editing) { if (editing) { delay(80); runCatching { field.requestFocus() } } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.REMOTE, t("Ziffernblock (ADB)", "Number pad (ADB)"), t("Zahlen auf dem TV eingeben – auch ohne Nummerntasten auf der Fernbedienung", "Enter numbers on the TV – even without number keys on the remote"), width = 860.dp) {
            if (editing) {
                Text(t("IP der Box (leer = diese Box selbst)", "Box IP (empty = this box itself)"), fontSize = 14.sp, color = NeoCyan)
                BasicTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 19.sp), cursorBrush = SolidColor(NeoCyan),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { vm.update { it.copy(adbHost = text.trim()) }; editing = false }),
                    modifier = Modifier.fillMaxWidth().background(NeoGlass, RoundedCornerShape(14.dp)).border(2.dp, NeoYellow, RoundedCornerShape(14.dp)).padding(14.dp).focusRequester(field),
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { SettingLine(t("Ziel-IP", "Target IP"), prefs.adbHost.ifEmpty { t("diese Box", "this box") }) { text = prefs.adbHost; editing = true } }
                item { SettingLine(t("Nach der Zahl automatisch OK senden", "Send OK after the number"), onOffK(prefs.keypadEnter)) { vm.update { it.copy(keypadEnter = !it.keypadEnter) } } }
                item {
                    SettingLine(
                        t("Automatisch senden (ohne OK)", "Send automatically (without OK)"),
                        if (prefs.keypadAutoSend <= 0) t("Aus", "Off") else "${prefs.keypadAutoSend}s",
                    ) {
                        val steps = listOf(0, 2, 3, 5)
                        val next = steps[(steps.indexOf(prefs.keypadAutoSend).coerceAtLeast(0) + 1) % steps.size]
                        vm.update { it.copy(keypadAutoSend = next) }
                    }
                }
                item {
                    SettingLine(t("Verbindung testen", "Test connection"), "") {
                        status = t("Teste …", "Testing …")
                        scope.launch { status = AdbCtl.test(ctx, prefs) }
                    }
                }
                if (status.isNotEmpty()) item { Text(status, fontSize = 14.sp, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
                item {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t("So geht's:", "How it works:"), fontSize = 14.sp, color = Color.White)
                        Text(
                            t("1. An der Box \"ADB über Netzwerk/Debugging\" einschalten (Entwickleroptionen).\n" +
                                "2. Oben \"Verbindung testen\" – beim ersten Mal fragt die Box, ob sie Lumen erlauben soll → \"Immer erlauben\".\n" +
                                "3. Unter Fernbedienung eine Taste anlernen und ihr die Aktion \"Ziffernblock ein/aus\" geben.\n" +
                                "4. In einer App (z. B. waipu) die Taste drücken, Zahl mit den Pfeiltasten wählen, OK = senden. Zurück schließt. Mit \"Automatisch senden\" geht's auch ohne OK – einfach kurz warten.",
                                "1. On the box, turn on \"ADB over network/debugging\" (developer options).\n" +
                                "2. Tap \"Test connection\" above – the first time the box asks to allow Lumen → \"Always allow\".\n" +
                                "3. Under Remote, learn a key and assign the action \"Number pad on/off\".\n" +
                                "4. In an app (e.g. waipu) press that key, pick digits with the arrows, OK = send. Back closes. With \"Send automatically\" it also works without OK – just wait briefly."),
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                item { Pill(t("Fertig", "Done"), Modifier.padding(start = 12.dp, top = 10.dp)) { onClose() } }
            }
        }
    }
}

private fun onOffK(v: Boolean) = if (v) t("An", "On") else t("Aus", "Off")
