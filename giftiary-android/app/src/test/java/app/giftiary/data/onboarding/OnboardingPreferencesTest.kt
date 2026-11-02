package app.giftiary.data.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Fresh install or update is read from the package itself, the one record older versions left. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingPreferencesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun installed(firstInstall: Long, lastUpdate: Long) {
        shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName).apply {
            firstInstallTime = firstInstall
            lastUpdateTime = lastUpdate
        }
    }

    @Test
    fun `a never-updated installation owes the introduction until it is completed`() {
        installed(firstInstall = 1_000, lastUpdate = 1_000)

        val onboarding = OnboardingPreferences(context)
        assertTrue(onboarding.pending.value)

        onboarding.complete()
        assertFalse(onboarding.pending.value)
        assertFalse(OnboardingPreferences(context).pending.value)
    }

    @Test
    fun `an installation updated from an earlier version owes nothing`() {
        installed(firstInstall = 1_000, lastUpdate = 2_000)

        assertFalse(OnboardingPreferences(context).pending.value)
    }
}
