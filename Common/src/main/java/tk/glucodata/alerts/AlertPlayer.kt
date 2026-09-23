package tk.glucodata.alerts

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.CombinedVibration
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tk.glucodata.AlertBridge
import tk.glucodata.Applic
import tk.glucodata.Log
import tk.glucodata.Notify
import tk.glucodata.R
import tk.glucodata.ui.model.GlucoseUnit
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Plays one alert at a time: notification, vibration, sound with delays and fade-in,
 * volume and Do Not Disturb overrides, and restores every system setting it changed
 * when the alert stops. All playback work runs on one handler thread.
 */
object AlertPlayer {
    private const val LOG_ID = "AlertPlayer"
    const val CHANNEL_ID = "glucoseAlerts"
    const val NOTIFICATION_ID = 81450
    private const val RAMP_STEP_MS = 200L
    private const val ANNOUNCE_LEAD_MS = 2500L
    private const val MAX_WAKE_MS = 30 * 60_000L

    enum class StopReason { DISMISSED, SNOOZED, RESOLVED, REPLACED, REMOTE, TIMEOUT }

    data class ActiveAlert(
        val rule: AlertRule,
        val reading: AlertEngine.Reading?,
        val lostMinutes: Int,
        val startedAt: Long,
        val isTest: Boolean,
        /** Raised by the connected phone or watch rather than by this device. */
        val remote: Boolean = false,
        /** False once sound and vibration ended while the notification stays up. */
        val ringing: Boolean = true
    )

    private val _active = MutableStateFlow<ActiveAlert?>(null)
    val active: StateFlow<ActiveAlert?> = _active.asStateFlow()

    /** Id of the rule currently ringing; tests and alerts that finished ringing do not count. */
    val activeRuleId: String?
        get() = _active.value?.takeIf { !it.isTest && it.ringing }?.rule?.id

    /** Id of the rule whose notification is up, ringing or not; tests do not count. */
    val shownRuleId: String?
        get() = _active.value?.takeIf { !it.isTest }?.rule?.id

    /** False while a more severe real alert is ringing, which [rule] must not interrupt. */
    fun canPlay(rule: AlertRule): Boolean {
        val current = _active.value ?: return true
        return !current.ringing || current.isTest || current.rule.priority >= rule.priority
    }

    private val handler: Handler by lazy {
        Handler(HandlerThread("GlucoseAlerts").apply { start() }.looper)
    }

    private val context: Context get() = Applic.app
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val notificationManager by lazy { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    // Everything below is only touched on the handler thread.
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var savedVolume: Pair<Int, Int>? = null
    private var savedFilter: Int? = null
    private var savedRinger: Int? = null
    private var flashOn = false
    private var vibrating = false
    private var channelCreated = false
    private val scheduled = mutableListOf<Runnable>()

    /** Runs [block] after [delayMs] unless the alert stops first. */
    private fun schedule(delayMs: Long, block: () -> Unit) {
        val runnable = object : Runnable {
            override fun run() {
                scheduled.remove(this)
                block()
            }
        }
        scheduled.add(runnable)
        handler.postDelayed(runnable, delayMs)
    }

    /** True while [alert] is still the one shown; reading updates keep it current. */
    private fun isCurrent(alert: ActiveAlert): Boolean =
        _active.value?.let { it.startedAt == alert.startedAt && it.rule.id == alert.rule.id } == true

    fun play(
        rule: AlertRule,
        reading: AlertEngine.Reading?,
        lostMinutes: Int = 0,
        isTest: Boolean = false,
        remote: Boolean = false
    ) {
        val alert = ActiveAlert(rule, reading, lostMinutes, System.currentTimeMillis(), isTest, remote)
        _active.value = alert
        handler.post { start(alert) }
    }

    /** Rings [rule] as it would for a real reading, using the latest value when there is one. */
    fun test(rule: AlertRule) {
        val text = AlertBridge.lastGlucoseText()
        val reading = AlertEngine.Reading(
            mgdl = if (rule.kind == AlertKind.HIGH) rule.thresholdMgdl + 10f else (rule.thresholdMgdl - 5f).coerceAtLeast(40f),
            rate = AlertBridge.lastGlucoseRate(),
            timeMillis = System.currentTimeMillis(),
            displayValue = text ?: currentUnit().format(rule.thresholdMgdl)
        )
        play(rule, reading, lostMinutes = rule.lossMinutes, isTest = true)
    }

    fun stop(reason: StopReason) {
        val alert = _active.value ?: return
        val keepNotification = reason == StopReason.TIMEOUT
        if (keepNotification) {
            _active.value = alert.copy(ringing = false)
        } else {
            _active.value = null
        }
        handler.post {
            stopOutputs()
            if (!keepNotification) notificationManager.cancel(NOTIFICATION_ID)
        }
        if (reason == StopReason.DISMISSED || reason == StopReason.SNOOZED) {
            AlertBridge.stopAlarmOnPeers()
        }
        if (reason == StopReason.DISMISSED) AlertSync.sendStop(alert.rule.id)
    }

    /** Stops the alert here and on connected devices. */
    fun dismiss() = stop(StopReason.DISMISSED)

    /** Snoozes the shown alert here and on connected devices. */
    fun snooze(minutes: Int) {
        val alert = _active.value ?: return
        if (!alert.isTest) {
            AlertStore.snoozeRule(alert.rule.id, minutes)
            AlertSync.sendSnooze(alert.rule, AlertStore.runtimeOf(alert.rule.id).snoozedUntil)
        } else {
            AlertSync.sendStop(alert.rule.id)
        }
        stop(StopReason.SNOOZED)
    }

    /** Refreshes the shown value while an alert is up. */
    fun updateReading(reading: AlertEngine.Reading) {
        val alert = _active.value ?: return
        if (alert.isTest || alert.rule.kind == AlertKind.SIGNAL_LOSS) return
        val updated = alert.copy(reading = reading)
        _active.value = updated
        handler.post { if (_active.value != null) postNotification(updated, silentUpdate = true) }
    }

    private fun start(alert: ActiveAlert) {
        stopOutputs()
        if (!isCurrent(alert)) return
        val rule = forThisDevice(alert.rule)
        acquireWakeLock(rule)
        postNotification(alert, silentUpdate = false)
        if (!alert.remote) {
            AlertSync.sendRing(alert)
            if (!alert.isTest) {
                val prefix = context.getString(if (rule.kind == AlertKind.HIGH || rule.kind == AlertKind.RISING) R.string.loc_watch_alert_high else R.string.loc_watch_alert_low)
                alert.reading?.let { AlertBridge.alarmToWatches("$prefix ${it.displayValue}") }
            }
        }
        if (rule.overrideDnd) overrideQuietModes()

        if (rule.vibrate) {
            schedule(rule.vibrationDelaySec * 1000L) { if (isCurrent(alert)) startVibration(rule) }
        }
        val soundDelayMs = rule.soundDelaySec * 1000L
        if (rule.announce) {
            schedule(soundDelayMs) { if (isCurrent(alert)) announce(alert) }
        }
        if (rule.output != AlertOutput.NONE) {
            val lead = if (rule.announce) ANNOUNCE_LEAD_MS else 0L
            schedule(soundDelayMs + lead) { if (isCurrent(alert)) startSound(rule) }
        }
        if (rule.flash) {
            schedule(soundDelayMs) {
                if (isCurrent(alert)) {
                    AlertBridge.startFlash()
                    flashOn = true
                }
            }
        }
        if (rule.playDurationSec > 0) {
            val endMs = maxOf(soundDelayMs, rule.vibrationDelaySec * 1000L) + rule.playDurationSec * 1000L
            schedule(endMs) { if (isCurrent(alert)) stop(StopReason.TIMEOUT) }
        }
    }

    /** Applies this device's own preferences, e.g. a watch that should only vibrate. */
    private fun forThisDevice(rule: AlertRule): AlertRule =
        if (AlertStore.settings.value.soundOnThisDevice) rule
        else rule.copy(output = AlertOutput.NONE, announce = false, vibrate = true)

    private fun stopOutputs() {
        scheduled.forEach { handler.removeCallbacks(it) }
        scheduled.clear()
        mediaPlayer?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        if (vibrating) {
            vibrator()?.cancel()
            vibrating = false
        }
        if (flashOn) {
            AlertBridge.stopFlash()
            flashOn = false
        }
        abandonFocus()
        restoreQuietModes()
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    private fun acquireWakeLock(rule: AlertRule) {
        val delays = maxOf(rule.soundDelaySec, rule.vibrationDelaySec) * 1000L
        val timeout = if (rule.playDurationSec > 0) delays + rule.playDurationSec * 1000L + 10_000L else MAX_WAKE_MS
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "juggluco:alert").apply {
            setReferenceCounted(false)
            acquire(timeout.coerceAtMost(MAX_WAKE_MS))
        }
    }

    // --- Vibration -------------------------------------------------------------------

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** Waveform for [rule], looping when it runs until stopped. */
    fun vibrationEffect(rule: AlertRule, loop: Boolean): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val timings = rule.effectiveVibrationTimings
        val amplitude = (rule.vibrationIntensity.coerceIn(1, 100) * 255 / 100).coerceIn(1, 255)
        val repeat = if (loop) 0 else -1
        val vib = vibrator()
        return if (vib?.hasAmplitudeControl() == true) {
            val amplitudes = IntArray(timings.size) { index -> if (index % 2 == 1) amplitude else 0 }
            VibrationEffect.createWaveform(timings, amplitudes, repeat)
        } else {
            VibrationEffect.createWaveform(timings, repeat)
        }
    }

    private fun startVibration(rule: AlertRule) {
        val vib = vibrator() ?: return
        if (!vib.hasVibrator()) return
        val asAlarm = rule.output == AlertOutput.ALARM || rule.overrideDnd
        if (!asAlarm && audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        vibrate(vib, rule, loop = true, asAlarm = asAlarm)
        vibrating = true
    }

    /** One pass of [rule]'s pattern, for previews in the editor. */
    fun previewVibration(rule: AlertRule) {
        val vib = vibrator() ?: return
        vibrate(vib, rule, loop = false, asAlarm = true)
    }

    @SuppressLint("MissingPermission")
    private fun vibrate(vib: Vibrator, rule: AlertRule, loop: Boolean, asAlarm: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val effect = vibrationEffect(rule, loop) ?: return
                val attributes = VibrationAttributes.Builder()
                    .setUsage(if (asAlarm) VibrationAttributes.USAGE_ALARM else VibrationAttributes.USAGE_NOTIFICATION)
                    .build()
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.vibrate(CombinedVibration.createParallel(effect), attributes)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = vibrationEffect(rule, loop) ?: return
                @Suppress("DEPRECATION")
                vib.vibrate(effect, audioAttributes(if (asAlarm) AlertOutput.ALARM else AlertOutput.NOTIFICATION))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(rule.effectiveVibrationTimings, if (loop) 0 else -1)
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "vibrate", th)
        }
    }

    // --- Sound -----------------------------------------------------------------------

    private fun streamOf(output: AlertOutput): Int = when (output) {
        AlertOutput.ALARM -> AudioManager.STREAM_ALARM
        AlertOutput.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
        AlertOutput.MEDIA, AlertOutput.NONE -> AudioManager.STREAM_MUSIC
    }

    private fun audioAttributes(output: AlertOutput): AudioAttributes = AudioAttributes.Builder()
        .setUsage(
            when (output) {
                AlertOutput.ALARM -> AudioAttributes.USAGE_ALARM
                AlertOutput.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
                AlertOutput.MEDIA, AlertOutput.NONE -> AudioAttributes.USAGE_MEDIA
            }
        )
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun startSound(rule: AlertRule) {
        if (rule.output == AlertOutput.NOTIFICATION && !rule.overrideDnd &&
            audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
        ) {
            Log.i(LOG_ID, "ringer muted, notification sound skipped")
            return
        }
        applyVolume(rule)
        requestFocus(rule.output)
        val player = createPlayer(rule, AlertSounds.resolve(context, rule))
            ?: createPlayer(rule, AlertSounds.fallback(context, rule))
            ?: return
        mediaPlayer = player
        if (rule.rampUpSec > 0) {
            player.setVolume(0.02f, 0.02f)
            player.start()
            rampVolume(player, System.currentTimeMillis(), rule.rampUpSec * 1000L)
        } else {
            player.setVolume(1f, 1f)
            player.start()
        }
    }

    private fun createPlayer(rule: AlertRule, uri: Uri): MediaPlayer? = try {
        MediaPlayer().apply {
            setAudioAttributes(audioAttributes(rule.output))
            setDataSource(context, uri)
            isLooping = true
            setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            prepare()
        }
    } catch (th: Throwable) {
        Log.stack(LOG_ID, "createPlayer $uri", th)
        null
    }

    private fun rampVolume(player: MediaPlayer, startedAt: Long, durationMs: Long) {
        if (mediaPlayer !== player) return
        val fraction = ((System.currentTimeMillis() - startedAt).toFloat() / durationMs).coerceIn(0f, 1f)
        // Loudness is perceived logarithmically; a squared curve sounds like an even fade.
        val volume = (fraction * fraction).coerceAtLeast(0.02f)
        runCatching { player.setVolume(volume, volume) }
        if (fraction < 1f) schedule(RAMP_STEP_MS) { rampVolume(player, startedAt, durationMs) }
    }

    private fun applyVolume(rule: AlertRule) {
        val stream = streamOf(rule.output)
        try {
            val max = audioManager.getStreamMaxVolume(stream)
            val current = audioManager.getStreamVolume(stream)
            val target = when {
                rule.volumePercent >= 0 -> (max * rule.volumePercent / 100f).roundToInt().coerceIn(1, max)
                // Forcing through quiet modes is pointless at volume zero.
                rule.overrideDnd && current == 0 -> (max / 2).coerceAtLeast(1)
                else -> return
            }
            if (target != current) {
                if (savedVolume == null) savedVolume = stream to current
                audioManager.setStreamVolume(stream, target, 0)
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "applyVolume", th)
        }
    }

    private fun requestFocus(output: AlertOutput) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(audioAttributes(output))
            .build()
        runCatching { audioManager.requestAudioFocus(request) }
        focusRequest = request
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    private fun announce(alert: ActiveAlert) {
        val text = when {
            alert.rule.kind == AlertKind.SIGNAL_LOSS -> context.getString(R.string.loc_alarm_announce_loss, alert.rule.name, alert.lostMinutes)
            else -> alert.reading?.displayValue ?: return
        }
        val output = if (alert.rule.output == AlertOutput.NONE) AlertOutput.NOTIFICATION else alert.rule.output
        AlertBridge.speak(text, audioAttributes(output))
    }

    // --- Do Not Disturb / ringer -------------------------------------------------------

    fun hasDndAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || notificationManager.isNotificationPolicyAccessGranted

    private fun overrideQuietModes() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                Log.i(LOG_ID, "no Do Not Disturb access, cannot override")
                return
            }
            val filter = notificationManager.currentInterruptionFilter
            if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                savedFilter = filter
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            val ringer = audioManager.ringerMode
            if (ringer != AudioManager.RINGER_MODE_NORMAL) {
                savedRinger = ringer
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "overrideQuietModes", th)
        }
    }

    private fun restoreQuietModes() {
        try {
            savedVolume?.let { (stream, volume) -> audioManager.setStreamVolume(stream, volume, 0) }
            savedRinger?.let { ringer ->
                // Going straight back to silent can leave vibrate mode on some versions.
                if (ringer == AudioManager.RINGER_MODE_SILENT && audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
                audioManager.ringerMode = ringer
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                savedFilter?.let { notificationManager.setInterruptionFilter(it) }
            }
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "restoreQuietModes", th)
        } finally {
            savedVolume = null
            savedRinger = null
            savedFilter = null
        }
    }

    // --- Notification ----------------------------------------------------------------

    private fun ensureChannel() {
        if (channelCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_title),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alert_channel_description)
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
        channelCreated = true
    }

    fun canUseFullScreen(): Boolean =
        Build.VERSION.SDK_INT < 34 || notificationManager.canUseFullScreenIntent()

    fun title(alert: ActiveAlert): String {
        val value = alert.reading?.displayValue
        return when {
            alert.rule.kind == AlertKind.SIGNAL_LOSS -> alert.rule.name
            value != null -> context.getString(R.string.loc_alarm_title_value, alert.rule.name, value, unitLabel(), arrow(alert.reading.rate)).trim()
            else -> alert.rule.name
        }
    }

    fun detail(alert: ActiveAlert): String {
        val rule = alert.rule
        val unit = currentUnit()
        val condition = when (rule.kind) {
            AlertKind.LOW -> context.getString(R.string.alert_detail_below, unit.format(rule.thresholdMgdl), context.getString(unit.labelRes)) +
                if (rule.forecastMinutes > 0) context.getString(R.string.alert_detail_within, rule.forecastMinutes) else ""
            AlertKind.HIGH -> context.getString(R.string.alert_detail_above, unit.format(rule.thresholdMgdl), context.getString(unit.labelRes)) +
                if (rule.forecastMinutes > 0) context.getString(R.string.alert_detail_within, rule.forecastMinutes) else ""
            AlertKind.FALLING -> context.getString(R.string.alert_detail_falling, unit.formatRate(rule.rateMgdlPerMin), context.getString(unit.labelRes))
            AlertKind.RISING -> context.getString(R.string.alert_detail_rising, unit.formatRate(rule.rateMgdlPerMin), context.getString(unit.labelRes))
            AlertKind.SIGNAL_LOSS -> context.getString(R.string.alert_detail_signal_loss, alert.lostMinutes)
        }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(alert.reading?.timeMillis ?: alert.startedAt))
        return if (alert.isTest) context.getString(R.string.alert_test_detail, condition) else context.getString(R.string.loc_alarm_detail_time, condition, time)
    }

    private fun currentUnit(): GlucoseUnit = if (Applic.unit == 1) GlucoseUnit.MMOL_L else GlucoseUnit.MG_DL

    private fun unitLabel(): String = context.getString(currentUnit().labelRes)

    fun arrow(rate: Float): String = when {
        !rate.isFinite() -> ""
        rate <= -2f -> "↓"
        rate <= -1f -> "↘"
        rate < 1f -> "→"
        rate < 2f -> "↗"
        else -> "↑"
    }

    private fun actionIntent(action: String, minutes: Int = 0): PendingIntent {
        val intent = Intent(context, AlertActionReceiver::class.java)
            .setAction(action)
            .putExtra(AlertActionReceiver.EXTRA_MINUTES, minutes)
        return PendingIntent.getBroadcast(
            context, action.hashCode() + minutes, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** The watch has its own round layout; everything else uses [AlertActivity]. */
    private val fullScreenActivity: Class<*> by lazy {
        if (Applic.isWearable) {
            runCatching { Class.forName("tk.glucodata.ui.WearAlertActivity") }.getOrDefault(AlertActivity::class.java)
        } else {
            AlertActivity::class.java
        }
    }

    private fun fullScreenIntent(): PendingIntent {
        val intent = Intent(context, fullScreenActivity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        return PendingIntent.getActivity(context, 81451, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun postNotification(alert: ActiveAlert, silentUpdate: Boolean) {
        try {
            ensureChannel()
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context).setPriority(Notification.PRIORITY_MAX)
            }
            val displayValue = alert.reading?.let { if (Applic.unit == 1) it.mgdl / 18f else it.mgdl }
            val iconSet = alert.rule.kind != AlertKind.SIGNAL_LOSS && !alert.isTest &&
                displayValue != null && AlertBridge.setGlucoseIcon(builder, displayValue)
            if (!iconSet) builder.setSmallIcon(if (alert.rule.kind == AlertKind.SIGNAL_LOSS) R.drawable.loss else R.drawable.novalue)

            val snoozeMinutes = AlertStore.settings.value.snoozeOptions.firstOrNull() ?: 15
            builder
                .setContentTitle(title(alert))
                .setContentText(detail(alert))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setWhen(alert.reading?.timeMillis ?: alert.startedAt)
                .setOnlyAlertOnce(silentUpdate)
                .setAutoCancel(true)
                // A watch running Juggluco rings the alert itself; do not bridge a copy there.
                .setLocalOnly(AlertSync.hasWearPeer())
                .setDeleteIntent(actionIntent(AlertActionReceiver.ACTION_DISMISS))
                .addAction(Notification.Action.Builder(null, context.getString(R.string.snooze_minutes, snoozeMinutes), actionIntent(AlertActionReceiver.ACTION_SNOOZE, snoozeMinutes)).build())
                .addAction(Notification.Action.Builder(null, context.getString(R.string.dismiss), actionIntent(AlertActionReceiver.ACTION_DISMISS)).build())

            if ((alert.rule.fullScreen || Applic.isWearable) && canUseFullScreen()) {
                builder.setContentIntent(fullScreenIntent())
                if (!silentUpdate) builder.setFullScreenIntent(fullScreenIntent(), true)
            } else {
                builder.setContentIntent(Notify.mkpending())
            }
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (th: Throwable) {
            Log.stack(LOG_ID, "postNotification", th)
        }
    }
}
