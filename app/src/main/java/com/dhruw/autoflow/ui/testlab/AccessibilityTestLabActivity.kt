package com.dhruw.autoflow.ui.testlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dhruw.autoflow.ui.theme.AutoFlowTheme
import kotlinx.coroutines.delay

/**
 * A safe, stable in-app target for exercising the accessibility engine:
 * known texts, test-tag view IDs, an editable field, a password field (to
 * verify SetText refuses it), a delayed element (to verify WaitForElement),
 * a scrollable list, and a long-pressable row. Reached only from Settings →
 * Developer tools; no production behavior depends on it.
 */
class AccessibilityTestLabActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TestLabScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TestLabScreen() {
    var status by rememberSaveable { mutableStateOf("Ready") }
    var input by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showDelayed by rememberSaveable { mutableStateOf(false) }
    var delayedVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showDelayed) {
        if (showDelayed && !delayedVisible) {
            delay(2_000)
            delayedVisible = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Surfaces testTags as accessibility view IDs so ViewId
            // selectors can be exercised against this screen.
            .semantics { testTagsAsResourceId = true }
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Accessibility Test Lab",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Safe targets for testing UI automations. Nothing here " +
                "leaves the device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.testTag("lab_status")
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { status = "Demo tapped" },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lab_demo_button")
        ) { Text("Demo Button") }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                showDelayed = true
                status = "Waiting for delayed element"
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lab_show_delayed")
        ) { Text("Show Delayed Element") }
        if (delayedVisible) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Delayed Element",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("lab_delayed_element")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                if (it.isNotEmpty()) status = "Text entered"
            },
            label = { Text("Type here") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lab_text_field")
        )

        Spacer(modifier = Modifier.height(12.dp))
        // Exists to verify the engine REFUSES password fields.
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password field (automation must refuse)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lab_password_field")
        )

        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { status = "Row tapped" },
                    onLongClick = { status = "Long pressed" }
                )
                .testTag("lab_long_press_row")
        ) {
            Text(
                text = "Long-press me",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Scrollable area", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .testTag("lab_scroll_area")
        ) {
            items(30) { index ->
                Text(
                    text = "Scroll item ${index + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { status = "Final test done" },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lab_final_button")
        ) { Text("Final Test Button") }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
