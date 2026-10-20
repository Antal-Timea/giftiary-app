package app.giftiary.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * Finds the provider's own sign-in page behind a Supabase authorize URL, without following it.
 *
 * Supabase answers the authorize URL with a redirect to the provider. When a Custom Tab follows
 * that redirect itself, Chrome hands the provider's page to an app that claims its domain, e.g.
 * the Facebook app for facebook.com, which drops the sign-in. Opened as the tab's first page, the
 * provider's page stays in the tab. The request is the one the browser would make; it starts
 * the same sign-in, PKCE included.
 */
class ProviderPageResolver(engine: HttpClientEngine) {

    private val client = HttpClient(engine) {
        followRedirects = false
        expectSuccess = false
    }

    /** @throws java.io.IOException when Supabase cannot be reached. */
    suspend fun providerPage(authorizeUrl: String): String {
        val response = client.get(authorizeUrl)
        val location = response.headers[HttpHeaders.Location]
        check(response.status.value in 300..399 && location != null) {
            "Supabase did not redirect to the provider (HTTP ${response.status.value})"
        }
        // Only a provider page over HTTPS is opened; anything else is not a sign-in to show.
        check(Url(location).protocol == URLProtocol.HTTPS) { "The provider page is not HTTPS" }
        return location
    }
}
