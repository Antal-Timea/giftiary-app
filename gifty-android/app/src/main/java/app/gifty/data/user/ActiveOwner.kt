package app.gifty.data.user

import app.gifty.domain.repository.NoActiveOwnerException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * The account whose rows the app may currently read and write. Null means none: owner-scoped
 * reads show nothing and writes are refused. Only [OwnerSession] changes it.
 */
interface ActiveOwner {

    val ownerId: StateFlow<UUID?>

    /** Resolve once per write, before its transaction, and use that id throughout. */
    fun requireOwnerId(): UUID = ownerId.value ?: throw NoActiveOwnerException()
}

/**
 * Follows the active owner: [query] runs for the current owner and is replaced when the owner
 * changes, so no collector ever keeps showing a previous account's rows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> ActiveOwner.scoped(whenNoOwner: T, query: (owner: UUID) -> Flow<T>): Flow<T> =
    ownerId.flatMapLatest { owner -> if (owner == null) flowOf(whenNoOwner) else query(owner) }
