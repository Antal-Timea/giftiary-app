package app.giftiary.presentation.sharing

import app.giftiary.domain.model.ActiveInvite
import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.model.WishlistMember
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.testing.FakeWishlistRepository
import app.giftiary.testing.FakeWishlistSharingRepository
import app.giftiary.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SharingViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sharing = FakeWishlistSharingRepository()
    private val pending = PendingWishlistJoin()
    private val listId = UUID.randomUUID()
    private val token = InviteToken.of("A".repeat(43))!!
    private val now = Instant.parse("2026-10-17T10:00:00Z")
    private val invite = ActiveInvite(UUID.randomUUID(), now, now.plusSeconds(604_800))
    private val member = WishlistMember(UUID.randomUUID(), null, now)

    private fun accessViewModel() = WishlistAccessViewModel(listId, sharing, FakeWishlistRepository())

    // ---- owner access ----

    @Test
    fun `the access screen loads invites and members`() = runTest {
        sharing.access = WishlistAccess(listOf(invite), listOf(member))

        val viewModel = accessViewModel()

        assertEquals(AccessContent.Loaded(sharing.access), viewModel.state.value.content)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `a first load that fails shows the failure and can be retried`() = runTest {
        sharing.failNext += SharingFailure.NETWORK

        val viewModel = accessViewModel()
        assertEquals(AccessContent.Failed(SharingFailure.NETWORK), viewModel.state.value.content)

        viewModel.load()
        assertEquals(AccessContent.Loaded(sharing.access), viewModel.state.value.content)
    }

    @Test
    fun `a failed refresh keeps the content and reports once`() = runTest {
        val viewModel = accessViewModel()
        sharing.failNext += SharingFailure.SERVER

        viewModel.load()

        assertTrue(viewModel.state.value.content is AccessContent.Loaded)
        assertEquals(SharingFailure.SERVER, viewModel.state.value.message)
        viewModel.onMessageShown()
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `a created invite is kept for sharing and the access list refreshed`() = runTest {
        val created = CreatedInvite(invite.id, token, invite.expiresAt)
        sharing.createdInvite = created
        val viewModel = accessViewModel()
        sharing.access = WishlistAccess(listOf(invite), emptyList())

        viewModel.onCreateInvite()

        assertEquals(created, viewModel.state.value.newInvite)
        assertEquals(AccessContent.Loaded(sharing.access), viewModel.state.value.content)
        assertEquals(listOf("access", "createInvite", "access"), sharing.calls)
    }

    @Test
    fun `the invite limit is reported and nothing is kept`() = runTest {
        val viewModel = accessViewModel()
        sharing.failNext += SharingFailure.INVITE_LIMIT

        viewModel.onCreateInvite()

        assertNull(viewModel.state.value.newInvite)
        assertFalse(viewModel.state.value.isCreating)
        assertEquals(SharingFailure.INVITE_LIMIT, viewModel.state.value.message)
    }

    @Test
    fun `revoking the new invite forgets its token`() = runTest {
        sharing.createdInvite = CreatedInvite(invite.id, token, invite.expiresAt)
        val viewModel = accessViewModel()
        viewModel.onCreateInvite()

        viewModel.onRevokeInvite(invite.id)

        assertNull(viewModel.state.value.newInvite)
        assertTrue("revokeInvite:${invite.id}" in sharing.calls)
        assertEquals("access", sharing.calls.last())
    }

    @Test
    fun `an invite or member already gone is not reported as a failure`() = runTest {
        val viewModel = accessViewModel()
        sharing.failNext += SharingFailure.UNAVAILABLE

        viewModel.onRevokeInvite(invite.id)

        assertNull(viewModel.state.value.message)
        assertEquals("access", sharing.calls.last())
    }

    @Test
    fun `a member is removed only after confirmation`() = runTest {
        val viewModel = accessViewModel()

        viewModel.onRemoveRequested(member)
        assertEquals(member, viewModel.state.value.confirmingRemoval)
        viewModel.onRemoveDismissed()
        assertFalse(sharing.calls.any { it.startsWith("removeMember") })

        viewModel.onRemoveRequested(member)
        viewModel.onRemoveConfirmed()
        assertNull(viewModel.state.value.confirmingRemoval)
        assertTrue("removeMember:${member.userId}" in sharing.calls)
        assertEquals("access", sharing.calls.last())
    }

    @Test
    fun `a failed removal is reported and the member stays changeable`() = runTest {
        val viewModel = accessViewModel()
        sharing.failNext += SharingFailure.NETWORK

        viewModel.onRemoveRequested(member)
        viewModel.onRemoveConfirmed()

        assertEquals(SharingFailure.NETWORK, viewModel.state.value.message)
        assertTrue(viewModel.state.value.changing.isEmpty())
    }

    // ---- shared lists ----

    @Test
    fun `shared lists load, and a failed refresh keeps them`() = runTest {
        val summary = SharedWishlistSummary(listId, "Birthday", null)
        sharing.sharedWishlists = listOf(summary)
        val viewModel = SharedWishlistsViewModel(sharing)
        assertEquals(OnlineContent.Loading, viewModel.state.value.content)

        viewModel.refresh()
        assertEquals(OnlineContent.Loaded(listOf(summary)), viewModel.state.value.content)

        sharing.failNext += SharingFailure.NETWORK
        viewModel.refresh()
        assertEquals(OnlineContent.Loaded(listOf(summary)), viewModel.state.value.content)
        assertEquals(SharingFailure.NETWORK, viewModel.state.value.refreshFailure)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun `a shared list that became unavailable is no longer shown`() = runTest {
        sharing.sharedWishlist = SharedWishlist(listId, "Birthday", "Bea", emptyList())
        val viewModel = SharedWishlistViewModel(listId, sharing)
        viewModel.refresh()
        assertTrue(viewModel.state.value.content is OnlineContent.Loaded)

        sharing.failNext += SharingFailure.UNAVAILABLE
        viewModel.refresh()

        assertEquals(OnlineContent.Failed(SharingFailure.UNAVAILABLE), viewModel.state.value.content)
    }

    @Test
    fun `a first shared list load that fails can be retried`() = runTest {
        sharing.sharedWishlist = SharedWishlist(listId, "Birthday", null, emptyList())
        val viewModel = SharedWishlistViewModel(listId, sharing)
        sharing.failNext += SharingFailure.SERVER

        viewModel.refresh()
        assertEquals(OnlineContent.Failed(SharingFailure.SERVER), viewModel.state.value.content)

        viewModel.refresh()
        assertEquals(OnlineContent.Loaded(sharing.sharedWishlist), viewModel.state.value.content)
    }

    @Test
    fun `leaving a shared list asks first, then ends the membership`() = runTest {
        val viewModel = SharedWishlistViewModel(listId, sharing)

        viewModel.onLeaveRequested()
        assertTrue(viewModel.leaving.value.confirming)
        viewModel.onLeaveDismissed()
        assertFalse(viewModel.leaving.value.confirming)
        assertFalse(sharing.calls.any { it.startsWith("leave") })

        viewModel.onLeaveRequested()
        viewModel.onLeaveConfirmed()

        assertEquals(listOf("leave:$listId"), sharing.calls)
        assertEquals(LeaveState(left = true), viewModel.leaving.value)
    }

    @Test
    fun `a list already gone counts as left`() = runTest {
        val viewModel = SharedWishlistViewModel(listId, sharing)
        sharing.failNext += SharingFailure.UNAVAILABLE

        viewModel.onLeaveConfirmed()

        assertTrue(viewModel.leaving.value.left)
        assertNull(viewModel.leaving.value.failure)
    }

    @Test
    fun `a failed leave stays on the list, is reported once and can be tried again`() = runTest {
        val viewModel = SharedWishlistViewModel(listId, sharing)
        sharing.failNext += SharingFailure.NETWORK

        viewModel.onLeaveConfirmed()
        assertEquals(LeaveState(failure = SharingFailure.NETWORK), viewModel.leaving.value)
        viewModel.onLeaveFailureShown()
        assertNull(viewModel.leaving.value.failure)

        viewModel.onLeaveConfirmed()
        assertTrue(viewModel.leaving.value.left)
        assertEquals(2, sharing.calls.count { it.startsWith("leave") })
    }

    @Test
    fun `a leave in progress or done is not repeated`() = runTest {
        sharing.gate = CompletableDeferred()
        val viewModel = SharedWishlistViewModel(listId, sharing)

        viewModel.onLeaveConfirmed()
        viewModel.onLeaveConfirmed()
        assertTrue(viewModel.leaving.value.isLeaving)
        sharing.gate!!.complete(Unit)
        viewModel.onLeaveConfirmed()

        assertEquals(1, sharing.calls.count { it.startsWith("leave") })
        assertTrue(viewModel.leaving.value.left)
    }

    @Test
    fun `after leaving, Shared with you no longer lists the list once it refreshes`() = runTest {
        val left = SharedWishlistSummary(listId, "Birthday", null)
        val kept = SharedWishlistSummary(UUID.randomUUID(), "Christmas", "Bea")
        sharing.sharedWishlists = listOf(left, kept)
        val shared = SharedWishlistsViewModel(sharing)
        shared.refresh()
        val detail = SharedWishlistViewModel(listId, sharing)

        detail.onLeaveConfirmed()
        assertTrue("the detail is told to close", detail.leaving.value.left)
        // The server no longer lists it; the list screen asks again when it is shown again.
        sharing.sharedWishlists = listOf(kept)
        shared.refresh()

        assertEquals(OnlineContent.Loaded(listOf(kept)), shared.state.value.content)
    }

    // ---- join ----

    @Test
    fun `an opened link is taken once and redeemed`() = runTest {
        pending.offer(PendingJoin.Invite(token))

        val viewModel = JoinWishlistViewModel(sharing, pending)

        assertEquals(JoinPhase.Joined(sharing.redeemedWishlistId), viewModel.state.value.phase)
        assertEquals(listOf(token), sharing.redeemedTokens)
        assertNull("the link is consumed", pending.take())
        // A second screen, e.g. after going back, does not join again.
        JoinWishlistViewModel(sharing, pending)
        assertEquals(1, sharing.redeemedTokens.size)
    }

    @Test
    fun `an invalid opened link says so without asking the server`() = runTest {
        pending.offer(PendingJoin.InvalidLink)

        val viewModel = JoinWishlistViewModel(sharing, pending)

        assertEquals(JoinPhase.Failed(JoinFailure.INVALID_LINK), viewModel.state.value.phase)
        assertTrue(sharing.calls.isEmpty())
    }

    @Test
    fun `a pasted link is redeemed, a pasted non-link is refused locally`() = runTest {
        val viewModel = JoinWishlistViewModel(sharing, pending)
        assertEquals(JoinPhase.Idle, viewModel.state.value.phase)

        viewModel.onInputChange("hello")
        viewModel.onJoin()
        assertEquals(JoinPhase.Failed(JoinFailure.INVALID_LINK), viewModel.state.value.phase)
        assertTrue(sharing.calls.isEmpty())

        viewModel.onInputChange("Join me: ${joinLink(token)}")
        assertEquals("editing clears the error", JoinPhase.Idle, viewModel.state.value.phase)
        viewModel.onJoin()
        assertEquals(JoinPhase.Joined(sharing.redeemedWishlistId), viewModel.state.value.phase)
        assertEquals(listOf(token), sharing.redeemedTokens)
    }

    @Test
    fun `a network failure can be retried with the same token`() = runTest {
        pending.offer(PendingJoin.Invite(token))
        sharing.failNext += SharingFailure.NETWORK

        val viewModel = JoinWishlistViewModel(sharing, pending)
        assertEquals(JoinPhase.Failed(JoinFailure.NETWORK), viewModel.state.value.phase)
        assertTrue(JoinFailure.NETWORK.isRetryable)

        viewModel.onRetry()
        assertEquals(JoinPhase.Joined(sharing.redeemedWishlistId), viewModel.state.value.phase)
    }

    @Test
    fun `unavailable is final and every refusal reads the same`() = runTest {
        listOf(SharingFailure.UNAVAILABLE, SharingFailure.INVITE_LIMIT).forEach { failure ->
            pending.offer(PendingJoin.Invite(token))
            sharing.failNext += failure

            val viewModel = JoinWishlistViewModel(sharing, pending)
            assertEquals(JoinPhase.Failed(JoinFailure.UNAVAILABLE), viewModel.state.value.phase)
            assertFalse(JoinFailure.UNAVAILABLE.isRetryable)

            val calls = sharing.calls.size
            viewModel.onRetry()
            assertEquals("nothing left to retry", calls, sharing.calls.size)
        }
    }

    @Test
    fun `a join in progress is not started twice`() = runTest {
        sharing.gate = CompletableDeferred()
        val viewModel = JoinWishlistViewModel(sharing, pending)
        viewModel.onInputChange(token.value)

        viewModel.onJoin()
        viewModel.onJoin()
        assertEquals(JoinPhase.Joining, viewModel.state.value.phase)

        sharing.gate!!.complete(Unit)
        assertEquals(listOf("redeem"), sharing.calls)
        assertEquals(JoinPhase.Joined(sharing.redeemedWishlistId), viewModel.state.value.phase)
    }
}
