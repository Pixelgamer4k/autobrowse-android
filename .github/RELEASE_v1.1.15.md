## Multiwindow Autobrowser v1.1.15

On-device **LiteRT-LM** inference, a streamlined local model setup, and stronger multi-window agent tooling — building on the v1.1.10 foundation.

### Downloads

| APK | Use case |
|-----|----------|
| **Multiwindow-Autobrowser.apk** | Signed release build — recommended for daily use |
| **Multiwindow-Autobrowser-debug.apk** | Debug build — for development and testing |

Both APKs are attached directly to this release (not zipped). Checksums are in `SHA256SUMS.txt`.

### Requirements

- Android 8.0+ (API 26+)
- Internet access for cloud LLM mode
- ~3–4 GB free storage for a local Gemma 4 model (optional)

### What's new since v1.1.10

#### Local LLM (LiteRT-LM)

- **LiteRT-LM on-device inference** — single `.litertlm` model bundles via Google's `litertlm-android`
- **Gemma 4 E2B & E4B** — official multimodal models with native tool calling and vision
- **Download or import** — fetch from Hugging Face or import your own `.litertlm` file
- **Delete models** — remove downloaded weights to free storage
- **Loading feedback** — clear progress during download, import, and engine warmup
- **RAM-aware context window** — defaults scale with device memory (8K–24K); slider up to 128K
- **CPU / GPU backends** — pick the best trade-off for your phone
- **Single-screen setup** — compact E2B/E4B picker with no scrolling

#### Agent & browser

- **Window arrange, resize, and focus** — agent tools for multi-tab comparison and research workflows
- **Faster post-run learning** — background heuristics refine strategies without blocking the UI

#### Build & distribution

- **GitHub Actions builds** — signed release and debug APKs built in CI (no local Gradle required)

### Quick start

1. Install **Multiwindow-Autobrowser.apk** (signed release).
2. Complete **LLM Setup** on first launch — **Cloud API is recommended** for fast agent runs.
3. For offline use: switch to **Local**, download **Gemma 4 E2B** or **E4B**, wait for the loading indicator, then tap **Continue**.
4. Open tabs with `+`; each tab is a floating window. Ask the agent to research or shop across windows.

### Window controls

| Gesture | Action |
|---------|--------|
| Tap 3-dot handle | Window menu (refresh, maximize, close) |
| Drag 3-dot handle | Move window |
| Drag corner arc | Resize (4:3 aspect ratio) |
| Tap window | Bring to front |

### Build info

| | |
|---|---|
| Version | 1.1.15 (1115) |
| Package | `com.autobrowse.android` |
| Min SDK | 26 |
| Target SDK | 35 |
| Signed | Yes (release APK) |

### Privacy

- API tokens encrypted on device
- No telemetry or analytics SDK
- Local model weights remain on your phone