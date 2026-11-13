package com.buildai.app.ui

import com.buildai.app.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class ChatMarkdownTest {
    @Test fun fencedHtmlStaysSourceAndStreamingFenceRemainsCopyable() {
        val html = "<!DOCTYPE html>\n<html>\n  <body>Hello</body>\n</html>"
        val blocks = markdownBlocks("Here is your page.\n\n```html\n$html\n```")
        assertEquals(2, blocks.size); assertTrue(blocks.last().code)
        assertEquals("html", blocks.last().language); assertEquals(html, blocks.last().text)
        val partial = markdownBlocks("```html\n<!DOCTYPE html>\n<html>")
        assertEquals("<!DOCTYPE html>\n<html>", partial.single().text); assertTrue(partial.single().code)
    }
    @Test fun emphasisAndListsLoseSyntaxWhileCodeAndUnsafeLinksStayLiteral() {
        val result = inlineMarkdown("## Hello\n**Bold** and `a*b`\n- item\n[Source](https://example.com)\n[Bad](javascript:alert(1))")
        assertEquals("Hello\nBold and a*b\n• item\nSource\n[Bad](javascript:alert(1))", result.text)
        assertEquals("https://example.com", result.getStringAnnotations("url", 0, result.length).single().item)
    }
}
