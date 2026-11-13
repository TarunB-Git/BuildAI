package com.buildai.app.runtime

/** Saved navigation state carries identity; values always come from live records. */
object SelectedRecord {
    fun resolve(snapshot: Map<String, Any>, records: Map<String, List<Map<String, Any>>>, entityId: String? = null): Pair<String, Map<String, Any>>? {
        val id = snapshot["id"]?.toString() ?: return null
        val matches = records.filterKeys { entityId == null || it == entityId }.flatMap { (entity, rows) ->
            rows.filter { it["id"]?.toString() == id }.map { entity to it }
        }
        return matches.singleOrNull()
    }
}
