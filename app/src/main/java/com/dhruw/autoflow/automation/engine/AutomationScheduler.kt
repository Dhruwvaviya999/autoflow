package com.dhruw.autoflow.automation.engine

import com.dhruw.autoflow.automation.model.Automation

/**
 * Schedules automations with time-based triggers. The production
 * implementation is WorkManager-backed; the domain and UI only ever see
 * this interface.
 */
interface AutomationScheduler {

    /** (Re)schedule the automation if its trigger is time-based; no-op otherwise. */
    fun schedule(automation: Automation)

    fun cancel(automationId: String)
}
