package app.giftiary.presentation.sharing

import app.giftiary.data.user.ActiveOwner
import app.giftiary.domain.model.RecipientInvite
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.WishlistSharingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The invitations waiting for the signed-in account, shared by the Lists screen and the navigation
 * badge. They are asked for only when Lists is shown or the app opens for an account: no push, no
 * polling. What one account loaded is never shown to another.
 */
@Singleton
class InvitationInbox @Inject constructor(
    private val sharing: WishlistSharingRepository,
    private val activeOwner: ActiveOwner,
) {

    private data class Held(val owner: UUID?, val state: OnlineState<List<RecipientInvite>>)

    private val held = MutableStateFlow(Held(null, OnlineState()))
    private var generation = 0L

    val state: Flow<OnlineState<List<RecipientInvite>>> =
        combine(held, activeOwner.ownerId) { held, owner -> if (held.owner == owner) held.state else OnlineState() }

    /**
     * Asks the server again, keeping what is shown meanwhile. A failed refresh keeps the shown
     * invitations; only a first load shows the failure. Called from the main thread.
     */
    suspend fun refresh() {
        val owner = activeOwner.ownerId.value ?: return
        val startedIn = ++generation
        held.update {
            if (it.owner == owner) it.copy(state = it.state.copy(isRefreshing = true)) else Held(owner, OnlineState(isRefreshing = true))
        }
        val content = try {
            OnlineContent.Loaded(sharing.myInvites())
        } catch (e: SharingException) {
            OnlineContent.Failed(e.failure)
        } finally {
            // Cancelled or not, a load replaced by none must not stay "refreshing".
            if (startedIn == generation) held.update { it.copy(state = it.state.copy(isRefreshing = false)) }
        }
        if (startedIn != generation) return
        held.update { current ->
            when {
                current.owner != owner -> current
                content is OnlineContent.Failed && current.state.content is OnlineContent.Loaded -> current
                else -> current.copy(state = OnlineState(content))
            }
        }
    }

    /** Takes an answered or vanished invitation off at once; the next refresh confirms it. */
    fun remove(inviteId: UUID) {
        held.update { current ->
            val loaded = current.state.content as? OnlineContent.Loaded ?: return@update current
            current.copy(state = current.state.copy(content = OnlineContent.Loaded(loaded.value.filterNot { it.id == inviteId })))
        }
    }
}
