package com.dhruw.autoflow.automation.processor

import java.io.InputStream

/**
 * Input handed to a processor. [openStream] can be called more than once —
 * each call must return a fresh stream positioned at the start.
 */
class ProcessorInput(
    val name: String,
    val sizeBytes: Long,
    val openStream: () -> InputStream
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()
}

/** Structured outcome; processors never throw for expected failures. */
sealed interface ProcessingResult<out T> {
    data class Success<T>(val data: T) : ProcessingResult<T>
    data class Failure(val message: String, val cause: Throwable? = null) : ProcessingResult<Nothing>
}

/**
 * Turns a file into structured data. Implementations stay free of Android
 * UI concerns; future processors (PDF, CSV, ...) plug in beside
 * InstagramDataProcessor without touching the automation engine.
 */
interface FileProcessor<T> {
    /** Human-readable name for logs and errors. */
    val name: String

    suspend fun process(input: ProcessorInput): ProcessingResult<T>
}
