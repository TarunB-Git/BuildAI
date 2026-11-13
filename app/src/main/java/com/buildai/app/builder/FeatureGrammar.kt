package com.buildai.app.builder

import com.buildai.app.runtime.*
import org.json.JSONObject

/** A capability schema, not an app template: the model still chooses controls, data and behavior. */
object FeatureGrammar {
    private fun implementationBase(app: AppSpec): String = forApp(app)
        .replace(Regex("(?m)^feature ::=.*$")) { """
            feature ::= "{" ws "\"screenId\":" ws screen-id ws "," ws "\"components\":" ws nonempty-components ws "," ws "\"screens\":" ws screens ws "," ws "\"entities\":" ws entities ws "," ws "\"actions\":" ws actions ws "}" ws
        """.trimIndent() } + "\nnonempty-components ::= \"[\" ws component (ws \",\" ws component){0,7} ws \"]\""

    fun forImplementation(app: AppSpec, selected: CompositionSelection): String {
        selected.validate()
        fun terminals(names: List<String>) = names.joinToString(" | ") { JSONObject.quote(JSONObject.quote(it)) }
        var grammar = implementationBase(app)
        val names = selected.components.map { it.name.lowercase().replace('_', '-') }
        grammar = grammar.replace(Regex("(?m)^component ::=.*$")) { "component ::= ${names.joinToString(" | ")}" }
        val requiredGroups = selected.components.joinToString(" ws \",\" ws ") { kind ->
            val rule = kind.name.lowercase().replace('_', '-')
            val maximum = when (kind) { ComponentKind.INPUT -> 8; ComponentKind.TEXT, ComponentKind.SUMMARY, ComponentKind.BUTTON -> 4; else -> 2 }
            "$rule (ws \",\" ws $rule){0,${maximum - 1}}"
        }
        grammar = grammar.replace(Regex("(?m)^nonempty-components ::=.*$")) { "nonempty-components ::= \"[\" ws $requiredGroups ws \"]\"" }
        if (!selected.screens) grammar = grammar.replace(Regex("(?m)^screens ::=.*$")) { "screens ::= \"[\" ws \"]\"" }
        if (!selected.records) grammar = grammar.replace(Regex("(?m)^entities ::=.*$")) { "entities ::= \"[\" ws \"]\"" }
        if (selected.actions.isEmpty()) grammar = grammar.replace(Regex("(?m)^actions ::=.*$")) { "actions ::= \"[\" ws \"]\"" }
        else grammar = grammar.replace(Regex("(?m)^action-kind ::=.*$")) { "action-kind ::= ${terminals(selected.actions.map { it.name })}" }
        val leaves = mutableSetOf(ExpressionOp.TEXT, ExpressionOp.NUMBER, ExpressionOp.BOOLEAN)
        if (ComponentKind.INPUT in selected.components || app.screens.any { s -> s.components.any { it.kind == com.buildai.app.runtime.ComponentKind.INPUT } }) leaves += ExpressionOp.INPUT
        if (selected.actions.any { it == com.buildai.app.runtime.ActionKind.UPDATE || it == com.buildai.app.runtime.ActionKind.DELETE }) leaves += ExpressionOp.RECORD
        leaves += selected.expressions.filter { it in setOf(ExpressionOp.COUNT, ExpressionOp.SUM, ExpressionOp.NOW, ExpressionOp.TODAY, ExpressionOp.RECORD) }
        grammar = grammar.replace(Regex("(?m)^leaf-op ::=.*$")) { "leaf-op ::= ${terminals(leaves.map { it.name })}" }
        val binary = selected.expressions.filter { it !in leaves && it !in setOf(ExpressionOp.INPUT, ExpressionOp.RECORD, ExpressionOp.IF) }
        if (binary.isNotEmpty()) grammar = grammar.replace(Regex("(?m)^binary-op ::=.*$")) { "binary-op ::= ${terminals(binary.map { it.name })}" }
        for (depth in 1..3) {
            val child = "expression-${depth - 1}"
            val calculation = if (binary.isEmpty()) "" else " | \"{\" ws \"\\\"op\\\":\" ws binary-op ws \",\" ws \"\\\"args\\\":\" ws \"[\" ws $child ws \",\" ws $child ws \"]\" ws \"}\""
            val conditional = if (ExpressionOp.IF !in selected.expressions) "" else " | \"{\" ws \"\\\"op\\\":\" ws \"\\\"IF\\\"\" ws \",\" ws \"\\\"args\\\":\" ws \"[\" ws $child ws \",\" ws $child ws \",\" ws $child ws \"]\" ws \"}\""
            grammar = grammar.replace(Regex("(?m)^expression-$depth ::=.*$")) { "expression-$depth ::= literal-expression$calculation$conditional" }
        }
        if ((binary.isNotEmpty() || ExpressionOp.IF in selected.expressions) && ComponentKind.SUMMARY in selected.components) {
            val alternatives = mutableListOf<String>()
            if (binary.isNotEmpty()) alternatives += """"{" ws "\"op\":" ws binary-op ws "," ws "\"args\":" ws "[" ws expression-2 ws "," ws expression-2 ws "]" ws "}""""
            if (ExpressionOp.IF in selected.expressions) alternatives += """"{" ws "\"op\":" ws "\"IF\"" ws "," ws "\"args\":" ws "[" ws expression-2 ws "," ws expression-2 ws "," ws expression-2 ws "]" ws "}""""
            grammar += "\ncalculated-expression ::= ${alternatives.joinToString(" | ")}"
            grammar = grammar.replace(Regex("(?m)^summary ::=.*$")) { it.value.replace("ws expression ws", "ws calculated-expression ws") }
        }
        return grammar
    }
    /** Verification arguments refer to actual contracts, not arbitrary placeholder identifiers. */
    fun forProbes(app: AppSpec, feature: FeaturePlan, inputIds: List<String>): String {
        fun terminals(ids: List<String>) = ids.distinct().joinToString(" | ") { JSONObject.quote(JSONObject.quote(it)) }
        val controls = app.screens.flatMap { it.components }
        val inputPairs = inputIds.joinToString(" ws \",\" ws ") { "${JSONObject.quote(JSONObject.quote(it) + ":")} ws string" }
        var grammar = forFeature(app, feature).replace("root ::= feature | unsupported", "root ::= probe-result") +
            "\nprobe-result ::= \"{\" ws \"\\\"checks\\\":\" ws checks ws \"}\" ws"
        grammar = grammar.replace(Regex("(?m)^inputs ::=.*$")) { "inputs ::= \"{\" ws $inputPairs ws \"}\"" }
        if (app.actions.isEmpty()) grammar = grammar.replace(Regex("(?m)^steps ::=.*$")) { "steps ::= \"[\" ws \"]\"" }
        else grammar = grammar.replace("\"\\\"action\\\":\" ws string", "\"\\\"action\\\":\" ws known-action") + "\nknown-action ::= ${terminals(app.actions.map { it.id })}"
        val alternatives = mutableListOf<String>()
        fun constrain(rule: String, key: String, ids: List<String>) {
            if (ids.isEmpty()) return
            alternatives += rule
            grammar = grammar.replace(Regex("(?m)^$rule ::=.*$")) { match -> match.value.replace("\"\\\"$key\\\":\" ws string", "\"\\\"$key\\\":\" ws $rule-id") }
            grammar += "\n$rule-id ::= ${terminals(ids)}"
        }
        constrain("component-equals", "component", controls.filter { it.kind == com.buildai.app.runtime.ComponentKind.SUMMARY || it.kind == com.buildai.app.runtime.ComponentKind.TEXT && it.expression != null }.map { it.id })
        constrain("component-count", "component", controls.filter { it.kind == com.buildai.app.runtime.ComponentKind.RECORD_LIST }.map { it.id })
        constrain("component-timer", "component", controls.filter { it.kind == com.buildai.app.runtime.ComponentKind.TIMER }.map { it.id })
        constrain("component-quiz", "component", controls.filter { it.kind == com.buildai.app.runtime.ComponentKind.QUIZ }.map { it.id })
        constrain("entity-count", "entity", app.entities.map { it.id })
        constrain("entity-field", "entity", app.entities.map { it.id })
        if (app.actions.any { it.kind == com.buildai.app.runtime.ActionKind.NAVIGATE }) constrain("screen-assertion", "screen", app.screens.map { it.id })
        require(alternatives.isNotEmpty()) { "Configure an observable calculation, record, timer, quiz or navigation before generating checks" }
        grammar = grammar.replace(Regex("(?m)^assertion ::=.*$")) { "assertion ::= ${alternatives.joinToString(" | ")}" }
        return grammar
    }
    fun forFeature(app: AppSpec, feature: FeaturePlan): String {
        val probes = feature.checks.indices.joinToString(" ws \",\" ws ") { "approved-check-$it" }
        val exactChecks = "checks ::= \"[\" ws $probes ws \"]\""
        val rules = feature.checks.mapIndexed { index, text ->
            "approved-check-$index ::= \"{\" ws \"\\\"name\\\":\" ws ${JSONObject.quote(JSONObject.quote(text))} ws \",\" ws \"\\\"inputs\\\":\" ws inputs ws \",\" ws \"\\\"steps\\\":\" ws steps ws \",\" ws \"\\\"assertions\\\":\" ws assertions ws \"}\""
        }
        return forApp(app).replace(Regex("(?m)^checks ::=.*$")) { exactChecks } + "\n" + rules.joinToString("\n")
    }
    fun forEdit(app: AppSpec): String = forApp(app).replace("root ::= feature | unsupported", "root ::= edit | unsupported") + "\n" + """
        edit ::= "{" ws "\"summary\":" ws string ws "," ws "\"components\":" ws components ws "," ws "\"removeComponents\":" ws strings ws "," ws "\"actions\":" ws actions ws "," ws "\"newFields\":" ws new-fields ws "," ws "\"entities\":" ws entities ws "," ws "\"checks\":" ws checks ws "}" ws
        new-fields ::= "[" ws (new-field (ws "," ws new-field){0,11})? ws "]"
        new-field ::= "{" ws "\"entity\":" ws string ws "," ws "\"field\":" ws field ws "}"
    """.trimIndent()
    fun forApp(app: AppSpec): String {
        val destinations = app.screens.joinToString(" | ") { "\"\\\"${it.id}\\\"\"" }
        return "screen-id ::= $destinations\n" + """
            root ::= feature | unsupported
            unsupported ::= "{" ws "\"unsupported\":" ws string ws "}" ws
            feature ::= "{" ws "\"screenId\":" ws screen-id ws "," ws "\"components\":" ws components ws "," ws "\"screens\":" ws screens ws "," ws "\"entities\":" ws entities ws "," ws "\"actions\":" ws actions ws "," ws "\"checks\":" ws checks ws "}" ws
            components ::= "[" ws (component (ws "," ws component){0,7})? ws "]"
            component ::= input | button | text | summary | record-list | timer | quiz
            input ::= "{" ws "\"id\":" ws string ws "," ws "\"kind\":\"INPUT\"," ws "\"label\":" ws string ws "," ws "\"fieldKind\":" ws field-kind (ws "," ws "\"referenceEntity\":" ws string)? ws "}"
            button ::= "{" ws "\"id\":" ws string ws "," ws "\"kind\":\"BUTTON\"," ws "\"label\":" ws string ws "," ws "\"actionId\":" ws string ws "}"
            text ::= "{" ws "\"id\":" ws string ws "," ws "\"kind\":\"TEXT\"," ws "\"label\":" ws string (ws "," ws "\"expression\":" ws expression)? ws "}"
            summary ::= "{" ws "\"id\":" ws string ws "," ws "\"kind\":\"SUMMARY\"," ws "\"label\":" ws string ws "," ws "\"expression\":" ws expression ws "}"
            timer ::= "{" ws "\"id\":" ws string ws "," ws "\"kind\":\"TIMER\"," ws "\"label\":" ws string ws "}"
            record-list ::= "{" ws "\"id\":" ws string ws "," ws "\"kind\":\"RECORD_LIST\"," ws "\"label\":" ws string ws "," ws "\"entityId\":" ws string ws "," ws "\"rowActions\":" ws strings (ws "," ws "\"displayFields\":" ws strings)? (ws "," ws "\"expression\":" ws expression)? (ws "," ws "\"sortField\":" ws string ws "," ws "\"sortDescending\":" ws boolean)? (ws "," ws "\"filter\":" ws expression)? ws "}"
            quiz ::= "{" ws "\"id\":" ws string ws "," ws "\"kind\":\"QUIZ\"," ws "\"label\":" ws string ws "," ws "\"questions\":" ws "[" ws question (ws "," ws question){0,7} ws "]" ws "}"
            question ::= "{" ws "\"id\":" ws string ws "," ws "\"question\":" ws string ws "," ws "\"options\":" ws strings ws "," ws "\"answer\":" ws integer ws "}"
            screens ::= "[" ws (screen (ws "," ws screen){0,1})? ws "]"
            screen ::= "{" ws "\"id\":" ws string ws "," ws "\"title\":" ws string ws "," ws "\"components\":" ws components ws "}"
            entities ::= "[" ws (entity (ws "," ws entity){0,2})? ws "]"
            entity ::= "{" ws "\"id\":" ws string ws "," ws "\"fields\":" ws "[" ws field (ws "," ws field){0,11} ws "]" ws "}"
            field ::= "{" ws "\"id\":" ws string ws "," ws "\"label\":" ws string ws "," ws "\"kind\":" ws field-kind ws "," ws "\"required\":" ws boolean (ws "," ws "\"referenceEntity\":" ws string)? ws "}"
            field-kind ::= "\"TEXT\"" | "\"NUMBER\"" | "\"BOOLEAN\"" | "\"DATE\"" | "\"REFERENCE\""
            actions ::= "[" ws (action (ws "," ws action){0,7})? ws "]"
            action ::= "{" ws "\"id\":" ws string ws "," ws "\"kind\":" ws action-kind ws "," ws "\"label\":" ws string ws "," ws "\"target\":" ws string ws "," ws "\"values\":" ws values (ws "," ws "\"recordId\":" ws expression)? (ws "," ws "\"duration\":" ws expression)? (ws "," ws "\"condition\":" ws expression)? ws "}"
            action-kind ::= "\"INSERT\"" | "\"UPDATE\"" | "\"DELETE\"" | "\"NAVIGATE\"" | "\"ASSIGN\"" | "\"START_TIMER\"" | "\"STOP_TIMER\""
            values ::= "{" ws (string ws ":" ws expression (ws "," ws string ws ":" ws expression){0,11})? ws "}"
            expression ::= expression-3
            literal-expression ::= "{" ws "\"op\":" ws leaf-op ws "," ws "\"value\":" ws string ws "}"
            leaf-op ::= "\"TEXT\"" | "\"NUMBER\"" | "\"BOOLEAN\"" | "\"INPUT\"" | "\"RECORD\"" | "\"COUNT\"" | "\"SUM\"" | "\"NOW\"" | "\"TODAY\""
            binary-op ::= "\"ADD\"" | "\"SUBTRACT\"" | "\"MULTIPLY\"" | "\"DIVIDE\"" | "\"EQ\"" | "\"GT\"" | "\"CONTAINS\"" | "\"AND\"" | "\"CONCAT\"" | "\"DATE_ADD\"" | "\"DATE_DIFF\"" | "\"BEFORE\"" | "\"AFTER\""
            checks ::= "[" ws check (ws "," ws check){0,7} ws "]"
            check ::= "{" ws "\"name\":" ws string ws "," ws "\"inputs\":" ws inputs ws "," ws "\"steps\":" ws steps ws "," ws "\"assertions\":" ws assertions ws "}"
            inputs ::= "{" ws (string ws ":" ws string (ws "," ws string ws ":" ws string){0,11})? ws "}"
            steps ::= "[" ws (step (ws "," ws step){0,7})? ws "]"
            step ::= "{" ws "\"action\":" ws string (ws "," ws "\"inputs\":" ws inputs)? (ws "," ws "\"record\":" ws selector)? (ws "," ws "\"inputRecords\":" ws selectors)? (ws "," ws "\"expect\":\"conditionRejected\"")? ws "}"
            selector ::= "{" ws "\"entity\":" ws string ws "," ws "\"index\":" ws integer ws "}"
            selectors ::= "{" ws string ws ":" ws selector (ws "," ws string ws ":" ws selector){0,11} ws "}"
            assertions ::= "[" ws assertion (ws "," ws assertion){0,7} ws "]"
            assertion ::= component-equals | component-count | component-timer | component-quiz | entity-count | entity-field | screen-assertion
            component-equals ::= "{" ws "\"component\":" ws string ws "," ws "\"equals\":" ws string ws "}"
            component-count ::= "{" ws "\"component\":" ws string ws "," ws "\"count\":" ws integer ws "}"
            component-timer ::= "{" ws "\"component\":" ws string ws "}"
            component-quiz ::= "{" ws "\"component\":" ws string ws "," ws "\"question\":" ws string ws "," ws "\"equals\":" ws string ws "}"
            entity-count ::= "{" ws "\"entity\":" ws string ws "," ws "\"count\":" ws integer ws "}"
            entity-field ::= "{" ws "\"entity\":" ws string ws "," ws "\"field\":" ws string ws "," ws "\"index\":" ws integer ws "," ws "\"equals\":" ws string ws "}"
            screen-assertion ::= "{" ws "\"screen\":" ws string ws "}"
            strings ::= "[" ws (string (ws "," ws string){0,11})? ws "]"
            boolean ::= "true" | "false"
            integer ::= [0-9]{1,6}
            string ::= "\"" char{0,256} "\""
            char ::= [^"\\\x7F\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
            ws ::= [ \t\n\r]?
        """.trimIndent() + "\nexpression-0 ::= literal-expression\n" + (1..3).joinToString("\n") { depth ->
            val child = "expression-${depth - 1}"
            "expression-$depth ::= literal-expression | \"{\" ws \"\\\"op\\\":\" ws binary-op ws \",\" ws \"\\\"args\\\":\" ws \"[\" ws $child ws \",\" ws $child ws \"]\" ws \"}\" | \"{\" ws \"\\\"op\\\":\" ws \"\\\"IF\\\"\" ws \",\" ws \"\\\"args\\\":\" ws \"[\" ws $child ws \",\" ws $child ws \",\" ws $child ws \"]\" ws \"}\""
        }
    }
}
