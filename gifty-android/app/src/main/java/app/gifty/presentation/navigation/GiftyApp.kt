package app.gifty.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.domain.model.AuthState
import app.gifty.presentation.auth.AuthGateViewModel
import app.gifty.presentation.auth.StartupScreen

/**
 * The auth gate. Neither side is shown until a saved session has been checked, so a signed-in
 * user never sees the sign-in screen flash by on launch.
 */
@Composable
fun GiftyApp(modifier: Modifier = Modifier, viewModel: AuthGateViewModel = hiltViewModel()) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    when (authState) {
        AuthState.Restoring -> StartupScreen(modifier)
        AuthState.SignedOut -> AuthNavHost(modifier)
        is AuthState.SignedIn -> GiftyNavHost(onSignOut = viewModel::signOut, modifier = modifier)
    }
}
