package com.buildai.app.builder

import com.buildai.app.runtime.*
import org.json.JSONArray
import org.json.JSONObject

/** A short tool choice list; public plans continue to use CompositionSelection's versioned schema. */
object CapabilityDecisions {
    private val operations = ExpressionOp.entries.filterNot { it in setOf(ExpressionOp.INPUT, ExpressionOp.NUMBER,
        ExpressionOp.TEXT, ExpressionOp.BOOLEAN, ExpressionOp.RECORD) }.toSet()
    private val names = ComponentKind.entries.map { it.name } + operations.map { it.name } + ActionKind.entries.map { it.name } + listOf("NEW_DATA", "NEW_SCREEN")
    fun read(j: JSONObject): CompositionSelection {
        require(j.keys().asSequence().toSet() == setOf("use")) { "Return only the use list of capability names" }
        val array = j.getJSONArray("use")
        require(array.length() in 1..32)
        val choices = (0 until array.length()).map { array.get(it).also { value -> require(value is String) } as String }
        require(choices.distinct().size == choices.size && choices.all { it in names }) { "Use each registered capability at most once" }
        return CompositionSelection(ComponentKind.entries.filter { it.name in choices }.toSet(), operations.filter { it.name in choices }.toSet(),
            ActionKind.entries.filter { it.name in choices }.toSet(), "NEW_DATA" in choices, "NEW_SCREEN" in choices).withDependencies().also { it.validate() }
    }
    fun json(selection: CompositionSelection) = JSONObject().put("use", JSONArray(selection.components.map { it.name } +
        selection.expressions.filter { it in operations }.map { it.name } + selection.actions.map { it.name } +
        listOfNotNull("NEW_DATA".takeIf { selection.records }, "NEW_SCREEN".takeIf { selection.screens })))
    val grammar: String get() = """
        root ::= "{" ws "\"use\":" ws "[" ws name (ws "," ws name){0,31} ws "]" ws "}" ws
        name ::= ${names.joinToString(" | ") { JSONObject.quote(JSONObject.quote(it)) }}
        ws ::= [ \t\r\n]?
    """.trimIndent()
    val prompt = """
        Select the smallest set of native capabilities needed for currentTask and its checks.
        Return one JSON object whose sole property use is an array of the required registered names. No IDs, invented names or explanation.
        Controls: INPUT typed entry; SUMMARY live calculated result; TEXT standalone text; BUTTON action;
        RECORD_LIST saved entries/search; TIMER deadline; QUIZ checked questions.
        Operations: MULTIPLY, DIVIDE, ADD, SUBTRACT for arithmetic; CONCAT text; EQ,GT,CONTAINS,AND,IF conditions;
        TODAY,NOW,DATE_ADD,DATE_DIFF,BEFORE,AFTER dates. COUNT,SUM only aggregate stored collections.
        Actions: INSERT save a record; UPDATE,DELETE records; NAVIGATE another screen; ASSIGN replace an input;
        START_TIMER,STOP_TIMER only control timers. Live results update automatically without an action.
        NEW_DATA only when a new saved collection is requested; NEW_SCREEN only for a new screen.
        An existing screen is available. Obey exclusions in currentTask. Never add unrequested saved data or actions.
    """.trimIndent()
}
