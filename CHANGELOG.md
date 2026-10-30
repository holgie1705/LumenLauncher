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

## v1.42
- Neu: Icon-Packs (Einstellungen → Aussehen → Icon-Pack). Unterstützt installierte Packs im ADW/Nova/Apex-Format (appfilter.xml). Mit Pack werden statt Bannern die Pack-Icons gezeigt; Apps ohne Eintrag behalten ihr Original.

## v1.43
- Icon-Packs: Apps ohne Eintrag im Pack werden automatisch angepasst (Hintergrund, Skalierung, Maske, Overlay aus dem Pack).

## v1.44
- Neu: Einstellungen → System → „Als Standard-Launcher festlegen“ (Android-Home-Rolle bzw. Home-Einstellungen), damit die Wahl dauerhaft bleibt.

## v1.45
- Uhr/Wetter im Home weiter links (Kopfzeile links 26 → 6 dp, Statusleiste rechts unverändert).

## v1.46
- Neues Layout „Frei“ (Einstellungen → Aussehen → Layout): Favoriten frei auf dem Bildschirm platzieren (lang drücken → „Frei verschieben“, Pfeiltasten, OK). Zurücksetzen pro Kachel oder komplett.

## v1.47
- Hinweis in „Reihen“: „Weiter ansehen“ braucht freigegebene Einträge (READ_EPG_DATA ist Systemberechtigung, per ADB nicht erteilbar).

## v1.48
- „Weiter ansehen“: neue Berechtigung READ_TV_LISTINGS (neuere Android-TV-Versionen, erteilbar). Einstellungen → Reihen → „Weiter ansehen: Zugriff erlauben".

## v1.49
- „Weiter ansehen“ und „Läuft gerade“ folgen jetzt dem gewählten Kachel-Stil (Größe).

## v1.50
- Kacheln unten: Die erste Reihe sitzt ganz unten, weitere Reihen liegen darunter und erscheinen beim Nachuntenklicken (statt dass die Reihen darüber die erste nach oben schieben).

## v1.51
- Kacheln unten: Beim Hoch-/Runterklicken sitzt die fokussierte Reihe wieder ganz unten.

## v1.52
- Kachel-Transparenz und Kachel-Stil (Größe) gelten jetzt für alle Reihen: Weiter ansehen, Läuft gerade, Radio, Web-Widgets, Eingänge.

## v1.53
- Eingänge (HDMI usw.) öffnen direkt in Lumen per TvView (Zurück schließt), statt über einen ggf. nicht vorhandenen anderen Player. Statusmeldung bei „Kein Signal“.

## v1.54
- Kacheln unten: Reihenwechsel schiebt die Liste jetzt per Berechnung statt per Item-Offset, sodass auch nach unten gescrollt wird.

## v1.55
- Kacheln unten: Zwischen der fokussierten Reihe und der Reihe darüber ist jetzt Platz für den Text zur App (kein Überlappen mehr).

## v1.56
- Kacheln unten: Änderung aus 1.55 zurückgenommen (kein Extra-Abstand, Text zur App bleibt fest an seiner Stelle wie in 1.54).

## v1.57
- Kacheln unten neu: Text zur App steht fest, die fokussierte Reihe sitzt immer direkt darunter, Reihen darüber verschwinden oben (kein Überlappen mehr).
- App-Name wechselt sauber: alter Name blendet erst aus, dann der neue ein.

## v1.58
- „Stock-Launcher überschreiben“ greift nur noch beim echten Startbildschirm des Stock-Launchers (HOME-Activity), nicht bei jedem Fenster einer App, die auch Launcher sein kann. waipu-Apps werden nie überschrieben (App sprang kurz nach dem Start zurück zu Lumen).

## v1.59
- Neuer Look „Neo-Glas“: Einstellungen als schwebendes Glas-Panel mit bunten Symbolen, Untertiteln und gelbem Fokus-Ring; Werte als Cyan-Kapseln; Menüs, PIN-Feld, Knöpfe und Kopfzeilen-Symbole im selben Stil.
- Radio neu gestaltet: große „Jetzt läuft“-Karte mit Logo, LIVE-Anzeige und Equalizer; Sender als Raster mit Logos; Favoriten/Beliebt/Suche als Reiter; lange drücken = Favorit speichern/entfernen.

## v1.60
- Radio-Karte auf dem Startbildschirm neu: Glaskarte in Senderfarbe mit großem Logo, „LIVE RADIO“, Song, animierter Wellenform und runder Stopp-Taste.
- Fokus-Stile der Kacheln überarbeitet: Rahmen (weißer Ring + Schatten), Neon-Leuchten (Farbverlaufs-Ring + Leuchten in App-Farbe), Anheben (3D, tiefer Schatten); dazu ein sanfter Glanz auf der fokussierten Kachel.
- Einstellungen: Titel und Abschnitt heißen wieder „Lumen Launcher“.

## v1.61
- Radio-Karte kompakter; Spektrum-Analyzer (Balken von unten, Verlauf) und Lautsprecher-Symbol statt Punkte.
- Fokus-Stile: Anheben hebt die Kachel jetzt sichtbar an; neu „Glas“ und „Unterstrich“ (5 Stile).
- Neu: Farbe der Menüs (Nachtblau, Graphit, Mokka & Weiß, Violett, Smaragd, Bordeaux, Mitternacht) unter Aussehen.
- Menü-Schriftgröße zusätzlich „Sehr groß“.
- Statusleiste (WLAN, VPN, RAM, Speicher) etwas größer; Uhr links etwas höher.

## v1.62
- Einstellungen: Hauptseite als Raster großer, farbiger Kacheln (Kategorie-Farbe, Symbol im Glaskreis, Kurzinfo); Android-Bereich als kleine Kacheln; Panel breiter.
- An/Aus-Einstellungen als Schalter.
- Senderlogos zusätzlich aus dem TuneIn-Verzeichnis (randlos, gute Qualität), Favicons als Rückfall.
- Uhr noch etwas höher.

## v1.63
- Autostart-App startet nur noch nach dem Einschalten der Box, nicht mehr nach jeder Neuinstallation oder jedem Neustart von Lumen.

## v1.64
- Kopfzeilen-Knöpfe (Radio, Suche, Timer, Einstellungen …): im Fokus wieder gefüllt in der Fokusfarbe (Gelb bzw. Farbe des Menü-Themas) mit dunkler Schrift.
- Uhr oben bündig ausgerichtet (Wetter bleibt).

## v1.65
- VPN-Dot neu gestaltet: großer pulsierender Status-Punkt mit Land/Flagge und IP links, Einstellungen in Abschnitten rechts.
- Unterdialoge (Info, Web-Widgets, Wallpaper-Galerie, Wetter, Ort) im gemeinsamen modernen Rahmen: schwebende Glaskarte im Menü-Thema, farbiger Kopf.
- Unterseiten der Einstellungen zeigen das passende farbige Symbol im Kopf.

## v1.66
- Einstellungen: Untertitel auf der Hauptseite entfernt, Versionsnummer oben rechts.
- Neue Schalter: Verlaufs-Spur, weißer Knopf mit Häkchen und Schatten, gleitende Animation. Mehrfach-Werte als Kapsel mit Pfeil.
- Neuer Bildschirmschoner „Galerie aus dem Netz“: Fotos von Bing, Unsplash und Wikimedia mit Überblendung und Zoom, große Uhr, Datum und Wetter (Standby & Energie → Bildschirmschoner).

## v1.67
- Neu: Einstellungen → „Leistung“: Ringanzeigen für Arbeitsspeicher, Speicher und Temperatur; RAM-Boost (beendet Hintergrund-Apps, mit Ausnahmeliste; spielende Apps bleiben); Aufräumen (Lumen-Cache, mit Root alle App-Caches); Systeminfos (Gerät, Android, CPU, Bildschirm, Laufzeit, RAM von Lumen).

## v1.68
- Leistung: mehr Temperatur-Quellen; gibt die Box keine Gradzahl frei, zeigt die Karte „Wärme“ den Android-Wärmestatus und die Reserve bis zur Drosselung.
- RAM-Boost schützt immer System-Apps, Launcher, Tastatur, Bedienungshilfen, Benachrichtigungs-Zugriffe und VPN-Apps; die Ausnahmeliste zeigt nur noch normale Apps.

## v1.69
- Neue Reihe „Infos“ mit Info-Widgets (Einstellungen → Info-Widgets): Fußball (OpenLigaDB: Live-Stand, nächstes/letztes Spiel, Tabellenplatz, Detailansicht mit Tabelle), Wetter groß (Stundenwerte, Regenradar des DWD), Countdown (auch jährlich), Notiz, Heimnetz (Router-/Internet-Ping, Speedtest, Gerätesuche).
- News-Ticker als Laufzeile (Tagesschau, Sportschau, kicker, heise, SPIEGEL), oben oder unten.
- Notiz und Countdowns am Handy/PC im Browser bearbeiten (mit Code, nur solange die Widget-Einstellungen offen sind).
- Radio-Reihe auf dem Startbildschirm mit runden Senderlogos; OK spielt direkt, laufender Sender mit drehendem Farbring.

## v1.70
- Info-Widgets haben jetzt die Größe der normalen Kacheln (Inhalt kompakt).
- Fußball: Standard-Liga 2. Bundesliga.
- Neu: Schnellmenü in der Kopfzeile (Lautstärke, Schlaftimer, Radio, Bildschirmschoner, RAM-Boost, Netzwerk, Bluetooth, Leistung).
- Neu: Update-Hinweis – prüft GitHub-Releases, markiert das Zahnrad und installiert die neue Version auf Knopfdruck (System → Nach Updates suchen).
- Neu: Einrichtungs-Assistent beim ersten Start (Sprache, Ort, Lieblings-Apps, Look); erneut startbar unter System.

## v1.71
- Neue Uhr-Designs: Flip-Uhr (umklappende Ziffern), Wortuhr („Es ist Viertel nach acht“), Neon.
- Animierte Hintergründe (gezeichnet, sparsam): Sternenhimmel mit Sternschnuppen, Polarlicht, Farbfluss, Bokeh, Regen, Schnee.
- Hintergrund nach Tageszeit & Wetter: nachts dunkler, abends warm; Regen/Schnee/Nebel/Gewitter als Effekt; Wallpaper mit passenden Wörtern im Dateinamen (z. B. regen, sonne, nacht, abend) werden bevorzugt.
- Menüfarbe „Aus dem Wallpaper“: Menüs übernehmen die Farben des aktuellen Hintergrundbilds.

## v1.72
- Heimnetz: nutzt bei aktivem VPN das echte WLAN/LAN (richtige IP, Router, Gerätesuche); Anzeige „WLAN + VPN“; Router-Erkennung mit mehreren Ports und Rückfall auf .1; Gerätesuche mit mehr typischen Ports.

## v1.73
- Heimnetz: erkennt das echte Netz über den Schnittstellennamen (eth/wlan statt VPN-Tunnel tun/ppp/wg); Router-Adresse von Hand eintragbar; Anzeige aller Schnittstellen zur Fehlersuche.

## v1.74
- Fix: Absturz-Schleife mit aktivem VPN (Binden ans WLAN wird von Android verweigert) – Heimnetz-Abfragen fangen alle Fehler ab.

## v1.75
- Heimnetz-Erkennung auf den Stand von 1.72 zurückgesetzt (1.73/1.74 führten mit VPN zu Neustart-Schleifen); Router-Feld und Schnittstellen-Anzeige wieder entfernt.

## v1.76
- Heimnetz-Geräteliste, letzte Spiele und Tabelle lassen sich jetzt mit der Fernbedienung blättern.
- Speedtest neu: Ping, Jitter, Download und Upload (Cloudflare) mit Live-Anzeige und Einschätzung (4K/HD).
- Fußball: Vereinslogos zuverlässiger (https, User-Agent), sonst Kreis mit Vereinskürzel.
- Leistung: „Gründlich beenden" stoppt Apps wie andere App-Killer über „Beenden erzwingen" (braucht die Bedienungshilfe „Lumen Home-Taste").
- Fernbedienung: „Taste anlernen" – gewünschte Taste einfach drücken. Box und Fernbedienung werden erkannt, Sondertasten angezeigt, Vorschlag für die Farbtasten.

## v1.77
- Uhr & Wetter lassen sich in alle vier Ecken setzen (Einstellungen → Aussehen → „Uhr & Wetter Position"). Rechts gespiegelt (Uhr außen), unten bündig über der Leiste; die Radio-Karte „unter der Uhr" wandert mit.

## v1.78
- Kleine Uhr & Wetter in anderen Apps: Ecke wählbar (Einstellungen → Aussehen → „Uhr & Wetter in Apps": Aus, oben/unten, links/rechts).
- Plugins-Reihe entfernt (wurde von keiner App genutzt).

## v1.79
- Neues Info-Widget „Jetzt im TV": aktuelle Sendung mit Fortschritt und „Danach" für deine Sender (wechselt alle 8 s), OK öffnet das Programm aller gewählten Sender; Klick auf einen Sender startet waipu.tv. Programmdaten: epgshare01 (XMLTV), alle 6 Stunden aktualisiert.
- Fußball: Tor-Alarm für den eigenen Verein – blinkendes Banner in Lumen, Hinweis auch über anderen Apps (während des Spiels jede Minute geprüft).
- Neues Info-Widget „Sonne & Mond": Sonnenbogen mit aktueller Position, Auf-/Untergang, Mondphase; Details mit Tageslänge, goldener Stunde, nächstem Voll-/Neumond.
- Handy als Fernbedienung (Einstellungen → Fernbedienung): QR-Code scannen, dann Steuerkreuz, Lautstärke, Medientasten, Text/Passwörter eintippen und Apps starten. Geschützt durch einen Code.
- Saisonale Themes: Halloween (Kürbisse, Fledermäuse, Vollmond), Weihnachten (Lichterkette, Schnee), Silvester (Feuerwerk), Ostern (Eier, Blüten) – automatisch zur passenden Zeit, abschaltbar unter Aussehen; auch einzeln als animierter Hintergrund wählbar.

## v1.80
- Speedtest: Download wird jetzt korrekt gemessen (richtiger User-Agent, kein gzip, bei Bedarf neue Verbindung); Upload ebenfalls mit User-Agent. Schlägt der Download fehl, steht der Grund in der Einschätzung.
- Heimnetz-Gerätesuche: zeigt – wenn ermittelbar – den Gerätenamen über der IP (Reverse-DNS beim Router, sonst NetBIOS für Windows-PCs).
- „Jetzt im TV": deutlich mehr Sender (öffentlich-rechtlich, dritte Programme, private Sparten, Nachrichten, Doku, Sport, Sky/Pay-TV, Kinder, Musik). EPG-Quelle unter Info-Widgets frei wählbar, mehrere Quellen (z. B. frei + Sky) mit Leerzeichen getrennt und zusammengeführt.
- Speicher: Wallpaper wird nur noch in Bildschirmauflösung dekodiert (bei 1080p rund die Hälfte weniger Bild-Speicher, sichtbar identisch); Bild-Cache wird bei Speichernot automatisch freigegeben.
- Schnellmenü: sitzt tiefer (nicht mehr vom oberen Rand/Overscan verdeckt) und ist über „Anpassen" frei bestückbar (Lautstärke, Timer, Radio, Boost, Netzwerk, Bluetooth, Einstellungen, Android u. a.).

## v1.81
- Heimnetz-Gerätesuche: zählt nur noch echte Verbindungen (vorher wurde „abgelehnt" mitgezählt, wodurch bei aktivem VPN viel zu viele Geräte erschienen); Scan fest ans Heimnetz gebunden, nicht ans VPN.
- Gerätenamen: zusätzlich über die ARP-Tabelle der Hersteller aus der MAC-Adresse (z. B. AVM, Apple, Samsung) – Reverse-DNS/NetBIOS bleiben als Ergänzung.

## v1.82
- Leistung: „Gründlich beenden" (der langsame Ablauf über die App-Einstellungen) entfernt. Der RAM-Boost beendet ohnehin sofort alle Hintergrund-Apps – das ist die schnelle Methode (wie andere App-Killer); ohne Root geht nichts Weitergehendes.
- RAM-Boost-Ausnahmen: Die aktuell ausgenommenen Apps stehen jetzt oben als eigene Liste und lassen sich dort direkt wieder abwählen; auch Ausnahmen zu deinstallierten Apps erscheinen.
- Neu: Amtliche Unwetterwarnungen (DWD) werden direkt bei Uhr/Wetter angezeigt (farbige Warn-Zeile, wandert in die gewählte Ecke mit); Details beim Öffnen des Wetters. Abschaltbar unter Aussehen → „Unwetterwarnungen (DWD)".

## v1.83
- Schnellmenü: Lautstärke (Leiser/Lauter) ist nicht mehr voreingestellt (bei Bedarf unter „Anpassen" wieder aktivierbar).
- „Anpassen" kann jetzt auch installierte Apps als Kachel ins Schnellmenü legen – ein Klick startet die App. Aufteilung in „Aktionen" und „Apps".

## v1.84
- Aussehen: Kachelgröße jetzt feinstufig (70–135 %) und eigene Einstellung „Abstände" (8–32 dp) für Raster und Reihen – passt sich so an jede Displaygröße an. Alte Einstellung wird übernommen.

## v1.85
- Neu: Türkamera / Kamera-Overlay (Einstellungen → System). Kleines Kamerafenster rechts oben über der laufenden App, für einstellbare Dauer (Standard 30 s), mit Stream-URL (rtsp/https/HLS), Name, Größe und Ton. Jede Taste schließt es.
- Auslösen: manuell (Schnellmenü-Kachel „Türkamera") oder automatisch per Link http://<Box>:8768/show?code=<Code>, den z. B. Home Assistant/ring-mqtt oder Scrypted beim Klingeln aufruft (Ring selbst hat keine offene Schnittstelle, daher Bridge nötig).

## v1.86
- Türkamera / Kamera-Overlay wieder entfernt.

## v1.87
- Radio: Entdecken nach Genre (Pop, Rock, Schlager, News, 80er/90er, Dance, Jazz, Klassik …) und Land (DE/AT/CH/Welt).
- Radio: Songverlauf – zuletzt gehörte Titel, OK sucht den Titel direkt in YouTube Music/YouTube.
- Radio: Einschlaf-Timer (15–90 min) mit sanftem Ausblenden am Ende.

## v1.88
- Neu: Podcasts – Suche (Apple-Verzeichnis), Abonnieren, Abspielen mit ±30 s und automatischem Fortsetzen an der Stelle, wo du aufgehört hast. Erreichbar über das Radio (Button „🎙 Podcasts").

## v1.89
- Podcast-Auswahl: oberster Eintrag bekommt oben etwas Abstand (Fokusrahmen klebt nicht mehr am Rand).

## v1.90
- Info-Seite neu gestaltet (Neo-Glass): Hero-Kopf mit Verlauf und Kennzahl-Chips (Version, Speicher, Android), Kontakt- und Spenden-Karte, Haftung/Datenschutz in dezenten Glaskästen.

## v1.91
- Einstellungs-Seiten an die Dialoge angeglichen: gleiche Abschnitts-Überschriften (Cyan, Versalien) und gleiche Kopfzeile wie in Wetter/Heimnetz/Leistung/Info. Zeilen und Karten waren bereits identisch.

## v1.92
- RAM-Boost-Ausnahmen: eine einzige Liste mit Schaltern – ausgenommene Apps bleiben sichtbar und lassen sich dort direkt wieder ein-/ausschalten (kein Verschwinden mehr nach dem Entfernen).
- Heimnetz-Gerätesuche: Gerätenamen jetzt über mDNS/Bonjour (Chromecast, Apple-TV/HomePod, Drucker, Sonos, NAS, PCs …) – ARP wird auf neuem Android gesperrt. Namen haben Vorrang, sonst Hersteller aus der MAC.
- Neustart (oben rechts): das Android-Menü wird jetzt viel schneller automatisch bestätigt, blitzt also nur noch kurz auf (ganz ohne Menü geht nur mit Root).

## v1.93
- Build-Warnungen bereinigt (veraltetes resolveService bei der Gerätesuche, experimenteller Coil-Cache-Aufruf) – nur Aufräumen, keine Funktionsänderung.

## v1.94
- RAM-Boost beendet jetzt auch vorinstallierte System-Apps (Netflix, YouTube, waipu …) im Hintergrund – vorher wurden genau die übersprungen, daher oft „0 MB“. Geschützt bleiben Launcher, Tastatur, Bedienungshilfen, VPN, Lumen, deine Ausnahmen und laufende Wiedergabe.
- Ausnahmen-Liste zeigt jetzt auch System-Apps (zum gezielten Schützen).
- Boost-Meldung ehrlicher formuliert (freigegebene MB bzw. „lief kaum etwas im Hintergrund“).

## v1.95
- Wetter-Symbole komplett neu: selbst gezeichnete Icons (Sonne, Mond, Wolken, Regen, Schnee, Gewitter, Nebel) statt Emojis. Dadurch kein leeres Kästchen mehr bei Nebel & Co. – überall einheitlich (Widget, Vorhersage, Wetter-Dialog, Bildschirmschoner, kleine Uhr in anderen Apps).

## v1.96
- Kleine Uhr/Wetter-Einblendung erscheint jetzt wirklich nur über fremden Apps: Sobald irgendein Lumen-Fenster vorn ist (Hauptmenü, Einstellungen, Schoner), wird sie ausgeblendet – auch direkt nach dem Neustart der Box.

## v1.97
- Uhr/Wetter-Einblendung korrigiert: Sie flackerte in fremden Apps (das eigene Overlay-Fenster hatte sich selbst ausgeblendet). Jetzt steuert der App-Lebenszyklus die Sichtbarkeit – sichtbar in fremden Apps, ausgeblendet nur in Lumen selbst (Hauptmenü, Einstellungen, Schoner), auch nach Neustart.

## v1.98
- News-Ticker: mehr Quellen zum An-/Abwählen – überwiegend deutsch: Tagesschau, SPIEGEL, ZEIT, Süddeutsche, FAZ, WELT, n-tv, FOCUS, Tagesspiegel, stern, Sportschau, kicker, SPORT1, heise, Golem, t3n und (englisch) BBC World.

## v1.99
- Neu: TV-Ziffernblock (Einstellungen → System → „Ziffernblock (ADB)"). Per angelernter Taste ein-/ausschaltbar; Ziffern mit den Pfeiltasten wählen, mehrstellige Zahl (z. B. 155) sammeln, OK sendet sie – optional mit Enter – per ADB an die laufende App (z. B. waipu), genau wie atvtools. Ziel-IP einstellbar (leer = diese Box), Verbindungstest inklusive. Braucht „ADB über Netzwerk" an der Box und einmalige Bestätigung des Lumen-Schlüssels. Kein Root/Shizuku nötig.

## v2.00
- Wallpaper-Galerie: zehn neue Themen-Sammlungen von Wallhaven (Natur, Weltall & Sterne, Städte & Architektur, Minimalistisch, Abstrakt & Muster, Dunkel & Neon, Tiere, Autos & Technik, Fantasy & Digital Art, Jahreszeiten) – ohne Konto, nur jugendfrei, mindestens 1920×1080.
- Wallpaper lassen sich jetzt auch wieder abschalten: OK auf ein bereits hinzugefügtes Bild (mit ✓) nimmt es wieder heraus; OK auf ein neues fügt es hinzu. Die Infozeile zeigt an, was OK gerade tut.
- Flackern beim Zurückschalten behoben: Lumen erscheint jetzt ohne Launcher-Übergangsanimation schlagartig deckend, damit auf schwachen Boxen nicht mehr kurz der Thomson-Startbildschirm durchblitzt.
- Uhr/Wetter-Overlay erscheint jetzt wirklich nur über echten App-Inhalten – nicht mehr auf fremden Launcher-/Startbildschirmen (z. B. Thomson- oder waipu-Home) und nicht in Lumen selbst. Zusätzlich wird es direkt nach dem Booten kurz unterdrückt und erscheint nie, solange Lumen die Vordergrund-App ist, damit es nicht beim Hochfahren aufblitzt.
- Nach dem Booten wird Lumen früher und mehrfach nach vorn geholt, damit der Stock-Launcher beim Reboot möglichst kurz zu sehen ist.
- Ziffernblock: optionales automatisches Senden ohne OK – nach einstellbarer Pause (Aus / 2 / 3 / 5 Sek.) wird die eingetippte Zahl von selbst abgeschickt, wie bei einer echten Nummerntaste. Der Countdown wird im Ziffernblock angezeigt; OK sendet weiterhin sofort, Zurück bricht ab. (Einstellungen → System → Ziffernblock)
- Erststart: Bei einer frischen Installation erscheint jetzt ganz zuerst eine Sprachauswahl (Deutsch / English); danach läuft Lumen in der gewählten Sprache. Bestehende Installationen bleiben unverändert.
