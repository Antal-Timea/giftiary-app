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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Opening the provider's page from the activity: a Custom Tab in the app's own task. */
@RunWith(RobolectricTestRunner::class)
class ProviderSignInLaunchTest {

    private val auth = FakeAuthRepository()
    private val providerSignIn = ProviderSignIn(auth, EnabledAuthProviders(setOf(AuthProvider.GOOGLE)))
    private val viewModel = ProviderSignInViewModel(providerSignIn)
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun `the page opens once in a Custom Tab of the app's task`() {
        viewModel.open(activity, AuthProvider.GOOGLE)
        viewModel.open(activity, AuthProvider.GOOGLE)

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(auth.providerSignInUrl(AuthProvider.GOOGLE), started.dataString)
        assertTrue("a Custom Tab", started.hasExtra("android.support.customtabs.extra.SESSION"))
        assertEquals("not in a task of its own", 0, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNull("opened once", shadowOf(activity).nextStartedActivity)
        assertEquals(ProviderSignInState.Launching(AuthProvider.GOOGLE), viewModel.state.value)
    }

    @Test
    fun `a provider this build does not offer opens nothing`() {
        viewModel.open(activity, AuthProvider.FACEBOOK)

        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(emptyList<AuthProvider>(), viewModel.providers.filter { it == AuthProvider.FACEBOOK })
    }

    @Test
    fun `without a browser the sign-in fails with that reason`() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).checkActivities(true)

        viewModel.open(activity, AuthProvider.GOOGLE)

        assertEquals(ProviderSignInState.Failed(AuthError.NO_BROWSER), viewModel.state.value)
    }
}
