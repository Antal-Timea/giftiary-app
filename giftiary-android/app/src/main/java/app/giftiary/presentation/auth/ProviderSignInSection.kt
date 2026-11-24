package app.giftiary.presentation.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import app.giftiary.presentation.theme.GiftiaryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Signing in with the providers offered now, shared by sign-in and account creation. */
@HiltViewModel
class ProviderSignInViewModel @Inject constructor(
    private val providerSignIn: ProviderSignIn,
) : ViewModel() {

    val providers: StateFlow<List<AuthProvider>> = providerSignIn.providers

    init {
        // Each time sign-in or account creation opens, so a flag changed in the dashboard shows
        // without a new release. The screen draws at once and gains Facebook if it is switched on.
        viewModelScope.launch { providerSignIn.refreshProviders() }
    }

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

/**
 * Opens a provider's sign-in page in a Custom Tab of the app's own task; false without a browser.
 *
 * The tab is pinned to a browser: left to the system, a page on facebook.com would go straight to
 * the Facebook app, which claims the domain but cannot complete this sign-in.
 */
internal fun openProviderPage(context: Context, url: String): Boolean {
    val browser = browserFor(context) ?: return false
    val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
    tab.intent.setPackage(browser)
    return try {
        tab.launchUrl(context, url.toUri())
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

// Apps that open any web link, i.e. browsers, not apps that only claim a domain. The default one
// is preferred if it has Custom Tabs, then any with Custom Tabs, then any browser at all.
private fun browserFor(context: Context): String? {
    val anyWebLink = Intent(Intent.ACTION_VIEW, "https://".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val browsers = context.packageManager.queryIntentActivities(anyWebLink, 0)
        .map { it.activityInfo.packageName }
        .distinct()
    return CustomTabsClient.getPackageName(context, browsers) ?: browsers.firstOrNull()
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

/**
 * One button per offered provider, the provider flow's progress or error, then "or" before the
 * email form below, as on the approved Sign in board. Nothing without providers.
 */
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
        message?.let { (text, color) -> AuthStatusText(text = text, color = color) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
            Text(
                text = stringResource(R.string.auth_or),
                modifier = Modifier.padding(horizontal = GiftiarySpacing.m),
                style = GiftiaryType.bodySmall,
                color = colors.textMuted,
            )
            HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
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
