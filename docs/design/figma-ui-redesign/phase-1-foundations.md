# SonyEdge UI Redesign - Phase 1 Foundations

## Result

Phase 1 foundations were created and validated in the SonyEdge Figma file:

- Figma: https://www.figma.com/design/qvqszCja2Chzf27h1Bx2qS
- Run ID: `sonyedge-ui-20260716`
- Plan constraint: Starter, one mode per variable collection
- Theme scope: light application shell plus a dedicated dark preview surface

## Variable collections

| Collection | Mode | Variables | Purpose |
| --- | --- | ---: | --- |
| SonyEdge / Primitives | Value | 19 | Raw neutral, blue, teal, rose and amber colors |
| SonyEdge / Color | Light | 26 | Background, text, icon and border semantic aliases |
| SonyEdge / Dimensions | Value | 21 | Spacing, radius, touch, icon and adaptive grid dimensions |
| SonyEdge / Motion | Value | 8 | Durations and Compose easing references |

All primitive colors have empty scopes by design. All semantic variables use targeted scopes. Every variable has an Android code syntax entry. Semantic colors alias primitives; no semantic color stores a duplicate raw value.

## Typography styles

All text styles use Roboto with zero letter spacing:

- SonyEdge / Display / Large - 32/40 Bold
- SonyEdge / Title / Large - 24/32 Bold
- SonyEdge / Title / Medium - 20/28 SemiBold
- SonyEdge / Title / Small - 17/24 SemiBold
- SonyEdge / Body / Large - 16/24 Regular
- SonyEdge / Body / Medium - 14/20 Regular
- SonyEdge / Body / Small - 12/16 Regular
- SonyEdge / Label / Large - 14/20 Medium
- SonyEdge / Label / Medium - 12/16 Medium
- SonyEdge / Label / Small - 11/16 Medium

## Effect styles

- SonyEdge / Elevation / Low
- SonyEdge / Elevation / Medium
- SonyEdge / Elevation / Overlay

The effects are deliberately restrained. Browse content should remain visually flat; elevation is reserved for navigation, contextual actions, menus and preview overlays.

## Motion tokens

- Duration: 100ms instant, 180ms fast, 220ms standard, 240ms navigation, 300ms progress
- Easing: standard, emphasized and selection spring references for Compose

The Figma Starter plan does not provide multiple variable modes. Reduced-motion behavior will therefore be documented at the interaction and Compose implementation layers rather than represented as a Figma mode.

## Validation evidence

- Collections: 4
- Variables: 74
- Aliases: 26
- Broken aliases: 0
- Variables with `ALL_SCOPES`: 0
- Variables missing Android syntax: 0
- Text styles: 10, all Roboto
- Effect styles: 3
- Validation result: PASS

