package com.autobrowse.android.ui.vpc

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.autobrowse.android.vpc.core.VpcState

@Composable
fun VirtualPcScreen(
    state: VpcState,
    onProvision: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onStartDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is VpcState.Running -> {
            Box(modifier = modifier.fillMaxSize()) {
                DesktopRenderer(
                    demoMode = state.demoMode,
                    websockifyPort = state.websockifyPort,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                VpcUtilityRail(
                    onStop = onStop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                )
            }
        }
        is VpcState.Booting -> BootSplash(state.logLines, modifier)
        is VpcState.Provisioning -> ProvisioningProgress(state, modifier)
        is VpcState.Provisioned -> ProvisionedCard(
            version = state.version,
            prootAvailable = state.prootAvailable,
            onStart = onStart,
            onStartDemo = onStartDemo,
            modifier = modifier,
        )
        is VpcState.Error -> ErrorCard(state.message, onProvision, onStartDemo, modifier)
        VpcState.NotProvisioned, VpcState.Unknown -> NotProvisionedCard(onProvision, onStartDemo, modifier)
    }
}

@Composable
private fun NotProvisionedCard(
    onProvision: () -> Unit,
    onStartDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DemoPreviewImage()
        Text(
            "Set up your Virtual PC",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            "Download Ubuntu 22.04 (~220 MB), then run a full desktop with XFCE over VNC. " +
                "The AI agent can execute shell commands, install packages, and automate the desktop.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onProvision, modifier = Modifier.fillMaxWidth()) {
            Text("Download Ubuntu rootfs")
        }
        OutlinedButton(onClick = onStartDemo, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Try demo desktop", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun ProvisionedCard(
    version: String,
    prootAvailable: Boolean,
    onStart: () -> Unit,
    onStartDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DemoPreviewImage()
        Text("Ubuntu rootfs v$version ready", style = MaterialTheme.typography.titleLarge)
        Text(
            if (prootAvailable) {
                "proot binaries detected — tap Power on to boot the live desktop."
            } else {
                "Add libproot.so + libproot-loader.so to jniLibs for real Linux execution. Demo mode is available now."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Power on Virtual PC", modifier = Modifier.padding(start = 8.dp))
        }
        if (!prootAvailable) {
            OutlinedButton(onClick = onStartDemo, modifier = Modifier.fillMaxWidth()) {
                Text("Launch demo desktop")
            }
        }
    }
}

@Composable
private fun ProvisioningProgress(state: VpcState.Provisioning, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            when (state.stage) {
                "download" -> "Downloading Ubuntu image"
                "extract" -> "Unpacking files"
                else -> "Preparing Virtual PC"
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        LinearProgressIndicator(
            progress = { state.progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        )
        state.detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BootSplash(logLines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Booting Ubuntu…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        logLines.forEach { line ->
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
    onDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Virtual PC error", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Text(message, textAlign = TextAlign.Center)
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text("Retry setup", modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onDemo) { Text("Open demo desktop") }
    }
}

@Composable
private fun DemoPreviewImage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        ImageRequest.Builder(context)
            .data("file:///android_asset/vpc/ubuntu-desktop-demo.jpg")
            .build(),
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        tonalElevation = 2.dp,
    ) {
        Image(
            painter = painter,
            contentDescription = "Ubuntu desktop preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}