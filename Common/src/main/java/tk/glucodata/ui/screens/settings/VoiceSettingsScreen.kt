package tk.glucodata.ui.screens.settings

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
        onNavigateBack = onNavigateBack
    ) {
        // SPOKEN ANNOUNCEMENTS
        SettingsSection(title = "Spoken announcements") {
            SettingsSwitchRow(
                title = stringResource(R.string.dialog_speak_new_readings),
                subtitle = stringResource(R.string.dialog_speak_new_readings_desc),
                icon = Icons.Default.RecordVoiceOver,
                checked = isVoiceActive,
                onCheckedChange = {
                    isVoiceActive = it
                    saveVoiceSettings()
                }
            )

            SettingsSwitchRow(
                title = stringResource(R.string.dialog_speak_alarms),
                subtitle = stringResource(R.string.dialog_speak_alarms_desc),
                icon = Icons.Default.NotificationsActive,
                checked = speakAlarms,
                onCheckedChange = {
                    speakAlarms = it
                    saveVoiceSettings()
                }
            )
        }

        // VOICE TUNING
        SettingsSection(title = "Voice modulation") {
            SettingsSliderRow(
                title = stringResource(R.string.dialog_speech_rate),
                valueText = String.format("%.1fx", speechSpeed),
                icon = Icons.Default.Speed,
                value = speechSpeed,
                onValueChange = {
                    speechSpeed = it
                    saveVoiceSettings()
                },
                valueRange = 0.5f..2.0f
            )

            SettingsSliderRow(
                title = stringResource(R.string.dialog_voice_pitch),
                valueText = String.format("%.1fx", speechPitch),
                icon = Icons.Default.MusicNote,
                value = speechPitch,
                onValueChange = {
                    speechPitch = it
                    saveVoiceSettings()
                },
                valueRange = 0.5f..2.0f
            )
        }

        // ACTIONS
        SettingsSection(title = "Actions") {
            SettingsActionRow(
                title = "Test voice synthesis",
                subtitle = "Speak a test phrase using current rate and pitch",
                icon = Icons.Default.PlayArrow,
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
                }
            )

            SettingsActionRow(
                title = "Reset default voice settings",
                subtitle = "Reset speech speed (1.0x) and pitch (1.0x) to normal",
                icon = Icons.Default.Refresh,
                onClick = {
                    speechSpeed = 1.0f
                    speechPitch = 1.0f
                    saveVoiceSettings()
                    Toast.makeText(context, "Voice reset to defaults", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // INFO
        SettingsInfoCard(
            text = "Juggluco uses Android's built-in Text-to-Speech (TTS) engine. Readings are pronounced whenever a new glucose point arrives via Bluetooth stream or NFC scan.",
            icon = Icons.Default.Info
        )
    }
}
