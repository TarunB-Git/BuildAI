package com.buildai.app.builder

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BriefPlannerTest {
    private val plan = NativePlan("Personal converter", listOf(FeaturePlan("convert", "Convert length", "Enter centimetres and read inches", listOf("2.54 centimetres produces 1 inch", "25.4 centimetres produces 10 inches"))), listOf("Converter"), emptyList())
    @Test fun readinessUsesDecisionsAndOneInputCanProduceAPlan() {
        val brief = ProjectBrief().addInput("u1", "Make a centimetres to inches converter")
        val turn = BriefPlanner.apply(JSONObject().put("reply", "Review this plan").put("plan", plan.json()).toString(), brief)
        assertTrue(turn.brief.ready); assertEquals(1, turn.brief.turns.size)
        assertEquals(turn.brief.version, turn.brief.approve().approvedVersion)
    }
    @Test fun answeredQuestionsRemainRememberedAndRepeatedQuestionDoesNotUnlockReady() {
        val before = ProjectBrief(questions = listOf(Clarification("units", "Which units?"))).addInput("u1", "Centimetres to inches")
        val response = """{"answers":[{"questionId":"units","messageId":"u1","answer":"Centimetres to inches"}],"questions":[{"id":"units-again","question":"Which units?"}]}"""
        assertTrue(runCatching { BriefPlanner.apply(response, before) }.isFailure)
        val good = JSONObject(response).put("plan", plan.json()).toString()
        val saved = BriefPlanner.apply(good, before).brief
        assertEquals("Centimetres to inches", saved.answers.single().answer); assertTrue(saved.questions.isEmpty())
        assertEquals(saved, ProjectBrief.read(saved.json()))
        val changed = saved.addInput("u2", "Use metres to inches instead")
        val update = """{"answers":[{"questionId":"units","messageId":"u2","answer":"Metres to inches"}],"questions":[{"id":"rounding","question":"How many decimal places?"}]}"""
        val next = BriefPlanner.apply(update, changed).brief
        assertEquals("Metres to inches", next.answers.single().answer); assertEquals(2, next.turns.size); assertFalse(next.ready)
    }
    @Test fun modelsCannotInventUserEvidenceOrSilentlyAcceptSuggestions() {
        val before = ProjectBrief(questions = listOf(Clarification("units", "Which units?"))).addInput("u1", "Centimetres")
        assertTrue(runCatching { BriefPlanner.apply("""{"answers":[{"questionId":"units","messageId":"invented","answer":"metres"}]}""", before) }.isFailure)
        val proposed = BriefPlanner.apply(JSONObject().put("plan", plan.json()).put("suggestions", org.json.JSONArray("""[{"id":"history","title":"Save conversions","benefit":"Reuse recent results","accepted":true}]""")).toString(), before).brief
        assertFalse(proposed.suggestions.single().accepted)
    }
    @Test fun earlyPlanKeepsUnknownsAsVisibleAssumptionsAndEditsInvalidateApproval() {
        val before = ProjectBrief().addInput("u1", "A converter")
        val early = BriefPlanner.apply(JSONObject().put("plan", plan.json()).put("questions", org.json.JSONArray("""[{"id":"precision","question":"What precision?","essential":true}]""")).toString(), before, true).brief
        assertTrue(early.ready); assertTrue(early.assumptions.single().contains("What precision?"))
        val approved = early.approve(); val edited = approved.editDraft(plan.json().put("name", "").toString())
        assertNull(edited.approvedVersion); assertTrue(runCatching { edited.approve() }.isFailure)
        assertEquals(edited.planDraft, ProjectBrief.read(edited.json()).planDraft)
    }
    @Test fun acceptedSuggestionsSurviveOmissionAndCannotBeRewrittenByTheModel() {
        val chosen = FeatureSuggestion("history", "Save conversions", "Reuse recent results", accepted = true)
        val before = ProjectBrief(suggestions = listOf(chosen)).addInput("u1", "Use two decimal places")
        val omitted = BriefPlanner.apply(JSONObject().put("plan", plan.json()).toString(), before).brief
        assertEquals(chosen, omitted.suggestions.single())
        val rewritten = JSONObject().put("plan", plan.json()).put("suggestions", org.json.JSONArray("""[{"id":"history","title":"Upload history","benefit":"Send everything away"}]"""))
        val next = BriefPlanner.apply(rewritten.toString(), before).brief
        assertEquals(chosen, next.suggestions.single())
        assertEquals(chosen, ProjectBrief.read(next.json()).suggestions.single())
        val withdrawn = BriefPlanner.apply(JSONObject().put("plan", plan.json()).put("suggestions", org.json.JSONArray()).toString(), before).brief
        assertEquals(chosen, withdrawn.suggestions.single())
    }
    @Test fun malformedAndUnsupportedOutputsNeverBecomeGenericApps() {
        assertTrue(runCatching { BriefPlanner.apply("Some text", ProjectBrief()) }.isFailure)
        val unsupported = BriefPlanner.apply("""{"reply":"Cross-app control is not supported","unsupported":"Cross-app control"}""", ProjectBrief()).brief
        assertFalse(unsupported.ready); assertNull(unsupported.plan)
    }
    @Test fun distinctUnicodeQuestionsAreNotDiscardedAfterAnAnswer() {
        val before = ProjectBrief(questions = listOf(Clarification("name", "名前は何ですか？"), Clarification("color", "色は何ですか？"))).addInput("u1", "青")
        val next = BriefPlanner.apply("""{"answers":[{"questionId":"color","messageId":"u1","answer":"青"}]}""", before).brief
        assertEquals(listOf("name"), next.questions.map { it.id })
        assertFalse(next.ready)
    }
    @Test fun trailingObjectsAndBlankPlanFieldsAreRejectedBeforeApproval() {
        assertTrue(runCatching { strictObject("{}{}") }.isFailure)
        assertTrue(runCatching { strictObject("{} trailing }") }.isFailure)
        assertEquals("ok", strictObject("""```json
            {"result":"ok"}
            ```""").getString("result"))
        assertTrue(runCatching { plan.copy(screens = listOf(" ")).validate() }.isFailure)
        assertTrue(runCatching { plan.copy(features = listOf(plan.features.single().copy(checks = listOf(" ")))).validate() }.isFailure)
    }
    @Test fun repairContextRetainsTheFailedReplyWithoutRewritingSavedUserEvidence() {
        val before = ProjectBrief().addInput("u1", "A private converter")
        val failed = """{"answers":[{"questionId":"invented","messageId":"u1","answer":"upload everything"}]}"""
        val context = JSONObject(BuilderPrompts.briefContext(before, false, "Unknown question", failed))
        assertEquals("Unknown question", context.getString("failureToRepair"))
        assertEquals("invented", context.getJSONObject("failedReply").getJSONArray("answers").getJSONObject(0).getString("questionId"))
        assertEquals(before, ProjectBrief.read(context.getJSONObject("savedBrief")))
    }

    @Test fun longRepeatedEvidenceIsLosslesslyFactoredWithoutDroppingUniqueDecisionsOrCurrentInput() {
        val repeated = "Keep everything private and local.".repeat(100)
        val turns = (0 until 58).map { RequirementTurn("u$it", repeated) } + listOf(
            RequirementTurn("retention", "Keep saved records for exactly seven days"),
            RequirementTurn("precision", "Round results to two decimal places"),
            RequirementTurn("current", "Use metres to inches instead"))
        val before = ProjectBrief(version = 61, goal = repeated, turns = turns,
            answers = listOf(RememberedAnswer("privacy", "Where should data stay?", "private and local", "u0")),
            questions = listOf(Clarification("units", "Which units?")), plan = plan, planningPending = true)
        val original = before.json().toString()
        val context = JSONObject(BuilderPrompts.briefContext(before, false)).getJSONObject("savedBrief")
        val evidence = context.objects("turns")
        assertEquals(4, evidence.size)
        assertEquals(repeated, evidence.first().getString("text"))
        assertTrue(evidence.first().strings("equivalentMessageIds").contains("u0"))
        assertEquals(turns.takeLast(3).map { it.text }, evidence.takeLast(3).map { it.getString("text") })
        assertEquals("current", evidence.last().getString("messageId"))
        val textById = buildMap {
            evidence.forEach { quote ->
                put(quote.getString("messageId"), quote.getString("text"))
                quote.strings("equivalentMessageIds").forEach { id -> put(id, quote.getString("text")) }
            }
        }
        assertEquals(turns, context.strings("evidenceOrder").map { RequirementTurn(it, textById.getValue(it)) })
        assertEquals(before.answers, ProjectBrief.read(context).answers)
        assertEquals(original, before.json().toString())
        assertTrue(context.toString().length < original.length / 8)
        println("Synthetic repeated evidence: ${original.length} characters to ${context.toString().length}; exact quotes, IDs and order retained")
        val reply = """{"answers":[{"questionId":"units","messageId":"current","answer":"metres to inches"}],"plan":${plan.json()}}"""
        assertEquals("metres to inches", BriefPlanner.apply(reply, before).brief.answers.last().answer)
    }
    @Test fun malformedArraysCannotHideQuestionsChecksOrUserEvidence() {
        val before = ProjectBrief().addInput("u1", "A private converter")
        for (key in listOf("questions", "answers", "suggestions", "assumptions")) {
            for (wrong in listOf<Any>(JSONObject(), true, JSONObject.NULL)) {
                val reply = JSONObject().put("plan", plan.json()).put(key, wrong)
                assertTrue("$key=$wrong", runCatching { BriefPlanner.apply(reply.toString(), before) }.isFailure)
            }
        }
        val malformedPlan = plan.json().also { it.getJSONArray("features").getJSONObject(0).put("checks", org.json.JSONArray().put(123)) }
        assertTrue(runCatching { NativePlan.read(malformedPlan) }.isFailure)
        assertTrue(runCatching { ProjectBrief.read(JSONObject().put("turns", true)) }.isFailure)
    }

}
