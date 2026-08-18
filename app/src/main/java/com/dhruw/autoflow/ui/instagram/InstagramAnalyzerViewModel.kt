package com.dhruw.autoflow.ui.instagram

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.processor.ProcessingResult
import com.dhruw.autoflow.automation.processor.ProcessorInput
import com.dhruw.autoflow.instagram.InstagramAnalysisStore
import com.dhruw.autoflow.instagram.InstagramDataProcessor
import com.dhruw.autoflow.instagram.InstagramFollowAnalyzer
import com.dhruw.autoflow.instagram.model.InstagramAccount
import com.dhruw.autoflow.instagram.model.InstagramAnalysisResult
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelectedExport(
    val uri: String,
    val name: String,
    val sizeBytes: Long
)

data class InstagramUiState(
    val selected: SelectedExport? = null,
    val analyzing: Boolean = false,
    val result: InstagramAnalysisResult? = null,
    val error: String? = null,
    // Results screen state
    val query: String = "",
    val sortAscending: Boolean = true
) {
    val canAnalyze: Boolean get() = selected != null && !analyzing

    val filteredNotFollowingBack: List<InstagramAccount>
        get() {
            val list = result?.notFollowingBack ?: return emptyList()
            val q = query.trim().removePrefix("@").lowercase()
            val filtered = if (q.isEmpty()) list else list.filter {
                it.username.lowercase().contains(q)
            }
            return if (sortAscending) filtered else filtered.asReversed()
        }
}

class InstagramAnalyzerViewModel(
    private val application: Application,
    private val processor: InstagramDataProcessor,
    private val analyzer: InstagramFollowAnalyzer,
    private val store: InstagramAnalysisStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        // An automation run may already have produced a result; show it.
        InstagramUiState(result = store.latest.value)
    )
    val uiState: StateFlow<InstagramUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages

    fun onFileSelected(uri: Uri) {
        val resolver = application.contentResolver
        var name = uri.lastPathSegment ?: "export"
        var size = -1L
        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        } catch (e: SecurityException) {
            _uiState.update { it.copy(error = "Could not access the selected file") }
            return
        }
        _uiState.update {
            it.copy(
                selected = SelectedExport(uri.toString(), name, size),
                error = null
            )
        }
    }

    fun analyze() {
        val selected = _uiState.value.selected ?: return
        _uiState.update { it.copy(analyzing = true, error = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val input = ProcessorInput(
                    name = selected.name,
                    sizeBytes = selected.sizeBytes,
                    openStream = {
                        application.contentResolver.openInputStream(Uri.parse(selected.uri))
                            ?: throw IOException("File is no longer accessible")
                    }
                )
                try {
                    when (val processed = processor.process(input)) {
                        is ProcessingResult.Failure -> Err(processed.message)
                        is ProcessingResult.Success -> Ok(analyzer.analyze(processed.data))
                    }
                } catch (e: FileNotFoundException) {
                    Err("The selected file no longer exists")
                } catch (e: SecurityException) {
                    Err("Permission to the selected file was revoked")
                } catch (e: IOException) {
                    Err("Could not read the selected file")
                }
            }
            when (outcome) {
                is Ok -> {
                    store.publish(outcome.value)
                    _uiState.update {
                        it.copy(analyzing = false, result = outcome.value, query = "")
                    }
                }
                is Err -> _uiState.update {
                    it.copy(analyzing = false, error = outcome.message)
                }
            }
        }
    }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun toggleSort() = _uiState.update { it.copy(sortAscending = !it.sortAscending) }

    fun clearSelection() = _uiState.update { it.copy(selected = null, error = null) }

    /** Writes the current result list into a user-chosen document. */
    fun export(target: Uri, asCsv: Boolean) {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    application.contentResolver.openOutputStream(target)?.use { stream ->
                        stream.bufferedWriter().use { writer ->
                            if (asCsv) {
                                writer.appendLine("username,profile_url")
                                result.notFollowingBack.forEach { account ->
                                    writer.appendLine(
                                        "${csv(account.username)},${csv(account.profileUrl ?: "")}"
                                    )
                                }
                            } else {
                                result.notFollowingBack.forEach { writer.appendLine(it.username) }
                            }
                        }
                        true
                    } ?: false
                } catch (e: IOException) {
                    false
                } catch (e: SecurityException) {
                    false
                }
            }
            _messages.emit(
                if (outcome) "Exported ${result.notFollowingBackCount} usernames"
                else "Export failed — could not write to the selected location"
            )
        }
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private sealed interface Outcome
    private data class Ok(val value: InstagramAnalysisResult) : Outcome
    private data class Err(val message: String) : Outcome

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AutoFlowApplication
                InstagramAnalyzerViewModel(
                    application = app,
                    processor = app.container.instagramProcessor,
                    analyzer = app.container.instagramAnalyzer,
                    store = app.container.instagramAnalysisStore
                )
            }
        }
    }
}
