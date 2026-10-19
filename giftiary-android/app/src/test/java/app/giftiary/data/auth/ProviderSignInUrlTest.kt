package app.giftiary.data.auth

import android.net.Uri
import app.giftiary.domain.repository.AuthProvider
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The provider page built by the real supabase-kt [Auth], configured like the app's (no network). */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class ProviderSignInUrlTest {

    private val verifiers = MemoryCodeVerifierCache()

    private val auth: Auth = createSupabaseClient("https://example.supabase.co", "publishable-key") {
        install(Auth) {
            flowType = FlowType.PKCE
            scheme = AUTH_CALLBACK_SCHEME
            host = AUTH_CALLBACK_HOST
            sessionManager = MemorySessionManager()
            codeVerifierCache = verifiers
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth

    private val repository = SupabaseAuthRepository(auth)

    @Test
    fun `google signs in through Supabase, back to the callback, with PKCE and the account chooser`() {
        val url = Uri.parse(repository.providerSignInUrl(AuthProvider.GOOGLE))

        assertEquals("https://example.supabase.co/auth/v1/authorize", url.toString().substringBefore('?'))
        assertEquals("google", url.getQueryParameter("provider"))
        assertEquals(AUTH_CALLBACK_URL, url.getQueryParameter("redirect_to"))
        assertEquals("s256", url.getQueryParameter("code_challenge_method")?.lowercase())
        assertNotNull(url.getQueryParameter("code_challenge"))
        assertEquals("select_account", url.getQueryParameter("prompt"))
        assertNull("no extra scopes", url.getQueryParameter("scopes"))
        // The verifier stays on the device for the exchange; only its challenge leaves.
        val verifier = runBlocking { withTimeout(5_000) { while (verifiers.loadCodeVerifier() == null) delay(10); verifiers.loadCodeVerifier() } }
        assertFalse(verifier!! in url.toString())
    }

    @Test
    fun `facebook signs in the same way, without Google's prompt`() {
        val url = Uri.parse(repository.providerSignInUrl(AuthProvider.FACEBOOK))

        assertEquals("facebook", url.getQueryParameter("provider"))
        assertEquals(AUTH_CALLBACK_URL, url.getQueryParameter("redirect_to"))
        assertNotNull(url.getQueryParameter("code_challenge"))
        assertNull(url.getQueryParameter("prompt"))
        assertNull(url.getQueryParameter("scopes"))
    }
}
