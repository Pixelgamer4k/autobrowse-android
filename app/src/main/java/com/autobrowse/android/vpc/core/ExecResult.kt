package com.autobrowse.android.vpc.core

data class CapturedOutput(
    val text: String,
    val truncated: Boolean = false,
)

data class ExecResult(
    val exitCode: Int,
    val stdout: CapturedOutput,
    val stderr: CapturedOutput,
    val timedOut: Boolean = false,
    val durationMs: Long = 0L,
) {
    fun summary(): String = buildString {
        append("exit_code=$exitCode")
        if (timedOut) append(" timed_out=true")
        append(" duration_ms=$durationMs")
        if (stdout.text.isNotBlank()) {
            append("\nstdout:\n")
            append(stdout.text)
            if (stdout.truncated) append("\n[stdout truncated]")
        }
        if (stderr.text.isNotBlank()) {
            append("\nstderr:\n")
            append(stderr.text)
            if (stderr.truncated) append("\n[stderr truncated]")
        }
    }

    companion object {
        fun timedOut(hint: String): ExecResult = ExecResult(
            exitCode = -1,
            stdout = CapturedOutput(""),
            stderr = CapturedOutput(hint),
            timedOut = true,
        )
    }
}