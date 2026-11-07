package com.lumen.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import java.io.File

/**
 * Dateimanager: Ordner durchblättern, Kopieren/Ausschneiden/Einfügen, Umbenennen, Löschen (mit Rückfrage),
 * neuen Ordner anlegen, Dateien öffnen und APKs installieren. Für Vollzugriff ab Android 11 oben antippen.
 */
@Composable
fun FileManagerDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val rootsList = remember { FileMgr.roots(ctx) }
    var dir by remember { mutableStateOf(rootsList.firstOrNull()?.second ?: File("/sdcard")) }
    var tick by remember { mutableIntStateOf(0) }
    var allFiles by remember { mutableStateOf(FileMgr.hasAllFiles()) }
    var clip by remember { mutableStateOf<Pair<File, Boolean>?>(null) }   // (Datei, ausgeschnitten?)
    var menu by remember { mutableStateOf<MenuSpec?>(null) }
    var editMode by remember { mutableIntStateOf(0) }   // 0 nichts, 1 neuer Ordner, 2 umbenennen
    var editTarget by remember { mutableStateOf<File?>(null) }
    var editText by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }

    val entries = remember(dir, tick) { FileMgr.list(dir) }
    LaunchedEffect(dir, tick) { allFiles = FileMgr.hasAllFiles() }
    LaunchedEffect(editMode) { if (editMode != 0) { delay(80); runCatching { field.requestFocus() } } }
    LaunchedEffect(dir) { delay(120); runCatching { firstRow.requestFocus() } }

    fun refresh() { tick++ }
    fun commitEdit() {
        val name = editText.trim()
        when (editMode) {
            1 -> if (name.isNotEmpty()) FileMgr.mkdir(dir, name)
            2 -> editTarget?.let { if (name.isNotEmpty()) FileMgr.rename(it, name) }
        }
        editMode = 0; editTarget = null; editText = ""; refresh()
    }

    fun itemMenu(f: File): MenuSpec {
        val items = mutableListOf<MenuItemSpec>()
        if (f.isDirectory) items += MenuItemSpec(t("Öffnen", "Open")) { menu = null; dir = f }
        else {
            if (FileMgr.isApk(f)) items += MenuItemSpec(t("APK installieren", "Install APK")) { menu = null; FileMgr.installApk(ctx, f) }
            items += MenuItemSpec(t("Öffnen mit …", "Open with …")) { menu = null; FileMgr.openFile(ctx, f) }
        }
        items += MenuItemSpec(t("Kopieren", "Copy")) { clip = f to false; menu = null }
        items += MenuItemSpec(t("Ausschneiden", "Cut")) { clip = f to true; menu = null }
        items += MenuItemSpec(t("Umbenennen", "Rename")) { editTarget = f; editText = f.name; editMode = 2; menu = null }
        items += MenuItemSpec(t("Löschen", "Delete")) {
            menu = MenuSpec(
                t("'${f.name}' löschen?", "Delete '${f.name}'?"),
                listOf(
                    MenuItemSpec(t("Ja, löschen", "Yes, delete")) { FileMgr.delete(f); menu = null; refresh() },
                    MenuItemSpec(t("Abbrechen", "Cancel")) { menu = null },
                ),
            )
        }
        return MenuSpec(f.name, items)
    }

    fun onRowClick(f: File) { if (f.isDirectory) dir = f else menu = itemMenu(f) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.also { spatialBlur(it) }?.setDimAmount(0f)
        NeoSheet(Ico.GRID, t("Dateimanager", "File manager"), dir.absolutePath, width = 1120.dp) {
            // Vollzugriff-Hinweis (ab Android 11), falls noch nicht erteilt
            if (!allFiles) {
                SettingLine(t("Vollzugriff auf alle Dateien erlauben", "Allow access to all files"), t("öffnen", "open")) { FileMgr.requestAllFiles(ctx) }
            }
            // Schnellziele (Interner Speicher, Downloads, SD, Lumen-Ordner)
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rootsList) { (label, root) ->
                    Pill(label, selected = dir.absolutePath == root.absolutePath || dir.absolutePath.startsWith(root.absolutePath + "/")) { dir = root }
                }
            }
            // Aktionsleiste
            LazyRow(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Pill("⬆ " + t("Hoch", "Up")) { dir.parentFile?.takeIf { it.canRead() }?.let { dir = it } } }
                item { Pill("＋ " + t("Ordner", "Folder")) { editText = ""; editTarget = null; editMode = 1 } }
                item { Pill("⟳ " + t("Aktualisieren", "Refresh")) { refresh() } }
                clip?.let { (f, cut) ->
                    item {
                        Pill("📋 " + t("Einfügen", "Paste") + " (${f.name})", selected = true) {
                            val ok = if (cut) FileMgr.moveInto(f, dir) else FileMgr.copyInto(f, dir)
                            if (ok && cut) clip = null
                            refresh()
                        }
                    }
                    item { Pill("✕ " + t("Abbrechen", "Cancel")) { clip = null } }
                }
            }
            // Eingabefeld (neuer Ordner / umbenennen)
            if (editMode != 0) BasicTextField(
                value = editText, onValueChange = { editText = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 19.sp), cursorBrush = SolidColor(NeoCyan),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { commitEdit() }),
                modifier = Modifier.fillMaxWidth().background(NeoGlass, RoundedCornerShape(14.dp)).border(2.dp, NeoYellow, RoundedCornerShape(14.dp)).padding(14.dp).focusRequester(field),
            )
            // Liste
            if (entries.isEmpty()) {
                Text(t("Ordner ist leer.", "Folder is empty."), fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(16.dp))
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(entries, key = { it.absolutePath }) { f ->
                    FileRow(
                        f,
                        modifier = if (f == entries.first()) Modifier.focusRequester(firstRow) else Modifier,
                        onClick = { onRowClick(f) },
                        onLong = { menu = itemMenu(f) },
                    )
                }
            }
            Pill(t("Schließen", "Close"), Modifier.padding(start = 4.dp, top = 4.dp)) { onClose() }
        }
        menu?.let { MenuDialog(it) { menu = null } }
    }
}

/** Eine Zeile im Dateimanager: Symbol, Name, Größe/Anzahl. OK öffnet (Ordner) bzw. Aktionen (Datei); lange OK = Aktionen. */
@Composable
private fun FileRow(f: File, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val isDir = f.isDirectory
    val icon = when {
        isDir -> "📁"
        FileMgr.isApk(f) -> "📦"
        f.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> "🖼"
        f.extension.lowercase() in listOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts") -> "🎬"
        f.extension.lowercase() in listOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "opus") -> "🎵"
        f.extension.lowercase() in listOf("zip", "rar", "7z", "tar", "gz") -> "🗜"
        else -> "📄"
    }
    val meta = if (isDir) "${FileMgr.childCount(f)} " + t("Einträge", "items") else FileMgr.humanSize(f.length())
    Surface(
        onClick = onClick, onLongClick = onLong, modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f), focusedContainerColor = Color.White.copy(alpha = 0.14f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, NeoYellow), shape = shape)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(icon, fontSize = 22.sp)
            Text(f.name, fontSize = 16.sp, fontWeight = if (isDir) FontWeight.SemiBold else FontWeight.Normal, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (FileMgr.isApk(f)) Box(Modifier.background(NeoCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp)).border(1.dp, NeoCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(t("APK", "APK"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(meta, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
        }
    }
}
