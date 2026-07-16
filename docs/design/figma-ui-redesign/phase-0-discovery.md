# SonyEdge UI Redesign - Phase 0 Discovery

## Project state

- Branch: `feature/figma-ui-redesign`
- Figma file: https://www.figma.com/design/qvqszCja2Chzf27h1Bx2qS
- Android UI stack: Kotlin, Jetpack Compose, Material 3
- Stable protocol/download stack: Java and Kotlin, preserved without behavior changes
- Verified camera baseline: Sony A7R III; compatibility remains broader than this model

## Current product flow

1. The user joins the camera Wi-Fi and starts connection discovery.
2. A successful connection automatically opens `Camera / PhotoRoot / Date`.
3. The user chooses a date folder, browses photos, previews one photo or enters selection mode.
4. Single or batch import starts the foreground download service and opens Imports.
5. Originals are validated and saved to `DCIM/Sony Picture`.
6. Settings provides reconnect, gallery access, and expandable diagnostics.

Android back priority must remain: close preview, clear selection, return to Browse, then open the previous camera folder.

## Functional preservation matrix

| Capability | Existing source of truth | Redesign requirement |
| --- | --- | --- |
| Wi-Fi connection and DMS discovery | `SonyEdgeViewModel.connectAndBrowse`, `SonyCameraRepository.connect`, `DiscoveryClient` | Preserve discovery, endpoint cache, progress and error states |
| Auto-open date level | `SonyEdgeViewModel.autoNextDateFolder` | Continue stopping at Date; never auto-open an arbitrary date |
| Folder hierarchy | `openFolder`, `goBack`, `refresh`, `folderCache`, `browseRequestId` | Preserve Root, refresh, breadcrumb, cached back navigation and stale-request protection |
| DMS pagination and media parsing | `DmsContentClient` | Preserve 32-item paging and original/large/thumbnail resource selection |
| Photo browse and selection | `LibraryScreen`, `PhotoTile`, `SelectionBar`, `itemKey` | Adaptive grid; preserve tap preview, long-press selection, stable keys, Select All and Clear |
| Fullscreen preview | `PhotoPreview`, `ZoomablePreviewImage` | Preserve pager, previous/next, counter, control visibility, select and single import |
| Zoom and orientation | `ZoomablePreviewImage`, `applyExifOrientation` | Preserve 1x-5x pinch, 1x/2.5x animated double tap, bounded pan, page reset and all EXIF orientations |
| Image loading and cache | `ProgressiveCameraImage`, `loadBitmap`, `cleanupExpiredImageCache` | Preserve progressive loading, neighbor prefetch, 128 MiB memory cache, 512 MiB disk cap and 7-day TTL |
| Batch import | `DownloadService`, `DownloadValidator` | Preserve serial foreground transfer, original candidate probing, validation and filename collision handling |
| Imports status | `onDownloadProgress`, `TransfersScreen` | Preserve file/batch progress, bytes, speed, ETA, success/failure, completion metrics, cancel and retry |
| Settings and diagnostics | `SettingsScreen`, `addLog`, `clearLogs` | Preserve reconnect, gallery, expandable logs and clear action |
| Android integration | `ComposeMainActivity`, Manifest | Preserve runtime permissions, progress receiver, system-bar behavior and notification return target |

The existing `invertSelection()` function has no user-facing entry and is not part of the current preservation contract. Video items remain selectable/importable, but video playback is outside this UI redesign.

## Architecture gaps

- `SonyEdgeUi.kt` currently owns theme, navigation, all screens, preview gestures, networking, decoding and cache behavior in one file.
- Navigation is inferred from `activeTab`, folders, photos and preview state rather than represented explicitly.
- The photo grid is fixed at three columns and the shell has no compact/medium/expanded strategy.
- Compose colors, XML theme colors and system bar colors are not driven by one token source.
- Loading, failure and retry states for images are collapsed to nullable bitmaps.
- There are no unit or Compose UI tests for navigation, selection, transfer state or responsive layout.

The first implementation pass will separate packages and state boundaries without creating multiple Gradle modules:

- `ui/theme`: color, typography, shape, spacing and motion tokens
- `ui/shell`: responsive scaffold, top status and primary navigation
- `ui/browse`: folder/date list, photo grid, selection mode and empty/error/loading states
- `ui/preview`: fullscreen pager, zoom and overlay controls
- `ui/transfers`: live transfer, completion, failure and recent activity
- `ui/settings`: connection, storage and diagnostics
- `ui/image`: current image loading, decode and cache behavior moved behind a focused API

## Locked design direction

The visual direction is a quiet photography utility rather than a marketing interface. Content takes priority over decoration. The palette uses neutral white/graphite surfaces, Sony blue only for primary actions and selected states, and teal/rose only for connection success or errors. Cards are limited to grouped settings and transfer summaries; browse content stays mostly unframed.

### Navigation and screens

1. **Browse - date folders**: compact camera status, clear date list, item counts, refresh and Root in an overflow menu.
2. **Browse - photo grid**: contextual folder header, adaptive edge-to-edge grid, progressive placeholders and selection mode.
3. **Photo preview**: dark fullscreen pager with light gradient chrome, animated control visibility, select and import actions.
4. **Imports - active**: current file preview/identity, batch and file progress, speed, ETA and cancel.
5. **Imports - complete/failure**: completion metrics, failed item summary, retry and gallery access.
6. **Settings**: connection, destination, cache information and diagnostics disclosure.
7. **System states**: disconnected, searching, loading folder, empty folder, image loading, image failure and camera error.

Primary navigation remains three destinations: Browse, Imports and Settings. Folder lists and photo grids are internal Browse levels, not separate bottom navigation destinations.

### Responsive rules

| Window width | Navigation | Browse layout | Photo grid |
| --- | --- | --- | --- |
| `< 600dp` compact | Bottom navigation | Single pane | Adaptive cells, minimum 108dp; usually 3 columns |
| `600-839dp` medium | Navigation rail | Single pane with wider content and optional detail sheet | Adaptive cells, minimum 132dp |
| `>= 840dp` expanded | Navigation rail | Folder list and photo content may use two panes | Adaptive cells, minimum 148dp |

- All page containers use window insets and content padding rather than fixed screen heights.
- Text must remain valid at large font scales; action rows wrap or change orientation instead of clipping.
- Preview controls use measured content plus system navigation inset; the previously verified extra bottom safety space remains represented by a minimum safe-area token.
- Landscape phones use the medium shell when width permits, but retain touch targets of at least 48dp.

### Motion plan

| Interaction | Motion |
| --- | --- |
| Primary destination change | 220ms fade-through with a small directional offset |
| Enter/leave folder | 240ms shared-axis horizontal transition |
| Selection mode | Bottom action bar slide/fade; selection marker spring scale |
| Preview open/close | 220ms fade plus subtle 0.96-1.0 image scale |
| Preview page swipe | Native pager motion; adjacent image prefetch avoids flashes |
| Show/hide preview chrome | 180ms alpha and vertical slide |
| Double-tap zoom/reset | Existing 180ms animated scale and offset |
| Transfer progress | 300ms progress interpolation; completion icon scale/fade |
| Reduced motion | Remove spatial translation and keep short crossfades |

## Figma discovery result

- The new file contains one empty page and no local variables, styles or components.
- Searches for mobile navigation, buttons, cards, colors, spacing and typography returned no reusable library assets.
- Therefore the file needs a small local SonyEdge design system: semantic color variables, spacing/radius/motion tokens, text and effect styles, plus reusable navigation, button, status, folder row, photo tile, metric and selection components.

## Code to design mapping

| Current code | Figma artifact | Planned Compose boundary |
| --- | --- | --- |
| `AppHeader` and `BottomNavigation` | Responsive app shell and camera status | `ui/shell` |
| `CameraScreen` and `FolderSummary` | Browse folders screen and folder row | `ui/browse` |
| `LibraryScreen`, `PhotoTile`, `SelectionBar` | Photo grid, photo tile and contextual action bar | `ui/browse` |
| `PhotoPreview`, `ZoomablePreviewImage` | Fullscreen preview and chrome states | `ui/preview` |
| `TransfersScreen`, metrics composables | Active/complete/failure import screens | `ui/transfers` |
| `SettingsScreen` | Grouped settings and diagnostics disclosure | `ui/settings` |
| Inline colors and shapes | Foundations page and semantic tokens | `ui/theme` |
| Image/cache helpers in `SonyEdgeUi.kt` | Loading/skeleton/error states | `ui/image` |

## Phase 0 gap analysis

- **Code-only**: all production functionality and several hand-tested gesture/cache behaviors; none are represented in Figma yet.
- **Figma-only**: nothing; the new file is blank.
- **Conflict**: none between code and Figma. The old XML purple theme conflicts with the active Compose Sony blue palette; the redesign will make a single semantic theme authoritative.
- **Excluded from visual v1**: video playback, process-death transfer recovery and a protocol redesign. These are new product/platform capabilities, not UI preservation work.

## Phase 0 exit evidence

- Current source and saved ADB audit screenshots were inspected.
- Two independent read-only code audits produced a complete function and regression inventory.
- The Figma file structure and library search were inspected.
- Page scope, component scope, responsive behavior, motion behavior and code mapping are locked above.

