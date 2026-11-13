package com.buildai.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.buildai.app.ui.theme.*

/** Keeps fenced source intact, including a fence that is still arriving over a stream. */
data class MarkdownBlock(val text: String, val code: Boolean = false, val language: String = "")
fun markdownBlocks(source: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val text = StringBuilder(); var fence: String? = null; var language = ""
    fun flush(code: Boolean) { if (text.isNotEmpty()) { result += MarkdownBlock(text.toString().removeSuffix("\n"), code, if (code) language else ""); text.clear() } }
    source.split('\n').forEach { line ->
        val trimmed = line.trimStart()
        if (fence == null && (trimmed.startsWith("```") || trimmed.startsWith("~~~"))) {
            flush(false); fence = trimmed.takeWhile { it == trimmed[0] }; language = trimmed.drop(fence!!.length).trim().take(30)
        } else if (fence != null && trimmed.startsWith(fence!!) && trimmed.drop(fence!!.length).isBlank()) {
            flush(true); fence = null; language = ""
        } else text.append(line).append('\n')
    }
    flush(fence != null)
    return result
}

@Composable
fun ChatMarkdown(source: String) {
    val blocks = remember(source) { markdownBlocks(source) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            if (block.code) CodeBlock(block) else {
                val uri = LocalUriHandler.current
                val content = remember(block.text) { inlineMarkdown(block.text) }
                SelectionContainer {
                    ClickableText(content, style = MaterialTheme.typography.bodyLarge.copy(color = OnBackground), onClick = { position ->
                        content.getStringAnnotations("url", position, position).firstOrNull()?.let { runCatching { uri.openUri(it.item) } }
                    })
                }
            }
        }
    }
}

/** Text formatting only; generated HTML is shown as source and never executed in chat. */
fun inlineMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    val pattern = Regex("(`[^`\n]+`|\\*\\*[^*]+\\*\\*|__[^_]+__|\\*[^*\n]+\\*|\\[[^\\]]+\\]\\(https?://[^\\s)]+\\))")
    source.lines().forEachIndexed { index, line ->
        if (index > 0) append("\n")
        val heading = Regex("^(#{1,6})\\s+(.+)$").find(line)
        val body = heading?.groupValues?.get(2) ?: line.replace(Regex("^\\s*[-*+]\\s+"), "• ").removePrefix("> ")
        if (heading != null) pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
        var cursor = 0
        pattern.findAll(body).forEach { match ->
            append(body.substring(cursor, match.range.first)); val part = match.value
            when {
                part.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(part.drop(1).dropLast(1)) }
                part.startsWith("**") || part.startsWith("__") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part.drop(2).dropLast(2)) }
                part.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(part.drop(1).dropLast(1)) }
                else -> { val name = part.substringAfter('[').substringBefore(']'); val url = part.substringAfter("](").dropLast(1)
                    pushStringAnnotation("url", url); withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) { append(name) }; pop() }
            }
            cursor = match.range.last + 1
        }
        append(body.substring(cursor)); if (heading != null) pop()
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(block.text) { mutableStateOf(false) }
    Surface(color = SurfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(block.language.ifBlank { "Code" }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                TextButton(onClick = { clipboard.setText(AnnotatedString(block.text)); copied = true }) { Text(if (copied) "Copied" else "Copy code") }
            }
            SelectionContainer { Text(block.text, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), color = OnSurface) }
        }
    }
}
