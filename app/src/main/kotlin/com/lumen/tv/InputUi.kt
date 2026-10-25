package com.lumen.tv

import android.media.tv.TvContract
import android.media.tv.TvView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text

/**
 * Eingang (HDMI usw.) direkt in Lumen anzeigen. Funktioniert auch, wenn kein anderes Programm
 * den Eingang öffnen kann. Zurück-Taste schließt.
 */
@Composable
fun InputPlayerDialog(input: InputSource, onClose: () -> Unit) {
    var status by remember { mutableStateOf(t("Verbinde …", "Connecting …")) }
    var ok by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    TvView(ctx).apply {
                        setZOrderMediaOverlay(true)
                        setCallback(object : TvView.TvInputCallback() {
                            override fun onConnectionFailed(inputId: String) {
                                ok = false; status = t("Eingang konnte nicht geöffnet werden.", "Could not open the input.")
                            }
                            override fun onDisconnected(inputId: String) {
                                ok = false; status = t("Verbindung getrennt.", "Disconnected.")
                            }
                            override fun onVideoAvailable(inputId: String) { ok = true }
                            override fun onVideoUnavailable(inputId: String, reason: Int) {
                                ok = false
                                status = when (reason) {
                                    android.media.tv.TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING -> t("Verbinde …", "Connecting …")
                                    android.media.tv.TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING -> t("Puffert …", "Buffering …")
                                    else -> t("Kein Signal am Eingang „", "No signal on input “") + input.label + "”"
                                }
                            }
                        })
                        tune(input.id, TvContract.buildChannelUriForPassthroughInput(input.id))
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { runCatching { it.reset() } },
            )
            if (!ok) Text(
                status, fontSize = 18.sp, color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
            )
            Text(
                input.label + "  ·  " + t("Zurück = schließen", "Back = close"), fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.TopStart).padding(18.dp).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
