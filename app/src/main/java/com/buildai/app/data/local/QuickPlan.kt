package com.buildai.app.data.local

import com.buildai.app.data.model.AppType
import com.buildai.app.data.model.Complexity
import com.buildai.app.data.model.ProjectPlan

/** A small, honest plan when a phone-sized model cannot return structured planning JSON. */
object QuickPlan {
    fun fromIdea(idea: String): ProjectPlan {
        val normalized = idea.trim().replace(Regex("\\s+"), " ").ifBlank { "A private offline app" }
        val description = if (normalized.length > 1500) normalized.take(650) + " Latest details: " + normalized.takeLast(800) else normalized
        val lower = description.lowercase()
        val category = when {
            "calculator" in lower || "calculate" in lower -> "Calculator" to "My Calculator"
            "timer" in lower || "pomodoro" in lower -> "Timer" to "Focus Timer"
            "habit" in lower -> "Habits" to "Habit Tracker"
            "recipe" in lower || "cook" in lower -> "Recipes" to "Recipe Book"
            "journal" in lower || "diary" in lower -> "Journal entries" to "My Journal"
            "task" in lower || "todo" in lower || "to-do" in lower -> "Tasks" to "Task List"
            "note" in lower -> "Notes" to "My Notes"
            "workout" in lower || "fitness" in lower -> "Workouts" to "Workout Log"
            "expense" in lower || "budget" in lower -> "Expenses" to "Expense Tracker"
            else -> "Items" to "My Local App"
        }
        val template = com.buildai.app.data.model.QuickTemplate.suggest(description)
        return ProjectPlan(
            appName = category.second,
            description = description,
            type = AppType.APP,
            features = listOf("Implement this workflow: $description", "Keep required interactions working offline"),
            screens = if (category.first == "Journal entries" || category.first == "Habits")
                listOf("Home", "History") else listOf("Home"),
            targetUser = "You",
            complexity = Complexity.SIMPLE,
            estimatedBuildTime = "A few minutes",
            quickTemplate = template
        )
    }
}
