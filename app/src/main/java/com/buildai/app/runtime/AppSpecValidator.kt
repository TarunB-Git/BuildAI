package com.buildai.app.runtime

import java.math.BigDecimal

/** Capability implementations ship in BuildAI; bundles can configure them, never supply code. */
object CapabilityRegistry {
    data class Capability(val kind: ComponentKind, val behavior: String, val permissions: List<String> = emptyList())
    val components = listOf(
        Capability(ComponentKind.TEXT, "Plain text or a bounded expression"),
        Capability(ComponentKind.INPUT, "Text, decimal number, date or checkbox input"),
        Capability(ComponentKind.BUTTON, "A bound deterministic action"),
        Capability(ComponentKind.RECORD_LIST, "Stored records, search, typed sorting, bounded filters and row actions"),
        Capability(ComponentKind.SUMMARY, "Count, sum or bounded calculation"),
        Capability(ComponentKind.TIMER, "A saved deadline, independent of a loaded AI model"),
        Capability(ComponentKind.QUIZ, "Configured questions and checked answers")
    )
}
object AppSpecValidator {
    private val identifier = Regex("[a-zA-Z][a-zA-Z0-9_-]{0,63}")
    fun validate(spec: AppSpec) {
        require(spec.schemaVersion == 1 && spec.name.isNotBlank() && spec.name.length <= 100)
        require(spec.screens.size in 1..12 && spec.actions.size <= 160 && spec.entities.size <= 20)
        unique(spec.screens.map { it.id }); unique(spec.actions.map { it.id }); unique(spec.entities.map { it.id })
        val screens = spec.screens.map { it.id }.toSet()
        require(spec.startScreen in screens) { "The starting screen is missing" }
        val components = spec.screens.flatMap { it.components }; unique(components.map { it.id })
        require(components.size <= 300) { "Too many app controls" }
        val inputs = components.filter { it.kind == ComponentKind.INPUT }.map { it.id }.toSet()
        val entities = spec.entities.associateBy { it.id }; val actions = spec.actions.associateBy { it.id }
        spec.entities.forEach { e ->
            require(e.fields.size in 1..40); unique(e.fields.map { it.id })
            e.fields.forEach { require(it.id != "id") { "id is reserved for record identity" }; require(it.label.isNotBlank() && it.label.length <= 100)
                if (it.kind == FieldKind.REFERENCE) require(it.referenceEntity in entities) { "A relationship references a missing collection" }
                else require(it.referenceEntity.isBlank())
            }
        }
        var nodes = 0
        fun expression(e: Expr, depth: Int = 0) {
            require(depth <= 8 && ++nodes <= 2048) { "App calculations exceed the supported complexity" }
            require(e.value.length <= 4096)
            val count = when (e.op) {
                ExpressionOp.ADD, ExpressionOp.SUBTRACT, ExpressionOp.MULTIPLY, ExpressionOp.DIVIDE,
                ExpressionOp.EQ, ExpressionOp.GT, ExpressionOp.CONTAINS, ExpressionOp.DATE_ADD, ExpressionOp.DATE_DIFF, ExpressionOp.BEFORE, ExpressionOp.AFTER -> 2..2
                ExpressionOp.IF -> 3..3
                ExpressionOp.AND -> 2..16
                ExpressionOp.CONCAT -> 1..16
                else -> 0..0
            }
            require(e.args.size in count) { "Incorrect calculation arguments" }
            when (e.op) {
                ExpressionOp.INPUT -> require(e.value in inputs) { "Calculation references a missing input" }
                ExpressionOp.RECORD -> require(e.value == "id" || spec.entities.any { entity -> entity.fields.any { it.id == e.value } }) { "Unknown record field" }
                ExpressionOp.COUNT -> require(e.value in entities) { "Unknown collection" }
                ExpressionOp.SUM -> {
                    val parts = e.value.split('.'); require(parts.size == 2)
                    require(entities[parts[0]]?.fields?.any { it.id == parts[1] && it.kind == FieldKind.NUMBER } == true) { "Sum needs a numeric stored field" }
                }
                ExpressionOp.NUMBER -> decimal(e.value)
                ExpressionOp.BOOLEAN -> e.value.toBooleanStrict()
                else -> Unit
            }
            e.args.forEach { expression(it, depth + 1) }
        }
        spec.actions.forEach { a ->
            require(a.label.length <= 100)
            when (a.kind) {
                ActionKind.INSERT, ActionKind.UPDATE, ActionKind.DELETE -> {
                    val entity = checkNotNull(entities[a.target]) { "Action collection is missing" }
                    require(a.values.keys.all { key -> entity.fields.any { it.id == key } }) { "Action writes an unknown field" }
                    if (a.kind == ActionKind.INSERT) require(entity.fields.filter { it.required }.all { it.id in a.values }) { "A required field is not bound" }
                    if (a.kind != ActionKind.INSERT) require(a.recordId != null) { "Select a record to change" }
                    if (a.kind == ActionKind.DELETE) require(a.values.isEmpty())
                }
                ActionKind.NAVIGATE -> { require(a.target in screens); require(a.values.keys.all { it in inputs }) }
                ActionKind.ASSIGN -> require(a.values.keys.all { it in inputs })
                ActionKind.START_TIMER -> { require(components.any { it.id == a.target && it.kind == ComponentKind.TIMER }) { "A timer action needs a timer control" }; require(a.duration != null) }
                ActionKind.STOP_TIMER -> require(components.any { it.id == a.target && it.kind == ComponentKind.TIMER }) { "A timer action needs a timer control" }
            }
            a.values.values.forEach { expression(it) }; a.recordId?.let { expression(it) }; a.duration?.let { expression(it) }; a.condition?.let { expression(it) }
        }
        spec.screens.forEach { screen ->
            require(screen.title.length <= 100 && screen.components.size <= 80)
            screen.components.forEach { c ->
                require(c.label.length <= 4096)
                c.expression?.let { expression(it) }
                c.filter?.let { require(c.kind == ComponentKind.RECORD_LIST); expression(it) }
                when (c.kind) {
                    ComponentKind.INPUT -> if (c.fieldKind == FieldKind.REFERENCE) require(c.referenceEntity in entities) { "A record picker needs a known collection" } else require(c.referenceEntity.isBlank())
                    ComponentKind.BUTTON -> require(c.actionId in actions) { "Button action is missing" }
                    ComponentKind.RECORD_LIST -> {
                        require(c.entityId in entities); require(c.rowActions.all { it in actions })
                        require(c.displayFields.distinct().size == c.displayFields.size && c.displayFields.all { id -> entities[c.entityId]!!.fields.any { it.id == id } })
                        require(c.sortField.isBlank() || entities[c.entityId]!!.fields.any { it.id == c.sortField }) { "Sort references an unknown field" }
                    }
                    ComponentKind.QUIZ -> {
                        require(c.questions.size in 1..40); unique(c.questions.map { it.id })
                        c.questions.forEach { q -> require(q.question.length in 1..1000 && q.options.size in 2..8 && q.answer in q.options.indices && q.options.all { it.length in 1..500 }) }
                    }
                    else -> Unit
                }
            }
        }
    }
    fun additive(old: AppSpec, next: AppSpec) {
        old.entities.forEach { entity ->
            val after = next.entities.find { it.id == entity.id }
            require(after != null) { "Hide collection controls instead of removing its stored schema" }
            entity.fields.forEach { field ->
                val afterField = after.fields.find { it.id == field.id }
                require(afterField != null && afterField.kind == field.kind && afterField.referenceEntity == field.referenceEntity && (!afterField.required || field.required)) {
                    "Keep existing field IDs and types; new required constraints need a supported data migration"
                }
            }
            require(after.fields.filterNot { field -> entity.fields.any { it.id == field.id } }.none { it.required }) { "New fields must be optional for existing records" }
        }
    }
    private fun unique(ids: List<String>) { require(ids.distinct().size == ids.size && ids.all(identifier::matches)) { "App IDs must be unique stable identifiers" } }
    fun decimal(raw: String): BigDecimal = raw.trim().let {
        require(it.length in 1..48 && it.matches(Regex("-?(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)"))) { "Enter a decimal number using a period or comma, without grouping separators" }
        BigDecimal(it.replace(',', '.')).also { n -> require(n.precision() <= 32 && n.scale() <= 16) { "Number exceeds supported precision" } }
    }
}
