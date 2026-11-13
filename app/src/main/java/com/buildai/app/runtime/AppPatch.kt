package com.buildai.app.runtime

sealed interface PatchOperation {
    data class PutComponent(val screenId: String, val component: ComponentSpec) : PatchOperation
    data class RemoveComponent(val screenId: String, val componentId: String) : PatchOperation
    data class AddField(val entityId: String, val field: FieldSpec) : PatchOperation
    data class PutAction(val action: ActionSpec) : PatchOperation
    data class AddScreen(val screen: ScreenSpec) : PatchOperation
}
data class AppPatch(val baseRevisionId: String, val summary: String, val operations: List<PatchOperation>) {
    fun applyTo(original: AppSpec): AppSpec {
        require(summary.isNotBlank() && summary.length <= 500 && operations.size in 1..16)
        var spec = original
        operations.forEach { op ->
            spec = when (op) {
                is PatchOperation.PutComponent -> {
                    require(spec.screens.any { it.id == op.screenId })
                    spec.copy(screens = spec.screens.map { screen -> if (screen.id != op.screenId) screen else {
                        val exists = screen.components.any { it.id == op.component.id }
                        screen.copy(components = if (exists) screen.components.map { if (it.id == op.component.id) op.component else it } else screen.components + op.component)
                    } })
                }
                is PatchOperation.RemoveComponent -> {
                    require(spec.screens.any { it.id == op.screenId && it.components.any { c -> c.id == op.componentId } })
                    spec.copy(screens = spec.screens.map { if (it.id == op.screenId) it.copy(components = it.components.filterNot { c -> c.id == op.componentId }) else it })
                }
                is PatchOperation.AddField -> {
                    require(!op.field.required)
                    require(spec.entities.any { it.id == op.entityId && it.fields.none { f -> f.id == op.field.id } })
                    spec.copy(entities = spec.entities.map { if (it.id == op.entityId) it.copy(fields = it.fields + op.field) else it })
                }
                is PatchOperation.PutAction -> spec.copy(actions = if (spec.actions.any { it.id == op.action.id }) spec.actions.map { if (it.id == op.action.id) op.action else it } else spec.actions + op.action)
                is PatchOperation.AddScreen -> { require(spec.screens.none { it.id == op.screen.id }); spec.copy(screens = spec.screens + op.screen) }
            }
        }
        AppSpecValidator.validate(spec); AppSpecValidator.additive(original, spec)
        return spec
    }
}
