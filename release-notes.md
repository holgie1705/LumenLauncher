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
