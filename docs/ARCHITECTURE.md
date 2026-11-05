# Architecture and implementation notes

## State and editing

`StudioViewModel` exposes one immutable `EditorUiState`. Editing commands transform a `StudioProject`, retain bounded undo/redo snapshots, and invalidate the dirty flag. Long gestures take one history checkpoint and then apply non-recording intermediate updates. Autosave only marks a project clean when no newer edit arrived during IO.

`CanvasEditing.kt` owns transient gesture sessions separately from serializable project data. In-progress strokes are previews; a completed stroke becomes an editable `PathObject`. No pointer/MotionEvent objects enter the project file.

## Persistence

`ProjectRepository` writes a temporary archive and atomically renames it to `autosave.animstudio`. SAF saves copy that known-good archive to the selected destination. Imported media is copied to per-project app storage and referenced by archive entry. Room indexes recent metadata; DataStore contains only workspace preferences.

## Rendering

`SoftwareFrameRenderer` evaluates animation for each object/camera and draws editable vectors, text, and cached image assets with Android Canvas. Export reuses this renderer for all output formats. GIF uses GIF89a/LZW; MP4 converts ARGB frames to YUV420 using the actual input `Image` plane and pixel strides before MediaCodec encoding and MediaMuxer muxing.

The interactive Compose renderer favors immediate feedback and displays source geometry, onion skins, rigs, paths, and handles. The export effects processor performs actual bitmap passes. A future GPU compositor can unify these paths without changing project models.

## Reliability boundaries

- Archive parsing uses bounded reads and rejects traversal, duplicate entries, malformed models, and unsupported versions.
- Media import is capped per asset. Image dimensions and project geometry are validated.
- Export cancellation is cooperative between frames; codec/import operations have platform-defined blocking behavior.
- Device codec capabilities, memory, and media formats vary. These are surfaced as explicit errors. No universal performance guarantee is made.

## Next engineering milestones

1. Add golden-image renderer tests and device codec tests across API 26/30/35.
2. Implement audio mixing and AAC muxing with clock drift tests.
3. Introduce GPU layer compositing and shared preview/export effect nodes.
4. Expand rig binding, mesh/weight UI, pose assets, and reusable character templates.
5. Add SVG subset parser/exporter with conformance fixtures and path-operation tests.
