package com.buildai.app.runtime

import org.junit.Assert.*
import org.junit.Test

class RecordQueriesTest {
    private val spec = AppSpec("Tasks", "home", listOf(ScreenSpec("home", "Tasks", listOf(ComponentSpec("query", ComponentKind.INPUT, "Search")))),
        listOf(EntitySpec("items", listOf(FieldSpec("title", "Title"), FieldSpec("amount", "Amount", FieldKind.NUMBER), FieldSpec("date", "Date", FieldKind.DATE), FieldSpec("done", "Done", FieldKind.BOOLEAN)))))
    private val rows = listOf(mapOf("id" to "a", "title" to "Train", "amount" to "12", "date" to "2026-02-01", "done" to false),
        mapOf("id" to "b", "title" to "TRAIN ticket", "amount" to "2.5", "date" to "2026-01-31", "done" to true),
        mapOf("id" to "c", "title" to "Food", "amount" to "3", "date" to "2026-03-01", "done" to false))
    @Test fun searchSortAndBooleanFilterComposeWithoutMutatingRecords() {
        val list = ComponentSpec("list", ComponentKind.RECORD_LIST, entityId = "items", expression = Expr.input("query"), sortField = "amount",
            filter = Expr(ExpressionOp.EQ, args = listOf(Expr(ExpressionOp.RECORD, "done"), Expr(ExpressionOp.BOOLEAN, "false"))))
        val context = ExpressionContext(mapOf("query" to "train"), records = mapOf("items" to rows))
        assertEquals(listOf("a"), RecordQueries.visible(spec, list, context).map { it["id"] })
        assertEquals(listOf("b", "c", "a"), RecordQueries.visible(spec, list.copy(expression = null, filter = null), context).map { it["id"] })
        assertEquals(listOf("c", "a", "b"), RecordQueries.visible(spec, list.copy(expression = null, filter = null, sortField = "date", sortDescending = true), context).map { it["id"] })
        assertEquals(3, rows.size); assertEquals("12", rows.first()["amount"])
    }
    @Test fun isoDatesUseCalendarArithmeticIncludingLeapDays() {
        val context = ExpressionContext(emptyMap())
        assertEquals("2024-03-01", Expressions.evaluate(Expr(ExpressionOp.DATE_ADD, args = listOf(Expr.text("2024-02-28"), Expr.number("2"))), context))
        assertEquals("2", Expressions.display(Expressions.evaluate(Expr(ExpressionOp.DATE_DIFF, args = listOf(Expr.text("2024-03-01"), Expr.text("2024-02-28"))), context)))
        assertEquals(true, Expressions.evaluate(Expr(ExpressionOp.BEFORE, args = listOf(Expr.text("2026-01-31"), Expr.text("2026-02-01"))), context))
        assertTrue(runCatching { Expressions.evaluate(Expr(ExpressionOp.DATE_ADD, args = listOf(Expr.text("2025-02-29"), Expr.number("1"))), context) }.isFailure)
    }
}
