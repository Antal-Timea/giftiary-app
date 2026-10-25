package app.giftiary.testing

import app.giftiary.domain.repository.AccountDeletion
import app.giftiary.domain.repository.AccountDeletionException
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.domain.repository.AccountDeletionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAccountDeletion : AccountDeletion {

    override val state = MutableStateFlow<AccountDeletionState>(AccountDeletionState.Idle)

    var deleteCalls = 0

    /** Thrown as an [AccountDeletionException] by the next call. */
    var failure: AccountDeletionFailure? = null

    /** When set, deleting waits for it, to observe the running state. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun deleteAccount() {
        deleteCalls++
        gate?.await()
        failure?.let {
            failure = null
            throw AccountDeletionException(it)
        }
    }

    var retryCalls = 0

    override fun retry() {
        retryCalls++
    }

    override fun onDeletedShown() {
        state.value = AccountDeletionState.Idle
    }
}
