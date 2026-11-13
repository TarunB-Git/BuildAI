package com.buildai.app.builder

import com.buildai.app.runtime.*
import org.junit.Assert.*
import org.junit.Test

class GuidedComposerTest {
    @Test fun configurationCombinesRecordsSearchAndLiveCalculationWithoutIdeaClassification() {
        val config = GuidedConfiguration("Materials", listOf(FieldSpec("label", "Material"), FieldSpec("width", "Width", FieldKind.NUMBER), FieldSpec("length", "Length", FieldKind.NUMBER)),
            saveRecords = true, search = true, calculation = "MULTIPLY", left = "width", right = "length", resultLabel = "Area")
        val spec = config.compose()
        assertTrue(spec.screens.single().components.any { it.kind == ComponentKind.RECORD_LIST })
        assertEquals("12.5", Expressions.display(Expressions.evaluate(spec.screens.single().components.first { it.id == "guidedResult" }.expression!!,
            ExpressionContext(mapOf("width" to "2.5", "length" to "5")))))
        assertEquals(config, GuidedConfiguration.read(config.json()))
        assertEquals(spec, AppSpec.parse(spec.json().toString()))
    }
    @Test fun missingNumericBindingsAndEmptyConfigurationStayIncomplete() {
        assertTrue(runCatching { GuidedConfiguration().compose() }.isFailure)
        assertTrue(runCatching { GuidedConfiguration(fields = listOf(FieldSpec("text", "Name")), calculation = "ADD", left = "text", right = "text").compose() }.isFailure)
    }
}
