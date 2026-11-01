package app.giftiary.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * The phone navigation unit of the design system (DS-04): the aqua wave, the ivory bar with the
 * four destinations, and the coral global add centred on the wave. They are one unit and never
 * appear apart. The add is an action, never a fifth destination: [onAdd] opens Quick Add.
 *
 * [selected] is null on a destination outside the four sections.
 */
@Composable
fun GiftiaryNavigationBar(
    selected: AppSection?,
    onSelect: (AppSection) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftiaryTheme.colors
    Box(modifier = modifier.fillMaxWidth()) {
        Wave(Modifier.fillMaxWidth().height(WaveHeight))
        Row(
            modifier = Modifier
                // The bar tucks 20dp under the wave.
                .padding(top = WaveHeight - 20.dp)
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = GiftiaryShapes.sheet, ambientColor = colors.textPrimary, spotColor = colors.textPrimary)
                .background(colors.navigationSurface, GiftiaryShapes.sheet)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 16.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarItem.entries.forEach { item ->
                NavigationItem(item = item, selected = item.section == selected, onClick = { onSelect(item.section) })
            }
        }
        GlobalAdd(onClick = onAdd, modifier = Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun NavigationItem(item: BarItem, selected: Boolean, onClick: () -> Unit) {
    val colors = GiftiaryTheme.colors
    val tint = if (selected) colors.navigationSelected else colors.navigationUnselected
    Column(
        modifier = Modifier
            .clip(GiftiaryShapes.container)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
        Text(
            text = stringResource(item.label),
            style = GiftiaryType.navigationLabel.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = tint,
        )
    }
}

@Composable
private fun GlobalAdd(onClick: () -> Unit, modifier: Modifier) {
    val colors = GiftiaryTheme.colors
    val label = stringResource(R.string.nav_global_add)
    Box(
        modifier = modifier
            .size(58.dp)
            .shadow(elevation = 6.dp, shape = GiftiaryShapes.pill, ambientColor = colors.primaryAction, spotColor = colors.primaryAction)
            .clip(GiftiaryShapes.pill)
            .background(colors.primaryAction)
            .border(3.dp, colors.background, GiftiaryShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = PlusIcon, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.onPrimaryAction)
    }
}

/** The design's curve, M0,32 C120,-6 270,-6 390,32 on a 390×46 box, stretched to the width. */
@Composable
private fun Wave(modifier: Modifier) {
    val color = GiftiaryTheme.colors.surfaceContext
    Canvas(modifier = modifier) {
        val x = size.width / 390f
        val y = size.height / 46f
        val path = Path().apply {
            moveTo(0f, 32f * y)
            cubicTo(120f * x, -6f * y, 270f * x, -6f * y, 390f * x, 32f * y)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color)
    }
}

private val WaveHeight = 52.dp

/** The four destinations, in the bar's order; Lists is the Wishlists section. */
private enum class BarItem(
    val section: AppSection,
    @StringRes val label: Int,
    paths: List<String>,
    dots: List<String> = emptyList(),
) {
    HOME(
        AppSection.HOME,
        R.string.nav_home,
        listOf(
            "M3,11.5 L12,4 l9,7.5",
            "M5.5,10v9a1,1 0 0,0 1,1H9a1,1 0 0,0 1,-1v-4a1,1 0 0,1 1,-1h2a1,1 0 0,1 1,1v4a1,1 0 0,0 1,1h2.5a1,1 0 0,0 1,-1v-9",
        ),
    ),
    PEOPLE(
        AppSection.PEOPLE,
        R.string.people_title,
        listOf(
            "M5.6,8a3.4,3.4 0 1,0 6.8,0a3.4,3.4 0 1,0 -6.8,0",
            "M2.6,20c0,-3.5 2.9,-6 6.4,-6s6.4,2.5 6.4,6a1,1 0 0,1 -1,1H3.6a1,1 0 0,1 -1,-1Z",
        ),
    ),
    GIFTS(
        AppSection.GIFTS,
        R.string.gifts_title,
        listOf(
            "M5.5,10h13a1.5,1.5 0 0,1 1.5,1.5v7a1.5,1.5 0 0,1 -1.5,1.5h-13a1.5,1.5 0 0,1 -1.5,-1.5v-7a1.5,1.5 0 0,1 1.5,-1.5Z",
            "M12,10v10M4,10V8a2,2 0 0,1 2,-2h1.5c1.5,0 2.5,1 2.5,2 0,-1 1,-2 2.5,-2H14a2,2 0 0,1 2,2v2",
            "M9,6c-1.2,0 -2,-0.9 -2,-2s1,-2 2,-2c1.5,0 3,2 3,4M15,6c1.2,0 2,-0.9 2,-2s-1,-2 -2,-2c-1.5,0 -3,2 -3,4",
        ),
    ),
    LISTS(
        AppSection.WISHLISTS,
        R.string.nav_lists,
        listOf("M9,6h11M9,12h11M9,18h11"),
        dots = listOf(
            "M3.25,6a1.25,1.25 0 1,0 2.5,0a1.25,1.25 0 1,0 -2.5,0",
            "M3.25,12a1.25,1.25 0 1,0 2.5,0a1.25,1.25 0 1,0 -2.5,0",
            "M3.25,18a1.25,1.25 0 1,0 2.5,0a1.25,1.25 0 1,0 -2.5,0",
        ),
    ),
    ;

    // Unselected icons are stroked at 1.8, the selected one at 2.2 (DS-04).
    val icon: ImageVector by lazy { strokedIcon(paths, dots, strokeWidth = 1.8f) }
    val selectedIcon: ImageVector by lazy { strokedIcon(paths, dots, strokeWidth = 2.2f) }
}

private val PlusIcon: ImageVector by lazy { strokedIcon(listOf("M12,5v14M5,12h14"), emptyList(), strokeWidth = 2.5f) }

// Drawn in black and tinted by Icon.
private fun strokedIcon(paths: List<String>, dots: List<String>, strokeWidth: Float): ImageVector =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        paths.forEach { path ->
            addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        dots.forEach { dot -> addPath(pathData = addPathNodes(dot), fill = SolidColor(Color.Black)) }
    }.build()
