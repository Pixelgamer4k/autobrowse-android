package com.autobrowse.android.vpc.core

sealed class VpcState {
    data object Unknown : VpcState()

    data object NotProvisioned : VpcState()

    data class Provisioning(
        val stage: String,
        val progress: Float,
        val detail: String? = null,
    ) : VpcState()

    data class Provisioned(
        val version: String,
        val prootAvailable: Boolean,
    ) : VpcState()

    data class Booting(
        val logLines: List<String> = emptyList(),
    ) : VpcState()

    data class Running(
        val headless: Boolean,
        val desktopRunning: Boolean,
        val vncPort: Int = 5901,
        val websockifyPort: Int = 6080,
        val uptimeMs: Long = 0L,
        val demoMode: Boolean = false,
    ) : VpcState()

    data class Error(
        val message: String,
        val recoverable: Boolean = true,
    ) : VpcState()
}