package com.buildai.app.runtime

/** Shared deterministic query behavior for native rendering and independent probes. */
object RecordQueries {
    fun visible(spec: AppSpec, component: ComponentSpec, context: ExpressionContext): List<Map<String, Any>> {
        val query = component.expression?.let { Expressions.display(Expressions.evaluate(it, context)) }.orEmpty()
        val rows = context.records[component.entityId].orEmpty().filter { row ->
            (query.isBlank() || row.filterKeys { it != "id" }.values.any { it.toString().contains(query, true) }) &&
                (component.filter == null || Expressions.evaluate(component.filter, ExpressionContext(context.inputs, row, context.records, context.now,
                    context.numericInputs, spec.entities.first { it.id == component.entityId }.fields.filter { it.kind == FieldKind.NUMBER }.map { it.id }.toSet())) == true)
        }
        if (component.sortField.isBlank()) return rows
        val field = spec.entities.first { it.id == component.entityId }.fields.first { it.id == component.sortField }
        val ordered = rows.sortedWith { left, right ->
            val a = left[field.id]?.let(Expressions::display).orEmpty(); val b = right[field.id]?.let(Expressions::display).orEmpty()
            when {
                a.isBlank() || b.isBlank() -> a.compareTo(b)
                field.kind == FieldKind.NUMBER -> AppSpecValidator.decimal(a).compareTo(AppSpecValidator.decimal(b))
                else -> a.compareTo(b, ignoreCase = true) // ISO dates have chronological lexical order.
            }
        }
        return if (component.sortDescending) ordered.reversed() else ordered
    }
}
