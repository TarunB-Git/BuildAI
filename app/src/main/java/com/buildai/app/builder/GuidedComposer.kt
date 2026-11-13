package com.buildai.app.builder

import com.buildai.app.runtime.*
import org.json.JSONArray
import org.json.JSONObject

/** Explicit user configuration, separate from AI success. Primitives can be combined freely. */
data class GuidedConfiguration(val name: String = "My personal tool", val fields: List<FieldSpec> = emptyList(),
    val saveRecords: Boolean = false, val search: Boolean = false, val calculation: String = "",
    val left: String = "", val right: String = "", val resultLabel: String = "Result") {
    fun json() = JSONObject().put("name", name).put("fields", JSONArray(fields.map { it.json() })).put("saveRecords", saveRecords)
        .put("search", search).put("calculation", calculation).put("left", left).put("right", right).put("resultLabel", resultLabel)
    fun compose(): AppSpec {
        require(fields.size in 1..12 && (saveRecords || calculation.isNotBlank())) { "Add fields and choose saved records or a calculation" }
        val controls = fields.map { ComponentSpec(it.id, ComponentKind.INPUT, it.label, it.kind) }.toMutableList()
        val actions = mutableListOf<ActionSpec>(); val entities = mutableListOf<EntitySpec>()
        if (calculation.isNotBlank()) {
            require(fields.any { it.id == left && it.kind == FieldKind.NUMBER } && fields.any { it.id == right && it.kind == FieldKind.NUMBER }) { "Choose two numeric inputs for the calculation" }
            val operation = ExpressionOp.valueOf(calculation)
            require(operation in setOf(ExpressionOp.ADD, ExpressionOp.SUBTRACT, ExpressionOp.MULTIPLY, ExpressionOp.DIVIDE))
            controls += ComponentSpec("guidedResult", ComponentKind.SUMMARY, resultLabel, expression = Expr(operation, args = listOf(Expr.input(left), Expr.input(right))))
        }
        if (saveRecords) {
            entities += EntitySpec("guidedRecords", fields)
            actions += ActionSpec("guidedSave", ActionKind.INSERT, "Save record", "guidedRecords", fields.associate { it.id to Expr.input(it.id) })
            actions += ActionSpec("guidedDelete", ActionKind.DELETE, "Delete record", "guidedRecords", recordId = Expr(ExpressionOp.RECORD, "id"))
            controls += ComponentSpec("guidedSaveButton", ComponentKind.BUTTON, "Save record", actionId = "guidedSave")
            if (search) controls += ComponentSpec("guidedSearch", ComponentKind.INPUT, "Search records")
            controls += ComponentSpec("guidedList", ComponentKind.RECORD_LIST, "Saved records", entityId = "guidedRecords",
                expression = if (search) Expr.input("guidedSearch") else null, rowActions = listOf("guidedDelete"))
        }
        return AppSpec(name, "home", listOf(ScreenSpec("home", name, controls)), entities, actions).also(AppSpecValidator::validate)
    }
    companion object {
        fun read(j: JSONObject) = GuidedConfiguration(j.optString("name", "My personal tool"), j.objects("fields").map {
            FieldSpec(it.getString("id"), it.getString("label"), FieldKind.valueOf(it.getString("kind")), it.optBoolean("required"))
        }, j.optBoolean("saveRecords"), j.optBoolean("search"), j.optString("calculation"), j.optString("left"), j.optString("right"), j.optString("resultLabel", "Result"))
    }
}
