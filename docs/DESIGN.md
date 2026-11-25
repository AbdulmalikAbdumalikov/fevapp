# Fevapp design rules

## Identity

- Original visual identity. Familiar *workflow* (preview → timeline → tools),
  but **no** copied icons, logos, brand colors, assets, effects or templates
  from other editors. Only generic video-editor conventions.
- Minimal and flat: no gradients, no glow, no heavy shadows.
- Dark theme only for now. All colors are tokens in
  `core/designsystem/.../theme/FevColors.kt`; never hard-code a color in UI code.

## Color tokens (dark)

| Token | Value | Use |
|---|---|---|
| background | `#111214` | Screen background |
| surface | `#1C1D21` | Cards, sheets, toolbar |
| surfaceHigh | `#24252A` | Raised controls (play button) |
| border | `#2A2B30` | Dividers, outlines |
| textPrimary | `#F3F4F6` | Main text, icons |
| textSecondary | `#A1A1AA` | Hints, secondary labels |
| accent | `#6EA8FE` | Primary actions, selection, trim handles |
| onAccent | `#0B1220` | Text/icons on accent |
| danger | `#F87171` | Delete actions |
| clipFill | `#2E3440` | Main-track clip background |
| textClipFill | `#4B3B7A` | Text-lane clips |
| audioClipFill | `#1E5A45` | Audio-lane clips |
| overlayClipFill | `#7A4B2E` | Overlay (picture-in-picture) clips |

## Typography

- **Manrope**, bundled as a variable font in `res/font` (no runtime downloads).
- Material 3 type scale with Manrope in every style.

## Shape

- Cards: 14dp radius. Buttons: 12–16dp (14dp default). Small chips: 8dp.

## Icons

- Material Icons *Rounded* (bundled through `material-icons-extended`) or simple
  custom stroke icons. Launcher icon is an original vector (frame + play wedge + timeline bar).

## Accessibility — easy for all ages

- Every tool = **icon + text label** (never icon-only in the tool bar).
- Touch targets **≥ 48dp** (tool buttons are 68×60dp).
- High contrast text on dark backgrounds.
- **Every gesture has a visible button**:
  - pinch-to-zoom → zoom − / + buttons
  - long-press drag to reorder → "Chapga" / "Oʻngga" buttons
  - long-press on a project → "⋮" button
  - scrub the timeline → play/pause + "Kesish" (select clip at playhead) + "Boʻlish"
  - tap preview to play → play button
- Destructive actions (delete project) ask for confirmation; timeline edits are undoable.

## Editor layout

1. Top bar: back · undo · redo · **Eksport** (accent)
2. Preview (fit) with fullscreen button
3. Playback bar: `current / total` time · play/pause · zoom − / + · fullscreen
4. Timeline: ruler · main video track (thumbnails, "+" at the end) · text lane · audio lane;
   **fixed center playhead**, content scrolls under it
5. Bottom tool bar (horizontal scroll):
   - nothing selected: Kesish, Audio, Matn, Filtr, Tezlik, Subtitr, Format
   - clip selected: Boʻlish, Oʻchirish, Nusxa, Chapga, Oʻngga, Tezlik, Filtr, Oʻtish, Ovoz, Ovozni ajratish, Teskari
   - text selected: Tahrirlash, Nusxa, Oʻchirish, Shu yerga, Boshi shu yerda, Oxiri shu yerda
   - audio selected: Ovoz, Boʻlish, Oʻchirish, Shu yerga, Boshi shu yerda, Oxiri shu yerda
     (the last three are the button alternatives to dragging and trimming)
6. Tool panels open as bottom sheets with **✕** (close) and **✓** (apply).

## Preview rendering notes

- Filters/transitions in the preview are a color matrix applied as a layer paint on the player's
  TextureView. Media3 GL video effects in ExoPlayer froze playback on the emulator, so they are used
  only in export (same matrix → same result).
- Text is drawn by `TextRenderer` in both preview and export.

## Language

- Uzbek (Latin) default, using the correct letters ʻ (Oʻ, Gʻ) and ʼ.
- All UI text in `strings.xml` of the module that owns the screen.
