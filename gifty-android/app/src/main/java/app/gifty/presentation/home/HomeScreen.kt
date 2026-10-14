package app.gifty.presentation.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.gifty.R
import app.gifty.domain.model.SyncStatus
import app.gifty.presentation.components.GiftyPrimaryButton
import app.gifty.presentation.components.GiftySecondaryButton
import app.gifty.presentation.sync.SyncStatusContent
import app.gifty.presentation.sync.SyncStatusLine
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme

@Composable
fun HomeScreen(
    syncStatus: SyncStatusContent,
    onOpenGifts: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenSyncAttention: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            SyncStatusLine(content = syncStatus, onOpenAttention = onOpenSyncAttention)
            Spacer(Modifier.height(GiftySpacing.l))
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
            Spacer(Modifier.height(GiftySpacing.s))
            TextButton(
                onClick = onSignOut,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
            ) {
                Text(text = stringResource(R.string.auth_sign_out), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreview() {
    GiftyTheme {
        HomeScreen(
            syncStatus = SyncStatusContent.Loaded(SyncStatus(waitingCount = 3, attention = emptyList())),
            onOpenGifts = {},
            onOpenPeople = {},
            onOpenSyncAttention = {},
            onSignOut = {},
        )
    }
}
