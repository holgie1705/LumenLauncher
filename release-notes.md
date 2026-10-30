## Lumen Launcher 2.00

A fast, lightweight, privacy-friendly Android TV launcher — no accounts, no ads, no tracking. Open source (GPL-3.0), Kotlin + Jetpack Compose for TV.

### Highlights in this release

**First start & language**
- New: on a fresh install Lumen now opens with a **language picker (Deutsch / English)** and then runs in the language you chose. Existing installs are untouched.

**TV number pad (ADB)**
- New: an on-screen **number pad** for remotes without number keys. Pop it up with a learned key while watching, pick digits with the D-pad, collect a multi-digit number (e.g. 155) and send it straight into the running app (e.g. waipu) over ADB — exactly like atvtools. No root, no Shizuku.
- Optional **auto-send**: after a configurable pause (off / 2 / 3 / 5 s) the typed number is sent on its own, like a real number key — with a visible countdown. OK still sends instantly, Back cancels.
- Target IP is configurable (empty = this box), with a built-in connection test. Needs "ADB over network" on the box and a one-time key approval.

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
