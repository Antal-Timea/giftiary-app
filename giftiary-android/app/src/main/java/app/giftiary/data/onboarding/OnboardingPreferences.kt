package app.giftiary.data.onboarding

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this device still owes its user the introduction. It is for the device, not an account:
 * signing out or deleting the account does not bring it back, and it has nothing to do with
 * whether anyone is signed in.
 *
 * Before onboarding existed nothing was recorded, so a missing record alone cannot tell a new user
 * from one who updated the app. Only an installation that has never been updated
 * ([isFreshInstall]) counts as new; an update that brings onboarding never shows it to someone who
 * already used Giftiary. A fresh install updated before the introduction was seen skips it too,
 * which errs on the side of never blocking anyone.
 */
@Singleton
class OnboardingPreferences internal constructor(
    context: Context,
    isFreshInstall: Boolean,
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, isFreshInstall(context))

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _pending = MutableStateFlow(isFreshInstall && !preferences.getBoolean(COMPLETED, false))
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun complete() {
        preferences.edit { putBoolean(COMPLETED, true) }
        _pending.value = false
    }

    private companion object {
        const val PREFERENCES_NAME = "giftiary_onboarding"
        const val COMPLETED = "completed"
    }
}

private fun isFreshInstall(context: Context): Boolean {
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() ?: return false
    return info.firstInstallTime == info.lastUpdateTime
}
