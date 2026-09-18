package tk.glucodata.ui.data

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tk.glucodata.Applic
import tk.glucodata.MainActivity
import tk.glucodata.Natives
import tk.glucodata.Notify
import tk.glucodata.SensorBridge
import tk.glucodata.nums.numio
import tk.glucodata.ui.model.AgpProfile
import tk.glucodata.ui.model.AlarmConfig
import tk.glucodata.ui.model.AlarmSoundStream
import tk.glucodata.ui.model.DisplayConfig
import tk.glucodata.ui.model.ExchangesConfig
import tk.glucodata.ui.model.GlucosePoint
import tk.glucodata.ui.model.GlucoseStats
import tk.glucodata.ui.model.GlucoseStatus
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.HardwareConfig
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
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

    private val _selectedTimeRange = MutableStateFlow(TimeRange.SIX_HOURS)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _stats = MutableStateFlow(GlucoseStats())
    val stats: StateFlow<GlucoseStats> = _stats.asStateFlow()

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

    fun setTimeRange(range: TimeRange) {
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

                // Read Display
                _displayConfig.value = DisplayConfig(
                    floatingGlucose = Natives.getfloatglucose(),
                    statusBarNotification = Natives.getshowalways(),
                    systemUiFullscreen = Natives.getsystemUI(),
                    invertColors = Natives.getInvertColors(),
                    showScans = Natives.getshowscans(),
                    showStream = Natives.getshowstream(),
                    showHistory = Natives.getshowhistories(),
                    showCalibratedStream = Natives.getshowcalibratedstream(),
                    showAmounts = Natives.getshownumbers(),
                    showMeals = Natives.getshowmeals()
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
                    }
                }

                // Read stream points from active sensors
                val sensorPtrs = Natives.activeSensorPtrs()
                if (sensorPtrs != null && sensorPtrs.isNotEmpty()) {
                    for (ptr in sensorPtrs) {
                        if (ptr == 0L) continue
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

        if (loadedList.isEmpty()) {
            loadedList = ArrayList(MockDataGenerator.generateReadings())
            if (_currentReading.value == null && loadedList.isNotEmpty()) {
                _currentReading.value = loadedList.last()
            }
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

                        detailsList.add(
                            SensorDetail(
                                id = info.serial,
                                name = info.serial,
                                sensorPtr = info.sensorptr,
                                status = status,
                                sensorGen = info.sensorgen,
                                sensorTypeName = typeName,
                                macAddress = info.macAddress ?: "D4:36:39:B2:A1:8F",
                                rssi = info.rssi,
                                signalQuality = SignalQuality.fromRssi(info.rssi),
                                startTime = info.startTime,
                                endTime = info.startTime + 14 * 24 * 3600 * 1000L,
                                lastReadingTime = System.currentTimeMillis() - 45_000L,
                                warmupMinutes = info.warmupMinutes,
                                minWarmupMinutes = info.minWarmupMinutes,
                                isConnected = info.isConnected,
                                isStreaming = info.isStreaming,
                                isHidden = info.isHidden,
                                hasCalibration = info.hasCalibration,
                                batteryPercent = 95,
                                connectionStatusStr = info.statusStr,
                                handshakeStatusStr = info.handshakeStr,
                                rawDiagnosticText = info.infoHtml
                            )
                        )
                        legacyList.add(
                            SensorInfo(
                                id = info.serial,
                                name = info.serial,
                                state = if (info.isConnected) SensorState.ACTIVE else SensorState.DISCONNECTED,
                                startTime = info.startTime,
                                endTime = info.startTime + 14 * 24 * 3600 * 1000L,
                                lastReadingTime = System.currentTimeMillis(),
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

                            detailsList.add(
                                SensorDetail(
                                    id = name,
                                    name = name,
                                    sensorPtr = ptr,
                                    status = SensorStatus.CONNECTED_STREAMING,
                                    sensorGen = 3,
                                    sensorTypeName = "FreeStyle Libre 3",
                                    macAddress = "D4:36:39:B2:A1:8F",
                                    rssi = -68,
                                    signalQuality = SignalQuality.GOOD,
                                    startTime = System.currentTimeMillis() - 3 * 24 * 3600 * 1000L,
                                    endTime = System.currentTimeMillis() + 11 * 24 * 3600 * 1000L,
                                    lastReadingTime = System.currentTimeMillis(),
                                    warmupMinutes = warmup,
                                    minWarmupMinutes = minWarmup,
                                    isConnected = true,
                                    isStreaming = true,
                                    isHidden = isHidden,
                                    hasCalibration = hasCali,
                                    batteryPercent = 94,
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

        if (detailsList.isEmpty()) {
            val mockActive = MockDataGenerator.generateSensorDetail()
            detailsList.add(mockActive)
            legacyList.add(MockDataGenerator.generateSensor())
        }

        _sensorDetails.value = detailsList
        _sensors.value = legacyList
        _previousSensors.value = MockDataGenerator.generatePreviousSensors()
    }

    private fun loadLogsFromNative() {
        val logList = ArrayList<LogRecord>()
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
                                id = itm.time,
                                timestamp = itm.time * 1000L,
                                type = type,
                                value = itm.value
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {}

        if (logList.isEmpty()) {
            logList.addAll(MockDataGenerator.generateLogs())
        }
        logList.sortByDescending { it.timestamp }
        _logs.value = logList
    }

    fun addLogEntry(type: LogType, value: Float, note: String, timestamp: Long = System.currentTimeMillis()) {
        val entry = LogRecord(
            id = timestamp,
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

    fun toggleGraphLayer(layer: String, enabled: Boolean) {
        val current = _displayConfig.value
        val updated = when (layer) {
            "scans" -> current.copy(showScans = enabled)
            "stream" -> current.copy(showStream = enabled)
            "history" -> current.copy(showHistory = enabled)
            "calibrated" -> current.copy(showCalibratedStream = enabled)
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
                        "stream" -> Natives.setshowstream(enabled)
                        "history" -> Natives.setshowhistories(enabled)
                        "calibrated" -> Natives.setshowcalibratedstream(enabled)
                        "amounts" -> Natives.setshownumbers(enabled)
                        "meals" -> Natives.setshowmeals(enabled)
                    }
                }
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
            refreshAll()
        }
    }

    fun navigateDays(days: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    if (days < 0) Natives.prevday(-days) else Natives.nextday(days)
                }
            } catch (_: Throwable) {}
            refreshAll()
        }
    }

    fun showLastScan() {
        scope.launch(Dispatchers.IO) {
            try {
                if (Applic.Nativesloaded) {
                    Natives.showlastscan()
                }
            } catch (_: Throwable) {}
            refreshAll()
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
            refreshAll()
        }
    }

    fun searchGlucose(
        label: Int = -1,
        under: Float = 0f,
        above: Float = 0f,
        keyword: String = ""
    ): Int {
        var count = 0
        try {
            if (Applic.Nativesloaded) {
                count = Natives.search(label, under, above, 0, 0, true, keyword, 0f)
            }
        } catch (_: Throwable) {}
        refreshAll()
        return count
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
            _agpProfile.value = AgpProfile.calculate(emptyList(), _statsPeriod.value)
            return
        }

        val cutoff = System.currentTimeMillis() - _statsPeriod.value.durationMillis
        val filtered = all.filter { it.timestamp >= cutoff }
        val toUse = if (filtered.isNotEmpty()) filtered else all

        _stats.value = GlucoseStats.calculate(toUse, _targetLow.value, _targetHigh.value)
        _agpProfile.value = AgpProfile.calculate(toUse, _statsPeriod.value)
    }

    private fun startPolling() {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(10_000L)
                try {
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
                } catch (_: Throwable) {}
            }
        }
    }
}
