package app.giftiary.presentation.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giftiary.presentation.theme.GiftiaryLayout
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/**
 * How much width the app has, from the window it is shown in: never the device, so split screen,
 * freeform windows, foldables and rotation are all handled alike. Material's breakpoints.
 */
enum class WidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    companion object {
        fun of(availableWidth: Dp): WidthClass = when {
            availableWidth < 600.dp -> COMPACT
            availableWidth < 840.dp -> MEDIUM
            else -> EXPANDED
        }
    }
}

/** The width class of the window the composition is in; set by [ProvideWidthClass]. */
val LocalWidthClass = staticCompositionLocalOf { WidthClass.COMPACT }

/** Measures the width available to [content] and provides its class, again on every resize. */
@Composable
fun ProvideWidthClass(content: @Composable () -> Unit) {
    BoxWithConstraints {
        CompositionLocalProvider(LocalWidthClass provides WidthClass.of(maxWidth), content = content)
    }
}

/**
 * Full width up to [GiftiaryLayout.readableMaxWidth], centred beyond it, so forms and text keep a
 * readable measure on wide windows. A phone never reaches the cap, so its layout is unchanged.
 */
fun Modifier.giftiaryReadableWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = GiftiaryLayout.readableMaxWidth)
    .fillMaxWidth()

/**
 * Full width up to [GiftiaryLayout.wideMaxWidth], centred beyond it: for layouts with several
 * columns, which should not spread across a very wide window.
 */
fun Modifier.giftiaryWideWidth(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = GiftiaryLayout.wideMaxWidth)
    .fillMaxWidth()

/** Phone, 7" and 10" tablets in both orientations, for screens that adapt to the window width. */
@Preview(name = "Phone", widthDp = 411, heightDp = 891, showBackground = true)
@Preview(name = "Tablet 7\" portrait", widthDp = 600, heightDp = 960, showBackground = true)
@Preview(name = "Tablet 10\" portrait", widthDp = 800, heightDp = 1280, showBackground = true)
@Preview(name = "Tablet 10\" landscape", widthDp = 1280, heightDp = 800, showBackground = true)
annotation class GiftiaryAdaptivePreviews

@GiftiaryAdaptivePreviews
@Composable
private fun ReadableWidthPreview() {
    GiftiaryTheme {
        ProvideWidthClass {
            Column(Modifier.giftiaryReadableWidth().padding(GiftiarySpacing.xl)) {
                // Shows the class each preview size gets; the column is capped beyond 640 dp.
                Text(LocalWidthClass.current.name, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
