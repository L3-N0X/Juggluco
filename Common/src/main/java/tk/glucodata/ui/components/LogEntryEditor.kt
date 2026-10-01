package tk.glucodata.ui.components

import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.R
import tk.glucodata.ui.data.GlucoseRepository
import tk.glucodata.ui.data.MealStore
import tk.glucodata.ui.model.GlucoseUnit
import tk.glucodata.ui.model.Ingredient
import tk.glucodata.ui.model.LogRecord
import tk.glucodata.ui.model.LogType
import tk.glucodata.ui.model.MealItem
import tk.glucodata.ui.model.NumberStore
import tk.glucodata.ui.model.toStoredLogValue
import tk.glucodata.ui.screens.ScreenLayout
import tk.glucodata.ui.theme.LocalLogbookColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DateFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.TimeZone
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 24 * HOUR_MS

private val SAVE_BLOCK_HEIGHT = 56.dp + 12.dp
private val FORM_MIN_HEIGHT = 300.dp
private val KEYPAD_GAPS = 8.dp * 3 + 12.dp
private val MIN_KEY_HEIGHT = 40.dp
private val MAX_KEY_HEIGHT = 52.dp

/** A tile of the editor: an entry type, plus the label of a custom one. */
private data class Field(val type: LogType, val label: Int = -1)

private val CarbsField = Field(LogType.CARBS)

/** The pair most meals are logged with, shown side by side on every new entry. */
private val PrimaryFields = listOf(CarbsField, Field(LogType.RAPID_INSULIN))

/** Logged a few times a day at most, so they are one tap away instead of always on screen. */
private val OccasionalFields = listOf(Field(LogType.BASAL_INSULIN), Field(LogType.BLOOD_GLUCOSE))

/** Order in which a combined entry is saved; the note goes to the first entry saved. */
private val SaveOrder = listOf(
    LogType.CARBS,
    LogType.RAPID_INSULIN,
    LogType.BASAL_INSULIN,
    LogType.BLOOD_GLUCOSE,
    LogType.CUSTOM
)

/**
 * Full-screen editor for logbook entries.
 *
 * A new entry opens on carbs and bolus side by side, since those are usually logged together,
 * with basal and finger-pricks one tap away. Amounts are typed on an in-app number pad, and the
 * quick values next to it are the user's own most used amounts rather than fixed presets. When
 * [entry] is set, the same screen edits (or deletes) that single entry.
 *
 * It lives in its own window, so it can be opened on top of the logbook sheet as well as the
 * main screen. [onSaved] receives a ready-to-show confirmation for new entries.
 */
@Composable
fun LogEntryEditor(
    repository: GlucoseRepository,
    onDismiss: () -> Unit,
    entry: LogRecord? = null,
    onSaved: (String) -> Unit = {}
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState && !visibleState.targetState) onDismiss()
    }
    val close = { visibleState.targetState = false }

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        EditorWindowSetup()
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(initialOffsetY = { it / 5 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 5 }) + fadeOut()
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                EditorContent(repository = repository, entry = entry, onClose = close, onSaved = onSaved)
            }
        }
    }
}

/**
 * Makes the dialog window edge to edge, with system bar icons that match the surface.
 *
 * From Android 15 the screen height Compose sizes a full-width dialog to includes the system bars,
 * while a dialog window is still kept clear of them, so the bottom of the editor (the save button)
 * would hang off the screen. The window is therefore allowed to cover the bars as well; the
 * content keeps clear of them through its own safe-drawing padding.
 */
@Suppress("DEPRECATION")
@Composable
internal fun EditorWindowSetup() {
    val view = LocalView.current
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            window.attributes = window.attributes.apply {
                fitInsetsTypes = 0
                fitInsetsSides = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightSurface
            isAppearanceLightNavigationBars = lightSurface
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditorContent(
    repository: GlucoseRepository,
    entry: LogRecord?,
    onClose: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val unit by repository.unit.collectAsState()
    val logs by repository.logs.collectAsState()
    val labels by repository.labelConfig.collectAsState()
    val editing = entry != null
    val entryField = entry?.let {
        when (it.type) {
            LogType.CUSTOM -> Field(LogType.CUSTOM, it.nativeLabel ?: -1)
            LogType.MEAL -> CarbsField
            else -> Field(it.type)
        }
    }

    val fields = remember { mutableStateListOf<Field>().apply { addAll(entryField?.let(::listOf) ?: PrimaryFields) } }
    val amounts = remember {
        mutableStateMapOf<Field, String>().apply {
            if (entry != null && entryField != null) {
                val shown = if (entryField.type == LogType.BLOOD_GLUCOSE) unit.toDisplay(entry.value) else entry.value
                put(entryField, plainAmount(shown, amountSpec(entryField.type, unit).decimals))
            }
        }
    }
    var activeField by remember { mutableStateOf(entryField ?: CarbsField) }
    var customTime by remember { mutableStateOf(entry?.timestamp) }
    var note by remember { mutableStateOf(entry?.note.orEmpty()) }
    var noteFocused by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // A meal is composed from ingredients and attached to the carbs entry. Like in the classic
    // view this is a phone feature, and only for entries this device logged itself.
    val mealsAvailable = MealStore.available && labels.editable &&
        (entry == null || (entryField == CarbsField && entry.nativeSource?.store == NumberStore.HERE))
    var mealItems by remember { mutableStateOf<List<MealItem>>(emptyList()) }
    var mealChanged by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<List<Ingredient>>(emptyList()) }
    var showMealComposer by remember { mutableStateOf(false) }
    LaunchedEffect(mealsAvailable) {
        if (!mealsAvailable) return@LaunchedEffect
        val (loadedCatalog, loadedItems) = withContext(Dispatchers.IO) {
            MealStore.ingredients() to (entry?.takeIf { it.mealPointer > 0 }?.let { MealStore.readMeal(it.mealPointer) }.orEmpty())
        }
        catalog = loadedCatalog
        if (!mealChanged) mealItems = loadedItems
    }
    val mealSummary = mealItems.mapNotNull { catalog.getOrNull(it.ingredient)?.name }.joinToString(", ")

    val history by produceState(LogHistory(), logs, unit) {
        value = withContext(Dispatchers.Default) { analyzeHistory(logs, unit, System.currentTimeMillis()) }
    }

    val separator = remember { DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator }
    val glucoseUnitLabel = stringResource(unit.labelRes)
    val insulinUnitLabel = stringResource(R.string.unit_insulin_short)
    val carbsUnitLabel = stringResource(R.string.unit_carbs_short)
    val customFallback = stringResource(R.string.log_type_custom)
    val typeNames = LogType.entries.associateWith { stringResource(it.shortLabelRes) }
    fun unitLabel(field: Field): String = when {
        field.type == LogType.BLOOD_GLUCOSE -> glucoseUnitLabel
        field.type.unitLabelRes == R.string.unit_carbs_short -> carbsUnitLabel
        field.type.unitLabelRes == R.string.unit_insulin_short -> insulinUnitLabel
        else -> ""
    }
    fun fieldName(field: Field): String = if (field.type == LogType.CUSTOM) {
        labels.nameOf(field.label).ifBlank { entry?.labelName.orEmpty() }.ifBlank { customFallback }
    } else {
        typeNames.getValue(field.type)
    }
    fun shown(text: String) = text.replace('.', separator)

    val keypadVisible = !(noteFocused && WindowInsets.isImeVisible)

    // A field is saved when it holds a value inside its range. A value that is too small is only
    // an error once the user has moved on: "0." on the way to "0.5" is still being typed.
    val parsed = fields.associateWith { field ->
        val text = amounts[field].orEmpty()
        val spec = amountSpec(field.type, unit)
        val value = text.toFloatOrNull()
        val typing = field == activeField && keypadVisible
        when {
            text.isEmpty() -> AmountState.Empty
            value == null || value > spec.max -> AmountState.Invalid
            value < spec.min -> if (typing) AmountState.Incomplete else AmountState.Invalid
            else -> AmountState.Valid(value)
        }
    }
    val saveOrder = fields.sortedBy { field -> SaveOrder.indexOf(field.type) }
    val validParts = saveOrder.mapNotNull { field ->
        if (parsed[field] !is AmountState.Valid) return@mapNotNull null
        val amount = shown(amounts[field].orEmpty())
        field to if (field.type == LogType.CUSTOM) {
            "${fieldName(field)} $amount"
        } else {
            formatAmount(field.type, amount, unitLabel(field))
        }
    }
    val canSave = validParts.isNotEmpty() &&
        parsed.values.none { it == AmountState.Invalid || it == AmountState.Incomplete }

    fun select(field: Field) {
        focusManager.clearFocus()
        activeField = field
    }

    fun edit(transform: (String, AmountSpec) -> String) {
        val spec = amountSpec(activeField.type, unit)
        amounts[activeField] = transform(amounts[activeField].orEmpty(), spec)
    }

    val bolusField = PrimaryFields[1]
    fun pickQuickValue(value: Float) {
        amounts[activeField] = plainAmount(value, amountSpec(activeField.type, unit).decimals)
        // Carbs are usually followed by the bolus for them, so move straight on to it.
        if (activeField == CarbsField && bolusField in fields && amounts[bolusField].isNullOrEmpty()) {
            activeField = bolusField
        }
    }

    fun addField(field: Field, prefill: Float? = null) {
        if (field !in fields) fields.add(field)
        if (prefill != null) amounts[field] = plainAmount(prefill, amountSpec(field.type, unit).decimals)
        select(field)
    }

    fun removeField(field: Field) {
        fields.remove(field)
        amounts.remove(field)
        if (activeField == field) activeField = fields.first()
    }

    fun save() {
        if (!canSave) return
        if (entry != null && entryField != null) {
            val value = (parsed[entryField] as AmountState.Valid).value
            repository.updateLogEntry(
                entry,
                entry.type,
                toStoredLogValue(entryField.type, value, unit),
                timestamp = customTime ?: entry.timestamp,
                note = note.trim(),
                meal = mealItems.takeIf { mealChanged }
            )
        } else {
            val timestamp = customTime ?: System.currentTimeMillis()
            var pendingNote = note.trim()
            val saved = validParts.filter { (field, _) ->
                val value = (parsed[field] as AmountState.Valid).value
                repository.addLogEntry(
                    field.type,
                    toStoredLogValue(field.type, value, unit),
                    pendingNote,
                    timestamp,
                    label = field.label,
                    meal = if (field == CarbsField) mealItems else emptyList()
                ).also { if (it) pendingNote = "" }
            }
            if (saved.isNotEmpty()) {
                onSaved(context.getString(R.string.log_entry_saved, saved.joinToString(" · ") { it.second }))
            }
        }
        onClose()
    }

    val activeSpec = amountSpec(activeField.type, unit)
    val quickValues = history.quickValues[activeField].orEmpty()
    val saveLabel = when {
        editing || validParts.isEmpty() -> stringResource(R.string.save)
        else -> stringResource(R.string.log_entry_save_summary, validParts.joinToString(" · ") { it.second })
    }

    val form: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenLayout.Gutter)
                .padding(bottom = 16.dp)
        ) {
            EntryTimeRow(
                customTime = customTime,
                onPickDate = { showDatePicker = true },
                onPickTime = { showTimePicker = true },
                onReset = { customTime = null }.takeIf { !editing && customTime != null }
            )
            Spacer(Modifier.height(12.dp))
            fields.chunked(2).forEachIndexed { rowIndex, rowFields ->
                if (rowIndex > 0) Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowFields.forEach { field ->
                        val spec = amountSpec(field.type, unit)
                        val withMeal = field == CarbsField && mealsAvailable
                        AmountTile(
                            type = field.type,
                            label = fieldName(field),
                            valueText = shown(amounts[field].orEmpty()),
                            unitText = unitLabel(field),
                            active = field == activeField && keypadVisible,
                            rangeHint = if (parsed[field] == AmountState.Invalid) {
                                stringResource(
                                    R.string.log_entry_out_of_range,
                                    shown(plainAmount(spec.min, spec.decimals)),
                                    shown(plainAmount(spec.max, spec.decimals))
                                )
                            } else null,
                            detail = mealSummary.takeIf { withMeal && it.isNotEmpty() },
                            onClick = { select(field) },
                            onMeal = if (withMeal) ({ showMealComposer = true }) else null,
                            onRemove = if (!editing && field !in PrimaryFields) ({ removeField(field) }) else null,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            if (!editing) {
                // Until the labels are read every type is offered; after that only those that
                // have a label to be saved under.
                val hasLabel = { field: Field -> labels.labels.isEmpty() || labels.labelFor(field.type) >= 0 }
                val addable = OccasionalFields.filter { it !in fields && hasLabel(it) }
                val customLabels = labels.customLabels.filter { Field(LogType.CUSTOM, it.index) !in fields }
                if (addable.isNotEmpty() || customLabels.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        addable.forEach { field ->
                            val type = field.type
                            val usual = history.usualBasal.takeIf { type == LogType.BASAL_INSULIN }
                            AddTypeChip(
                                type = type,
                                label = if (usual != null) {
                                    stringResource(
                                        R.string.log_entry_usual_basal,
                                        formatAmount(
                                            type,
                                            shown(plainAmount(usual, amountSpec(type, unit).decimals)),
                                            unitLabel(field)
                                        )
                                    )
                                } else {
                                    fieldName(field)
                                },
                                highlighted = usual != null,
                                onClick = { addField(field, usual) }
                            )
                        }
                        if (customLabels.isNotEmpty()) {
                            Box {
                                var menuOpen by remember { mutableStateOf(false) }
                                AddTypeChip(
                                    type = LogType.CUSTOM,
                                    label = stringResource(R.string.log_entry_other_label),
                                    highlighted = false,
                                    onClick = { menuOpen = true }
                                )
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    customLabels.forEach { label ->
                                        DropdownMenuItem(
                                            text = { Text(label.name.ifBlank { customFallback }) },
                                            leadingIcon = {
                                                Icon(
                                                    LogType.CUSTOM.icon,
                                                    contentDescription = null,
                                                    tint = LocalLogbookColors.current.forType(LogType.CUSTOM).primary
                                                )
                                            },
                                            onClick = {
                                                menuOpen = false
                                                addField(Field(LogType.CUSTOM, label.index))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            NoteField(
                note = note,
                onNoteChange = { note = it },
                suggestions = history.recentNotes
                    .filter { it != note && (note.isBlank() || it.contains(note.trim(), ignoreCase = true)) }
                    .takeIf { noteFocused || note.isBlank() }
                    .orEmpty(),
                onFocusChange = { noteFocused = it },
                onDone = { focusManager.clearFocus() }
            )
        }
    }

    val keypadPanel: @Composable (Modifier, Dp, Boolean) -> Unit = { modifier, keyHeight, showQuickValues ->
        Column(modifier = modifier) {
            if (keypadVisible) {
                if (showQuickValues && quickValues.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickValues.forEach { value ->
                            val amount = shown(plainAmount(value, activeSpec.decimals))
                            SuggestionChip(
                                onClick = { pickQuickValue(value) },
                                label = {
                                    Text(
                                        if (activeField.type == LogType.CUSTOM) amount
                                        else formatAmount(activeField.type, amount, unitLabel(activeField))
                                    )
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                AmountKeypad(
                    decimalSeparator = separator,
                    decimalEnabled = activeSpec.decimals > 0,
                    keyHeight = keyHeight,
                    onDigit = { digit -> edit { text, spec -> appendDigit(text, digit, spec) } },
                    onDecimal = { edit { text, spec -> appendDecimal(text, spec) } },
                    onBackspace = { edit { text, _ -> text.dropLast(1) } },
                    onClear = { edit { _, _ -> "" } }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    val saveButton: @Composable () -> Unit = {
        Button(
            onClick = ::save,
            enabled = canSave,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = saveLabel,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(if (editing) R.string.log_entry_edit_title else R.string.log_entry_new_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.closename))
                }
            },
            actions = {
                if (editing) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
        )

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            if (maxWidth > maxHeight && maxWidth >= 600.dp) {
                Row(modifier = Modifier.fillMaxSize()) {
                    form(Modifier.weight(1f).fillMaxHeight())
                    Column(
                        modifier = Modifier
                            .width(360.dp)
                            .fillMaxHeight()
                            .padding(horizontal = ScreenLayout.Gutter)
                    ) {
                        keypadPanel(Modifier.weight(1f).verticalScroll(rememberScrollState()), 48.dp, true)
                        saveButton()
                        Spacer(Modifier.height(12.dp))
                    }
                }
            } else {
                // The save button is never squeezed out: what is left after it and the form's
                // minimum height is what the keypad gets, down to a floor where the form yields.
                val fontScale = LocalDensity.current.fontScale
                val reserved = SAVE_BLOCK_HEIGHT + FORM_MIN_HEIGHT * fontScale + KEYPAD_GAPS
                val quickRow = 40.dp
                val roomForQuick = maxHeight - reserved - quickRow > MIN_KEY_HEIGHT * 4f
                val quickSpace = if (roomForQuick) quickRow else 0.dp
                val keyHeight = ((maxHeight - reserved - quickSpace) / 4f)
                    .coerceIn(MIN_KEY_HEIGHT, MAX_KEY_HEIGHT)
                Column(modifier = Modifier.fillMaxSize()) {
                    form(Modifier.weight(1f).fillMaxWidth())
                    keypadPanel(Modifier.fillMaxWidth().padding(horizontal = ScreenLayout.Gutter), keyHeight, roomForQuick)
                    Box(modifier = Modifier.padding(horizontal = ScreenLayout.Gutter)) { saveButton() }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        EntryDatePickerDialog(
            initial = customTime ?: System.currentTimeMillis(),
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                customTime = picked
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        EntryTimePickerDialog(
            initial = customTime ?: System.currentTimeMillis(),
            onDismiss = { showTimePicker = false },
            onConfirm = { picked ->
                customTime = picked
                showTimePicker = false
            }
        )
    }

    if (confirmDelete && entry != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.log_entry_delete_question)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    repository.deleteLogEntry(entry)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.log_entry_deleted),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    onClose()
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showMealComposer) {
        MealComposer(
            initialItems = mealItems,
            logs = logs,
            onDismiss = { showMealComposer = false },
            onDone = { items, updatedCatalog, carbs ->
                showMealComposer = false
                catalog = updatedCatalog
                mealItems = items
                mealChanged = true
                if (items.isNotEmpty()) {
                    amounts[CarbsField] = plainAmount(carbs, amountSpec(LogType.CARBS, unit).decimals)
                }
                select(CarbsField)
            }
        )
    }
}

private sealed interface AmountState {
    data object Empty : AmountState
    data object Invalid : AmountState
    data object Incomplete : AmountState
    data class Valid(val value: Float) : AmountState
}

@Composable
private fun EntryTimeRow(
    customTime: Long?,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onReset: (() -> Unit)?
) {
    val locale = Locale.getDefault()
    val time = customTime ?: System.currentTimeMillis()
    val dateLabel = when (val days = remember(time) { daysBeforeToday(time) }) {
        0 -> stringResource(R.string.logbook_time_today)
        1 -> stringResource(R.string.log_entry_date_yesterday)
        else -> remember(time, locale) {
            DateFormat.getDateInstance(if (days < 300) DateFormat.MEDIUM else DateFormat.LONG, locale).format(time)
        }
    }
    val timeLabel = if (customTime == null) {
        stringResource(R.string.log_entry_time_now)
    } else {
        remember(customTime, locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(customTime) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = onPickDate,
            label = { Text(dateLabel) },
            leadingIcon = {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            }
        )
        AssistChip(
            onClick = onPickTime,
            label = { Text(timeLabel) },
            leadingIcon = {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
            }
        )
        if (onReset != null) {
            IconButton(onClick = onReset) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.log_entry_time_reset),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun daysBeforeToday(time: Long): Int {
    fun dayStart(millis: Long) = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return ((dayStart(System.currentTimeMillis()) - dayStart(time) + DAY_MS / 2) / DAY_MS).toInt()
}

/**
 * Picks the day of an entry, keeping the time of day of [initial]. Days in the future cannot be
 * chosen, so a forgotten entry can be logged back to any earlier day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDatePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val start = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    // The picker works in UTC midnights, so the local calendar day is carried over as such.
    val today = remember { Calendar.getInstance() }
    fun utcMidnight(calendar: Calendar) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    val latest = remember { utcMidnight(today) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = utcMidnight(start),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= latest
            override fun isSelectableYear(year: Int) = year <= today.get(Calendar.YEAR)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val selected = state.selectedDateMillis ?: return@TextButton
                    val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selected }
                    val picked = (start.clone() as Calendar).apply {
                        set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
                    }
                    // Today's date with a time that has not happened yet falls back to now.
                    onConfirm(minOf(picked.timeInMillis, System.currentTimeMillis()))
                }
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    ) {
        DatePicker(state = state)
    }
}

/**
 * Picks a time of day on the date of [initial]. A time that would lie in the future is taken to
 * mean the day before, so a late-evening entry logged after midnight lands on the right day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTimePickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val context = LocalContext.current
    val start = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    val state = rememberTimePickerState(
        initialHour = start.get(Calendar.HOUR_OF_DAY),
        initialMinute = start.get(Calendar.MINUTE),
        is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        val picked = (start.clone() as Calendar).apply {
                            set(Calendar.HOUR_OF_DAY, state.hour)
                            set(Calendar.MINUTE, state.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        if (picked.timeInMillis > System.currentTimeMillis() + 60_000L) {
                            picked.add(Calendar.DAY_OF_MONTH, -1)
                        }
                        onConfirm(picked.timeInMillis)
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

/**
 * One amount of the editor. [detail] is an extra line under the value (the ingredients of an
 * attached meal), and [onMeal] opens the meal composer from the tile's corner.
 */
@Composable
private fun AmountTile(
    type: LogType,
    label: String,
    valueText: String,
    unitText: String,
    active: Boolean,
    rangeHint: String?,
    detail: String?,
    onClick: () -> Unit,
    onMeal: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val typeColors = LocalLogbookColors.current.forType(type)
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (active) typeColors.container else scheme.surfaceContainerHigh,
        label = "tileContainer"
    )
    val border by animateColorAsState(
        when {
            rangeHint != null -> scheme.error
            active -> typeColors.primary
            else -> Color.Transparent
        },
        label = "tileBorder"
    )
    val content = if (active) typeColors.onContainer else scheme.onSurface
    val tileDescription =
        stringResource(R.string.log_entry_tile_description, label, valueText.ifEmpty { "0" }, unitText)

    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 112.dp)
            .semantics { contentDescription = tileDescription },
        shape = RoundedCornerShape(24.dp),
        color = container,
        border = BorderStroke(2.dp, border)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)) {
            Row(
                modifier = Modifier.heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(type.icon, contentDescription = null, tint = typeColors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) typeColors.onContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onMeal != null) {
                    IconButton(onClick = onMeal, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = stringResource(R.string.meal_compose),
                            tint = if (detail != null) typeColors.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.log_entry_remove_type, label),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = valueText.ifEmpty { "0" },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (valueText.isEmpty()) content.copy(alpha = 0.38f) else content,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline()
                )
                if (active) BlinkingCaret(color = typeColors.primary, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = unitText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (active) typeColors.onContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline()
                )
            }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (active) typeColors.onContainer else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (rangeHint != null) {
                Text(
                    text = rangeHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error
                )
            }
        }
    }
}

@Composable
private fun BlinkingCaret(color: Color, modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 530), RepeatMode.Reverse),
        label = "caretAlpha"
    )
    Box(
        modifier = modifier
            .padding(start = 2.dp)
            .size(width = 2.dp, height = 30.dp)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
private fun AddTypeChip(
    type: LogType,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val typeColors = LocalLogbookColors.current.forType(type)
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = if (highlighted) type.icon else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        },
        colors = if (highlighted) {
            AssistChipDefaults.assistChipColors(
                containerColor = typeColors.container,
                labelColor = typeColors.onContainer,
                leadingIconContentColor = typeColors.primary
            )
        } else {
            AssistChipDefaults.assistChipColors(leadingIconContentColor = typeColors.primary)
        },
        border = if (highlighted) null else AssistChipDefaults.assistChipBorder(enabled = true)
    )
}

@Composable
private fun NoteField(
    note: String,
    onNoteChange: (String) -> Unit,
    suggestions: List<String>,
    onFocusChange: (Boolean) -> Unit,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        placeholder = { Text(stringResource(R.string.log_entry_note_hint)) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChange(it.isFocused) }
    )
    if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = {
                        onNoteChange(suggestion)
                        onDone()
                    },
                    label = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

@Composable
private fun AmountKeypad(
    decimalSeparator: Char,
    decimalEnabled: Boolean,
    keyHeight: Dp,
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    KeypadKey(onClick = { onDigit(digit) }, height = keyHeight, modifier = Modifier.weight(1f)) {
                        KeyLabel(digit.toString())
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeypadKey(
                onClick = onDecimal,
                enabled = decimalEnabled,
                height = keyHeight,
                modifier = Modifier.weight(1f)
            ) {
                if (decimalEnabled) KeyLabel(decimalSeparator.toString())
            }
            KeypadKey(onClick = { onDigit('0') }, height = keyHeight, modifier = Modifier.weight(1f)) {
                KeyLabel("0")
            }
            val backspaceLabel = stringResource(R.string.log_entry_backspace)
            KeypadKey(
                onClick = onBackspace,
                onLongClick = onClear,
                height = keyHeight,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = backspaceLabel }
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
            }
        }
    }
}

@Composable
private fun KeyLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

@Composable
private fun formatAmount(type: LogType, shownValue: String, unitText: String): String = when (type) {
    LogType.RAPID_INSULIN, LogType.BASAL_INSULIN -> stringResource(R.string.log_value_insulin, shownValue)
    LogType.CARBS, LogType.MEAL -> stringResource(R.string.log_value_carbs, shownValue)
    else -> stringResource(R.string.log_glucose_value, shownValue, unitText)
}

// --- Amount text editing. Amounts are kept with '.' and shown with the locale's separator. ---

private fun appendDigit(text: String, digit: Char, spec: AmountSpec): String {
    val next = if (text == "0") digit.toString() else text + digit
    val dot = next.indexOf('.')
    val integerDigits = if (dot >= 0) dot else next.length
    val fractionDigits = if (dot >= 0) next.length - dot - 1 else 0
    return if (integerDigits > spec.maxIntegerDigits || fractionDigits > spec.decimals) text else next
}

private fun appendDecimal(text: String, spec: AmountSpec): String =
    if (spec.decimals == 0 || '.' in text) text else text.ifEmpty { "0" } + "."

internal fun plainAmount(value: Float, decimals: Int): String =
    BigDecimal(value.toDouble()).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

// --- What the editor learns from the logbook. ---

private data class LogHistory(
    /** The amounts the user logs most, per field, ascending. */
    val quickValues: Map<Field, List<Float>> = emptyMap(),
    /** Notes the user has written before, most used first. */
    val recentNotes: List<String> = emptyList(),
    /** The basal dose usually taken around this time of day, if it has not been logged yet. */
    val usualBasal: Float? = null
)

private const val QUICK_VALUE_COUNT = 4
private const val NOTE_SUGGESTION_COUNT = 6

private fun analyzeHistory(logs: List<LogRecord>, unit: GlucoseUnit, now: Long): LogHistory {
    val since = now - 90 * DAY_MS
    // value -> (times used, last used)
    val usage = HashMap<Field, HashMap<Float, Pair<Int, Long>>>()
    val notes = HashMap<String, Triple<String, Int, Long>>()
    for (record in logs) {
        if (record.timestamp < since || record.timestamp > now + HOUR_MS) continue
        val field = when (record.type) {
            LogType.MEAL -> CarbsField
            LogType.CUSTOM -> Field(LogType.CUSTOM, record.nativeLabel ?: -1)
            else -> Field(record.type)
        }
        if (field.type != LogType.BLOOD_GLUCOSE) {
            val decimals = amountSpec(field.type, unit).decimals
            val key = plainAmount(record.value, decimals).toFloat()
            if (key > 0f) {
                val perField = usage.getOrPut(field) { HashMap() }
                val (count, last) = perField[key] ?: (0 to 0L)
                perField[key] = (count + 1) to maxOf(last, record.timestamp)
            }
        }
        val note = record.note.trim()
        if (note.isNotEmpty()) {
            val key = note.lowercase(Locale.getDefault())
            val (shown, count, last) = notes[key] ?: Triple(note, 0, 0L)
            notes[key] = if (record.timestamp >= last) Triple(note, count + 1, record.timestamp) else Triple(shown, count + 1, last)
        }
    }

    val quickValues = usage.mapValues { (_, values) ->
        values.entries
            .sortedWith(compareByDescending<Map.Entry<Float, Pair<Int, Long>>> { it.value.first }.thenByDescending { it.value.second })
            .take(QUICK_VALUE_COUNT)
            .map { it.key }
            .sorted()
    }
    val recentNotes = notes.values
        .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third })
        .take(NOTE_SUGGESTION_COUNT)
        .map { it.first }

    return LogHistory(quickValues, recentNotes, usualBasal(logs, now))
}

/**
 * Basal is taken once or twice a day at fairly fixed times. If it has been logged near the
 * current time of day at least twice in the last two weeks and not in the last eight hours, the
 * most recent dose from that time slot is offered as a one-tap entry.
 */
private fun usualBasal(logs: List<LogRecord>, now: Long): Float? {
    val recent = logs.filter { it.type == LogType.BASAL_INSULIN && it.timestamp in (now - 14 * DAY_MS)..now }
    if (recent.isEmpty() || recent.any { it.timestamp > now - 8 * HOUR_MS }) return null
    val nowMinute = minuteOfDay(now)
    val sameSlot = recent.filter {
        val distance = abs(minuteOfDay(it.timestamp) - nowMinute)
        min(distance, 24 * 60 - distance) <= 120
    }
    if (sameSlot.size < 2) return null
    return sameSlot.maxBy { it.timestamp }.value.takeIf { it > 0f }
}

private fun minuteOfDay(millis: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}
