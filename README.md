# SonyEdge

Android prototype for connecting to a Sony A7R III camera Wi-Fi network and importing photos through the old Imaging Edge / PlayMemories-style Wi-Fi services.

## What it does

- Uses a Kotlin + Jetpack Compose launcher UI (`ComposeMainActivity`) with the old Java View activity kept only as a legacy reference while the Compose migration is completed.
- Discovers Sony camera/DMS endpoints on the camera Wi-Fi network.
- Caches the working DMS endpoint so `Browse` can reconnect quickly after the first probe.
- Restores the last opened camera folder on the next browse, falls back to the camera root if that folder is no longer available, and provides a `Root` action.
- Main `Browse` now opens the camera root so folders can be selected instead of jumping straight into the last photo folder.
- Browses the camera's DLNA ContentDirectory by folder, with paged reads for large folders.
- Enters a camera folder immediately and streams paged DMS results into the Library grid as pages arrive instead of waiting for the whole folder to finish loading.
- Uses a compact album-folder page with a path/count header, small folder rows, and a lightweight indeterminate loading bar so tapping a date folder gives immediate visual feedback while the camera is still responding.
- Renders Albums with a RecyclerView album grid instead of a ScrollView/LinearLayout stack, so camera folders behave more like an import gallery and scale better when there are many date folders.
- Album cards use a gallery-cover style preview block, count badge, folder type label, and compact open affordance instead of plain file-manager rows.
- Album actions are compact right-aligned controls, and folder cards avoid pre-scanning child folders for cover thumbnails so opening remains immediate on slow camera Wi-Fi.
- Keeps folder-level browsing stable in Albums, switches to Library only after photo items arrive, and ignores stale browse callbacks so slow camera Wi-Fi responses cannot overwrite a newer folder selection.
- Opens likely photo folders directly into Library with a full-width loading state, so date folders feel like they entered immediately while photo pages stream in.
- Keeps a full-width loading footer at the bottom of the photo grid while additional DMS pages are still streaming in, then removes it when the folder finishes.
- Shows the Library count as loaded items while a folder is still streaming, then switches back to total photos after loading completes.
- Shows a slow-camera response hint after a short wait, guarded by the current browse generation so stale folder requests cannot overwrite the active UI.
- Runs camera folder browsing on a small dedicated executor so a stale slow folder request does not block the next folder selection behind unrelated probe work.
- Uses a lightweight Library import status bar instead of a large tool card, keeping the photo grid close to the top while preserving a compact Browse action.
- Adds a compact Library Back action so photo folders opened directly from Albums can return to the parent camera folder without detouring through Tools.
- Disables Library and Albums Back actions at the camera root, then re-enables them when folder history exists.
- Handles Android Back like a gallery app: exit selection mode first, then return to the parent camera folder, then fall back from secondary pages to Library.
- Keeps the photo grid in a clean browsing mode by hiding unselected checkboxes until selection mode starts, while long-press selection and batch download still work.
- In selection mode, tapping either the card body or the thumbnail toggles selection instead of accidentally opening preview.
- Tracks selected photos by stable camera item keys instead of RecyclerView positions, so selection stays tied to the actual photo while pages continue loading or the grid recycles cells.
- Selection mode replaces the bottom navigation with the compact selection action bar, then restores navigation when selection clears or downloads move to Transfers.
- Uses a compact global app bar with smaller title/subtitle text so Library and Albums content starts higher on the first screen.
- Adds compact Albums actions for Back, Root, and Refresh so folder navigation does not require opening the diagnostic Tools page.
- Shows readable folder titles and a camera-style folder path instead of raw DMS object IDs.
- Uses a bottom-navigation layout with Library, Albums, Transfers, and Tools pages so camera controls, transfer status, and logs no longer compete with the photo grid; page switching is separated from navigation item selection to avoid recursive tab callbacks.
- Shows a RecyclerView photo grid with preview thumbnails, tap-to-preview, long-press/select-checkbox selection, a compact bottom selection action bar, selected-count feedback, disabled-empty download action, refresh, and empty-folder feedback.
- Guards asynchronous thumbnail loading with the expected image URL so recycled RecyclerView cells cannot show stale previews after fast scrolling.
- Gives photo RecyclerView items stable IDs and fans out shared thumbnail requests to all currently matching visible cells, preventing fast-scroll recycling from repainting the top grid with mismatched previews.
- Uses separate dynamic LRU caches for grid thumbnails and full-screen previews, with RGB_565 grid thumbnails to keep many more already-seen photos resident in memory.
- Stores fetched previews in the app cache directory, so scrolling back through large folders can restore thumbnails locally instead of repeatedly hitting the camera Wi-Fi.
- Deduplicates in-flight thumbnail requests per URL to reduce repeated camera Wi-Fi image fetches during fast scrolling.
- Resets the Library and Albums RecyclerViews to the top whenever a new camera folder opens, avoiding stale scroll positions from the previous folder.
- Adjusts photo grid columns for phone, large-screen, and landscape layouts while keeping thumbnail cards stable.
- Uses Material Components with a Material 3 theme, dynamic color entrypoint, theme-resolved UI color tokens with fallbacks, Material chips, icon-enhanced Material buttons, Material checkboxes, Material progress indicator, Material main/photo/folder/log cards, rounded thumbnail previews, a Material preview dialog, primary/secondary action hierarchy, surface cards, and restrained 8dp content cards.
- Uses a fixed blue/neutral import-app palette with light system bars, compact top chrome, denser buttons, hidden unavailable Back actions, and a Settings tab instead of exposing diagnostics as a primary tool surface.
- Displays cleaner photo metadata with quality labels and human-readable file sizes.
- Preview opens as a near full-screen dark photo viewer with a larger image area, filename/index metadata, previous/next navigation, selection, one-photo download, and close controls.
- Preview requests a higher-resolution image decode than the grid while keeping grid thumbnails lightweight.
- Downloads selected original files to Android gallery storage under `DCIM/Sony Picture`.
- Shows batch download progress, current file, success/failure counts, and a cancel button.
- Transfers includes a Recent Activity list for the current batch, showing imported, failed, cancelled, and completed events with newest items first.
- Keeps batch downloads moving when one file fails, then reports the failure count at the end.
- Collects failed download items and enables `Retry Failed` to retry only those files.
- Clears the current selection after a fully successful batch, while preserving selection after cancellation or failures for quick retry.
- Enables an `Open Gallery` action after successful downloads and shows lightweight completion/cancel/failure feedback.
- Validates downloaded files by extension, MIME type, size, and header bytes so RAW/original support is proven rather than assumed.

## Current verified state

- A7R III DMS discovery works on `192.168.122.1:64321`.
- Original JPEG download has been verified by real-device testing.
- The app still keeps the older ScalarWebAPI probe path for diagnostics, but the primary import path is now UPnP/DLNA ContentDirectory.

## Build

This project uses the Android Gradle Plugin already declared in `build.gradle`.

The app depends on Google Material Components:

- `androidx.recyclerview:recyclerview:1.3.2`
- `com.google.android.material:material:1.12.0`
- Kotlin Android plugin `2.0.21`
- `androidx.activity:activity-compose:1.9.3`
- Jetpack Compose UI/Foundation/Material icons `1.7.5`
- Jetpack Compose Material 3 `1.3.1`
- `android.useAndroidX=true`

Required local tools:

- JDK 11 or newer
- Android SDK with at least one installed platform matching `compileSdk 35`
- Android SDK build-tools

Example:

```powershell
$env:JAVA_HOME = "D:\Software\Android Studio\jbr"
$env:ANDROID_HOME = "D:\Software\AndroidSDK"
$env:ANDROID_SDK_ROOT = "D:\Software\AndroidSDK"
& "C:\Users\N.k\.gradle\wrapper\dists\gradle-8.14-bin\38aieal9i53h9rfe7vjup95b9\gradle-8.14\bin\gradle.bat" :app:assembleDebug
```

Compose migration note: this project now builds the Kotlin + Jetpack Compose launcher UI. If Compose artifacts are missing on a fresh machine, use Android Studio Gradle sync, copy another machine's Gradle dependency cache, or place a Maven repository under `local-maven`.

See `docs/compose-dependency-setup.md`.

To check whether the primary Compose dependencies are available locally:

```powershell
.\scripts\check-compose-deps.ps1
```

## Device Deploy Test

After connecting an Android phone with USB debugging enabled and authorizing the computer:

```powershell
.\scripts\android-deploy-test.ps1
```

The script builds the debug APK, installs it with `adb install -r`, launches `com.codex.sonyedge`, and saves a short logcat capture under `build/device-logs/`.
It defaults to the ADB bundled with `D:\Software\scrcpy-win64-v4.0` and also saves a launch screenshot under `build/device-screenshots/`.

To capture the current phone screen without reinstalling:

```powershell
.\scripts\android-screenshot.ps1
```

## Protocol Notes

See `docs/sony-imaging-edge-protocol.md`.
