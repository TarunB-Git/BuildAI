package com.buildai.app.data.api

import android.content.Context
import android.app.ActivityManager
import com.buildai.app.data.local.LocalLlmEngine
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.Message
import com.buildai.app.data.model.MessageRole
import com.buildai.app.data.model.ProjectPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.io.File

/** Custom builds always use the selected model, regardless of its file size. */
class LocalPwaGenerator(context: Context,
    private val engine: com.buildai.app.data.local.ModelAdapter = LocalLlmEngine(context)) {
    private val featureValidator by lazy { GeneratedAppValidator(context) }
    private val ramBytes = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .let { manager -> ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem }

    suspend fun generate(
        modelFile: File, plan: ProjectPlan, design: DesignSpec,
        onProgress: (String, Float) -> Unit,
        validate: suspend (String) -> Unit,
        previousHtml: String? = null,
        onDraft: (String) -> Unit = {},
        onProgressEvidence: (com.buildai.app.data.local.InferenceProgress) -> Unit = {}
    ): Result<String> {
        val basePrompt = """
            Write the working HTML controls and JavaScript for this app:
            Name: ${plan.appName}
            Purpose: ${plan.description}
            Features: ${plan.features.joinToString("; ")}
            Screens: ${plan.screens.joinToString(", ")}
            ${if (GeneratedAppFeatureChecks.forPlan(plan) != null) "Include two number inputs, a Calculate button, and an output element in html. On click, read the CURRENT inputs with Number(), add them, and update that output element. It must work for whole numbers and decimals. Register callbacks as functions: onclick=()=>{...}, or addEventListener('click', handler). Never use addEventListener('click', handler()) because that calls it at startup." else ""}
            Return exactly JSON with two strings: {"html":"working HTML controls","js":"working JavaScript event handlers"}.
            BuildAI already supplies the page title, mobile layout and CSS. Do not write CSS, a header, html/head/body tags, explanations or example code. Minify the code and keep the complete JSON under 600 tokens. Implement only the requested behavior. Use localStorage only for data that needs saving. No external dependencies. The html string contains controls only, without script tags. The js string contains code only, without script tags. Use valid JavaScript: declare named functions with the function keyword, or assign handlers with document.getElementById('buttonId').onclick=()=>{...}. Read input.value INSIDE the event handler, after the user types; never cache input values at page startup. Input values are strings: use Number(input.value) for calculations. Every referenced element id must exist in html.
        """.trimIndent()
        val previousContent = previousHtml?.substringAfter("<!--app-content-->", previousHtml)
            ?.substringBefore("<!--/app-content-->")?.take(4000)
        val revision = previousContent?.let {
            "\nUpdate this existing content to match the requirements. Preserve localStorage keys and saved data.\nExisting content:\n$it"
        }.orEmpty()
        val contextSize = when {
            ramBytes < 5L * 1024 * 1024 * 1024 -> 4096
            ramBytes < 7L * 1024 * 1024 * 1024 -> 6144
            else -> 8192
        }
        return try {
            run {
                onProgress("Writing your requested app on this phone…", 0.15f)
                var repair = ""
                var failure: Exception? = null
                val probe = GeneratedAppFeatureChecks.forPlan(plan)
                suspend fun checkDraft(draft: String): String {
                    val html = HtmlValidator.extractAndValidate(LocalAppScaffold.wrap(draft, plan, design))
                    onProgress("Checking the app opens…", 0.85f)
                    validate(html)
                    probe?.let {
                        onProgress("Checking the requested calculation…", 0.9f)
                        featureValidator.validateInteraction(html, it)
                    }
                    return html
                }
                repeat(3) { attempt ->
                    val output = StringBuilder()
                    engine.reply(modelFile, "You write compact functional app code. Return JSON with html and js strings. Use single quotes inside code when possible. No CSS, document boilerplate or explanations.",
                        listOf(Message(role = MessageRole.USER, content = basePrompt + revision + repair)),
                        maxTokens = 1024, contextSize = contextSize, grammar = LocalAppScaffold.CODE_GRAMMAR,
                        priority = com.buildai.app.data.local.InferencePriority.BUILD,
                        onProgress = onProgressEvidence,
                        onStatus = { onProgress(it, if (attempt == 0) 0.25f else 0.6f) }).collect { output.append(it) }
                    val draft = output.toString()
                    onDraft(draft)
                    try {
                        return@run Result.success(checkDraft(draft))
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        var feedbackError = e
                        var feedbackDraft = draft
                        if (probe != null && e is GeneratedAppValidationException) {
                            var corrected = draft
                            if (e.diagnostic.contains("event registration calls"))
                                corrected = LocalAppScaffold.repairCalledEventHandler(corrected) ?: corrected
                            corrected = LocalAppScaffold.repairLiveNumericAddition(corrected) ?: corrected
                            if (e.diagnostic.contains("Missing HTML element"))
                                corrected = LocalAppScaffold.repairMissingTextOutput(corrected) ?: corrected
                            if (corrected != draft) {
                                onProgress("Repairing the calculation and checking it again…", 0.8f)
                                onDraft(corrected)
                                try { return@run Result.success(checkDraft(corrected)) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (repairedError: Exception) { feedbackError = repairedError; feedbackDraft = corrected }
                            }
                        }
                        failure = feedbackError
                        if (attempt < 2) {
                            onProgress("Repairing the app after its opening check…", 0.55f)
                            val diagnostic = (feedbackError as? GeneratedAppValidationException)?.diagnostic ?: feedbackError.message.orEmpty()
                            repair = "\nYour previous code failed validation: ${diagnostic.take(400)}\nPrevious JSON:\n${feedbackDraft.take(5000)}\nFix the error and return the complete corrected JSON. Check all functions and event handlers are valid JavaScript and all numeric inputs use Number()."
                        }
                    }
                }
                throw checkNotNull(failure)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            Result.failure(IllegalStateException("The model did not produce a working custom app: ${e.message}. Revise the plan or choose a quick template; your previous app is preserved.", e))
        } finally { engine.unload() }
    }
}
