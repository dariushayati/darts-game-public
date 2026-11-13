---
name: Darty Time
description: A local-first neon darts game for Android.
colors:
  arena-black: "#03050A"
  night-hall: "#05070D"
  navy-slate: "#0C1220"
  raised-navy: "#141C2E"
  control-bar: "#0A1222"
  blueprint-edge: "#22304A"
  chalk-white: "#F4F6FA"
  muted-steel: "#97A1B2"
  signal-blue: "#1E88E5"
  cursor-blue: "#42A5F5"
  lock-green: "#00E676"
  warning-amber: "#FFB300"
  alert-red: "#E53935"
  invader-violet: "#B388FF"
  lava-orange: "#FF6D00"
  ring-yellow: "#FFEB00"
  dart-cyan: "#29B6F6"
  hot-magenta: "#C2185B"
  pill-gray: "#2C2E36"
  chip-well: "#0F1624"
  result-scrim: "#F203050A"
  overlay-glass: "#E60A1018"
  name-plate: "#07090D"
  daylight-bg: "#EEF2F8"
  daylight-surface: "#FFFFFF"
  daylight-text: "#0B1018"
  daylight-muted: "#55637A"
  daylight-edge: "#C4CEE0"
typography:
  display:
    fontFamily: "Roboto, sans-serif"
    fontSize: "44sp"
    fontWeight: 900
    lineHeight: "44sp"
    letterSpacing: "-1sp"
  headline:
    fontFamily: "Roboto, sans-serif"
    fontSize: "34sp"
    fontWeight: 900
    lineHeight: "34sp"
    letterSpacing: "-0.5sp"
  title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "22sp"
    fontWeight: 900
    lineHeight: "26sp"
    letterSpacing: "0.5sp"
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "18sp"
    letterSpacing: "0sp"
  label:
    fontFamily: "Roboto, sans-serif"
    fontSize: "10sp"
    fontWeight: 700
    lineHeight: "13sp"
    letterSpacing: "1.5sp"
rounded:
  chip: "8dp"
  input: "10dp"
  control: "12dp"
  button: "14dp"
  card: "16dp"
  panel: "20dp"
  overlay: "24dp"
  pill: "50dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "14dp"
  lg: "20dp"
  xl: "28dp"
components:
  button-primary:
    backgroundColor: "{colors.signal-blue}"
    textColor: "#FFFFFF"
    typography: "{typography.label}"
    rounded: "{rounded.button}"
    padding: "0 20dp"
    height: "50dp"
  button-secondary:
    backgroundColor: "{colors.navy-slate}"
    textColor: "{colors.signal-blue}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    height: "48dp"
  game-card:
    backgroundColor: "{colors.navy-slate}"
    textColor: "{colors.chalk-white}"
    rounded: "{rounded.panel}"
    padding: "16dp"
  setup-chip:
    backgroundColor: "{colors.chip-well}"
    textColor: "{colors.muted-steel}"
    typography: "{typography.label}"
    rounded: "{rounded.chip}"
    padding: "8dp 12dp"
  text-field:
    backgroundColor: "{colors.navy-slate}"
    textColor: "{colors.chalk-white}"
    typography: "{typography.body}"
    rounded: "{rounded.input}"
  bottom-nav:
    backgroundColor: "{colors.navy-slate}"
    textColor: "{colors.muted-steel}"
    typography: "{typography.label}"
  player-badge:
    backgroundColor: "{colors.dart-cyan}"
    textColor: "{colors.name-plate}"
    typography: "{typography.title}"
    rounded: "{rounded.control}"
    size: "48dp"
---

# Design System: Darty Time

## Overview

**Creative North Star: "The Arcade Scoreboard"**

Darty Time is a dark, glowing scoreboard that sits beside a real dartboard. It should feel like a well-run arcade cabinet: bold score-first type, electric signal accents, and one board that owns the screen. The interface is friendly and competitive, serious about fast turns and trustworthy scoring but never stiff or corporate.

Surfaces are navy-black and mostly flat. One-pixel borders and tonal fills separate regions; accent glows are reserved for live game signals such as the called target, the active player, a lock, or a completed match. The result is an interface that reads from a standing distance and stays out of the way of the throw.

The light theme is a daylight version of the same scoreboard, not a generic Material reskin: white and cool-gray surfaces, near-black type, and the same signal accents. It should still feel like Darty Time from across the room.

**Key Characteristics:**
- Navy-black arena surfaces with crisp blue-gray edges.
- Score-first Roboto Black labels and tight display headings.
- Functional signal accents, never decorative color noise.
- Tactile 48–56dp controls with strong selected and pressed states.
- One dominant board or scoreboard per view.
- Flat surfaces with tonal layering and accent glow, not drop shadows.

## Colors

The palette is an electric navy field with a small family of high-signal accents. Primary blue carries navigation and the main action; green, amber, red, violet, orange, yellow, and cyan identify games and outcomes.

### Primary
- **Signal Blue** (`{colors.signal-blue}`): Primary buttons, selected player-count chips, active navigation, and the main action in a game.
- **Cursor Blue** (`{colors.cursor-blue}`): Links, arrows, selected icons, and hover-like emphasis that needs to separate from Signal Blue.

### Secondary
- **Lock Green** (`{colors.lock-green}`): Successful locks, confirmations, and the Shields identity.
- **Warning Amber** (`{colors.warning-amber}`): Timer pressure, reset actions, and caution states.
- **Alert Red** (`{colors.alert-red}`): Misses, lives lost, and the Gunslinger identity.
- **Invader Violet** (`{colors.invader-violet}`): The Invaders game identity and secondary celebratory accents.
- **Lava Orange** (`{colors.lava-orange}`): The Lava game identity and heat-related danger.
- **Ring Yellow** (`{colors.ring-yellow}`): The Ring Run identity and ring highlights.
- **Dart Cyan** (`{colors.dart-cyan}`): Player one, dart markers, and cool accent details.

### Neutral
- **Arena Black** (`{colors.arena-black}`): The deepest background and splash color.
- **Night Hall** (`{colors.night-hall}`): The main app background.
- **Navy Slate** (`{colors.navy-slate}`): Cards, panels, and navigation surfaces.
- **Raised Navy** (`{colors.raised-navy}`): Raised or secondary surfaces inside cards.
- **Control Bar** (`{colors.control-bar}`): Bottom action bars and floating control strips.
- **Blueprint Edge** (`{colors.blueprint-edge}`): One-pixel borders and dividers.
- **Chalk White** (`{colors.chalk-white}`): Primary text and high-emphasis labels.
- **Muted Steel** (`{colors.muted-steel}`): Secondary text, captions, and unselected navigation.
- **Pill Gray** (`{colors.pill-gray}`): Small status pills and neutral chips.
- **Chip Well** (`{colors.chip-well}`): Unselected chip backgrounds.
- **Result Scrim** (`{colors.result-scrim}`): Full-screen scrim behind match results.
- **Overlay Glass** (`{colors.overlay-glass}`): The translucent result card surface.
- **Name Plate** (`{colors.name-plate}`): Dark text on bright player badges.

### Named Rules
**The Signal Rarity Rule.** Accent colors are functional. Use them for the active player, the live target, selection, game identity, and outcomes; do not use them as ambient decoration.

**The On-Accent Rule.** Text on a filled accent uses dark ink when it has more contrast, otherwise white. Never assume white on a bright game color; `DartsPalette.onColor` owns the decision.

## Typography

**Display Font:** Roboto (Android system sans; `sans-serif` fallback)
**Body Font:** Roboto (Android system sans; `sans-serif` fallback)

**Character:** Utilitarian system type pushed to black weights and wide tracking. The app does not ship a custom font; personality comes from weight, spacing, casing, and scale rather than a display face.

### Hierarchy
- **Display** (900, 44sp, 44sp line-height, -1sp tracking): Setup-screen game titles and the largest result names.
- **Headline** (900, 34sp, 34sp line-height, -0.5sp tracking): Winner names and high-emphasis match results.
- **Title** (900, 22sp, 26sp line-height, 0.5sp tracking): Game-card titles, player initials, and section leads.
- **Body** (400, 13sp, 18sp line-height): Rule summaries, descriptions, and supporting copy.
- **Label** (700, 10sp, 13sp line-height, 1.5sp tracking, uppercase): Section eyebrows, chips, navigation labels, and button text.

### Named Rules
**The Score-First Type Rule.** The most important number, name, or target gets the heaviest weight and the largest size in its region. If two elements compete for first read, reduce one.

## Layout

The app uses a single-column phone layout with 20dp horizontal margins, 28dp vertical rhythm on menu and setup screens, and 18dp gaps between major blocks. Content is capped at 640dp; the game menu is capped at 720dp. Cards use 16dp internal padding, and game action bars use 10dp outer and inner padding.

Phones are portrait-first and scroll vertically. Tablets are free to rotate. At a smallest width of 600dp they are treated as tablets; in landscape with a width of at least 600dp the game frame switches to two panes, with a 320–420dp control panel at 38% of the width and the board filling the remaining space. Top-level navigation has four destinations — GAMES, STATS, AUTODARTS, SETTINGS — and changes shape by width: a bottom navigation bar on phones and a leading navigation rail on tablets.

All screens respect the system safe-drawing insets, including the status bar, display cutout, navigation bar, and keyboard. Interactive controls are 48–56dp tall, with 48dp square icon buttons and 50–56dp primary actions.

**The One Board Rule.** A play screen has one dominant board or scoreboard. Chrome stays at the edges; never put a second competing focal surface beside the live board.

## Elevation & Depth

The system is flat by default. Depth comes from tonal surface levels, 1–1.5dp blue-gray borders, and accent glows around live game signals. There are no decorative drop shadows. Game-complete overlays use a scrim plus a radial accent glow, and the board uses animated glow and flash to show locks, hits, and targets.

### Shadow Vocabulary
- **Signal glow** (`radial accent gradient at 18–28% alpha`): Active target, result celebration, and board highlights.
- **Scrim** (`{colors.result-scrim}`): Dims the live game behind a result overlay.
- **Edge line** (`1–1.5dp {colors.blueprint-edge}`): The normal boundary between surfaces; this replaces shadow-based separation.

### Named Rules
**The Flat-By-Default Rule.** Surfaces are flat at rest. Add glow only when a live game state needs attention, and never add a drop shadow to a card or button.

## Shapes

The form language is rounded and tactile: 8dp chips, 10dp inputs, 12dp icon controls, 14–16dp buttons, 16–20dp cards and panels, 24dp result overlays, and fully rounded 50dp pills. Player avatars and board targets are circles. Borders are thin and crisp; corners are never sharp on interactive chrome. The board itself is the one large circular geometry, with segmented rings and a compact number ring.

## Components

### Buttons
- **Shape:** Rounded rectangles (12–16dp radius); primary setup actions are 50–56dp tall.
- **Primary:** Signal Blue fill, white Roboto Black label, 1sp tracking, uppercase.
- **Secondary / Icon:** Navy Slate fill or transparent, 1.5dp accent or border stroke, 48dp square.
- **States:** Selected and pressed states use a brighter blue or a filled accent; disabled icon actions fall back to Blueprint Edge.

### Chips
- **Style:** 8dp radius, 1.2dp border, 8dp vertical and 12dp horizontal padding, 10sp uppercase label.
- **State:** Selected uses an accent border and a 16% accent fill; unselected uses Chip Well with Muted Steel text.

### Cards / Containers
- **Corner Style:** 16–20dp.
- **Background:** Navy Slate on Night Hall; Raised Navy for nested blocks.
- **Shadow Strategy:** None; use a 1dp Blueprint Edge border and tonal contrast.
- **Internal Padding:** 14–16dp.

### Inputs / Fields
- **Style:** Outlined field with a 10dp radius, Navy Slate fill, Chalk White text, and a Muted Steel label.
- **Focus:** Material default focus stroke; no custom glow on text fields.
- **Error / Disabled:** Not customized; inherit Material 3 behavior.

### Navigation
- **Style:** Four-item Material navigation bar on phones and a navigation rail on tablets, 10sp uppercase labels, 1.5dp edge divider.
- **States:** Selected icon and label use Cursor Blue and Chalk White with a 20% blue indicator; unselected items use Muted Steel.
- **Adaptive treatment:** Bottom bar on phones, leading rail on tablets; top-level only, so it never competes with in-game action bars.

### Player Badge
- **Style:** 48dp rounded square with a 1.5dp accent border and a 9dp inner player-color fill.
- **Content:** The player’s initial in black Roboto Black.
- **Behavior:** The badge sits at the start of the game action bar and anchors the current turn.

### Board Surface (Signature)
- **Style:** A full circular dartboard drawn on Canvas with a dark segmented field, a compact number ring, and player-color fills.
- **Behavior:** Targets glow and fill as shields accumulate; hits flash, locks pulse, and the press-and-hold loupe magnifies the aim point.
- **Constraint:** The board is the focal surface. Keep labels and controls outside its circle.

### Match Complete Overlay (Signature)
- **Style:** Full-screen scrim, a 24dp card with Overlay Glass, a radial winner-color glow, and 32 confetti pieces.
- **Content:** Winner name, match reason, three stat tiles, and sorted final standings.
- **Behavior:** The card scales in over 450ms; confetti runs for about 2.3s.

## Do's and Don'ts

### Do:
- **Do** use accent color for the active player, live target, selection, game identity, and outcome.
- **Do** keep primary actions at 50–56dp and icon controls at 48dp.
- **Do** separate surfaces with a 1dp Blueprint Edge border and a tonal fill change.
- **Do** use Roboto Black at 22sp or larger for the first-read score or name.
- **Do** preserve the one-board focus on every play screen.
- **Do** respect safe-drawing insets so the status bar, cutout, and navigation bar never cover content.

### Don't:
- **Don't** use default purple Material or a generic admin-dashboard layout.
- **Don't** add drop shadows to cards, buttons, or navigation.
- **Don't** spend accent colors on decoration that does not carry state.
- **Don't** put a second large focal surface beside the board.
- **Don't** introduce a custom display font; the system sans is the brand voice.
- **Don't** hide the board or the current target behind chrome.
