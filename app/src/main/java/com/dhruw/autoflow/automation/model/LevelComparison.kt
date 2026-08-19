package com.dhruw.autoflow.automation.model

/** Numeric comparison shared by battery triggers and conditions. */
enum class LevelComparison {
    LESS_THAN,
    LESS_OR_EQUAL,
    GREATER_THAN,
    GREATER_OR_EQUAL;

    fun matches(value: Int, threshold: Int): Boolean = when (this) {
        LESS_THAN -> value < threshold
        LESS_OR_EQUAL -> value <= threshold
        GREATER_THAN -> value > threshold
        GREATER_OR_EQUAL -> value >= threshold
    }
}

val LevelComparison.label: String
    get() = when (this) {
        LevelComparison.LESS_THAN -> "Below"
        LevelComparison.LESS_OR_EQUAL -> "At or below"
        LevelComparison.GREATER_THAN -> "Above"
        LevelComparison.GREATER_OR_EQUAL -> "At or above"
    }

/** Connection direction shared by Wi-Fi, Bluetooth, and headset triggers. */
enum class ConnectionEvent { CONNECTED, DISCONNECTED }

val ConnectionEvent.label: String
    get() = when (this) {
        ConnectionEvent.CONNECTED -> "Connects"
        ConnectionEvent.DISCONNECTED -> "Disconnects"
    }
