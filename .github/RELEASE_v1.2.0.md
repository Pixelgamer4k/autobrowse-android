## Multiwindow Autobrowser v1.2.0

Virtual PC integration — Ubuntu via proot with AI agent tools and a Browser / Virtual PC mode switch.

### What's new

- **Browser ↔ Virtual PC mode switch** — segmented control above the workspace; chat bar stays persistent
- **Virtual PC screen** — provisioning flow, boot splash, demo Ubuntu desktop (matches reference mockups)
- **noVNC desktop renderer** — WebView + asset loader; connects to local websockify when live
- **Agent tools** — `vpc_status`, `vpc_start`, `vpc_stop`, `vpc_exec`, `vpc_write_file`, `vpc_read_file`
- **Rootfs provisioning** — WorkManager download + zstd/tar extraction pipeline
- **Ubuntu foreground service** — keeps the virtual environment alive while powered on

### Virtual PC setup

1. Switch to **Virtual PC** mode.
2. Tap **Try demo desktop** for the preview UI immediately, or **Download Ubuntu rootfs** when the image is hosted.
3. Add `libproot.so` and `libproot-loader.so` to `app/src/main/jniLibs/arm64-v8a/` for real Linux execution (see README in that folder).

### Downloads

| APK | Use case |
|-----|----------|
| **Multiwindow-Autobrowser.apk** | Signed release — recommended |
| **Multiwindow-Autobrowser-debug.apk** | Debug build |

Checksums in `SHA256SUMS.txt` on the Releases page.