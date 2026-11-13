package com.buildai.app.builder

import com.buildai.app.runtime.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CompositionSelectionTest {
    @Test fun approvedCapabilitySelectionSurvivesPlanAndBriefStorage() {
        val chosen = CompositionSelection(setOf(ComponentKind.INPUT, ComponentKind.SUMMARY), setOf(ExpressionOp.DIVIDE))
        val plan = NativePlan("Unit helper", listOf(FeaturePlan("units", "Convert units", "Enter centimetres and read inches", listOf("2.54 shows 1"), chosen)), listOf("Convert"), emptyList())
        val original = ProjectBrief().addInput("u1", "A length helper").editPlan(plan)
        val saved = ProjectBrief.read(original.json())
        assertEquals(chosen, saved.plan!!.features.single().composition)
        assertTrue(saved.plan!!.features.single().composition!!.configurationPrompt().contains("screens MUST be []"))
    }
    @Test fun unknownOrInvisibleActionsDoNotBecomeRegisteredCapabilities() {
        assertTrue(runCatching { CompositionSelection.read(JSONObject("""{"components":["WEB_CODE"],"expressions":[],"actions":[],"records":false,"screens":false}""")) }.isFailure)
        assertTrue(runCatching { CompositionSelection(setOf(ComponentKind.SUMMARY), actions = setOf(ActionKind.INSERT)).validate() }.isFailure)
        assertTrue(runCatching { CompositionSelection.read(JSONObject("""{"components":["TEXT"],"expressions":[],"actions":[],"records":false,"screens":false,"permission":"cloud"}""")) }.isFailure)
    }
    @Test fun selectedCapabilitiesAndCalculationsMustActuallyBeImplemented() {
        val selected = CompositionSelection(setOf(ComponentKind.INPUT, ComponentKind.SUMMARY), setOf(ExpressionOp.MULTIPLY))
        val width = ComponentSpec("width", ComponentKind.INPUT, "Width", FieldKind.NUMBER)
        val height = ComponentSpec("height", ComponentKind.INPUT, "Height", FieldKind.NUMBER)
        val output = ComponentSpec("area", ComponentKind.SUMMARY, "Area", expression = Expr(ExpressionOp.MULTIPLY, args = listOf(Expr.input("width"), Expr.input("height"))))
        fun candidate(controls: List<ComponentSpec>) = AppSpec("Area", "home", listOf(ScreenSpec("home", "Area", controls)))
        fun fragment(controls: List<ComponentSpec>) = JSONObject().put("components", org.json.JSONArray(controls.map { it.json() }))
        val inputs = listOf(width, height)
        assertTrue(runCatching { selected.validateImplementation(candidate(inputs), fragment(inputs)) }.exceptionOrNull()?.message.orEmpty().contains("SUMMARY"))
        val constant = inputs + output.copy(expression = Expr.number("10"))
        assertTrue(runCatching { selected.validateImplementation(candidate(constant), fragment(constant)) }.exceptionOrNull()?.message.orEmpty().contains("MULTIPLY"))
        val complete = inputs + output
        selected.validateImplementation(candidate(complete), fragment(complete))
        val disguisedConstant = inputs + output.copy(expression = Expr(ExpressionOp.MULTIPLY, args = listOf(Expr.number("2"), Expr.number("3"))))
        assertTrue(runCatching { selected.validateImplementation(candidate(disguisedConstant), fragment(disguisedConstant)) }.exceptionOrNull()?.message.orEmpty().contains("not connected"))
        assertTrue(runCatching { selected.copy(records = true).validateImplementation(candidate(complete), fragment(complete)) }.isFailure)
    }
}
