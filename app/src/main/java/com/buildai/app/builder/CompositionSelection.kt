package com.buildai.app.builder

import com.buildai.app.runtime.*
import org.json.JSONArray
import org.json.JSONObject

/** A bounded capability selection, never an idea-specific app template or a permission grant. */
data class CompositionSelection(
    val components: Set<ComponentKind>, val expressions: Set<ExpressionOp> = emptySet(),
    val actions: Set<ActionKind> = emptySet(), val records: Boolean = false, val screens: Boolean = false
) {
    /** Visible triggers are dependencies of an action, not a new user feature. */
    fun withDependencies(): CompositionSelection {
        var visible = components
        if (actions.isNotEmpty() && ComponentKind.BUTTON !in visible && ComponentKind.RECORD_LIST !in visible) visible = visible + ComponentKind.BUTTON
        if (actions.any { it == ActionKind.START_TIMER || it == ActionKind.STOP_TIMER }) visible = visible + ComponentKind.TIMER
        return copy(components = visible)
    }
    fun validate() {
        require(components.isNotEmpty() && components.size <= 7) { "Select at least one supported visible capability" }
        require(expressions.size <= 12 && actions.size <= 7) { "Keep a feature's capabilities focused" }
        require(actions.isEmpty() || ComponentKind.BUTTON in components || ComponentKind.RECORD_LIST in components) { "Actions need a visible button or record row action" }
    }
    fun json() = JSONObject().put("components", JSONArray(components.map { it.name })).put("expressions", JSONArray(expressions.map { it.name }))
        .put("actions", JSONArray(actions.map { it.name })).put("records", records).put("screens", screens)
    fun configurationPrompt(): String = buildString {
        appendLine("Configure ONLY the selected capabilities for currentTask. Return ONLY JSON with screenId, components, screens, entities, actions, in that order.")
        appendLine("You MUST implement each selected component kind: ${components.joinToString { it.name }}. Do not stop after adding inputs; implement the requested output and behavior too.")
        appendLine("components adds real controls to the existing screenId. Choose real unique IDs and labels. Never duplicate an existing screen or earlier IDs.")
        if (!screens) appendLine("screens MUST be []. The existing screen already holds your components.")
        if (!records) appendLine("entities MUST be []. Do not invent stored collections.")
        if (actions.isEmpty()) appendLine("actions MUST be []. Live calculated outputs have no button action.")
        if (ComponentKind.INPUT in components) appendLine("INPUT has id, kind INPUT, label, fieldKind. Use NUMBER for decimal quantities, TEXT for words, BOOLEAN for a checkbox, DATE for a calendar date, REFERENCE for choosing a saved record. REFERENCE also names referenceEntity.")
        if (ComponentKind.SUMMARY in components) appendLine("SUMMARY has id, kind SUMMARY, label, expression. It must read live inputs, not example constants.")
        if (ComponentKind.TEXT in components) appendLine("TEXT has id, kind TEXT, label, optional expression for calculated text.")
        if (ComponentKind.BUTTON in components) appendLine("BUTTON has id, kind BUTTON, label, actionId pointing to a declared action.")
        if (ComponentKind.RECORD_LIST in components) appendLine("RECORD_LIST has id,kind RECORD_LIST,label,entityId,rowActions []; optional displayFields and expression reading a search input, sortField,sortDescending,filter.")
        if (ComponentKind.TIMER in components) appendLine("TIMER has id,kind TIMER,label. A START_TIMER action supplies the saved deadline.")
        if (ComponentKind.QUIZ in components) appendLine("QUIZ has id,kind QUIZ,label,questions with id,question,options,answer (index).")
        appendLine("Expressions read an input as {\"op\":\"INPUT\",\"value\":\"the actual input ID\"}; constants use NUMBER,TEXT,BOOLEAN with value strings.")
        if (expressions.isNotEmpty()) appendLine("Selected operations: ${expressions.joinToString { it.name }}. Binary calculation syntax: {\"op\":\"selected operation\",\"args\":[EXPRESSION,EXPRESSION]}. Use shallow args and real input IDs.")
        if (ExpressionOp.COUNT in expressions || ExpressionOp.SUM in expressions) appendLine("COUNT value is entity ID; SUM value is entity.field for a stored numeric field. These do not evaluate a formula string.")
        if (ExpressionOp.IF in expressions) appendLine("IF args are condition,true-result,false-result.")
        if (records) appendLine("Entities have id,fields with id,label,kind,required. Declare only data requested in the workflow.")
        if (actions.isNotEmpty()) {
            appendLine("Selected actions: ${actions.joinToString { it.name }}. Each has id,kind,label,target,values mapping field IDs to expressions.")
            if (ActionKind.INSERT in actions) appendLine("INSERT target is entity ID; values maps every required field to its input expression.")
            if (ActionKind.UPDATE in actions || ActionKind.DELETE in actions) appendLine("UPDATE/DELETE target an entity and use recordId {\"op\":\"RECORD\",\"value\":\"id\"}.")
            if (ActionKind.NAVIGATE in actions) appendLine("NAVIGATE targets a screen ID; do not replace existing screens.")
            if (ActionKind.ASSIGN in actions) appendLine("ASSIGN values maps input IDs to expressions.")
            if (ActionKind.START_TIMER in actions || ActionKind.STOP_TIMER in actions) appendLine("Timer actions target a TIMER control ID. START_TIMER duration is an expression in seconds.")
        }
        appendLine("Checks will be configured separately against your actual IDs. Unsupported work returns {\"unsupported\":\"reason\"}.")
    }
    fun validateImplementation(candidate: AppSpec, fragment: JSONObject) {
        val ids = (fragment.objects("components") + fragment.objects("screens").flatMap { it.objects("components") }).map { it.getString("id") }.toSet()
        val added = candidate.screens.flatMap { it.components }.filter { it.id in ids }
        require(added.map { it.kind }.toSet().containsAll(components)) { "Implement the selected ${components.filterNot { kind -> added.any { it.kind == kind } }.joinToString { it.name }} controls, including their requested behavior" }
        require(added.all { it.kind in components }) { "An unselected component was added" }
        val newActionIds = fragment.objects("actions").map { it.getString("id") }.toSet()
        val newActions = candidate.actions.filter { it.id in newActionIds }
        require(newActions.map { it.kind }.toSet().containsAll(actions) && newActions.all { it.kind in actions }) { "Implement only the selected action kinds" }
        newActions.forEach { action ->
            require(candidate.screens.flatMap { it.components }.any { it.actionId == action.id || action.id in it.rowActions }) {
                "${action.label.ifBlank { action.id }} has no visible control. Connect it to a button or record-row action."
            }
        }
        require(fragment.objects("entities").isNotEmpty() == records) { "New saved collections must match the approved capability selection" }
        require(fragment.objects("screens").isNotEmpty() == screens) { "New screens must match the approved capability selection" }
        val used = mutableSetOf<ExpressionOp>()
        fun visit(expr: Expr) { used += expr.op; expr.args.forEach(::visit) }
        added.forEach { it.expression?.let(::visit); it.filter?.let(::visit) }
        newActions.forEach { it.values.values.forEach(::visit); it.duration?.let(::visit); it.recordId?.let(::visit); it.condition?.let(::visit) }
        val requiredOperations = expressions - setOf(ExpressionOp.TEXT, ExpressionOp.NUMBER, ExpressionOp.BOOLEAN, ExpressionOp.RECORD, ExpressionOp.NOW, ExpressionOp.TODAY)
        require(used.containsAll(requiredOperations)) { "Use the selected ${requiredOperations.filterNot { it in used }.joinToString { it.name }} operations in the added behavior" }
        // Constants can satisfy an operation checklist without implementing a live result.
        // Follow dependencies from observable outputs/actions, rather than accepting inputs
        // that merely appear next to a precomputed example answer.
        fun reads(e: Expr): Set<String> = (if (e.op == ExpressionOp.INPUT) setOf(e.value) else emptySet()) + e.args.flatMap(::reads)
        val consumed = added.flatMap { listOfNotNull(it.expression, it.filter) }.flatMap(::reads).toSet() +
            newActions.flatMap { it.values.values + listOfNotNull(it.duration, it.recordId, it.condition) }.flatMap(::reads)
        added.filter { it.kind == ComponentKind.INPUT }.forEach { input ->
            require(input.id in consumed) { "${input.label.ifBlank { input.id }} is not connected to the requested result or action. Read its live value instead of an example constant." }
        }
    }
    companion object {
        fun read(j: JSONObject): CompositionSelection {
            require(j.keys().asSequence().all { it in setOf("components", "expressions", "actions", "records", "screens") })
            return CompositionSelection(j.strings("components").map { ComponentKind.valueOf(it) }.toSet(),
                j.strings("expressions").map { ExpressionOp.valueOf(it) }.toSet(), j.strings("actions").map { ActionKind.valueOf(it) }.toSet(),
                j.getBoolean("records"), j.getBoolean("screens")).withDependencies().also { it.validate() }
        }
        val grammar: String get() {
            fun choices(values: List<String>) = values.joinToString(" | ") { JSONObject.quote(JSONObject.quote(it)) }
            return """
                root ::= "{" ws "\"components\":" ws "[" ws component (ws "," ws component){0,6} ws "]" ws "," ws "\"expressions\":" ws "[" ws (expression (ws "," ws expression){0,11})? ws "]" ws "," ws "\"actions\":" ws "[" ws (action (ws "," ws action){0,6})? ws "]" ws "," ws "\"records\":" ws boolean ws "," ws "\"screens\":" ws boolean ws "}" ws
                component ::= ${choices(ComponentKind.entries.map { it.name })}
                expression ::= ${choices(ExpressionOp.entries.map { it.name })}
                action ::= ${choices(ActionKind.entries.map { it.name })}
                boolean ::= "true" | "false"
                ws ::= [ \t\r\n]?
            """.trimIndent()
        }
        val prompt = """
            Select capabilities for currentTask. You are selecting kinds, NOT configuring controls or assigning IDs.
            Return exactly one JSON object with this shape:
            {"components":[],"expressions":[],"actions":[],"records":false,"screens":false}
            The first three fields are arrays of enum-name STRINGS. records and screens are BOOLEANS.
            Never return maps, control IDs, field names or screen names. Select the smallest necessary set;
            a kind appearing in this documentation is not a reason to select it. Empty expressions/actions are allowed.
            components must contain at least one of: INPUT for typed entry, SUMMARY for live calculations, TEXT for static text, BUTTON for an action,
            RECORD_LIST for saved records/search, TIMER for a deadline, QUIZ for questions with checked answers.
            expressions: INPUT reads live input; NUMBER/TEXT/BOOLEAN are constants. ADD/SUBTRACT/MULTIPLY/DIVIDE are arithmetic.
            COUNT and SUM aggregate SAVED collections only; they are never operations for an unsaved input calculation.
            Other supported operations: NOW,TODAY,CONCAT,EQ,GT,CONTAINS,AND,IF,DATE_ADD,DATE_DIFF,BEFORE,AFTER,RECORD.
            actions: INSERT/UPDATE/DELETE for saved records, NAVIGATE for another screen, ASSIGN for input assignment,
            START_TIMER/STOP_TIMER for a timer. Live calculations do not need actions.
            records means NEW saved collections are needed. screens means NEW screens are needed; an existing screen is already available.
            Do not select records or timer actions for unsaved calculations. Do not add unrelated capabilities.
            The following tool configures your selected capabilities. Return only the selection JSON, without explanations.
        """.trimIndent()
    }
}
