# Fevapp — Free Video Editor App

Fevapp is a **free, fully offline video editor for Android**. No internet
permission, no analytics, no ads, no accounts, no paywalls — 100% free.

The workflow follows the familiar mobile-editor layout (preview on top, a
timeline with a fixed center playhead, a scrollable tool bar at the bottom)
so people who already use popular editors feel at home, but Fevapp has its
own visual identity, icons and code.

UI language: **Uzbek (Latin)** by default; every text lives in `strings.xml`
so English and Russian can be added later.

## What works (milestone 1)

- Create a project and pick several videos/photos with the Android Photo Picker (selection order kept)
- Preview (Media3 ExoPlayer) synced with the timeline playhead; play/pause, fullscreen
- Timeline: fixed center playhead, scrub + fling, pinch-to-zoom (and zoom buttons), ruler, clip thumbnails
- Select a clip → trim with handles, split at playhead, delete, duplicate, reorder (long-press drag or ◀ ▶ buttons)
- Undo / redo for every edit
- Format tool (Original, 9:16, 16:9, 1:1, 4:5) for export
- Export MP4 (720p/1080p, 30/60 fps) with Media3 Transformer → saved to gallery (Movies/Fevapp) → share sheet
- Projects auto-saved in Room and reopened from Home (rename / duplicate / delete)

## What works (phase 2 — text & audio)

- **Text** on its own lane: add at the playhead, edit words, size, color, background box, bold,
  font (sans / serif / mono), position (top / middle / bottom); move (long-press) and trim (handles)
  or use the buttons *Shu yerga*, *Boshi shu yerda*, *Oxiri shu yerda*. The same renderer draws the
  preview and the exported video, so they match.
- **Audio** lane: music from the device (system file picker) or the sound of another video;
  volume, fade in / fade out, split, move, trim; several clips may overlap and are mixed on export
- **Clip volume** and **Ovozni ajratish** (extract a clip's sound to the audio lane and mute the clip)

## What works (phase 2 — core editing, continued)

- **Speed** 0.25×–4× per video clip (pitch kept), speed badge on the clip
- **Filters**: 7 original looks (Oq-qora, Kontrast oq-qora, Iliq, Sovuq, Yorqin, Xira) + brightness,
  contrast, saturation, warmth; live preview; apply to one clip or all. One color matrix drives both
  the preview (TextureView color filter) and the export (Media3 RgbMatrix)
- **Transitions**: dip through black or white between clips (0.2–1.5 s), badge on the cut
- **Text animations**: fade, pop, slide up
- **Voice-over**: record from the microphone while the video plays muted; lands on the audio lane
- **Background export**: a foreground service with a progress notification keeps exporting after you leave the app

## What works (phase 3 — started)

- **Overlay / picture-in-picture**: a video or photo on top of the main track; drag it on the preview
  or use the Joylashuv sheet (position, size)
- **Format background color** behind clips that do not fill the frame; the preview shows the real
  output frame (letterbox) for every format
- **Text**: drag it anywhere on the preview
- **Audio waveforms** on audio-lane clips; overlapping lane clips get their own 48 dp rows

## Recent improvements

- **Preview controls**: tap a text or picture-in-picture clip on the preview to select it, drag to move,
  pinch to resize (one undo step per gesture); tap elsewhere = play/pause
- **Add media anywhere**: always-visible + on the main track (adds at the end), *Qoʻshish* tool (inserts
  at the playhead), big "add" button in an empty project
- **Performance**: playback no longer recomposes the whole editor — the timeline layer is moved with
  `graphicsLayer`, the ruler redraws in its draw phase, off-screen items are skipped
- **Compact controls**: fixed-width tool buttons with two-line labels, default-size chips (48 dp touch
  targets kept), smaller timeline rows

## Architecture

```
:app                    Application, MainActivity, navigation graph
:core:designsystem      Theme, color tokens, Manrope font, shared components
:core:model             Project → Track → Clip, TextClip, AudioClip (immutable, microseconds, serializable)
:core:common            Small utilities (ids, time formatting)
:core:database          Room: projects table + ProjectRepository
:engine:timeline        Clip + lane commands (text, audio, volume, extract) + EditHistory (undo/redo)
:engine:render          Media3: preview (+ synced audio-lane players), import, text renderer/overlay, gain, export
:engine:effects         Plugin interfaces (Effect, Filter, Transition) for future features
:feature:home           Home screen (projects grid, new project)
:feature:editor         Editor (preview, timeline, tools)
:feature:export         Export settings, progress, save & share
build-logic/            Gradle convention plugins (fevapp.android.feature, …)
```

Key ideas:

- **Immutable timeline + command pattern.** Every edit is a `TimelineCommand`
  (pure function `Project -> Project`). `EditHistory` keeps snapshots, so undo/redo
  is trivial and always correct.
- **Time is in microseconds** everywhere (same as Media3).
- **One JSON column per project** in Room — new timeline features don't need table migrations.
- **Convention plugins** — a new feature module only needs
  `alias(libs.plugins.fevapp.android.feature)` and a namespace.
- Colors come only from `FevTheme.colors`, so a light theme can be added by adding a second palette.

See [docs/DESIGN.md](docs/DESIGN.md) and [docs/ROADMAP.md](docs/ROADMAP.md).

## Build & run

Requirements: JDK 17, Android SDK (platform 37, build-tools), Git.

```bash
# Windows
gradlew.bat assembleDebug
gradlew.bat :engine:timeline:test
gradlew.bat installDebug      # with a phone connected (USB debugging on)

# macOS / Linux
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Open the project folder in Android Studio to browse and run it.

## License

MIT — see [LICENSE](LICENSE). The bundled Manrope font is under the SIL Open Font License.
