package app.giftiary.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * [content] with an [action] at its end, both centred on one line while the content keeps room for
 * its longest word. With large text the action moves under the content, at its start, instead of
 * squeezing a heading or a sentence to a letter per line.
 */
@Composable
fun TrailingActionRow(
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(contents = listOf(content, action), modifier = modifier) { (contentMeasurables, actionMeasurables), constraints ->
        val contentMeasurable = contentMeasurables.single()
        val actionPlaceable = actionMeasurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = constraints.maxWidth
        val besideWidth = width - actionPlaceable.width
        if (besideWidth > 0 && contentMeasurable.minIntrinsicWidth(Constraints.Infinity) <= besideWidth) {
            val contentPlaceable = contentMeasurable.measure(Constraints.fixedWidth(besideWidth))
            val height = maxOf(contentPlaceable.height, actionPlaceable.height)
            layout(width, height) {
                contentPlaceable.placeRelative(0, (height - contentPlaceable.height) / 2)
                actionPlaceable.placeRelative(besideWidth, (height - actionPlaceable.height) / 2)
            }
        } else {
            val contentPlaceable = contentMeasurable.measure(Constraints.fixedWidth(width))
            layout(width, contentPlaceable.height + actionPlaceable.height) {
                contentPlaceable.placeRelative(0, 0)
                actionPlaceable.placeRelative(0, contentPlaceable.height)
            }
        }
    }
}
