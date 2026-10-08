package app.gifty.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.domain.repository.OwnerSessionState
import app.gifty.presentation.auth.AccountPreparationFailedScreen
import app.gifty.presentation.auth.AuthGateViewModel
import app.gifty.presentation.auth.StartupScreen

/**
 * The auth gate. The app is shown only for an account whose data is ready; while a saved session
 * is checked or an account is prepared, the startup screen covers it.
 */
@Composable
fun GiftyApp(modifier: Modifier = Modifier, viewModel: AuthGateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        OwnerSessionState.Restoring -> StartupScreen(modifier)
        OwnerSessionState.SignedOut -> AuthNavHost(modifier)
        is OwnerSessionState.Failed -> AccountPreparationFailedScreen(
            onRetry = viewModel::retry,
            onSignOut = viewModel::signOut,
            modifier = modifier,
        )
        // One navigation graph per account: switching accounts, even within a single frame,
        // never carries back stack, screen state or half-typed input over to the next account.
        is OwnerSessionState.Ready -> key(current.user.id) {
            GiftyNavHost(onSignOut = viewModel::signOut, modifier = modifier)
        }
    }
}
