package com.ghostuser.app.data

import com.ghostuser.app.model.Macro
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * On-disk / shareable envelope for exported macros. Versioned so future formats
 * can be detected. Import is lenient — see [MacroTransfer.parse].
 */
@Serializable
data class MacroBundle(
    val app: String = "GhostUser",
    val version: Int = 1,
    val macros: List<Macro> = emptyList(),
)

/** Serializes macros for export and parses them back on import. */
object MacroTransfer {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun export(macros: List<Macro>): String = json.encodeToString(MacroBundle(macros = macros))

    /**
     * Accepts, in order of preference: a [MacroBundle], a bare list of macros, or
     * a single macro object. Returns an empty list if none of those parse.
     */
    fun parse(text: String): List<Macro> {
        runCatching { return json.decodeFromString<MacroBundle>(text).macros }
        runCatching { return json.decodeFromString<List<Macro>>(text) }
        runCatching { return listOf(json.decodeFromString<Macro>(text)) }
        return emptyList()
    }
}
