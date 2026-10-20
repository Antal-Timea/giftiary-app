package app.giftiary.data.auth

import android.net.Uri
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.AuthProvider
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * The provider page found through the real supabase-kt [Auth], configured like the app's, with a
 * mock Supabase answering the authorize request.
 */
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

    private val requests = mutableListOf<Uri>()

    private fun repository(answer: () -> Pair<HttpStatusCode, String?>) = SupabaseAuthRepository(
        auth,
        ProviderPageResolver(
            MockEngine { request ->
                requests += Uri.parse(request.url.toString())
                val (status, location) = answer()
                respond("", status, location?.let { headersOf(HttpHeaders.Location, it) } ?: headersOf())
            },
        ),
    )

    private val facebookPage = "https://www.facebook.com/dialog/oauth?client_id=1&state=s"

    @Test
    fun `google opens its own page, found through Supabase with PKCE and the account chooser`() = runBlocking {
        val page = repository { HttpStatusCode.Found to "https://accounts.google.com/o/oauth2/v2/auth?state=s" }
            .providerSignInUrl(AuthProvider.GOOGLE)

        assertEquals("https://accounts.google.com/o/oauth2/v2/auth?state=s", page)
        val authorize = requests.single()
        assertEquals("https://example.supabase.co/auth/v1/authorize", authorize.toString().substringBefore('?'))
        assertEquals("google", authorize.getQueryParameter("provider"))
        assertEquals(AUTH_CALLBACK_URL, authorize.getQueryParameter("redirect_to"))
        assertEquals("s256", authorize.getQueryParameter("code_challenge_method")?.lowercase())
        assertNotNull(authorize.getQueryParameter("code_challenge"))
        assertEquals("select_account", authorize.getQueryParameter("prompt"))
        assertNull("no extra scopes", authorize.getQueryParameter("scopes"))
        // The verifier stays on the device for the exchange; only its challenge leaves.
        val verifier = withTimeout(5_000) { while (verifiers.loadCodeVerifier() == null) delay(10); verifiers.loadCodeVerifier() }
        assertFalse(verifier!! in authorize.toString())
    }

    @Test
    fun `facebook opens facebook's page directly, so no app that claims facebook dot com takes it over`() = runBlocking {
        val page = repository { HttpStatusCode.Found to facebookPage }.providerSignInUrl(AuthProvider.FACEBOOK)

        assertEquals(facebookPage, page)
        val authorize = requests.single()
        assertEquals("facebook", authorize.getQueryParameter("provider"))
        assertEquals(AUTH_CALLBACK_URL, authorize.getQueryParameter("redirect_to"))
        assertNotNull(authorize.getQueryParameter("code_challenge"))
        assertNull(authorize.getQueryParameter("prompt"))
        assertNull(authorize.getQueryParameter("scopes"))
    }

    @Test
    fun `an unreachable Supabase is a network error`() = runBlocking {
        val error = runCatching { repository { throw IOException("offline") }.providerSignInUrl(AuthProvider.FACEBOOK) }

        assertEquals(AuthError.NETWORK_UNAVAILABLE, (error.exceptionOrNull() as AuthException).error)
    }

    @Test
    fun `no redirect to a provider page is an error, never a page to open`() = runBlocking {
        listOf(
            // E.g. the provider is not enabled on the project.
            HttpStatusCode.BadRequest to null,
            HttpStatusCode.OK to null,
            // A Location only counts on a redirect.
            HttpStatusCode.OK to "https://www.facebook.com/dialog/oauth",
            HttpStatusCode.Found to null,
            HttpStatusCode.Found to "http://www.facebook.com/dialog/oauth",
            HttpStatusCode.Found to "giftiary://auth/callback?code=x",
        ).forEach { answer ->
            val error = runCatching { repository { answer }.providerSignInUrl(AuthProvider.FACEBOOK) }.exceptionOrNull()
            assertTrue("$answer", error is AuthException && error.error == AuthError.UNKNOWN)
        }
    }
}
