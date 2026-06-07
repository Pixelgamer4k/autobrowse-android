package com.autobrowse.android.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.autobrowse.android.domain.model.AttachmentType
import com.autobrowse.android.domain.model.PendingAttachment
import com.autobrowse.android.ui.theme.AppGradients
import com.autobrowse.android.ui.theme.Motion
import com.autobrowse.android.ui.theme.SectionSeparator
import java.util.UUID

private val ComposerBorder = Color(0xFF4A4A56)

@Composable
fun ChatBar(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<PendingAttachment>,
    onAddAttachment: (PendingAttachment) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    ChatComposer(
        value = value,
        onValueChange = onValueChange,
        attachments = attachments,
        onAddAttachment = onAddAttachment,
        onRemoveAttachment = onRemoveAttachment,
        onSend = onSend,
        isSending = isSending,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<PendingAttachment>,
    onAddAttachment: (PendingAttachment) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    keyboardLiftActive: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    androidx.compose.runtime.LaunchedEffect(isFocused) {
        onFocusChange(isFocused)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { addFromUri(context, it, AttachmentType.IMAGE, onAddAttachment) }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { addFromUri(context, it, AttachmentType.PDF, onAddAttachment) }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { addFromUri(context, it, AttachmentType.VIDEO, onAddAttachment) }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!spoken.isNullOrBlank()) {
                onValueChange(
                    if (value.isBlank()) spoken else "$value $spoken",
                )
            }
        }
    }

    fun launchSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
        }
        runCatching { speechLauncher.launch(intent) }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) launchSpeechRecognizer()
    }

    fun onMicClick() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> launchSpeechRecognizer()
            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val canSend = (value.isNotBlank() || attachments.isNotEmpty()) && !isSending
    val showSend = value.isNotBlank() || attachments.isNotEmpty()
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.9f,
        animationSpec = Motion.springSnappy,
        label = "sendScale",
    )

    val bottomInsets = if (keyboardLiftActive) {
        WindowInsets.navigationBars.union(WindowInsets.ime)
    } else {
        WindowInsets.navigationBars
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppGradients.composerDock)
            .windowInsetsPadding(bottomInsets.only(WindowInsetsSides.Bottom)),
    ) {
        SectionSeparator()

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
        AttachmentPickerSheet(
            visible = showPicker,
            onDismiss = { showPicker = false },
            onPickImage = { imagePicker.launch(arrayOf("image/*")) },
            onPickPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
            onPickVideo = { videoPicker.launch(arrayOf("video/*")) },
        )

        AnimatedVisibility(
            visible = attachments.isNotEmpty(),
            enter = fadeIn(Motion.tweenMedium) + slideInVertically(Motion.springSmoothOffset) { -it / 3 },
            exit = fadeOut(Motion.tweenQuick) + slideOutVertically(Motion.springSmoothOffset) { -it / 3 },
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(attachments, key = { it.id }) { attachment ->
                    AttachmentChip(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment.id) },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AppGradients.composerInput)
                .border(
                    width = 1.dp,
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                    } else {
                        ComposerBorder.copy(alpha = 0.55f)
                    },
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (attachments.isEmpty()) "Ask anything"
                        else "Add a message about your attachment…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(20.dp),
                maxLines = 6,
                enabled = !isSending,
                interactionSource = interactionSource,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AnimatedContent(
                targetState = showPicker,
                transitionSpec = {
                    fadeIn(Motion.tweenQuick) + scaleIn(Motion.springBouncy) togetherWith
                        fadeOut(Motion.tweenQuick) + scaleOut(Motion.tweenQuick)
                },
                label = "attachIcon",
            ) { expanded ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (expanded) {
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                                        Color(0xFF2A2A36),
                                    ),
                                )
                            } else {
                                AppGradients.actionChip
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (expanded) {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                            } else {
                                Color.White.copy(alpha = 0.1f)
                            },
                            shape = CircleShape,
                        )
                        .clickable { showPicker = !expanded },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach file",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!showSend) {
                    IconButton(
                        onClick = { onMicClick() },
                        enabled = !isSending,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                AnimatedContent(
                    targetState = showSend,
                    transitionSpec = {
                        fadeIn(Motion.tweenQuick) + scaleIn(Motion.springBouncy) togetherWith
                            fadeOut(Motion.tweenQuick) + scaleOut(Motion.tweenQuick)
                    },
                    label = "micSendToggle",
                ) { sending ->
                    if (sending) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .scale(sendScale)
                                .clip(CircleShape)
                                .background(
                                    if (canSend) AppGradients.sendButton else AppGradients.actionChip,
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (canSend) {
                                        Color.White.copy(alpha = 0.25f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    },
                                    shape = CircleShape,
                                )
                                .clickable(enabled = canSend, onClick = onSend),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = if (canSend) {
                                    Color(0xFF1A1A22)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(AppGradients.speakPill)
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(20.dp),
                                )
                                .clickable(enabled = !isSending, onClick = { onMicClick() }),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Speak",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: PendingAttachment,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppGradients.attachmentChip)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (attachment.type) {
                AttachmentType.IMAGE -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(attachment.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = attachment.fileName,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                AttachmentType.PDF, AttachmentType.VIDEO -> {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = attachment.type.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(end = 2.dp)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
                Text(
                    text = attachment.type.name.lowercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun AttachmentType.icon(): ImageVector = when (this) {
    AttachmentType.IMAGE -> Icons.Default.Image
    AttachmentType.PDF -> Icons.Default.PictureAsPdf
    AttachmentType.VIDEO -> Icons.Default.Videocam
}

private fun addFromUri(
    context: android.content.Context,
    uri: Uri,
    type: AttachmentType,
    onAdd: (PendingAttachment) -> Unit,
) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
    val mime = context.contentResolver.getType(uri) ?: when (type) {
        AttachmentType.IMAGE -> "image/jpeg"
        AttachmentType.PDF -> "application/pdf"
        AttachmentType.VIDEO -> "video/mp4"
    }
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    } ?: uri.lastPathSegment ?: "file"

    val size = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
    } ?: 0L

    onAdd(
        PendingAttachment(
            id = UUID.randomUUID().toString(),
            uri = uri,
            type = type,
            fileName = name,
            mimeType = mime,
            sizeBytes = size,
        ),
    )
}