package com.deeperseeker.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * A deliberately small Markdown renderer covering what chat answers actually
 * use: fenced code blocks, inline code, bold, italic and headings.
 *
 * A full Markdown library would add a large dependency for marginal benefit;
 * this renderer is streaming-friendly because it re-parses plain text on every
 * recomposition without any incremental state to keep in sync.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { splitBlocks(text) }
    // Read the theme here: inlineMarkdown is a plain function and cannot touch
    // MaterialTheme, which is a @Composable-only property.
    val inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is Block.Code -> CodeBlock(block.content, block.language)
                is Block.Paragraph -> Text(
                    text = inlineMarkdown(block.content, inlineCodeBackground),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

private sealed interface Block {
    data class Paragraph(val content: String) : Block
    data class Code(val content: String, val language: String?) : Block
}

/**
 * Splits the text into code fences and everything else. Fences that are still
 * open (the model is mid-block) are rendered as code so the partial output
 * looks right while streaming.
 */
private fun splitBlocks(text: String): List<Block> {
    if (text.isBlank()) return emptyList()
    val blocks = mutableListOf<Block>()
    val buffer = StringBuilder()
    var inCode = false
    var language: String? = null
    val code = StringBuilder()

    for (line in text.lines()) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                blocks += Block.Code(code.toString().trimEnd('\n'), language)
                code.clear()
                language = null
                inCode = false
            } else {
                if (buffer.isNotBlank()) {
                    blocks += Block.Paragraph(buffer.toString().trim())
                    buffer.clear()
                }
                language = trimmed.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                inCode = true
            }
            continue
        }
        if (inCode) code.appendLine(line) else buffer.appendLine(line)
    }

    if (code.isNotEmpty()) blocks += Block.Code(code.toString().trimEnd('\n'), language)
    if (buffer.isNotBlank()) blocks += Block.Paragraph(buffer.toString().trim())
    return blocks
}

@Composable
private fun CodeBlock(content: String, language: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
    ) {
        if (!language.isNullOrBlank()) {
            Text(
                text = language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Applies inline emphasis. Order matters: `**bold**` is matched before `*italic*`
 * so the bold marker is not consumed by the italic rule.
 */
private fun inlineMarkdown(text: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index++
                }
            }

            text.startsWith("`", index) -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                        )
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            text.startsWith("*", index) -> {
                val end = text.indexOf('*', index + 1)
                if (end > index) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            text.startsWith("#", index) -> {
                // Headings render as bold lines; the marker itself is dropped.
                val end = text.indexOf('\n', index).let { if (it == -1) text.length else it }
                val heading = text.substring(index, end).trimStart('#').trim()
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(heading) }
                index = end
            }

            else -> {
                append(text[index])
                index++
            }
        }
    }
}