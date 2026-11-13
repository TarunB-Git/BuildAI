package com.buildai.app.data.api

/** Structural checks only. JavaScript is also executed in an isolated WebView before saving. */
object HtmlValidator {
    fun extractAndValidate(raw: String): String {
        require(raw.length <= 600_000) { "The generated app is too large. Try a simpler app." }
        val start = Regex("<!doctype\\s+html[^>]*>|<html(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(raw)?.range?.first
        require(start != null) { "The AI didn't return an HTML app. Try again." }
        val end = Regex("</html\\s*>", RegexOption.IGNORE_CASE).findAll(raw).lastOrNull()?.range?.last
        require(end != null && end > start) { "The generated app was cut short. Try again." }
        val html = raw.substring(start, end + 1)
        require(Regex("<body(?:\\s[^>]*)?>[\\s\\S]*</body\\s*>", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            "The generated app is missing its page content. Try again."
        }
        require(Regex("<script(?:\\s[^>]*)?>[\\s\\S]*?</script\\s*>", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            "The generated app is missing its functionality. Try again."
        }
        require(!Regex("<(script|iframe|object|embed)[^>]+(?:src|data)\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(html)
            && !Regex("<link[^>]+rel\\s*=\\s*['\"]?stylesheet", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            "The generated app needs external files. Try again to create a self-contained app."
        }
        return html.trim()
    }
}
