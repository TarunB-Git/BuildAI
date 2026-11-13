package com.buildai.app.data.model

enum class BuildMode { CUSTOM, TEMPLATE }

enum class QuickTemplate(val label: String, val features: List<String>) {
    NOTES("Notes / journal", listOf("Write, edit and delete entries", "Search saved entries", "Save entries on this device")),
    TASKS("Task checklist", listOf("Add and edit tasks", "Mark tasks complete", "Save tasks on this device")),
    HABITS("Habit tracker", listOf("Add habits", "Mark each habit done for today", "See completion history")),
    EXPENSES("Expense tracker", listOf("Record amounts and categories", "See total spending", "Edit and delete expenses")),
    CALCULATOR("Calculator", listOf("Calculate with two numbers", "Add, subtract, multiply and divide", "Show the result")),
    TIMER("Focus timer", listOf("Set the duration", "Start, pause and reset the timer", "Show remaining time while the app is open"));

    companion object {
        fun suggest(idea: String): QuickTemplate {
            val text = idea.lowercase()
            return when {
                Regex("calculat|convert|math").containsMatchIn(text) -> CALCULATOR
                Regex("timer|pomodoro|focus session").containsMatchIn(text) -> TIMER
                Regex("expense|budget|spending").containsMatchIn(text) -> EXPENSES
                Regex("habit|daily tracker").containsMatchIn(text) -> HABITS
                Regex("task|checklist|to-do|todo").containsMatchIn(text) -> TASKS
                else -> NOTES
            }
        }
    }
}
