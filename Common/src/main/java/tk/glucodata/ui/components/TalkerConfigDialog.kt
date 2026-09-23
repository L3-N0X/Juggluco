package tk.glucodata.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.glucodata.Natives
import tk.glucodata.R

@Composable
fun TalkerConfigDialog(
    onDismiss: () -> Unit
) {
    var isVoiceActive by remember {
        mutableStateOf(try { Natives.getVoiceActive() } catch (_: Throwable) { false })
    }
    var speakAlarms by remember {
        mutableStateOf(try { Natives.speakalarms() } catch (_: Throwable) { true })
    }
    var speechSpeed by remember {
        mutableFloatStateOf(try { Natives.getVoiceSpeed().let { if (it > 0) it else 1.0f } } catch (_: Throwable) { 1.0f })
    }
    var speechPitch by remember {
        mutableFloatStateOf(try { Natives.getVoicePitch().let { if (it > 0) it else 1.0f } } catch (_: Throwable) { 1.0f })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_voice_speech), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Speak glucose on reading
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.dialog_speak_new_readings), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.dialog_speak_new_readings_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = isVoiceActive,
                        onCheckedChange = {
                            isVoiceActive = it
                            try {
                                Natives.saveVoice(speechSpeed, speechPitch, 50, 0, it)
                            } catch (_: Throwable) {}
                        }
                    )
                }

                HorizontalDivider()

                // Speak Alarms
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.dialog_speak_alarms), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.dialog_speak_alarms_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(
                        checked = speakAlarms,
                        onCheckedChange = {
                            speakAlarms = it
                            try {
                                Natives.setspeakalarms(it)
                            } catch (_: Throwable) {}
                        }
                    )
                }

                HorizontalDivider()

                // Speech Speed Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.dialog_speech_rate), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${String.format(java.util.Locale.getDefault(), "%.1f", speechSpeed)}x", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speechSpeed,
                        onValueChange = { speechSpeed = it },
                        onValueChangeFinished = {
                            try {
                                Natives.saveVoice(speechSpeed, speechPitch, 50, 0, isVoiceActive)
                            } catch (_: Throwable) {}
                        },
                        valueRange = 0.5f..2.0f
                    )
                }

                // Speech Pitch Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.dialog_voice_pitch), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${String.format(java.util.Locale.getDefault(), "%.1f", speechPitch)}x", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speechPitch,
                        onValueChange = { speechPitch = it },
                        onValueChangeFinished = {
                            try {
                                Natives.saveVoice(speechSpeed, speechPitch, 50, 0, isVoiceActive)
                            } catch (_: Throwable) {}
                        },
                        valueRange = 0.5f..2.0f
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.dialog_done))
            }
        }
    )
}
