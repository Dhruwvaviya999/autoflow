package com.dhruw.autoflow.data.local

import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.data.repository.AutomationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-lifetime storage. Data is lost when the process dies — accepted
 * Phase 2 limitation until Room replaces this behind the same interface.
 */
class InMemoryAutomationRepository : AutomationRepository {

    private val state = MutableStateFlow<List<Automation>>(emptyList())

    override val automations: StateFlow<List<Automation>> = state.asStateFlow()

    override suspend fun getById(id: String): Automation? =
        state.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<Automation> = state.value

    override suspend fun upsert(automation: Automation) {
        state.update { current ->
            val without = current.filterNot { it.id == automation.id }
            (without + automation).sortedByDescending { it.createdAt }
        }
    }

    override suspend fun delete(id: String) {
        state.update { current -> current.filterNot { it.id == id } }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        state.update { current ->
            current.map { if (it.id == id) it.copy(enabled = enabled) else it }
        }
    }

    override suspend fun markRun(id: String, timestamp: Long) {
        state.update { current ->
            current.map { if (it.id == id) it.copy(lastRunAt = timestamp) else it }
        }
    }
}
