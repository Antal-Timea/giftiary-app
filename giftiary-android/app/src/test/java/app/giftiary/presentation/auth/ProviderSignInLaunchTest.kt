package app.giftiary.presentation.auth

import android.app.Application
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.auth.EnabledAuthProviders
import app.giftiary.data.auth.ProviderSignIn
import app.giftiary.data.auth.ProviderSignInState
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.testing.FakeAuthRepository
import app.giftiary.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** From a tap to the provider's page: found once, handed out once, opened in a Custom Tab of the app's task. */
@RunWith(RobolectricTestRunner::class)
class ProviderSignInLaunchTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val providerSignIn = ProviderSignIn(auth, EnabledAuthProviders(setOf(AuthProvider.FACEBOOK)))
    private val viewModel = ProviderSignInViewModel(providerSignIn)
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    private val page = "https://www.facebook.com/dialog/oauth?state=s"

    @Test
    fun `a tap finds the page once and hands it out once`() {
        viewModel.begin(AuthProvider.FACEBOOK)
        viewModel.begin(AuthProvider.FACEBOOK)

        assertEquals(listOf(AuthProvider.FACEBOOK), auth.providerUrlCalls)
        val url = viewModel.takePageToOpen()
        assertEquals("https://auth.example.test/authorize?provider=facebook", url)
        assertNull("handed out once", viewModel.takePageToOpen())
        assertEquals(ProviderSignInState.Launching(AuthProvider.FACEBOOK), viewModel.state.value)
    }

    @Test
    fun `a provider this build does not offer is never looked up`() {
        viewModel.begin(AuthProvider.GOOGLE)

        assertTrue(auth.providerUrlCalls.isEmpty())
        assertNull(viewModel.takePageToOpen())
    }

    @Test
    fun `the page opens as the first page of a Custom Tab in the app's task`() {
        assertTrue(openProviderPage(activity, page))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(page, started.dataString)
        assertTrue("a Custom Tab", started.hasExtra("android.support.customtabs.extra.SESSION"))
        assertEquals("not in a task of its own", 0, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun `without a browser nothing opens and the sign-in fails with that reason`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).checkActivities(true)
        viewModel.begin(AuthProvider.FACEBOOK)

        assertFalse(openProviderPage(activity, viewModel.takePageToOpen()!!))
        viewModel.onBrowserMissing()

        assertEquals(ProviderSignInState.Failed(AuthError.NO_BROWSER), viewModel.state.value)
    }
}
