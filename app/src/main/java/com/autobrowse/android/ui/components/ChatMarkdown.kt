package com.autobrowse.android.ui.components

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.autobrowse.android.domain.model.AgentRole
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin

private enum class MarkdownSegmentType { MARKDOWN, MERMAID }

private data class MarkdownSegment(
    val type: MarkdownSegmentType,
    val content: String,
)

internal fun shouldRenderMarkdown(role: AgentRole, content: String): Boolean {
    if (role != AgentRole.USER) return true
    return content.contains("**") ||
        content.contains("__") ||
        content.contains("```") ||
        content.contains("$$") ||
        content.contains("\\(") ||
        content.contains("\\[") ||
        content.contains("\n#") ||
        (content.contains("|") && content.contains("\n"))
}

internal fun normalizeAssistantMarkdown(content: String): String {
    var text = content.replace("\r\n", "\n")
    text = text.replace(Regex("""\\\[(.*?)\\\]""", RegexOption.DOT_MATCHES_ALL)) { match ->
        "\n$$\n${match.groupValues[1].trim()}\n$$\n"
    }
    text = text.replace(Regex("""\\\((.+?)\\\)""")) { match ->
        "$$${match.groupValues[1].trim()}$$"
    }
    return text
}

internal fun splitMarkdownSegments(markdown: String): List<MarkdownSegment> {
    if (!markdown.contains("```mermaid")) {
        return listOf(MarkdownSegment(MarkdownSegmentType.MARKDOWN, markdown))
    }
    val segments = mutableListOf<MarkdownSegment>()
    val regex = Regex("""```mermaid\s*\n([\s\S]*?)```""", RegexOption.MULTILINE)
    var lastEnd = 0
    regex.findAll(markdown).forEach { match ->
        if (match.range.first > lastEnd) {
            segments += MarkdownSegment(
                MarkdownSegmentType.MARKDOWN,
                markdown.substring(lastEnd, match.range.first),
            )
        }
        segments += MarkdownSegment(MarkdownSegmentType.MERMAID, match.groupValues[1].trim())
        lastEnd = match.range.last + 1
    }
    if (lastEnd < markdown.length) {
        segments += MarkdownSegment(MarkdownSegmentType.MARKDOWN, markdown.substring(lastEnd))
    }
    if (segments.isEmpty()) {
        segments += MarkdownSegment(MarkdownSegmentType.MARKDOWN, markdown)
    }
    return segments
}

private fun createMarkwon(context: Context, textSizePx: Float): Markwon {
    return Markwon.builder(context)
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(HtmlPlugin.create())
        .usePlugin(ImagesPlugin.create())
        .usePlugin(
            JLatexMathPlugin.create(textSizePx) { builder ->
                builder.inlinesEnabled(true)
            },
        )
        .build()
}

@Composable
fun MarkdownMessageText(
    content: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textSizePx = with(density) { 13.sp.toPx() }
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f).toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val markwon = remember(context, textSizePx) { createMarkwon(context, textSizePx) }
    val normalized = remember(content) { normalizeAssistantMarkdown(content) }
    val segments = remember(normalized) { splitMarkdownSegments(normalized) }

    Column(modifier = modifier.fillMaxWidth()) {
        segments.forEach { segment ->
            when (segment.type) {
                MarkdownSegmentType.MARKDOWN -> {
                    if (segment.content.isNotBlank()) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT,
                                    )
                                    movementMethod = LinkMovementMethod.getInstance()
                                    includeFontPadding = false
                                    setTextIsSelectable(true)
                                }
                            },
                            update = { textView ->
                                textView.setTextColor(textColor)
                                textView.setLinkTextColor(linkColor)
                                textView.textSize = 13f
                                markwon.setMarkdown(textView, segment.content.trim())
                            },
                        )
                    }
                }
                MarkdownSegmentType.MERMAID -> {
                    MermaidDiagram(
                        diagram = segment.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 420.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MermaidDiagram(
    diagram: String,
    modifier: Modifier = Modifier,
) {
    val html = remember(diagram) { buildMermaidHtml(diagram) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "https://localhost/",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        },
    )
}

private fun buildMermaidHtml(diagram: String): String {
    val escaped = diagram
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
        <style>
          html, body { margin: 0; padding: 8px; background: transparent; }
          .mermaid { display: flex; justify-content: center; }
        </style>
        </head>
        <body>
        <pre class="mermaid">$escaped</pre>
        <script>mermaid.initialize({ startOnLoad: true, theme: 'dark', securityLevel: 'loose' });</script>
        </body>
        </html>
    """.trimIndent()
}