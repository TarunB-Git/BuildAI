package com.buildai.app.builder

import org.json.JSONObject

/** Constrain the planning protocol's types, not the person's idea or features. */
object PlanningGrammar {
    fun forBrief(brief: ProjectBrief): String {
        fun choices(values: List<String>) = values.distinct().joinToString(" | ") { JSONObject.quote(JSONObject.quote(it)) }
        val questions = (brief.questions.map { it.id } + brief.answers.map { it.questionId }).distinct()
        val answerRules = if (questions.isEmpty()) "answers ::= \"[\" ws \"]\"" else """
            answers ::= "[" ws (answer (ws "," ws answer){0,2})? ws "]"
            answer ::= "{" ws "\"questionId\":" ws known-question ws "," ws "\"messageId\":" ws current-message ws "," ws "\"answer\":" ws string ws "}"
            known-question ::= ${choices(questions)}
            current-message ::= ${choices(listOf(checkNotNull(brief.turns.lastOrNull()).messageId))}
        """.trimIndent()
        return """
            root ::= "{" ws "\"reply\":" ws string ws "," ws "\"answers\":" ws answers ws "," ws "\"questions\":" ws questions ws "," ws "\"assumptions\":" ws strings ws "," ws "\"suggestions\":" ws suggestions (ws "," ws "\"plan\":" ws plan)? (ws "," ws "\"offlineStarter\":" ws starter)? (ws "," ws "\"unsupported\":" ws string)? ws "}" ws
            questions ::= "[" ws (question (ws "," ws question)?)? ws "]"
            question ::= "{" ws "\"id\":" ws id ws "," ws "\"question\":" ws string ws "," ws "\"essential\":" ws boolean ws "}"
            suggestions ::= "[" ws (suggestion (ws "," ws suggestion){0,2})? ws "]"
            suggestion ::= "{" ws "\"id\":" ws id ws "," ws "\"title\":" ws string ws "," ws "\"benefit\":" ws string ws "}"
            plan ::= "{" ws "\"name\":" ws string ws "," ws "\"features\":" ws "[" ws feature (ws "," ws feature){0,11} ws "]" ws "," ws "\"screens\":" ws strings ws "," ws "\"data\":" ws strings ws "}"
            feature ::= "{" ws "\"id\":" ws id ws "," ws "\"title\":" ws string ws "," ws "\"workflow\":" ws string ws "," ws "\"checks\":" ws "[" ws string (ws "," ws string){0,7} ws "]" ws "}"
            strings ::= "[" ws (string (ws "," ws string){0,11})? ws "]"
            starter ::= "\"NOTES\"" | "\"TASKS\"" | "\"HABITS\"" | "\"EXPENSES\"" | "\"CALCULATOR\"" | "\"TIMER\""
            id ::= "\"" [a-zA-Z] [a-zA-Z0-9_-]{0,63} "\""
            boolean ::= "true" | "false"
            string ::= "\"" char+ "\""
            char ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
            ws ::= [ \t\r\n]*
        """.trimIndent() + "\n" + answerRules
    }
}
