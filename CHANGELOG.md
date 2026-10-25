# Lumen Launcher – Changelog (Deutsch)

## Bauen & installieren
1. Ordner in Android Studio (Koala oder neuer) öffnen, Gradle-Sync abwarten.
2. `adb connect <TV-IP>` → Run.
3. Standard-Launcher: `adb shell cmd package set-home-activity com.lumen.tv/.MainActivity`

## Einmalig im TV aktivieren (Einstellungen → Bedienungshilfen)
- **Lumen Home-Taste**: nötig für Fernbedienungs-Shortcuts, Auto-Standby, Einschlaf-Timer,
  Kindersicherung außerhalb von Lumen und "Stock-Launcher überschreiben".
- Optional: **Benachrichtigungszugriff → Lumen Wiedergabe** für die Reihe "Läuft gerade".
(Beides lässt sich in Lumen → Einstellungen → System direkt öffnen.)

## Ordner: /sdcard/Android/data/com.lumen.tv/files/
- `wallpapers/` Bilder (jpg/png/webp), GIF, Videos (mp4/webm/mkv) – wechseln automatisch
- `fonts/` eigene Schriften (ttf/otf)
- `themes/` Theme-Pakete (json) – werden in Lumen per "Theme speichern" erzeugt und lassen sich teilen

## Stock-Launcher loswerden
- Ohne Root: `adb shell pm disable-user --user 0 com.google.android.tvlauncher`
  (rückgängig: `adb shell pm enable com.google.android.tvlauncher`) – klappt nicht auf jedem Gerät.
- Mit Root: Einstellungen → System → "Stock-Launcher einfrieren (Root)".

## Plugins schreiben
Eine App deklariert einen exportierten ContentProvider:

    <provider android:name=".MyRows" android:authorities="com.example.rows" android:exported="true">
        <intent-filter><action android:name="com.lumen.tv.PLUGIN"/></intent-filter>
    </provider>

`content://com.example.rows/rows` liefert einen Cursor mit den Spalten
`row` (Reihentitel), `title`, `image` (URL oder content-URI), `intent` (Intent.toUri(URI_INTENT_SCHEME)).
Lumen zeigt jede `row` als eigene Reihe unter "Plugins".

## Layouts, Uhr, Wetter
- Einstellungen → Aussehen: **Layout** (Reihen / Raster / Seitenleiste), **Kachel-Stil** (Banner, Breit, Kompakt, Icons, Rund),
  **Uhr-Stil** (Digital, Minimal, Analog), **Wetter** (Aus / Kompakt / Mit Vorschau), **Wetter-Ort**.
- Wetter kommt von Open-Meteo (kostenlos, ohne Konto). Ort: tippen oder aus den beliebten Städten wählen.
  OK auf das Uhr/Wetter-Widget oben links öffnet die Wetter-Details.

## Bedienung
OK = starten · OK lang = Menü (Reihen, Sperren, Autostart, App-Info, Deinstallieren)
Oben: Suche · Timer (Einschlaf-Timer) · Einstellungen

## Sprache / Language (neu in 0.6)
Einstellungen → "Sprache / Language": Deutsch → English → System. Gilt sofort für die gesamte Oberfläche
(Menüs, Reihen-Titel, Wetter, Datum, Statusleiste, Toasts, VPN-Dot, Info). Eigene Reihen-Namen bleiben, wie du sie vergeben hast.

## VPN-Dot (neu in 0.6)
Einstellungen → "VPN-Dot": kleiner Punkt in der Bildschirmecke (grün = VPN aktiv, rot = kein VPN), blinkt im
einstellbaren Intervall. Beim ersten Einschalten öffnet Lumen die Systemseite "Über anderen Apps einblenden" – dort Lumen erlauben.
Größe, Intervall, Dauer, Ecke, Autostart nach dem Booten, Test-Knopf. Das VPN-Land kommt über ipapi.co (nur bei offenem Menü und aktivem VPN).
**Wichtig:** die alte eigenständige "VPN DOT"-App vorher deinstallieren (`adb uninstall com.holgie.vpndottv`, Paketname ggf. anpassen),
sonst erscheinen zwei Punkte. Der Punkt ist in Lumen standardmäßig AUS.

## Info (neu in 0.6)
Einstellungen → "Info": Version, Kontakt (Telegram, E-Mail), Buy me a coffee, Hinweise zu Online-Diensten.

## Neues Einstellungs-Menü (0.7)
Seitenpanel links im Stil von Projectivy: Kopf mit Lumen-Logo und Datum, Hauptliste mit Symbolen
(Aussehen, Standby & Energie, Reihen bearbeiten, Fernbedienung, Kindersicherung, System, Sprache, VPN-Dot, Info)
und ein Block **Android** (Android-Einstellungen, Netzwerk & WLAN, Bildschirm, Ton, Apps verwalten).
In der Kopfzeile gibt es zusätzlich einen "Android"-Knopf. Zurück-Taste = eine Ebene zurück.
Schrift: Aussehen → **Menü-Schrift** (gilt für das Menü und die Kopfzeile) und **Menü-Schriftgröße**.
Eigene Schriften (ttf/otf) nach `Android/data/com.lumen.tv/files/fonts/` kopieren.

## Favoriten & Kopfzeilen-Position (0.8)
- Langer Druck auf eine App → **Zu Favoriten hinzufügen**. Die Reihe "Favoriten" steht ganz oben (leer = unsichtbar),
  Reihenfolge per "Nach links/rechts verschieben". Verhalten wie eine eigene Reihe, lässt sich unter Reihen bearbeiten aus-/einblenden und verschieben.
- Einstellungen → Aussehen → **Position der Kopfzeile**: Oben oder Unten (Uhr, Wetter, Status, Knöpfe).

## Look (0.9)
Einstellungen, Kontextmenü und Android-Block sind jetzt flach und volle Breite gestaltet (heller Balken bei Fokus, Abschnittsbänder, halbtransparentes Panel) – angelehnt an das Projectivy-Menü. Die Android-Einstellungen bleiben im Block "Android" direkt im Hauptmenü und als Knopf in der Kopfzeile.

## 1.0
- Einstellungen → Aussehen: **Position der Kacheln** (Oben/Unten, im Layout "Reihen") und **Position der Kopfzeile** (Oben/Unten).
- Kacheln verschieben: Langer Druck → "Nach links/rechts verschieben" funktioniert jetzt auch in "Alle Apps" (schaltet auf "Eigene Reihenfolge" um; Einstellung "Alle Apps sortieren nach").
- Android-Unterseiten (Bildschirm, Ton, …): fehlt eine Seite auf der Box, öffnet Lumen automatisch die Android-Haupteinstellungen und zeigt einen Hinweis als Toast.
- Menü moderner: abgerundete Fokus-Balken mit Rahmen, Symbol-Kacheln, Farbverlauf im Kopf.

## 1.1
- **Schrift:** Bai Jamjuree (SIL OFL, Lizenz liegt bei) ist jetzt die Standardschrift – eckig-rund wie im Projectivy-Menü. Wechseln unter Aussehen → Schriftart / Menü-Schrift.
- **Text zur App** (großer Titel + "Noch nicht geöffnet"): Aussehen → "Text zur App": Aus / Klein / Groß (Standard: Klein).
- **Wetter:** Sonnenauf-/untergang in den Wetter-Details, Einheit °C/km/h oder °F/mph.
- **System-Apps aufräumen** (Idee aus CleanYourTV): Langer Druck auf eine System-App → "App deaktivieren (Root/ADB)".
  Mit Root sofort (reversibel), sonst sammelt Lumen die Befehle in `debloat.bat` / `restore.bat` (Ordner `Android/data/com.lumen.tv/files/`), die du am PC ausführst.
- **Info:** zeigt den aktuellen Arbeitsspeicher-Verbrauch von Lumen.

## 1.2 – Wallpaper-Galerie
Einstellungen → Aussehen → **Wallpaper herunterladen (Galerie)**. Quellen (ohne Konto/Schlüssel): Bing Tagesbild, Wikimedia Commons (Qualitätsbilder),
NASA (Weltall), Unsplash-Fotos über picsum.photos. OK auf ein Bild lädt es (1920 px) in den Ordner `wallpapers/`; ein ✓ markiert schon geladene Bilder.
Die Bilder gehören ihren Urhebern (Lizenz steht unter der Vorschau) – nur für den privaten Gebrauch gedacht. Die Galerie nutzt einen kleinen eigenen Bild-Cache und gibt ihn beim Schließen frei.

## v1.3
- Radio-Player: Reihe „Radio“ auf dem Startbildschirm (ein-/ausblendbar unter Einstellungen → Aussehen), Sendersuche über radio-browser.info (SWR3, SR1, Radio Bollerwagen, Malle …), Wiedergabe im Hintergrund, Sender speichern/entfernen (lang drücken).
- Menüpunkt „Bildschirm“ entfernt.
- Moderneres Design: Icon-Buttons oben (klappen beim Fokus aus), rundere Kacheln mit kräftigerem Fokus-Rahmen, größere Reihentitel, Verlauf oben/unten.

## v1.4
- Feste Hintergrund-/Akzentfarbe als Standard (kein Farbwechsel mehr beim Scrollen; "Auto" bleibt wählbar), Uhr und Wetter kleiner.

## v1.5
- App heißt „Lumen Launcher“. Laufender Sender/Song oben unter der Uhr. Statusleiste kleiner, Start-Zähler entfernt, Zahnrad-Symbol, optionaler Neustart-Knopf (Root / Systemrecht / Android-Ausschaltmenü), RAM/CPU: kleinerer Bild-Cache, RGB565, Aurora mit ~8 fps.

## v1.6
- Neustart-Knopf standardmäßig sichtbar; neue „Jetzt läuft“-Karte (Logo, Sender, Song, Equalizer, Stopp).

## v1.9
- Einstellungen als durchscheinendes Seitenpanel am linken Rand; Startbildschirm bleibt dahinter sichtbar (kein Systemabdunkeln, nur sanfter Verlauf).

## v1.11
- Senderlogos: Favicon (https), sonst Seiten-Icon der Sender-Homepage/Stream-Domain, sonst Anfangsbuchstaben; SVG-Unterstützung (coil-svg).

## v1.13
- Radio-Symbol als Ersatz ohne Senderlogo; „Lumen beim Systemstart öffnen“ (Einstellungen → System): Boot-Empfänger + Bedienungshilfe.

## v1.14
- Kacheln verschieben: Menü → „Verschieben (Pfeiltasten)“, dann Links/Rechts, OK beendet.

## v1.15
- Autostart-App per Auswahlliste einstellbar; Info-Kopf fokussierbar (Liste scrollt ganz nach oben).

## v1.16
- Position der Radio-Karte wählbar (7 Plätze).

## v1.19

## v1.20

## v1.21
- Web-Widgets: beliebige Webadresse als Live-Kachel (Reihe „Widgets“), OK = groß und bedienbar; Verwaltung unter Einstellungen → Web-Widgets.

## v1.22
- Info: Haftungsausschluss / Nutzung auf eigene Gefahr (DE/EN).

## v1.23
- Look „Wallpaper pur“ (neuer Standard): Originalbild ohne Farbwechsel; Abdunkeln standardmäßig 25 %.
- Fokus-Stil der Kacheln: Rahmen (ruhig, ohne Skalierung), Leuchten, Anheben.
- Neue Kachel-Stile: Poster (hoch), Karte.
- Web-Widgets: Größe wie Apps / Mittel / Groß, einzeln ein-/ausblendbar.
- Neue Schriften: Outfit, Plus Jakarta Sans, Manrope, Sora (SIL Open Font License, Google Fonts); Handschrift entfernt.

## v1.24

## v1.25

## v1.26

## v1.27

## v1.28

## v1.30

## v1.31
- Neustart-Knopf: klickt im Android-Ausschaltmenü automatisch „Neu starten“ (Bedienungshilfe muss neu aktiviert werden).

## v1.32
- Android-Einstellungen schließen sich nicht mehr sofort: Home-Übernahme pausiert 2 Minuten nach dem Öffnen und ignoriert Einstellungs-Apps.

## v1.33
- Release-Build möglich (gradle assembleRelease, mit Debug-Schlüssel signiert): deutlich flüssiger als Debug. profileinstaller ergänzt.

## v1.34
- Startseite näher an den Rand: Uhr/Wetter, Reihen und Statusleiste (Seitenabstand 56 → 26 dp).

## v1.35
- Uhr/Wetter (Hero) noch etwas weiter links (14 dp).

## v1.36
- Neu: Einstellungen → Aussehen → Kachel-Transparenz (0/25/50/70 %): App-Kacheln und Banner werden durchsichtig, das Wallpaper scheint durch.

## v1.37
- Neu: kleine Uhr + Wetter oben links, solange eine andere App läuft (Einstellungen → Aussehen → Uhr & Wetter in Apps). Braucht die Bedienungshilfe.

## v1.38
- README auf Englisch für GitHub, .gitignore ergänzt; alter README-Inhalt liegt in CHANGELOG.md.

## v1.39
- Lizenztexte aller Schriften im Ordner licenses/, Kontakt in der README.

## v1.40
- LICENSE (GPL-3.0) ergänzt, Copyright Holger Schmidt.

## v1.41
- Mini-Uhr in Apps kleiner (Uhr 17 sp, Wetter 13 sp).
