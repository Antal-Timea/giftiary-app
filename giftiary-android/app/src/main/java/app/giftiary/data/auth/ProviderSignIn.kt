package app.giftiary.data.auth

import app.giftiary.data.remote.FeatureFlagRemote
import app.giftiary.data.remote.RemoteException
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The providers this build offers; any other one is never shown or started. */
data class EnabledAuthProviders(val providers: Set<AuthProvider>)

sealed interface ProviderSignInState {
    data object Idle : ProviderSignInState

    /** The provider's page is being found or is open in the browser; the app waits for the user. */
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
    private val flags: FeatureFlagRemote,
) {

    private val built: List<AuthProvider> = AuthProvider.entries.filter { it in enabled.providers }

    // Facebook Login also needs its remote flag, so it can be offered or withdrawn without a new
    // release; until the flag has been read as on, it is not offered.
    private val _providers = MutableStateFlow(built - AuthProvider.FACEBOOK)

    /** The providers offered now, in a fixed order. */
    val providers: StateFlow<List<AuthProvider>> = _providers.asStateFlow()

    /**
     * Reads Facebook Login's remote flag and offers Facebook only if it is on. A missing flag or a
     * failed read leaves it out; the other providers never wait for the answer.
     *
     * Facebook is withdrawn while the flag is read, so a screen opening now never shows an
     * answer read for an earlier one. Only the latest read may decide: an older one finishing
     * later is dropped.
     */
    suspend fun refreshProviders() {
        if (AuthProvider.FACEBOOK !in built) return
        val refresh = synchronized(lock) {
            _providers.value = built - AuthProvider.FACEBOOK
            ++refreshes
        }
        val facebook = try {
            flags.isEnabled(FACEBOOK_LOGIN_FLAG)
        } catch (_: RemoteException) {
            false
        }
        synchronized(lock) {
            if (refresh == refreshes) _providers.value = if (facebook) built else built - AuthProvider.FACEBOOK
        }
    }

    private val _state = MutableStateFlow<ProviderSignInState>(ProviderSignInState.Idle)
    val state: StateFlow<ProviderSignInState> = _state.asStateFlow()

    // Guards the state, the providers and the fields below. Each exchange gets a number; only the
    // latest one may finish the state, and only one a provider flow waited for may report a
    // failure. Each attempt gets a number too, so a page found for an attempt that ended meanwhile
    // is dropped; and each flag read, so only the latest one sets the providers.
    private val lock = Any()
    private var refreshes = 0
    private var exchanges = 0
    private var exchangeForProvider = false
    private var attempts = 0

    /**
     * The page to open for [provider], or null when it is not offered now, a sign-in is
     * already under way (e.g. after a second tap), finding the page failed, or the attempt ended
     * while the page was being found.
     */
    suspend fun begin(provider: AuthProvider): String? {
        val attempt = synchronized(lock) {
            if (provider !in _providers.value || _state.value.isBusy) return null
            _state.value = ProviderSignInState.Launching(provider)
            ++attempts
        }
        val result = try {
            Result.success(authRepository.providerSignInUrl(provider))
        } catch (e: CancellationException) {
            synchronized(lock) { if (isCurrent(attempt, provider)) _state.value = ProviderSignInState.Idle }
            throw e
        } catch (e: AuthException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(AuthException(AuthError.PROVIDER_FAILED, e))
        }
        return synchronized(lock) {
            if (!isCurrent(attempt, provider)) return null
            result.getOrElse { e ->
                _state.value = ProviderSignInState.Failed((e as AuthException).error.asProviderError())
                null
            }
        }
    }

    private fun isCurrent(attempt: Int, provider: AuthProvider) =
        attempt == attempts && _state.value == ProviderSignInState.Launching(provider)

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

    companion object {
        const val FACEBOOK_LOGIN_FLAG = "facebook_login_enabled"
    }
}
