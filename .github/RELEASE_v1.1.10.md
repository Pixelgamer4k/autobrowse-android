## Multiwindow Autobrowser v1.1.10

First public release of **Multiwindow Autobrowser** — a floating multi-window mobile browser with an integrated LLM automation agent.

### Downloads

| APK | Use case |
|-----|----------|
| **Multiwindow Autobrowser.apk** | Signed release build — recommended for daily use |
| **Multiwindow Autobrowser debug.apk** | Debug build — for development and testing |

Both files are attached directly to this release (not zipped). Checksums are in `SHA256SUMS.txt`.

### Requirements

- Android 8.0+ (API 26+)
- Internet access for cloud LLM mode
- ~500 MB free storage if using local on-device models

### What's new in v1.1.10

- **Multiwindow Autobrowser** rebrand with custom 3D launcher icon
- **Floating browser windows** — drag, resize, maximize, and stack multiple live tabs
- **Corner resize grip** — minimal arc handle, slightly outside the window frame
- **3-dot window chrome** — tap to open menu, drag to move
- **AI browser agent** — natural-language browsing, research, and automation
- **Secure Dashboard** — bundled skills, learned skills export/import, self-improved strategies
- **LLM Setup** — Cloud API (OpenRouter-compatible, recommended) or local GGUF models (experimental)
- **Agent chat** — Markdown rendering, attachments (image/PDF/video), voice input
- **40+ browser tools** — search, snapshot, click, extract, tab management, and more

### Quick start

1. Install **Multiwindow Autobrowser.apk** (signed release).
2. Complete **LLM Setup** on first launch — Cloud API is recommended.
3. Open tabs with the `+` button; each tab is a floating window.
4. Ask the agent: *"Find 4 affordable sneakers on Amazon and open each in its own window."*

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
| Version | 1.1.10 (1110) |
| Package | `com.autobrowse.android` |
| Min SDK | 26 |
| Target SDK | 35 |
| Signed | Yes (release APK) |

### Privacy

- API tokens encrypted on device
- No telemetry or analytics SDK
- Local model weights remain on your phone

### Full changelog

- Rebrand to Multiwindow Autobrowser with GitHub presentation and mockups
- Custom app icon (3D Android + stacked windows)
- External corner resize grip (arc only, no kite-shaped L-bracket)
- Remove mini apps / Notes feature — focused browser + agent experience
- Polished LLM setup flow (cloud-first, local experimental path)