package com.dhruw.autoflow.di

import android.content.Context
import android.util.Log
import com.dhruw.autoflow.automation.engine.AutomationEngine
import com.dhruw.autoflow.automation.engine.AutomationRunner
import com.dhruw.autoflow.automation.engine.AutomationScheduler
import com.dhruw.autoflow.automation.engine.handlers.DelayActionHandler
import com.dhruw.autoflow.automation.engine.handlers.LogActionHandler
import com.dhruw.autoflow.automation.engine.handlers.SetVariableActionHandler
import com.dhruw.autoflow.data.local.AutoFlowDatabase
import com.dhruw.autoflow.data.repository.AutomationRepository
import com.dhruw.autoflow.data.repository.ExecutionRepository
import com.dhruw.autoflow.data.repository.RoomAutomationRepository
import com.dhruw.autoflow.data.repository.RoomExecutionRepository
import com.dhruw.autoflow.instagram.InstagramAnalysisStore
import com.dhruw.autoflow.instagram.InstagramDataProcessor
import com.dhruw.autoflow.instagram.InstagramFollowAnalyzer
import com.dhruw.autoflow.services.background.WorkManagerAutomationScheduler
import com.dhruw.autoflow.services.files.CopyFileActionHandler
import com.dhruw.autoflow.services.files.FileAccess
import com.dhruw.autoflow.services.files.FileScanStateStore
import com.dhruw.autoflow.services.files.FileTriggerMonitor
import com.dhruw.autoflow.services.files.MoveFileActionHandler
import com.dhruw.autoflow.services.files.RenameFileActionHandler
import com.dhruw.autoflow.services.files.SafFileAccess
import com.dhruw.autoflow.services.files.WorkManagerFileMonitor
import com.dhruw.autoflow.automation.engine.AutomationEventDispatcher
import com.dhruw.autoflow.automation.engine.AutomationHealthCalculator
import com.dhruw.autoflow.automation.engine.ConditionEvaluator
import com.dhruw.autoflow.automation.engine.SimulationEngine
import com.dhruw.autoflow.automation.engine.SystemStateTracker
import com.dhruw.autoflow.automation.engine.WorkflowValidator
import com.dhruw.autoflow.automation.model.SystemEvent
import com.dhruw.autoflow.data.repository.NotificationRecordRepository
import com.dhruw.autoflow.data.repository.RoomNotificationRecordRepository
import com.dhruw.autoflow.core.DiagnosticEventLog
import com.dhruw.autoflow.data.transfer.WorkflowTransferService
import com.dhruw.autoflow.services.CapabilityStatusProvider
import com.dhruw.autoflow.services.background.SchedulerDiagnostics
import com.dhruw.autoflow.services.system.AndroidDeviceStateProvider
import com.dhruw.autoflow.services.system.AudioDeviceMonitor
import com.dhruw.autoflow.services.system.BatteryMonitor
import com.dhruw.autoflow.services.system.NetworkMonitor
import com.dhruw.autoflow.services.system.ScreenMonitor
import com.dhruw.autoflow.services.system.SystemMonitorHub
import com.dhruw.autoflow.services.accessibility.AccessibilityAccessManager
import com.dhruw.autoflow.services.accessibility.UiAutomationActionHandler
import com.dhruw.autoflow.services.accessibility.UiAutomationSessionManager
import com.dhruw.autoflow.services.accessibility.UiInspector
import com.dhruw.autoflow.services.instagram.InstagramAnalysisActionHandler
import com.dhruw.autoflow.services.notification.AutomationNotifier
import com.dhruw.autoflow.services.notification.InstalledApps
import com.dhruw.autoflow.services.notification.NotificationAccessManager
import com.dhruw.autoflow.services.notification.SaveNotificationActionHandler
import com.dhruw.autoflow.services.notification.ShowNotificationActionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency wiring; one instance per process, owned by the Application. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Process-lifetime scope backing the repositories' StateFlow caches. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = AutoFlowDatabase.getInstance(appContext)

    val automationRepository: AutomationRepository =
        RoomAutomationRepository(database.automationDao(), applicationScope)

    val executionRepository: ExecutionRepository =
        RoomExecutionRepository(database.executionDao(), applicationScope)

    val notificationRecordRepository: NotificationRecordRepository =
        RoomNotificationRecordRepository(database.notificationRecordDao())

    private val notifier = AutomationNotifier(appContext)

    val installedApps = InstalledApps(appContext)

    val notificationAccessManager = NotificationAccessManager(appContext)

    val fileAccess: FileAccess = SafFileAccess(appContext)

    val fileScanStateStore = FileScanStateStore(appContext)

    val fileMonitor: FileTriggerMonitor = WorkManagerFileMonitor(appContext)

    val instagramProcessor = InstagramDataProcessor()

    val instagramAnalyzer = InstagramFollowAnalyzer()

    val instagramAnalysisStore = InstagramAnalysisStore()

    val accessibilityAccessManager = AccessibilityAccessManager(appContext)

    /** Element-inspector state; in-memory only, user-started, auto-expiring. */
    val uiInspector = UiInspector()

    /** Single-session UI automation runtime (guard, cancel, confirmations). */
    val uiAutomationSessionManager =
        UiAutomationSessionManager(appContext, accessibilityAccessManager)

    /** Installed version name, shown in About and stamped into export files. */
    val appVersionName: String = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

    /** Opt-in, in-memory engine event log for Developer Tools. */
    val diagnosticEventLog = DiagnosticEventLog()

    /** WorkManager's view of AutoFlow's scheduled automations. */
    val schedulerDiagnostics = SchedulerDiagnostics(appContext)

    /** Room schema version, surfaced in Diagnostics. */
    val databaseSchemaVersion: Int = AutoFlowDatabase.SCHEMA_VERSION

    /** Live view of which Android capabilities AutoFlow currently has. */
    val capabilityStatusProvider = CapabilityStatusProvider(
        context = appContext,
        notificationAccessManager = notificationAccessManager,
        accessibilityAccessManager = accessibilityAccessManager
    )

    /** Centralized workflow checks shared by the editor, importer and tester. */
    val workflowValidator = WorkflowValidator()

    val healthCalculator = AutomationHealthCalculator(workflowValidator)

    /** Reads and writes .autoflow files through the storage picker. */
    val workflowTransferService = WorkflowTransferService(
        context = appContext,
        repository = automationRepository,
        validator = workflowValidator,
        appVersion = appVersionName
    )

    /** Edge detection for system triggers; in-memory by design (see class doc). */
    val systemStateTracker = SystemStateTracker()

    private val deviceStateProvider = AndroidDeviceStateProvider(appContext, systemStateTracker)

    /**
     * Dry evaluation of triggers/conditions against synthetic payloads. Uses
     * the real device state so "IF battery below 20%" simulates honestly.
     */
    val simulationEngine = SimulationEngine(ConditionEvaluator(deviceStateProvider))

    private val engine = AutomationEngine(
        conditionEvaluator = ConditionEvaluator(deviceStateProvider),
        handlers = listOf(
            ShowNotificationActionHandler(notifier),
            DelayActionHandler(),
            LogActionHandler(sink = { Log.i("AutoFlowAction", it) }),
            SetVariableActionHandler(),
            CopyFileActionHandler(fileAccess),
            MoveFileActionHandler(fileAccess),
            RenameFileActionHandler(fileAccess),
            InstagramAnalysisActionHandler(
                fileAccess = fileAccess,
                processor = instagramProcessor,
                analyzer = instagramAnalyzer,
                store = instagramAnalysisStore,
                notifier = notifier
            ),
            SaveNotificationActionHandler(notificationRecordRepository),
            UiAutomationActionHandler(uiAutomationSessionManager)
        )
    )

    val runner = AutomationRunner(
        engine = engine,
        automationRepository = automationRepository,
        executionRepository = executionRepository,
        onAutoDisabled = { automation, failures ->
            notifier.notify(
                title = "AutoFlow disabled \"${automation.name}\"",
                message = "$failures runs in a row failed. Open AutoFlow to review it."
            )
        },
        onDiagnostic = diagnosticEventLog::record
    )

    /** Push-style trigger events (notifications, system) enter the engine through here. */
    val eventDispatcher = AutomationEventDispatcher(
        automationRepository = automationRepository,
        runner = runner,
        onDiagnostic = diagnosticEventLog::record
    )

    private val dispatchSystemEvent: (SystemEvent) -> Unit = { event ->
        applicationScope.launch {
            try {
                eventDispatcher.dispatch(event)
            } catch (e: Exception) {
                Log.w("AutoFlowSystemHub", "System dispatch failed: ${e.javaClass.simpleName}")
            }
        }
    }

    /**
     * Callback-based system monitors (battery, network/Wi-Fi, screen,
     * audio). Started once from Application.onCreate; boot and Bluetooth use
     * manifest receivers instead so they work without a running process.
     */
    val systemMonitorHub = SystemMonitorHub(
        listOf(
            BatteryMonitor(appContext, systemStateTracker, dispatchSystemEvent),
            NetworkMonitor(appContext, systemStateTracker, dispatchSystemEvent),
            ScreenMonitor(appContext, systemStateTracker, dispatchSystemEvent),
            AudioDeviceMonitor(appContext, systemStateTracker, dispatchSystemEvent)
        )
    )

    val scheduler: AutomationScheduler =
        WorkManagerAutomationScheduler(appContext, fileMonitor)
}
