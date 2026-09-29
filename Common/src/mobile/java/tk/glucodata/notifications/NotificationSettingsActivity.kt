@file:OptIn(ExperimentalMaterial3Api::class)

package tk.glucodata.notifications

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Looks3
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.LooksTwo
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.glucodata.Natives
import tk.glucodata.Notify
import tk.glucodata.R
import tk.glucodata.ui.screens.ScreenLayout
import tk.glucodata.ui.screens.settings.SettingsActionRow
import tk.glucodata.ui.screens.settings.SettingsSection
import tk.glucodata.ui.screens.settings.SettingsSegmentedRow
import tk.glucodata.ui.screens.settings.SettingsSliderRow
import tk.glucodata.ui.screens.settings.SettingsSwitchRow
import tk.glucodata.ui.theme.JugglucoTheme
import tk.glucodata.ui.theme.jugglucoColorScheme
import tk.glucodata.widgets.WIDGET_GRAPH_HOURS
import tk.glucodata.widgets.WidgetDataSource
import tk.glucodata.widgets.WidgetFont
import tk.glucodata.widgets.WidgetPalette
import tk.glucodata.widgets.WidgetRenderer
import tk.glucodata.widgets.WidgetSnapshot
import tk.glucodata.widgets.WidgetStatsPeriod
import java.util.Date
import kotlin.math.roundToInt

/**
 * Settings for the glucose notification and the status bar icons, with a preview of the
 * notification shade drawn by the same code as the real notification. Changes apply right away.
 */
class NotificationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            JugglucoTheme {
                NotificationSettings(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun NotificationSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(NotificationConfigStore.load(context)) }
    val update: (NotificationConfig) -> Unit = {
        config = it
        NotificationConfigStore.save(context, it)
    }
    var showNotification by remember { mutableStateOf(runCatching { Natives.getshowalways() }.getOrDefault(true)) }
    var dark by rememberSaveable { mutableStateOf(WidgetPalette.systemIsDark()) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val snapshot = rememberPreviewSnapshot(config)
    // Live Updates can be switched off for the app in the system settings; check again on return.
    var liveAllowed by remember { mutableStateOf(LiveUpdate.allowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val allowed = LiveUpdate.allowed(context)
        if (allowed != liveAllowed) {
            liveAllowed = allowed
            NotificationRefresher.request(context)
        }
    }
    // What the phone will actually show: without permission the regular notification stays.
    val live = config.liveUpdate && liveAllowed

    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notif_settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.loc_action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // The preview stays put while the options scroll underneath it.
            Column(
                modifier = Modifier.padding(start = ScreenLayout.Gutter, end = ScreenLayout.Gutter, top = ScreenLayout.TopPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadePreview(
                    config = config,
                    snapshot = snapshot,
                    dark = dark,
                    expanded = expanded && config.hasExpandedView && !live,
                    live = live,
                    showNotification = showNotification,
                    onToggleExpanded = { expanded = !expanded }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = !expanded, onClick = { expanded = false }, label = { Text(stringResource(R.string.notif_preview_collapsed)) })
                    FilterChip(
                        selected = expanded && config.hasExpandedView && !live,
                        enabled = config.hasExpandedView && !live,
                        onClick = { expanded = true },
                        label = { Text(stringResource(R.string.notif_preview_expanded)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = !dark, onClick = { dark = false }, label = { Text(stringResource(R.string.widget_backdrop_light)) })
                    FilterChip(selected = dark, onClick = { dark = true }, label = { Text(stringResource(R.string.widget_backdrop_dark)) })
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = ScreenLayout.Gutter, end = ScreenLayout.Gutter, top = 8.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(ScreenLayout.SectionSpacing)
            ) {
                NotificationSection(config, update, showNotification) {
                    showNotification = it
                    Thread { runCatching { Notify.glucosestatus(it) } }.start()
                }
                LiveUpdateSection(config, update, liveAllowed, showNotification)
                ExpandedSection(config, update)
                StatusIconSection(config, update)
            }
        }
    }
}

// ---- Preview -------------------------------------------------------------------------------------

@Composable
private fun rememberPreviewSnapshot(config: NotificationConfig): WidgetSnapshot? {
    val context = LocalContext.current
    // Whole hours, so moving between options that need the same history does not reload it.
    val hours = ((config.historyMillis(System.currentTimeMillis()) + 3_599_999L) / 3_600_000L).coerceAtLeast(24L)
    return produceState<WidgetSnapshot?>(null, hours) {
        value = withContext(Dispatchers.Default) {
            WidgetDataSource.load(context, hours * 3_600_000L).takeIf { it.times.size > 3 } ?: WidgetDataSource.sample()
        }
    }.value
}

@Composable
private fun ShadePreview(
    config: NotificationConfig,
    snapshot: WidgetSnapshot?,
    dark: Boolean,
    expanded: Boolean,
    live: Boolean,
    showNotification: Boolean,
    onToggleExpanded: () -> Unit
) {
    val context = LocalContext.current
    val scheme = remember(dark) { jugglucoColorScheme(context, dark) }
    val palette = remember(dark) { WidgetPalette.forNotification(context, dark) }
    val shade = if (dark) scheme.surfaceContainerLowest else scheme.surfaceContainerLow
    val onShade = scheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(shade)
            .padding(12.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatusBarPreview(config, snapshot, onShade, showNotification, if (live) palette else null)
        if (showNotification) {
            if (live) LiveUpdateCard(config, snapshot, palette, scheme.surfaceContainerHigh, onShade, scheme.primary)
            else NotificationCard(config, snapshot, dark, expanded, scheme.surfaceContainerHigh, onShade, scheme.primary, onToggleExpanded)
        }
    }
}

@Composable
private fun StatusBarPreview(
    config: NotificationConfig,
    snapshot: WidgetSnapshot?,
    color: Color,
    showNotification: Boolean,
    /** Set when the glucose notification is a Live Update, which shows as a chip. */
    livePalette: WidgetPalette?
) {
    val context = LocalContext.current
    val size = StatusIconRenderer.sizePx(context)
    val mainIcon = if (livePalette != null) config.liveChipIcon else config.mainIcon
    val kinds = listOfNotNull(if (showNotification) mainIcon else null) + config.extraIcons.filterNotNull()
    val icons = produceState<List<ImageBitmap?>>(emptyList(), config, snapshot, showNotification) {
        val data = snapshot ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val input = GlucoseNotificationStyler.iconInput(context, data, null)
            kinds.map { StatusIconRenderer.render(it, input, config, size)?.asImageBitmap() }
        }
    }.value
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            DateFormat.getTimeFormat(context).format(Date()),
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(2.dp))
        @Composable
        fun StatusIcon(index: Int, kind: StatusIconKind, tint: Color) {
            val bitmap = icons.getOrNull(index)
            val modifier = Modifier.size(18.dp)
            when {
                kind == StatusIconKind.APP -> Icon(painterResource(R.drawable.novalue), contentDescription = null, tint = tint, modifier = modifier)
                bitmap != null -> Image(bitmap, contentDescription = stringResource(kind.labelRes), colorFilter = ColorFilter.tint(tint), modifier = modifier)
                else -> Spacer(modifier)
            }
        }
        kinds.forEachIndexed { index, kind ->
            if (index == 0 && showNotification && livePalette != null && snapshot != null) {
                val chip = Color(LiveUpdate.color(config, snapshot, livePalette))
                val onChip = if (chip.luminance() > 0.5f) Color(0xFF171C1F) else Color.White
                val text = remember(config, snapshot) {
                    LiveUpdate.chipText(config, GlucoseNotificationStyler.iconInput(context, snapshot, null))
                }
                Row(
                    modifier = Modifier.clip(CircleShape).background(chip).padding(start = 6.dp, end = if (text != null) 9.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusIcon(index, kind, onChip)
                    text?.let { Text(it, color = onChip, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                }
            } else {
                StatusIcon(index, kind, color)
            }
        }
        Spacer(Modifier.weight(1f))
        listOf(Icons.Default.Wifi, Icons.Default.SignalCellularAlt, Icons.Default.BatteryFull).forEach {
            Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NotificationCard(
    config: NotificationConfig,
    snapshot: WidgetSnapshot?,
    dark: Boolean,
    expanded: Boolean,
    container: Color,
    content: Color,
    accent: Color,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .clickable(enabled = config.hasExpandedView, onClick = onToggle)
    ) {
        val cardWidth = maxWidth
        if (!expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppBadge(accent, 36.dp)
                Spacer(Modifier.width(12.dp))
                val width = cardWidth - 12.dp - 36.dp - 12.dp - 44.dp
                val image = rememberNotificationImage(config, snapshot, dark, width, expanded = false)
                Box(modifier = Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.CenterStart) {
                    image?.let {
                        Image(it, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize())
                    }
                }
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (config.hasExpandedView) Icon(Icons.Default.ExpandMore, contentDescription = null, tint = content.copy(alpha = 0.7f))
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBadge(accent, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.app_name) + " · " + DateFormat.getTimeFormat(context).format(Date(snapshot?.currentTime ?: System.currentTimeMillis())),
                        color = content.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ExpandLess, contentDescription = null, tint = content.copy(alpha = 0.7f))
                }
                Spacer(Modifier.height(10.dp))
                val image = rememberNotificationImage(config, snapshot, dark, cardWidth - 28.dp, expanded = true)
                image?.let {
                    Image(it, contentDescription = null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/**
 * Approximation of the system's Live Update template: title and text, the graph as the large
 * icon, and the progress bar as a gauge through the ranges.
 */
@Composable
private fun LiveUpdateCard(
    config: NotificationConfig,
    snapshot: WidgetSnapshot?,
    palette: WidgetPalette,
    container: Color,
    content: Color,
    accent: Color
) {
    val context = LocalContext.current
    val data = snapshot ?: return
    val time = DateFormat.getTimeFormat(context).format(Date(data.currentTime))
    val input = remember(config, data) { GlucoseNotificationStyler.iconInput(context, data, null) }
    val graph = produceState<ImageBitmap?>(null, config, data, palette) {
        value = if (!config.sparkline) null else withContext(Dispatchers.Default) {
            NotificationRenderer(context).graphIcon(config, data, 64f, context.resources.displayMetrics.density, palette).asImageBitmap()
        }
    }.value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppBadge(Color(LiveUpdate.color(config, data, palette)), 22.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.app_name) + " · " + time,
                color = content.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(LiveUpdate.title(context, config, data, input), color = content, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                LiveUpdate.text(context, config, data, input, time)?.let {
                    Text(it, color = content.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                }
            }
            graph?.let {
                Image(it, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            }
        }
        if (config.liveRangeBar && data.hasReading) {
            Spacer(Modifier.height(12.dp))
            RangeGauge(data, palette, accent)
        }
    }
}

@Composable
private fun RangeGauge(snapshot: WidgetSnapshot, palette: WidgetPalette, fallback: Color) {
    val zones = remember(snapshot) { LiveUpdate.zones(snapshot) }
    val position = LiveUpdate.position(snapshot, snapshot.currentMgDl)
    val marker = snapshot.status?.takeUnless { snapshot.isStale }?.let { Color(palette.rangeColor(it)) } ?: fallback
    Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
        val bar = 6.dp.toPx()
        val gap = 2.dp.toPx()
        val total = zones.last().second - zones.first().first
        val usable = size.width - gap * (zones.size - 1)
        var x = 0f
        val top = (size.height - bar) / 2f
        zones.forEach { (from, to, zone) ->
            val width = usable * (to - from) / total
            drawRoundRect(Color(palette.rangeColor(zone)), Offset(x, top), Size(width, bar), CornerRadius(bar / 2f))
            x += width + gap
        }
        val cx = (size.width * position).coerceIn(size.height / 2f, size.width - size.height / 2f)
        drawCircle(Color.White, radius = size.height / 2f * 0.92f, center = Offset(cx, size.height / 2f))
        drawCircle(marker, radius = size.height / 2f * 0.62f, center = Offset(cx, size.height / 2f))
    }
}

@Composable
private fun AppBadge(accent: Color, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(accent),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(R.drawable.novalue), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
private fun rememberNotificationImage(config: NotificationConfig, snapshot: WidgetSnapshot?, dark: Boolean, width: Dp, expanded: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    return produceState<ImageBitmap?>(null, config, snapshot, dark, width, expanded) {
        val data = snapshot ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val renderer = NotificationRenderer(context)
            val palette = WidgetPalette.forNotification(context, dark)
            val widthDp = width.value.coerceAtLeast(120f)
            if (expanded) renderer.expanded(config, data, widthDp, density, palette)?.asImageBitmap()
            else renderer.collapsed(config, data, widthDp, density, palette).asImageBitmap()
        }
    }.value
}

// ---- Options -------------------------------------------------------------------------------------

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
private fun HoursRow(title: String, icon: ImageVector, hours: Int, onSelect: (Int) -> Unit) {
    SettingsSegmentedRow(title = title, icon = icon) {
        Segmented(WIDGET_GRAPH_HOURS.toList(), hours, onSelect) { stringResource(R.string.widget_hours_short, it) }
    }
}

@Composable
private fun NotificationSection(
    config: NotificationConfig,
    onChange: (NotificationConfig) -> Unit,
    showNotification: Boolean,
    onShowNotification: (Boolean) -> Unit
) {
    SettingsSection(title = stringResource(R.string.notif_section_notification)) {
        SettingsSwitchRow(
            title = stringResource(R.string.notif_show),
            subtitle = stringResource(R.string.notif_show_desc),
            icon = Icons.Default.Notifications,
            checked = showNotification,
            onCheckedChange = onShowNotification
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
        SettingsSwitchRow(
            title = stringResource(R.string.notif_time_in_range),
            subtitle = stringResource(R.string.notif_time_in_range_desc),
            icon = Icons.Default.PieChart,
            checked = config.showTimeInRange,
            onCheckedChange = { onChange(config.copy(showTimeInRange = it)) }
        )
        SettingsSwitchRow(
            title = stringResource(R.string.notif_sparkline),
            subtitle = stringResource(R.string.notif_sparkline_desc),
            icon = Icons.Default.Insights,
            checked = config.sparkline,
            onCheckedChange = { onChange(config.copy(sparkline = it)) }
        )
        if (config.sparkline) {
            HoursRow(stringResource(R.string.notif_sparkline_hours), Icons.Default.Timelapse, config.sparklineHours) {
                onChange(config.copy(sparklineHours = it))
            }
        }
        SettingsSwitchRow(
            title = stringResource(R.string.widget_range_colors),
            subtitle = stringResource(R.string.widget_range_colors_desc),
            icon = Icons.Default.InvertColors,
            checked = config.rangeColors,
            onCheckedChange = { onChange(config.copy(rangeColors = it)) }
        )
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
            valueRange = NotificationConfig.TEXT_SCALE_RANGE.first.toFloat()..NotificationConfig.TEXT_SCALE_RANGE.last.toFloat()
        )
    }
}

@Composable
private fun LiveUpdateSection(config: NotificationConfig, onChange: (NotificationConfig) -> Unit, allowed: Boolean, showNotification: Boolean) {
    val context = LocalContext.current
    val supported = LiveUpdate.supported
    SettingsSection(title = stringResource(R.string.notif_section_live)) {
        SettingsSwitchRow(
            title = stringResource(R.string.notif_live_update),
            subtitle = stringResource(
                when {
                    !supported -> R.string.notif_live_unsupported
                    !showNotification -> R.string.notif_live_needs_notification
                    else -> R.string.notif_live_update_desc
                }
            ),
            icon = Icons.Default.SmartButton,
            checked = supported && config.liveUpdate,
            enabled = supported,
            onCheckedChange = { onChange(config.copy(liveUpdate = it)) }
        )
        if (supported && config.liveUpdate) {
            if (!allowed) {
                SettingsActionRow(
                    title = stringResource(R.string.notif_live_allow),
                    subtitle = stringResource(R.string.notif_live_allow_desc),
                    icon = Icons.Default.Settings,
                    onClick = { runCatching { context.startActivity(LiveUpdate.settingsIntent(context)) } }
                )
            }
            SettingsSegmentedRow(title = stringResource(R.string.notif_live_chip_icon), icon = Icons.Default.LooksOne) {
                ChipRow(StatusIconKind.entries, config.liveChipIcon, { onChange(config.copy(liveChipIcon = it)) }) { Text(stringResource(it.labelRes)) }
            }
            SettingsSegmentedRow(
                title = stringResource(R.string.notif_live_chip_text),
                subtitle = stringResource(R.string.notif_live_chip_text_desc),
                icon = Icons.AutoMirrored.Filled.ShortText
            ) {
                ChipRow(LiveChipText.entries, config.liveChipText, { onChange(config.copy(liveChipText = it)) }) { Text(stringResource(it.labelRes)) }
            }
            SettingsSwitchRow(
                title = stringResource(R.string.notif_live_range_bar),
                subtitle = stringResource(R.string.notif_live_range_bar_desc),
                icon = Icons.Default.LinearScale,
                checked = config.liveRangeBar,
                onCheckedChange = { onChange(config.copy(liveRangeBar = it)) }
            )
        }
    }
    if (supported && config.liveUpdate) {
        Text(
            stringResource(R.string.notif_live_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = ScreenLayout.CardPadding)
        )
    }
}

@Composable
private fun ExpandedSection(config: NotificationConfig, onChange: (NotificationConfig) -> Unit) {
    SettingsSection(title = stringResource(R.string.notif_section_expanded)) {
        SettingsSwitchRow(
            title = stringResource(R.string.widget_section_graph),
            icon = Icons.AutoMirrored.Filled.ShowChart,
            checked = config.expandedGraph,
            onCheckedChange = { onChange(config.copy(expandedGraph = it)) }
        )
        if (config.expandedGraph) {
            HoursRow(stringResource(R.string.widget_graph_hours), Icons.Default.Timelapse, config.graphHours) {
                onChange(config.copy(graphHours = it))
            }
            SettingsSegmentedRow(title = stringResource(R.string.notif_graph_height), icon = Icons.Default.Height) {
                Segmented(NotificationGraphHeight.entries, config.graphHeight, { onChange(config.copy(graphHeight = it)) }) { stringResource(it.labelRes) }
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
        SettingsSwitchRow(
            title = stringResource(R.string.notif_expanded_stats),
            subtitle = stringResource(R.string.notif_expanded_stats_desc),
            icon = Icons.Default.DataUsage,
            checked = config.expandedStats,
            onCheckedChange = { onChange(config.copy(expandedStats = it)) }
        )
        if (config.expandedStats || config.showTimeInRange) {
            SettingsSegmentedRow(title = stringResource(R.string.widget_period), icon = Icons.Default.DateRange) {
                Segmented(WidgetStatsPeriod.entries, config.statsPeriod, { onChange(config.copy(statsPeriod = it)) }) { stringResource(it.labelRes) }
            }
        }
    }
}

@Composable
private fun StatusIconSection(config: NotificationConfig, onChange: (NotificationConfig) -> Unit) {
    val extras = listOf<StatusIconKind?>(null) + StatusIconKind.entries.filter { it != StatusIconKind.APP }

    SettingsSection(title = stringResource(R.string.notif_section_icons)) {
        SettingsSegmentedRow(
            title = stringResource(R.string.notif_main_icon),
            subtitle = stringResource(R.string.notif_main_icon_desc),
            icon = Icons.Default.LooksOne
        ) {
            ChipRow(StatusIconKind.entries, config.mainIcon, { onChange(config.copy(mainIcon = it)) }) { Text(stringResource(it.labelRes)) }
        }
        SettingsSegmentedRow(title = stringResource(R.string.notif_second_icon), icon = Icons.Default.LooksTwo) {
            ChipRow(extras, config.secondIcon, { onChange(config.copy(secondIcon = it)) }) { Text(stringResource(it?.labelRes ?: R.string.off)) }
        }
        SettingsSegmentedRow(title = stringResource(R.string.notif_third_icon), icon = Icons.Default.Looks3) {
            ChipRow(extras, config.thirdIcon, { onChange(config.copy(thirdIcon = it)) }) { Text(stringResource(it?.labelRes ?: R.string.off)) }
        }
        SettingsSliderRow(
            title = stringResource(R.string.notif_icon_size),
            valueText = "${config.iconScale}%",
            icon = Icons.Default.ZoomIn,
            value = config.iconScale.toFloat(),
            onValueChange = { onChange(config.copy(iconScale = (it / 5f).roundToInt() * 5)) },
            valueRange = NotificationConfig.ICON_SCALE_RANGE.first.toFloat()..NotificationConfig.ICON_SCALE_RANGE.last.toFloat()
        )
        SettingsSegmentedRow(title = stringResource(R.string.notif_icon_weight), icon = Icons.Default.FormatBold) {
            Segmented(StatusIconWeight.entries, config.iconWeight, { onChange(config.copy(iconWeight = it)) }) { stringResource(it.labelRes) }
        }
    }
    Text(
        stringResource(R.string.notif_icons_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = ScreenLayout.CardPadding)
    )
}
