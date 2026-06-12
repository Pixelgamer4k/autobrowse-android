package com.autobrowse.android.vpc.core

import android.content.Context
import java.io.File

class ProotCommandBuilder(private val context: Context) {
    private val nativeDir: String
        get() = context.applicationInfo.nativeLibraryDir

    val rootfsDir: File
        get() = File(context.filesDir, "vpc/rootfs")

    val tmpDir: File
        get() = File(context.filesDir, "vpc/tmp").apply { mkdirs() }

    val workspaceDir: File
        get() = File(context.filesDir, "vpc/workspace").apply { mkdirs() }

    fun isProotAvailable(): Boolean =
        File(nativeDir, "libproot.so").exists() &&
            File(nativeDir, "libproot-loader.so").exists()

    fun build(inner: List<String>, workdir: String = "/root"): List<String> = buildList {
        add("$nativeDir/libproot.so")
        add("--link2symlink")
        add("-0")
        add("-r")
        add(rootfsDir.absolutePath)
        listOf("/dev", "/proc", "/sys").forEach { bind ->
            add("-b")
            add(bind)
        }
        add("-b")
        add("${workspaceDir.absolutePath}:/agent-workspace")
        add("-w")
        add(workdir)
        add("/usr/bin/env")
        add("-i")
        add("HOME=/root")
        add("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
        add("DISPLAY=:1")
        add("TMPDIR=/tmp")
        add("LANG=C.UTF-8")
        add("TERM=xterm-256color")
        addAll(inner)
    }

    fun processEnvironment(): Map<String, String> = mapOf(
        "PROOT_TMP_DIR" to tmpDir.absolutePath,
        "PROOT_LOADER" to "$nativeDir/libproot-loader.so",
        "PROOT_NO_SECCOMP" to "1",
    )
}