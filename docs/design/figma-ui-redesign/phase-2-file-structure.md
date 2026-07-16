# Phase 2 - Figma file structure

## Status

Phase 2 is prepared but not yet validated in Figma. The Starter-plan MCP quota was
reached immediately before the page-structure mutation could be confirmed. Do not
assume that the attempted mutation succeeded; the next run must inspect the page
list first and then use the same idempotent keys before creating anything.

## Planned page order

1. `Cover`
2. `Getting Started`
3. `Foundations`
4. `--- Components ---`
5. `Components`
6. `--- Screens ---`
7. `Screens`
8. `Utilities`

The existing `Page 1` should be reused as `Cover`. Every page must carry the
`dsb` shared-plugin keys `run_id`, `phase`, and `key` with run ID
`sonyedge-ui-20260716` so retries remain idempotent.

## Foundations content

- Color: primitive and semantic swatches, token name, value, intended use, and
  Compose mapping. Semantic swatches must be variable-bound.
- Typography: all 10 Roboto text styles with size, line height, weight, and
  Compose role. Letter spacing remains zero.
- Dimensions: spacing scale, page padding, grid gaps, minimum 48 dp touch target,
  stable toolbar heights, and system-inset guidance.
- Shape and elevation: radius samples plus Low, Medium, and Overlay effect styles.
- Motion: 100, 180, 220, 240, and 300 ms durations; Standard, Emphasized, and
  Spring references; preview paging, double-tap zoom, selection, and transfer
  feedback examples.

## Responsive and preservation checks

- Document behavior for compact phones, regular phones, landscape, and expanded
  widths instead of treating one artboard as a fixed implementation size.
- Keep long file names, Chinese text, diagnostics, and 1.3-1.5x font scaling usable.
- Keep photo-card aspect ratios stable while images load and prevent bottom bars
  from covering the final grid row.
- Preserve EXIF orientation, preview zoom and paging rules, selection workflows,
  camera-folder traversal, imports, retry/cancel, and diagnostics.
- Bind Figma variables and styles rather than duplicating literal values.

## Exit criteria

- All pages exist in the planned order and have stable idempotency metadata.
- Foundations sections are complete, readable, and free of clipping or overlap.
- Screenshots are captured for Cover, Getting Started, and Foundations.
- Figma values match Phase 1: 74 variables, 10 text styles, and 3 effect styles.
- `figma-state.json` records the confirmed page IDs only after inspection.

## Resume procedure

1. Restore Figma MCP access or move the file to a plan with available MCP calls.
2. Inspect top-level pages before any mutation.
3. If the previous mutation landed, reuse its pages; otherwise create the missing
   pages using the planned names and shared-plugin keys.
4. Continue with Cover, Getting Started, and Foundations in small validated batches.

