## Lumen Launcher 2.08

New design “Spatial”, three new languages & a cleaner settings menu.

### What's new
- **New Spatial design**: the selected row curves as a 3D arc with depth, reflection and a soft glow around the focused tile. A new top bar with a large thin clock, date & weather, fine line icons and a glass status pill (Wi-Fi, VPN, RAM, free storage). New glass settings panel on the right with categories and sub-pages, glass long-press menu and the new font Plus Jakarta Sans everywhere.
- **The new look applies to every layout** – Shelves, Grid, Sidebar and Free only change how the tiles are arranged; menus, top bar and background stay in the new design.
- **Clean home screen**: only the selected row is shown – press down to get to the next one (Settings → Appearance → “Show only the selected row”). Tile position top / center / bottom is adjustable; default is bottom, right above the news ticker.
- **All tiles the same size** in every row (Continue watching, radio, widgets, info). Radio stations are now tiles with logo and name.
- **Tiles in the 3D arc**: glass tiles as designed, or your own tile style (banner, icons, poster …) incl. spacing.
- **Clock & weather in other apps** as a small glass pill – optionally fully transparent (just text).
- **Calm background**: the color tint following the selected app and the rising light dots are now off by default and can be switched on separately.
- **New languages**: Italiano, Français, Español (Settings → Tools → Language).
- **Cleaned up**: settings without effect in the new design were removed (top bar position, clock style, menu color, font choice), as were the “OK opens · long-press for options” hints on the home screen.

---

## Lumen Launcher 2.07

Neon volume & box temperature.

### What's new
- **Neon volume**: the custom volume display is now a glowing ring of 32 segments (cyan → violet → pink) with a big value in the middle, on the right edge. It now reacts to every volume change – also on boxes where Android handles the keys itself (e.g. the waipu box). Night mode caps the volume there too.
- **Box temperature**: if the temperature files are locked (as on the waipu box), Lumen asks Android's thermal service over ADB and shows the real value under Performance. The first time, the box asks once whether Lumen may use ADB – choose "Always allow".
- **Start animation removed** – Lumen opens straight to the home screen again.

---

## Lumen Launcher 2.06

Quick switch, volume, start animation & goal banner.

### What's new
- **Quick switch to the last app**: press Home twice quickly to jump to the app you used before – like Alt-Tab, e.g. between waipu and YouTube (Settings → Remote). There is also a "Switch to last app" action for any key.
- **Custom volume display** in Lumen style instead of the Android bar, with icon and percentage. Plus a **night mode** that caps the volume in the evening (e.g. 10 pm–7 am at 40 %). If your TV handles the volume itself, nothing changes.
- **Start animation**: when the box powers on, the Lumen logo lights up briefly before the home screen; an autostart app opens afterwards. Can be turned off and previewed under Settings → System.
- **Goal alert as a big banner** over other apps too (waipu, Netflix …): crests, score and a pulsing frame instead of the small Android message. Try it with "Test goal alert" under Info widgets → Football.

---

## Lumen Launcher 2.05

Transfer settings & updates via GitHub.

### What's new
- **Transfer settings over Wi-Fi** (Settings → System): open "Receive settings from another box" on the target, then "Send settings to another box" on yours – the target is found automatically (or by IP); confirm with OK there and you're done. Layout, rows, favorites, widgets, radio, podcasts, colors and font are copied. PIN, parental controls, VPN dot, printer and remote code stay separate per box; the weather place can be taken over or kept.
- **Updates via GitHub**: new versions are published as GitHub releases; every box running Lumen then shows "Update available" by itself and installs with one click.

---

## Lumen Launcher 2.04

Cleaner info page & football fix.

### What's new
- **Redesigned info page**: a calm app card (logo, one line, version · Android · memory), a developer card and uniform link rows for **Telegram, email, GitHub and Buy me a coffee**. Privacy and disclaimer are folded away and open on demand. The dialog is now opaque, so the menu behind no longer shows through.
- **Football widget**: teams of the foreign leagues (England, Spain, Italy, France – 1st and 2nd tier each) load again. Data now comes from ESPN's public API (free, no key, with club crests) instead of TheSportsDB, whose free tier no longer returns team lists. German leagues stay on OpenLigaDB.

---

## Lumen Launcher 2.03

File manager & radio.

### What's new
- **Tidier menus**: the settings home screen is now lean — clear categories (Appearance, Rows, Standby, Remote, **Tools**, Parental, System, Info). All the utilities (file manager, performance, info/web widgets, VPN dot, 3D printer, number pad, language + Android quick links) are grouped under the new **Tools** sub-page, which also slims down the System page.
- **File manager** (Settings → File manager): browse folders, **copy / cut / paste**, rename, delete (with confirm), create folders, open files and **install APKs** directly. Quick targets for internal storage, Downloads, SD/USB and the Lumen folder. On Android 11+ grant "all files access" once at the top. OK opens a folder or shows actions for a file; long-press OK opens actions anywhere.
- **Radio**: added the **Punk** genre.
- **Football widget**: foreign leagues added — **Premier League & Championship (England), La Liga & La Liga 2 (Spain), Serie A & Serie B (Italy), Ligue 1 & Ligue 2 (France)**, each with team picker, table and fixtures. German leagues keep using OpenLigaDB; foreign ones use TheSportsDB (free). League IDs are resolved automatically by country + name.

---

## Lumen Launcher 2.02

Performance & printer polish for weak boxes.

### What's new

**Quality-of-life**
- **Force stop right from the app menu** (long-press an app): Lumen opens the app info, auto-taps "Force stop" + "OK" and returns — no detour through Android settings.
- The **clock/weather overlay** in other apps is now reliable: transient windows (volume bar, keyboard) no longer hide it, and a watchdog rebuilds it if the system drops it.
- **Clock/weather over the fullscreen camera**, small, top-right.
- **Radio**: country picker now also offers UK, US and Canada.
- **News ticker**: extra English-language sources — The Guardian, Sky News (UK), NPR, New York Times, CBS News (US), CBC, CTV News (Canada); each toggleable.
- English UI fixes (accessibility service shown as "Lumen Home key").

**Smarter RAM management**
- The **RAM boost** now clears the background of **every** installed app — including icon-less background services that hold the most memory — in two quick passes. Core system services (framework, SystemUI, telephony/Bluetooth, keyboard, Play services, launchers) stay protected at all times.
- New **Auto-boost** (Settings → Performance): automatically clears the background when you return to the home menu, but only when RAM usage is above a threshold you set (default 75 %). Optional.
- New **Clear app on leaving** (exit-kill): when you leave an app, that specific app is cleared from the background (never while audio is still playing). Optional.

**3D printer notifications**
- New: a notification on **print finished** and **print error**, even when the camera tile isn't open (requires the bridge URL). Toggle it in the Bambu dialog.

**Backup without credentials**
- Settings export no longer writes the printer access code or remote code to the file; importing keeps your existing codes.

**Screensaver polish**
- Smooth fade-in, clock/weather drift vertically to avoid burn-in, better legibility (scrim + shadow), a subtle progress line, and extra NASA space imagery in the rotation.

---

## Lumen Launcher 2.00

A fast, lightweight, privacy-friendly Android TV launcher — no accounts, no ads, no tracking. Open source (GPL-3.0), Kotlin + Jetpack Compose for TV.

### Highlights in this release

**First start & language**
- New: on a fresh install Lumen now opens with a **language picker (Deutsch / English)** and then runs in the language you chose. Existing installs are untouched.

**TV number pad (ADB)**
- New: an on-screen **number pad** for remotes without number keys. Pop it up with a learned key while watching, pick digits with the D-pad, collect a multi-digit number (e.g. 155) and send it straight into the running app (e.g. waipu) over ADB — exactly like atvtools. No root, no Shizuku.
- Optional **auto-send**: after a configurable pause (off / 2 / 3 / 5 s) the typed number is sent on its own, like a real number key — with a visible countdown. OK still sends instantly, Back cancels.
- Target IP is configurable (empty = this box), with a built-in connection test. Needs "ADB over network" on the box and a one-time key approval.

**Camera & 3D printer status (new)**
- Web widgets can now play **live camera streams**: an `rtsp://` address (e.g. a Bambu Lab printer via go2rtc) or an `.m3u8` stream plays natively via ExoPlayer — as a tile and fullscreen, with automatic reconnect. Web widgets can now also be **edited** (name/address) afterwards.
- New **Bambu Lab printer status** overlay: open the camera fullscreen and a sleek glass bar shows live job, stage, progress bar, ETA, nozzle/bed/chamber temps, layer, speed, part-cooling fan, Wi-Fi, AMS humidity, chamber light, hotend type and AMS spools (colour, material, remaining % and grams) — plus an error warning. Two ways to feed it: a local **status bridge** (HTTP JSON, works even in cloud mode) or the printer's **local MQTT** (LAN-only mode).

**Wallpaper gallery**
- Ten new themed collections from **Wallhaven** (Nature, Space & stars, Cities & architecture, Minimal, Abstract & patterns, Dark & neon, Animals, Cars & tech, Fantasy & digital art, Seasons) — no account, SFW only, at least 1920×1080.
- Wallpapers can now be **removed again**: OK on a picture already added (marked ✓) takes it back out; OK on a new one adds it. The info line shows what OK will do.

**Overlay, launcher & boot**
- The clock/weather **overlay** now shows **only over real app content** — never on a launcher/home screen (stock launcher, waipu home) and never in Lumen itself. It is also suppressed right after boot and whenever Lumen is the foreground app, so it no longer flashes on startup.
- **No more flicker when returning home**: Lumen now appears instantly and fully opaque, so on weak boxes the stock launcher doesn't briefly show through.
- After a reboot Lumen is brought to the front earlier and repeatedly, so the stock launcher is visible as briefly as possible.

**News ticker**
- More sources, mostly German, each individually toggleable (Tagesschau, SPIEGEL, ZEIT, Süddeutsche, FAZ, WELT, n-tv, FOCUS, Tagesspiegel, stern, Sportschau, kicker, SPORT1, heise, Golem, t3n and BBC World).

### Install
Download `app-release.apk` below and install it on your box (sideload or `adb install -r app-release.apk`). Lumen then checks GitHub for newer releases and updates itself with one click.

Full history: see [CHANGELOG.md](CHANGELOG.md) (German).
