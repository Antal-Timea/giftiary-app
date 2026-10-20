package app.giftiary.presentation.auth

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.giftiary.R
import app.giftiary.data.auth.ProviderSignIn
import app.giftiary.data.auth.ProviderSignInState
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.presentation.components.GiftiarySecondaryButton
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Signing in with the providers this build offers, shared by sign-in and account creation. */
@HiltViewModel
class ProviderSignInViewModel @Inject constructor(
    private val providerSignIn: ProviderSignIn,
) : ViewModel() {

    val providers: List<AuthProvider> = providerSignIn.providers

    val state: StateFlow<ProviderSignInState> = providerSignIn.state

    // Finding the page takes a request; the screen opens it once it is known.
    private val _pageToOpen = MutableStateFlow<String?>(null)
    val pageToOpen: StateFlow<String?> = _pageToOpen.asStateFlow()

    fun begin(provider: AuthProvider) {
        viewModelScope.launch { providerSignIn.begin(provider)?.let { _pageToOpen.value = it } }
    }

    /** The page to open now, handed out once. */
    fun takePageToOpen(): String? = _pageToOpen.getAndUpdate { null }

    fun onBrowserMissing() = providerSignIn.onBrowserMissing()
}

/** Opens a provider's sign-in page in a Custom Tab of the app's own task; false without a browser. */
internal fun openProviderPage(context: Context, url: String): Boolean = try {
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
    true
} catch (_: ActivityNotFoundException) {
    false
}

@Composable
internal fun rememberProviderClick(viewModel: ProviderSignInViewModel): (AuthProvider) -> Unit {
    // The activity, so the tab opens in Giftiary's task and the callback link closes it again.
    val context = LocalContext.current
    val page by viewModel.pageToOpen.collectAsStateWithLifecycle()
    LaunchedEffect(page) {
        val url = viewModel.takePageToOpen() ?: return@LaunchedEffect
        if (!openProviderPage(context, url)) viewModel.onBrowserMissing()
    }
    return viewModel::begin
}

/** "or", then one button per offered provider, with the provider flow's progress or error. Nothing without providers. */
@Composable
internal fun ProviderSignInSection(
    providers: List<AuthProvider>,
    state: ProviderSignInState,
    onProvider: (AuthProvider) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (providers.isEmpty()) return
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
            Text(
                text = stringResource(R.string.auth_or),
                modifier = Modifier.padding(horizontal = GiftiarySpacing.m),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
        }
        providers.forEach { provider ->
            GiftiarySecondaryButton(
                text = stringResource(provider.labelRes()),
                onClick = { onProvider(provider) },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && !state.isBusy,
                leadingIcon = painterResource(provider.markRes()),
            )
        }
        val message = when (state) {
            ProviderSignInState.Exchanging -> stringResource(R.string.auth_provider_signing_in) to colors.textSecondary
            is ProviderSignInState.Failed -> stringResource(state.error.messageRes()) to colors.error
            ProviderSignInState.Idle, is ProviderSignInState.Launching -> null
        }
        message?.let { (text, color) ->
            Text(
                text = text,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
        }
    }
}

private fun AuthProvider.labelRes() = when (this) {
    AuthProvider.GOOGLE -> R.string.auth_continue_with_google
    AuthProvider.FACEBOOK -> R.string.auth_continue_with_facebook
}

private fun AuthProvider.markRes() = when (this) {
    AuthProvider.GOOGLE -> R.drawable.ic_provider_google
    AuthProvider.FACEBOOK -> R.drawable.ic_provider_facebook
}
