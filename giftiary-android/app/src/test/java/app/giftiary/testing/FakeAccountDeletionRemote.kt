package app.giftiary.testing

import app.giftiary.data.remote.AccountDeletionRemote
import app.giftiary.data.remote.RemoteException
import app.giftiary.data.remote.RemoteFailure
import kotlinx.coroutines.CompletableDeferred
import java.util.Collections
import java.util.UUID

class FakeAccountDeletionRemote : AccountDeletionRemote {

    val calls: MutableList<UUID> = Collections.synchronizedList(mutableListOf())

    /** Thrown as a [RemoteException] by every call while set. */
    var failure: RemoteFailure? = null

    /** When set, each call waits for it, to observe what happens meanwhile. */
    var gate: CompletableDeferred<Unit>? = null

    /** Runs as each call reaches the server, e.g. to see what had happened by then. */
    var onCall: (UUID) -> Unit = {}

    override suspend fun deleteAccount(owner: UUID) {
        calls += owner
        onCall(owner)
        gate?.await()
        failure?.let { throw RemoteException(it) }
    }
}
