#### Executive Summary

You're building what amounts to a Termux + proot-distro + VNC stack, embedded natively in your app, with the AI agent as the primary "user" of the Linux environment. The good news: every individual piece (proot on Android, Ubuntu rootfs, XFCE over VNC, WebView-rendered noVNC) is battle-tested. The risk is in integration details: Android 10+ exec restrictions, the Android 12+ phantom process killer, rootfs extraction with symlinks, and session persistence. This document addresses all of them.

---

#### 1. Overall Architecture

The Virtual PC layer is a vertical slice that sits beside your browser window system, sharing only the agent runtime and chat UI.

```
┌─────────────────────────────────────────────────────────────┐
│                      ANDROID APP (Kotlin)                    │
│                                                              │
│  ┌────────────────────┐      ┌────────────────────────────┐ │
│  │   BROWSER MODE     │      │      VIRTUAL PC MODE       │ │
│  │  (existing)        │      │                            │ │
│  │  Floating WebView  │      │  DesktopRenderer           │ │
│  │  windows, dots,    │      │  (WebView -> noVNC client) │ │
│  │  window manager    │      │  + VPC toolbar (keys, etc) │ │
│  └────────┬───────────┘      └─────────────┬──────────────┘ │
│           │                                │                 │
│  ┌────────┴────────────────────────────────┴──────────────┐ │
│  │            ModeController (Browser | VirtualPC)        │ │
│  └────────────────────────┬───────────────────────────────┘ │
│                           │                                  │
│  ┌────────────────────────┴───────────────────────────────┐ │
│  │   AGENT RUNTIME (existing: tools, memory, skills,      │ │
│  │   trajectories, reflection) + NEW VpcToolset           │ │
│  └────────────────────────┬───────────────────────────────┘ │
│                           │                                  │
│  ┌────────────────────────┴───────────────────────────────┐ │
│  │                  VirtualPCManager                       │ │
│  │  - lifecycle (provision/boot/suspend/shutdown)         │ │
│  │  - CommandExecutor (proot exec bridge)                 │ │
│  │  - SessionManager (tmux-backed persistent shells)      │ │
│  │  - ScreenshotService (VNC fb / scrot)                  │ │
│  └──────┬─────────────────────────────┬───────────────────┘ │
│         │                             │                      │
│  ┌──────┴──────────┐         ┌────────┴──────────────────┐  │
│  │ RootfsProvisioner│        │ UbuntuForegroundService   │  │
│  │ download+verify  │        │ (FGS, wakelock, notif)    │  │
│  │ +extract image   │        │ owns the proot process    │  │
│  └─────────────────┘         └────────┬──────────────────┘  │
└───────────────────────────────────────┼──────────────────────┘
                                        │ fork/exec libproot.so
            ┌───────────────────────────┴───────────────────┐
            │            PROOT (rootless, link2symlink)     │
            │  ┌─────────────────────────────────────────┐  │
            │  │        UBUNTU 22.04 ARM64 ROOTFS        │  │
            │  │  Xtigervnc (:1) ── XFCE4 session        │  │
            │  │  websockify :6080 -> :5901 (noVNC)      │  │
            │  │  tmux server (agent sessions)           │  │
            │  │  python3/pip, git, xdotool, scrot, ...  │  │
            │  └─────────────────────────────────────────┘  │
            └───────────────────────────────────────────────┘
```

**Data flows:**

- **Display**: XFCE renders into Xtigervnc's in-memory framebuffer → websockify exposes it as WebSocket on `localhost:6080` → noVNC (bundled HTML/JS assets) in a WebView renders it. Everything stays on loopback; nothing leaves the device.
- **Agent commands**: Agent tool call → `VirtualPCManager.execute()` → spawns `proot ... /bin/bash -c "..."` OR injects into a persistent tmux pane → stdout/stderr/exit code captured → returned to the agent as the tool result.
- **Vision**: Agent calls `vpc_screenshot` → `scrot`/ImageMagick `import` runs inside the X display → PNG written to a shared path → Android reads, downscales, base64-encodes → attached to the next GPT-4o message.
- **File bridge**: A directory inside app-private storage is bind-mounted into the rootfs (e.g., `/sdcard-bridge` and `/agent-workspace`), so Android and Linux can exchange files without copies.

**Key architectural decisions:**

1. **proot binary ships as `libproot.so` in `jniLibs`**. Android 10+ (targetSdk 29+) blocks `exec()` of files in writable app storage (W^X). Files in `applicationInfo.nativeLibraryDir` remain executable. Ship `proot`, `loader`, and a static `busybox` this way (this is exactly how Termux-derived apps solve it).
2. **One proot "boot" process tree, owned by a foreground service**, not per-command proot invocations for everything. Headless commands run as lightweight `proot exec` entries into the same rootfs, but the desktop (Xvnc + XFCE + websockify + tmux) lives under a single supervised init script to keep the process count low (critical for the phantom process killer, see §8).
3. **The agent talks to Linux through Android, never directly**. All tool calls route through `VirtualPCManager` so you get timeouts, output truncation, logging into your existing trajectory system, and security policy enforcement in one place.

---

#### 2. UI/UX Architecture

**Mode switching.** Introduce a top-level mode concept above your window canvas:

- Add a `ModeController` holding `StateFlow<AppMode>` where `AppMode = Browser | VirtualPC`.
- UI affordance: a segmented pill or two icons (globe / monitor) in your top bar, or extend your existing window-dot indicator row with a visually distinct "PC" chip at the end (recommended, since users already understand that row as "places I can be"). Make the PC chip styled differently (monitor glyph, accent border) to signal it's a different category, not another browser window.
- `Crossfade` or `AnimatedContent` between `BrowserCanvas()` and `VirtualPcScreen()` composables. The chat bar lives *outside* this switch so it never remounts:

```kotlin
Column(Modifier.fillMaxSize()) {
    AnimatedContent(targetState = mode, modifier = Modifier.weight(1f)) { m ->
        when (m) {
            AppMode.Browser -> BrowserCanvas(...)
            AppMode.VirtualPC -> VirtualPcScreen(...)
        }
    }
    AgentChatBar(...)  // shared, persistent
}
```

**VirtualPcScreen states** (sealed):

- `NotProvisioned` → polished onboarding card: "Set up your Virtual PC", image size, one-tap download with progress (download %, extraction %, ETA), runs via WorkManager so it survives app death.
- `Booting` → branded splash with boot log ticker (users love seeing real boot lines; pipe the init script's output here).
- `Running` → full-bleed WebView with noVNC, plus a slim collapsible utility rail: Esc/Tab/Ctrl/Alt keys, clipboard sync button, screenshot button, "Force redraw", quality toggle.
- `Suspended/Error` → resume/repair actions.

**Desktop aesthetics** are baked into the image, not the app: XFCE with Arc-Dark or your own GTK theme, Papirus-Dark icons, a custom wallpaper carrying your app's branding, a bottom dock (Plank or a tuned xfce4-panel), and font rendering tweaks (`xfconf` DPI set for mobile screens, e.g., 144 DPI at 1280x800 virtual resolution). The result feels intentional with zero user setup.

**Chat bar integration.** When mode is VirtualPC, your agent's system prompt should be augmented: "You are currently viewing the Virtual PC. The vpc_* tools operate on it." Tool availability itself should not be mode-gated (the agent may legitimately use the VPC while the user looks at the browser), but mode determines default context and which screenshot the agent grabs for vision.

---

#### 3. Linux Environment Strategy

**proot execution model.**

- Bundle per-ABI binaries (arm64-v8a primary): `libproot.so`, `libproot-loader.so`, `libbusybox.so` in `jniLibs`.
- Required environment: `PROOT_TMP_DIR` pointed at app-private dir, `PROOT_LOADER` pointed at the loader in `nativeLibraryDir`, and `PROOT_NO_SECCOMP=1` as a fallback flag if you hit seccomp issues on specific OEM kernels (detect and retry).
- Core invocation flags: `--link2symlink` (no hardlink permission in app storage), `-0` (fake root, needed for apt), `-r $ROOTFS`, binds for `/dev`, `/proc`, `/sys`, `/dev/urandom→/dev/random`, fake `/proc/stat` and `/proc/loadavg` files (some kernels hide them, breaking tools), `-b $WORKSPACE:/agent-workspace`, `-w /root`, and a sane env (`PATH`, `HOME=/root`, `DISPLAY=:1`, `TMPDIR=/tmp`, `LANG=C.UTF-8`).

**Desktop stack inside the rootfs.** Use **Xtigervnc** (Xvnc) rather than Xvfb+x11vnc: it's one process that is both X server and VNC server, which halves your process count and copy overhead. Boot script (`/usr/local/bin/vpc-init`, supervised by the Android service):

```bash
#!/bin/bash
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
Xtigervnc :1 -geometry 1280x800 -depth 24 -rfbport 5901 \
  -localhost yes -SecurityTypes None &
export DISPLAY=:1
xfconf-query ... # any runtime theming
dbus-launch startxfce4 &
websockify --web /opt/novnc 6080 localhost:5901 &
tmux new-session -d -s agent
wait -n   # if any core process dies, exit so Android can restart us
```

`-SecurityTypes None` is acceptable only because everything binds to localhost inside the app sandbox; still generate a random port pair per boot to avoid collisions with other local apps.

**Rendering.** Bundle noVNC's static assets in `assets/novnc/` and serve them either via `WebViewAssetLoader` (cleanest, `https://appassets.androidplatform.net/...`) while noVNC connects its WebSocket to `ws://127.0.0.1:6080`. Configure the WebView: hardware acceleration on, `setSupportZoom(false)` (noVNC handles scaling), JS enabled, and intercept touch carefully (noVNC handles touch→mouse translation; add your utility rail for keys it can't synthesize). Target **1280x800 @ 24-bit**: it's the sweet spot for legibility and VNC bandwidth on a phone. Future upgrade path: a native VNC client on a `SurfaceView` for lower latency, but WebView+noVNC is the right MVP call.

**Lifecycle and background execution.**

- `UbuntuForegroundService`: a foreground service (type `specialUse` on API 34+, with a clear declaration string) holding a partial wakelock while the VPC is "powered on". The notification shows state (Running / Suspended) with Stop and Screenshot actions.
- Request battery-optimization exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) during VPC onboarding, with honest UX copy.
- **Suspend semantics**: when the user leaves the app for a long time and the OS pressure rises, you can drop the desktop (kill Xvnc/XFCE) but keep tmux + filesystem intact, then cold-restart the desktop in ~5-10s on return. The agent's headless tools keep working without the desktop at all, which is your most resilient mode.
- Persist a `vpc_state.json` (booted-at, ports, last session ids) so a process-death restart can reattach to tmux sessions if proot survived, or cleanly reboot if not (detect via pidfile + `/proc/<pid>` checks).

---

#### 4. Pre-built Image Creation & Distribution

**Build pipeline (CI-friendly, runs in GitLab CI or GitHub Actions on a Linux runner):**

1. Start from the official **Ubuntu Base 22.04 arm64 rootfs tarball** (~28 MB) on an x86_64 runner with `qemu-user-static` + binfmt, chrooted (or use a `--platform=linux/arm64` Docker container and `docker export`).
2. Install, in one layer: `xfce4 xfce4-terminal xfce4-goodies-lite-selection tigervnc-standalone-server dbus-x11 python3 python3-pip git tmux curl wget htop nano xdotool scrot imagemagick jq unzip ca-certificates fonts-dejavu`, plus a lightweight browser (`falkon` or `netsurf`; avoid Firefox/Chromium snap-based installs, which don't work under proot, install Firefox from Mozilla's deb repo if you need it), Plank or tuned xfce4-panel, Arc-Dark + Papirus-Dark, your wallpaper.
3. Pre-write XFCE config under `/root/.config/xfce4/` (panel layout, theme, compositing **off**, screensaver/lock disabled, no power manager) and `/opt/novnc`. Pre-create `/usr/local/bin/vpc-init` and agent helper scripts (`vpc-screenshot`, `vpc-click`, `vpc-type` wrappers around scrot/xdotool).
4. **Shrink**: `apt clean`, remove `/var/lib/apt/lists/*`, `/usr/share/doc`, `/usr/share/man`, all locales except C/en (`localepurge` or manual rm), strip `/usr/share/icons` to the one theme used, remove `__pycache__`. Target: **~550-750 MB uncompressed**.
5. **Compress with zstd -19** (`tar -cf - . | zstd -19 -T0`) → expect **180-260 MB**. zstd decompresses 3-5x faster than xz on mobile ARM cores, which matters enormously for first-launch UX.
6. Emit `rootfs-v{N}-arm64.tar.zst` + `manifest.json` (version, size, sha256, min app version).

**Hosting**: GitLab/GitHub Releases work but throttle; for production use **Cloudflare R2** (zero egress fees) behind a stable manifest URL so you can ship rootfs updates independently of APK updates.

**First-launch flow**: onboarding card → WorkManager download (resumable via HTTP Range, notification progress) → sha256 verify → streamed extraction (decompress + untar in one pass, no intermediate file, see §9) → write version marker → boot. Total first-launch time on a flagship with good Wi-Fi: roughly 2-4 minutes. Show stage-by-stage progress ("Downloading 142/220 MB", "Unpacking 38,000 files...") and let users leave the screen.

---

#### 5. Agent Tool Design

All tools share conventions: results include `exit_code`, truncated `stdout`/`stderr` (cap ~12 KB each, note truncation to the model), and `duration_ms`. All route through `VirtualPCManager`.

| Tool | Description | Parameters | Behavior / Android implementation |
|---|---|---|---|
| `vpc_status` | Report VPC state | none | Returns `provisioned/booted/desktop_running`, uptime, disk free, running sessions. Reads manager state + quick `df -h` probe. |
| `vpc_start` / `vpc_stop` | Power control | `desktop: Bool = true` | Starts the FGS and boot script (optionally headless, no Xvnc, for battery-cheap automation). Stop sends SIGTERM tree, then SIGKILL after grace. |
| `vpc_exec` | Run a shell command, wait for completion | `command: String`, `timeout_sec: Int = 120`, `workdir: String?` | Spawns `proot ... bash -lc "$command"` as a fresh process; captures output; enforces timeout with process-tree kill. The workhorse tool. |
| `vpc_session_exec` | Run a command in a **persistent** named tmux session | `session: String = "agent"`, `command: String`, `wait: Bool = true`, `timeout_sec` | `tmux send-keys` into the pane; when `wait`, append a sentinel (`; echo __DONE_$nonce_$?`) and poll `tmux capture-pane` until the sentinel appears. Gives the agent stateful shells (activated venvs, cd state, long-running REPLs). |
| `vpc_session_read` | Read recent output of a session | `session`, `lines: Int = 100` | `tmux capture-pane -p -S -N`. Lets the agent check on long jobs without blocking. |
| `vpc_write_file` | Create/overwrite a file in Linux FS | `path: String`, `content: String`, `mode: String?` | Write via the bind-mounted workspace when path is under it (fast path); otherwise pipe content through `vpc_exec` heredoc with base64 to avoid quoting hell: `echo $b64 | base64 -d > path`. |
| `vpc_read_file` | Read a file | `path`, `max_bytes: Int = 65536`, `offset: Int = 0` | Inverse of above; base64 through exec, decode on Android, truncate honestly. |
| `vpc_install` | Install packages | `apt: [String]?`, `pip: [String]?` | Wraps `apt-get install -y` / `pip install` with `DEBIAN_FRONTEND=noninteractive`, longer default timeout (600s), and output summarization (strip progress noise before returning to the model to save tokens). |
| `vpc_screenshot` | Capture desktop for vision | `region: {x,y,w,h}?`, `scale: Float = 0.75` | Runs `scrot -o /agent-workspace/.shots/$ts.png` (or `import -window root`) inside `DISPLAY=:1`; Android reads it from the bind mount, downscales/compresses to JPEG ~100-200 KB, returns it as an image content part on the next model turn. |
| `vpc_gui_action` | Mouse/keyboard automation | `actions: [{type: move\|click\|double_click\|drag\|type\|key, x?, y?, text?, key?}]` | Translates to a batched `xdotool` script (`xdotool mousemove X Y click 1 ...`) executed via `vpc_exec`. Batching cuts round trips. Pair with `vpc_screenshot` for the classic see-act loop. |
| `vpc_list_dir` | Directory listing | `path`, `depth: Int = 1` | `find -maxdepth` with size/type, formatted compactly for the model. |
| `vpc_push_file` / `vpc_pull_file` | Move files between browser-mode downloads / Android storage and the VPC | `android_path` or `vpc_path` | Copy through the bind-mounted bridge directory. This is the glue that lets browser-agent downloads be processed by Linux tooling. |

**Agent guidance (system prompt additions):** teach the model the preferred patterns: use `vpc_exec` for one-shots, `vpc_session_exec` for stateful work, screenshot-then-`vpc_gui_action` loops for GUI tasks, prefer CLI over GUI whenever possible (faster, cheaper, more reliable), and check `vpc_status` before assuming the desktop is up.

---

#### 6. Technical Implementation Plan

**Phase 1, Headless MVP (1-2 weeks).** Prove the foundation before any UI.
- `jniLibs` packaging of proot/loader/busybox; verify exec on Android 10-15 devices.
- `RootfsProvisioner` (download, verify, extract), `VirtualPCManager` (boot headless, `execute()`), `UbuntuForegroundService`.
- Agent tools: `vpc_exec`, `vpc_write_file`, `vpc_read_file`, `vpc_status`, `vpc_install`.
- Exit criteria: agent can run `python3 -c`, install a pip package, and write/run a script, surviving app backgrounding.

**Phase 2, Desktop Rendering (1-2 weeks).**
- Rootfs v2 with XFCE/Xtigervnc/websockify/noVNC; `vpc-init` supervisor script.
- `VirtualPcScreen` composable states, `DesktopRenderer` (WebView + asset loader + noVNC config), mode switcher, utility key rail.
- Exit criteria: polished Ubuntu desktop visible and interactive in-app.

**Phase 3, Agent Vision & GUI Automation (1 week).**
- `vpc_screenshot` pipeline into your multimodal message builder; `vpc_gui_action`; `vpc_session_exec`/`vpc_session_read` (tmux bridge); file push/pull bridge.
- System prompt updates, trajectory logging of VPC tool calls, skill-library entries for common VPC workflows.
- Exit criteria: agent completes "open the file manager and create a folder" via see-act loop, and a long pip-based data task via sessions.

**Phase 4, Polish & Hardening (1-2 weeks).**
- Onboarding/download UX with WorkManager, resumable downloads, error recovery and "repair install".
- Suspend/resume, phantom-process mitigations, OEM quirk handling, battery exemption flow.
- Quality toggles (resolution/color depth), clipboard sync, rootfs update channel, telemetry on boot success rates.

---

#### 7. Folder Structure & Component Design

```
app/src/main/
├── jniLibs/arm64-v8a/
│   ├── libproot.so, libproot-loader.so, libbusybox.so
├── assets/novnc/            # noVNC static bundle
└── java/.../vpc/
    ├── core/
    │   ├── VirtualPCManager.kt        # facade + state machine (singleton/DI)
    │   ├── ProotCommandBuilder.kt     # assembles argv + env for proot
    │   ├── CommandExecutor.kt         # process spawn, timeout, output capture
    │   ├── TmuxSessionManager.kt      # persistent session bridge
    │   └── VpcState.kt                # sealed states + persistence
    ├── provision/
    │   ├── RootfsProvisioner.kt       # orchestrates download→verify→extract
    │   ├── RootfsDownloadWorker.kt    # WorkManager, resumable
    │   └── TarZstExtractor.kt         # streaming extraction w/ symlinks
    ├── service/
    │   └── UbuntuForegroundService.kt # FGS, wakelock, watchdog, notification
    ├── desktop/
    │   ├── ScreenshotService.kt       # scrot trigger + bitmap pipeline
    │   └── XdotoolBridge.kt           # action batch → xdotool script
    ├── tools/                          # plugs into existing agent tool registry
    │   ├── VpcToolset.kt              # registers all tools + JSON schemas
    │   ├── VpcExecTool.kt, VpcFileTools.kt, VpcScreenshotTool.kt,
    │   ├── VpcGuiActionTool.kt, VpcSessionTools.kt, VpcInstallTool.kt
    └── ui/
        ├── VirtualPcScreen.kt, DesktopRenderer.kt
        ├── ProvisioningCard.kt, BootSplash.kt, VpcUtilityRail.kt
        └── ModeController.kt           # may live one level up
```

---

#### 8. Key Challenges & Solutions

- **Android 10+ W^X exec restriction**: cannot `exec()` from writable app dirs. **Solution**: ship all native binaries as fake `.so` files in `jniLibs` (set `android:extractNativeLibs="true"` or use legacy packaging) and exec from `nativeLibraryDir`. Everything *inside* the rootfs is fine because proot's loader runs it via ptrace tricks.
- **Android 12+ phantom process killer**: the OS kills app child processes beyond 32 system-wide and under memory pressure. **Solution**: (a) keep your process tree small, Xtigervnc instead of Xvfb+x11vnc, one tmux server, one websockify; (b) run under a foreground service which raises priority; (c) implement a watchdog in the FGS that detects death of the proot tree and offers one-tap resume, with tmux state making resumes cheap; (d) document the `max_phantom_processes` ADB override for power users only, never require it.
- **Rootfs extraction (symlinks, special files)**: Java tar libs mishandle symlinks and you can't create device nodes. **Solution**: app-private storage is ext4, so create symlinks with `android.system.Os.symlink()` during extraction; skip hardlinks (record and recreate as symlinks, matching proot `--link2symlink`); skip device nodes entirely (proot binds real `/dev`). Stream zstd→tar with Commons Compress + zstd-jni so you never hold the decompressed archive on disk.
- **proot performance**: ptrace syscall interception costs 20-50% on syscall-heavy workloads. **Solution**: accept it for MVP (CPU-bound Python is barely affected; `apt` and `npm install` are the painful cases); disable XFCE compositing; warn the agent in its prompt that package installs are slow so it sets long timeouts.
- **First-launch weight**: 200+ MB download. **Solution**: zstd, R2 hosting, resumable WorkManager download, honest progress UI, and a "headless-only" lite image option (~80 MB) later for users who only want agent tooling without the desktop.
- **Security**: the agent executes arbitrary shell as "root" (fake) inside the sandbox. The blast radius is the app sandbox plus network. **Solution**: keep VNC/websockify strictly on localhost; never bind-mount broad shared storage by default (only the dedicated bridge dir); add a per-command audit log into your trajectory store; consider a user-visible "agent is executing: `<cmd>`" ticker; and an optional confirmation gate for destructive patterns (`rm -rf`, `curl | bash`) configurable by the user.
- **OEM quirks** (Xiaomi/Samsung killers, seccomp differences): detect boot failure modes, retry with `PROOT_NO_SECCOMP=1`, and ship a diagnostics screen that dumps proot stderr for bug reports.

---

#### 9. Code-Level Guidance

**VirtualPCManager (core facade):**

```kotlin
class VirtualPCManager(
    private val ctx: Context,
    private val provisioner: RootfsProvisioner,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<VpcState>(VpcState.Unknown)
    val state: StateFlow<VpcState> = _state

    private val nativeDir get() = ctx.applicationInfo.nativeLibraryDir
    private val rootfs get() = File(ctx.filesDir, "vpc/rootfs")
    private val tmpDir get() = File(ctx.filesDir, "vpc/tmp").apply { mkdirs() }
    val workspace get() = File(ctx.filesDir, "vpc/workspace").apply { mkdirs() }

    fun prootCommand(inner: List<String>, workdir: String = "/root") = buildList {
        add("$nativeDir/libproot.so")
        add("--link2symlink"); add("-0")
        add("-r"); add(rootfs.absolutePath)
        listOf("/dev", "/proc", "/sys").forEach { add("-b"); add(it) }
        add("-b"); add("${workspace.absolutePath}:/agent-workspace")
        add("-w"); add(workdir)
        add("/usr/bin/env"); add("-i")
        add("HOME=/root"); add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        add("DISPLAY=:1"); add("TMPDIR=/tmp"); add("LANG=C.UTF-8")
        add("TERM=xterm-256color")
        addAll(inner)
    }

    suspend fun execute(
        command: String, timeout: Duration = 120.seconds, workdir: String = "/root",
    ): ExecResult = withContext(Dispatchers.IO) {
        val pb = ProcessBuilder(prootCommand(listOf("/bin/bash", "-lc", command), workdir))
            .apply {
                environment()["PROOT_TMP_DIR"] = tmpDir.absolutePath
                environment()["PROOT_LOADER"] = "$nativeDir/libproot-loader.so"
            }
        val start = SystemClock.elapsedRealtime()
        val proc = pb.start()
        val stdout = async { proc.inputStream.readCapped(MAX_OUTPUT) }
        val stderr = async { proc.errorStream.readCapped(MAX_OUTPUT) }
        val finished = proc.waitForCompat(timeout)
        if (!finished) proc.destroyForcibly().also { proc.waitFor() }
        ExecResult(
            exitCode = if (finished) proc.exitValue() else -1,
            stdout = stdout.await(), stderr = stderr.await(),
            timedOut = !finished,
            durationMs = SystemClock.elapsedRealtime() - start,
        )
    }
}
```

**Agent tool definition + handler (fits a typical OpenAI tool registry):**

```kotlin
object VpcExecTool {
    val schema = ToolSchema(
        name = "vpc_exec",
        description = "Execute a shell command inside the Virtual PC (Ubuntu). " +
            "Returns exit code, stdout, stderr. Use vpc_session_exec for stateful work.",
        parameters = jsonObject {
            "command" to string(required = true, desc = "Bash command to run")
            "timeout_sec" to integer(default = 120, max = 900)
            "workdir" to string(default = "/root")
        },
    )

    suspend fun handle(args: JsonObject, vpc: VirtualPCManager): JsonObject {
        if (vpc.state.value !is VpcState.Running)
            return errorJson("Virtual PC is not running. Call vpc_start first.")
        val r = vpc.execute(
            command = args.str("command"),
            timeout = args.int("timeout_sec", 120).seconds,
            workdir = args.str("workdir", "/root"),
        )
        return jsonObject {
            "exit_code" to r.exitCode; "timed_out" to r.timedOut
            "stdout" to r.stdout.text; "stdout_truncated" to r.stdout.truncated
            "stderr" to r.stderr.text; "duration_ms" to r.durationMs
        }
    }
}
```

**Streaming download + extraction (critical details only):**

```kotlin
suspend fun provision(url: String, expectedSha256: String) {
    val tar = File(ctx.cacheDir, "rootfs.tar.zst")
    resumableDownload(url, tar)                       // OkHttp + Range header
    require(tar.sha256() == expectedSha256) { "Checksum mismatch" }

    ZstdCompressorInputStream(tar.inputStream().buffered()).use { zstd ->
        TarArchiveInputStream(zstd).use { tarIn ->
            generateSequence { tarIn.nextEntry }.forEach { e ->
                val dest = File(rootfsDir, e.name).also { guardPathTraversal(it) }
                when {
                    e.isDirectory -> dest.mkdirs()
                    e.isSymbolicLink -> runCatching { Os.symlink(e.linkName, dest.path) }
                    e.isLink -> hardlinks += e            // recreate as symlinks after pass
                    e.isCharacterDevice || e.isBlockDevice || e.isFIFO -> { /* skip: proot binds /dev */ }
                    else -> dest.outputStream().use { tarIn.copyTo(it) }
                        .also { if (e.mode and 0b001_000_000 != 0) dest.setExecutable(true) }
                }
                reportProgress(tarIn.bytesRead)
            }
        }
    }
    hardlinks.forEach { Os.symlink(it.linkName.toAbsoluteRootfsPath(), File(rootfsDir, it.name).path) }
    File(rootfsDir, ".vpc_version").writeText(manifest.version)
}
```

**tmux sentinel pattern for `vpc_session_exec`:**

```kotlin
suspend fun sessionExec(session: String, command: String, timeout: Duration): ExecResult {
    val nonce = UUID.randomUUID().toString().take(8)
    vpc.execute("""tmux send-keys -t $session "${command.tmuxEscape()}; echo __DONE_${nonce}_\$?" Enter""")
    val deadline = now() + timeout
    while (now() < deadline) {
        delay(750)
        val pane = vpc.execute("tmux capture-pane -t $session -p -S -200").stdout.text
        Regex("__DONE_${nonce}_(\\d+)").find(pane)?.let { m ->
            return ExecResult(exitCode = m.groupValues[1].toInt(),
                stdout = pane.substringBeforeSentinel(nonce), ...)
        }
    }
    return ExecResult.timedOut(hint = "Job still running; use vpc_session_read to monitor.")
}
```

---

#### 10. Performance, Optimization & Future Roadmap

**Performance now:**
- Disable XFCE compositing and animations in the baked config; biggest single VNC win.
- 1280x800 @ 24bpp default with a user toggle to 1024x640 for older devices; noVNC `qualityLevel 6 / compressionLevel 2`.
- Run agent headless whenever possible (`vpc_start(desktop=false)`): no Xvnc means dramatically less CPU/battery for pure scripting tasks.
- Cap and summarize tool outputs before they hit the model; raw apt logs will torch your token budget.
- Downscale screenshots to ~1024px wide JPEG for GPT-4o; full-res adds cost without accuracy gains at desktop UI scale.

**Image-size roadmap:** lite headless image (~80 MB compressed) as an alternative tier; delta updates for rootfs upgrades (ship a versioned overlay tarball applied on top rather than full re-download).

**Future roadmap, in priority order:**
1. **Native VNC renderer** (SurfaceView client) replacing WebView/noVNC for latency and battery.
2. **Optional Termux:X11-style local display** (X over a local socket to a native view), removing VNC encode/decode entirely; the largest possible perf jump while staying rootless.
3. **AT-SPI / accessibility-tree GUI automation**: give the agent structured UI trees from Linux apps instead of pure pixel reasoning, mirroring how strong browser agents prefer DOM over screenshots.
4. **Multiple named environments** (per-project rootfs overlays or shared rootfs with separate HOMEs) so agent experiments can't pollute each other.
5. **Snapshot/restore** of the workspace dir for "undo" after risky agent operations.
6. **Optional rooted/KVM mode** on capable devices (real Linux VM via AVF on Android 13+ Pixels eventually) behind the same `VirtualPCManager` interface so tools don't change.

**Final advice on sequencing:** build Phase 1 headless first and wire `vpc_exec` into your agent immediately. You'll get 70% of the agent-capability value (Python, pip, git, file processing, scripting) before writing a single line of desktop UI, and you'll de-risk the two scariest unknowns (proot exec on modern Android, process lifecycle) with the smallest possible investment.
