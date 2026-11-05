# Animation Studio

A native, offline-first Android 2D animation editor built with Kotlin, Jetpack Compose, Material 3, immutable project state, and independently testable graphics/animation engines.

> **Project status: working foundation / alpha.** This repository contains real editing, persistence, rigging, audio-preview, and rendering code—not a UI mockup. It does **not** yet claim feature parity with Synfig Studio, OpenToonz, or Adobe Animate. See [Remaining production work](docs/DEVELOPMENT_PLAN.md#phase-2--production-expansion-not-represented-as-complete) and the limitations below.

## What works

- New-project presets, custom canvas/FPS/duration, recent projects, editable autosave recovery, duplication, deletion, and Storage Access Framework Open/Save As.
- Responsive professional dark workspace for phones and tablets, collapsible inspector/timeline, full-screen canvas, touch/mouse timeline seeking, and hardware shortcuts.
- Pressure-aware vector brush/pencil strokes, Bézier paths, direct node editing, shapes, text, images, selection/move/rotate/scale, fill, gradients, eyedropper, and erasing.
- Multi-touch pan/zoom/rotation, grid/snapping, checkerboard, rulers, safe area, motion-path display, and configurable onion skin.
- Layer hierarchy, locking, visibility, opacity, blend modes, grouping, ordering, duplication, cels/exposure, blank cels, and frame insertion/deletion.
- Position/rotation/scale/opacity keyframes with linear, hold, eased, and cubic-Bézier interpolation; work-area looping and synchronized playback.
- Bone hierarchy, parenting, constraints, FK, CCD IK, and normalized linear-blend skin deformation in the rigging engine.
- Local PNG/JPEG/WebP/GIF-image and WAV/MP3/supported-audio import through Android codecs; multi-clip audio preview with trim, volume, fades, and decoded waveforms.
- Real PNG/JPEG stills and sequences, LZW-compressed animated GIF, and H.264 MP4 rendering through MediaCodec/MediaMuxer. Background WorkManager progress and cancellation are wired to the UI.
- Command-style undo/redo snapshots and versioned, validated `.animstudio` ZIP/JSON serialization that retains editable objects and animation data.

## Important current limitations

- MP4 output is currently **silent**. Audio preview synchronization works, but final multichannel audio mixing/muxing remains to be implemented.
- Interactive effects preview shows source geometry for responsiveness; the software export compositor applies the configured blur, shadow, glow, color, opacity, and motion-blur effects.
- Advanced SVG import/export, Boolean paths, mesh weight-painting UI, pose libraries, lip-sync, scene transitions, video import, WebM, and GPU effect graphs are not yet complete.
- The current rig UI creates and edits bones and runs FK/IK; full character mesh binding/pose workflows need additional UI.
- Codec support and practical project size depend on the device. Export failures are surfaced and never replaced with fake files.

## Requirements

- Android Studio with Android SDK 35
- JDK 17
- Android 8.0 (API 26) or newer on device
- No account, cloud service, storage permission, or network connection is required for editing.

## Build and install

```bash
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

The Debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it with Android Studio or:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### GitHub Actions Debug APK

Every push to `main` or `arena/**`, every pull request, and each manual workflow dispatch runs unit tests and `assembleDebug`. Download **`animation-studio-debug-apk`** from a successful **Android CI** run. Unit-test reports are also uploaded.

### Connected UI tests

On a running API 26+ emulator/device:

```bash
./gradlew connectedDebugAndroidTest
```

The Compose tests cover project creation, tool selection, timeline playback controls, and reaching export configuration. Device matrix testing and codec-specific export verification are still required before a production release.

### Signed release APK

Debug builds need no signing setup. For a local release, create `keystore.properties` (never commit it):

```properties
storeFile=/absolute/path/release.keystore
storePassword=...
keyAlias=...
keyPassword=...
```

The optional **Signed Release APK** workflow is enabled by setting the repository variable `ANDROID_SIGNING_CONFIGURED=true` and these GitHub secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Run **Signed Release APK → Run workflow**. The workflow decodes the keystore into the runner's temporary directory, builds a signed release, and uploads it as an artifact. If signing is not configured, no misleading release artifact is produced.

## Editing workflow

1. Create a project and choose Brush, Pencil, a shape, Pen, or Bézier.
2. Draw with a finger/stylus. Pressure is retained per sampled point; two fingers control the viewport.
3. Use Selection/Move/Rotate/Scale or Direct Selection to edit existing vector objects.
4. Add blank cels for frame-by-frame animation, or select an object and key its transform channels.
5. Add image/audio assets from File → Import. Audio clip controls are in the Audio inspector.
6. Save as `.animstudio`. The complete editable scene is retained, not flattened.
7. Choose Render → Configure export and select a real output file/folder.

## Architecture

| Module | Responsibility |
| --- | --- |
| `app` | Android lifecycle, Compose workspace, ViewModel, gesture/command integration |
| `core` | Serializable immutable domain models |
| `core-ui` | Material 3 professional theme and shared controls |
| `graphics-engine` | Affine matrix geometry |
| `vector-engine` | Bounds, hit testing, Bézier flattening, object operations |
| `animation-engine` | Keyframe/color/transform sampling and interpolation |
| `timeline-engine` | Frame/cel/exposure/keyframe editing and timing |
| `rigging-engine` | FK, CCD IK, constraints, weighted deformation |
| `drawing-tools` | Stabilized pressure stroke sampling and shape creation |
| `layers-engine` | Layer hierarchy and object commands |
| `camera-engine` | Animated camera evaluation |
| `audio-engine` | Synchronized clip players and PCM waveform decoding |
| `effects-engine` | Software bitmap effects/compositing |
| `rendering-engine` | Frame renderer, GIF/H.264 encoders, export worker |
| `project-manager` | Room metadata, atomic autosaves, SAF persistence, assets |
| `import-export` | Bounded local media import and metadata probing |
| `file-format` | Versioned archive codec and structural validation |
| `settings` | DataStore workspace preferences |

Repositories and services are constructor-injected through `AppContainer`; CPU-heavy import, decode, save, and export operations run on IO/background workers. Project updates use immutable Kotlin data classes.

## Project format and export details

- [`.animstudio` version 1 format](docs/FILE_FORMAT.md)
- [Rendering/export behavior](docs/EXPORTS.md)
- [Development checklist and remaining work](docs/DEVELOPMENT_PLAN.md)

## Privacy and safety

Core editing is offline. Projects and imported media stay in app-managed storage or user-selected SAF locations. Imported files are size-bounded and validated; project archive paths and sizes are checked. No project content is uploaded by application code.
