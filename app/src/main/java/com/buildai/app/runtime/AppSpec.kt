package com.buildai.app.runtime

import org.json.JSONArray
import org.json.JSONObject

enum class FieldKind { TEXT, NUMBER, BOOLEAN, DATE, REFERENCE }
enum class ComponentKind { TEXT, INPUT, BUTTON, RECORD_LIST, SUMMARY, TIMER, QUIZ }
enum class ActionKind { INSERT, UPDATE, DELETE, NAVIGATE, ASSIGN, START_TIMER, STOP_TIMER }
enum class ExpressionOp { TEXT, NUMBER, BOOLEAN, INPUT, RECORD, COUNT, SUM, NOW, TODAY, DATE_ADD, DATE_DIFF, BEFORE, AFTER, ADD, SUBTRACT, MULTIPLY, DIVIDE, CONCAT, EQ, GT, CONTAINS, AND, IF }

data class Expr(val op: ExpressionOp, val value: String = "", val args: List<Expr> = emptyList()) {
    fun json(): JSONObject = JSONObject().put("op", op.name).put("value", value).put("args", JSONArray(args.map { it.json() }))
    companion object {
        fun text(value: String) = Expr(ExpressionOp.TEXT, value)
        fun input(id: String) = Expr(ExpressionOp.INPUT, id)
        fun number(value: String) = Expr(ExpressionOp.NUMBER, value)
        fun read(j: JSONObject, depth: Int = 0): Expr {
            require(depth <= 8) { "Calculation is too deeply nested" }
            keys(j, "op", "value", "args")
            return Expr(ExpressionOp.valueOf(j.getString("op")), j.optString("value"),
                objects(j, "args", 16).map { read(it, depth + 1) })
        }
    }
}
data class FieldSpec(val id: String, val label: String, val kind: FieldKind = FieldKind.TEXT, val required: Boolean = false, val referenceEntity: String = "") {
    fun json() = JSONObject().put("id", id).put("label", label).put("kind", kind.name).put("required", required).put("referenceEntity", referenceEntity)
}
data class EntitySpec(val id: String, val fields: List<FieldSpec>) {
    fun json() = JSONObject().put("id", id).put("fields", JSONArray(fields.map { it.json() }))
}
data class QuizQuestion(val id: String, val question: String, val options: List<String>, val answer: Int) {
    fun json() = JSONObject().put("id", id).put("question", question).put("options", JSONArray(options)).put("answer", answer)
}
data class ComponentSpec(
    val id: String, val kind: ComponentKind, val label: String = "",
    val fieldKind: FieldKind = FieldKind.TEXT, val actionId: String = "", val entityId: String = "",
    val expression: Expr? = null, val rowActions: List<String> = emptyList(), val questions: List<QuizQuestion> = emptyList(), val displayFields: List<String> = emptyList(),
    val sortField: String = "", val sortDescending: Boolean = false, val filter: Expr? = null, val referenceEntity: String = ""
) {
    fun json() = JSONObject().put("id", id).put("kind", kind.name).put("label", label).put("fieldKind", fieldKind.name)
        .put("actionId", actionId).put("entityId", entityId).put("rowActions", JSONArray(rowActions))
        .put("displayFields", JSONArray(displayFields)).put("questions", JSONArray(questions.map { it.json() })).put("sortField", sortField).put("sortDescending", sortDescending).put("referenceEntity", referenceEntity)
        .also { expression?.let { e -> it.put("expression", e.json()) }; filter?.let { e -> it.put("filter", e.json()) } }
}
data class ScreenSpec(val id: String, val title: String, val components: List<ComponentSpec>) {
    fun json() = JSONObject().put("id", id).put("title", title).put("components", JSONArray(components.map { it.json() }))
}
data class ActionSpec(
    val id: String, val kind: ActionKind, val label: String = "", val target: String = "",
    val values: Map<String, Expr> = emptyMap(), val recordId: Expr? = null, val duration: Expr? = null, val condition: Expr? = null
) {
    fun json() = JSONObject().put("id", id).put("kind", kind.name).put("label", label).put("target", target)
        .put("values", JSONObject().also { j -> values.forEach { (k, v) -> j.put(k, v.json()) } })
        .also { j -> recordId?.let { j.put("recordId", it.json()) }; duration?.let { j.put("duration", it.json()) }; condition?.let { j.put("condition", it.json()) } }
}
data class AppSpec(
    val name: String, val startScreen: String, val screens: List<ScreenSpec>,
    val entities: List<EntitySpec> = emptyList(), val actions: List<ActionSpec> = emptyList(), val schemaVersion: Int = 1
) {
    fun json() = JSONObject().put("schemaVersion", schemaVersion).put("name", name).put("startScreen", startScreen)
        .put("screens", JSONArray(screens.map { it.json() })).put("entities", JSONArray(entities.map { it.json() }))
        .put("actions", JSONArray(actions.map { it.json() }))
    companion object {
        fun parse(text: String): AppSpec {
            require(text.length <= 262_144) { "App definition exceeds the supported size" }
            val j = JSONObject(text); keys(j, "schemaVersion", "name", "startScreen", "screens", "entities", "actions")
            require(j.getInt("schemaVersion") == 1) { "This app schema version is not supported" }
            val entities = objects(j, "entities", 20).map { e ->
                keys(e, "id", "fields")
                EntitySpec(e.getString("id"), objects(e, "fields", 40).map { f ->
                    keys(f, "id", "label", "kind", "required", "referenceEntity")
                    FieldSpec(f.getString("id"), f.getString("label"), FieldKind.valueOf(f.getString("kind")), f.optBoolean("required"), f.optString("referenceEntity"))
                })
            }
            val screens = objects(j, "screens", 12).map { s ->
                keys(s, "id", "title", "components")
                ScreenSpec(s.getString("id"), s.getString("title"), objects(s, "components", 80).map { c ->
                    keys(c, "id", "kind", "label", "fieldKind", "actionId", "entityId", "expression", "rowActions", "questions", "displayFields", "sortField", "sortDescending", "filter", "referenceEntity")
                    ComponentSpec(c.getString("id"), ComponentKind.valueOf(c.getString("kind")), c.optString("label"),
                        FieldKind.valueOf(c.optString("fieldKind", "TEXT")), c.optString("actionId"), c.optString("entityId"),
                        c.optJSONObject("expression")?.let { Expr.read(it) }, strings(c, "rowActions", 12),
                        objects(c, "questions", 40).map { q ->
                            keys(q, "id", "question", "options", "answer")
                            QuizQuestion(q.getString("id"), q.getString("question"), strings(q, "options", 8), q.getInt("answer"))
                        }, strings(c, "displayFields", 40), c.optString("sortField"), c.optBoolean("sortDescending"), c.optJSONObject("filter")?.let { Expr.read(it) }, c.optString("referenceEntity"))
                })
            }
            val actions = objects(j, "actions", 160).map { a ->
                keys(a, "id", "kind", "label", "target", "values", "recordId", "duration", "condition")
                val values = a.optJSONObject("values") ?: JSONObject()
                require(values.length() <= 40)
                ActionSpec(a.getString("id"), ActionKind.valueOf(a.getString("kind")), a.optString("label"), a.optString("target"),
                    values.keys().asSequence().associateWith { Expr.read(values.getJSONObject(it)) },
                    a.optJSONObject("recordId")?.let { Expr.read(it) }, a.optJSONObject("duration")?.let { Expr.read(it) }, a.optJSONObject("condition")?.let { Expr.read(it) })
            }
            return AppSpec(j.getString("name"), j.getString("startScreen"), screens, entities, actions).also(AppSpecValidator::validate)
        }
    }
}
internal fun keys(j: JSONObject, vararg allowed: String) {
    require(j.keys().asSequence().all { it in allowed }) { "App definition contains unsupported settings" }
}
internal fun objects(j: JSONObject, name: String, max: Int): List<JSONObject> {
    if (!j.has(name)) return emptyList()
    val a = j.getJSONArray(name)
    require(a.length() <= max) { "Too many $name" }
    return (0 until a.length()).map { a.getJSONObject(it) }
}
internal fun strings(j: JSONObject, name: String, max: Int): List<String> {
    if (!j.has(name)) return emptyList()
    val a = j.getJSONArray(name)
    require(a.length() <= max)
    return (0 until a.length()).map { a.get(it).also { value -> require(value is String) { "$name must contain strings" } } as String }
}
