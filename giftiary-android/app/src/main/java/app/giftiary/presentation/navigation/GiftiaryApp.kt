package app.giftiary.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.data.auth.RecoveryState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.domain.repository.AccountDeletionState
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.presentation.auth.AccountDeletedScreen
import app.giftiary.presentation.auth.AuthGateViewModel
import app.giftiary.presentation.auth.FinishingAccountDeletionScreen
import app.giftiary.presentation.auth.PasswordChangedScreen
import app.giftiary.presentation.auth.RecoveryScreen
import app.giftiary.presentation.auth.StartupScreen
import app.giftiary.presentation.onboarding.OnboardingScreen

/**
 * The auth gate. The app is shown only for an account whose data is open; while a saved session
 * is checked, the startup screen covers it. A new installation's signed-out side starts with the
 * introduction.
 */
@Composable
fun GiftiaryApp(modifier: Modifier = Modifier, viewModel: AuthGateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recovery by viewModel.recoveryState.collectAsStateWithLifecycle()
    val passwordChanged by viewModel.passwordChanged.collectAsStateWithLifecycle()
    val deletion by viewModel.deletionState.collectAsStateWithLifecycle()
    val showOnboarding by viewModel.showOnboarding.collectAsStateWithLifecycle()
    when (val screen = gateScreen(state, recovery, passwordChanged, deletion, showOnboarding)) {
        GateScreen.Recovery -> RecoveryScreen(viewModel = hiltViewModel(), modifier = modifier)
        GateScreen.PasswordChanged -> PasswordChangedScreen(onContinue = viewModel::onPasswordChangedShown, modifier = modifier)
        is GateScreen.FinishingDeletion -> FinishingAccountDeletionScreen(
            failure = screen.failure,
            onRetry = if (screen.failure != null) viewModel::retryDeletion else ({}),
            modifier = modifier,
        )
        GateScreen.AccountDeleted -> AccountDeletedScreen(onContinue = viewModel::onAccountDeletedShown, modifier = modifier)
        GateScreen.Startup -> StartupScreen(modifier)
        GateScreen.Onboarding -> OnboardingScreen(onDone = viewModel::onOnboardingDone, modifier = modifier)
        GateScreen.SignIn -> AuthNavHost(modifier)
        // One navigation graph per account: switching accounts, even within a single frame,
        // never carries back stack, screen state or half-typed input over to the next account.
        is GateScreen.App -> key(screen.user.id) {
            GiftiaryNavHost(accountEmail = screen.user.email, onSignOut = viewModel::signOut, modifier = modifier)
        }
    }
}

/** What the gate shows, most urgent first. Only [App] holds the signed-in navigation graph. */
internal sealed interface GateScreen {
    data object Recovery : GateScreen
    data object PasswordChanged : GateScreen
    data class FinishingDeletion(val failure: AccountDeletionFailure?) : GateScreen
    data object AccountDeleted : GateScreen
    data object Startup : GateScreen
    data object Onboarding : GateScreen
    data object SignIn : GateScreen
    data class App(val user: AuthUser) : GateScreen
}

/**
 * A recovery link covers everything, then a deletion being finished; the introduction is only ever
 * the signed-out side's start, so recovery, deletion and provider or confirmation links (which open
 * an account directly) never wait behind it.
 */
internal fun gateScreen(
    session: OwnerSessionState,
    recovery: RecoveryState,
    passwordChanged: Boolean,
    deletion: AccountDeletionState,
    showOnboarding: Boolean,
): GateScreen = when {
    recovery != RecoveryState.Idle -> GateScreen.Recovery
    passwordChanged -> GateScreen.PasswordChanged
    deletion == AccountDeletionState.Finishing -> GateScreen.FinishingDeletion(failure = null)
    deletion is AccountDeletionState.FinishFailed -> GateScreen.FinishingDeletion(deletion.failure)
    deletion == AccountDeletionState.Deleted -> GateScreen.AccountDeleted
    else -> when (session) {
        OwnerSessionState.Restoring -> GateScreen.Startup
        OwnerSessionState.SignedOut -> if (showOnboarding) GateScreen.Onboarding else GateScreen.SignIn
        is OwnerSessionState.Ready -> GateScreen.App(session.user)
    }
}
