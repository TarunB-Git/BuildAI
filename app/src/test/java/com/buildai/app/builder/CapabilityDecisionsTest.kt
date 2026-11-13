package com.buildai.app.builder

import com.buildai.app.runtime.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test

class CapabilityDecisionsTest {
    @Test fun decisionsRoundTripDifferentCapabilityCombinationsWithoutAppTemplates() {
        val calculation = CompositionSelection(setOf(ComponentKind.INPUT, ComponentKind.SUMMARY), setOf(ExpressionOp.MULTIPLY))
        val records = CompositionSelection(setOf(ComponentKind.INPUT, ComponentKind.BUTTON, ComponentKind.RECORD_LIST), actions = setOf(ActionKind.INSERT), records = true)
        for (selection in listOf(calculation, records)) assertEquals(selection, CapabilityDecisions.read(CapabilityDecisions.json(selection)))
    }
    @Test fun decisionsRejectUnknownDuplicateWrongTypeAndPermissionChoices() {
        for (raw in listOf("{\"use\":[\"INPUT\",\"INPUT\"]}", "{\"use\":[true]}", "{\"use\":true}",
            "{\"use\":[\"INPUT\",\"ENABLE_CLOUD\"]}", "{\"use\":[\"INPUT\"],\"enableCloud\":true}", "{\"use\":[]}")) {
            assertTrue(raw, runCatching { CapabilityDecisions.read(JSONObject(raw)) }.isFailure)
        }
        val logMeal = CapabilityDecisions.read(JSONObject("{\"use\":[\"INPUT\",\"INSERT\",\"NEW_DATA\"]}"))
        assertEquals(setOf(ComponentKind.INPUT, ComponentKind.BUTTON), logMeal.components)
        assertEquals(setOf(ActionKind.INSERT), logMeal.actions)
        assertTrue(logMeal.records)
        val timer = CapabilityDecisions.read(JSONObject("{\"use\":[\"START_TIMER\"]}"))
        assertEquals(setOf(ComponentKind.BUTTON, ComponentKind.TIMER), timer.components)
    }
}
