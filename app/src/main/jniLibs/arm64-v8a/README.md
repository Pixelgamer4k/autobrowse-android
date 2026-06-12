# proot native binaries (arm64-v8a)

Place these executables here (renamed as `.so` for Android W^X compatibility):

- `libproot.so` — proot binary
- `libproot-loader.so` — proot loader
- `libbusybox.so` — optional static busybox helper

Build or extract from a Termux-compatible proot release, then copy into this folder before release builds.

Without these files the Virtual PC runs in **demo mode** (desktop preview UI + simulated `vpc_exec` responses).