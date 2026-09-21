package tk.glucodata.ui.screens.settings

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import tk.glucodata.ui.screens.ScreenLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        )
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ScreenLayout.Gutter,
                    end = ScreenLayout.Gutter,
                    top = ScreenLayout.TopPadding,
                    bottom = 36.dp
                ),
            verticalArrangement = Arrangement.spacedBy(ScreenLayout.SectionSpacing)
        ) {
            content()
        }
    }
}

private val SettingsGroupOuterRadius = 20.dp
private val SettingsGroupInnerRadius = 4.dp
private val SettingsGroupItemGap = 3.dp

private data class SettingsGroupItemBounds(val offset: Offset, val size: Size)

private fun settingsGroupItemShape(index: Int, count: Int): RoundedCornerShape = when {
    count <= 1 -> RoundedCornerShape(SettingsGroupOuterRadius)
    index == 0 -> RoundedCornerShape(
        topStart = SettingsGroupOuterRadius,
        topEnd = SettingsGroupOuterRadius,
        bottomStart = SettingsGroupInnerRadius,
        bottomEnd = SettingsGroupInnerRadius
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = SettingsGroupInnerRadius,
        topEnd = SettingsGroupInnerRadius,
        bottomStart = SettingsGroupOuterRadius,
        bottomEnd = SettingsGroupOuterRadius
    )
    else -> RoundedCornerShape(SettingsGroupInnerRadius)
}

/**
 * Lays out its children as a Material 3 grouped list: every direct child becomes
 * its own filled item with a tiny gap to its neighbors, square corners where two
 * items touch, and the full radius only at the very top and bottom of the group.
 */
@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val itemColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    val itemBounds = remember { mutableStateOf(emptyList<SettingsGroupItemBounds>()) }

    Layout(
        content = content,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SettingsGroupOuterRadius))
            .drawBehind {
                val bounds = itemBounds.value
                val count = bounds.size
                bounds.forEachIndexed { index, item ->
                    val shape = settingsGroupItemShape(index, count)
                    val outline = shape.createOutline(item.size, layoutDirection, this)
                    translate(left = item.offset.x, top = item.offset.y) {
                        drawOutline(outline, color = itemColor)
                    }
                }
            }
    ) { measurables, constraints ->
        val gapPx = SettingsGroupItemGap.roundToPx()
        val childConstraints = Constraints.fixedWidth(constraints.maxWidth)
        val placeables = measurables.map { it.measure(childConstraints) }
        val totalHeight = if (placeables.isEmpty()) {
            0
        } else {
            placeables.sumOf { it.height } + gapPx * (placeables.size - 1)
        }

        val bounds = ArrayList<SettingsGroupItemBounds>(placeables.size)
        var y = 0
        for (placeable in placeables) {
            bounds.add(SettingsGroupItemBounds(Offset(0f, y.toFloat()), Size(placeable.width.toFloat(), placeable.height.toFloat())))
            y += placeable.height + gapPx
        }
        itemBounds.value = bounds

        layout(constraints.maxWidth, totalHeight) {
            var placeY = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(0, placeY)
                placeY += placeable.height + gapPx
            }
        }
    }
}

@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = ScreenLayout.CardPadding, bottom = 6.dp)
            )
        }
        SettingsGroup(content = content)
    }
}

@Composable
fun SettingsIcon(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * A setting row that opens a dedicated screen or section.
 * Renders an icon on the left and text in the middle. A row that only
 * navigates has no trailing indicator; a caret + vertical line + switch only
 * appears when the row combines navigation with a boolean value, since tapping
 * the switch and tapping the rest of the row do different things.
 */
@Composable
fun SettingsNavRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
) {
    val hasToggle = checked != null && onCheckedChange != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(
            icon = icon,
            tint = if (enabled) iconTint else MaterialTheme.colorScheme.outline,
            backgroundColor = if (enabled) iconBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    lineHeight = 18.sp
                )
            }
        }

        if (hasToggle) {
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .width(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

/**
 * Standard toggle row: icon on left, text in middle, switch on right.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(
            icon = icon,
            tint = if (enabled) iconTint else MaterialTheme.colorScheme.outline,
            backgroundColor = if (enabled) iconBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    lineHeight = 18.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/**
 * Action row: icon on left, text in middle, optional action/trailing widget on right.
 */
@Composable
fun SettingsActionRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(
            icon = icon,
            tint = if (enabled) iconTint else MaterialTheme.colorScheme.outline,
            backgroundColor = if (enabled) iconBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    lineHeight = 18.sp
                )
            }
        }

        if (trailingContent != null) {
            trailingContent()
        }
    }
}

/**
 * Slider row: icon on left, title and value text on right, slider underneath.
 */
@Composable
fun SettingsSliderRow(
    title: String,
    valueText: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(
                icon = icon,
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.outline,
                backgroundColor = if (enabled) iconBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            )

            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp, top = 2.dp)
        )
    }
}

/**
 * Segmented selection row: icon and text on top, segmented control underneath.
 */
@Composable
fun SettingsSegmentedRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIcon(
                icon = icon,
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.outline,
                backgroundColor = if (enabled) iconBackground else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        content()
    }
}

/**
 * Informational card adhering to Material 3 tokens.
 */
@Composable
fun SettingsInfoCard(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Info,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsInfoCard(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Info
) {
    SettingsInfoCard(
        modifier = modifier,
        icon = icon
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )
    }
}

// Backward compatibility helper during migration
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector? = null,
    categorySubtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SettingsSection(
        modifier = modifier,
        title = categorySubtitle ?: title.takeIf { it.isNotEmpty() },
        content = content
    )
}

/**
 * Converts an Android [Spanned] instance to a Compose [AnnotatedString],
 * preserving bold, italic, underline, and foreground color styling.
 */
fun Spanned.toAnnotatedString(): AnnotatedString {
    val text = this.toString()
    val builder = AnnotatedString.Builder(text)
    val spans = getSpans(0, length, Any::class.java)
    for (span in spans) {
        val start = getSpanStart(span).coerceIn(0, text.length)
        val end = getSpanEnd(span).coerceIn(0, text.length)
        if (start >= end) continue
        when (span) {
            is StyleSpan -> {
                when (span.style) {
                    Typeface.BOLD -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    Typeface.ITALIC -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    Typeface.BOLD_ITALIC -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                }
            }
            is UnderlineSpan -> {
                builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            }
            is ForegroundColorSpan -> {
                builder.addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
            }
        }
    }
    return builder.toAnnotatedString()
}

/**
 * Parses an HTML string cleanly using [HtmlCompat], stripping tags and returning
 * a styled [AnnotatedString] suitable for Jetpack Compose [Text] components.
 */
fun htmlToAnnotatedString(html: String): AnnotatedString {
    if (html.isBlank()) return AnnotatedString("")
    return try {
        val spanned = HtmlCompat.fromHtml(
            html,
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
        spanned.toAnnotatedString()
    } catch (_: Throwable) {
        AnnotatedString(html.replace(Regex("<[^>]+>"), " ").trim())
    }
}
