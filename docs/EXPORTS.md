# Rendering and export

The exporter uses the same project model as the editor and evaluates the selected scene at every requested frame. It never writes a placeholder or renames another file type.

- **PNG / JPEG:** high-resolution still at the configured start frame. JPEG is flattened because the codec cannot store alpha.
- **PNG / JPEG sequence:** individually encoded frames in a user-selected SAF directory.
- **GIF:** GIF89a with looping metadata, per-frame delay, optional transparency, a 3:3:2 palette, and LZW compression.
- **MP4:** H.264 through `MediaCodec`, flexible YUV420 input honoring device plane/pixel strides, and `MediaMuxer`. Dimensions are rounded down to even values. The current MP4 path is **silent**; audio preview exists but final audio mixing/muxing is not yet complete.

Rendering runs in a cancellable `CoroutineWorker`. Frame count, phase, progress, failures, and output URIs are reported through WorkManager. Codec support and performance vary by device. No WebM file is advertised until a real encoder is available.

Large effects and high resolutions can require substantial memory. Reduce export resolution or disable expensive effects on low-memory devices; out-of-memory/codec errors are surfaced rather than replaced with fake output.
