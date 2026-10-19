package app.giftiary.data.auth

import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The providers this build offers; any other one is never shown or started. */
data class EnabledAuthProviders(val providers: Set<AuthProvider>)

sealed interface ProviderSignInState {
    data object Idle : ProviderSignInState

    /** The provider's page is open in the browser; the app waits for the user to come back. */
    data class Launching(val provider: AuthProvider) : ProviderSignInState

    /** A callback code is being exchanged for a session. */
    data object Exchanging : ProviderSignInState

    data class Failed(val error: AuthError) : ProviderSignInState

    val isBusy: Boolean get() = this is Launching || this is Exchanging
}

/**
 * Signing in with a provider in a browser, from opening its page to the session it ends with.
 * Held in memory only: nothing of the flow or of callback links is saved.
 *
 * The page opens in a Custom Tab, and the user either comes back through the callback link or
 * closes the tab. The link arrives before the app resumes, so resuming while still [Launching]
 * means the tab was closed, and the sign-in is over without an error.
 */
@Singleton
class ProviderSignIn @Inject constructor(
    private val authRepository: AuthRepository,
    enabled: EnabledAuthProviders,
) {

    val providers: List<AuthProvider> = AuthProvider.entries.filter { it in enabled.providers }

    private val _state = MutableStateFlow<ProviderSignInState>(ProviderSignInState.Idle)
    val state: StateFlow<ProviderSignInState> = _state.asStateFlow()

    // Guards the state and the fields below. Each exchange gets a number; only the latest one
    // may finish the state, and only one a provider flow waited for may report a failure.
    private val lock = Any()
    private var exchanges = 0
    private var exchangeForProvider = false

    /**
     * The page to open for [provider], or null when it is not offered by this build or a sign-in
     * is already under way, e.g. after a second tap.
     */
    fun begin(provider: AuthProvider): String? = synchronized(lock) {
        if (provider !in providers || _state.value.isBusy) return null
        val url = try {
            authRepository.providerSignInUrl(provider)
        } catch (_: Exception) {
            _state.value = ProviderSignInState.Failed(AuthError.PROVIDER_FAILED)
            return null
        }
        _state.value = ProviderSignInState.Launching(provider)
        url
    }

    fun onBrowserMissing() = synchronized(lock) {
        if (_state.value is ProviderSignInState.Launching) _state.value = ProviderSignInState.Failed(AuthError.NO_BROWSER)
    }

    fun onAppResumed() = synchronized(lock) {
        if (_state.value is ProviderSignInState.Launching) _state.value = ProviderSignInState.Idle
    }

    /**
     * A callback reported an error. Shown only if a provider flow waited for it: an expired
     * sign-up confirmation needs no message, since signing in normally still works.
     */
    fun onCallbackFailed(error: AuthError) = synchronized(lock) {
        if (_state.value is ProviderSignInState.Launching) _state.value = ProviderSignInState.Failed(error)
    }

    /** A callback code is being exchanged; returns the number to finish it with. */
    fun onExchangeStarted(): Int = synchronized(lock) {
        val current = _state.value
        exchangeForProvider = current is ProviderSignInState.Launching ||
            (current == ProviderSignInState.Exchanging && exchangeForProvider)
        _state.value = ProviderSignInState.Exchanging
        ++exchanges
    }

    fun onExchangeFinished(exchange: Int, error: AuthError?) = synchronized(lock) {
        if (exchange != exchanges || _state.value != ProviderSignInState.Exchanging) return
        _state.value = if (error != null && exchangeForProvider) {
            ProviderSignInState.Failed(error.asProviderError())
        } else {
            ProviderSignInState.Idle
        }
    }

    // Credentials errors cannot apply to a provider; what is left is worth retrying or reporting.
    private fun AuthError.asProviderError() = when (this) {
        AuthError.NETWORK_UNAVAILABLE, AuthError.RATE_LIMITED -> this
        else -> AuthError.PROVIDER_FAILED
    }
}
