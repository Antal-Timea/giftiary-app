package app.giftiary.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giftiary.domain.repository.AccountDeletion
import app.giftiary.domain.repository.AccountDeletionException
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The account part of Settings: who is signed in, and deleting the account. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    accountSession: AccountSession,
    private val accountDeletion: AccountDeletion,
) : ViewModel() {

    /** Only for the user themself; never shown to anyone else and never used as a name. */
    val email: String? = (accountSession.state.value as? OwnerSessionState.Ready)?.user?.email

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun onDeleteAccount() {
        _state.update { it.copy(deleteDialog = it.deleteDialog ?: DeleteAccountDialog()) }
    }

    /** Ignored while the deletion runs: its outcome is shown in the dialog that started it. */
    fun onDeleteAccountDismiss() {
        _state.update { if (it.deleteDialog?.deleting == true) it else it.copy(deleteDialog = null) }
    }

    fun onDeleteAccountConfirm() {
        val dialog = _state.value.deleteDialog ?: return
        if (dialog.deleting) return
        _state.update { it.copy(deleteDialog = DeleteAccountDialog(deleting = true)) }
        viewModelScope.launch {
            try {
                // On success the account closes and the app leaves this screen; the dialog stays
                // busy until then.
                accountDeletion.deleteAccount()
            } catch (e: AccountDeletionException) {
                _state.update { it.copy(deleteDialog = DeleteAccountDialog(failure = e.failure)) }
            }
        }
    }
}

data class SettingsUiState(
    /** Null while the deletion confirmation is not shown. */
    val deleteDialog: DeleteAccountDialog? = null,
)

/** [failure] is why the last attempt did not delete the account; confirming again retries. */
data class DeleteAccountDialog(
    val deleting: Boolean = false,
    val failure: AccountDeletionFailure? = null,
)
