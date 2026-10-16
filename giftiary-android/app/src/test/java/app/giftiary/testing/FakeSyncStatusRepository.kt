package app.giftiary.testing

import app.giftiary.domain.model.SyncStatus
import app.giftiary.domain.repository.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class FakeSyncStatusRepository : SyncStatusRepository {

    val status = MutableStateFlow(SyncStatus(waitingCount = 0, attention = emptyList()))
    var statusFlowFailure: Throwable? = null
    var dismissFailure: Throwable? = null
    val dismissed = mutableListOf<UUID>()

    override fun observeSyncStatus(): Flow<SyncStatus> = statusFlowFailure?.let { failure -> flow { throw failure } } ?: status

    override suspend fun dismissConflict(id: UUID) {
        dismissFailure?.let { throw it }
        dismissed += id
    }
}
