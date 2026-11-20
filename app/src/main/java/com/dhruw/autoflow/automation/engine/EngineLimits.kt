package com.dhruw.autoflow.automation.engine

/**
 * Global safeguards for automation execution. Centralized so the validator,
 * the engine and the UI all agree on the same numbers, and so a workflow can
 * never wedge the device: every wait is bounded, every retry is counted and
 * every stored list has a ceiling.
 */
object EngineLimits {

    /** Hard ceiling for one automation run, including nested branches. */
    const val MAX_RUN_MILLIS: Long = 10 * 60 * 1000

    /** Steps in one automation's THEN list. */
    const val MAX_ACTIONS = 50

    /** How deeply If / else steps may nest. */
    const val MAX_BRANCH_DEPTH = 3

    /** How deeply And/Or/Not condition groups may nest. */
    const val MAX_CONDITION_DEPTH = 3

    /** Steps inside one UI automation action. */
    const val MAX_UI_STEPS = 30

    /** Bounds for a UI automation's overall timeout. */
    const val MIN_UI_TIMEOUT_MILLIS: Long = 1_000
    const val MAX_UI_TIMEOUT_MILLIS: Long = 300_000

    /** Longest a single Delay step may wait. */
    const val MAX_DELAY_MILLIS: Long = 3_600_000

    /** Bounds for a Speak step's pitch and speech rate. */
    const val MIN_SPEECH_RATIO = 0.1f
    const val MAX_SPEECH_RATIO = 2.0f

    /** Bounds for a Speak step's own volume, relative to its audio stream. */
    const val MIN_SPEAK_VOLUME = 0.0f
    const val MAX_SPEAK_VOLUME = 1.0f

    /**
     * Longest one utterance may hold a Speak step open. A queued utterance
     * also waits for whatever is already speaking, so this covers that wait:
     * without it a callback the engine never delivers would wedge every later
     * Speak step behind it.
     */
    const val MAX_SPEAK_MILLIS = 120_000L

    /**
     * Longest text a Speak step may hold. The platform engine rejects an
     * utterance over `TextToSpeech.getMaxSpeechInputLength()` (4000 on every
     * supported level), so a longer one would only fail at run time.
     */
    const val MAX_SPEAK_TEXT_LENGTH = 4000

    /** Retries for steps that are safe to repeat, and the pause between them. */
    const val MAX_RETRIES = 2
    const val RETRY_DELAY_MILLIS: Long = 2_000

    /**
     * Newest executions kept in history; older rows are pruned on every
     * write. Kept modest because the whole list is observed in memory for the
     * History screen and the health calculation.
     */
    const val MAX_HISTORY_ROWS = 200

    /** Newest notification records kept (opt-in Save notification action). */
    const val MAX_NOTIFICATION_RECORDS = 500

    /** Runs of the same automation that may be queued at once. */
    const val MAX_QUEUED_RUNS_PER_AUTOMATION = 1
}
