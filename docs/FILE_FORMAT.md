# `.animstudio` project format — version 1

An Animation Studio project is a UTF-8 JSON document inside a standard ZIP container. It is not a flattened image.

| Entry | Purpose |
| --- | --- |
| `manifest.json` | Format identifier, version, and generator. Readers reject future unsupported versions. |
| `scene.json` | Complete editable project: canvas, FPS, duration, scenes, layers, cels, paths, shapes, text, images by reference, keyframes/easing, bones, skin weights, cameras, audio clips, effects, and asset metadata. |
| `assets/<id>/<filename>` | Original imported media, referenced by stable asset IDs. |

## Compatibility and safety

- `kind` is the polymorphic type discriminator for vector objects and fills. Unknown JSON fields are ignored to permit additive changes.
- A breaking model change increments `formatVersion` and requires an explicit migration before loading.
- IDs are stable UUID strings. Object edits and animation tracks therefore survive a save/reopen cycle.
- Readers reject unsafe paths, duplicate entries, invalid references, non-finite geometry, duplicate IDs, oversized entries, and archives exceeding configured memory limits.
- Imported bytes are copied into app-managed storage before an archive is written. Runtime project decoding uses bounded reads to reduce ZIP-bomb risk.
- Autosaves use the same complete editable format, not screenshots or rendered previews.

## Example manifest

```json
{"format":"com.animationstudio.project","version":1,"generator":"Animation Studio"}
```
