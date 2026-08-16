# Versta — DESIGN.md

Rules for building Versta interfaces. Terse by design; `readme.md` carries the
context and provenance, `components/*/​*.prompt.md` carry per-component usage.

Link `styles.css`. Reference **semantic roles only** (`--primary`,
`--surface-container-high`, `--on-surface-variant`) — never palette tones
(`--primary-40`) in a component. That indirection is what lets Material You
repaint the app from the user's wallpaper.

---

## 1. The five rules

1. **One colour.** A single tonal palette, seeded by wallpaper (Material You) or
   Versta Magenta `#E0218A` as fallback. No second brand hue.
2. **One action per screen.** At most one filled button *or* one FAB *or* one
   mic orb. Everything else is text, icon buttons, or rows.
3. **Shape carries state.** Selection and press change the radius, not just the
   colour. Pill → 8px on press; chip 8px → pill when selected; orb circle →
   38% squircle while listening.
4. **Nothing appears without motion, nothing bounces twice.** Enter decelerate,
   exit accelerate, move/resize with one spring overshoot.
5. **If a layout seems to need an image, it needs less content.** No
   illustration system, no stock photography, no gradients, no texture except
   the tick-rule motif.

---

## 2. Colour

| Use | Role token |
| --- | --- |
| Screen background | `--surface` |
| Cards, sheets, rows | `--surface-container-low` → `--surface-container-highest` |
| Primary action, active orb | `--primary` / `--on-primary` |
| Result panes, tonal FAB | `--primary-container` / `--on-primary-container` |
| Selected chips & rows | `--secondary-container` / `--on-secondary-container` |
| Secondary text, icons | `--on-surface-variant` |
| Hairlines | `--outline-variant` (dividers) · `--outline` (input borders) |
| Snackbar, inverted card | `--inverse-surface` / `--inverse-on-surface` |
| Destructive | `--error` / `--error-container` |
| Speaking state, privacy affirmations | `--success` |

Dark mode is `[data-theme="dark"]` on any ancestor — roles remap, nothing else
changes.

**State layers** are `currentColor` over the component: hover 8%, focus/press
10%, drag 16%. Disabled is 38% content on a 12% container. Press also scales:
0.96 buttons · 0.92 icon buttons and orbs · 0.985 cards.

Elevation is **tonal first**. Shadows only for things that genuinely float:
FAB, menu, bottom sheet, snackbar, dialog.

---

## 3. Type

Schibsted Grotesk everywhere; JetBrains Mono for machine metadata; `--font-script`
for translated output so no language renders as boxes.

| Situation | Token |
| --- | --- |
| Marketing / onboarding hero | `--type-hero` + `--tracking-hero` |
| Screen title (large app bar) | `--type-headline-lg` |
| Dialog title, section header | `--type-headline-sm` |
| Translated result | `--type-translation` (32px) |
| Source echo | `--type-translation-sm` (20px) |
| List row headline | `--type-body-lg` |
| Supporting line | `--type-body-md` on `--on-surface-variant` |
| Button, chip, nav label | `--type-label-lg` / `-md` |
| Codes, sizes, rates, versions | `--type-mono`, uppercase, `--tracking-mono` |

Negative tracking on everything display-sized; normal tracking on body. Flush
left, ragged right, always.

**Language identity is words + a two-letter mono code** (`Japanese` / `JA`), with
the endonym (`日本語`) on the supporting line. Never a flag, never an emoji.

Set `rtl` on translated panes for Arabic, Hebrew, Farsi, Urdu.

---

## 4. Layout

4px grid. 16px screen margins on phones. Minimum touch target 48px; list rows
56px; the mic orb 96px.

The standard translate screen is three fixed bands:

```
┌──────────────────────────────┐
│  LanguageSwap                │  fixed, replaces the top app bar
├──────────────────────────────┤
│                              │
│  content (scrolls)           │  composer, or source echo + result pane
│                              │
├──────────────────────────────┤
│      ◯  MicOrb               │  fixed, ~34–72px above the gesture nav
└──────────────────────────────┘
```

Secondary screens use `TopAppBar variant="large"` and scroll under it
(`scrolled` raises tone and shadow). Prefer a `BottomSheet` over a new screen —
it keeps the translation visible behind the scrim.

Radii: 28px cards, 32px sheets and dialogs, 16px fields and rows, full for
anything tappable.

---

## 5. Motion

| Change | Duration | Easing |
| --- | --- | --- |
| State layer, ripple | 100ms | `--ease-standard` |
| Colour, opacity, icon fill | 200ms | `--ease-standard` |
| Sheet / dialog / pane in | 400ms | `--ease-emphasized-decelerate` |
| Anything leaving | 200ms | `--ease-emphasized-accelerate` |
| Shape morph, thumb slide | 500ms | `--ease-spring-default` |

Four signature animations, all in `tokens/motion.css` — use these, don't invent
new ones:

- **Listening pulse** — two rings expanding from the orb, 1.6s, 500ms offset.
- **Morphing loader** — a blob changing shape while rotating. Never a spinner ring.
- **Wavy progress** — the completed portion waves, the remainder stays flat.
- **Word stagger** — translated words rise in at 45ms intervals.

Everything respects `prefers-reduced-motion`.

---

## 6. Icons

Material Symbols Rounded only, via `<span class="vs-icon">translate</span>`.
20px dense · 24px default · 32px large icon buttons · 40px orb. Weight 400 (500
on dark). **Outlined at rest, filled (`data-fill="1"`) when active or selected.**

Never hand-draw an SVG glyph, never substitute emoji or unicode symbols. If a
concept has no Material Symbol, use words.

`offline_bolt` means "works with no connection" and appears nowhere else.

---

## 7. Copy

Sentence case. Second person. No exclamation marks, no emoji, no full stop on
single-line UI copy.

| Write | Not |
| --- | --- |
| Japanese is ready to use offline | Success! Japanese Language Pack Downloaded! |
| Listening — English | Please start speaking now… |
| Speech stays on your phone | We never share your data with anyone 🔒 |
| No connection needed | Works even when you're offline! |

Buttons are one- or two-word verbs: Speak, Copy, Remove, Keep, Download, Skip.
Numbers are concrete: `84 MB`, `31 languages`, `283 MB of 2.4 GB free`.
Destructive copy names the cost: "You'll need a connection to download the 84 MB
pack again."

Status reads as a state, not an announcement: "Reading text", "Frame held",
"Speaking". Empty states describe the next action, not the absence.

---

## 8. Choosing a component

| Need | Use |
| --- | --- |
| Commit an action | `Button` — one `filled` per screen, `text` for everything secondary |
| One glyph, one action | `IconButton` |
| The screen's single add/capture | `FAB` |
| Start listening or speaking | `MicOrb` |
| Source ⇄ target header | `LanguageSwap` |
| Show translated text | `TranslationPane` |
| Group related content | `Card` |
| Filter a list | `Chip` |
| Switch 2–4 peer views | `SegmentedButton` |
| Top-level destinations | `NavigationBar` (3–4 items) |
| Toggle a setting | `Switch` in a `ListItem` trailing slot — applied instantly, no Save |
| Secondary content over the screen | `BottomSheet` |
| A decision that must be made now | `Dialog` — two actions maximum |
| Confirm something happened | `Snackbar` — one action |
| Progress with a known percentage | `WavyProgress` |
| Short indeterminate wait | `LoadingIndicator` |

**Not in this system, on purpose:** Avatar, Tabs, Tooltip, Menu, Checkbox,
Radio, DatePicker, Table. If you reach for one, the screen probably has too much
on it.

---

## 9. Known substitutions

Flag these if you're shipping to production:

- Schibsted Grotesk and JetBrains Mono are Google Fonts stand-ins; no brand
  binaries were supplied.
- Material Symbols Rounded stands in for the app's icon set.
- `#E0218A` is a chosen fallback seed, not a supplied brand colour.
- No wordmark file exists — the name is typeset in Schibsted Grotesk 800 beside
  the mark.
