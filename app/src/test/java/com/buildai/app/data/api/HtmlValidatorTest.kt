package com.buildai.app.data.api

import org.junit.Assert.*
import org.junit.Test

class HtmlValidatorTest {
    private val html = "<!DOCTYPE html><html><head></head><body><button>Add</button><script>let items=[];</script></body></html>"
    @Test fun acceptsFencedCompleteDocument() { assertEquals(html, HtmlValidator.extractAndValidate("```html\n$html\n```")) }
    @Test fun acceptsMixedCaseClosingTags() { assertTrue(HtmlValidator.extractAndValidate(html.replace("</html>", "</HTML>")).endsWith("</HTML>")) }
    @Test fun rejectsTruncatedOutput() { assertThrows(IllegalArgumentException::class.java) { HtmlValidator.extractAndValidate(html.removeSuffix("</html>")) } }
    @Test fun rejectsMissingBody() { assertThrows(IllegalArgumentException::class.java) { HtmlValidator.extractAndValidate("<html><script>void 0</script></html>") } }
    @Test fun rejectsMissingFunctionality() { assertThrows(IllegalArgumentException::class.java) { HtmlValidator.extractAndValidate("<html><body>Hi</body></html>") } }
    @Test fun rejectsExternalScripts() { assertThrows(IllegalArgumentException::class.java) { HtmlValidator.extractAndValidate(html.replace("<script>", "<script src='https://example.com/code.js'>")) } }
    @Test fun rejectsExternalStylesheets() { assertThrows(IllegalArgumentException::class.java) { HtmlValidator.extractAndValidate(html.replace("</head>", "<link rel='stylesheet' href='style.css'></head>")) } }
    @Test fun rejectsNonHtmlAnswer() { assertThrows(IllegalArgumentException::class.java) { HtmlValidator.extractAndValidate("Sorry, I cannot do that") } }
}
