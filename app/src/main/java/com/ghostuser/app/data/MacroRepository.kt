package com.ghostuser.app.data

import android.content.Context
import com.ghostuser.app.model.Macro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Single source of truth for saved macros, backed by one JSON file in the app's
 * private storage. A process-wide singleton so the overlay (running inside the
 * accessibility service) and the UI stay in sync without IPC.
 */
object MacroRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var file: File

    private val _macros = MutableStateFlow<List<Macro>>(emptyList())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    /** Idempotent; safe to call from Application.onCreate. */
    @Synchronized
    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "macros.json")
        _macros.value = load()
    }

    private fun load(): List<Macro> = try {
        if (file.exists()) json.decodeFromString(file.readText()) else emptyList()
    } catch (t: Throwable) {
        emptyList()
    }

    fun get(id: String): Macro? = _macros.value.firstOrNull { it.id == id }

    /** Insert or replace by id, then persist. */
    fun upsert(macro: Macro) {
        val current = _macros.value
        val idx = current.indexOfFirst { it.id == macro.id }
        _macros.value = if (idx >= 0) {
            current.toMutableList().also { it[idx] = macro }
        } else {
            current + macro
        }
        persist()
    }

    fun delete(id: String) {
        _macros.value = _macros.value.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        val snapshot = _macros.value
        ioScope.launch {
            try {
                file.writeText(json.encodeToString(snapshot))
            } catch (_: Throwable) {
                // Best-effort; in-memory state remains the source of truth for
                // this session even if the write fails.
            }
        }
    }

    fun newId(): String = "macro_" + System.currentTimeMillis().toString(36)

    /**
     * Appends imported macros, always assigning fresh unique ids so an import can
     * never overwrite an existing macro (even if the file was exported from this
     * same device). Returns how many were added.
     */
    fun importMacros(incoming: List<Macro>): Int {
        if (incoming.isEmpty()) return 0
        val used = _macros.value.map { it.id }.toMutableSet()
        var counter = 0
        fun uniqueId(): String {
            var id: String
            do {
                id = "macro_" + System.currentTimeMillis().toString(36) + "_" + (counter++)
            } while (!used.add(id))
            return id
        }
        _macros.value = _macros.value + incoming.map { it.copy(id = uniqueId()) }
        persist()
        return incoming.size
    }
}
