package com.autobrowse.android.vpc.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.time.Duration

object CommandExecutor {
    private const val MAX_OUTPUT_BYTES = 12 * 1024

    suspend fun run(
        command: List<String>,
        environment: Map<String, String> = emptyMap(),
        timeout: Duration,
    ): ExecResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val start = System.currentTimeMillis()
            val process = ProcessBuilder(command)
                .apply { environment().putAll(environment) }
                .redirectErrorStream(false)
                .start()

            val stdoutJob = async { readCapped(process.inputStream) }
            val stderrJob = async { readCapped(process.errorStream) }

            val finished = process.waitForCompat(timeout)
            if (!finished) {
                process.destroyForcibly()
                runCatching { process.waitFor() }
            }

            ExecResult(
                exitCode = if (finished) process.exitValue() else -1,
                stdout = stdoutJob.await(),
                stderr = stderrJob.await(),
                timedOut = !finished,
                durationMs = System.currentTimeMillis() - start,
            )
        }
    }

    private fun readCapped(stream: InputStream): CapturedOutput {
        val buffer = ByteArray(MAX_OUTPUT_BYTES + 1)
        val read = stream.read(buffer, 0, buffer.size)
        stream.close()
        if (read <= 0) return CapturedOutput("")
        val truncated = read > MAX_OUTPUT_BYTES
        val length = if (truncated) MAX_OUTPUT_BYTES else read
        return CapturedOutput(
            text = String(buffer, 0, length, Charsets.UTF_8),
            truncated = truncated,
        )
    }

    private fun Process.waitForCompat(timeout: Duration): Boolean {
        val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
        while (System.currentTimeMillis() < deadline) {
            if (!isAlive) return true
            Thread.sleep(50)
        }
        return !isAlive
    }
}