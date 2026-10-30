# Lumen Launcher

A fast, lightweight, privacy-friendly home launcher for Android TV, written in Kotlin with Jetpack Compose for TV.

Lumen aims to be a full alternative to launchers like Projectivy, CleanMyTV or Free My TV: clean look, low RAM use, no accounts, no ads, no tracking.

> **Status: experimental.** Lumen is a hobby project. It is developed and tested on a single box (a waipu.tv Android TV device). Behaviour on other devices may differ. Use it at your own risk (see [Disclaimer](#disclaimer)).

## Features

**Home screen**
- Rows (apps, favorites, now playing, radio, web widgets), grid or sidebar layout, or a free layout where you place your favorites anywhere on the screen
- Tile styles: banner, wide, compact, icons, round, poster, card
- Adjustable tile transparency so the wallpaper shines through
- Clock (digital, minimal, analog) and weather (Open-Meteo, no key needed); clock & weather placeable in any corner, with official DWD severe-weather alerts
- Stepless tile size (70–135 %) and adjustable spacing to fit any display
- Small clock and weather in the top left corner while another app is running (optional)
- Move tiles with the D-pad, favorites row, custom row order
- Five tile focus styles: frame, neon glow, lift (3D), glass, underline

**Wallpapers and themes**
- Pictures, GIFs and videos from a local folder, rotating automatically
- Built-in wallpaper gallery (Bing daily image, Wikimedia Commons, NASA, picsum.photos)
- Icon pack support (ADW/Nova/Apex format, `appfilter.xml`)
- Animated drawn backgrounds (starry sky, northern lights, color flow, bokeh, rain, snow) and an optional background that follows time of day and weather
- Clock designs: digital, minimal, analog, flip clock, word clock, neon
- Modern "Neo glass" menus: floating settings panel with colorful category tiles, switches, version badge
- Menu colors: night blue, graphite, mocha & white, violet, emerald, bordeaux, midnight, or taken from the wallpaper; adjustable menu text size
- Looks: Pure, OLED, Aurora, Glass; accent color; modern fonts (Outfit, Plus Jakarta Sans, Manrope, Sora, Bai Jamjuree) or your own fonts
- Save and share your look as a theme file

**Radio & podcasts**
- Station search through radio-browser.info, background playback
- Browse stations by genre (pop, rock, schlager, news, 80s/90s, dance, jazz, classical …) and country (DE/AT/CH/world)
- Song history of recently played tracks, one click searches a track on YouTube Music
- Sleep timer (15–90 min) with a gentle fade-out
- Radio screen with a large "now playing" card and a station grid (favorites, popular, search); long-press to save a favorite
- Compact "Live radio" card on the home screen with spectrum analyzer and stop button, selectable position
- Station logos from the TuneIn directory, with favicon fallbacks
- Podcasts: search (Apple directory), subscribe, play with ±30 s skip and automatic resume where you stopped

**Screensaver**
- Clock (OLED-friendly), your own photos, or a web gallery (Bing, Unsplash, Wikimedia) with crossfade and slow zoom, large clock, date and weather

**Info widgets and news**
- "Info" row with football (OpenLigaDB: live score, next/last match, table), large weather (hourly values, DWD rain radar), countdowns, a note and a home network tile (ping, speed test, device scan)
- News ticker with headlines from Tagesschau, Sportschau, kicker, heise or SPIEGEL
- Edit the note and countdowns from your phone's browser (local network, code protected)

**Web widgets**
- Show web pages as live tiles on the home screen, open them fullscreen and interactive

**System integration**
- Info widgets: football with goal alert, "On TV now" guide (XMLTV), sun & moon, weather, countdown, note, home network with speed test
- Phone as remote: scan a QR code, then D-pad, volume, media keys, typing text and launching apps from your phone
- Seasonal themes: Halloween, Christmas, New Year's Eve and Easter backgrounds, switched on automatically
- Home key override, boot start, auto standby and sleep timer, remote shortcuts with key learning and remote detection (needs the accessibility service)
- Parental controls with PIN, enforced outside Lumen too
- Restart button (root, system permission, or the Android power menu)
- Quick menu, fully customizable (sleep timer, radio, screensaver, RAM boost, network, Bluetooth, your own apps …)
- Update check against GitHub releases with one-click install
- Setup assistant on first start
- Performance screen: memory, storage and temperature gauges, RAM boost with an exception list, cache cleanup (own cache; all apps with root), system info
- Optional VPN indicator dot (green = VPN active, red = none)
- German and English interface

## Requirements

- Android TV / Google TV device, Android 8.0 (API 26) or newer
- Android Studio (Koala or newer) or Gradle, JDK 17

## Build

```
gradle assembleDebug      # quick test build
gradle assembleRelease    # optimized build (recommended for daily use)
```

The release APK is signed with the debug key so it installs without extra setup. Output:
`app/build/outputs/apk/release/app-release.apk`.

> Debug builds are noticeably slower than release builds on weak TV hardware. Use the release build for daily use.

## Install

**Easiest:** download the latest `app-release.apk` from the [Releases](../../releases) page and install it on your box (sideload, or `adb install`). Once Lumen runs, it checks GitHub for newer releases and updates itself with one click.

**With adb:**
```
adb connect <TV-IP>:5555
adb -s <TV-IP>:5555 install -r app-release.apk
```

If several devices are connected, always add `-s <TV-IP>:5555` to your adb commands.

## Make Lumen your default launcher

1. Press the Home key and choose **Lumen Launcher** with "Always". If your box forgets the choice, set it explicitly:
   ```
   adb shell cmd package set-home-activity com.lumen.tv/.MainActivity
   ```
2. In Lumen: **Settings → System**
   - turn on **Open Lumen at system start**
   - open **Accessibility service "Lumen Home-Taste"** and enable it (needed for the home key, shortcuts, standby, parental controls and the mini clock)
   - optionally turn on **Override stock launcher**
3. Grant **Display over other apps** to Lumen in the Android settings (needed for boot start and the VPN dot).

### Removing another launcher

Only do this for launchers you do not need. Some boxes (for example waipu.tv) ship a TV app that also registers as a home launcher, so removing it would also remove the app.

```
adb shell pm uninstall --user 0 <package>
```

Undo with `adb shell cmd package install-existing <package>`. Some firmwares block `pm disable-user`; `pm uninstall --user 0` removes the app for your user only.

## Permissions

| Permission | Why |
| --- | --- |
| INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE | weather, radio, wallpaper gallery, web widgets |
| SYSTEM_ALERT_WINDOW | VPN dot, boot start |
| RECEIVE_BOOT_COMPLETED | open Lumen after boot |
| FOREGROUND_SERVICE (+ media playback, special use) | radio playback in the background |
| POST_NOTIFICATIONS | radio notification |
| REQUEST_DELETE_PACKAGES | uninstall apps from the long-press menu |
| KILL_BACKGROUND_PROCESSES | RAM boost in the performance screen |
| REQUEST_INSTALL_PACKAGES | install a new Lumen version from GitHub (update check) |
| READ_TV_LISTINGS, READ_EPG_DATA | "Continue watching" rows from other apps (READ_TV_LISTINGS can be granted at runtime or with `adb shell pm grant com.lumen.tv android.permission.READ_TV_LISTINGS`) |
| Accessibility service (user-enabled) | home key, shortcuts, standby, parental controls, mini clock, restart click in the power menu |
| Notification access (optional) | "Now playing" row |

The accessibility service reads window content only to press "Restart" in the Android power menu. Nothing is stored or sent.

## Privacy

Lumen has no accounts, no ads and no analytics. The only online requests are:
- **Open-Meteo** – weather and place search (only when weather is on)
- **radio-browser.info** – radio station search
- **TuneIn (opml.radiotime.com)** – station logos (only the station name is sent)
- **Wallpaper gallery** – Bing, Wikimedia Commons, NASA, picsum.photos (only when you open the gallery, or when the web gallery screensaver is running)
- **OpenLigaDB** – football matches and tables (only with the football widget)
- **RSS feeds** of the news sources you select (only with the news ticker)
- **Deutscher Wetterdienst** – rain radar image; **Cloudflare** – speed test file (only when you start it)
- **GitHub API** – checks for a newer Lumen release (at most every 6 hours)
- **ipapi.co** – country of your public IP in the VPN dot menu (only while a VPN is active)
- Whatever web widgets you add yourself

All settings stay on the device.

## Files on the device

`/sdcard/Android/data/com.lumen.tv/files/`
- `wallpapers/` – images (jpg, png, webp), GIFs, videos (mp4, webm, mkv)
- `fonts/` – your own fonts (ttf, otf)
- `themes/` – theme files created with "Save look as theme"

## Tech

Kotlin 2.0, Jetpack Compose for TV (`androidx.tv:tv-material`), Media3 ExoPlayer, Coil 2, AGP 8.5, minSdk 26, targetSdk 34. State is held in a ViewModel with `StateFlow`; settings are stored as JSON in SharedPreferences.

## Known limitations

- Tested on one device only; other boxes may behave differently (home key handling, power menu, overlay).
- The power-menu restart click depends on the texts of your firmware's menu.
- "Continue watching" needs `READ_TV_LISTINGS` (grant it under Settings → Rows). Only entries the source apps mark as searchable are returned; on older firmwares the old system-only permission applies and the row stays empty.
- The mini clock may be covered by apps that draw over everything in fullscreen.
- "Override stock launcher" only reacts to the real home screen of the stock launcher; waipu.tv apps are never overridden.
- After `adb install -r` Android may bring the last used app to the front; run `adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME` to return to Lumen.
- Release builds use R8 minification; if something breaks, please open an issue with details.

## Disclaimer

Lumen is provided "as is", without warranty of any kind. Features such as app disabling (root/ADB), restart and the accessibility service interfere with your device. Use them only if you understand the consequences. The author is not liable for damage or data loss. Wallpapers from the gallery belong to their authors; see the license shown below each preview. Station data comes from radio-browser.info and weather data from Open-Meteo.

## Credits and licenses

- Fonts: Bai Jamjuree, Outfit, Plus Jakarta Sans, Manrope, Sora – SIL Open Font License 1.1. The license texts with the original copyright lines are in the [`licenses/`](licenses) folder.
- Libraries: AndroidX / Jetpack Compose, Media3, Coil – Apache License 2.0
- Weather: [Open-Meteo](https://open-meteo.com) · Radio: [radio-browser.info](https://www.radio-browser.info) · Station logos: TuneIn

## License

Copyright (C) 2026 Holger Schmidt

Lumen Launcher is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License, version 3](LICENSE) as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

Third-party components keep their own licenses (see [Credits and licenses](#credits-and-licenses)).

## Author and contact

Made by **Holger Schmidt** (HOLGIE).

- Telegram: [@HOLGeH](https://t.me/HOLGeH)
- Email: holgie4711@proton.me
- Support the project: [buymeacoffee.com/holgie](https://buymeacoffee.com/holgie)

## Changelog

See [CHANGELOG.md](CHANGELOG.md) (German).
