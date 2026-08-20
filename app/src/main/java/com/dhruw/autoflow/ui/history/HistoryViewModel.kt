package com.dhruw.autoflow.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.model.Execution
import com.dhruw.autoflow.automation.model.ExecutionStatus
import com.dhruw.autoflow.data.repository.ExecutionRepository
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Status filter for the history list. */
enum class HistoryFilter(val label: String, val status: ExecutionStatus?) {
    ALL("All", null),
    SUCCESS("Success", ExecutionStatus.SUCCESS),
    FAILED("Failed", ExecutionStatus.FAILED),
    CANCELLED("Cancelled", ExecutionStatus.CANCELLED),
    SKIPPED("Skipped", ExecutionStatus.SKIPPED)
}

/** Runs that started on the same day, newest day first. */
data class HistoryGroup(
    val label: String,
    val executions: List<Execution>
)

data class HistoryUiState(
    val groups: List<HistoryGroup> = emptyList(),
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val totalCount: Int = 0
) {
    val shownCount: Int get() = groups.sumOf { it.executions.size }
    val isFiltered: Boolean get() = query.isNotBlank() || filter != HistoryFilter.ALL
}

class HistoryViewModel(
    private val executionRepository: ExecutionRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> =
        combine(
            executionRepository.executions,
            query,
            filter
        ) { executions, currentQuery, currentFilter ->
            val matching = executions.filter { it.matches(currentQuery, currentFilter) }
            HistoryUiState(
                groups = groupByDay(matching),
                query = currentQuery,
                filter = currentFilter,
                totalCount = executions.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    /** Live view of one execution, so a running automation updates in place. */
    fun execution(id: String): StateFlow<Execution?> =
        executionRepository.executions
            .map { list -> list.firstOrNull { it.id == id } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun groupByDay(executions: List<Execution>): List<HistoryGroup> {
        if (executions.isEmpty()) return emptyList()
        val now = clock()
        return executions
            .groupBy { startOfDay(it.startedAt) }
            .toSortedMap(compareByDescending { it })
            .map { (dayStart, runs) ->
                HistoryGroup(label = dayLabel(dayStart, now), executions = runs)
            }
    }

    private fun startOfDay(timestamp: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun dayLabel(dayStart: Long, now: Long): String {
        val today = startOfDay(now)
        val yesterday = today - DAY_MILLIS
        return when (dayStart) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.getDefault())
                .format(java.util.Date(dayStart))
        }
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AutoFlowApplication).container
                HistoryViewModel(container.executionRepository)
            }
        }
    }
}

private fun Execution.matches(query: String, filter: HistoryFilter): Boolean {
    val matchesFilter = filter.status == null || status == filter.status
    if (!matchesFilter) return false
    if (query.isBlank()) return true
    val needle = query.trim()
    return automationName.contains(needle, ignoreCase = true) ||
        message?.contains(needle, ignoreCase = true) == true
}
