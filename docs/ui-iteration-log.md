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
