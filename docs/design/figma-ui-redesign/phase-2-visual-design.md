# Phase 2 - Visual design

## Result

The SonyEdge Figma file now contains a complete visual direction for the Android
application. The design was created in the existing file:

- File: `SonyEdge UI Redesign`
- URL: <https://www.figma.com/design/qvqszCja2Chzf27h1Bx2qS>
- Branch: `feature/figma-ui-redesign`

Figma MCP access reached the Starter-plan call limit, so the page structure and
visual boards were completed through the authenticated Chrome editor. The local
SVG sources remain in `docs/design/figma-ui-redesign/exports/` so the design can
be reproduced, reviewed, or repaired without relying on one browser session.

## Page structure

| Page | Figma ID | Purpose |
| --- | --- | --- |
| Cover | `0:1` | Product direction and primary visual language |
| Getting Started | `11:2` | Import journey, destinations and responsive principles |
| Foundations | `11:3` | Color, typography, dimensions, elevation and motion |
| --- Components --- | `11:4` | Section divider |
| Components | `11:5` | Reusable states and controls |
| --- Screens --- | `11:6` | Section divider |
| Screens | `11:7` | Product screens and responsive variants |
| Utilities | `11:8` | Implementation, motion, media and accessibility rules |

## Product architecture

The redesigned application keeps three stable user destinations:

1. `Browse` owns camera connection state, folder/date navigation, the photo grid,
   selection mode and full-screen preview.
2. `Imports` owns the active queue, progress, completed items, failed-item retry,
   cancel and gallery access.
3. `Settings` owns camera reconnection, import preferences, cache management and
   diagnostics.

Camera, PhotoRoot and Date remain internal browse states rather than becoming
extra destinations. This keeps the bottom navigation predictable while
preserving the existing DMS traversal and auto-open behavior.

## Core screens

The `Screens` page contains:

- Camera folders with connection status and date-first navigation.
- Photo grid with stable aspect-ratio cards and RAW/JPEG metadata.
- Selection mode with Select All, Invert, Clear and Import originals.
- Dark immersive preview with paging, EXIF, selection, import and zoom guidance.
- Imports queue with progress, cancel, completed, failed and retry states.
- Settings with camera, MediaStore destination, cache and diagnostics.
- A 320 dp compact variant with two columns and icon-first selection actions.
- A 600 dp expanded variant with navigation rail, three-column grid and a side
  preview detail panel.

## Responsive contract

- Compact: `320-359 dp`, 16 dp page padding, two-column media grid.
- Medium: `360-599 dp`, 16-20 dp page padding, two or three columns based on
  minimum card width.
- Expanded: `600+ dp`, 24 dp page padding, three to five columns and an optional
  navigation rail or preview side panel.
- System status, cutout and gesture insets are part of the layout calculation.
- Bottom navigation and selection actions must never cover the final media row.
- Touch targets remain at least 48 dp and labels do not wrap into multiple lines
  on compact screens.

## Motion contract

- `100 ms`: pressed and immediate feedback.
- `180 ms`: selection and small state transitions.
- `220 ms`: content appearance and thumbnail crossfade.
- `240 ms`: destination and folder transitions.
- `300 ms`: preview entry, exit and pager emphasis.
- Preview paging follows the finger at 1x; paging is disabled while zoomed.
- Double tap animates between 1x and 2.5x; pinch remains direct from 1x to 5x.
- A resolved image crossfades over the previous bitmap so the UI never flashes
  white or clears a cached thumbnail.
- Reduced-motion mode replaces travel with fades without changing workflows.

## Functional preservation

The visual redesign explicitly retains:

- Saved endpoint discovery, DMS pagination, folder cache and request cancellation.
- Camera to PhotoRoot to Date traversal and optional latest-date auto-open.
- Stable media IDs, thumbnail memory/disk cache and progressive loading.
- JPEG/RAW identification, EXIF orientation and original-file validation.
- Selection, batch import, queue progress, cancel, failed retry and gallery access.
- MediaStore output to `DCIM/Sony Picture`.
- Settings, connection tools, base URL details and diagnostics.
- Android back priority: preview, selection, Browse destination, previous folder.

## Validation

- Eight SVG design sources parse as valid UTF-8 XML.
- No SVG uses gradients or negative letter spacing.
- All eight Figma pages contain visual content.
- Chrome screenshots were checked for Cover, Getting Started, Foundations,
  Components, Screens, Utilities and both section dividers.
- The Screens canvas was reduced from 5200 to 4000 px after visual review to remove
  unused whitespace without scaling or deleting content.
- No application source code was changed during this phase.

