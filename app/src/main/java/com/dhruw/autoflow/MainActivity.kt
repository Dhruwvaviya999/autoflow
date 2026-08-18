package com.dhruw.autoflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dhruw.autoflow.ui.AutoFlowApp
import com.dhruw.autoflow.ui.theme.AutoFlowTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Notification permission is deliberately NOT requested here: the
        // Permission center in Settings asks when the user opts in, and
        // handlers report a clear execution error when it is missing.

        // File triggers poll every ~15 min in the background; opening the app
        // is a natural moment for a prompt scan. No-op without file automations.
        (application as AutoFlowApplication).container.fileMonitor.requestImmediateScan()

        setContent {
            AutoFlowTheme {
                AutoFlowApp()
            }
        }
    }
}
