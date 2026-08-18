package com.dhruw.autoflow.di

import android.content.Context
import android.util.Log
import com.dhruw.autoflow.automation.engine.AutomationEngine
import com.dhruw.autoflow.automation.engine.AutomationRunner
import com.dhruw.autoflow.automation.engine.AutomationScheduler
import com.dhruw.autoflow.automation.engine.handlers.DelayActionHandler
import com.dhruw.autoflow.automation.engine.handlers.LogActionHandler
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
import com.dhruw.autoflow.services.instagram.InstagramAnalysisActionHandler
import com.dhruw.autoflow.services.notification.AutomationNotifier
import com.dhruw.autoflow.services.notification.ShowNotificationActionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    private val notifier = AutomationNotifier(appContext)

    val fileAccess: FileAccess = SafFileAccess(appContext)

    val fileScanStateStore = FileScanStateStore(appContext)

    val fileMonitor: FileTriggerMonitor = WorkManagerFileMonitor(appContext)

    val instagramProcessor = InstagramDataProcessor()

    val instagramAnalyzer = InstagramFollowAnalyzer()

    val instagramAnalysisStore = InstagramAnalysisStore()

    private val engine = AutomationEngine(
        handlers = listOf(
            ShowNotificationActionHandler(notifier),
            DelayActionHandler(),
            LogActionHandler(sink = { Log.i("AutoFlowAction", it) }),
            CopyFileActionHandler(fileAccess),
            MoveFileActionHandler(fileAccess),
            RenameFileActionHandler(fileAccess),
            InstagramAnalysisActionHandler(
                fileAccess = fileAccess,
                processor = instagramProcessor,
                analyzer = instagramAnalyzer,
                store = instagramAnalysisStore,
                notifier = notifier
            )
        )
    )

    val runner = AutomationRunner(engine, automationRepository, executionRepository)

    val scheduler: AutomationScheduler =
        WorkManagerAutomationScheduler(appContext, fileMonitor)
}
