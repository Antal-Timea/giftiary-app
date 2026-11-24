package app.giftiary.testing

import app.giftiary.data.remote.FeatureFlagRemote
import app.giftiary.data.remote.RemoteException
import app.giftiary.data.remote.RemoteFailure
import kotlinx.coroutines.CompletableDeferred
import java.util.Collections

/** The `feature_flags` table: the keys in [enabled] are on, every other key has no row and is off. */
class FakeFeatureFlagRemote(vararg on: String) : FeatureFlagRemote {

    val enabled: MutableSet<String> = Collections.synchronizedSet(on.toMutableSet())

    val reads: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Thrown as a [RemoteException] by every read while set. */
    var failure: RemoteFailure? = null

    /** While set, a read waits for it to complete, as a slow network would. */
    var answer: CompletableDeferred<Unit>? = null

    /**
     * While true, each read waits for its own entry in [held], which the test completes with
     * the flag's value, in any order.
     */
    var holdEach = false

    val held: MutableList<CompletableDeferred<Boolean>> = Collections.synchronizedList(mutableListOf())

    override suspend fun isEnabled(key: String): Boolean {
        reads += key
        if (holdEach) return CompletableDeferred<Boolean>().also { held += it }.await()
        answer?.await()
        failure?.let { throw RemoteException(it) }
        return key in enabled
    }
}
