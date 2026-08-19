package com.dhruw.autoflow.services.notification

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/** One selectable notification source in the app picker. */
data class InstalledApp(val packageName: String, val label: String)

/**
 * Resolves package names to human-readable labels and lists apps the user
 * can pick as notification sources. Backed by PackageManager — nothing is
 * hard-coded. Visibility of other apps is granted by the manifest
 * `<queries>` launcher-intent filter.
 */
class InstalledApps(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager
    private val labelCache = ConcurrentHashMap<String, String>()

    /** Display label for [packageName], or null when the app is not visible/installed. */
    fun labelFor(packageName: String): String? =
        labelCache[packageName] ?: try {
            packageManager
                .getApplicationLabel(packageManager.getApplicationInfo(packageName, 0))
                .toString()
                .also { labelCache[packageName] = it }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    /**
     * Launchable apps sorted by label. Uninstalled apps simply stop being
     * listed; an automation holding a stale package keeps its stored label
     * and matches nothing until the app returns.
     */
    fun launchableApps(): List<InstalledApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolved ->
                val info = resolved.activityInfo ?: return@mapNotNull null
                InstalledApp(
                    packageName = info.packageName,
                    label = resolved.loadLabel(packageManager)?.toString()
                        ?.takeIf { it.isNotBlank() } ?: info.packageName
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
