package com.dhruw.autoflow.instagram

import com.dhruw.autoflow.instagram.model.InstagramAnalysisResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the most recent analysis for the UI. Both the manual analyzer and
 * the automation action publish here, so results reach the same screens no
 * matter which path produced them. In-memory by design: analysis results
 * are cheap to regenerate from the export file.
 */
class InstagramAnalysisStore {

    private val _latest = MutableStateFlow<InstagramAnalysisResult?>(null)
    val latest: StateFlow<InstagramAnalysisResult?> = _latest.asStateFlow()

    fun publish(result: InstagramAnalysisResult) {
        _latest.value = result
    }
}
