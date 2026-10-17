package app.giftiary.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.giftiary.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which links the system hands to the app as an auth callback, resolved against the merged
 * manifest. Only the URL the Auth settings allow may open the app; the SDK is configured with the
 * same constants.
 */
@RunWith(RobolectricTestRunner::class)
class AuthCallbackLinkTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the callback URL is the Giftiary one`() {
        assertEquals("giftiary://auth/callback", AUTH_CALLBACK_URL)
    }

    @Test
    fun `a confirmation link to the callback URL opens the app`() {
        assertEquals(listOf(MainActivity::class.java.name), handlersOf(AUTH_CALLBACK_URL + "?code=one-time"))
    }

    @Test
    fun `the old Gifty scheme and other paths are not auth callbacks of the app`() {
        listOf(
            "gifty://auth/callback?code=one-time",
            "giftiary://auth/elsewhere",
            "giftiary://other/callback",
        ).forEach { link ->
            assertTrue(link, handlersOf(link).isEmpty())
        }
    }

    private fun handlersOf(link: String): List<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
    }
}
