package com.buildai.app.data.local

import org.junit.Assert.*
import org.junit.Test

class QuickPlanTest {
    @Test fun retainsIdeaAndBuildsReviewableOfflinePlan() {
        val plan = QuickPlan.fromIdea("A habit tracker for watering plants, with a weekly history")
        assertEquals("Habit Tracker", plan.appName)
        assertEquals(listOf("Home", "History"), plan.screens)
        assertTrue(plan.features.any { it.contains("watering plants") })
        assertTrue(plan.features.any { it.contains("Keep required interactions working offline") })
    }
}
