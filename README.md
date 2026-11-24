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

## Architecture

```
:app                    Application, MainActivity, navigation graph
:core:designsystem      Theme, color tokens, Manrope font, shared components
:core:model             Project → Track → Clip (immutable, microseconds, serializable)
:core:common            Small utilities (ids, time formatting)
:core:database          Room: projects table + ProjectRepository
:engine:timeline        Commands (add/trim/split/delete/move/duplicate/format) + EditHistory (undo/redo)
:engine:render          Media3: preview player, media import, Composition + Transformer export, gallery saver
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
