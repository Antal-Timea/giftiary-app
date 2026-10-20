package app.giftiary.data.auth

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderPageResolverTest {

    @Test
    fun `the redirect is read, never followed`() = runBlocking {
        val requested = mutableListOf<String>()
        val resolver = ProviderPageResolver(
            MockEngine { request ->
                requested += request.url.toString()
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://www.facebook.com/dialog/oauth?state=s"))
            },
        )

        val page = resolver.providerPage("https://example.supabase.co/auth/v1/authorize?provider=facebook")

        assertEquals("https://www.facebook.com/dialog/oauth?state=s", page)
        assertEquals(listOf("https://example.supabase.co/auth/v1/authorize?provider=facebook"), requested)
    }
}
