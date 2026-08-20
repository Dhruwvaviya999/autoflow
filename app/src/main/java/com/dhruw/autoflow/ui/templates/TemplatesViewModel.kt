package com.dhruw.autoflow.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.transfer.AutomationTemplate
import java.util.UUID
import kotlinx.coroutines.launch

class TemplatesViewModel(
    private val repository: AutomationRepository
) : ViewModel() {

    /**
     * Stores the template as a new, switched-off automation and hands its id
     * back so the caller can open the editor on it.
     */
    fun create(template: AutomationTemplate, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            val existingNames = repository.getAll().map { it.name }.toSet()
            repository.upsert(
                template.build().copy(
                    id = id,
                    name = uniqueName(template.name, existingNames),
                    enabled = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
            onCreated(id)
        }
    }

    private fun uniqueName(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        var index = 2
        while ("$name $index" in existing) index++
        return "$name $index"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as AutoFlowApplication).container
                TemplatesViewModel(container.automationRepository)
            }
        }
    }
}
