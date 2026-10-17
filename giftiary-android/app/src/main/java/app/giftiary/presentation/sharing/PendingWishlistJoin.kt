package app.giftiary.presentation.sharing

import app.giftiary.domain.model.InviteToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PendingJoin {
    data class Invite(val token: InviteToken) : PendingJoin

    /** A join link without a usable token; the join screen says so instead of doing nothing. */
    data object InvalidLink : PendingJoin
}

/**
 * A join link that was opened but not acted on yet, e.g. while signed out. Held in process memory
 * only, deliberately: a killed process forgets it, and the invite has to be opened again rather
 * than its token ever being written anywhere.
 */
@Singleton
class PendingWishlistJoin @Inject constructor() {

    private val pending = MutableStateFlow<PendingJoin?>(null)

    val isPending: Flow<Boolean> = pending.map { it != null }.distinctUntilChanged()

    /** A newer link replaces one that is still waiting. */
    fun offer(join: PendingJoin) {
        pending.value = join
    }

    /** Hands the waiting join to exactly one taker and forgets it. */
    fun take(): PendingJoin? = pending.getAndUpdate { null }
}
