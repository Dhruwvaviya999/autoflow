package com.dhruw.autoflow.data.repository

import com.dhruw.autoflow.automation.model.Execution
import kotlinx.coroutines.flow.StateFlow

/** Storage for execution history. */
interface ExecutionRepository {

    /** All recorded executions, newest first. */
    val executions: StateFlow<List<Execution>>

    /** Insert, or update an existing execution with the same id. */
    suspend fun record(execution: Execution)

    suspend fun clear()
}
