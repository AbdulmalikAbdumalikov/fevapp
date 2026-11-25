# Fevapp roadmap

Fevapp stays **free and offline** in every phase.

## Phase 1 — Foundation (done)

- Multi-module architecture, design system, Uzbek UI
- Projects in Room, Home screen
- Editor: preview, timeline (fixed playhead, zoom, thumbnails), trim, split, delete, duplicate, reorder, undo/redo
- Format (aspect ratio) for export
- Export MP4 (720p/1080p, 30/60 fps) to gallery + share

## Phase 2 — Core editing

- ✅ **Text**: text clips on the text lane, styles (size, color, box, bold, font family, position)
- ✅ Text in/out animations (fade, pop, slide up)
- ⬜ Text: bundled fonts, free drag on the preview
- ✅ **Audio**: music from device, sound from a video, per-clip volume, fade in/out, extract audio
- ✅ Audio: voice-over recording (RECORD_AUDIO, still offline)
- ⬜ Audio: waveform on the lane
- ✅ **Speed**: constant speed (0.25×–4×)
- ⬜ Speed curves
- ✅ **Filters & adjustments**: looks + brightness, contrast, saturation, warmth (one color matrix for preview and export)
- ✅ **Transitions**: dip to black / white
- ⬜ Overlapping transitions (cross-fade, slide, zoom) — need a second video sequence
- ✅ Export as a foreground service (continues in background)

## Phase 3 — Advanced

- Keyframes (position, scale, rotation, opacity, volume)
- Overlays / picture-in-picture on the overlay track
- Masks
- Chroma key (green screen)
- Canvas & background (blur, color) for every aspect ratio; preview letterboxing
- Stickers (original, bundled)
- Reverse clip

## Phase 4 — Offline AI

- **Auto captions** with whisper.cpp (on-device, Uzbek support)
- **Background removal** with MediaPipe selfie/subject segmentation
- **Noise reduction** with RNNoise

## Later

- Light theme (second `FevColors` palette)
- Large-text mode
- More languages: English, Russian
