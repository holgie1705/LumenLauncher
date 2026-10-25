# Lumen Launcher

A fast, lightweight, privacy-friendly home launcher for Android TV, written in Kotlin with Jetpack Compose for TV.

Lumen aims to be a full alternative to launchers like Projectivy, CleanMyTV or Free My TV: clean look, low RAM use, no accounts, no ads, no tracking.

> **Status: experimental.** Lumen is a hobby project. It is developed and tested on a single box (a waipu.tv Android TV device). Behaviour on other devices may differ. Use it at your own risk (see [Disclaimer](#disclaimer)).

## Features

**Home screen**
- Rows (apps, favorites, now playing, radio, web widgets), grid or sidebar layout, or a free layout where you place your favorites anywhere on the screen
- Tile styles: banner, wide, compact, icons, round, poster, card
- Adjustable tile transparency so the wallpaper shines through
- Clock (digital, minimal, analog) and weather (Open-Meteo, no key needed), position of header and tiles selectable
- Small clock and weather in the top left corner while another app is running (optional)
- Move tiles with the D-pad, favorites row, custom row order
- Calm focus frame, glow or lift effect

**Wallpapers and themes**
- Pictures, GIFs and videos from a local folder, rotating automatically
- Built-in wallpaper gallery (Bing daily image, Wikimedia Commons, NASA, picsum.photos)
- Icon pack support (ADW/Nova/Apex format, `appfilter.xml`)
- Looks: Pure, OLED, Aurora, Glass; accent color; modern fonts (Outfit, Plus Jakarta Sans, Manrope, Sora, Bai Jamjuree) or your own fonts
- Save and share your look as a theme file

**Radio**
- Station search through radio-browser.info, background playback, "now playing" card with selectable position
- Station logos with sensible fallbacks

**Web widgets**
- Show web pages as live tiles on the home screen, open them fullscreen and interactive

**System integration**
- Home key override, boot start, auto standby and sleep timer, remote shortcuts (needs the accessibility service)
- Parental controls with PIN, enforced outside Lumen too
- Restart button (root, system permission, or the Android power menu)
- Optional VPN indicator dot (green = VPN active, red = none)
- Plugin API: other apps can add rows through a ContentProvider
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
| READ_TV_LISTINGS, READ_EPG_DATA | "Continue watching" rows from other apps (READ_TV_LISTINGS can be granted at runtime or with `adb shell pm grant com.lumen.tv android.permission.READ_TV_LISTINGS`) |
| Accessibility service (user-enabled) | home key, shortcuts, standby, parental controls, mini clock, restart click in the power menu |
| Notification access (optional) | "Now playing" row |

The accessibility service reads window content only to press "Restart" in the Android power menu. Nothing is stored or sent.

## Privacy

Lumen has no accounts, no ads and no analytics. The only online requests are:
- **Open-Meteo** – weather and place search (only when weather is on)
- **radio-browser.info** – radio station search
- **Wallpaper gallery** – Bing, Wikimedia Commons, NASA, picsum.photos (only when you open the gallery)
- **ipapi.co** – country of your public IP in the VPN dot menu (only while a VPN is active)
- Whatever web widgets you add yourself

All settings stay on the device.

## Files on the device

`/sdcard/Android/data/com.lumen.tv/files/`
- `wallpapers/` – images (jpg, png, webp), GIFs, videos (mp4, webm, mkv)
- `fonts/` – your own fonts (ttf, otf)
- `themes/` – theme files created with "Save look as theme"

## Plugin API

An app can add rows to Lumen with an exported ContentProvider:

```xml
<provider android:name=".MyRows" android:authorities="com.example.rows" android:exported="true">
    <intent-filter><action android:name="com.lumen.tv.PLUGIN"/></intent-filter>
</provider>
```

`content://com.example.rows/rows` returns a cursor with the columns `row` (row title), `title`, `image` (URL or content URI) and `intent` (`Intent.toUri(URI_INTENT_SCHEME)`). Lumen shows every `row` as its own row under "Plugins".

## Tech

Kotlin 2.0, Jetpack Compose for TV (`androidx.tv:tv-material`), Media3 ExoPlayer, Coil 2, AGP 8.5, minSdk 26, targetSdk 34. State is held in a ViewModel with `StateFlow`; settings are stored as JSON in SharedPreferences.

## Known limitations

- Tested on one device only; other boxes may behave differently (home key handling, power menu, overlay).
- The power-menu restart click depends on the texts of your firmware's menu.
- "Continue watching" needs `READ_TV_LISTINGS` (grant it under Settings → Rows). Only entries the source apps mark as searchable are returned; on older firmwares the old system-only permission applies and the row stays empty.
- The mini clock may be covered by apps that draw over everything in fullscreen.
- Release builds use R8 minification; if something breaks, please open an issue with details.

## Disclaimer

Lumen is provided "as is", without warranty of any kind. Features such as app disabling (root/ADB), restart and the accessibility service interfere with your device. Use them only if you understand the consequences. The author is not liable for damage or data loss. Wallpapers from the gallery belong to their authors; see the license shown below each preview. Station data comes from radio-browser.info and weather data from Open-Meteo.

## Credits and licenses

- Fonts: Bai Jamjuree, Outfit, Plus Jakarta Sans, Manrope, Sora – SIL Open Font License 1.1. The license texts with the original copyright lines are in the [`licenses/`](licenses) folder.
- Libraries: AndroidX / Jetpack Compose, Media3, Coil – Apache License 2.0
- Weather: [Open-Meteo](https://open-meteo.com) · Radio: [radio-browser.info](https://www.radio-browser.info)

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
