package app.giftiary.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = GiftiaryTheme.colors.primaryAction)
    }
}

/**
 * Centered full-screen message for empty and error states. The optional [title] carries the
 * editorial voice; the [message] explains what to do next.
 */
@Composable
fun MessageContent(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val colors = GiftiaryTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = GiftiarySpacing.xxl, vertical = GiftiarySpacing.xl),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = GiftiaryType.sheetTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = message,
            style = GiftiaryType.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
