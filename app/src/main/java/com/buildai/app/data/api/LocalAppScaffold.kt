package com.buildai.app.data.api

import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.ProjectPlan

/** Common presentation only. The selected model supplies the app's controls and behavior. */
object LocalAppScaffold {
    val CODE_GRAMMAR = """
        root ::= "{\"html\":" string ",\"js\":" string "}"
        string ::= "\"" char* "\""
        char ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
    """.trimIndent()

    /** Used only after a failed interaction check, then checked again before accepting the app. */
    fun repairCalledEventHandler(output: String): String? {
        val code = runCatching { org.json.JSONObject(output) }.getOrNull() ?: return null
        val script = code.optString("js")
        val registration = Regex("""(\.addEventListener\(\s*['"](?:click|submit|input|change)['"]\s*,\s*)([a-zA-Z_${'$'}][\w${'$'}]*)(\(\s*\))(\s*\))""")
        val repaired = registration.replace(script) { match ->
            val name = match.groupValues[2]
            if (Regex("\\bfunction\\s+${Regex.escape(name)}\\s*\\(").containsMatchIn(script))
                match.groupValues[1] + name + match.groupValues[4]
            else match.value
        }
        return if (repaired != script) code.put("js", repaired).toString() else null
    }

    /** Supported addition checks identify stale/out-of-scope numeric values before using this repair. */
    fun repairLiveNumericAddition(output: String): String? {
        val code = runCatching { org.json.JSONObject(output) }.getOrNull() ?: return null
        val script = code.optString("js")
        val binding = Regex("""(?:const|let|var)\s+([a-zA-Z_${'$'}][\w${'$'}]*)\s*=\s*(?:(?:Number|parseFloat|parseInt)\(\s*)?document\.getElementById\(\s*(['"])([^'"]+)\2\s*\)\.value""")
        val values = binding.findAll(script).associate { match ->
            match.groupValues[1] to "Number(document.getElementById(${org.json.JSONObject.quote(match.groupValues[3])}).value)"
        }
        val addition = Regex("""(=\s*)([a-zA-Z_${'$'}][\w${'$'}]*)\s*\+\s*([a-zA-Z_${'$'}][\w${'$'}]*)(?=\s*[;,)\}])""")
        val repaired = addition.replace(script) { match ->
            val first = values[match.groupValues[2]]
            val second = values[match.groupValues[3]]
            if (first != null && second != null) match.groupValues[1] + first + "+" + second else match.value
        }
        return if (repaired != script) code.put("js", repaired).toString() else null
    }

    fun repairMissingTextOutput(output: String): String? {
        val code = runCatching { org.json.JSONObject(output) }.getOrNull() ?: return null
        val original = code.optString("html")
        var controls = original
        val target = Regex("""document\.getElementById\(\s*(['"])([^'"]+)\1\s*\)\.(?:textContent|innerText)\s*=""")
        target.findAll(code.optString("js")).map { it.groupValues[2] }.distinct().forEach { id ->
            if (!Regex("""\bid\s*=\s*(['"])${Regex.escape(id)}\1""").containsMatchIn(controls))
                controls += "<output id=\"${escape(id)}\"></output>"
        }
        return if (controls != original) code.put("html", controls).toString() else null
    }

    fun wrap(output: String, plan: ProjectPlan, design: DesignSpec): String {
        val normalized = if (output.trimStart().startsWith("{")) {
            val code = org.json.JSONObject(output)
            val controls = code.getString("html")
            val script = code.getString("js")
            require(controls.isNotBlank() && script.isNotBlank()) { "The model did not include app controls and code." }
            require(Regex("<(input|button|textarea|select|canvas|form|table|a)(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(controls)) { "The model did not include usable app controls." }
            "<app>" + controls + "<script>" + script.replace("</script", "<\\/script", ignoreCase = true) + "</script></app>"
        } else output
        // Some models still return a full document. Accept it only through the same validator.
        if (Regex("<!doctype|<html(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(normalized) && !normalized.startsWith("<app>"))
            return HtmlValidator.extractAndValidate(normalized)
        val content = Regex("<app>([\\s\\S]*?)</app>", RegexOption.IGNORE_CASE).find(normalized)?.groupValues?.get(1)
            ?: error("The model did not finish the app's controls and code. Try fewer features.")
        require(content.contains("<script", ignoreCase = true)) { "The model did not include working app code." }
        val palette = DesignDefaults.palette(design.paletteId)
        val primary = "#%06X".format(palette.primary and 0xFFFFFF)
        val accent = "#%06X".format(palette.accent and 0xFFFFFF)
        return """<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${escape(plan.appName)}</title><style>
:root{--primary:$primary;--accent:$accent;color-scheme:dark}*{box-sizing:border-box}body{margin:0;background:#0d1117;color:#f4f6fb;font:16px system-ui}main{max-width:460px;margin:auto;padding:20px 16px 70px}h1{font-size:1.5rem}h2{font-size:1.2rem}p{line-height:1.5;overflow-wrap:anywhere}label{display:block;margin:12px 0}input,textarea,select{width:100%;padding:12px;background:#161b22;color:#f4f6fb;border:1px solid #64748b;border-radius:8px;font:inherit;margin:5px 0}input[type=checkbox],input[type=radio]{width:auto}button,input[type=submit]{background:var(--primary);color:white;border:0;border-radius:9px;padding:12px;min-height:44px;margin:6px 6px 6px 0;font:inherit}table{width:100%;border-collapse:collapse}th,td{padding:8px;border-bottom:1px solid #64748b}.card,fieldset{background:#161b22;padding:16px;border-radius:12px;border:0;margin:12px 0}.error,[role=alert]{color:#fca5a5}header p{color:#adb6c4}nav{display:flex;flex-wrap:wrap;gap:6px}</style></head><body><main><header><h1>${escape(plan.appName)}</h1><p>${escape(plan.description.take(240))}</p></header><!--app-content-->$content<!--/app-content--></main></body></html>"""
    }
    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
