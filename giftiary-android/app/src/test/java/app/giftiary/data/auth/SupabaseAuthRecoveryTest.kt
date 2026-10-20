package app.giftiary.data.auth

import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.testing.noProviderPages
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID

/** Password recovery through the real supabase-kt [Auth] against a fake Auth server. */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class SupabaseAuthRecoveryTest {

    private val userId = UUID.randomUUID()
    private val requests = mutableListOf<HttpRequestData>()
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("{}") }
    private val engine = MockEngine { request ->
        requests += request
        answer(request)
    }

    private val auth: Auth = createSupabaseClient("https://example.supabase.co", "publishable-key") {
        httpEngine = engine
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
            flowType = FlowType.PKCE
            scheme = AUTH_CALLBACK_SCHEME
            host = AUTH_CALLBACK_HOST
            defaultRedirectUrl = AUTH_CALLBACK_URL
        }
    }.auth
    private val repository = SupabaseAuthRepository(auth, noProviderPages())

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun `a reset email is requested for the recovery path, not the confirmation callback`() = runTest {
        repository.requestPasswordReset("anna@example.com")

        val request = requests.single()
        assertTrue(request.url.encodedPath.endsWith("/auth/v1/recover"))
        assertEquals(AUTH_RECOVERY_URL, request.url.parameters["redirect_to"])
        assertEquals("anna@example.com", body(request)["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a recovery code is exchanged for a saved session`() = runTest {
        repository.requestPasswordReset("anna@example.com")
        answer = { respondJson(sessionJson()) }

        repository.exchangeRecoveryCode("one-time-code")

        val exchange = requests.last()
        assertTrue(exchange.url.encodedPath.endsWith("/auth/v1/token"))
        assertEquals("pkce", exchange.url.parameters["grant_type"])
        assertEquals("one-time-code", body(exchange)["auth_code"]?.jsonPrimitive?.content)
        assertNotNull(auth.currentSessionOrNull())
        assertNotNull("saved like any session", auth.sessionManager.loadSessionOrNull())
    }

    @Test
    fun `a used, expired or foreign recovery code is an invalid link`() = runTest {
        listOf("flow_state_not_found", "flow_state_expired", "bad_code_verifier", "otp_expired").forEach { code ->
            repository.requestPasswordReset("anna@example.com")
            answer = { respondError(code, HttpStatusCode.BadRequest) }

            assertError(AuthError.LINK_INVALID) { repository.exchangeRecoveryCode("one-time-code") }
            answer = { respondJson("{}") }
        }
    }

    @Test
    fun `a recovery code that cannot be sent is a network failure`() = runTest {
        repository.requestPasswordReset("anna@example.com")
        answer = { throw IOException("offline") }

        assertError(AuthError.NETWORK_UNAVAILABLE) { repository.exchangeRecoveryCode("one-time-code") }
    }

    @Test
    fun `the new password is set for the signed-in account`() = runTest {
        auth.importSession(session(), autoRefresh = false, source = SessionSource.Storage)
        answer = { respondJson("""{"id":"$userId","aud":"authenticated"}""") }

        repository.setNewPassword("new-password-1")

        val request = requests.single()
        assertTrue(request.url.encodedPath.endsWith("/auth/v1/user"))
        assertEquals("PUT", request.method.value)
        assertEquals("new-password-1", body(request)["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the same or a weak password is refused with its reason`() = runTest {
        auth.importSession(session(), autoRefresh = false, source = SessionSource.Storage)

        answer = { respondError("same_password", HttpStatusCode.UnprocessableEntity) }
        assertError(AuthError.SAME_PASSWORD) { repository.setNewPassword("new-password-1") }

        answer = { respondError("weak_password", HttpStatusCode.UnprocessableEntity) }
        assertError(AuthError.WEAK_PASSWORD) { repository.setNewPassword("new-password-1") }

        answer = { respondError("session_not_found", HttpStatusCode.Forbidden) }
        assertError(AuthError.LINK_INVALID) { repository.setNewPassword("new-password-1") }
    }

    @Test
    fun `a failure keeps the code and the password out of its message`() = runTest {
        repository.requestPasswordReset("anna@example.com")
        answer = { respondError("flow_state_not_found", HttpStatusCode.BadRequest) }

        val failure = runCatching { repository.exchangeRecoveryCode("secret-code") }.exceptionOrNull() as AuthException

        assertFalse(failure.message.orEmpty().contains("secret-code"))
    }

    private fun session() = UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = userId.toString(), email = "anna@example.com"),
    )

    private fun sessionJson() =
        """{"access_token":"access","refresh_token":"refresh","expires_in":3600,"token_type":"bearer",""" +
            """"user":{"id":"$userId","aud":"authenticated","email":"anna@example.com"}}"""

    private fun body(request: HttpRequestData) =
        Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    private suspend fun assertError(expected: AuthError, block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("expected $expected but got $failure", failure is AuthException)
        assertEquals(expected, (failure as AuthException).error)
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(body, HttpStatusCode.OK, JSON_HEADERS)

    private fun MockRequestHandleScope.respondError(code: String, status: HttpStatusCode) =
        respond("""{"code":${status.value},"error_code":"$code","msg":"refused"}""", status, JSON_HEADERS)

    private companion object {
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
