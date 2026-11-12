package app.giftiary.testing

import app.giftiary.data.remote.DeviceProofRemote
import app.giftiary.data.remote.RemoteException
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.data.user.DeviceProofs
import java.util.Collections
import java.util.UUID

/**
 * The server's device proofs: registered hashes belong to their account until it is [deleted], as
 * the cascade from auth.users does, and the question answers from them alone.
 */
class FakeDeviceProofRemote : DeviceProofRemote {

    private val registered: MutableMap<String, UUID> = Collections.synchronizedMap(mutableMapOf())

    val registrations: MutableList<UUID> = Collections.synchronizedList(mutableListOf())
    val questions: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Thrown as a [RemoteException] by every registration while set. */
    var registerFailure: RemoteFailure? = null

    /** Thrown as a [RemoteException] by every question while set. */
    var questionFailure: RemoteFailure? = null

    /** The account is gone, and with it every proof it registered. */
    fun delete(owner: UUID) {
        synchronized(registered) { registered.values.removeAll { it == owner } }
    }

    override suspend fun register(owner: UUID, proofHash: String) {
        registrations += owner
        registerFailure?.let { throw RemoteException(it) }
        registered[proofHash] = owner
    }

    override suspend fun isLive(secret: String): Boolean {
        questions += secret
        questionFailure?.let { throw RemoteException(it) }
        return DeviceProofs.hashOf(secret) in registered
    }
}
