**English** | [Русский](README.ru.md)

# Glyph Clock

A clock app for the Nothing Phone (3a) / (3a) Pro in the classic Nothing style: alarms, world clock, timers and stopwatch. Alarms and timers drive the Glyph lights from the recording inside a melody made in Glyph Composer (`.ogg`, tag `AUTHOR`).

## Features

- **Alarms** with a round dial picker that follows the phone's 12/24-hour setting; the editor is a bottom sheet (time, days, label, melody, vibration; more options unfold)
- **Skip the next ring** of a repeating alarm, with undo
- **World clock**, **timers** (several at once, with a dialler-style keypad) and a **stopwatch** with laps
- **Glyph light** played along with the sound; the preview shows the camera ring and the three glyph strips
- **Night mode** that dims the glyphs between chosen hours
- Melody picked in the system's own list (Nothing Signals, Glyph Composer, My sounds); default melodies for alarms and timers
- Alarm volume, gradual volume increase (5–120 s), light/sound offset
- Quick Settings tile with the next alarm
- Russian and English interface, switchable in Settings

## Install

Download `GlyphClock.apk` from the [latest release](../../releases/latest) and open it on the phone. Android 14 or newer. The build is signed with a debug key.

## Glyph notes

- Turn on **Glyph Interface** in the phone settings.
- On Nothing OS older than Android 16, run once over USB: `adb shell settings put global nt_glyph_interface_debug_enable 1` (lasts 48 hours).
- The Glyph kit only drives the LEDs for an app that is on screen, so the app asks for the "display over other apps" permission to open the ringing screen by itself.
- Sounds built into Nothing OS (Nothing Signals / Machines) play normally, but their light is kept by the system, so they have no Glyph light here. Use compositions made in Glyph Composer.

## Build

```
gradlew.bat assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk`. `gradle.properties` points at a JDK and a Gradle cache on one specific machine (`D:/jdk-17`, `D:/.gradle`): change them for yours.

## Third-party material

- `app/libs/glyph-matrix-sdk-2.0.aar`: Glyph Developer Kit by Nothing.
- `app/src/main/res/font/matrix_print.ttf`: MatrixSans Print, SIL OFL 1.1 ([FriedOrange/MatrixSans](https://github.com/FriedOrange/MatrixSans)).
- `app/src/main/res/font/oranienbaum.ttf`: Oranienbaum, SIL OFL 1.1.
