package tk.glucodata.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.glucodata.Applic
import tk.glucodata.Dialogs
import tk.glucodata.MainActivity
import tk.glucodata.Natives
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository

data class ExportStreamOption(
    val typeIndex: Int,
    val titleRes: Int,
    val descRes: Int,
    val fileExtension: String,
    val icon: ImageVector,
    val supportsCalibration: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    repository: GlucoseRepository,
    onNavigateBack: () -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Calculate maximum available history days
    val maxHistoryDays = remember {
        try {
            val hour24 = 1000 * 60 * 60 * 24L
            val endtime = Natives.getendtime()
            val oldest = Natives.oldestdatatime()
            if (oldest > 0 && endtime > oldest) {
                ((endtime - oldest + hour24 - 1) / hour24).toInt().coerceAtLeast(1)
            } else {
                90
            }
        } catch (_: Throwable) {
            90
        }
    }

    // Stream options
    val streamOptions = remember {
        listOf(
            ExportStreamOption(
                typeIndex = 2,
                titleRes = R.string.streamname,
                descRes = R.string.export_stream_desc,
                fileExtension = ".tsv",
                icon = Icons.AutoMirrored.Filled.ShowChart,
                supportsCalibration = true
            ),
            ExportStreamOption(
                typeIndex = 3,
                titleRes = R.string.historyname,
                descRes = R.string.export_history_desc,
                fileExtension = ".tsv",
                icon = Icons.Default.History,
                supportsCalibration = true
            ),
            ExportStreamOption(
                typeIndex = 0,
                titleRes = R.string.amountsname,
                descRes = R.string.export_amounts_desc,
                fileExtension = ".tsv",
                icon = Icons.Default.Medication,
                supportsCalibration = false
            ),
            ExportStreamOption(
                typeIndex = 1,
                titleRes = R.string.scansname,
                descRes = R.string.export_scans_desc,
                fileExtension = ".tsv",
                icon = Icons.Default.Nfc,
                supportsCalibration = true
            ),
            ExportStreamOption(
                typeIndex = 4,
                titleRes = R.string.mealsname,
                descRes = R.string.export_meals_desc,
                fileExtension = ".html",
                icon = Icons.Default.Restaurant,
                supportsCalibration = false
            ),
            ExportStreamOption(
                typeIndex = 5,
                titleRes = R.string.libreviewname,
                descRes = R.string.export_libreview_desc,
                fileExtension = ".csv",
                icon = Icons.Default.TableChart,
                supportsCalibration = true
            )
        )
    }

    var selectedTypeIndex by rememberSaveable { mutableIntStateOf(2) }
    var selectedPresetDays by rememberSaveable { mutableStateOf<Int?>(14) }
    var customDaysText by rememberSaveable { mutableStateOf("14") }
    var isCustomRangeActive by rememberSaveable { mutableStateOf(false) }

    var isCalibrated by rememberSaveable {
        mutableStateOf(try { Natives.getDoCalibrate() } catch (_: Throwable) { false })
    }

    val activeOption = streamOptions.firstOrNull { it.typeIndex == selectedTypeIndex } ?: streamOptions[0]

    val effectiveDays: Float = if (isCustomRangeActive) {
        customDaysText.toFloatOrNull()?.coerceIn(0.1f, 3650f) ?: 14f
    } else {
        selectedPresetDays?.toFloat() ?: maxHistoryDays.toFloat()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.export_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.closename)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.export_glucose_data),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.export_screen_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 1. Time Range Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.export_time_range_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Range preset chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(7, 14, 30, 90)
                        presets.forEach { days ->
                            FilterChip(
                                selected = !isCustomRangeActive && selectedPresetDays == days,
                                onClick = {
                                    isCustomRangeActive = false
                                    selectedPresetDays = days
                                    customDaysText = days.toString()
                                },
                                label = { Text("$days ${stringResource(R.string.days)}", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        // All Available Data preset
                        FilterChip(
                            selected = !isCustomRangeActive && selectedPresetDays == maxHistoryDays,
                            onClick = {
                                isCustomRangeActive = false
                                selectedPresetDays = maxHistoryDays
                                customDaysText = maxHistoryDays.toString()
                            },
                            label = { Text(stringResource(R.string.timerange_all), fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        // Custom chip
                        FilterChip(
                            selected = isCustomRangeActive,
                            onClick = { isCustomRangeActive = true },
                            label = {
                                Text(
                                    if (isCustomRangeActive) "$customDaysText ${stringResource(R.string.days)}" else stringResource(R.string.timerange_custom),
                                    fontSize = 12.sp,
                                    fontWeight = if (isCustomRangeActive) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    if (isCustomRangeActive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customDaysText,
                            onValueChange = { input ->
                                val filtered = input.filter { it.isDigit() || it == '.' }
                                customDaysText = filtered
                            },
                            label = { Text(stringResource(R.string.export_days_count)) },
                            suffix = { Text(stringResource(R.string.days)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 2. Data Stream & Format Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.export_select_stream),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    streamOptions.forEach { option ->
                        val isSelected = selectedTypeIndex == option.typeIndex
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedTypeIndex = option.typeIndex },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedTypeIndex = option.typeIndex }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = stringResource(option.titleRes),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = option.fileExtension,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = stringResource(option.descRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Calibration Toggle Card
            if (activeOption.supportsCalibration) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.export_include_calibrated),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.export_calibrated_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isCalibrated,
                            onCheckedChange = { isCalibrated = it }
                        )
                    }
                }
            }

            // 4. Summary & Action Button
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Summary: ${stringResource(activeOption.titleRes)} (${activeOption.fileExtension}) • ${effectiveDays.toInt()} ${stringResource(R.string.days)}" +
                            if (activeOption.supportsCalibration && isCalibrated) " • ${stringResource(R.string.calibrated)}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val activity = context as? MainActivity
                            if (activity != null) {
                                try {
                                    Dialogs.runExport(activity, selectedTypeIndex, isCalibrated, effectiveDays)
                                } catch (e: Throwable) {
                                    Applic.argToaster(activity, "Export error: ${e.message}", Toast.LENGTH_SHORT)
                                }
                            } else {
                                Toast.makeText(context, "Export initialized for ${effectiveDays.toInt()} days", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.export_action_button),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
