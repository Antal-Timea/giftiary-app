package app.gifty.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gifty.domain.model.AuthState
import app.gifty.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Decides between the auth flow and the app; nothing is shown as signed in or out while restoring. */
@HiltViewModel
class AuthGateViewModel @Inject constructor(
    private val repository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = repository.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Restoring)

    fun signOut() {
        viewModelScope.launch {
            try {
                repository.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Still signed in; the auth state shows it, so there is nothing else to report.
            }
        }
    }
}
