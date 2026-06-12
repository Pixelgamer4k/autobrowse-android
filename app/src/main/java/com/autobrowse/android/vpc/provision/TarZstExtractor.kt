package com.autobrowse.android.vpc.provision

import android.system.Os
import com.github.luben.zstd.ZstdInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max

class TarZstExtractor(
    private val rootfsDir: File,
    private val onProgress: (Float, String) -> Unit = { _, _ -> },
) {
    fun extract(input: InputStream, expectedBytes: Long = 0L) {
        rootfsDir.mkdirs()
        val hardlinks = mutableListOf<TarArchiveEntry>()
        var extractedBytes = 0L

        ZstdInputStream(input.buffered()).use { zstd ->
            TarArchiveInputStream(zstd).use { tarIn ->
                var entry: TarArchiveEntry? = tarIn.nextEntry
                while (entry != null) {
                    val dest = File(rootfsDir, entry.name)
                    guardPathTraversal(dest)
                    when {
                        entry.isDirectory -> dest.mkdirs()
                        entry.isSymbolicLink -> runCatching {
                            Os.symlink(entry.linkName, dest.absolutePath)
                        }
                        entry.isLink -> hardlinks += entry
                        entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO -> Unit
                        else -> {
                            dest.parentFile?.mkdirs()
                            dest.outputStream().use { out -> tarIn.copyTo(out) }
                            if (entry.mode and 0b001_000_000 != 0) dest.setExecutable(true)
                        }
                    }
                    extractedBytes += max(entry.size, 1L)
                    if (expectedBytes > 0L) {
                        onProgress(
                            (extractedBytes.toFloat() / expectedBytes).coerceIn(0f, 0.99f),
                            "Unpacking ${entry.name}",
                        )
                    }
                    entry = tarIn.nextEntry
                }
            }
        }

        hardlinks.forEach { link ->
            val dest = File(rootfsDir, link.name)
            runCatching { Os.symlink(link.linkName, dest.absolutePath) }
        }
        onProgress(1f, "Extraction complete")
    }

    private fun guardPathTraversal(dest: File) {
        val canonical = dest.canonicalPath
        val root = rootfsDir.canonicalPath
        require(canonical.startsWith(root)) { "Path traversal blocked: ${dest.path}" }
    }
}