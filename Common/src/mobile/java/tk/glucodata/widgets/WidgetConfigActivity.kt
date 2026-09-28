@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.R
import tk.glucodata.ui.screens.ScreenLayout
import tk.glucodata.ui.screens.settings.ColorPickerDialog
import tk.glucodata.ui.screens.settings.SettingsSection
import tk.glucodata.ui.screens.settings.SettingsSegmentedRow
import tk.glucodata.ui.screens.settings.SettingsSliderRow
import tk.glucodata.ui.screens.settings.SettingsSwitchRow
import tk.glucodata.ui.theme.JugglucoTheme
import kotlin.math.roundToInt

/**
 * Widget gallery and editor.
 *
 * Opened by the launcher when a widget is placed or reconfigured, it edits that widget. Opened from
 * the app's settings, it first shows every widget kind and then pins the configured one to the
 * home screen.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val manager = AppWidgetManager.getInstance(this)
        val placedKind = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetKind.forProvider(manager.getAppWidgetInfo(appWidgetId)?.provider?.className)
                ?: run { finish(); return }
        } else null
        val placedSize = if (placedKind != null) placedSizeDp(manager, appWidgetId) else null

        setContent {
            JugglucoTheme {
                var editing by rememberSaveable { mutableStateOf(placedKind) }
                val kind = editing
                if (kind == null) {
                    WidgetGallery(
                        onPick = { editing = it },
                        onBack = { finish() }
                    )
                } else {
                    val initial = remember(kind) {
                        if (placedKind != null) WidgetConfigStore.load(this, appWidgetId, kind) else kind.defaultConfig()
                    }
                    WidgetEditor(
                        kind = kind,
                        initial = initial,
                        previewSize = placedSize ?: SizeF(kind.defaultWidthDp, kind.defaultHeightDp),
                        addToHomeScreen = placedKind == null,
                        onDone = { config ->
                            if (placedKind != null) {
                                WidgetConfigStore.save(this, appWidgetId, config)
                                WidgetUpdater.update(this, kind, intArrayOf(appWidgetId))
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                finish()
                            } else {
                                requestPin(kind, config)
                                editing = null
                            }
                        },
                        onBack = { if (placedKind != null) finish() else editing = null }
                    )
                }
            }
        }
    }

    private fun placedSizeDp(manager: AppWidgetManager, appWidgetId: Int): SizeF? {
        val options = manager.getAppWidgetOptions(appWidgetId) ?: return null
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        return if (width > 0 && height > 0) SizeF(width.toFloat(), height.toFloat()) else null
    }

    private fun requestPin(kind: WidgetKind, config: WidgetConfig) {
        val manager = AppWidgetManager.getInstance(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !manager.isRequestPinAppWidgetSupported) return
        val callback = Intent(this, kind.provider)
            .setAction(BaseGlucoseWidget.ACTION_PINNED)
            .putExtra(BaseGlucoseWidget.EXTRA_CONFIG, config.toJson().toString())
        // Mutable so the launcher can add the id of the new widget.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getBroadcast(
            this, System.currentTimeMillis().toInt(), callback, PendingIntent.FLAG_UPDATE_CURRENT or mutable
        )
        manager.requestPinAppWidget(ComponentName(this, kind.provider), null, pending)
    }
}

private fun canPin(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

/** Pretend wallpapers behind the preview, to check how a widget reads on light and dark home screens. */
private enum class Backdrop(val labelRes: Int) {
    COLORFUL(R.string.widget_backdrop_colorful),
    DARK(R.string.widget_backdrop_dark),
    LIGHT(R.string.widget_backdrop_light);

    @Composable
    fun brush(): Brush {
        val scheme = MaterialTheme.colorScheme
        return when (this) {
            COLORFUL -> Brush.linearGradient(
                listOf(
                    lerpDark(scheme.primary),
                    lerpDark(scheme.tertiary),
                    lerpDark(scheme.secondary)
                )
            )
            DARK -> Brush.verticalGradient(listOf(Color(0xFF232A2F), Color(0xFF0E1214)))
            LIGHT -> Brush.verticalGradient(listOf(Color(0xFFF1F4F7), Color(0xFFD8E0E6)))
        }
    }

    val isDark: Boolean get() = this != LIGHT

    private fun lerpDark(color: Color): Color = androidx.compose.ui.graphics.lerp(color, Color(0xFF101418), 0.45f)
}

@Composable
private fun rememberSnapshot(kind: WidgetKind?, config: WidgetConfig?): WidgetSnapshot? {
    val context = LocalContext.current
    val hours = when {
        kind == null || config == null -> 24L
        kind.hasStats -> maxOf(24L, config.statsPeriod.hours.toLong())
        else -> 24L
    }
    return produceState<WidgetSnapshot?>(null, hours) {
        value = withContext(Dispatchers.Default) {
            WidgetDataSource.load(context, hours * 3_600_000L).takeIf { it.times.size > 3 } ?: WidgetDataSource.sample()
        }
    }.value
}

@Composable
private fun rememberWidgetImage(
    kind: WidgetKind,
    config: WidgetConfig,
    snapshot: WidgetSnapshot?,
    size: SizeF,
    backdrop: Backdrop
): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    return produceState<ImageBitmap?>(null, kind, config, snapshot, size, backdrop) {
        val data = snapshot ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val palette = WidgetPalette.resolve(context, config, data.status, wallpaperDark = backdrop.isDark)
            WidgetRenderer(context).render(kind, config, data, size.width, size.height, density, palette).asImageBitmap()
        }
    }.value
}

@Composable
private fun WidgetPreview(
    kind: WidgetKind,
    config: WidgetConfig,
    snapshot: WidgetSnapshot?,
    size: SizeF,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .background(backdrop.brush()),
        contentAlignment = Alignment.Center
    ) {
        // Shown at its real size where it fits, and scaled down proportionally where it does not.
        val fit = minOf(1f, (maxWidth.value - 32f) / size.width, (maxHeight.value - 32f) / size.height)
        val shownSize = SizeF(size.width * fit, size.height * fit)
        val image = rememberWidgetImage(kind, config, snapshot, size, backdrop)
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = stringResource(kind.labelRes),
                modifier = Modifier.size(shownSize.width.dp, shownSize.height.dp)
            )
        }
    }
}

@Composable
private fun WidgetScaffold(
    title: String,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.loc_action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = bottomBar,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

@Composable
private fun WidgetGallery(onPick: (WidgetKind) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val snapshot = rememberSnapshot(null, null)
    val backdrop = Backdrop.COLORFUL
    WidgetScaffold(title = stringResource(R.string.widget_settings_title), onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ScreenLayout.Gutter,
                    end = ScreenLayout.Gutter,
                    top = ScreenLayout.TopPadding,
                    bottom = 36.dp
                ),
            verticalArrangement = Arrangement.spacedBy(ScreenLayout.SectionSpacing)
        ) {
            WidgetKind.entries.forEach { kind ->
                Surface(
                    onClick = { onPick(kind) },
                    shape = RoundedCornerShape(28.dp),
                    color = ScreenLayout.cardContainerColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(ScreenLayout.CardPadding / 2)) {
                        WidgetPreview(
                            kind = kind,
                            config = kind.defaultConfig(),
                            snapshot = snapshot,
                            size = SizeF(kind.defaultWidthDp, kind.defaultHeightDp),
                            backdrop = backdrop,
                            height = if (kind.defaultHeightDp > 120f) 200.dp else 132.dp
                        )
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
                            Text(
                                stringResource(kind.labelRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(kind.descriptionRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(if (canPin(context)) R.string.widget_gallery_hint else R.string.widget_gallery_hint_launcher),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = ScreenLayout.CardPadding)
            )
        }
    }
}

private val CUSTOM_SWATCHES = listOf(
    0xFF101418, 0xFF000000, 0xFF1F2A30, 0xFF1B3A4B, 0xFF2D2A4A, 0xFF3E2A35,
    0xFFFFFFFF, 0xFFF3E9DC, 0xFFE3F0E8, 0xFFE6E4F5
).map { it.toInt() }

@Composable
private fun WidgetEditor(
    kind: WidgetKind,
    initial: WidgetConfig,
    previewSize: SizeF,
    addToHomeScreen: Boolean,
    onDone: (WidgetConfig) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var config by remember(kind) { mutableStateOf(initial) }
    var backdrop by rememberSaveable {
        mutableStateOf(if (WidgetPalette.wallpaperIsDark(context) == false) Backdrop.LIGHT else Backdrop.COLORFUL)
    }
    var showColorPicker by remember { mutableStateOf(false) }
    val snapshot = rememberSnapshot(kind, config)
    val pinnable = !addToHomeScreen || canPin(context)

    WidgetScaffold(
        title = stringResource(kind.labelRes),
        onBack = onBack,
        bottomBar = {
            if (pinnable) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Button(
                        onClick = { onDone(config) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = ScreenLayout.Gutter, vertical = 12.dp)
                            .height(52.dp)
                    ) {
                        Icon(if (addToHomeScreen) Icons.Default.Add else Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (addToHomeScreen) R.string.widget_add_to_home else R.string.widget_save))
                    }
                }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The preview stays put while the options scroll underneath it.
            Column(
                modifier = Modifier.padding(start = ScreenLayout.Gutter, end = ScreenLayout.Gutter, top = ScreenLayout.TopPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WidgetPreview(kind, config, snapshot, previewSize, backdrop)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Backdrop.entries.forEach { option ->
                        FilterChip(
                            selected = backdrop == option,
                            onClick = { backdrop = option },
                            label = { Text(stringResource(option.labelRes)) }
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenLayout.Gutter, end = ScreenLayout.Gutter, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ScreenLayout.SectionSpacing)
            ) {
                StyleSection(config, onChange = { config = it }, onPickColor = { showColorPicker = true })
                TextSection(kind, config) { config = it }
                ContentSection(kind, config) { config = it }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColorArgb = config.customColor,
            onColorSelected = {
                config = config.copy(background = WidgetBackground.CUSTOM, customColor = it)
                showColorPicker = false
            },
            onUseSystemColors = {
                config = config.copy(background = WidgetBackground.SURFACE)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { label(option) })
        }
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label(option), maxLines = 1) }
            )
        }
    }
}

@Composable
private fun StyleSection(config: WidgetConfig, onChange: (WidgetConfig) -> Unit, onPickColor: () -> Unit) {
    SettingsSection(title = stringResource(R.string.widget_section_style)) {
        SettingsSegmentedRow(title = stringResource(R.string.widget_background), icon = Icons.Default.Palette) {
            ChipRow(WidgetBackground.entries, config.background, { onChange(config.copy(background = it)) }) {
                Text(stringResource(it.labelRes))
            }
            if (config.background == WidgetBackground.CUSTOM) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CUSTOM_SWATCHES.forEach { argb ->
                        ColorSwatch(Color(argb), selected = config.customColor == argb) {
                            onChange(config.copy(customColor = argb))
                        }
                    }
                    val custom = config.customColor !in CUSTOM_SWATCHES
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (custom) Color(config.customColor) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = if (custom) 3.dp else 1.dp,
                                color = if (custom) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable(onClick = onPickColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.settings_color_picker_title),
                            tint = if (custom) contentFor(Color(config.customColor)) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (config.background != WidgetBackground.NONE) {
            SettingsSliderRow(
                title = stringResource(R.string.widget_opacity),
                valueText = "${config.opacity}%",
                icon = Icons.Default.Opacity,
                value = config.opacity.toFloat(),
                onValueChange = { onChange(config.copy(opacity = (it / 5f).roundToInt() * 5)) },
                valueRange = 0f..100f
            )
            SettingsSegmentedRow(title = stringResource(R.string.widget_shape), icon = Icons.Default.RoundedCorner) {
                Segmented(WidgetShape.entries, config.shape, { onChange(config.copy(shape = it)) }) { stringResource(it.labelRes) }
            }
        }
        if (config.backgroundIsWallpaper || config.background == WidgetBackground.CUSTOM) {
            SettingsSegmentedRow(
                title = stringResource(R.string.widget_text_color),
                subtitle = stringResource(R.string.widget_text_color_desc),
                icon = Icons.Default.FormatColorText
            ) {
                Segmented(WidgetContentColor.entries, config.content, { onChange(config.copy(content = it)) }) { stringResource(it.labelRes) }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = contentFor(color), modifier = Modifier.size(20.dp))
    }
}

private fun contentFor(color: Color): Color = if (color.luminance() > 0.45f) Color(0xFF171C1F) else Color.White

@Composable
private fun TextSection(kind: WidgetKind, config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    SettingsSection(title = stringResource(R.string.widget_section_text)) {
        SettingsSegmentedRow(title = stringResource(R.string.widget_font), icon = Icons.Default.TextFields) {
            ChipRow(WidgetFont.entries, config.font, { onChange(config.copy(font = it)) }) { font ->
                val family = remember(font) { FontFamily(WidgetRenderer.typeface(font, font.weight)) }
                Text(stringResource(font.labelRes), fontFamily = family)
            }
        }
        SettingsSliderRow(
            title = stringResource(R.string.widget_text_size),
            valueText = "${config.textScale}%",
            icon = Icons.Default.FormatSize,
            value = config.textScale.toFloat(),
            onValueChange = { onChange(config.copy(textScale = (it / 5f).roundToInt() * 5)) },
            valueRange = 70f..140f
        )
        if (kind == WidgetKind.MINIMAL) {
            SettingsSegmentedRow(title = stringResource(R.string.widget_alignment), icon = Icons.Default.FormatAlignCenter) {
                Segmented(WidgetAlignment.entries, config.alignment, { onChange(config.copy(alignment = it)) }) { stringResource(it.labelRes) }
            }
        }
    }
}

@Composable
private fun ContentSection(kind: WidgetKind, config: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    SettingsSection(title = stringResource(R.string.widget_section_content)) {
        SettingsSwitchRow(
            title = stringResource(R.string.widget_range_colors),
            subtitle = stringResource(R.string.widget_range_colors_desc),
            icon = Icons.Default.InvertColors,
            checked = config.rangeColors,
            onCheckedChange = { onChange(config.copy(rangeColors = it)) }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.widget_show_arrow),
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            checked = config.showArrow,
            onCheckedChange = { onChange(config.copy(showArrow = it)) }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.widget_show_delta),
            icon = Icons.Default.Difference,
            checked = config.showDelta,
            onCheckedChange = { onChange(config.copy(showDelta = it)) }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.widget_show_time),
            subtitle = stringResource(R.string.widget_show_time_desc),
            icon = Icons.Default.Schedule,
            checked = config.showTime,
            onCheckedChange = { onChange(config.copy(showTime = it)) }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.widget_show_unit),
            icon = Icons.Default.Straighten,
            checked = config.showUnit,
            onCheckedChange = { onChange(config.copy(showUnit = it)) }
        )
    }
    if (kind.hasGraph) {
        SettingsSection(title = stringResource(R.string.widget_section_graph)) {
            SettingsSegmentedRow(title = stringResource(R.string.widget_graph_hours), icon = Icons.Default.Timelapse) {
                Segmented(WIDGET_GRAPH_HOURS.toList(), config.graphHours, { onChange(config.copy(graphHours = it)) }) {
                    stringResource(R.string.widget_hours_short, it)
                }
            }
            SettingsSwitchRow(
                title = stringResource(R.string.widget_target_band),
                icon = Icons.Default.Tonality,
                checked = config.showTargetBand,
                onCheckedChange = { onChange(config.copy(showTargetBand = it)) }
            )
            SettingsSwitchRow(
                title = stringResource(R.string.widget_time_axis),
                icon = Icons.Default.AvTimer,
                checked = config.showTimeAxis,
                onCheckedChange = { onChange(config.copy(showTimeAxis = it)) }
            )
        }
    }
    if (kind.hasStats) {
        SettingsSection(title = stringResource(R.string.widget_in_range)) {
            SettingsSegmentedRow(title = stringResource(R.string.widget_period), icon = Icons.Default.DateRange) {
                Segmented(WidgetStatsPeriod.entries, config.statsPeriod, { onChange(config.copy(statsPeriod = it)) }) { stringResource(it.labelRes) }
            }
        }
    }
}
