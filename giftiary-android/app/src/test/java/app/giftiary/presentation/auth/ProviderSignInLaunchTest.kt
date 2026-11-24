package app.giftiary.presentation.auth

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import app.giftiary.data.auth.EnabledAuthProviders
import app.giftiary.data.auth.ProviderSignIn
import app.giftiary.data.auth.ProviderSignInState
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthProvider
import app.giftiary.testing.FakeAuthRepository
import app.giftiary.testing.FakeFeatureFlagRemote
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
    private val providerSignIn = ProviderSignIn(auth, EnabledAuthProviders(setOf(AuthProvider.FACEBOOK)), FakeFeatureFlagRemote(ProviderSignIn.FACEBOOK_LOGIN_FLAG))
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

    private val packageManager = shadowOf(activity.packageManager)

    // An app that opens any web link, optionally with Custom Tabs.
    private fun installBrowser(packageName: String, customTabs: Boolean) {
        packageManager.addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, "https://".toUri()).addCategory(Intent.CATEGORY_BROWSABLE),
            ResolveInfo().apply { activityInfo = ActivityInfo().apply { this.packageName = packageName; name = "$packageName.Browser" } },
        )
        if (customTabs) {
            packageManager.addResolveInfoForIntent(
                Intent("android.support.customtabs.action.CustomTabsService").setPackage(packageName),
                ResolveInfo().apply { serviceInfo = ServiceInfo().apply { this.packageName = packageName; name = "$packageName.Tabs" } },
            )
        }
    }

    @Test
    fun `the page opens as the first page of a Custom Tab of a browser, in the app's task`() {
        installBrowser("com.example.plainbrowser", customTabs = false)
        installBrowser("com.android.chrome", customTabs = true)

        assertTrue(openProviderPage(activity, page))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals(page, started.dataString)
        // Pinned to the browser, so the system cannot hand facebook.com to the Facebook app.
        assertEquals("com.android.chrome", started.`package`)
        assertTrue("a Custom Tab", started.hasExtra("android.support.customtabs.extra.SESSION"))
        assertEquals("not in a task of its own", 0, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun `without a Custom Tabs browser the page still opens in a browser, never left to the system`() {
        installBrowser("com.example.plainbrowser", customTabs = false)

        assertTrue(openProviderPage(activity, page))

        assertEquals("com.example.plainbrowser", shadowOf(activity).nextStartedActivity.`package`)
    }

    @Test
    fun `without a browser nothing opens and the sign-in fails with that reason`() {
        viewModel.begin(AuthProvider.FACEBOOK)

        assertFalse(openProviderPage(activity, viewModel.takePageToOpen()!!))
        assertNull(shadowOf(activity).nextStartedActivity)
        viewModel.onBrowserMissing()

        assertEquals(ProviderSignInState.Failed(AuthError.NO_BROWSER), viewModel.state.value)
    }
}
