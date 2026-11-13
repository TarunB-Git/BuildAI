package com.buildai.app.runtime

/** Gate fixtures compose capabilities directly. The builder must not select these as idea templates. */
object ManualExamples {
    fun notes() = AppSpec("Study notes", "home", listOf(
        ScreenSpec("home", "Notes", listOf(
            ComponentSpec("note", ComponentKind.INPUT, "Note"),
            ComponentSpec("tag", ComponentKind.INPUT, "Tag"),
            ComponentSpec("save", ComponentKind.BUTTON, "Save note", actionId = "saveNote"),
            ComponentSpec("search", ComponentKind.INPUT, "Search notes"),
            ComponentSpec("notes", ComponentKind.RECORD_LIST, "Saved notes", entityId = "notes", expression = Expr.input("search"), rowActions = listOf("deleteNote")),
            ComponentSpec("count", ComponentKind.SUMMARY, "Notes saved", expression = Expr(ExpressionOp.COUNT, "notes"))
        )),
        ScreenSpec("quiz", "Quiz", listOf(ComponentSpec("practice", ComponentKind.QUIZ, "Study quiz", questions = listOf(
            QuizQuestion("questionOne", "What is 2 + 2?", listOf("3", "4", "5"), 1)
        ))))
    ), listOf(EntitySpec("notes", listOf(FieldSpec("body", "Note", required = true), FieldSpec("tag", "Tag")))), listOf(
        ActionSpec("saveNote", ActionKind.INSERT, "Save note", "notes", mapOf("body" to Expr.input("note"), "tag" to Expr.input("tag"))),
        ActionSpec("deleteNote", ActionKind.DELETE, "Delete note", "notes", recordId = Expr(ExpressionOp.RECORD, "id"))
    ))
    fun budget() = AppSpec("Trip budget", "home", listOf(ScreenSpec("home", "Budget", listOf(
        ComponentSpec("budget", ComponentKind.INPUT, "Budget", FieldKind.NUMBER),
        ComponentSpec("description", ComponentKind.INPUT, "Expense"),
        ComponentSpec("amount", ComponentKind.INPUT, "Amount", FieldKind.NUMBER),
        ComponentSpec("add", ComponentKind.BUTTON, "Add expense", actionId = "addExpense"),
        ComponentSpec("spent", ComponentKind.SUMMARY, "Spent", expression = Expr(ExpressionOp.SUM, "expenses.amount")),
        ComponentSpec("remaining", ComponentKind.SUMMARY, "Remaining", expression = Expr(ExpressionOp.SUBTRACT, args = listOf(Expr.input("budget"), Expr(ExpressionOp.SUM, "expenses.amount")))),
        ComponentSpec("expenses", ComponentKind.RECORD_LIST, "Expenses", entityId = "expenses", rowActions = listOf("removeExpense"))
    ))), listOf(EntitySpec("expenses", listOf(FieldSpec("description", "Expense", required = true), FieldSpec("amount", "Amount", FieldKind.NUMBER, true)))), listOf(
        ActionSpec("addExpense", ActionKind.INSERT, "Add expense", "expenses", mapOf("description" to Expr.input("description"), "amount" to Expr.input("amount"))),
        ActionSpec("removeExpense", ActionKind.DELETE, "Remove expense", "expenses", recordId = Expr(ExpressionOp.RECORD, "id"))
    ))
    fun routine() = AppSpec("Focus routine", "home", listOf(ScreenSpec("home", "Routine", listOf(
        ComponentSpec("task", ComponentKind.INPUT, "Routine step"),
        ComponentSpec("save", ComponentKind.BUTTON, "Add step", actionId = "saveStep"),
        ComponentSpec("steps", ComponentKind.RECORD_LIST, "Routine steps", entityId = "steps", rowActions = listOf("completeStep")),
        ComponentSpec("seconds", ComponentKind.INPUT, "Focus seconds", FieldKind.NUMBER),
        ComponentSpec("start", ComponentKind.BUTTON, "Start focus", actionId = "startFocus"),
        ComponentSpec("focus", ComponentKind.TIMER, "Focus remaining"),
        ComponentSpec("stop", ComponentKind.BUTTON, "Stop focus", actionId = "stopFocus")
    ))), listOf(EntitySpec("steps", listOf(FieldSpec("title", "Step", required = true), FieldSpec("complete", "Complete", FieldKind.BOOLEAN)))), listOf(
        ActionSpec("saveStep", ActionKind.INSERT, "Add step", "steps", mapOf("title" to Expr.input("task"), "complete" to Expr(ExpressionOp.BOOLEAN, "false"))),
        ActionSpec("completeStep", ActionKind.UPDATE, "Complete step", "steps", mapOf("complete" to Expr(ExpressionOp.BOOLEAN, "true")), Expr(ExpressionOp.RECORD, "id")),
        ActionSpec("startFocus", ActionKind.START_TIMER, "Start focus", "focus", duration = Expr.input("seconds")),
        ActionSpec("stopFocus", ActionKind.STOP_TIMER, "Stop focus", "focus")
    ))
}
