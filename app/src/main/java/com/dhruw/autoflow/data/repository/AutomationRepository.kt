package com.dhruw.autoflow.data.repository

import com.dhruw.autoflow.automation.model.Automation
import kotlinx.coroutines.flow.StateFlow

/**
 * Storage for automations. The in-memory implementation can be replaced by
 * Room without touching UI, ViewModels, or the engine.
 */
interface AutomationRepository {

    /** All automations, newest first. */
    val automations: StateFlow<List<Automation>>

    suspend fun getById(id: String): Automation?

    /** Direct fetch for background workers that can't rely on the flow cache. */
    suspend fun getAll(): List<Automation>

    suspend fun upsert(automation: Automation)

    suspend fun delete(id: String)

    suspend fun setEnabled(id: String, enabled: Boolean)

    suspend fun markRun(id: String, timestamp: Long)
}
