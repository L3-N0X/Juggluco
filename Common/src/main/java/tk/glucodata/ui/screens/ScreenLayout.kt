package tk.glucodata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale for the Compose screens.
 *
 * The rule for every screen: at most two levels of padded containers,
 * the screen gutter and the card padding. Anything nested deeper reads as a
 * double indent, so inner blocks are separated with spacing or dividers
 * instead of another padded container.
 */
object ScreenLayout {
    /** Horizontal gutter between screen content and the display edges. */
    val Gutter = 16.dp

    /** Content padding inside a card or other surface placed in the gutter. */
    val CardPadding = 16.dp

    /** Vertical gap between top level sections of a screen. */
    val SectionSpacing = 16.dp

    /** Gap above the first item of a scrolling screen (the app bar sits above it). */
    val TopPadding = 12.dp

    /** Bottom gap so the last item clears the bottom navigation bar and any FAB. */
    val BottomPadding = 96.dp
}

/**
 * Standard body for a scrolling tab screen: the persistent app bar supplies the
 * title, this supplies the gutter, section spacing and bottom bar clearance.
 */
@Composable
fun ScreenContent(
    modifier: Modifier = Modifier,
    spacing: Dp = ScreenLayout.SectionSpacing,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = ScreenLayout.Gutter,
                end = ScreenLayout.Gutter,
                top = ScreenLayout.TopPadding,
                bottom = ScreenLayout.BottomPadding
            ),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/** Heading above a group of cards inside a screen, aligned with the gutter. */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}
