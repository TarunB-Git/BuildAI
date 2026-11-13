package com.buildai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.buildai.app.data.model.DesignDefaults
import com.buildai.app.data.model.DesignSpec
import com.buildai.app.data.model.TabSpec
import com.buildai.app.data.storage.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DesignViewModel(
    app: Application,
    private val projects: ConversationRepository = ConversationRepository(app),
    initialProjectId: String? = null
) : AndroidViewModel(app) {
    private var projectId: String? = null
    private var draft = false

    private val _spec = MutableStateFlow(DesignSpec())
    val spec: StateFlow<DesignSpec> = _spec.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    init { bind(initialProjectId) }

    fun reportError(message: String) { _error.value = message }

    fun bind(id: String?, editDraft: Boolean = false) {
        draft = editDraft
        projectId = id
        _spec.value = id?.let { projects.load(it)?.designSpec } ?: DesignSpec()
        _error.value = null
    }

    private fun change(spec: DesignSpec) {
        try {
            if (!draft) projectId?.let { projects.saveDesign(it, spec) }
            _spec.value = spec
            _error.value = null
        } catch (_: Exception) { _error.value = "Couldn't save your design. Free some space and try again." }
    }

    // ── User actions ─────────────────────────────────────────────────────────

    fun selectTemplate(templateId: String) {
        val tmpl = DesignDefaults.template(templateId)
        change(_spec.value.copy(
            templateId = templateId,
            tabs = tmpl.defaultTabNames.mapIndexed { i, name ->
                TabSpec(name, DesignDefaults.defaultTabIcon(name, i))
            }
        ))
    }

    fun setNativeTabs(screens: List<String>) { change(_spec.value.copy(tabs = screens.take(5).mapIndexed { i, title -> TabSpec(title, DesignDefaults.defaultTabIcon(title, i)) })) }
    fun setNativeTabs(app: com.buildai.app.runtime.AppSpec, reset: Boolean = false) {
        val tabs = com.buildai.app.runtime.NativeNavigation.tabs(app, if (reset) DesignSpec(tabs = emptyList()) else _spec.value)
        if (tabs != _spec.value.tabs) change(_spec.value.copy(tabs = tabs))
    }

    fun selectPalette(paletteId: String) {
        change(_spec.value.copy(paletteId = paletteId))
    }

    fun selectIcon(iconKey: String) {
        change(_spec.value.copy(iconKey = iconKey))
    }

    fun updateTabName(index: Int, name: String) {
        val tabs = _spec.value.tabs.toMutableList()
        if (index in tabs.indices) tabs[index] = tabs[index].copy(name = name)
        change(_spec.value.copy(tabs = tabs))
    }

    fun updateTabIcon(index: Int, iconKey: String) {
        val tabs = _spec.value.tabs.toMutableList()
        if (index in tabs.indices) tabs[index] = tabs[index].copy(iconKey = iconKey)
        change(_spec.value.copy(tabs = tabs))
    }

    fun addTab() {
        val tabs = _spec.value.tabs
        if (tabs.size < 5) {
            change(_spec.value.copy(
                tabs = tabs + TabSpec("Tab ${tabs.size + 1}", "home")
            ))
        }
    }

    fun removeTab(index: Int) {
        val tabs = _spec.value.tabs.toMutableList()
        if (tabs.size > 1 && index in tabs.indices) {
            tabs.removeAt(index)
            change(_spec.value.copy(tabs = tabs))
        }
    }

    fun applyDraft(): Boolean = try {
        projectId?.let { projects.saveDesign(it, _spec.value) }
        _error.value = null
        true
    } catch (_: Exception) { _error.value = "Couldn't save your appearance. Free some space and try again."; false }

    fun reset() { change(DesignSpec()) }
}
