package app.giftiary.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.data.auth.RecoveryState
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.presentation.auth.AuthGateViewModel
import app.giftiary.presentation.auth.PasswordChangedScreen
import app.giftiary.presentation.auth.RecoveryScreen
import app.giftiary.presentation.auth.StartupScreen

/**
 * The auth gate. The app is shown only for an account whose data is open; while a saved session
 * is checked, the startup screen covers it.
 */
@Composable
fun GiftiaryApp(modifier: Modifier = Modifier, viewModel: AuthGateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recovery by viewModel.recoveryState.collectAsStateWithLifecycle()
    val passwordChanged by viewModel.passwordChanged.collectAsStateWithLifecycle()
    if (recovery != RecoveryState.Idle) {
        RecoveryScreen(viewModel = hiltViewModel(), modifier = modifier)
        return
    }
    if (passwordChanged) {
        PasswordChangedScreen(onContinue = viewModel::onPasswordChangedShown, modifier = modifier)
        return
    }
    when (val current = state) {
        OwnerSessionState.Restoring -> StartupScreen(modifier)
        OwnerSessionState.SignedOut -> AuthNavHost(modifier)
        // One navigation graph per account: switching accounts, even within a single frame,
        // never carries back stack, screen state or half-typed input over to the next account.
        is OwnerSessionState.Ready -> key(current.user.id) {
            GiftiaryNavHost(onSignOut = viewModel::signOut, modifier = modifier)
        }
    }
}
