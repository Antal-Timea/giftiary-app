package app.giftiary.testing

import app.giftiary.data.auth.ProviderPageResolver
import io.ktor.client.engine.mock.MockEngine

/** For repository tests that never sign in with a provider: any request fails the test. */
fun noProviderPages() = ProviderPageResolver(MockEngine { error("No provider page is expected in this test") })
