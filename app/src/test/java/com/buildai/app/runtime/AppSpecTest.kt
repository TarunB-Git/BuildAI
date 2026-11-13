package com.buildai.app.runtime

import org.junit.Assert.*
import org.junit.Test

class AppSpecTest {
    @Test fun independentExamplesHaveDistinctBehaviorAndRoundTrip() {
        val examples = listOf(ManualExamples.notes(), ManualExamples.budget(), ManualExamples.routine())
        examples.forEach { spec -> AppSpecValidator.validate(spec); assertEquals(spec, AppSpec.parse(spec.json().toString())) }
        assertTrue(examples[0].screens.any { screen -> screen.components.any { it.kind == ComponentKind.QUIZ } })
        assertTrue(examples[1].screens.flatMap { it.components }.any { it.expression?.op == ExpressionOp.SUBTRACT })
        assertTrue(examples[2].actions.any { it.kind == ActionKind.START_TIMER })
    }
    @Test fun rejectsUnregisteredCodeAndBrokenBindings() {
        val spec = ManualExamples.notes()
        assertTrue(runCatching { AppSpec.parse(spec.json().put("javascript", "alert(1)").toString()) }.isFailure)
        val bad = spec.copy(actions = spec.actions.filterNot { it.id == "saveNote" })
        assertTrue(runCatching { AppSpecValidator.validate(bad) }.isFailure)
        for (key in listOf("entities", "actions")) {
            assertTrue("Malformed $key must reach repair", runCatching { AppSpec.parse(spec.json().put(key, org.json.JSONObject()).toString()) }.isFailure)
        }
    }
    @Test fun decimalCalculationsReadCurrentInputsAndRejectZeroDivisors() {
        val add = Expr(ExpressionOp.ADD, args = listOf(Expr.input("a"), Expr.input("b")))
        assertEquals("0.3", Expressions.display(Expressions.evaluate(add, ExpressionContext(mapOf("a" to "0.1", "b" to "0.2")))))
        assertEquals("8", Expressions.display(Expressions.evaluate(add, ExpressionContext(mapOf("a" to "3", "b" to "5")))))
        assertTrue(runCatching { Expressions.evaluate(Expr(ExpressionOp.DIVIDE, args = listOf(Expr.number("1"), Expr.number("0.00"))), ExpressionContext(emptyMap())) }.isFailure)
        val nested = Expr(ExpressionOp.MULTIPLY, args = listOf(Expr(ExpressionOp.DIVIDE, args = listOf(Expr.number("1"), Expr.number("3"))), Expr.number("3")))
        val value = Expressions.evaluate(nested, ExpressionContext(emptyMap())) as java.math.BigDecimal
        assertTrue(value.subtract(java.math.BigDecimal.ONE).abs() < java.math.BigDecimal("0.0000000000000001"))
        val equal = Expr(ExpressionOp.EQ, args = listOf(Expr.input("amount"), Expr.number("2.5")))
        assertEquals(true, Expressions.evaluate(equal, ExpressionContext(mapOf("amount" to "2,50"))))
        val textEquality = Expr(ExpressionOp.EQ, args = listOf(Expr.text("02"), Expr.text("2")))
        assertEquals(false, Expressions.evaluate(textEquality, ExpressionContext(emptyMap())))
    }
    @Test fun oneFeaturePatchLeavesUnrelatedComponentsAndFieldsIntact() {
        val base = ManualExamples.notes()
        val patch = AppPatch("revision-one", "Rename the save control", listOf(PatchOperation.PutComponent("home", base.screens[0].components.first { it.id == "save" }.copy(label = "Keep note"))))
        val after = patch.applyTo(base)
        assertEquals(base.entities, after.entities); assertEquals(base.actions, after.actions)
        assertEquals(base.screens[1], after.screens[1])
        assertEquals(base.screens[0].components.filterNot { it.id == "save" }, after.screens[0].components.filterNot { it.id == "save" })
    }
    @Test fun destructiveSchemaChangesAreRejectedBeforePublication() {
        val base = ManualExamples.notes()
        val after = base.copy(entities = base.entities.map { it.copy(fields = it.fields.filterNot { f -> f.id == "body" }) })
        assertTrue(runCatching { AppSpecValidator.additive(base, after) }.isFailure)
    }
    @Test fun timerActionsCannotTargetMissingOrUnrelatedControls() {
        val base = ManualExamples.routine()
        for (kind in listOf(ActionKind.START_TIMER, ActionKind.STOP_TIMER)) {
            for (target in listOf("missing", base.screens.first().components.first { it.kind == ComponentKind.INPUT }.id)) {
                val broken = base.copy(actions = base.actions + ActionSpec("badTimer", kind, target = target, duration = Expr.number("30")))
                assertTrue(runCatching { AppSpecValidator.validate(broken) }.isFailure)
            }
        }
    }
}
