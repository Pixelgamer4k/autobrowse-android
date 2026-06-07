package com.autobrowse.android.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.autobrowse.android.domain.model.AgentRole
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import io.noties.markwon.linkify.LinkifyPlugin

internal enum class MarkdownSegmentType { MARKDOWN, MERMAID }

internal data class MarkdownSegment(
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
        content.contains("~~") ||
        content.contains("\n- ") ||
        content.contains("\n* ") ||
        content.contains("\n> ") ||
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

internal fun stabilizeIncompleteMarkdown(content: String): String {
    var text = content.replace("\r\n", "\n")
    val fenceCount = Regex("```").findAll(text).count()
    if (fenceCount % 2 != 0) {
        text += "\n```"
    }
    if (text.contains("\\[") && text.lastIndexOf("\\[") > text.lastIndexOf("\\]")) {
        text += "\n\\]"
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

private fun createMarkwon(
    context: Context,
    textSizePx: Float,
    textColor: Int,
    linkColor: Int,
    codeBackgroundColor: Int,
    codeBlockBackgroundColor: Int,
): Markwon {
    return Markwon.builder(context)
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(TablePlugin.create(context))
        .usePlugin(TaskListPlugin.create(context))
        .usePlugin(LinkifyPlugin.create())
        .usePlugin(HtmlPlugin.create())
        .usePlugin(CoilImagesPlugin.create(context))
        .usePlugin(
            JLatexMathPlugin.create(textSizePx) { builder ->
                builder.inlinesEnabled(true)
                builder.theme().textColor(textColor)
            },
        )
        .usePlugin(
            object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .linkColor(linkColor)
                        .codeTypeface(Typeface.MONOSPACE)
                        .codeTextColor(textColor)
                        .codeBackgroundColor(codeBackgroundColor)
                        .codeBlockTextColor(textColor)
                        .codeBlockBackgroundColor(codeBlockBackgroundColor)
                        .headingBreakHeight(0)
                        .headingTextSizeMultipliers(floatArrayOf(1.35f, 1.25f, 1.15f, 1.1f, 1.05f, 1f))
                }
            },
        )
        .build()
}

@Composable
fun MarkdownMessageText(
    content: String,
    modifier: Modifier = Modifier,
    stabilizeIncomplete: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textSizePx = with(density) { 13.sp.toPx() }
    val colorScheme = MaterialTheme.colorScheme
    val textColor = colorScheme.onSurface.copy(alpha = 0.92f).toArgb()
    val linkColor = colorScheme.primary.toArgb()
    val codeBackgroundColor = colorScheme.surfaceVariant.copy(alpha = 0.65f).toArgb()
    val codeBlockBackgroundColor = colorScheme.surfaceVariant.copy(alpha = 0.85f).toArgb()
    val markwon = remember(context, textSizePx, textColor, linkColor, codeBackgroundColor, codeBlockBackgroundColor) {
        createMarkwon(
            context = context,
            textSizePx = textSizePx,
            textColor = textColor,
            linkColor = linkColor,
            codeBackgroundColor = codeBackgroundColor,
            codeBlockBackgroundColor = codeBlockBackgroundColor,
        )
    }
    val normalized = remember(content, stabilizeIncomplete) {
        val base = normalizeAssistantMarkdown(content)
        if (stabilizeIncomplete) stabilizeIncompleteMarkdown(base) else base
    }
    val segments = remember(normalized) { splitMarkdownSegments(normalized) }

    Column(modifier = modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, segment ->
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
                                    isVerticalScrollBarEnabled = false
                                }
                            },
                            update = { textView ->
                                textView.setTextColor(textColor)
                                textView.setLinkTextColor(linkColor)
                                textView.textSize = 13f
                                markwon.setMarkdown(textView, segment.content.trim())
                                textView.requestLayout()
                            },
                        )
                    }
                }
                MarkdownSegmentType.MERMAID -> {
                    keyForMermaid(segment.content, index)?.let { key ->
                        MermaidDiagram(
                            diagram = segment.content,
                            modifier = Modifier.fillMaxWidth(),
                            contentKey = key,
                        )
                    }
                }
            }
        }
    }
}

private fun keyForMermaid(diagram: String, index: Int): String? {
    if (diagram.isBlank()) return null
    return "$index:${diagram.hashCode()}"
}

@Composable
private fun MermaidDiagram(
    diagram: String,
    contentKey: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val html = remember(contentKey) { buildMermaidHtml(diagram) }
    var contentHeightPx by remember(contentKey) { mutableIntStateOf(0) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .then(
                if (contentHeightPx > 0) {
                    Modifier.height(with(density) { contentHeightPx.toDp() })
                } else {
                    Modifier.heightIn(max = 640.dp)
                },
            ),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        },
        update = { webView ->
            configureMermaidWebView(
                webView = webView,
                html = html,
                contentKey = contentKey,
                onHeight = { heightPx ->
                    mainHandler.post {
                        if (heightPx != contentHeightPx) {
                            contentHeightPx = heightPx
                        }
                    }
                },
            )
        },
    )
}

private fun sanitizeJsBridgeName(raw: String): String =
    raw.replace(Regex("[^a-zA-Z0-9_]"), "_")

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
private fun configureMermaidWebView(
    webView: WebView,
    html: String,
    contentKey: String,
    onHeight: (Int) -> Unit,
) {
    val bridgeTag = "MermaidBridge_${sanitizeJsBridgeName(contentKey)}"
    webView.removeJavascriptInterface(bridgeTag)
    webView.addJavascriptInterface(
        MermaidHeightBridge(onHeight),
        bridgeTag,
    )
    webView.loadDataWithBaseURL(
        "https://localhost/",
        html.replace("MERMAID_BRIDGE", bridgeTag),
        "text/html",
        "UTF-8",
        null,
    )
}

private class MermaidHeightBridge(
    private val onHeight: (Int) -> Unit,
) {
    @JavascriptInterface
    fun onHeight(heightPx: Float) {
        onHeight(heightPx.toInt().coerceIn(80, 1600))
    }
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
          html, body { margin: 0; padding: 8px; background: transparent; overflow: hidden; }
          #wrap { display: flex; justify-content: center; width: 100%; }
          .mermaid { width: 100%; }
          .mermaid svg { max-width: 100%; height: auto !important; }
        </style>
        </head>
        <body>
        <div id="wrap"><pre class="mermaid">$escaped</pre></div>
        <script>
          mermaid.initialize({ startOnLoad: false, theme: 'dark', securityLevel: 'loose' });
          function reportHeight() {
            var wrap = document.getElementById('wrap');
            var h = wrap ? wrap.getBoundingClientRect().height : 0;
            if (h > 0 && window.MERMAID_BRIDGE) {
              window.MERMAID_BRIDGE.onHeight(h + 16);
            }
          }
          mermaid.run({ querySelector: '.mermaid' }).then(function() {
            reportHeight();
            setTimeout(reportHeight, 250);
            setTimeout(reportHeight, 900);
          }).catch(function() { reportHeight(); });
        </script>
        </body>
        </html>
    """.trimIndent()
}