package com.dhruw.autoflow.services.system

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dhruw.autoflow.automation.engine.SystemStateTracker
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.Automation
import com.dhruw.autoflow.automation.model.Condition
import com.dhruw.autoflow.automation.model.ConnectionEvent
import com.dhruw.autoflow.automation.model.LevelComparison
import com.dhruw.autoflow.automation.model.SystemEvent
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.data.local.AutoFlowDatabase
import com.dhruw.autoflow.data.repository.RoomAutomationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device coverage for Phase 6 plumbing: Room persistence of system
 * workflows, monitor lifecycle, and the Android device-state provider.
 * Deliberately avoids asserting on the physical environment (no "Wi-Fi must
 * be connected") — only on values being sane and registration not crashing.
 */
@RunWith(AndroidJUnit4::class)
class SystemIntegrationTest {

    private lateinit var database: AutoFlowDatabase
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AutoFlowDatabase::class.java
        ).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
        database.close()
    }

    @Test
    fun systemTriggerAutomationPersistsAndRoundTrips() = runBlocking {
        val repository = RoomAutomationRepository(database.automationDao(), scope)
        val automation = Automation(
            id = "sys-1",
            name = "Low battery warning",
            enabled = true,
            trigger = Trigger.BatteryLevelTrigger(LevelComparison.LESS_OR_EQUAL, 20),
            conditions = listOf(
                Condition.NotCondition(Condition.IsChargingCondition(charging = true)),
                Condition.WiFiConnectedCondition("Home")
            ),
            actions = listOf(Action.ShowNotificationAction("Battery", "Below 20%")),
            createdAt = 1L,
            updatedAt = 2L
        )

        repository.upsert(automation)
        assertEquals(automation, repository.getById("sys-1"))

        val bluetooth = automation.copy(
            id = "sys-2",
            trigger = Trigger.BluetoothConnectionTrigger(
                ConnectionEvent.CONNECTED, "AA:BB:CC:DD:EE:FF", "Car"
            ),
            conditions = emptyList()
        )
        repository.upsert(bluetooth)
        assertEquals(bluetooth, repository.getById("sys-2"))

        val boot = automation.copy(id = "sys-3", trigger = Trigger.DeviceBootTrigger, conditions = emptyList())
        repository.upsert(boot)
        assertEquals(boot, repository.getById("sys-3"))
    }

    @Test
    fun monitorHubStartsAndStopsWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val tracker = SystemStateTracker()
        val events = mutableListOf<SystemEvent>()
        val hub = SystemMonitorHub(
            listOf(
                BatteryMonitor(context, tracker, events::add),
                NetworkMonitor(context, tracker, events::add),
                ScreenMonitor(context, tracker, events::add),
                AudioDeviceMonitor(context, tracker, events::add)
            )
        )

        hub.start()
        hub.start() // idempotent
        hub.stop()
        hub.stop()

        // Registration must only seed baselines, never emit synthetic
        // transitions at startup (process-recreation safety).
        assertTrue("startup must not fabricate events, got $events", events.isEmpty())
    }

    @Test
    fun deviceStateProviderReturnsSaneValues() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val provider = AndroidDeviceStateProvider(context, SystemStateTracker())

        val level = provider.batteryLevel()
        assertNotNull(level)
        assertTrue("battery level $level out of range", level!! in 0..100)
        assertNotNull(provider.isCharging())
        assertNotNull(provider.isScreenOn())
        // Network/Wi-Fi values must not throw; environment decides the value.
        provider.isNetworkAvailable()
        provider.isWifiConnected()
        provider.connectedWifiSsid()
        assertEquals(false, provider.isAnyBluetoothDeviceConnected())
    }
}
