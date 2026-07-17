# Phase 3: Compose implementation and phone validation

Date: 2026-07-17

Branch: `feature/figma-ui-redesign`

Build: `0.2.0 (16)`

APK: `SonyEdge-v0.2.0-16-debug-20260717-141230.apk`

## Implementation

The Figma direction is now reflected in the Compose application shell:

- Three stable destinations remain Browse, Imports and Settings.
- Browse continues to map to the internal Camera and Library states.
- The media grid uses adaptive minimum card widths instead of a fixed column count.
- Widths below 600 dp use bottom navigation; widths at or above 600 dp use a
  navigation rail.
- The selection bar exposes Select All, Invert, Clear and Import while keeping
  compact widths icon-first.
- The visual system uses the Figma background, surface, border, text, primary,
  success and preview colors.
- Folder lists use adaptive columns on expanded widths.
- Preview controls use a dark, non-flashing surface with animated visibility.
- Existing DMS, selection, preview, image cache, EXIF and download behavior was
  preserved.

## Build validation

`./gradlew.bat --no-daemon :app:assembleDebug --console=plain` completed
successfully using Android Studio JBR and Android SDK 35.

APK metadata was checked with `aapt`:

- Package: `com.codex.sonyedge`
- Version code: `16`
- Version name: `0.2.0`
- Minimum SDK: `26`
- Target SDK: `35`

## Phone and camera validation

Device:

- Serial: `909e29e1`
- Model: `CPH2025`
- Install result: success

Camera-connected checks:

- Connected to the camera DMS endpoint successfully.
- Loaded the Date container and displayed four date folders.
- Opened the `2026-7-10` folder containing 76 items.
- Loaded the adaptive two-column thumbnail grid.
- Scrolled rapidly to later items and returned to the first items; cached
  thumbnails remained correct and visible.
- Entered selection mode by long press and verified the selection action bar.
- Opened full-screen preview.
- Swiped from item 1 to item 2 and confirmed title, count and image stayed in sync.
- Double-tap zoomed the current image without changing pages.
- Imported `DSC06741.JPG`.
- Download result: 1 succeeded, 0 failed.
- Saved file: `/sdcard/DCIM/Sony Picture/DSC06741.JPG`.
- Saved size: `29,261,824` bytes.
- The Imports page reported 27.9 MB, 752 KB/s average and 38 seconds elapsed.
- No crash entries were present in the Android crash log during the tested flow.

## Remaining validation

The current phone covers the production medium-width path. The expanded 600+ dp
navigation rail still needs a screenshot check when a tablet or emulator is
available. Its code path compiled successfully.
