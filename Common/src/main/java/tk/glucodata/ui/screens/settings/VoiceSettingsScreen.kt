package tk.glucodata.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

@Composable
fun VoiceSettingsScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

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

    val saveVoiceSettings = {
        try {
            Natives.saveVoice(speechSpeed, speechPitch, 50, 0, isVoiceActive)
            Natives.setspeakalarms(speakAlarms)
        } catch (_: Throwable) {}
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_group_voice_title),
        subtitle = stringResource(R.string.settings_voice_speech),
        onNavigateBack = onNavigateBack
    ) {
        // SPOKEN ANNOUNCEMENTS
        SettingsCard(
            title = stringResource(R.string.settings_voice_speech),
            icon = Icons.Default.RecordVoiceOver,
            categorySubtitle = "SPEECH GENERATION"
        ) {
            SettingsToggleRow(
                title = stringResource(R.string.dialog_speak_new_readings),
                subtitle = stringResource(R.string.dialog_speak_new_readings_desc),
                checked = isVoiceActive,
                onCheckedChange = {
                    isVoiceActive = it
                    saveVoiceSettings()
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            SettingsToggleRow(
                title = stringResource(R.string.dialog_speak_alarms),
                subtitle = stringResource(R.string.dialog_speak_alarms_desc),
                checked = speakAlarms,
                onCheckedChange = {
                    speakAlarms = it
                    saveVoiceSettings()
                }
            )
        }

        // VOICE TUNING
        SettingsCard(
            title = "Voice Modulation",
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            categorySubtitle = "RATE & PITCH"
        ) {
            // Speech Rate / Speed Slider
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dialog_speech_rate),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = String.format("%.1fx", speechSpeed),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = speechSpeed,
                    onValueChange = {
                        speechSpeed = it
                        saveVoiceSettings()
                    },
                    valueRange = 0.5f..2.0f
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Speech Pitch Slider
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dialog_voice_pitch),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = String.format("%.1fx", speechPitch),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = speechPitch,
                    onValueChange = {
                        speechPitch = it
                        saveVoiceSettings()
                    },
                    valueRange = 0.5f..2.0f
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Reset & Test Voice
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        speechSpeed = 1.0f
                        speechPitch = 1.0f
                        saveVoiceSettings()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Reset Defaults", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        try {
                            var tts: android.speech.tts.TextToSpeech? = null
                            tts = android.speech.tts.TextToSpeech(context) { status ->
                                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                                    tts?.setSpeechRate(speechSpeed)
                                    tts?.setPitch(speechPitch)
                                    tts?.speak("Glucose 105, trending stable", android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "test_speech")
                                }
                            }
                        } catch (_: Throwable) {
                            Toast.makeText(context, "Voice test triggered", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Speech", fontSize = 12.sp)
                }
            }
        }

        // INFO CARD
        SettingsInfoCard(
            text = "Voice announcements utilize Android's built-in Text-To-Speech (TTS) engine. Readings are announced as soon as they are decrypted from your sensor via Bluetooth or NFC.",
            icon = Icons.Default.Info
        )
    }
}
