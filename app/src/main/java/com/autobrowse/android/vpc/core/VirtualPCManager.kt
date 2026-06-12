package com.autobrowse.android.vpc.core

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.autobrowse.android.vpc.provision.RootfsProvisioner
import com.autobrowse.android.vpc.service.UbuntuForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class VirtualPCManager(
    private val context: Context,
    private val provisioner: RootfsProvisioner,
    private val scope: CoroutineScope,
) {
    private val proot = ProotCommandBuilder(context)
    private val _state = MutableStateFlow<VpcState>(VpcState.Unknown)
    val state: StateFlow<VpcState> = _state.asStateFlow()

    private var bootStartedAt: Long = 0L
    private var bootProcess: Process? = null

    val workspace: File get() = proot.workspaceDir

    init {
        scope.launch { refreshState() }
    }

    suspend fun refreshState() = withContext(Dispatchers.IO) {
        when {
            provisioner.isProvisioned() -> {
                val version = provisioner.installedVersion() ?: "unknown"
                val running = _state.value
                if (running is VpcState.Running) {
                    _state.value = running.copy(
                        uptimeMs = (SystemClock.elapsedRealtime() - bootStartedAt).coerceAtLeast(0L),
                    )
                } else {
                    _state.value = VpcState.Provisioned(
                        version = version,
                        prootAvailable = proot.isProotAvailable(),
                    )
                }
            }
            _state.value is VpcState.Provisioning -> Unit
            else -> _state.value = VpcState.NotProvisioned
        }
    }

    fun startProvisioning() {
        provisioner.enqueueDownload()
        _state.value = VpcState.Provisioning("queued", 0f, "Preparing download…")
    }

    suspend fun start(desktop: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        if (!provisioner.isProvisioned()) {
            return@withContext Result.failure(IllegalStateException("Virtual PC not provisioned"))
        }
        if (!proot.isProotAvailable()) {
            _state.value = VpcState.Running(
                headless = !desktop,
                desktopRunning = desktop,
                demoMode = true,
            )
            bootStartedAt = SystemClock.elapsedRealtime()
            return@withContext Result.success(Unit)
        }

        _state.value = VpcState.Booting(listOf("Starting Ubuntu environment…"))
        val intent = Intent(context, UbuntuForegroundService::class.java).apply {
            action = UbuntuForegroundService.ACTION_START
            putExtra(UbuntuForegroundService.EXTRA_DESKTOP, desktop)
        }
        context.startForegroundService(intent)

        val initScript = if (desktop) {
            "/usr/local/bin/vpc-init"
        } else {
            "/usr/local/bin/vpc-init-headless"
        }
        val command = proot.build(listOf("/bin/bash", "-lc", initScript))
        val result = CommandExecutor.run(
            command = command,
            environment = proot.processEnvironment(),
            timeout = 30.seconds,
        )
        bootProcess = null
        if (!result.timedOut && result.exitCode == 0) {
            bootStartedAt = SystemClock.elapsedRealtime()
            _state.value = VpcState.Running(
                headless = !desktop,
                desktopRunning = desktop,
            )
            Result.success(Unit)
        } else {
            val message = result.stderr.text.ifBlank { result.stdout.text }
                .ifBlank { "Boot failed (exit ${result.exitCode})" }
            _state.value = VpcState.Error(message)
            Result.failure(IllegalStateException(message))
        }
    }

    suspend fun startDemo() {
        bootStartedAt = SystemClock.elapsedRealtime()
        _state.value = VpcState.Running(
            headless = false,
            desktopRunning = true,
            demoMode = true,
        )
    }

    suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        val intent = Intent(context, UbuntuForegroundService::class.java).apply {
            action = UbuntuForegroundService.ACTION_STOP
        }
        context.startService(intent)
        bootProcess?.destroyForcibly()
        bootProcess = null
        if (provisioner.isProvisioned()) {
            _state.value = VpcState.Provisioned(
                version = provisioner.installedVersion() ?: "unknown",
                prootAvailable = proot.isProotAvailable(),
            )
        } else {
            _state.value = VpcState.NotProvisioned
        }
    }

    suspend fun execute(
        command: String,
        timeout: Duration = 120.seconds,
        workdir: String = "/root",
    ): ExecResult = withContext(Dispatchers.IO) {
        val running = _state.value
        if (running is VpcState.Running && running.demoMode) {
            return@withContext ExecResult(
                exitCode = 0,
                stdout = CapturedOutput("demo@vpc:~$ $command\n[demo mode — provision rootfs + proot binaries to run real commands]"),
                stderr = CapturedOutput(""),
                durationMs = 12,
            )
        }
        if (running !is VpcState.Running && _state.value !is VpcState.Provisioned) {
            return@withContext ExecResult(
                exitCode = 1,
                stdout = CapturedOutput(""),
                stderr = CapturedOutput("Virtual PC is not running. Call vpc_start first."),
            )
        }
        if (!proot.isProotAvailable()) {
            return@withContext ExecResult(
                exitCode = 1,
                stdout = CapturedOutput(""),
                stderr = CapturedOutput("proot binaries missing from jniLibs"),
            )
        }
        val argv = proot.build(listOf("/bin/bash", "-lc", command), workdir)
        CommandExecutor.run(argv, proot.processEnvironment(), timeout)
    }

    fun statusSummary(): String {
        return when (val s = _state.value) {
            is VpcState.NotProvisioned -> "not_provisioned"
            is VpcState.Provisioning -> "provisioning:${s.stage}"
            is VpcState.Provisioned -> "provisioned proot=${s.prootAvailable}"
            is VpcState.Booting -> "booting"
            is VpcState.Running -> buildString {
                append("running headless=${s.headless} desktop=${s.desktopRunning}")
                if (s.demoMode) append(" demo=true")
                append(" uptime_ms=${s.uptimeMs}")
            }
            is VpcState.Error -> "error:${s.message}"
            VpcState.Unknown -> "unknown"
        }
    }

    fun onProvisioningProgress(stage: String, progress: Float, detail: String?) {
        _state.value = VpcState.Provisioning(stage, progress, detail)
    }

    fun onProvisioningComplete(version: String) {
        _state.value = VpcState.Provisioned(version, proot.isProotAvailable())
    }

    fun onProvisioningFailed(message: String) {
        _state.value = VpcState.Error(message, recoverable = true)
    }
}