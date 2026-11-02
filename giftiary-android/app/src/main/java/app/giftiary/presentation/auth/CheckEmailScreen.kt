package app.giftiary.presentation.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import app.giftiary.R
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType

/**
 * Shown after sign-up. Nobody is signed in yet; opening the emailed link on this phone signs the
 * user in through the auth callback, which takes the app past this screen on its own.
 */
@Composable
fun CheckEmailScreen(email: String, onBackToSignIn: () -> Unit, modifier: Modifier = Modifier) {
    AuthPage(modifier) {
        GiftiaryTaskHeader(
            title = stringResource(R.string.auth_check_email_title),
            subtitle = stringResource(R.string.auth_check_email_message, email),
            onBack = null,
        )
        Text(
            text = stringResource(R.string.auth_check_email_other_device),
            style = GiftiaryType.bodySmall,
            color = GiftiaryTheme.colors.textSecondary,
        )
        GiftiarySecondaryButton(
            text = stringResource(R.string.auth_back_to_sign_in),
            onClick = onBackToSignIn,
            modifier = Modifier.fillMaxWidth().padding(top = GiftiarySpacing.l),
        )
    }
}

@PreviewLightDark
@Composable
private fun CheckEmailPreview() {
    GiftiaryTheme {
        CheckEmailScreen(email = "anna@example.com", onBackToSignIn = {})
    }
}
