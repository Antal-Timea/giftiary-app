package app.giftiary.testing

import app.giftiary.data.remote.GiftPhotoRemote
import app.giftiary.data.remote.RemoteAccount
import app.giftiary.data.remote.RemoteException
import app.giftiary.data.remote.RemoteFailure
import java.util.Collections

/** Storage in memory: objects by path, every call recorded in order. */
class FakeGiftPhotoRemote : GiftPhotoRemote {

    val objects: MutableMap<String, ByteArray> = Collections.synchronizedMap(mutableMapOf())
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Thrown as a [RemoteException] by every call while set. */
    var failure: RemoteFailure? = null

    override suspend fun upload(account: RemoteAccount, path: String, bytes: ByteArray) {
        calls += "upload $path"
        failure?.let { throw RemoteException(it) }
        objects[path] = bytes
    }

    override suspend fun download(account: RemoteAccount, path: String): ByteArray {
        calls += "download $path"
        failure?.let { throw RemoteException(it) }
        return objects[path] ?: throw RemoteException(RemoteFailure.SERVER)
    }

    override suspend fun delete(account: RemoteAccount, paths: List<String>) {
        calls += "delete ${paths.sorted().joinToString()}"
        failure?.let { throw RemoteException(it) }
        paths.forEach(objects::remove)
    }

    override suspend fun deleteAll(account: RemoteAccount) {
        calls += "deleteAll ${account.ownerUserId}"
        failure?.let { throw RemoteException(it) }
        objects.keys.removeAll { it.startsWith("${account.ownerUserId}/") }
    }
}
