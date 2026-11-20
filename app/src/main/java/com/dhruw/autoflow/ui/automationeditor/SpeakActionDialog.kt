package com.dhruw.autoflow.ui.automationeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dhruw.autoflow.AutoFlowApplication
import com.dhruw.autoflow.automation.engine.EngineLimits
import com.dhruw.autoflow.automation.engine.TemplateResolver
import com.dhruw.autoflow.automation.model.Action
import com.dhruw.autoflow.automation.model.SpeakQueueMode
import com.dhruw.autoflow.automation.model.SpeakStream
import com.dhruw.autoflow.automation.model.Trigger
import com.dhruw.autoflow.automation.model.speakVoiceLabel
import kotlinx.coroutines.launch
import java.util.Locale

/** The languages offered up front. */
private val OFFERED_LANGUAGES = listOf(
    "" to "System default",
    "en-US" to "English (US)",
    "en-GB" to "English (UK)",
    "en-IN" to "English (India)",
    "hi-IN" to "Hindi",
    "gu-IN" to "Gujarati",
    "mr-IN" to "Marathi",
    "bn-IN" to "Bengali",
    "ta-IN" to "Tamil",
    "te-IN" to "Telugu",
    "es-ES" to "Spanish",
    "fr-FR" to "French",
    "de-DE" to "German",
    "it-IT" to "Italian",
    "pt-BR" to "Portuguese (Brazil)",
    "ru-RU" to "Russian",
    "ar-SA" to "Arabic",
    "ja-JP" to "Japanese",
    "ko-KR" to "Korean",
    "zh-CN" to "Chinese (Mandarin)"
)

/**
 * A template cannot resolve in the editor — there is no run to read a battery
 * level from — so a preview speaks each placeholder's own name instead of
 * reading braces aloud.
 */
internal fun speakPreviewText(template: String): String =
    Regex("""\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}""")
        .replace(template) { match -> match.groupValues[1].replace('.', ' ') }

/**
 * Editor for a Speak step: the text, the language, a specific installed voice,
 * and volume, pitch and rate.
 * Preview plays the current settings through the same shared engine the
 * automation will use, because hearing a voice setting is the only honest way
 * to judge it.
 *
 * The placeholder chips are narrowed to what [trigger] will actually deliver,
 * so tapping one can never build a template this automation cannot resolve.
 *
 * [Action.SpeakAction] also carries an audio stream, a queue mode and a
 * skip-on-silent flag. The editor deliberately offers none of the three and
 * leaves each at its default, but an action that arrives with them
 * set — from an imported workflow — keeps those values through an edit here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakActionDialog(
    initial: Action.SpeakAction?,
    trigger: Trigger?,
    onDismiss: () -> Unit,
    onConfirm: (Action.SpeakAction) -> Unit
) {
    val appContext = LocalContext.current.applicationContext
    val tts = remember { (appContext as AutoFlowApplication).container.ttsEngine }
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf(initial?.text ?: "") }
    var languageTag by rememberSaveable { mutableStateOf(initial?.languageTag ?: "") }
    var voiceName by rememberSaveable { mutableStateOf(initial?.voiceName ?: "") }
    var volume by rememberSaveable { mutableStateOf(initial?.volume ?: 1f) }
    var pitch by rememberSaveable { mutableStateOf(initial?.pitch ?: 1f) }
    var speechRate by rememberSaveable { mutableStateOf(initial?.speechRate ?: 1f) }
    var waitUntilFinished by rememberSaveable {
        mutableStateOf(initial?.waitUntilFinished ?: true)
    }
    var previewing by remember { mutableStateOf(false) }

    // automation.name resolves on every run; the rest depend on the trigger.
    val chips = remember(trigger) {
        listOf("automation.name") + TemplateResolver.payloadVariablesFor(trigger)
    }

    // Reloaded on every language change, so the picker only ever lists voices
    // that can speak the chosen one. null while the engine is still starting.
    val voices by produceState<List<String>?>(initialValue = null, languageTag) {
        value = tts.offlineVoiceNames(languageTag)
    }

    // Not editable here; carried through so an imported action keeps them.
    val stream = initial?.stream ?: SpeakStream.NOTIFICATION
    val queueMode = initial?.queueMode ?: SpeakQueueMode.INTERRUPT
    val skipIfSilent = initial?.skipIfSilent ?: false

    // Closing the dialog mid-preview must not leave the engine talking.
    DisposableEffect(Unit) { onDispose { tts.stop() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Speak text") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text to speak") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    chips.forEach { name ->
                        SuggestionChip(
                            onClick = { text = appendTemplate(text, name) },
                            label = {
                                Text(name, style = MaterialTheme.typography.labelSmall)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                PickerField(
                    label = "Language",
                    value = languageTag,
                    options = OFFERED_LANGUAGES.map { it.first },
                    labelOf = { tag ->
                        OFFERED_LANGUAGES.firstOrNull { it.first == tag }?.second
                            ?: tag.ifBlank { "System default" }
                    },
                    onPick = { picked ->
                        languageTag = picked
                        // The old pick belongs to the old language's voice list.
                        voiceName = ""
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                val available = voices
                PickerField(
                    label = "Voice",
                    value = voiceName,
                    options = listOf("") + available.orEmpty(),
                    labelOf = { name ->
                        if (name.isBlank()) "Engine default"
                        else speakVoiceLabel(name, available.orEmpty().indexOf(name) + 1)
                    },
                    onPick = { voiceName = it },
                    enabled = !available.isNullOrEmpty(),
                    supporting = when {
                        available == null -> "Reading the installed voices..."
                        available.isEmpty() -> "No offline voice is installed for this language"
                        else -> "Preview each one to hear which is which"
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                SliderRow(
                    label = "Volume",
                    value = volume,
                    range = EngineLimits.MIN_SPEAK_VOLUME..EngineLimits.MAX_SPEAK_VOLUME,
                    steps = 9,
                    onChange = { volume = it }
                )
                SliderRow(
                    label = "Pitch",
                    value = pitch,
                    range = EngineLimits.MIN_SPEECH_RATIO..EngineLimits.MAX_SPEECH_RATIO,
                    steps = 18,
                    onChange = { pitch = it }
                )
                SliderRow(
                    label = "Speech rate",
                    value = speechRate,
                    range = EngineLimits.MIN_SPEECH_RATIO..EngineLimits.MAX_SPEECH_RATIO,
                    steps = 18,
                    onChange = { speechRate = it }
                )

                Spacer(modifier = Modifier.height(16.dp))
                SwitchRow(
                    label = "Wait until finished",
                    description = "Hold the automation open until the words are out",
                    checked = waitUntilFinished,
                    onChange = { waitUntilFinished = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    onConfirm(
                        Action.SpeakAction(
                            text = text.trim(),
                            languageTag = languageTag,
                            pitch = pitch,
                            speechRate = speechRate,
                            voiceName = voiceName,
                            stream = stream,
                            queueMode = queueMode,
                            volume = volume,
                            waitUntilFinished = waitUntilFinished,
                            skipIfSilent = skipIfSilent
                        )
                    )
                }
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = text.isNotBlank() && !previewing,
                    onClick = {
                        previewing = true
                        scope.launch {
                            // Always INTERRUPT: a preview should start now, not
                            // queue behind an automation that happens to be talking.
                            tts.speak(
                                text = speakPreviewText(text.trim()),
                                languageTag = languageTag,
                                voiceName = voiceName,
                                pitch = pitch,
                                speechRate = speechRate,
                                volume = volume,
                                stream = stream,
                                queueMode = SpeakQueueMode.INTERRUPT,
                                waitUntilFinished = true
                            )
                            previewing = false
                        }
                    }
                ) { Text(if (previewing) "Playing…" else "Preview") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Append `{{name}}`, keeping a single space between it and any text before it. */
private fun appendTemplate(current: String, name: String): String =
    if (current.isEmpty() || current.endsWith(" ")) "$current{{$name}}"
    else "$current {{$name}}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PickerField(
    label: String,
    value: T,
    options: List<T>,
    labelOf: (T) -> String,
    onPick: (T) -> Unit,
    enabled: Boolean = true,
    supporting: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val open = expanded && enabled

    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = labelOf(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            supportingText = supporting?.let { note ->
                { Text(note, style = MaterialTheme.typography.labelSmall) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = enabled
                )
                .fillMaxWidth(),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onPick(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    Text(
        text = "$label: ${String.format(Locale.US, "%.1f", value)}",
        style = MaterialTheme.typography.labelMedium
    )
    Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
