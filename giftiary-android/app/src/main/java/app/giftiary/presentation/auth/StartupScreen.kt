package app.giftiary.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import kotlinx.coroutines.delay

/**
 * Shown while a saved session is restored. That is usually instant, so the progress indicator
 * only appears if it takes long enough to notice, e.g. when an expired session is refreshed.
 */
@Composable
fun StartupScreen(modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PROGRESS_DELAY_MILLIS)
        showProgress = true
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xl),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = GiftiaryType.display,
                color = colors.textPrimary,
            )
            Box(Modifier.size(24.dp)) {
                if (showProgress) {
                    CircularProgressIndicator(color = colors.primaryAction, strokeWidth = 2.dp)
                }
            }
        }
    }
}

private const val PROGRESS_DELAY_MILLIS = 600L

@PreviewLightDark
@Composable
private fun StartupPreview() {
    GiftiaryTheme {
        StartupScreen()
    }
}
