package app.giftiary.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading a flag from the `feature_flags` table, as anon, before anyone signs in. */
class SupabaseFeatureFlagRemoteTest {

    private val publishableKey = "sb_publishable_test"
    private var status = HttpStatusCode.OK
    private var body = "[]"
    private val requests = mutableListOf<HttpRequestData>()

    private val engine = MockEngine { request ->
        requests += request
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private val remote = SupabaseFeatureFlagRemote(AccountBoundClients("https://project.supabase.test", publishableKey, engine))

    @Test
    fun `asks the table for that one key, with the publishable key and no account`() = runTest {
        body = """[{"enabled":true}]"""

        assertTrue(remote.isEnabled("facebook_login_enabled"))

        val request = requests.single()
        assertEquals("/rest/v1/feature_flags", request.url.encodedPath)
        assertEquals("eq.facebook_login_enabled", request.url.parameters["key"])
        assertEquals(publishableKey, request.headers["apikey"])
        // No session token: the Authorization header, if sent, carries the publishable key only.
        request.headers[HttpHeaders.Authorization]?.let { assertEquals("Bearer $publishableKey", it) }
    }

    @Test
    fun `a flag that is off, or has no row, reads as off`() = runTest {
        body = """[{"enabled":false}]"""
        assertFalse(remote.isEnabled("facebook_login_enabled"))

        body = "[]"
        assertFalse(remote.isEnabled("facebook_login_enabled"))
    }

    @Test
    fun `a server error or an unexpected body is a failure, not an answer`() = runTest {
        status = HttpStatusCode.InternalServerError
        body = """{"message":"boom"}"""
        assertThrows(RemoteException::class.java) { runBlocking { remote.isEnabled("facebook_login_enabled") } }

        status = HttpStatusCode.OK
        body = """[{"enabled":"yes"}]"""
        assertThrows(RemoteException::class.java) { runBlocking { remote.isEnabled("facebook_login_enabled") } }
    }
}
