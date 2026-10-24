package app.giftiary.testing

import app.giftiary.data.local.dao.SyncOperationDao
import java.util.UUID

/**
 * Arranges a blocked operation, as a sync failure or an earlier version of the app left it. Only
 * the operation's current mutation is blocked: returns 0, changing nothing, if [mutationId] is no
 * longer it. Only `blockedReason` changes, on the row as it is now.
 */
suspend fun SyncOperationDao.blockCurrentMutation(id: UUID, mutationId: UUID, reason: String): Int {
    val operation = findById(id)?.takeIf { it.mutationId == mutationId } ?: return 0
    update(operation.copy(blockedReason = reason))
    return 1
}
