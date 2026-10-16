package app.giftiary.presentation.auth

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
import app.giftiary.R
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme

/**
 * Shown after sign-up. Nobody is signed in yet; opening the emailed link on this phone signs the
 * user in through the auth callback, which takes the app past this screen on its own.
 */
@Composable
fun CheckEmailScreen(email: String, onBackToSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = GiftiarySpacing.xl, vertical = GiftiarySpacing.xxxl),
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.auth_check_email_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.displaySmall,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(GiftiarySpacing.l))
            Text(
                text = stringResource(R.string.auth_check_email_message, email),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(GiftiarySpacing.m))
            Text(
                text = stringResource(R.string.auth_check_email_other_device),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Spacer(Modifier.weight(1f))
            GiftiarySecondaryButton(
                text = stringResource(R.string.auth_back_to_sign_in),
                onClick = onBackToSignIn,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun CheckEmailPreview() {
    GiftiaryTheme {
        CheckEmailScreen(email = "anna@example.com", onBackToSignIn = {})
    }
}
