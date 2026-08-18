package com.dhruw.autoflow.data.local

import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.data.repository.ExecutionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryExecutionRepository(
    private val maxEntries: Int = 200
) : ExecutionRepository {

    private val state = MutableStateFlow<List<Execution>>(emptyList())

    override val executions: StateFlow<List<Execution>> = state.asStateFlow()

    override suspend fun record(execution: Execution) {
        state.update { current ->
            val without = current.filterNot { it.id == execution.id }
            (listOf(execution) + without)
                .sortedByDescending { it.startedAt }
                .take(maxEntries)
        }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}
