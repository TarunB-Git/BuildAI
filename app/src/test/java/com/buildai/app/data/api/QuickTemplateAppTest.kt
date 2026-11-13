package com.buildai.app.data.api

import com.buildai.app.data.local.QuickPlan
import com.buildai.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class QuickTemplateAppTest {
    @Test fun calculatorAndTimerDoNotTurnIntoListApps() {
        val p = QuickPlan.fromIdea("A calculator to add two numbers")
        assertEquals(BuildMode.CUSTOM, p.buildMode)
        assertEquals(QuickTemplate.CALCULATOR, p.quickTemplate)
        val calculator = HtmlValidator.extractAndValidate(QuickTemplateApp.build(p.copy(quickTemplate = QuickTemplate.CALCULATOR), DesignSpec()))
        val timer = HtmlValidator.extractAndValidate(QuickTemplateApp.build(p.copy(quickTemplate = QuickTemplate.TIMER), DesignSpec()))
        assertTrue(calculator.contains("Cannot divide by zero"))
        assertTrue(timer.contains("Date.now()"))
        assertFalse(calculator.contains("item-form"))
        assertFalse(timer.contains("item-form"))
    }
    @Test fun templateChoiceSurvivesSavingAndLegacyPlansDefaultToCustom() {
        val original = QuickPlan.fromIdea("Track my expenses").copy(buildMode = BuildMode.TEMPLATE, quickTemplate = QuickTemplate.EXPENSES)
        assertEquals(original, ProjectPlan.fromJson(original.toJson()))
        val legacy = original.toJson().apply { remove("buildMode"); remove("quickTemplate") }
        assertEquals(BuildMode.CUSTOM, ProjectPlan.fromJson(legacy).buildMode)
    }
    @Test fun everyChosenTemplateIsSelfContainedAndEscapesUserText() {
        QuickTemplate.entries.forEach { kind ->
            val plan = QuickPlan.fromIdea("Private tool").copy(appName = "</script><img src=x>", quickTemplate = kind)
            val html = HtmlValidator.extractAndValidate(QuickTemplateApp.build(plan, DesignSpec()))
            assertFalse(html.contains("<img src=x>"))
        }
    }
}
