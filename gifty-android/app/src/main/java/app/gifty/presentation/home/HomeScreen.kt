package app.gifty.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.gifty.R
import app.gifty.presentation.components.GiftyPrimaryButton
import app.gifty.presentation.components.GiftySecondaryButton
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

@Composable
fun HomeScreen(onOpenGifts: () -> Unit, onOpenPeople: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftyTheme.colors
    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = GiftySpacing.xl, vertical = GiftySpacing.xxxl),
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(GiftySpacing.s))
            Text(
                text = stringResource(R.string.home_tagline),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.weight(1f))
            GiftyPrimaryButton(
                text = stringResource(R.string.home_open_gifts),
                onClick = onOpenGifts,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(GiftySpacing.m))
            GiftySecondaryButton(
                text = stringResource(R.string.home_open_people),
                onClick = onOpenPeople,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreview() {
    GiftyTheme {
        HomeScreen(onOpenGifts = {}, onOpenPeople = {})
    }
}
