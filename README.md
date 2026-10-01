# Glyph Clock

Часы для Nothing Phone (3a) / (3a) Pro в стиле классического Nothing: будильник, мировое время, таймер, секундомер.
Будильник и таймер подсвечивают Glyph по записи из мелодии, созданной в Glyph Composer (`.ogg`, тег `AUTHOR`).

## Сборка

```
gradlew.bat assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk` (подписан debug-ключом).
В `gradle.properties` прописаны пути к JDK и кэшу Gradle конкретной машины (`D:/jdk-17`, `D:/.gradle`) — поправьте под себя.

Для Glyph на Android ниже 16 один раз по USB: `adb shell settings put global nt_glyph_interface_debug_enable 1`.

## Сторонние материалы

- `app/libs/glyph-matrix-sdk-2.0.aar` — Glyph Developer Kit от Nothing.
- `app/src/main/res/font/matrix_print.ttf` — MatrixSans Print, SIL OFL 1.1 (github.com/FriedOrange/MatrixSans).

## Fonts

MatrixSans Print (dot matrix) and Oranienbaum (headlines), both SIL Open Font License 1.1.
