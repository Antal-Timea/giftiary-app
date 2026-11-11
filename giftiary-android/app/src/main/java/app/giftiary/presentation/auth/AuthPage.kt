package app.giftiary.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import app.giftiary.presentation.components.DecoratedPage
import app.giftiary.presentation.components.GiftCutout
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * The page every auth screen is set on, as on the approved Sign in board: ivory, the task gutter,
 * scrolling above the keyboard. Screens start it with a `GiftiaryTaskHeader`. A [decorated] page
 * has the board's gift cut-outs behind it.
 *
 * It only scrolls when the content is taller than the window, e.g. at large text, on a short screen
 * or with the keyboard up; otherwise a drag would only move the fields against the fixed cut-outs.
 */
@Composable
internal fun AuthPage(modifier: Modifier = Modifier, decorated: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(modifier = modifier, containerColor = GiftiaryTheme.colors.background) { contentPadding ->
        val page = @Composable {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    // The keyboard covers the navigation bar, which contentPadding already keeps clear of.
                    .consumeWindowInsets(contentPadding)
                    .imePadding()
                    .verticalScroll(scroll, enabled = scroll.canScrollForward || scroll.canScrollBackward)
                    .giftiaryReadableWidth()
                    .padding(horizontal = GiftiarySpacing.taskGutter, vertical = GiftiarySpacing.browseGutter),
                verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
                content = content,
            )
        }
        if (decorated) DecoratedPage(top = GiftCutout.LARGE, bottom = GiftCutout.SMALL, content = page) else page()
    }
}

/** An answer to the last attempt, such as a failed sign-in, announced when it appears. */
@Composable
internal fun AuthStatusText(text: String, modifier: Modifier = Modifier, color: Color = GiftiaryTheme.colors.error) {
    Text(
        text = text,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = GiftiaryType.bodySmall,
        color = color,
    )
}
