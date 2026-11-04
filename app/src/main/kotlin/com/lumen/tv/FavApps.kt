package com.lumen.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Dauerhaft erreichbarer App-Auswähler: jede installierte App direkt zu den Favoriten an-/abwählen. */
@Composable
fun FavAppsDialog(vm: HomeViewModel, prefs: Prefs, apps: List<TvApp>, onClose: () -> Unit) {
    val live by vm.prefs.collectAsState()
    val favs = live.rows.firstOrNull { it.id == "favs" }?.pkgs.orEmpty()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    LaunchedEffect(editing) { if (editing) { delay(80); runCatching { field.requestFocus() } } }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }

    val list = apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<TvApp> { it.pkg in favs }.thenBy { it.label.lowercase() })

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setDimAmount(0f)
        NeoSheet(Ico.GRID, t("Favoriten-Apps", "Favorite apps"), t("App antippen = zu Favoriten hinzufügen oder entfernen. Sie erscheinen in der Favoriten-Reihe.", "Tap an app to add or remove it from favorites. They appear in the Favorites row."), width = 900.dp) {
            SettingLine(t("Suche", "Search"), query.ifEmpty { "—" }, Modifier.focusRequester(first)) { editing = true }
            if (editing) BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 19.sp), cursorBrush = SolidColor(NeoCyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { editing = false }),
                modifier = Modifier.fillMaxWidth().background(NeoGlass, RoundedCornerShape(14.dp)).border(2.dp, NeoYellow, RoundedCornerShape(14.dp)).padding(14.dp).focusRequester(field),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(list, key = { it.pkg }) { a ->
                    val on = a.pkg in favs
                    SettingLine(a.label, if (on) t("★ Favorit", "★ Favorite") else t("+ hinzufügen", "+ add")) {
                        if (on) vm.removeFromRow("favs", a.pkg) else vm.addToRow("favs", a.pkg)
                    }
                }
                item { Pill(t("Fertig", "Done"), Modifier.padding(start = 12.dp, top = 10.dp)) { onClose() } }
            }
        }
    }
}
