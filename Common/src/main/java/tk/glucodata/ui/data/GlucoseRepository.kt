package tk.glucodata.ui.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tk.glucodata.Applic
import tk.glucodata.Backup
import tk.glucodata.BleMirror
import tk.glucodata.MainActivity
import tk.glucodata.MessageSender
import tk.glucodata.Natives
import tk.glucodata.Nightscout
import tk.glucodata.Notify
import tk.glucodata.SensorBridge
import tk.glucodata.XInfuus
import tk.glucodata.nums.numio
import tk.glucodata.ui.model.AgpProfile
import tk.glucodata.ui.model.AlarmConfig
import tk.glucodata.ui.model.AlarmSoundStream
import tk.glucodata.ui.model.DeltaCalculation
import tk.glucodata.ui.model.DisplayConfig
import tk.glucodata.ui.model.ExchangesConfig
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStats
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.HardwareConfig
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.model.MirrorConnection
import tk.glucodata.ui.model.SensorDetail
import tk.glucodata.ui.model.SensorInfo
import tk.glucodata.ui.model.SensorState
import tk.glucodata.ui.model.SensorStatus
import tk.glucodata.ui.model.SignalQuality
import tk.glucodata.ui.model.StatsPeriod
import tk.glucodata.ui.model.TimeRange
import tk.glucodata.ui.model.WatchConfig

class GlucoseRepository(
    private val scope: CoroutineScope
) {
    private val _currentReading = MutableStateFlow<GlucosePoint?>(null)
    val currentReading: StateFlow<GlucosePoint?> = _currentReading.asStateFlow()

    private val _readings = MutableStateFlow<List<GlucosePoint>>(emptyList())
    val readings: StateFlow<List<GlucosePoint>> = _readings.asStateFlow()

    private val _sensors = MutableStateFlow<List<SensorInfo>>(emptyList())
    val sensors: StateFlow<List<SensorInfo>> = _sensors.asStateFlow()

    private val _mirrorConnections = MutableStateFlow<List<MirrorConnection>>(emptyList())
    val mirrorConnections: StateFlow<List<MirrorConnection>> = _mirrorConnections.asStateFlow()

    private val _sensorDetails = MutableStateFlow<List<SensorDetail>>(emptyList())
    val sensorDetails: StateFlow<List<SensorDetail>> = _sensorDetails.asStateFlow()

    private val _previousSensors = MutableStateFlow<List<SensorDetail>>(emptyList())
    val previousSensors: StateFlow<List<SensorDetail>> = _previousSensors.asStateFlow()

    private val _logs = MutableStateFlow<List<LogRecord>>(emptyList())
    val logs: StateFlow<List<LogRecord>> = _logs.asStateFlow()

    private val _unit = MutableStateFlow(GlucoseUnit.MG_DL)
    val unit: StateFlow<GlucoseUnit> = _unit.asStateFlow()

    private val _targetLow = MutableStateFlow(70f)
    val targetLow: StateFlow<Float> = _targetLow.asStateFlow()

    private val _targetHigh = MutableStateFlow(180f)
    val targetHigh: StateFlow<Float> = _targetHigh.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow<TimeRange?>(TimeRange.SIX_HOURS)
    val selectedTimeRange: StateFlow<TimeRange?> = _selectedTimeRange.asStateFlow()

    private val _stats = MutableStateFlow(GlucoseStats())
    val stats: StateFlow<GlucoseStats> = _stats.asStateFlow()

    private val _screenStats = MutableStateFlow(GlucoseStats())
    val screenStats: StateFlow<GlucoseStats> = _screenStats.asStateFlow()

    private val _statsPeriod = MutableStateFlow(StatsPeriod.FOURTEEN_DAYS)
    val statsPeriod: StateFlow<StatsPeriod> = _statsPeriod.asStateFlow()

    private val _statsUseHistory = MutableStateFlow(true)
    val statsUseHistory: StateFlow<Boolean> = _statsUseHistory.asStateFlow()

    private val _agpProfile = MutableStateFlow(AgpProfile.calculate(emptyList(), StatsPeriod.FOURTEEN_DAYS))
    val agpProfile: StateFlow<AgpProfile> = _agpProfile.asStateFlow()

    private val _alarms = MutableStateFlow(AlarmConfig())
    val alarms: StateFlow<AlarmConfig> = _alarms.asStateFlow()

    private val _exchanges = MutableStateFlow(ExchangesConfig())
    val exchanges: StateFlow<ExchangesConfig> = _exchanges.asStateFlow()

    private val _displayConfig = MutableStateFlow(DisplayConfig())
    val displayConfig: StateFlow<DisplayConfig> = _displayConfig.asStateFlow()

    private val _hardwareConfig = MutableStateFlow(HardwareConfig())
    val hardwareConfig: StateFlow<HardwareConfig> = _hardwareConfig.asStateFlow()

    private val _watchConfig = MutableStateFlow(WatchConfig())
    val watchConfig: StateFlow<WatchConfig> = _watchConfig.asStateFlow()

    init {
        refreshSettings()
        refreshAll()
        startPolling()
    }

    fun setTimeRange(range: TimeRange?) {
        _selectedTimeRange.value = range
        recalculateStats()
    }

    fun setStatsPeriod(period: StatsPeriod) {
        _statsPeriod.value = period
        recalculateStats()
    }

    fun setStatsUseHistory(useHistory: Boolean) {
        _statsUseHistory.value = useHistory
        try {
            if (Applic.Nativesloaded) {
                Natives.analysedays(_statsPeriod.value.days, useHistory)
            }
        } catch (_: Throwable) {}
        recalculateStats()
    }

    fun setUnit(newUnit: GlucoseUnit) {
        _unit.value = newUnit
        try {
            if (Applic.Nativesloaded) {
                Natives.setunit(if (newUnit == GlucoseUnit.MMOL_L) 1 else 2)
            }
        } catch (_: Throwable) {}
    }

    fun setTargetRange(low: Float, high: Float) {
        _targetLow.value = low
        _targetHigh.value = high
        try {
            if (Applic.Nativesloaded) {
                Natives.setTargetRange(low, high)
            }
        } catch (_: Throwable) {}
        recalculateStats()
    }

    fun refreshSettings() {
        try {
            if (Applic.Nativesloaded) {
                val nativeUnit = Natives.getunit()
                _unit.value = GlucoseUnit.fromNative(nativeUnit)
                val tLow = Natives.targetlow()
                val tHigh = Natives.targethigh()
                if (tLow > 0f) _targetLow.value = tLow
                if (tHigh > 0f) _targetHigh.value = tHigh

                // Read Alarms
                _alarms.value = AlarmConfig(
                    lowAlarmEnabled = Natives.hasalarmlow(),
                    lowThreshold = Natives.alarmlow().let { if (it > 0f) it else 70f },
                    lowSnoozeMinutes = Natives.readalarmsuspension(0).toInt().coerceAtLeast(5),
                    highAlarmEnabled = Natives.hasalarmhigh(),
                    highThreshold = Natives.alarmhigh().let { if (it > 0f) it else 180f },
                    highSnoozeMinutes = Natives.readalarmsuspension(1).toInt().coerceAtLeast(5),
                    urgentLowEnabled = try { Natives.hasalarmverylow() } catch (_: Throwable) { true },
                    urgentLowThreshold = try { Natives.alarmverylow().let { if (it > 0f) it else 54f } } catch (_: Throwable) { 54f },
                    lossAlarmEnabled = Natives.hasalarmloss(),
                    lossWaitMinutes = Natives.readalarmsuspension(4).toInt().coerceAtLeast(10),
                    valueAvailableNotification = Natives.hasvaluealarm(),
                    soundStream = AlarmSoundStream.fromId(Natives.getalarmSoundType())
                )

                // Read Exchanges
                _exchanges.value = ExchangesConfig(
                    xdripBroadcast = Natives.getxbroadcast(),
                    glucodataBroadcast = Natives.getJugglucobroadcast(),
                    librelinkBroadcast = Natives.getlibrelinkused(),
                    everSenseBroadcast = try { Natives.geteverSensebroadcast() } catch (_: Throwable) { false },
                    healthConnect = try { Natives.gethealthConnect() } catch (_: Throwable) { false },
                    libreViewEnabled = Natives.getuselibreview(),
                    xdripWebServer = Natives.getusexdripwebserver()
                )

                val savedMinimalistUnits = try {
                    Applic.app.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                        .getBoolean(KEY_MINIMALIST_UNITS, true)
                } catch (_: Throwable) { true }
                val savedDeltaMinutes = try {
                    Applic.app.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                        .getInt(KEY_DELTA_CALCULATION, 1)
                } catch (_: Throwable) { 1 }
                val deltaCalculation = DeltaCalculation.fromMinutes(savedDeltaMinutes)

                // Read Display
                _displayConfig.value = DisplayConfig(
                    floatingGlucose = Natives.getfloatglucose(),
                    statusBarNotification = Natives.getshowalways(),
                    systemUiFullscreen = Natives.getsystemUI(),
                    invertColors = Natives.getInvertColors(),
                    showScans = Natives.getshowscans(),
                    showCalibratedScans = Natives.getshowcalibratedscans(),
                    showStream = Natives.getshowstream(),
                    showCalibratedStream = Natives.getshowcalibratedstream(),
                    showHistory = Natives.getshowhistories(),
                    showCalibratedHistory = Natives.getshowcalibratedhistories(),
                    showAmounts = Natives.getshownumbers(),
                    showMeals = Natives.getshowmeals(),
                    minimalistUnits = savedMinimalistUnits,
                    deltaCalculation = deltaCalculation,
                    calibrationEnabled = try { Natives.getDoCalibrate() } catch (_: Throwable) { false },
                    calibratePastReadings = try { Natives.getCalibratePast() } catch (_: Throwable) { false },
                    calibrateAllValues = try { Natives.getAllValues() } catch (_: Throwable) { false }
                )

                // Read Hardware
                _hardwareConfig.value = HardwareConfig(
                    nfcSound = Natives.nfcsound(),
                    googleScan = try { Natives.getGoogleScan() } catch (_: Throwable) { false },
                    hasNfc = MainActivity.hasnfc
                )
            }
        } catch (_: Throwable) {}
    }

    fun refreshAll() {
        scope.launch(Dispatchers.IO) {
            refreshSettings()
            loadReadingsFromNative()
            loadSensorsFromNative()
            loadLogsFromNative()
            recalculateStats()
        }
    }

    private fun loadReadingsFromNative() {
        var loadedList = ArrayList<GlucosePoint>()
        try {
            if (Applic.Nativesloaded) {
                // Check latest reading first
                val strGl = Natives.lastglucose()
                if (strGl != null && strGl.time > 0) {
                    val rawVal = try {
                        strGl.value.replace(',', '.').toFloat()
                    } catch (_: Throwable) {
                        0f
                    }
                    val valMgDl = if (_unit.value == GlucoseUnit.MMOL_L) {
                        GlucoseUnit.MMOL_L.toMgDl(rawVal)
                    } else rawVal

                    if (valMgDl > 0f) {
                        _currentReading.value = GlucosePoint(
                            timestamp = strGl.time * 1000L,
                            valueMgDl = valMgDl,
                            rate = strGl.rate,
                            status = GlucoseStatus.fromValue(valMgDl, _targetLow.value, _targetHigh.value)
                        )
                    } else {
                        _currentReading.value = null
                    }
                } else {
                    _currentReading.value = null
                }

                // Read stream points from all sensors (falling back to active)
                val allPtrs = try { Natives.allSensorPtrs() } catch (_: Throwable) { null }
                val sensorPtrs: LongArray = if (allPtrs != null && allPtrs.isNotEmpty()) allPtrs else (Natives.activeSensorPtrs() ?: LongArray(0))

                if (sensorPtrs.isNotEmpty()) {
                    for (ptr in sensorPtrs) {
                        if (ptr == 0L) continue

                        // 1. Raw Stream Readings
                        var pos = 0
                        var safetyLimit = 50000
                        while (safetyLimit-- > 0) {
                            val res = Natives.streamfromSensorptr(ptr, pos)
                            val time = res and 0xFFFFFFFFL
                            val nextPos = (res ushr 48).toInt() and 0xFFFF
                            if (time == 0L || nextPos <= pos) break
                            val mgdL = (res ushr 32).toInt() and 0xFFFF
                            if (mgdL in 20..600) {
                                loadedList.add(
                                    GlucosePoint(
                                        timestamp = time * 1000L,
                                        valueMgDl = mgdL.toFloat(),
                                        isScan = false,
                                        isHistory = false,
                                        isCalibrated = false,
                                        status = GlucoseStatus.fromValue(mgdL.toFloat(), _targetLow.value, _targetHigh.value)
                                    )
                                )
                            }
                            pos = nextPos
                        }

                        // 2. Calibrated Stream Readings
                        pos = 0
                        safetyLimit = 50000
                        while (safetyLimit-- > 0) {
                            val res = try { Natives.calibratedStreamfromSensorptr(ptr, pos) } catch (_: Throwable) { 0L }
                            val time = res and 0xFFFFFFFFL
                            val nextPos = (res ushr 48).toInt() and 0xFFFF
                            if (time == 0L || nextPos <= pos) break
                            val mgdL = (res ushr 32).toInt() and 0xFFFF
                            if (mgdL in 20..600) {
                                loadedList.add(
                                    GlucosePoint(
                                        timestamp = time * 1000L,
                                        valueMgDl = mgdL.toFloat(),
                                        isScan = false,
                                        isHistory = false,
                                        isCalibrated = true,
                                        status = GlucoseStatus.fromValue(mgdL.toFloat(), _targetLow.value, _targetHigh.value)
                                    )
                                )
                            }
                            pos = nextPos
                        }

                        // 3. Scan Readings
                        pos = 0
                        safetyLimit = 10000
                        while (safetyLimit-- > 0) {
                            val res = try { Natives.scanfromSensorptr(ptr, pos) } catch (_: Throwable) { 0L }
                            val time = res and 0xFFFFFFFFL
                            val nextPos = (res ushr 48).toInt() and 0xFFFF
                            if (time == 0L || nextPos <= pos) break
                            val mgdL = (res ushr 32).toInt() and 0xFFFF
                            if (mgdL in 20..600) {
                                loadedList.add(
                                    GlucosePoint(
                                        timestamp = time * 1000L,
                                        valueMgDl = mgdL.toFloat(),
                                        isScan = true,
                                        isHistory = false,
                                        isCalibrated = false,
                                        status = GlucoseStatus.fromValue(mgdL.toFloat(), _targetLow.value, _targetHigh.value)
                                    )
                                )
                            }
                            pos = nextPos
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        if (_currentReading.value == null && loadedList.isNotEmpty()) {
            val latest = loadedList.filter { !it.isScan && !it.isCalibrated }.lastOrNull() ?: loadedList.last()
            _currentReading.value = latest
        }

        loadedList.sortBy { it.timestamp }
        _readings.value = loadedList
    }

    private fun loadSensorsFromNative() {
        val detailsList = ArrayList<SensorDetail>()
        val legacyList = ArrayList<SensorInfo>()

        try {
            if (Applic.Nativesloaded) {
                // 1. Query live active GATT sensors via SensorBridge
                val rawList = SensorBridge.getActiveGattSensors()
                if (!rawList.isNullOrEmpty()) {
                    for (info in rawList) {
                        val typeName = when (info.sensorgen) {
                            1 -> "FreeStyle Libre 1 / 2"
                            2 -> "FreeStyle Libre 2"
                            3 -> "FreeStyle Libre 3"
                            0x10 -> "SiBionics (GS1 / GS3)"
                            0x30, 0x40 -> "Dexcom G7 / ONE+"
                            0x50 -> "Accu-Chek SmartGuide"
                            else -> "CGM Sensor"
                        }

                        val status = when {
                            !info.isConnected -> SensorStatus.DISCONNECTED
                            info.isStreaming -> SensorStatus.CONNECTED_STREAMING
                            else -> SensorStatus.CONNECTED_IDLE
                        }

                        var start = info.startTime
                        if (start <= 0L && info.dataptr != 0L) {
                            try {
                                start = Natives.getSensorStartmsec(info.dataptr)
                            } catch (_: Throwable) {}
                        }
                        var end = 0L
                        if (!info.serial.isNullOrEmpty()) {
                            try {
                                val endData = Natives.getSensorEndData(info.serial)
                                val expectedSec = endData and 0xFFFFFFFFL
                                if (expectedSec > 0L) {
                                    end = expectedSec * 1000L
                                }
                            } catch (_: Throwable) {}
                        }
                        if (end <= 0L && start > 0L) {
                            end = start + 14 * 24 * 3600 * 1000L
                        }

                        detailsList.add(
                            SensorDetail(
                                id = info.serial ?: "Unknown",
                                name = info.serial ?: typeName,
                                sensorPtr = info.sensorptr,
                                status = status,
                                sensorGen = info.sensorgen,
                                sensorTypeName = typeName,
                                macAddress = info.macAddress,
                                rssi = info.rssi,
                                signalQuality = if (info.isConnected && info.rssi != null && info.rssi != 0) SignalQuality.fromRssi(info.rssi) else SignalQuality.LOST,
                                startTime = start,
                                endTime = end,
                                lastReadingTime = if (info.isConnected) System.currentTimeMillis() else 0L,
                                warmupMinutes = info.warmupMinutes,
                                minWarmupMinutes = info.minWarmupMinutes,
                                isConnected = info.isConnected,
                                isStreaming = info.isStreaming,
                                isHidden = info.isHidden,
                                hasCalibration = info.hasCalibration,
                                batteryPercent = null,
                                connectionStatusStr = info.statusStr ?: if (info.isConnected) "Connected" else "Disconnected",
                                handshakeStatusStr = info.handshakeStr ?: "",
                                rawDiagnosticText = info.infoHtml ?: ""
                            )
                        )
                        legacyList.add(
                            SensorInfo(
                                id = info.serial ?: "Unknown",
                                name = info.serial ?: typeName,
                                state = if (info.isConnected) SensorState.ACTIVE else SensorState.DISCONNECTED,
                                startTime = start,
                                endTime = end,
                                lastReadingTime = if (info.isConnected) System.currentTimeMillis() else 0L,
                                sensorType = typeName,
                                isStreaming = info.isStreaming,
                                isConnected = info.isConnected
                            )
                        )
                    }
                }

                // 2. Check activeSensorPtrs if no Gatt callbacks found
                if (detailsList.isEmpty()) {
                    val ptrs = Natives.activeSensorPtrs()
                    if (ptrs != null && ptrs.isNotEmpty()) {
                        for (ptr in ptrs) {
                            if (ptr == 0L) continue
                            val name = Natives.namefromSensorptr(ptr) ?: "Sensor"
                            val infoText = Natives.sensortextfromSensorptr(ptr) ?: ""
                            val warmup = try { Natives.getManualWarmupMinutes(ptr) } catch (_: Throwable) { 60 }
                            val minWarmup = try { Natives.getMinimalWarmup(ptr) } catch (_: Throwable) { 60 }
                            val isHidden = try { Natives.getHidefromSensorptr(ptr) } catch (_: Throwable) { false }
                            val hasCali = try { Natives.calibrateNR(ptr, 0) > 0 || Natives.calibrateNR(ptr, 1) > 0 } catch (_: Throwable) { false }

                            var end = 0L
                            try {
                                val endData = Natives.getSensorEndData(name)
                                val expectedSec = endData and 0xFFFFFFFFL
                                if (expectedSec > 0L) {
                                    end = expectedSec * 1000L
                                }
                            } catch (_: Throwable) {}
                            val start = if (end > 0L) end - 14 * 24 * 3600 * 1000L else 0L

                            detailsList.add(
                                SensorDetail(
                                    id = name,
                                    name = name,
                                    sensorPtr = ptr,
                                    status = SensorStatus.CONNECTED_STREAMING,
                                    sensorGen = 3,
                                    sensorTypeName = "FreeStyle Libre 3",
                                    macAddress = null,
                                    rssi = null,
                                    signalQuality = SignalQuality.GOOD,
                                    startTime = start,
                                    endTime = end,
                                    lastReadingTime = System.currentTimeMillis(),
                                    warmupMinutes = warmup,
                                    minWarmupMinutes = minWarmup,
                                    isConnected = true,
                                    isStreaming = true,
                                    isHidden = isHidden,
                                    hasCalibration = hasCali,
                                    batteryPercent = null,
                                    connectionStatusStr = "Connected",
                                    handshakeStatusStr = "Authenticated",
                                    rawDiagnosticText = infoText
                                )
                            )
                            legacyList.add(
                                SensorInfo(
                                    id = name,
                                    name = name,
                                    state = SensorState.ACTIVE,
                                    startTime = start,
                                    endTime = end,
                                    lastReadingTime = System.currentTimeMillis(),
                                    sensorType = if (infoText.isNotEmpty()) infoText else "Active Sensor",
                                    isConnected = true,
                                    isStreaming = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        _sensorDetails.value = detailsList
        _sensors.value = legacyList
        _previousSensors.value = emptyList()
    }

    private fun loadLogsFromNative() {
        var logList = ArrayList<LogRecord>()
        try {
            if (Applic.Nativesloaded && numio.numptrs.isNotEmpty() && numio.numptrs[0] != 0L) {
                val ptr = numio.numptrs[0]
                val first = Natives.getfirstNum(ptr)
                val last = Natives.getlastNum(ptr)
                for (pos in first..last) {
                    val itm = Natives.getNumitem(ptr, pos)
                    if (itm != null && itm.time > 0) {
                        val type = when (itm.label) {
                            0 -> LogType.RAPID_INSULIN
                            1 -> LogType.CARBS
                            2 -> LogType.BASAL_INSULIN
                            3 -> LogType.BLOOD_GLUCOSE
                            else -> LogType.NOTE
                        }
                        logList.add(
                            LogRecord(
                                id = itm.time * 1000L + pos,
                                timestamp = itm.time * 1000L,
                                type = type,
                                value = itm.value
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {}

        logList.sortByDescending { it.timestamp }
        _logs.value = logList
    }

    fun addLogEntry(type: LogType, value: Float, note: String, timestamp: Long = System.currentTimeMillis()) {
        val entry = LogRecord(
            id = System.nanoTime(),
            timestamp = timestamp,
            type = type,
            value = value,
            note = note
        )
        _logs.value = (listOf(entry) + _logs.value).sortedByDescending { it.timestamp }

        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded && numio.numptrs.isNotEmpty() && numio.numptrs[0] != 0L) {
                    val labelCode = when (type) {
                        LogType.RAPID_INSULIN -> 0
                        LogType.CARBS -> 1
                        LogType.BASAL_INSULIN -> 2
                        LogType.BLOOD_GLUCOSE -> 3
                        LogType.MEAL -> 1
                        LogType.NOTE -> 4
                    }
                    Natives.saveNum(numio.numptrs[0], timestamp / 1000L, value, labelCode, 0)
                }
            } catch (_: Throwable) {}
        }
    }

    fun deleteLogEntry(entry: LogRecord) {
        _logs.value = _logs.value.filterNot { it.id == entry.id }
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded && numio.numptrs.isNotEmpty() && numio.numptrs[0] != 0L) {
                    val ptr = numio.numptrs[0]
                    val first = Natives.getfirstNum(ptr)
                    val last = Natives.getlastNum(ptr)
                    val targetSec = entry.timestamp / 1000L
                    for (pos in first..last) {
                        val itm = Natives.getNumitem(ptr, pos)
                        if (itm != null && itm.time == targetSec) {
                            Natives.removeNum(ptr, pos)
                            break
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    // --- SENSOR ACTIONS (UX Overhaul) ---

    fun setWarmupMinutes(sensorPtr: Long, minutes: Int) {
        _sensorDetails.value = _sensorDetails.value.map {
            if (it.sensorPtr == sensorPtr || sensorPtr == 0L) it.copy(warmupMinutes = minutes) else it
        }
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded && sensorPtr != 0L) {
                    Natives.setManualWarmupMinutes(sensorPtr, minutes)
                }
            } catch (_: Throwable) {}
        }
    }

    fun setSensorHidden(sensorPtr: Long, hidden: Boolean) {
        _sensorDetails.value = _sensorDetails.value.map {
            if (it.sensorPtr == sensorPtr || sensorPtr == 0L) it.copy(isHidden = hidden) else it
        }
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded && sensorPtr != 0L) {
                    Natives.setHidefromSensorptr(sensorPtr, hidden)
                }
            } catch (_: Throwable) {}
        }
    }

    fun useSensorAgain(sensorPtr: Long, activity: Activity?) {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded && sensorPtr != 0L) {
                    SensorBridge.useAgain(activity as? MainActivity, sensorPtr)
                }
            } catch (_: Throwable) {}
            loadSensorsFromNative()
        }
    }

    fun forgetAndRescan(sensorId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                SensorBridge.forgetDevice(sensorId)
            } catch (_: Throwable) {}
            loadSensorsFromNative()
        }
    }

    fun resetSensor(sensorPtr: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded && sensorPtr != 0L) {
                    // SiBionics reset if supported
                }
            } catch (_: Throwable) {}
            loadSensorsFromNative()
        }
    }

    // --- ALARM CONFIG ACTIONS ---

    fun updateAlarms(config: AlarmConfig) {
        _alarms.value = config
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.setalarms(
                        config.lowThreshold,
                        config.highThreshold,
                        config.lowAlarmEnabled,
                        config.highAlarmEnabled,
                        config.valueAvailableNotification,
                        config.lossAlarmEnabled
                    )
                    Natives.writealarmsuspension(0, config.lowSnoozeMinutes.toShort())
                    Natives.writealarmsuspension(1, config.highSnoozeMinutes.toShort())
                    Natives.writealarmsuspension(4, config.lossWaitMinutes.toShort())
                    Natives.setalarmSoundType(config.soundStream.id)
                }
            } catch (_: Throwable) {}
        }
    }

    // --- EXCHANGES ACTIONS ---

    fun setXdripBroadcast(enabled: Boolean) {
        _exchanges.value = _exchanges.value.copy(xdripBroadcast = enabled)
    }

    fun setGlucodataBroadcast(enabled: Boolean) {
        _exchanges.value = _exchanges.value.copy(glucodataBroadcast = enabled)
    }

    fun setHealthConnect(enabled: Boolean, context: Activity?) {
        _exchanges.value = _exchanges.value.copy(healthConnect = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    SensorBridge.initHealthConnect(context as? MainActivity, enabled)
                }
            } catch (_: Throwable) {}
        }
    }

    fun setLibreViewEnabled(enabled: Boolean) {
        _exchanges.value = _exchanges.value.copy(libreViewEnabled = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.setuselibreview(enabled)
                }
            } catch (_: Throwable) {}
        }
    }

    fun setXdripWebServer(enabled: Boolean) {
        _exchanges.value = _exchanges.value.copy(xdripWebServer = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.setusexdripwebserver(enabled)
                }
            } catch (_: Throwable) {}
        }
    }

    fun setLibrelinkBroadcast(enabled: Boolean) {
        _exchanges.value = _exchanges.value.copy(librelinkBroadcast = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    if (enabled) {
                        val intent = Intent(XInfuus.glucoseaction)
                        val receivers = Applic.app.packageManager.queryBroadcastReceivers(intent, 0)
                        val names = receivers.mapNotNull { it.activityInfo?.packageName }.distinct()
                        val targetNames = if (names.isNotEmpty()) names.toTypedArray() else arrayOf("tk.glucodata.dev", "com.freestylelibre.app")
                        Natives.setlibrelinkRecepters(targetNames)
                    } else {
                        Natives.setlibrelinkRecepters(emptyArray())
                    }
                    XInfuus.setlibrenames()
                }
            } catch (_: Throwable) {}
        }
    }

    // --- MIRROR & DATA RELAY ACTIONS ---

    fun refreshMirrorConnections() {
        scope.launch(Dispatchers.IO) {
            val list = mutableListOf<MirrorConnection>()
            try {
                if (Applic.Nativesloaded) {
                    Applic.ensureNetStarted()
                    val count = try { Natives.backuphostNr() } catch (_: Throwable) { 0 }
                    for (i in 0 until count) {
                        val rawIps = try { Natives.getbackupIPs(i) } catch (_: Throwable) { null }
                        val ips = rawIps?.filterNotNull()?.filter { it.isNotBlank() } ?: emptyList()
                        val label = try { Natives.getbackuplabel(i) ?: "" } catch (_: Throwable) { "" }
                        val port = try { Natives.getbackuphostport(i) ?: "" } catch (_: Throwable) { "" }
                        val isReceiver = try { Natives.getbackuphostreceive(i) != 0 } catch (_: Throwable) { false }
                        val sendAmounts = try { Natives.getbackuphostnums(i) } catch (_: Throwable) { false }
                        val sendStream = try { Natives.getbackuphoststream(i) } catch (_: Throwable) { false }
                        val sendScans = try { Natives.getbackuphostscans(i) } catch (_: Throwable) { false }
                        val isActive = try { Natives.getbackuphostactive(i) } catch (_: Throwable) { false }
                        val isPassive = try { Natives.getbackuphostpassive(i) } catch (_: Throwable) { false }
                        val isDeactivated = try { Natives.getHostDeactivated(i) } catch (_: Throwable) { false }
                        val status = try { Natives.mirrorStatus(i) ?: "" } catch (_: Throwable) { "" }
                        list.add(
                            MirrorConnection(
                                index = i,
                                label = label,
                                ips = ips,
                                port = port,
                                isReceiver = isReceiver,
                                sendAmounts = sendAmounts,
                                sendStream = sendStream,
                                sendScans = sendScans,
                                isActive = isActive,
                                isPassive = isPassive,
                                isDeactivated = isDeactivated,
                                status = status
                            )
                        )
                    }
                }
            } catch (_: Throwable) {}
            _mirrorConnections.value = list
        }
    }

    fun addLocalReceiverConnection(targetPort: String = "17580", label: String = "Local Prod Sync"): Boolean {
        return try {
            if (!Applic.Nativesloaded) return false
            val portStr = targetPort.trim().ifEmpty { "17580" }
            val pos = Natives.changebackuphost(
                -1,
                arrayOf("127.0.0.1"),
                1,
                false,
                portStr,
                false,
                false,
                false,
                false,
                true,
                true,
                false,
                null,
                0L,
                label.trim().ifEmpty { "Local Prod Sync" },
                false,
                false,
                null,
                false,
                BleMirror.TRANSPORT_TCP,
                false
            )
            if (pos >= 0) {
                BleMirror.configurationChanged(pos, true)
                MessageSender.reinit()
                Applic.switchSync()
                refreshMirrorConnections()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun addLocalSenderConnection(targetPort: String = "17581", label: String = "Local Dev Build"): Boolean {
        return try {
            if (!Applic.Nativesloaded) return false
            val portStr = targetPort.trim().ifEmpty { "17581" }
            val pos = Natives.changebackuphost(
                -1,
                arrayOf("127.0.0.1"),
                1,
                false,
                portStr,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                null,
                0L,
                label.trim().ifEmpty { "Local Dev Build" },
                false,
                false,
                null,
                false,
                BleMirror.TRANSPORT_TCP,
                false
            )
            if (pos >= 0) {
                BleMirror.configurationChanged(pos, true)
                MessageSender.reinit()
                Applic.switchSync()
                refreshMirrorConnections()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun addCustomMirrorConnection(
        ip: String,
        port: String,
        isReceiver: Boolean,
        label: String,
        sendStream: Boolean = true,
        sendScans: Boolean = true,
        sendAmounts: Boolean = true
    ): Boolean {
        return try {
            if (!Applic.Nativesloaded) return false
            val cleanIp = ip.trim().ifEmpty { "127.0.0.1" }
            val cleanPort = port.trim().ifEmpty { "17580" }
            val pos = Natives.changebackuphost(
                -1,
                arrayOf(cleanIp),
                1,
                false,
                cleanPort,
                if (isReceiver) false else sendAmounts,
                if (isReceiver) false else sendStream,
                if (isReceiver) false else sendScans,
                false,
                isReceiver,
                isReceiver,
                false,
                null,
                0L,
                label.trim().ifEmpty { if (isReceiver) "Receiver" else "Sender" },
                false,
                false,
                null,
                false,
                BleMirror.TRANSPORT_TCP,
                false
            )
            if (pos >= 0) {
                BleMirror.configurationChanged(pos, true)
                MessageSender.reinit()
                Applic.switchSync()
                refreshMirrorConnections()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun saveMirrorConnection(
        index: Int,
        ips: List<String>,
        port: String,
        isReceiver: Boolean,
        label: String,
        sendStream: Boolean = true,
        sendScans: Boolean = true,
        sendAmounts: Boolean = true,
        isActiveOnly: Boolean = false,
        isPassiveOnly: Boolean = false,
        password: String? = null
    ): Boolean {
        return try {
            if (!Applic.Nativesloaded) return false
            val cleanIps = ips.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("127.0.0.1") }
            val cleanPort = port.trim().ifEmpty { "17580" }
            val pos = Natives.changebackuphost(
                index,
                cleanIps.toTypedArray(),
                cleanIps.size,
                false,
                cleanPort,
                if (isReceiver) false else sendAmounts,
                if (isReceiver) false else sendStream,
                if (isReceiver) false else sendScans,
                false,
                isReceiver,
                isActiveOnly || isReceiver,
                isPassiveOnly,
                password?.ifBlank { null },
                0L,
                label.trim().ifEmpty { if (isReceiver) "Receiver" else "Sender" },
                false,
                false,
                null,
                false,
                BleMirror.TRANSPORT_TCP,
                false
            )
            if (pos >= 0) {
                BleMirror.configurationChanged(pos, true)
                MessageSender.reinit()
                Applic.switchSync()
                refreshMirrorConnections()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun deleteMirrorConnection(index: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded && index >= 0) {
                    Natives.deletebackuphost(index)
                    BleMirror.configurationChanged()
                    MessageSender.reinit()
                    refreshMirrorConnections()
                }
            } catch (_: Throwable) {}
        }
    }

    fun resetMirrorConnection(index: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded && index >= 0) {
                    Natives.resetbackuphost(index)
                    Applic.switchSync()
                    MessageSender.reinit()
                    refreshMirrorConnections()
                }
            } catch (_: Throwable) {}
        }
    }

    fun setMirrorReceivePort(port: String): Boolean {
        return try {
            val cleanPort = port.trim()
            val num = cleanPort.toIntOrNull()
            if (num != null && num in 1024..65535) {
                Natives.setreceiveport(cleanPort)
                MessageSender.reinit()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun openAdvancedMirrorView(activity: Activity) {
        if (activity is MainActivity) {
            try {
                Backup().realmkbackupview(activity, false)
            } catch (_: Throwable) {}
        }
    }

    fun openWebServerConfig(activity: Activity) {
        if (activity is MainActivity) {
            try {
                Nightscout.show(activity, activity.window.decorView)
            } catch (_: Throwable) {}
        }
    }

    // --- DISPLAY & UI ACTIONS ---

    fun setFloatingGlucose(enabled: Boolean, activity: Activity) {
        _displayConfig.value = _displayConfig.value.copy(floatingGlucose = enabled)
        scope.launch(Dispatchers.Main) {
            try {
                tk.glucodata.Floating.setfloatglucose(activity, enabled)
            } catch (_: Throwable) {}
        }
    }

    fun setStatusBarNotification(enabled: Boolean) {
        _displayConfig.value = _displayConfig.value.copy(statusBarNotification = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                Notify.glucosestatus(enabled)
            } catch (_: Throwable) {}
        }
    }

    fun setSystemUiFullscreen(enabled: Boolean, activity: Activity?) {
        _displayConfig.value = _displayConfig.value.copy(systemUiFullscreen = enabled)
        scope.launch(Dispatchers.Main) {
            try {
                if (Applic.Nativesloaded) {
                    SensorBridge.setSystemUi(activity as? MainActivity, enabled)
                }
            } catch (_: Throwable) {}
        }
    }

    fun setInvertColors(enabled: Boolean) {
        _displayConfig.value = _displayConfig.value.copy(invertColors = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.setInvertColors(enabled)
                }
            } catch (_: Throwable) {}
        }
    }

    fun setCalibrationEnabled(enabled: Boolean) {
        _displayConfig.value = _displayConfig.value.copy(calibrationEnabled = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.setDoCalibrate(enabled)
                    Natives.setshowcalibratedstream(enabled)
                }
            } catch (_: Throwable) {}
        }
    }

    fun setCalibratePastReadings(enabled: Boolean) {
        _displayConfig.value = _displayConfig.value.copy(calibratePastReadings = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.setCalibratePast(enabled)
                }
            } catch (_: Throwable) {}
        }
    }

    fun setCalibrateAllValues(enabled: Boolean) {
        _displayConfig.value = _displayConfig.value.copy(calibrateAllValues = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.setAllValues(enabled)
                }
            } catch (_: Throwable) {}
        }
    }

    // Only true once: before the user has ever been asked, and only while the feature is off.
    fun shouldPromptCalibrationEnable(): Boolean {
        if (_displayConfig.value.calibrationEnabled) return false
        return try {
            !Applic.app.getSharedPreferences(CALIBRATION_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_CALIBRATION_PROMPT_SHOWN, false)
        } catch (_: Throwable) {
            false
        }
    }

    fun markCalibrationPromptShown() {
        try {
            Applic.app.getSharedPreferences(CALIBRATION_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CALIBRATION_PROMPT_SHOWN, true)
                .apply()
        } catch (_: Throwable) {}
    }

    fun toggleGraphLayer(layer: String, enabled: Boolean) {
        val current = _displayConfig.value
        val updated = when (layer) {
            "scans" -> current.copy(showScans = enabled)
            "calibratedscans" -> current.copy(showCalibratedScans = enabled)
            "stream" -> current.copy(showStream = enabled)
            "calibrated", "calibratedstream" -> current.copy(showCalibratedStream = enabled)
            "history" -> current.copy(showHistory = enabled)
            "calibratedhistory" -> current.copy(showCalibratedHistory = enabled)
            "amounts" -> current.copy(showAmounts = enabled)
            "meals" -> current.copy(showMeals = enabled)
            else -> current
        }
        _displayConfig.value = updated
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    when (layer) {
                        "scans" -> Natives.setshowscans(enabled)
                        "calibratedscans" -> Natives.setshowcalibratedscans(enabled)
                        "stream" -> Natives.setshowstream(enabled)
                        "calibrated", "calibratedstream" -> Natives.setshowcalibratedstream(enabled)
                        "history" -> Natives.setshowhistories(enabled)
                        "calibratedhistory" -> Natives.setshowcalibratedhistories(enabled)
                        "amounts" -> Natives.setshownumbers(enabled)
                        "meals" -> Natives.setshowmeals(enabled)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    fun setMinimalistUnits(enabled: Boolean) {
        _displayConfig.value = _displayConfig.value.copy(minimalistUnits = enabled)
        scope.launch(Dispatchers.IO) {
            try {
                Applic.app.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_MINIMALIST_UNITS, enabled)
                    .apply()
            } catch (_: Throwable) {}
        }
    }

    fun setDeltaCalculation(calculation: DeltaCalculation) {
        _displayConfig.value = _displayConfig.value.copy(deltaCalculation = calculation)
        scope.launch(Dispatchers.IO) {
            try {
                Applic.app.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_DELTA_CALCULATION, calculation.minutes)
                    .apply()
            } catch (_: Throwable) {}
        }
    }

    // --- GRAPH NAVIGATION ACTIONS ---

    fun jumpToNow() {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.settonow()
                }
            } catch (_: Throwable) {}
        }
    }

    fun navigateDays(days: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    if (days < 0) Natives.prevday(-days) else Natives.nextday(days)
                }
            } catch (_: Throwable) {}
        }
    }

    fun showLastScan() {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.showlastscan()
                }
            } catch (_: Throwable) {}
        }
    }

    fun moveToDate(year: Int, month: Int, day: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    val start = Natives.getstarttime()
                    Natives.movedate(start, year, month, day)
                }
            } catch (_: Throwable) {}
        }
    }

    fun searchGlucose(
        label: Int = -1,
        under: Float = 0f,
        above: Float = 0f,
        keyword: String = ""
    ): List<Long> {
        val matches = mutableListOf<Long>()

        // 1. Search in glucose readings
        if (under > 0f || above > 0f) {
            for (pt in _readings.value) {
                if (under > 0f && pt.valueMgDl <= under) {
                    matches.add(pt.timestamp)
                } else if (above > 0f && pt.valueMgDl >= above) {
                    matches.add(pt.timestamp)
                }
            }
        }

        // 2. Search in event logs
        for (log in _logs.value) {
            val categoryMatches = when (label) {
                0 -> log.type == LogType.RAPID_INSULIN
                1 -> log.type == LogType.CARBS || log.type == LogType.MEAL
                2 -> log.type == LogType.BASAL_INSULIN
                3 -> log.type == LogType.BLOOD_GLUCOSE
                else -> true
            }
            val keywordMatches = if (keyword.isNotEmpty()) {
                (log.note?.contains(keyword, ignoreCase = true) == true) ||
                    log.type.label.contains(keyword, ignoreCase = true)
            } else true

            if (categoryMatches && keywordMatches) {
                matches.add(log.timestamp)
            }
        }

        try {
            if (Applic.Nativesloaded) {
                Natives.search(label, under, above, 0, 0, true, keyword, 0f)
            }
        } catch (_: Throwable) {}

        matches.sort()
        return matches.distinct()
    }

    fun nextSearchMatch() {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.latersearch()
                }
            } catch (_: Throwable) {}
            refreshAll()
        }
    }

    fun prevSearchMatch() {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.earliersearch()
                }
            } catch (_: Throwable) {}
            refreshAll()
        }
    }

    fun stopSearch() {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.stopsearch()
                }
            } catch (_: Throwable) {}
            refreshAll()
        }
    }

    private fun recalculateStats() {
        val all = _readings.value
        if (all.isEmpty()) {
            _stats.value = GlucoseStats()
            _screenStats.value = GlucoseStats()
            _agpProfile.value = AgpProfile.calculate(emptyList(), _statsPeriod.value)
            return
        }

        val cutoff = System.currentTimeMillis() - _statsPeriod.value.durationMillis
        val filtered = all.filter { it.timestamp >= cutoff }
        val toUse = if (filtered.isNotEmpty()) filtered else all

        _stats.value = GlucoseStats.calculate(toUse, _targetLow.value, _targetHigh.value)
        _agpProfile.value = AgpProfile.calculate(toUse, _statsPeriod.value)

        // Calculate screen stats specifically for the selected time range window (e.g. 1h, 6h, or custom duration)
        val screenDuration = _selectedTimeRange.value?.durationMillis ?: (6 * 3600 * 1000L)
        val screenCutoff = System.currentTimeMillis() - screenDuration
        val screenFiltered = all.filter { it.timestamp >= screenCutoff }
        val screenToUse = if (screenFiltered.isNotEmpty()) screenFiltered else all
        _screenStats.value = GlucoseStats.calculate(screenToUse, _targetLow.value, _targetHigh.value)
    }

    private fun startPolling() {
        scope.launch(Dispatchers.IO) {
            var counter = 0
            while (isActive) {
                delay(3_000L)
                try {
                    counter++
                    if (Applic.Nativesloaded) {
                        val strGl = Natives.lastglucose()
                        if (strGl != null && strGl.time > 0) {
                            val rawVal = try {
                                strGl.value.replace(',', '.').toFloat()
                            } catch (_: Throwable) { 0f }
                            val valMgDl = if (_unit.value == GlucoseUnit.MMOL_L) {
                                GlucoseUnit.MMOL_L.toMgDl(rawVal)
                            } else rawVal

                            if (valMgDl > 0f) {
                                val newPt = GlucosePoint(
                                    timestamp = strGl.time * 1000L,
                                    valueMgDl = valMgDl,
                                    rate = strGl.rate,
                                    status = GlucoseStatus.fromValue(valMgDl, _targetLow.value, _targetHigh.value)
                                )
                                _currentReading.value = newPt
                                val currentList = _readings.value
                                if (currentList.none { it.timestamp == newPt.timestamp }) {
                                    _readings.value = (currentList + newPt).sortedBy { it.timestamp }
                                    recalculateStats()
                                }
                            }
                        }
                    }
                    if (counter % 3 == 0) {
                        loadReadingsFromNative()
                        loadSensorsFromNative()
                        loadLogsFromNative()
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private companion object {
        const val CALIBRATION_PREFS = "calibration_prefs"
        const val KEY_CALIBRATION_PROMPT_SHOWN = "calibration_prompt_shown"
        const val UI_PREFS = "ui_prefs"
        const val KEY_DELTA_CALCULATION = "delta_calculation_minutes"
        const val KEY_MINIMALIST_UNITS = "minimalist_units"
    }
}
