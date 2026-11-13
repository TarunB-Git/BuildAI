package com.buildai.app.data.api

import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.ProjectPlan
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

class PwaGenerator(private val api: GeminiApi = GeminiApi()) {
    private val codeModels = listOf("gemini-2.5-flash-lite", "gemini-2.5-flash", "gemma-4-26b-a4b-it")

    suspend fun generate(
        plan: ProjectPlan, design: DesignSpec, apiKey: String, model: String,
        onProgress: (String, Float) -> Unit,
        validate: suspend (String) -> Unit = {}, provider: CloudProvider = CloudProvider.GEMINI, fallbackEnabled: Boolean = true, fallbackModels: List<String>? = null
    ): Result<String> {
        if (DesignDefaults.template(design.templateId).hasBottomNav && design.tabs.any { it.name.isBlank() })
            return Result.failure(IllegalArgumentException("Give every navigation tab a name before building."))
        var initial: String? = null
        var workingModel: String? = null
        var lastError: Exception = Exception("Couldn't generate this app. Try again.")
        for (candidate in (listOf(model) + if (fallbackEnabled) fallbackModels ?: if (provider == CloudProvider.GEMINI) codeModels else emptyList() else emptyList()).distinct()) {
            onProgress("Writing your app…", 0.15f)
            try {
                initial = HtmlValidator.extractAndValidate(callApi(buildGenerationPrompt(plan, design), apiKey, candidate, provider))
                workingModel = candidate
                break
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                lastError = e
                if (e is GeminiApiException && e.status in listOf(401, 403)) return Result.failure(e)
            }
        }
        val original = initial ?: return Result.failure(lastError)
        onProgress("Reviewing your app…", 0.60f)
        val reviewed = try {
            HtmlValidator.extractAndValidate(callApi(buildReviewPrompt(original, plan), apiKey, checkNotNull(workingModel), provider))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        onProgress("Checking that your app opens…", 0.85f)
        try {
            if (reviewed != null) {
                try { validate(reviewed); return Result.success(reviewed) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* The original may still be usable. */ }
            }
            validate(original)
            return Result.success(original)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return Result.failure(e) }
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    internal fun buildGenerationPrompt(plan: ProjectPlan, design: DesignSpec): String {
        val palette  = DesignDefaults.palette(design.paletteId)
        val template = DesignDefaults.template(design.templateId)
        val primary  = colorHex(palette.primary)
        val accent   = colorHex(palette.accent)
        val features = plan.features.joinToString("\n") { "- $it" }
        val screens  = plan.screens.joinToString("\n") { "- $it" }
        val tabs     = if (template.hasBottomNav)
            design.tabs.joinToString(", ") { "${it.name} (icon: ${it.iconKey})" }
        else "N/A"

        return """
Generate a complete, self-contained single HTML file for this mobile web app. Output ONLY the raw HTML — no explanation, no markdown fences, no text before <!DOCTYPE html> or after </html>.

APP DETAILS
Name: ${plan.appName}
Description: ${plan.description}
For: ${plan.targetUser}

FEATURES TO IMPLEMENT (all of these must work):
$features

SCREENS / SECTIONS:
$screens

DESIGN
Layout: ${template.name} — ${template.description}
Primary colour (headers, buttons, active nav): #$primary
Accent colour (highlights, icons, badges): #$accent
Background: #0D1117
Surface / card colour: #161B22
App icon: ${design.iconKey} — draw it with a clean inline SVG. Do not use emoji or Unicode pictograms as icons.
Navigation tabs and their icons: $tabs
Use the selected icons throughout; do not load an external icon library.

TECHNICAL REQUIREMENTS
1. Single HTML file — all CSS and JS inline, zero external dependencies
2. All user data stored in localStorage — works 100% offline
3. Mobile-first layout, max-width 460px, centred on larger screens
4. Dark theme using the exact colours above
5. Each screen listed above is a separate view (toggle display with JS)
${if (template.hasBottomNav) "6. Fixed bottom navigation bar with tab names: $tabs" else "6. Back buttons and clear section headers for navigation"}
7. Empty states for every list (e.g. \"No items yet — add your first one!\")
8. Working add / edit / delete for any data the app manages
9. Confirmation dialog before deleting anything
10. Clean card UI with 12px rounded corners and subtle box-shadows
11. Smooth 200ms fade transitions between views
12. Input validation with inline error messages
13. The app must fully implement every feature listed above

Use JavaScript toggles for navigation. Never use fetch, network requests, external assets, iframes or native Android APIs. Implement only on-device features; no scheduled notifications or background tasks.

Output the complete HTML file starting with <!DOCTYPE html>
        """.trimIndent()
    }

    private fun buildReviewPrompt(html: String, plan: ProjectPlan): String {
        val features = plan.features.joinToString(", ")
        return """
Review the following web app HTML and output a corrected, complete version.

Check for:
1. All features implemented and working: $features
2. localStorage save/load functioning correctly on every screen
3. No broken JavaScript (unclosed brackets, undefined variables, missing handlers)
4. All navigation transitions working
5. Empty states shown when lists are empty
6. Delete confirmation working
7. Add/edit forms actually saving data

Output ONLY the corrected HTML starting with <!DOCTYPE html>. No explanation.

--- HTML TO REVIEW ---
$html
        """.trimIndent()
    }


    private suspend fun callApi(prompt: String, apiKey: String, model: String, provider: CloudProvider): String {
        if (provider == CloudProvider.GEMINI) return api.generate(model, apiKey, JSONArray().put(JSONObject().put("role", "user")
            .put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
        val output = StringBuilder()
        CloudProviderApi(api).stream(provider, model, apiKey, listOf(com.buildai.app.data.model.Message(role = com.buildai.app.data.model.MessageRole.USER, content = prompt)), "Return only the requested complete HTML.", maxTokens = 8192).collect { output.append(it) }
        return output.toString()
    }

    private fun colorHex(argb: Long): String = "%06X".format(argb and 0xFFFFFF)
}
