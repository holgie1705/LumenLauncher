package com.lumen.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.DecimalFormat

/**
 * Dateimanager-Logik (ohne UI): Verzeichnisse auflisten, kopieren/verschieben/löschen/umbenennen,
 * Ordner anlegen, APKs installieren und Dateien öffnen. Nutzt den FileProvider „<pkg>.files".
 * Für den Vollzugriff auf den ganzen Speicher ist ab Android 11 „Zugriff auf alle Dateien" nötig.
 */
object FileMgr {

    /** Hat die App Vollzugriff auf den gesamten Speicher? (unter Android 11 immer ja) */
    fun hasAllFiles(): Boolean = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    /** Systemseite „Zugriff auf alle Dateien" für Lumen öffnen. */
    fun requestAllFiles(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Die Einstiegspunkte (Schnellziele) des Dateimanagers. */
    fun roots(ctx: Context): List<Pair<String, File>> {
        val out = mutableListOf<Pair<String, File>>()
        val internal = Environment.getExternalStorageDirectory()
        out += t("Interner Speicher", "Internal storage") to internal
        File(internal, "Download").takeIf { it.isDirectory }?.let { out += (t("Downloads", "Downloads") to it) }
        // Wechseldatenträger (SD/USB): aus getExternalFilesDirs die Mount-Wurzeln ableiten.
        runCatching {
            ctx.getExternalFilesDirs(null).filterNotNull().forEach { d ->
                val root = d.path.substringBefore("/Android/").let { File(it) }
                if (root.absolutePath != internal.absolutePath && root.isDirectory && out.none { it.second.absolutePath == root.absolutePath }) {
                    out += (t("SD-Karte / USB", "SD card / USB") to root)
                }
            }
        }
        ctx.getExternalFilesDir(null)?.let { out += (t("Lumen-Ordner", "Lumen folder") to it) }
        return out
    }

    /** Inhalt eines Ordners: Ordner zuerst, dann Dateien, jeweils alphabetisch. */
    fun list(dir: File): List<File> = runCatching {
        dir.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList()
    }.getOrDefault(emptyList())

    /** Freien, nicht kollidierenden Zielnamen in destDir finden. */
    private fun uniqueTarget(destDir: File, name: String): File {
        var target = File(destDir, name)
        if (!target.exists()) return target
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (target.exists()) { target = File(destDir, "$base ($i)$ext"); i++ }
        return target
    }

    fun copyInto(src: File, destDir: File): Boolean = runCatching {
        if (src.absolutePath == destDir.absolutePath || destDir.absolutePath.startsWith(src.absolutePath + "/")) return false
        src.copyRecursively(uniqueTarget(destDir, src.name), overwrite = false)
    }.getOrDefault(false)

    fun moveInto(src: File, destDir: File): Boolean {
        if (src.parentFile?.absolutePath == destDir.absolutePath) return true
        if (destDir.absolutePath.startsWith(src.absolutePath + "/")) return false
        val target = uniqueTarget(destDir, src.name)
        if (runCatching { src.renameTo(target) }.getOrDefault(false)) return true
        // Anderes Dateisystem (z. B. intern -> SD): kopieren, dann Original löschen.
        return runCatching { if (src.copyRecursively(target, overwrite = false)) { src.deleteRecursively(); true } else false }.getOrDefault(false)
    }

    fun delete(f: File): Boolean = runCatching { f.deleteRecursively() }.getOrDefault(false)

    fun rename(f: File, newName: String): Boolean = runCatching {
        val n = newName.trim(); if (n.isEmpty() || n.contains('/')) return false
        f.renameTo(File(f.parentFile, n))
    }.getOrDefault(false)

    fun mkdir(parent: File, name: String): Boolean = runCatching {
        val n = name.trim(); if (n.isEmpty() || n.contains('/')) return false
        File(parent, n).mkdirs()
    }.getOrDefault(false)

    private fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    /** APK installieren (System-Installer). Fehlt die Erlaubnis „Unbekannte Apps", zuerst dorthin leiten. */
    fun installApk(ctx: Context, f: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(ctx, t("Erst 'Apps aus dieser Quelle zulassen' aktivieren.", "Enable 'allow apps from this source' first."), Toast.LENGTH_LONG).show()
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uriFor(ctx, f), "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure { Toast.makeText(ctx, t("Installation fehlgeschlagen.", "Install failed."), Toast.LENGTH_LONG).show() }
    }

    /** Beliebige Datei mit einer passenden App öffnen. */
    fun openFile(ctx: Context, f: File) {
        if (f.extension.equals("apk", true)) { installApk(ctx, f); return }
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uriFor(ctx, f), mime(f.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure { Toast.makeText(ctx, t("Keine App zum Öffnen gefunden.", "No app found to open this."), Toast.LENGTH_LONG).show() }
    }

    fun isApk(f: File) = f.isFile && f.extension.equals("apk", true)

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble() / 1024; var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return DecimalFormat("#.#").format(v) + " " + units[i]
    }

    /** Anzahl Einträge eines Ordners (für die Zeile), ohne tiefes Zählen. */
    fun childCount(dir: File): Int = runCatching { dir.listFiles()?.size ?: 0 }.getOrDefault(0)

    fun mime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "ts" -> "video/*"
            "mp3", "flac", "wav", "aac", "ogg", "m4a", "opus" -> "audio/*"
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
            "pdf" -> "application/pdf"
            "txt", "log", "md", "ini", "cfg", "json", "xml", "csv" -> "text/*"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "html", "htm" -> "text/html"
            else -> "*/*"
        }
    }
}
