package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val HIGHLIGHT_PATTERN = Regex("\\*\\*(.+?)\\*\\*|\\((.+?)\\)")

/**
 * Personas consistently wrap inner thoughts in **bold** and stage directions in (parentheses) --
 * rendering those literally as raw asterisks/parens reads as a formatting bug, not style, so this
 * strips the delimiters and tints just that inner text with the theme's lime accent instead.
 */
@Composable
fun ChatMessageText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 20.sp
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, color, highlightColor) {
        buildAnnotatedString {
            var lastIndex = 0
            for (match in HIGHLIGHT_PATTERN.findAll(text)) {
                if (match.range.first > lastIndex) {
                    withStyle(SpanStyle(color = color)) {
                        append(text.substring(lastIndex, match.range.first))
                    }
                }
                val inner = match.groupValues[1].ifEmpty { match.groupValues[2] }
                withStyle(SpanStyle(color = highlightColor)) {
                    append(inner)
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < text.length) {
                withStyle(SpanStyle(color = color)) {
                    append(text.substring(lastIndex))
                }
            }
        }
    }
    Text(text = annotated, fontSize = fontSize, lineHeight = lineHeight, modifier = modifier)
}
