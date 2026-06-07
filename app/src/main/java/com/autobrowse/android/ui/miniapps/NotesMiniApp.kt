package com.autobrowse.android.ui.miniapps

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.autobrowse.android.domain.model.NoteListItem
import com.autobrowse.android.ui.components.MarkdownMessageText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotesEditorState(
    val selectedNoteId: String? = null,
    val title: String = "",
    val body: String = "",
    val isPinned: Boolean = false,
    val isPreviewMode: Boolean = false,
    val searchQuery: String = "",
)

@Composable
fun NotesMiniApp(
    notes: List<NoteListItem>,
    editor: NotesEditorState,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onDeleteNote: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onTogglePin: () -> Unit,
    onTogglePreview: () -> Unit,
    onWrapSelection: (prefix: String, suffix: String) -> Unit,
    onInsertLinePrefix: (String) -> Unit,
    onInsertBlock: (String) -> Unit,
    onAttachImage: (android.net.Uri) -> Unit,
    onSaveDrawing: (Bitmap) -> Unit,
    onExportText: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportPdf: () -> Unit,
    onExportImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noteBackground = Color(0xFFFFF9EE)
    val sidebarColor = Color(0xFFF2F0E8)
    var showDrawing by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onAttachImage) }
    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(148.dp)
                    .fillMaxHeight()
                    .background(sidebarColor),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to apps")
                    }
                    Text("Notes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(
                    value = editor.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    singleLine = true,
                    placeholder = { Text("Search", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                IconButton(
                    onClick = onNewNote,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.End),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New note")
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(notes, key = { it.id }) { note ->
                        val selected = note.id == editor.selectedNoteId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) Color.White else Color.Transparent,
                                )
                                .clickable { onSelectNote(note.id) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (note.isPinned) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = note.preview.ifBlank { "No additional text" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatNoteDate(note.updatedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(noteBackground),
            ) {
                NotesFormatToolbar(
                    isPreviewMode = editor.isPreviewMode,
                    isPinned = editor.isPinned,
                    onTogglePreview = onTogglePreview,
                    onTogglePin = onTogglePin,
                    onBold = { onWrapSelection("**", "**") },
                    onItalic = { onWrapSelection("_", "_") },
                    onStrike = { onWrapSelection("~~", "~~") },
                    onHeading = { onInsertLinePrefix("# ") },
                    onBullet = { onInsertLinePrefix("- ") },
                    onQuote = { onInsertLinePrefix("> ") },
                    onCode = { onWrapSelection("`", "`") },
                    onLatex = { onInsertBlock("\n$$\n\n$$\n") },
                    onImage = { imagePicker.launch(arrayOf("image/*")) },
                    onDraw = { showDrawing = true },
                    onExportClick = { showExportMenu = true },
                    onDelete = { editor.selectedNoteId?.let(onDeleteNote) },
                    showExportMenu = showExportMenu,
                    onDismissExport = { showExportMenu = false },
                    onExportText = { showExportMenu = false; onExportText() },
                    onExportMarkdown = { showExportMenu = false; onExportMarkdown() },
                    onExportPdf = { showExportMenu = false; onExportPdf() },
                    onExportImage = { showExportMenu = false; onExportImage() },
                )

                if (editor.isPreviewMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = editor.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        MarkdownMessageText(content = editor.body)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        BasicTextField(
                            value = editor.title,
                            onValueChange = onTitleChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1C1E),
                                fontFamily = FontFamily.Serif,
                            ),
                            cursorBrush = SolidColor(Color(0xFFFFB800)),
                            decorationBox = { inner ->
                                if (editor.title.isEmpty()) {
                                    Text(
                                        "Title",
                                        style = TextStyle(
                                            fontSize = 22.sp,
                                            color = Color(0xFF8E8E93),
                                            fontFamily = FontFamily.Serif,
                                        ),
                                    )
                                }
                                inner()
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color(0xFFE5E5EA),
                        )
                        BasicTextField(
                            value = editor.body,
                            onValueChange = onBodyChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = Color(0xFF3A3A3C),
                                fontFamily = FontFamily.SansSerif,
                            ),
                            cursorBrush = SolidColor(Color(0xFFFFB800)),
                            decorationBox = { inner ->
                                if (editor.body.isEmpty()) {
                                    Text(
                                        "Start writing… Markdown, LaTeX ($$…$$), tables, and code supported.",
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            color = Color(0xFF8E8E93),
                                        ),
                                    )
                                }
                                inner()
                            },
                        )
                    }
                }
            }
        }

        if (showDrawing) {
            NotesDrawingSheet(
                onDismiss = { showDrawing = false },
                onSave = { strokes ->
                    showDrawing = false
                    if (strokes.isNotEmpty()) {
                        onSaveDrawing(strokesToBitmap(strokes))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun NotesFormatToolbar(
    isPreviewMode: Boolean,
    isPinned: Boolean,
    onTogglePreview: () -> Unit,
    onTogglePin: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrike: () -> Unit,
    onHeading: () -> Unit,
    onBullet: () -> Unit,
    onQuote: () -> Unit,
    onCode: () -> Unit,
    onLatex: () -> Unit,
    onImage: () -> Unit,
    onDraw: () -> Unit,
    onExportClick: () -> Unit,
    onDelete: () -> Unit,
    showExportMenu: Boolean,
    onDismissExport: () -> Unit,
    onExportText: () -> Unit,
    onExportMarkdown: () -> Unit,
    onExportPdf: () -> Unit,
    onExportImage: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFAF8F2),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            FormatIcon(Icons.Default.FormatBold, "Bold", onBold)
            FormatIcon(Icons.Default.FormatItalic, "Italic", onItalic)
            FormatIcon(Icons.Default.StrikethroughS, "Strikethrough", onStrike)
            FormatIcon(Icons.Default.Title, "Heading", onHeading)
            FormatIcon(Icons.Default.FormatListBulleted, "Bullet list", onBullet)
            FormatIcon(Icons.Default.FormatQuote, "Quote", onQuote)
            FormatIcon(Icons.Default.Code, "Code", onCode)
            FormatIcon(Icons.Default.Title, "LaTeX", onLatex)
            FormatIcon(Icons.Default.Image, "Image", onImage)
            FormatIcon(Icons.Default.Brush, "Draw", onDraw)
            IconButton(onClick = onTogglePreview, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (isPreviewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle preview",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin note",
                    modifier = Modifier.size(18.dp),
                )
            }
            Box {
                IconButton(onClick = onExportClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.SaveAlt, contentDescription = "Export", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showExportMenu, onDismissRequest = onDismissExport) {
                    DropdownMenuItem(text = { Text("Text (.txt)") }, onClick = onExportText)
                    DropdownMenuItem(text = { Text("Markdown (.md)") }, onClick = onExportMarkdown)
                    DropdownMenuItem(text = { Text("PDF") }, onClick = onExportPdf)
                    DropdownMenuItem(text = { Text("Image (PNG)") }, onClick = onExportImage)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete note", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FormatIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(17.dp))
    }
}

@Composable
fun NoteImageBlock(path: String, caption: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(path)
            .crossfade(true)
            .build(),
        contentDescription = caption,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.FillWidth,
    )
}

private fun formatNoteDate(timestamp: Long): String {
    val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

private fun strokesToBitmap(strokes: List<DrawStroke>, width: Int = 900, height: Int = 600): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        paint.color = stroke.color.toArgb()
        paint.strokeWidth = stroke.width
        val path = android.graphics.Path()
        stroke.points.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        canvas.drawPath(path, paint)
    }
    return bitmap
}