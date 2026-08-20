package com.dhruw.autoflow.data.repository

import android.util.Log
import com.dhruw.autoflow.automation.engine.EngineLimits
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.data.local.converters.WorkflowJsonException
import com.dhruw.autoflow.data.local.converters.toDomain
import com.dhruw.autoflow.data.local.converters.toEntity
import com.dhruw.autoflow.data.local.dao.ExecutionDao
import com.dhruw.autoflow.data.local.entity.ExecutionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Room-backed [ExecutionRepository]. History is intentionally kept when an
 * automation is deleted ([Execution.automationName] is denormalized for
 * exactly that case). Capped at [maxEntries] newest rows.
 */
class RoomExecutionRepository(
    private val dao: ExecutionDao,
    scope: CoroutineScope,
    private val maxEntries: Int = EngineLimits.MAX_HISTORY_ROWS
) : ExecutionRepository {

    override val executions: StateFlow<List<Execution>> =
        dao.observeAll()
            .map { entities -> entities.mapNotNull { it.toDomainOrNull() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun record(execution: Execution) {
        dao.upsert(execution.toEntity())
        dao.prune(maxEntries)
    }

    override suspend fun clear() {
        dao.clear()
    }

    private fun ExecutionEntity.toDomainOrNull(): Execution? = try {
        toDomain()
    } catch (e: WorkflowJsonException) {
        Log.w(TAG, "Dropping malformed execution row $id", e)
        null
    }

    private companion object {
        const val TAG = "AutoFlowRepo"
    }
}
