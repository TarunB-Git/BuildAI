package com.buildai.app.runtime

import java.math.BigDecimal
import java.math.MathContext

class ExpressionContext(
    val inputs: Map<String, String>, val record: Map<String, Any> = emptyMap(),
    val records: Map<String, List<Map<String, Any>>> = emptyMap(), val now: Long = System.currentTimeMillis(),
    val numericInputs: Set<String> = emptySet(), val numericFields: Set<String> = emptySet()
) {
    companion object {
        fun forSpec(spec: AppSpec, inputs: Map<String, String>, record: Map<String, Any> = emptyMap(),
            records: Map<String, List<Map<String, Any>>> = emptyMap(), entityId: String? = null): ExpressionContext = ExpressionContext(
            inputs, record, records, numericInputs = spec.screens.flatMap { it.components }.filter { it.kind == ComponentKind.INPUT && it.fieldKind == FieldKind.NUMBER }.map { it.id }.toSet(),
            numericFields = spec.entities.firstOrNull { it.id == entityId }?.fields.orEmpty().filter { it.kind == FieldKind.NUMBER }.map { it.id }.toSet())
    }
}
object Expressions {
    fun evaluate(e: Expr, context: ExpressionContext, depth: Int = 0): Any {
        require(depth <= 8)
        fun arg(index: Int) = evaluate(e.args[index], context, depth + 1)
        // The input parser bounds external values. Computed DECIMAL128 values must
        // retain their precision when consumed by another operation.
        fun number(index: Int): BigDecimal = arg(index).let { value ->
            if (value is BigDecimal) value else AppSpecValidator.decimal(display(value))
        }
        val result: Any = when (e.op) {
            ExpressionOp.TEXT -> e.value
            ExpressionOp.NUMBER -> AppSpecValidator.decimal(e.value)
            ExpressionOp.BOOLEAN -> e.value.toBooleanStrict()
            ExpressionOp.INPUT -> context.inputs[e.value].orEmpty().let { if (e.value in context.numericInputs && it.isNotBlank()) AppSpecValidator.decimal(it) else it }
            ExpressionOp.RECORD -> (context.record[e.value] ?: "").let { if (e.value in context.numericFields && display(it).isNotBlank()) AppSpecValidator.decimal(display(it)) else it }
            ExpressionOp.COUNT -> BigDecimal(context.records[e.value].orEmpty().size)
            ExpressionOp.SUM -> {
                val (entity, field) = e.value.split('.')
                context.records[entity].orEmpty().fold(BigDecimal.ZERO) { sum, row ->
                    val value = row[field]?.let(::display).orEmpty()
                    sum + if (value.isBlank()) BigDecimal.ZERO else AppSpecValidator.decimal(value)
                }
            }
            ExpressionOp.NOW -> BigDecimal(context.now)
            ExpressionOp.TODAY -> java.time.Instant.ofEpochMilli(context.now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
            ExpressionOp.DATE_ADD -> java.time.LocalDate.parse(display(arg(0))).plusDays(number(1).longValueExact()).toString()
            ExpressionOp.DATE_DIFF -> BigDecimal(java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(display(arg(1))), java.time.LocalDate.parse(display(arg(0)))))
            ExpressionOp.BEFORE -> java.time.LocalDate.parse(display(arg(0))).isBefore(java.time.LocalDate.parse(display(arg(1))))
            ExpressionOp.AFTER -> java.time.LocalDate.parse(display(arg(0))).isAfter(java.time.LocalDate.parse(display(arg(1))))
            ExpressionOp.ADD -> number(0).add(number(1), MathContext.DECIMAL128)
            ExpressionOp.SUBTRACT -> number(0).subtract(number(1), MathContext.DECIMAL128)
            ExpressionOp.MULTIPLY -> number(0).multiply(number(1), MathContext.DECIMAL128)
            ExpressionOp.DIVIDE -> { val divisor = number(1); require(divisor.signum() != 0) { "Cannot divide by zero" }; number(0).divide(divisor, MathContext.DECIMAL128) }
            ExpressionOp.CONCAT -> e.args.joinToString("") { display(evaluate(it, context, depth + 1)) }
            ExpressionOp.EQ -> {
                val left = arg(0); val right = arg(1)
                if (left is BigDecimal || right is BigDecimal) {
                    val a = if (left is BigDecimal) left else AppSpecValidator.decimal(display(left))
                    val b = if (right is BigDecimal) right else AppSpecValidator.decimal(display(right))
                    a.compareTo(b) == 0
                } else display(left) == display(right)
            }
            ExpressionOp.GT -> number(0) > number(1)
            ExpressionOp.CONTAINS -> display(arg(0)).contains(display(arg(1)), ignoreCase = true)
            ExpressionOp.AND -> e.args.all { evaluate(it, context, depth + 1) == true }
            ExpressionOp.IF -> if (arg(0) == true) arg(1) else arg(2)
        }
        require(display(result).length <= 4096) { "Calculation result is too large" }
        return result
    }
    fun display(value: Any): String = if (value is BigDecimal) value.stripTrailingZeros().toPlainString() else value.toString()
}
