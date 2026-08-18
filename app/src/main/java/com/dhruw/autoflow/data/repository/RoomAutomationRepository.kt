package com.dhruw.autoflow.data.repository

import android.util.Log
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.data.local.converters.WorkflowJsonException
import com.dhruw.autoflow.data.local.converters.toDomain
import com.dhruw.autoflow.data.local.converters.toEntity
import com.dhruw.autoflow.data.local.dao.AutomationDao
import com.dhruw.autoflow.data.local.entity.AutomationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Room-backed [AutomationRepository]. The [automations] StateFlow is a cache
 * of the database flow (single source of truth stays in Room). Rows whose
 * workflow JSON cannot be decoded are dropped with a warning instead of
 * crashing the app.
 */
class RoomAutomationRepository(
    private val dao: AutomationDao,
    scope: CoroutineScope
) : AutomationRepository {

    override val automations: StateFlow<List<Automation>> =
        dao.observeAll()
            .map { entities -> entities.mapNotNull { it.toDomainOrNull() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun getById(id: String): Automation? =
        dao.getById(id)?.toDomainOrNull()

    override suspend fun getAll(): List<Automation> =
        dao.getAll().mapNotNull { it.toDomainOrNull() }

    override suspend fun upsert(automation: Automation) {
        dao.upsert(automation.toEntity())
    }

    override suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }

    override suspend fun markRun(id: String, timestamp: Long) {
        dao.markRun(id, timestamp)
    }

    private fun AutomationEntity.toDomainOrNull(): Automation? = try {
        toDomain()
    } catch (e: WorkflowJsonException) {
        Log.w(TAG, "Dropping malformed automation row $id", e)
        null
    }

    private companion object {
        const val TAG = "AutoFlowRepo"
    }
}
