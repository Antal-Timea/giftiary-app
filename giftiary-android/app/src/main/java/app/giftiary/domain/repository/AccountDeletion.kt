package app.giftiary.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Deleting the signed-in account: on the server first, then everything of it on this device.
 *
 * Once the server has deleted the account, the deletion is finished on this device even if the app
 * stops in between: the next start completes it before any account opens.
 */
interface AccountDeletion {

    val state: StateFlow<AccountDeletionState>

    /**
     * Deletes the signed-in account. Returns once the server has deleted it; the device is then
     * cleaned up as [state] shows. Calls made while one runs wait for that one.
     *
     * @throws AccountDeletionException if the server did not delete it; nothing changed locally.
     */
    suspend fun deleteAccount()

    /** Tries again to finish a deletion that [AccountDeletionState.FinishFailed]. */
    fun retry()

    /** The [AccountDeletionState.Deleted] message was shown. */
    fun onDeletedShown()
}

sealed interface AccountDeletionState {
    data object Idle : AccountDeletionState

    /** The account is being removed from this device; it stays closed meanwhile. */
    data object Finishing : AccountDeletionState

    /** Finishing could not complete yet; the account stays closed until it does. */
    data class FinishFailed(val failure: AccountDeletionFailure) : AccountDeletionState

    /** Done and signed out; shown once. */
    data object Deleted : AccountDeletionState
}

enum class AccountDeletionFailure {
    NETWORK,
    SERVER,
}

class AccountDeletionException(val failure: AccountDeletionFailure) : Exception(failure.name)
