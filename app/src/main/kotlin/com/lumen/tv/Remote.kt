package com.lumen.tv

import android.content.Context
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** Tasten anlernen: Die nächste gedrückte Taste wird erkannt – in Lumen selbst oder (falls aktiv) über die Bedienungshilfe. */
object KeyLearn {
    val waiting = MutableStateFlow(false)
    val captured = MutableStateFlow<Int?>(null)
    /** Tasten, die zum Bedienen gebraucht werden und nicht belegt werden sollen. */
    val reserved = setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER,
    )
}

/** Erkannte Box und Fernbedienung(en) samt Sondertasten. */
class RemoteInfo(val box: String, val remotes: List<String>, val keys: List<Pair<Int, String>>)

object RemoteDetect {
    /** Sondertasten, nach denen gesucht wird (Farbtasten, App-Tasten, Medientasten …). */
    private val CANDIDATES = listOf(
        KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_PROG_BLUE,
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_BOOKMARK,
        KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_SEARCH,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_RECORD,
        KeyEvent.KEYCODE_ALL_APPS, KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_DVR, KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_CAPTIONS,
        KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_BUTTON_2, KeyEvent.KEYCODE_BUTTON_3, KeyEvent.KEYCODE_BUTTON_4, KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2,
    )

    fun detect(): RemoteInfo {
        val box = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.distinct().joinToString(" ")
        val devs = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }
            .filter { !it.isVirtual && (it.sources and (InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD)) != 0 }
        val names = devs.map { it.name }.filter { n -> listOf("gpio", "power", "hdmi_cec", "uinput").none { n.lowercase().contains(it) } }.distinct()
        val has = CANDIDATES.filter { k -> devs.any { d -> runCatching { d.hasKeys(k)[0] }.getOrDefault(false) } }
        return RemoteInfo(box, names.ifEmpty { devs.map { it.name }.distinct() }, has.map { it to keyName(it) })
    }

    /** Vorschlag für Farbtasten, falls vorhanden: Rot = Lumen, Grün = Schlaftimer, Gelb = Standby, Blau = Sprachsuche. */
    fun preset(info: RemoteInfo): List<Shortcut> {
        val codes = info.keys.map { it.first }.toSet()
        return listOfNotNull(
            Shortcut(KeyEvent.KEYCODE_PROG_RED, false, ActionType.LUMEN).takeIf { KeyEvent.KEYCODE_PROG_RED in codes },
            Shortcut(KeyEvent.KEYCODE_PROG_GREEN, false, ActionType.TIMER).takeIf { KeyEvent.KEYCODE_PROG_GREEN in codes },
            Shortcut(KeyEvent.KEYCODE_PROG_YELLOW, false, ActionType.STANDBY).takeIf { KeyEvent.KEYCODE_PROG_YELLOW in codes },
            Shortcut(KeyEvent.KEYCODE_PROG_BLUE, false, ActionType.ASSIST).takeIf { KeyEvent.KEYCODE_PROG_BLUE in codes },
        )
    }
}

/** Großer Hinweis „Drück jetzt die Taste“; erkennt die Taste und gibt den Code zurück. Zurück bricht ab. */
@Composable
fun KeyLearnDialog(onKey: (Int) -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    val captured by KeyLearn.captured.collectAsState()
    DisposableEffect(Unit) { KeyLearn.captured.value = null; KeyLearn.waiting.value = true; onDispose { KeyLearn.waiting.value = false } }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
    // über die Bedienungshilfe erkannt (auch Tasten, die Android sonst nicht an Lumen gibt)
    LaunchedEffect(captured) { captured?.let { KeyLearn.captured.value = null; onKey(it) } }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusRequester(focus).focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                    val code = e.key.nativeKeyCode
                    when {
                        code == KeyEvent.KEYCODE_BACK -> { onCancel(); true }
                        code in KeyLearn.reserved -> true
                        else -> { KeyLearn.waiting.value = false; onKey(code); true }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.width(620.dp).clip(RoundedCornerShape(30.dp)).background(NeoPanelBrush).border(1.dp, NeoLine, RoundedCornerShape(30.dp)).padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(84.dp).background(NeoCyan.copy(alpha = 0.2f), CircleShape).border(2.dp, NeoCyan, CircleShape), contentAlignment = Alignment.Center) {
                    PanelIcon(Ico.REMOTE, Modifier.size(40.dp), NeoCyan)
                }
                Text(t("Drück jetzt die Taste", "Press the button now"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    t("Lumen erkennt die Taste auf deiner Fernbedienung automatisch. Pfeiltasten und OK bleiben frei. Zurück bricht ab.",
                        "Lumen detects the button on your remote automatically. Arrow keys and OK stay free. Back cancels."),
                    fontSize = 15.sp, color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center,
                )
            }
        }
    }
}
