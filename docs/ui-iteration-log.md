# SonyEdge UI Iteration Log

## 2026-07-01

### Current direction
- Main architecture is Kotlin + Compose for the UI, while the verified Sony camera protocol and download service remain unchanged.
- The app should feel like a photo import tool, not a network protocol probe.
- Primary user flow: connect camera Wi-Fi, browse albums by date, preview photos, select originals, import to `DCIM/Sony Picture`, review transfer status.

### Implemented in this iteration
- Added explicit connection states: idle, searching, connected, error.
- Kept connection and folder errors on the current page instead of forcing users into diagnostics.
- Reset old transfer counters and failed items whenever a new import starts.
- Enlarged photo selection hit targets and added long-press selection mode.
- Added folder path context on Photos and folder summary on Albums.
- Reworked Tools into user-facing cards for camera connection and gallery access, with diagnostics collapsed by default.
- Reworked full-screen photo preview:
  - Larger image-first layout.
  - Top overlay with close, file name and index.
  - Bottom overlay with Select/Deselect and Import.
  - Previous/next buttons are disabled at boundaries.
  - Horizontal swipe switches photos when available.
- Reduced grid thumbnail decode size and enlarged in-memory bitmap cache to reduce thumbnail reloads after scrolling.
- Added EXIF orientation correction for JPEG thumbnails and previews via `androidx.exifinterface`.
- Reworked preview image rendering:
  - Image content now fills the preview surface behind floating controls instead of being squeezed between bars.
  - Letterbox/background is dark instead of light, reducing the "white flash" feeling.
  - Preview navigation uses horizontal slide + fade transitions.
  - Full preview loading falls back to the already-cached preview bitmap while the larger image decodes.

### Verification status
- Build and ADB runs verified:
  - App launch.
  - Camera connection.
  - Album browsing: `PhotoRoot` -> `Date` -> `2024-4-30`.
  - Photo selection.
  - Single import to `DCIM/Sony Picture`.
  - Bottom navigation did not crash.
  - Multi-photo browsing: `PhotoRoot` -> `Date` -> `2024-5-1`.
  - Photo preview opened on `DSC09604.JPG` and showed `1 of 92`.
  - Swipe left changed preview to `DSC09605.JPG` and showed `2 of 92`.
  - Previous button was disabled at the first item and enabled after swiping to the second item.
- Latest APK after the root-folder subtitle fix built and installed successfully.
- Latest preview/orientation pass:
  - Build succeeded after adding `androidx.exifinterface:exifinterface:1.3.7`.
  - Installed to device `909e29e1`.
  - Verified `2024-5-1` folder with 92 items.
  - Screenshot evidence:
    - `build/device-screenshots/sonyedge-exif-grid.png`
    - `build/device-screenshots/sonyedge-exif-preview-portrait.png`
    - `build/device-screenshots/sonyedge-exif-preview-after-swipe.png`
  - `DSC09614.JPG` displayed upright in the grid and full preview.
  - Preview swipe changed `DSC09614.JPG` `10 / 92` to `DSC09615.JPG` `11 / 92`.
  - Preview Import button remained fully visible after layout changes.

### Next validation checklist
- Continue judging preview visual polish on device; the current top and bottom controls are functional but may still need aesthetic refinement.
- Test selecting from preview and importing from preview after the EXIF/animation changes.
- Test a very large date folder by scrolling down and back up to judge thumbnail cache behavior.

## 2026-07-02

### Implemented in this iteration
- Reworked bottom navigation into three stable destinations: `Browse`, `Imports`, and `Settings`.
- Removed the confusing split between Photos and Albums as separate bottom-nav pages; folder browsing and photo grids are now both internal states of Browse.
- Added folder result caching for back navigation, so returning from a photo folder to the date list no longer re-fetches the camera directory.
- Added request-token guarding around folder loads to prevent stale slow responses from overwriting newer navigation state.
- Replaced the preview's manual drag switch with Compose `HorizontalPager`, giving real horizontal page motion and settled-page state sync.
- Removed clickable ripple/pressed feedback from the preview image surface; tapping the image only toggles controls.
- Simplified the selection action bar to only show selected count, one All/Clear toggle, and Import count.
- Changed photo and preview selection affordances to circular controls.

### Verification status
- Build succeeded and APK was installed to device `909e29e1`.
- ADB flow verified:
  - App launch.
  - Connect and browse.
  - `PhotoRoot` folder opens.
  - `Date` folder opens.
  - `2024-5-1` opens with 92 photos.
  - Preview opens on portrait `DSC09614.JPG` as `10 / 92`.
  - Horizontal swipe changes preview to `DSC09615.JPG` as `11 / 92`.
  - Tapping the preview image hides controls without leaving a dark pressed overlay.
  - Long-press selection shows the compact action bar: `1 selected`, `All`, `Import (1)`.
  - Returning from `2024-5-1` to `Date` shows the date list within two seconds from cached state.
- Screenshot evidence:
  - `build/device-screenshots/sonyedge-after-connect.png`
  - `build/device-screenshots/sonyedge-photoroot.png`
  - `build/device-screenshots/sonyedge-date.png`
  - `build/device-screenshots/sonyedge-photo-grid.png`
  - `build/device-screenshots/sonyedge-preview-portrait-new.png`
  - `build/device-screenshots/sonyedge-preview-after-pager-swipe.png`
  - `build/device-screenshots/sonyedge-preview-tap-image.png`
  - `build/device-screenshots/sonyedge-selection-bar.png`
  - `build/device-screenshots/sonyedge-back-cache.png`
- UI tree evidence:
  - `build/device-logs/ui-after-connect.xml`
  - `build/device-logs/ui-photoroot.xml`
  - `build/device-logs/ui-date.xml`
  - `build/device-logs/ui-photo-grid.xml`
  - `build/device-logs/ui-preview-portrait-new.xml`
  - `build/device-logs/ui-preview-after-pager-swipe.xml`
  - `build/device-logs/ui-preview-tap-image.xml`
  - `build/device-logs/ui-selection-bar.xml`
  - `build/device-logs/ui-back-cache.xml`

### Next validation checklist
- Test preview selection plus preview Import on a single photo.
- Test a 100+ item date folder by scrolling to the bottom and back to judge thumbnail cache retention.
- Continue visual polish of the preview controls if the current dark overlay still feels too heavy on the real device.

## 2026-07-02 Follow-up

### Implemented in this iteration
- Fixed Imports progress semantics:
  - UI now treats progress as completed files (`success + failed`) instead of the currently-started file index.
  - Preview single import no longer shows a false `Import complete` state while the first file is still downloading.
- Added explicit download state to Compose UI state so the Imports page can distinguish queued/downloading/done states.
- Normalized user-facing transfer wording from download/downloads to import/imports.
- Changed the download notification launch target from legacy `MainActivity` to `ComposeMainActivity`.
- Applied a focused visual pass from design review:
  - Bottom navigation selected state now uses Sony blue instead of the default purple.
  - Photo grid back action moved to the left of the folder title.
  - Photo filename labels changed from heavy rounded pills to a lighter bottom gradient overlay.
  - Unselected selection circles are now transparent with a white outline; selected circles remain Sony blue.
  - Selection bottom bar height and elevation were reduced.

### Verification status
- Build succeeded after the transfer-state fix.
- Installed to device `909e29e1`.
- Verified preview single import on `DSC09918.JPG` from `2024-6-8`:
  - Early state: `Importing 0 of 1`, `0 imported / 0 failed`, `1/1 Downloading DSC09918.JPG`.
  - Final state: `Import complete`, `1 imported / 0 failed`, `Saved 1 item to DCIM/Sony Picture.`
- Verified 132-photo folder `2024-6-8` after visual changes.
- Large-folder scroll test:
  - Opened `2024-6-8` with 132 items.
  - Scrolled down and back to the top.
  - Already loaded top thumbnails remained visible after returning.
- Final APK built and installed after the visual pass.
- Screenshot evidence:
  - `build/device-screenshots/sonyedge-large-grid-top-1.png`
  - `build/device-screenshots/sonyedge-large-grid-top-2.png`
  - `build/device-screenshots/sonyedge-large-preview-import-before.png`
  - `build/device-screenshots/sonyedge-preview-selected.png`
  - `build/device-screenshots/sonyedge-retest-import-early.png`
  - `build/device-screenshots/sonyedge-retest-import-final.png`
  - `build/device-screenshots/sonyedge-final-grid-visual.png`
- UI tree evidence:
  - `build/device-logs/ui-large-grid-top-1.xml`
  - `build/device-logs/ui-large-grid-top-2.xml`
  - `build/device-logs/ui-large-preview-import-before.xml`
  - `build/device-logs/ui-preview-selected.xml`
  - `build/device-logs/ui-retest-import-early.xml`
  - `build/device-logs/ui-retest-import-final.xml`
  - `build/device-logs/ui-final-grid-visual.xml`

### Next validation checklist
- Continue reducing the Browse header height; it still consumes too much vertical space on phone screens.
- Improve loading placeholders so unloaded thumbnails look like skeleton loading, not failed images.
- Rework the preview overlay into a lighter top/bottom gradient treatment.

## 2026-07-06 Preview Polish and ADB Validation

### Implemented in this iteration
- Reinstalled the app on device `909e29e1` after resolving the debug-signature mismatch by uninstalling only `com.codex.sonyedge`.
- Updated the preview pipeline to prefer a decodable original JPEG URL for preview, falling back to the camera thumbnail/preview URL.
- Kept EXIF-based bitmap orientation correction in the shared decode path so original JPEG previews can display with their embedded orientation.
- Added preview-page prefetch for the current and nearby items to reduce black flashes while swiping through the full-screen pager.
- Made the preview dialog draw edge-to-edge and switch the host status/navigation bars to the dark preview color while the preview is open, restoring the normal light bars on close.
- Reduced the preview gradient overlay heights so controls feel lighter.

### Verification status
- Build succeeded with `:app:assembleDebug`.
- Installed and launched on device `909e29e1`.
- Connected to the A7R III camera Wi-Fi and opened `Camera / PhotoRoot / Date / 2024-6-8`.
- Verified 132-photo grid:
  - Real thumbnails loaded in a 3-column grid.
  - Scrolled down and back to the top; already loaded top thumbnails remained visible and correctly matched their filenames.
- Verified preview:
  - Opened `DSC09918.JPG`.
  - Swiped horizontally to `DSC09919.JPG`.
  - Status bar and navigation area now use the dark preview background instead of the previous gray system bar.
  - Preview controls remained stable after swiping.
- Verified directory back behavior:
  - Closed preview and returned from `2024-6-8` to `Date`.
  - The date list reappeared from cached state without a visible reload.
- Verified selection mode:
  - Long-pressed a photo in `2024-6-8`.
  - Compact bottom bar displayed `1 selected`, `All`, and `Import (1)` without text clipping or overflow.

### Screenshot evidence
- `build/device-screenshots/sonyedge-home-ready2-20260706.png`
- `build/device-screenshots/sonyedge-after-connect-20260706.png`
- `build/device-screenshots/sonyedge-grid-132-20260706.png`
- `build/device-screenshots/sonyedge-grid-scroll-return-20260706.png`
- `build/device-screenshots/sonyedge-preview-bars-final-20260706.png`
- `build/device-screenshots/sonyedge-preview-bars-final-swipe-20260706.png`
- `build/device-screenshots/sonyedge-back-date-final-20260706.png`
- `build/device-screenshots/sonyedge-selection-final-20260706.png`

## 2026-07-08 Transfer Speed and ETA

### Implemented in this iteration
- Added per-file transfer telemetry from `DownloadService`: bytes done, bytes total, current speed, and estimated remaining seconds.
- Added a live Imports metrics panel showing:
  - Current transfer speed.
  - Estimated remaining time.
  - Current file byte progress.
  - Per-file progress bar.
- Kept high-frequency progress samples out of Recent activity so the activity list stays readable.
- Bumped the Android build to `versionCode 13` and `versionName 0.1.12`.

### Verification status
- Build succeeded with `:app:assembleDebug`.
- Installed to device `909e29e1` after uninstalling the old debug-signature-mismatched package.
- Granted location, nearby devices, and notification permissions after reinstall.
- Connected to the A7R III camera Wi-Fi.
- Browsed `Camera / PhotoRoot / Date / 2026-7-8`.
- Selected all 3 photos in the folder and started a real import.
- Imports page displayed live metrics during transfer:
  - Speed: `730 KB/s`.
  - Remaining: `0:30`.
  - Current file: `5.5 MB / 26.6 MB`.
- Import completed successfully:
  - `3 imported / 0 failed`.
  - Saved to `DCIM/Sony Picture`.
- Confirmed files in `/sdcard/DCIM/Sony Picture` after import.

### Screenshot evidence
- `build/device-screenshots/sonyedge-transfer-speed-eta-progress-20260708.png`
- `build/device-screenshots/sonyedge-transfer-speed-eta-complete-20260708.png`

## 2026-07-08 Import Completion Metrics

### Implemented in this iteration
- Added batch-level transfer metrics from `DownloadService`:
  - Total imported bytes.
  - Batch elapsed time.
  - Average transfer speed.
- Kept live per-file metrics during transfer.
- Added an Imports completion summary with `Total`, `Average`, and `Elapsed` cards after import finishes.
- Reduced metric-card label/value text sizes so three completion cards fit on phone-width screens without clipping.
- Added a local UX audit note under `docs/audits/2026-07-08-current-flow/`.
- Bumped the Android build to `versionCode 14` and `versionName 0.1.13`.

### Verification status
- Build succeeded with `:app:assembleDebug`.
- Archived versioned APK:
  - `app/build/outputs/versioned-apk/SonyEdge-v0.1.13-14-debug-20260708-123933.apk`
- Installed to device `909e29e1`.
- Connected to the A7R III camera Wi-Fi.
- Browsed `Camera / PhotoRoot / Date / 2026-7-8`.
- Imported one original photo successfully.
- Verified live transfer metrics during import:
  - Speed: `905 KB/s`.
  - Remaining: `0:20`.
  - Current file: `9.4 MB / 26.6 MB`.
- Verified completion metrics after import:
  - Total: `26.6 MB`.
  - Average: `962 KB/s`.
  - Elapsed: `0:28`.
  - `1 imported / 0 failed`.

### Screenshot evidence
- `build/device-screenshots/sonyedge-complete-metrics-20260708.png`
- `build/device-screenshots/sonyedge-complete-metrics-fixed-20260708.png`

## 2026-07-08 Auto Open Date Folder

### Implemented in this iteration
- Changed the initial camera connection flow so `Connect and browse` automatically opens the camera's date-folder level.
- The auto-open path is intentionally narrow:
  - `Camera` -> `PhotoRoot`.
  - `PhotoRoot` -> `Date`.
  - Stop at `Date` so the user still chooses the shooting day manually.
- Kept manual Root, Back, Refresh, and folder navigation behavior unchanged.
- Preserved the folder stack so Back from a date folder still has normal navigation context.
- Bumped the Android build to `versionCode 15` and `versionName 0.1.14`.

### Verification status
- Build succeeded with `:app:assembleDebug`.
- Archived versioned APK:
  - `app/build/outputs/versioned-apk/SonyEdge-v0.1.14-15-debug-20260708-125018.apk`
- Installed to device `909e29e1`.
- Connected to the A7R III camera Wi-Fi.
- From a fresh app launch, tapped `Connect and browse` once.
- App landed directly on `Date` with path `Camera / PhotoRoot / Date`.
- Date folder `2026-7-8` was visible without manually tapping `PhotoRoot` or `Date`.
- Tapping `2026-7-8` still opened the photo grid with 3 photos.

### Screenshot evidence
- `build/device-screenshots/sonyedge-auto-date-folder-20260708.png`

### Notes
- The tested `DSC09918.JPG` and `DSC09919.JPG` appear to be landscape frames with portrait subjects, so they do not prove the vertical-photo orientation case by themselves.
- The preview now prefers original JPEG bytes when available, which is the correct path for vertical photos whose EXIF orientation is only present on the original file.

## 2026-07-06 Preview Controls, Zoom, and Cache Cleanup

### Implemented in this iteration
- Moved the preview bottom controls upward and reduced button sizes so Import/Select/Previous/Next are less likely to be clipped on phones with different navigation bars, rounded corners, or display cutouts.
- Added pinch-to-zoom preview support:
  - Each preview page tracks its own zoom state.
  - Scale is clamped between 1x and 5x.
  - Pan is enabled while zoomed and resets when the image returns to 1x.
- Added disk image-cache maintenance:
  - Cache files live under `cache/sonyedge-image-cache`.
  - A cleanup pass runs at most every 12 hours from image loading.
  - Files not accessed for 7 days are deleted.
  - Disk-cache hits refresh `lastModified`, so actively used thumbnails/previews are retained.

### Verification status
- Build succeeded with `:app:assembleDebug`.
- Installed and launched on device `909e29e1`.
- Connected to the A7R III camera Wi-Fi and opened `Camera / PhotoRoot / Date / 2024-6-8`.
- Opened `DSC09918.JPG` preview and verified the bottom controls are visually fully visible after the safe-area adjustment.
- Verified the app process remained alive and logcat showed no `FATAL EXCEPTION` after opening the preview.
- Verified image cache directory exists on-device with cached `.img` files via `run-as com.codex.sonyedge`.

### Screenshot evidence
- `build/device-screenshots/sonyedge-preview-zoom-ui-20260706.png`
- `build/device-screenshots/sonyedge-preview-bottom-safe-2-20260706.png`

### Notes
- ADB cannot reliably perform true multi-touch pinch gestures with the simple `input` command set used here, so pinch-to-zoom still needs a quick hand test on the device.
- The cache cleanup policy is verified by source and by confirming the runtime cache directory/files; full 7-day expiry is time-based and should not be forced by changing the phone clock during camera-transfer testing.

## 2026-07-07 Preview Zoom Page Reset

### Implemented in this iteration
- Reset fullscreen preview zoom scale and pan offset whenever the pager settles on a different photo.
- This fixes the case where a zoomed photo stayed zoomed after swiping to another photo and then swiping back.
- Kept normal 1x horizontal paging behavior unchanged; pinch zoom still consumes gestures only while multi-touch or already zoomed.

### Verification status
- Build succeeded with `:app:assembleDebug`.
- Versioned APK archived as `app/build/outputs/versioned-apk/SonyEdge-v0.1.5-6-debug-20260707-200626.apk`.
- Installed successfully on ADB device `a109cf4`.
- Device package info confirmed `versionCode=6` and `versionName=0.1.5`.

## 2026-07-07 Preview Bottom Safe-Area Hardening

### Implemented in this iteration
- Changed the fullscreen preview bottom controls from a fixed-height overlay to content-measured layout.
- Kept the same visual style and buttons, but moved `navigationBarsPadding()` onto the content container so tall gesture/navigation areas cannot squeeze or clip Import/Select controls.
- Targeted the new test phone environment `a109cf4`, which reports `1440x3168` with `640dpi`, a useful stress case for oversized UI density.
- After live camera testing still showed controls touching the bottom edge on `a109cf4`, added a fixed 40dp bottom safety spacer below the preview action row. This protects the controls even when Android does not report an effective navigation-bar inset inside the fullscreen dialog.

### Verification status
- Build succeeded with `:app:assembleDebug`.
- Versioned APK archived as `app/build/outputs/versioned-apk/SonyEdge-v0.1.6-7-debug-20260707-201139.apk`.
- Installed successfully on ADB device `a109cf4`.
- Device package info confirmed `versionCode=7` and `versionName=0.1.6`.
- Captured launch screenshot at `build/device-screenshots/sonyedge-a109cf4-v016-home-20260707.png`.
- Live camera preview verification before the 40dp spacer showed `Import`, `Select photo`, and `Next photo` bounds touching the physical bottom (`y=3168`) on `a109cf4`; this confirmed the original clipping risk.
- Rebuilt after the spacer adjustment as `versionCode=8`, `versionName=0.1.7`.
- Versioned APK archived as `app/build/outputs/versioned-apk/SonyEdge-v0.1.7-8-debug-20260707-201709.apk`.
- Installed successfully on ADB device `a109cf4`.
- Reopened the live camera path `Camera / PhotoRoot / Date / 2024-6-8 / DSC09918.JPG`.
- Captured preview screenshot at `build/device-screenshots/sonyedge-a109cf4-preview-bottom-v017-pull-20260707.png`; Import, Select, Previous, and Next controls are visually fully visible above the bottom edge.

## 2026-07-07 Cache Cleanup Hardening

### Implemented in this iteration
- Run image disk-cache cleanup once when the Compose app starts, not only during image loading.
- Keep the existing "not accessed for 7 days" expiry policy.
- Added a 512MB disk-cache cap for `cache/sonyedge-image-cache`; when the cap is exceeded, oldest cache files are deleted first.
- Kept the 12-hour throttle for routine cleanup during image loading so scrolling large folders does not repeatedly scan the cache directory.

### Verification status
- Built successfully with `:app:assembleDebug`.
- Installed successfully on ADB device `a109cf4` as `versionName=0.1.8`, `versionCode=9`.
- Verified startup cleanup by creating `cache/sonyedge-image-cache/stale-startup-test.img` with mtime `2025-01-01 00:00`; after app restart the file was deleted.
- Cache file count after cleanup check: `102`.

## 2026-07-07 Preview Zoom Gesture Fix

### Implemented in this iteration
- Track whether the current preview image is zoomed and disable `HorizontalPager` user scrolling while zoomed.
- Clamp zoomed image panning to the fitted image bounds so a photo edge cannot be dragged into the middle of the preview.
- Reset zoom and pan to centered `1x` whenever the settled preview page changes.
- Added double-tap zoom toggle between `1x` and `2.5x` for a faster inspection gesture.
- Move preview gesture handling to the full preview canvas, not just the rendered bitmap, so dragging on black letterbox areas still works.
- Use immediate state updates while dragging for better finger tracking.
- Use 180ms animated scale/offset transitions for double-tap zoom and zoom reset.

### Verification status
- Built successfully with `:app:assembleDebug`.
- Installed successfully on ADB device `a109cf4` as `versionName=0.1.10`, `versionCode=11`.
- Opened live camera path through `PhotoRoot / Date` into a 47-item folder and opened preview.
- ADB verification: zoomed preview stayed on `DSC09968.JPG` / `2 / 47` after a horizontal drag.
- ADB verification: after double-tap reset to `1x`, the same horizontal drag advanced to `DSC09969.JPG` / `3 / 47`.
- Follow-up build installed successfully on ADB device `a109cf4` as `versionName=0.1.11`, `versionCode=12`.
- ADB verification on the animated version: zoomed preview stayed on `DSC09967.JPG` / `1 / 47` after a horizontal drag.
- ADB verification on the animated version: after double-tap reset to `1x`, the same horizontal drag advanced to `DSC09968.JPG` / `2 / 47`.
- Captured evidence screenshots:
  - `build/device-screenshots/sonyedge-v011-zoom-pan-tight.png`
  - `build/device-screenshots/sonyedge-v011-reset-swipe.png`
  - `build/device-screenshots/sonyedge-v012-zoom-pan.png`
  - `build/device-screenshots/sonyedge-v012-reset-swipe.png`

## 2026-07-22 Camera Browse Navigation Cleanup

### Implemented in this iteration
- Back from a single-day photo grid now restores the cached `Date` folder list.
- Back from the `Date` list returns to the connected camera home without exposing the internal `Camera` or `PhotoRoot` containers.
- Removed the duplicate folder summary row from the `Date` list.
- Removed the protocol breadcrumb from a single-day photo grid.
- Removed filename overlays from photo thumbnails while retaining tap-to-preview and selection behavior.

### Verification status
- `:app:testDebugUnitTest` and `:app:assembleDebug` succeeded for `versionName=0.5.4`, `versionCode=36`.
- Installed successfully on ADB device `909e29e1` and connected to `DIRECT-leE1:ILCE-7RM3` at `192.168.122.1`.
- Confirmed both the Android system Back action and the page toolbar Back action return from `2026-7-21` to `Date`.
- Confirmed both Back paths return from `Date` to the connected camera home.
- Confirmed the `Date` page has one summary only, and the 37-photo page has no breadcrumb or filename overlays.
- Opened `DSC06913.JPG` from the cleaned thumbnail grid to verify preview navigation remains available.

## 2026-07-22 Local-only Wi-Fi Preview Routing Fix

### Implemented in this iteration
- Route every Compose thumbnail and large-preview HTTP request through the `CameraWifiBinding` network selected by the one-tap camera connection flow.
- Hold the Wi-Fi lease until the response body has been read, then release it through the existing reference-counted binding.
- Log HTTP status and transport failures instead of silently leaving the loading placeholder on screen.

### Verification status
- `:app:testDebugUnitTest` and `:app:assembleDebug` succeeded for `versionName=0.5.5`, `versionCode=37`.
- Installed successfully on ADB device `909e29e1` and performed a fresh one-tap connection to `DIRECT-leE1:ILCE-7RM3`.
- Opened `2026-7-21`; all 15 initially visible `TN_*.JPG` resources loaded through camera network `169` in about two seconds.
- Opened `DSC06913.JPG`; its 888,917-byte `LRG_DSC06913.JPG` preview loaded in under one second and replaced the loading state.
- Imported the original `DSC06913.JPG`: 36,012,032-byte JPEG, 1 success, 0 failures.
- After the download released its Wi-Fi lease, scrolled to uncached photos and confirmed another set of thumbnail requests loaded successfully.

## 2026-07-23 Automatic Camera Selection Detection

### Implemented in this iteration
- Detect camera-side selection mode from `XPushList + ContentDirectory` service advertisement immediately after connection discovery.
- Skip the normal DMS root preload in camera-selection mode and start the existing XPush receive/import flow automatically.
- Keep normal connected-home browsing unchanged when `XPushList` is absent.
- Share one guarded receive request between automatic detection and the Settings diagnostic action.
- Reject stale results after disconnect/reconnect and keep each request scoped to its own XPush guard.
- Show a receiving state on the connected home and disable the manual receive action while setup is active.
- Attempt `X_TransferEnd` before releasing the requested camera Wi-Fi when cancelling or disconnecting an active setup.
- Start the download handoff as a foreground service on Android 8 and newer.
- Treat the camera closing its Wi-Fi after a terminal XPush transfer as a normal completion, preserving the Imports result instead of showing a connection error.

### Verification status
- Capability detection unit tests cover usable XPush, missing control URL, and missing ContentDirectory.
- Transfer-state unit tests cover active, completed, cancelled, and fatal download states.
- `:app:testDebugUnitTest` and `:app:assembleDebug` succeeded for `versionName=0.5.7`, `versionCode=39`.
- Installed successfully on ADB device `909e29e1` and connected to `DIRECT-leE1:ILCE-7RM3`.
- In camera-side selection mode, discovery advertised both `ContentDirectory` and `XPushList`; SonyEdge automatically executed `X_TransferStart`, `X_GetPushRoot`, and browsed `PushRoot` without opening Settings.
- Imported `DSC06885.JPG`: 21,037,056-byte JPEG, 1 success, 0 failures, saved to `DCIM/Sony Picture`.
- Confirmed `X_TransferProgress 1/1` and `X_TransferEnd errCode=0` both returned HTTP 200.
- After the camera closed its Wi-Fi, SonyEdge remained on the Imports page with `Import complete`, while Android returned to the previous home Wi-Fi.
- Crash log buffer was empty after the transfer.
- In normal camera-browse mode, the same camera advertised three services without `XPushList`; SonyEdge did not start XPush and followed the DMS browsing path.
- Confirmed automatic navigation through `PhotoRoot / Date`, six visible date folders, and a 76-photo folder loaded in three DMS pages.
- Confirmed the visible three-column grid loaded its thumbnail resources through the camera network.
- Opened `DSC06822.JPG`; its 578,433-byte `LRG_DSC06822.JPG` preview loaded successfully.
- Crash log buffer remained empty after the normal browse regression.

## 2026-07-23 First-use Camera QR Connection

### Implemented in this iteration
- Added offline Sony Wi-Fi QR scanning with ZXing Android Embedded.
- Parse `W01` payloads into the `DIRECT-<suffix>:<model>` SSID, password, model, and camera identity.
- Use the existing Android `WifiNetworkSpecifier` connection path after a successful scan.
- Probe the common `192.168.122.1:64321` endpoint before SSDP without making it a compatibility requirement.
- Verify the scanned camera identity against the SSDP device UDN when that identity is advertised.
- Store the camera identity beside the existing Keystore-encrypted Wi-Fi profile after service verification succeeds.
- Keep manual SSID/password entry hidden until QR scanning or QR-initiated connection fails.
- Preserve one-tap reconnect for a successfully remembered camera.

### Verification status
- Sony QR parser unit tests cover the supplied A7R III payload, separated identity normalization, invalid prefixes, missing and duplicate fields, invalid identities, and UDN identity extraction.
- `:app:testDebugUnitTest`, `:app:compileDebugKotlin`, and `:app:assembleDebug` succeeded.
- The first installed scanner build exposed ZXing's default landscape orientation, so a project-owned portrait capture activity was added for the follow-up `versionName=0.5.9`, `versionCode=41` build.
- Installed `0.5.9 (41)` on device `909e29e1` and completed a first-use scan after removing the previous saved camera.
- Scanned the camera's real QR code in portrait orientation and connected to `DIRECT-leE1:ILCE-7RM3`; discovery resolved the camera service at `192.168.122.1`.
- Confirmed the saved profile contains model `ILCE-7RM3`, identity `E8E8B7349C13`, and the SSID. The Wi-Fi password is stored only as Android Keystore AES-GCM ciphertext and an initialization vector.
- Browsed `PhotoRoot / Date`, opened the 37-item `2026-7-21` folder, loaded the three-column thumbnail grid, and opened the large preview for `DSC06913.JPG`.
- Imported the 34.3 MB `DSC06913.JPG` original to `DCIM/Sony Picture`: 1 imported, 0 failed, about 1.4 MB/s over 25 seconds.
- Disconnected, returned to the normal phone Wi-Fi, and reconnected to the remembered camera with one tap and without reopening the scanner.
- Cancelled a later "scan another camera" attempt and confirmed manual SSID/password entry became available only after that scan failure.
- Android's crash log buffer remained empty after the complete connection, browse, preview, download, disconnect, reconnect, and fallback regression.

### Screenshot evidence
- `app/build/device-screenshots/qr-first-browse-v059.png`
- `app/build/device-screenshots/qr-first-preview-v059.png`

## 2026-07-23 Android 16 Preview Window Compatibility

### Root cause and implementation
- Reproduced the clipped photo-preview controls on OPPO `PGEM10`, Android 16, at `1440x3168` with a `640 dpi` display-density override.
- UI Automator showed the preview action nodes extending to the physical bottom edge, leaving most of the controls outside the visible screen.
- Window diagnostics showed the full-screen Compose `Dialog` receiving an incompatible surface/content measurement on this ColorOS build.
- Changing only `decorFitsSystemWindows` in the intermediate `0.5.10 (42)` build did not correct the layout.
- Replaced the platform full-screen `Dialog` with an Activity-owned full-screen Compose overlay and added `BackHandler` so system Back still closes the preview.

### Verification status
- `:app:testDebugUnitTest` and `:app:assembleDebug` succeeded for `versionName=0.5.11`, `versionCode=43`.
- Installed `0.5.11 (43)` on device `a109cf4`, reconnected to `DIRECT-leE1:ILCE-7RM3`, opened the 37-item `2026-7-21` folder, and loaded `DSC06913.JPG`.
- Confirmed the previous, import, select, and next controls are fully visible. Their lowest UI Automator bound is `y=3124`, within the `3168 px` display.
- Confirmed both the next button and a horizontal swipe advance from `1 / 37` to `2 / 37`.
- Confirmed system Back returns from the preview to the 37-item grid.
- Cleared the Android crash buffer before the final interaction regression; no crash entries were produced.

### Screenshot evidence
- Before: `app/build/device-screenshots/preview-android16-pgem10.png`
- Fixed: `app/build/device-screenshots/preview-android16-pgem10-v0511.png`
