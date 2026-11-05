# Animation Studio development checklist

This checklist distinguishes verified foundations from planned production work. A checked item means source implementation and (where possible without an Android SDK) deterministic tests exist; it does not imply feature parity with desktop animation suites.

## Phase 0 — Project foundation
- [x] Versioned Gradle/Android Studio configuration and independent engine modules.
- [x] Versioned `.animstudio` domain model (scene, layers, editable objects, tracks, rigs, audio, cameras, effects, and assets).
- [x] CI workflow that compiles, tests, and archives a Debug APK.
- [x] No account, network service, or cloud dependency in core editing.

**Acceptance:** the repository opens as one Android Studio project; JSON/ZIP serialization retains editable scene data; advanced capabilities are never simulated.

## Phase 1 — Editor vertical slice
- [x] Welcome, project setup, recent-project management, and responsive professional dark workspace.
- [x] Touch/stylus vector drawing, shape creation, selection, transform gestures, node editing, erasing, fill/gradient, eyedropper, pan/zoom/rotation.
- [x] Nested layer operations, object selection, onion skin, frame operations, keyframes, interpolation, playback, and timeline controls.
- [x] Undo/redo, autosave, explicit Save/Open/Save As, project validation, and crash-recovery data.
- [x] Bone creation/FK/IK foundations and rig editing model.
- [x] Local image/audio import and synchronized foundational audio preview.
- [x] Real PNG, animated GIF, and H.264 MP4 software rendering/export paths with progress and cancellation.
- [x] Unit and Compose integration tests for core workflows.

**Acceptance:** a user can create a project, draw and transform editable art, add keyframes and frames, play it, save/reopen it, and export actual rendered files.

## Phase 2 — Production expansion (not represented as complete)
- [ ] Pressure-curve editor, textured/custom raster brushes, brush import, and advanced stylus tilt rendering.
- [ ] Full SVG path/import/export coverage, Boolean path operations, clipping masks, and vector shape interpolation.
- [ ] Weight painting, general mesh skinning, reusable rig template UI, pose library, lip-sync assistant, and motion blending.
- [ ] Multi-scene transition editor and multiple simultaneous camera views.
- [ ] GPU-composited effects, motion blur, and arbitrary effect graphs.
- [ ] Multichannel audio mixing into exported video, complete waveform editing, and video import.
- [ ] WebM export, transparent video where codec support exists, and hardware-specific encoder fallback diagnostics.
- [ ] Multiple artboards, full ruler/guide/snapping editor, docking persistence, and complete keyboard shortcut customization.

## Verification policy

Run `./gradlew testDebugUnitTest assembleDebug` and the connected Compose tests on API 26 and API 35 emulators. The authoring sandbox used for the initial implementation has no Java or Android SDK, so CI/device results must be checked before calling a release production-ready. Export codecs vary by device; failures must be surfaced rather than replaced with placeholder files.
