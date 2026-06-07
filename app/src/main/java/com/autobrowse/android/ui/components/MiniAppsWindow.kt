package com.autobrowse.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autobrowse.android.domain.model.MiniAppId
import com.autobrowse.android.domain.model.NoteListItem
import com.autobrowse.android.ui.miniapps.NotesEditorState
import com.autobrowse.android.ui.miniapps.NotesMiniApp

@Composable
fun MiniAppsWindowOverlay(
    visible: Boolean,
    activeMiniApp: MiniAppId?,
    notes: List<NoteListItem>,
    notesEditor: NotesEditorState,
    onClose: () -> Unit,
    onLaunchApp: (MiniAppId) -> Unit,
    onBackToLauncher: () -> Unit,
    onNotesSearchChange: (String) -> Unit,
    onSelectNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onDeleteNote: (String) -> Unit,
    onNoteTitleChange: (String) -> Unit,
    onNoteBodyChange: (String) -> Unit,
    onToggleNotePin: () -> Unit,
    onToggleNotePreview: () -> Unit,
    onWrapNoteSelection: (String, String) -> Unit,
    onInsertNoteLinePrefix: (String) -> Unit,
    onInsertNoteBlock: (String) -> Unit,
    onAttachNoteImage: (android.net.Uri) -> Unit,
    onSaveNoteDrawing: (android.graphics.Bitmap) -> Unit,
    onExportNoteText: () -> Unit,
    onExportNoteMarkdown: () -> Unit,
    onExportNotePdf: () -> Unit,
    onExportNoteImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        when (activeMiniApp) {
            MiniAppId.NOTES -> NotesMiniApp(
                notes = notes,
                editor = notesEditor,
                onBack = onBackToLauncher,
                onSearchChange = onNotesSearchChange,
                onSelectNote = onSelectNote,
                onNewNote = onNewNote,
                onDeleteNote = onDeleteNote,
                onTitleChange = onNoteTitleChange,
                onBodyChange = onNoteBodyChange,
                onTogglePin = onToggleNotePin,
                onTogglePreview = onToggleNotePreview,
                onWrapSelection = onWrapNoteSelection,
                onInsertLinePrefix = onInsertNoteLinePrefix,
                onInsertBlock = onInsertNoteBlock,
                onAttachImage = onAttachNoteImage,
                onSaveDrawing = onSaveNoteDrawing,
                onExportText = onExportNoteText,
                onExportMarkdown = onExportNoteMarkdown,
                onExportPdf = onExportNotePdf,
                onExportImage = onExportNoteImage,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            )
            null -> MiniAppsLauncherGrid(
                onClose = onClose,
                onLaunchApp = onLaunchApp,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun MiniAppsLauncherGrid(
    onClose: () -> Unit,
    onLaunchApp: (MiniAppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
                Text(
                    text = "Mini Apps",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close mini apps",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
        }
        Text(
            text = "Launch tools alongside your browser.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniAppTile(
                label = "Notes",
                tint = Color(0xFFFFB800),
                icon = Icons.Default.Note,
                onClick = { onLaunchApp(MiniAppId.NOTES) },
            )
        }
    }
}

@Composable
private fun MiniAppTile(
    label: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
fun MiniAppsLauncherIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(34.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Apps,
            contentDescription = "Mini apps",
            modifier = Modifier.size(20.dp),
            tint = Color.White,
        )
    }
}