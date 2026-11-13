package com.buildai.app.data.api

import com.buildai.app.data.local.QuickPlan
import org.junit.Assert.*
import org.junit.Test

class GeneratedAppFeatureChecksTest {
    @Test fun checksOnlyTheSupportedAdditionWorkflow() {
        assertNotNull(GeneratedAppFeatureChecks.forPlan(QuickPlan.fromIdea("Add two numbers")))
        assertNull(GeneratedAppFeatureChecks.forPlan(QuickPlan.fromIdea("Add two shopping items")))
        assertNull(GeneratedAppFeatureChecks.forPlan(QuickPlan.fromIdea("Multiply two numbers")))
    }
}
