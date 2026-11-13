package com.buildai.app

import androidx.test.platform.app.InstrumentationRegistry
import com.buildai.app.data.api.GeneratedAppFeatureChecks
import com.buildai.app.data.api.GeneratedAppValidationException
import com.buildai.app.data.api.GeneratedAppValidator
import com.buildai.app.data.api.LocalAppScaffold
import com.buildai.app.data.local.QuickPlan
import com.buildai.app.data.model.DesignSpec
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GeneratedAppFeatureValidationTest {
    @Test fun repairsNumericValuesDeclaredInAnotherHandler() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val plan = QuickPlan.fromIdea("Add two numbers")
        val draft = JSONObject().put("html", "<input type='number' id='a'><input type='number' id='b'><button id='calculate'>Calculate</button><output id='answer'></output>")
            .put("js", "document.getElementById('a').oninput=()=>{var first=Number(document.getElementById('a').value)};document.getElementById('b').oninput=()=>{var second=Number(document.getElementById('b').value)};document.getElementById('calculate').onclick=()=>{var total=first+second;document.getElementById('answer').textContent=total};").toString()
        val validator = GeneratedAppValidator(app)
        val probe = checkNotNull(GeneratedAppFeatureChecks.forPlan(plan))
        try {
            validator.validateInteraction(LocalAppScaffold.wrap(draft, plan, DesignSpec()), probe)
            fail("Values declared inside another handler are unavailable to Calculate")
        } catch (_: GeneratedAppValidationException) { }
        val repaired = checkNotNull(LocalAppScaffold.repairLiveNumericAddition(draft))
        validator.validateInteraction(LocalAppScaffold.wrap(repaired, plan, DesignSpec()), probe)
    }

    @Test fun repairsTheMissingOutputElementNamedByTheModel() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val plan = QuickPlan.fromIdea("Add two numbers")
        val draft = JSONObject().put("html", "<input type='number' id='a'><input type='number' id='b'><button id='calculate'>Calculate</button>")
            .put("js", "document.getElementById('calculate').onclick=()=>{document.getElementById('answer').textContent=Number(document.getElementById('a').value)+Number(document.getElementById('b').value)};").toString()
        val validator = GeneratedAppValidator(app)
        val probe = checkNotNull(GeneratedAppFeatureChecks.forPlan(plan))
        try {
            validator.validateInteraction(LocalAppScaffold.wrap(draft, plan, DesignSpec()), probe)
            fail("An absent result element must fail the check")
        } catch (expected: GeneratedAppValidationException) {
            assertTrue(expected.diagnostic.contains("Missing HTML element"))
        }
        val repaired = checkNotNull(LocalAppScaffold.repairMissingTextOutput(draft))
        validator.validateInteraction(LocalAppScaffold.wrap(repaired, plan, DesignSpec()), probe)
    }

    @Test fun detectsAndRepairsAnImmediatelyCalledCalculatorHandler() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val plan = QuickPlan.fromIdea("Add two numbers")
        val draft = JSONObject().put("html", "<input type='number' id='a'><input type='number' id='b'><button id='calculate'>Calculate</button><output id='answer'></output>")
            .put("js", "function calculate(){document.getElementById('answer').textContent=Number(document.getElementById('a').value)+Number(document.getElementById('b').value)}document.getElementById('calculate').addEventListener('click',calculate());").toString()
        val validator = GeneratedAppValidator(app)
        val probe = checkNotNull(GeneratedAppFeatureChecks.forPlan(plan))
        try {
            validator.validateInteraction(LocalAppScaffold.wrap(draft, plan, DesignSpec()), probe)
            fail("A calculator that only computes at startup must fail its interaction check")
        } catch (expected: GeneratedAppValidationException) {
            assertTrue(expected.diagnostic.contains("event registration calls"))
        }
        val repaired = checkNotNull(LocalAppScaffold.repairCalledEventHandler(draft))
        validator.validateInteraction(LocalAppScaffold.wrap(repaired, plan, DesignSpec()), probe)
    }
}
