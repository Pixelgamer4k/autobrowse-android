package com.autobrowse.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    highlightBackground: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
    highlightForeground: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight? = null,
) {
    val annotated = remember(text, query, highlightBackground, highlightForeground) {
        buildHighlightedAnnotatedString(
            text = text,
            query = query,
            highlightBackground = highlightBackground,
            highlightForeground = highlightForeground,
            fontWeight = fontWeight,
        )
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

fun buildHighlightedAnnotatedString(
    text: String,
    query: String,
    highlightBackground: Color,
    highlightForeground: Color,
    fontWeight: FontWeight? = null,
): AnnotatedString {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) {
        return AnnotatedString(text)
    }
    val lowerText = text.lowercase()
    val lowerQuery = trimmedQuery.lowercase()
    return buildAnnotatedString {
        append(text)
        var start = 0
        while (start < lowerText.length) {
            val index = lowerText.indexOf(lowerQuery, start)
            if (index < 0) break
            addStyle(
                SpanStyle(
                    background = highlightBackground,
                    color = highlightForeground,
                    fontWeight = fontWeight,
                ),
                index,
                index + lowerQuery.length,
            )
            start = index + lowerQuery.length
        }
    }
}