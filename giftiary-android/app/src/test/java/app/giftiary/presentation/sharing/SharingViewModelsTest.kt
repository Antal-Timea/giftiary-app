package app.giftiary.presentation.sharing

import app.giftiary.domain.model.ActiveInvite
import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.EmailInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.ReservationState
import app.giftiary.domain.model.SharedWish
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.model.WishlistAccess
import app.giftiary.domain.model.WishlistMember
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.testing.FakeProfileRepository
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

    private val profiles = FakeProfileRepository()

    private fun accessViewModel() = WishlistAccessViewModel(listId, sharing, FakeWishlistRepository(), profiles)

    // ---- owner access ----

    @Test
    fun `email invites load beside the access, and their failure leaves link sharing working`() = runTest {
        sharing.access = WishlistAccess(listOf(invite), listOf(member))
        sharing.failEmailNext += SharingFailure.SERVER

        val viewModel = accessViewModel()

        assertEquals(AccessContent.Loaded(sharing.access), viewModel.state.value.content)
        assertEquals(EmailInvitesContent.Failed(SharingFailure.SERVER), viewModel.state.value.emailInvites)
        sharing.createdInvite = CreatedInvite(invite.id, token, invite.expiresAt)
        viewModel.onCreateInvite()
        assertEquals(invite.id, viewModel.state.value.newInvite?.id)

        viewModel.load()
        assertEquals(EmailInvitesContent.Loaded(emptyList()), viewModel.state.value.emailInvites)
    }

    @Test
    fun `an email invite is sent normalized, closes the form and is confirmed once`() = runTest {
        val viewModel = accessViewModel()

        viewModel.onEmailInviteOpened()
        viewModel.onEmailChanged("  Bob@Example.COM ")
        viewModel.onSendEmailInvite()

        assertEquals(listOf("list", "create:bob@example.com", "list"), sharing.emailCalls)
        assertNull(viewModel.state.value.emailForm)
        assertEquals("bob@example.com", viewModel.state.value.invitedEmail)
        val listed = (viewModel.state.value.emailInvites as EmailInvitesContent.Loaded).invites
        assertEquals(listOf("bob@example.com"), listed.map { it.email })
        viewModel.onInvitedNoticeShown()
        assertNull(viewModel.state.value.invitedEmail)
    }

    @Test
    fun `inviting a pending address again reads exactly like a first invite`() = runTest {
        val viewModel = accessViewModel()
        fun send() {
            viewModel.onEmailInviteOpened()
            viewModel.onEmailChanged("bob@example.com")
            viewModel.onSendEmailInvite()
        }

        send()
        val first = viewModel.state.value
        viewModel.onInvitedNoticeShown()
        send()

        assertEquals(first.invitedEmail, viewModel.state.value.invitedEmail)
        assertEquals(first.emailInvites, viewModel.state.value.emailInvites)
        assertNull(viewModel.state.value.emailForm)
    }

    @Test
    fun `an address that is not one is refused before anything is sent`() = runTest {
        val viewModel = accessViewModel()
        viewModel.onEmailInviteOpened()

        listOf("", "bob", "bob@example", "bob @example.com", "@example.com").forEach { text ->
            viewModel.onEmailChanged(text)
            viewModel.onSendEmailInvite()
            assertEquals(text, EmailInviteError.INVALID, viewModel.state.value.emailForm?.error)
        }
        assertEquals(listOf("list"), sharing.emailCalls)

        viewModel.onEmailChanged("bob@example.com")
        assertNull("typing clears the error", viewModel.state.value.emailForm?.error)
    }

    @Test
    fun `refusals keep the form open with what was typed, saying nothing about an account`() = runTest {
        val viewModel = accessViewModel()
        viewModel.onEmailInviteOpened()
        viewModel.onEmailChanged("me@example.com")
        val expected = listOf(
            SharingFailure.INVALID_INPUT to EmailInviteError.NOT_ALLOWED,
            SharingFailure.ALREADY_MEMBER to EmailInviteError.ALREADY_MEMBER,
            SharingFailure.INVITE_LIMIT to EmailInviteError.LIMIT,
            SharingFailure.UNAVAILABLE to EmailInviteError.UNAVAILABLE,
            SharingFailure.NETWORK to EmailInviteError.NETWORK,
            SharingFailure.SERVER to EmailInviteError.SERVER,
        )

        expected.forEach { (failure, error) ->
            sharing.failEmailNext += failure
            viewModel.onSendEmailInvite()
            assertEquals(EmailInviteForm(text = "me@example.com", error = error), viewModel.state.value.emailForm)
            assertNull(viewModel.state.value.invitedEmail)
        }
    }

    @Test
    fun `closing the form forgets what was typed`() = runTest {
        val viewModel = accessViewModel()
        viewModel.onEmailInviteOpened()
        viewModel.onEmailChanged("bob@example.com")

        viewModel.onEmailInviteDismissed()
        assertNull(viewModel.state.value.emailForm)
        viewModel.onEmailInviteOpened()
        assertEquals(EmailInviteForm(), viewModel.state.value.emailForm)
    }

    @Test
    fun `withdrawing an email invite asks the server and shows what it now holds`() = runTest {
        val pendingInvite = EmailInvite(UUID.randomUUID(), "bob@example.com", now, now)
        sharing.emailInvites = listOf(pendingInvite)
        val viewModel = accessViewModel()

        viewModel.onRevokeEmailInvite(pendingInvite.id)

        assertEquals(listOf("list", "revoke:${pendingInvite.id}", "list"), sharing.emailCalls)
        assertEquals(EmailInvitesContent.Loaded(emptyList()), viewModel.state.value.emailInvites)
        assertTrue(viewModel.state.value.changing.isEmpty())
    }

    @Test
    fun `a failed email invite refresh keeps the invites shown`() = runTest {
        sharing.emailInvites = listOf(EmailInvite(UUID.randomUUID(), "bob@example.com", now, now))
        val viewModel = accessViewModel()
        sharing.failEmailNext += SharingFailure.NETWORK

        viewModel.load()

        assertEquals(EmailInvitesContent.Loaded(sharing.emailInvites), viewModel.state.value.emailInvites)
    }

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

    @Test
    fun `an owner without a name is asked for one, and can still create an invite`() = runTest {
        sharing.createdInvite = CreatedInvite(invite.id, token, invite.expiresAt)
        val viewModel = accessViewModel()

        viewModel.checkOwnerName()
        assertTrue(viewModel.state.value.showNamePrompt)

        viewModel.onCreateInvite()
        assertEquals("the prompt never blocks", sharing.createdInvite, viewModel.state.value.newInvite)
    }

    @Test
    fun `the name prompt can be put off for this screen, and goes once a name exists`() = runTest {
        val viewModel = accessViewModel()
        viewModel.checkOwnerName()

        viewModel.onNamePromptDismissed()
        assertFalse(viewModel.state.value.showNamePrompt)
        viewModel.checkOwnerName()
        assertFalse("not asked again on this screen", viewModel.state.value.showNamePrompt)

        val other = accessViewModel()
        profiles.name = "Anna"
        other.checkOwnerName()
        assertFalse(other.state.value.showNamePrompt)
    }

    @Test
    fun `sharing reads only the owner's name, never the avatar`() = runTest {
        profiles.avatarPath = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/10000000-0000-4000-8000-000000000001.jpg"
        val viewModel = accessViewModel()

        viewModel.checkOwnerName()

        assertEquals(listOf("displayName"), profiles.calls)
    }

    @Test
    fun `an unreadable profile shows no prompt rather than a wrong one`() = runTest {
        profiles.failNext += ProfileFailure.NETWORK
        val viewModel = accessViewModel()

        viewModel.checkOwnerName()

        assertFalse(viewModel.state.value.showNamePrompt)
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

    // ---- reservations ----

    private val lamp = SharedWish(UUID.randomUUID(), "Lamp", null, null, null, ReservationState.AVAILABLE)
    private val socks = SharedWish(UUID.randomUUID(), "Socks", null, null, null, ReservationState.RESERVED_BY_ME)

    private fun reservationViewModel(vararg wishes: SharedWish): SharedWishlistViewModel {
        sharing.sharedWishlist = SharedWishlist(listId, "Birthday", null, wishes.toList())
        return SharedWishlistViewModel(listId, sharing).also { it.refresh() }
    }

    private fun SharedWishlistViewModel.stateOf(wish: SharedWish): ReservationState? =
        (state.value.content as OnlineContent.Loaded).value.wishes.single { it.id == wish.id }.reservation

    private fun serverHas(vararg wishes: SharedWish) {
        sharing.sharedWishlist = SharedWishlist(listId, "Birthday", null, wishes.toList())
    }

    @Test
    fun `reserving shows the server's answer, then reads the whole list again`() = runTest {
        val viewModel = reservationViewModel(lamp)
        sharing.calls.clear()
        sharing.reserveAnswer = ReservationState.RESERVED_BY_ME
        serverHas(lamp.copy(reservation = ReservationState.RESERVED_BY_ME))

        viewModel.onReserve(lamp.id)

        assertEquals(listOf("reserve:${lamp.id}", "sharedWishlist"), sharing.calls)
        assertEquals(ReservationState.RESERVED_BY_ME, viewModel.stateOf(lamp))
        assertNull(viewModel.reservations.value.message)
        assertTrue(viewModel.reservations.value.inFlight.isEmpty())
    }

    @Test
    fun `nothing changes before the server answers, and the wish takes one action at a time`() = runTest {
        val viewModel = reservationViewModel(lamp)
        sharing.calls.clear()
        sharing.gate = CompletableDeferred()

        viewModel.onReserve(lamp.id)
        viewModel.onReserve(lamp.id)
        viewModel.onReleaseRequested(lamp.id)

        assertEquals(ReservationState.AVAILABLE, viewModel.stateOf(lamp))
        assertEquals(setOf(lamp.id), viewModel.reservations.value.inFlight)
        assertNull("no release while reserving", viewModel.reservations.value.confirmingRelease)
        assertEquals(listOf("reserve:${lamp.id}"), sharing.calls)

        serverHas(lamp.copy(reservation = ReservationState.RESERVED_BY_ME))
        sharing.gate!!.complete(Unit)
        assertEquals(ReservationState.RESERVED_BY_ME, viewModel.stateOf(lamp))
        assertTrue(viewModel.reservations.value.inFlight.isEmpty())
    }

    @Test
    fun `another wish can be reserved while one waits`() = runTest {
        val other = lamp.copy(id = UUID.randomUUID(), name = "Mug")
        val viewModel = reservationViewModel(lamp, other)
        sharing.gate = CompletableDeferred()

        viewModel.onReserve(lamp.id)
        viewModel.onReserve(other.id)

        assertEquals(setOf(lamp.id, other.id), viewModel.reservations.value.inFlight)
        sharing.gate!!.complete(Unit)
    }

    @Test
    fun `a lost race shows the wish as reserved and says so, naming no one`() = runTest {
        val viewModel = reservationViewModel(lamp)
        sharing.reserveAnswer = ReservationState.RESERVED_BY_OTHER
        serverHas(lamp.copy(reservation = ReservationState.RESERVED_BY_OTHER))

        viewModel.onReserve(lamp.id)

        assertEquals(ReservationState.RESERVED_BY_OTHER, viewModel.stateOf(lamp))
        assertEquals(ReservationMessage.TAKEN_BY_OTHER, viewModel.reservations.value.message)
        viewModel.onReservationMessageShown()
        assertNull(viewModel.reservations.value.message)
    }

    @Test
    fun `releasing asks first, then shows what the server says`() = runTest {
        val viewModel = reservationViewModel(socks)
        sharing.calls.clear()

        viewModel.onReleaseRequested(socks.id)
        assertEquals(socks.id, viewModel.reservations.value.confirmingRelease)
        viewModel.onReleaseDismissed()
        assertTrue(sharing.calls.isEmpty())

        sharing.releaseAnswer = ReservationState.AVAILABLE
        serverHas(socks.copy(reservation = ReservationState.AVAILABLE))
        viewModel.onReleaseRequested(socks.id)
        viewModel.onReleaseConfirmed()

        assertNull(viewModel.reservations.value.confirmingRelease)
        assertEquals(listOf("release:${socks.id}", "sharedWishlist"), sharing.calls)
        assertEquals(ReservationState.AVAILABLE, viewModel.stateOf(socks))
    }

    @Test
    fun `a release answered with another state shows that state, without the lost-race message`() = runTest {
        val viewModel = reservationViewModel(socks)
        sharing.releaseAnswer = ReservationState.RESERVED_BY_OTHER
        serverHas(socks.copy(reservation = ReservationState.RESERVED_BY_OTHER))

        viewModel.onReleaseRequested(socks.id)
        viewModel.onReleaseConfirmed()

        assertEquals(ReservationState.RESERVED_BY_OTHER, viewModel.stateOf(socks))
        assertNull(viewModel.reservations.value.message)
    }

    @Test
    fun `after a lost connection the last state stays and the list is read again`() = runTest {
        val viewModel = reservationViewModel(lamp)
        sharing.calls.clear()
        sharing.failNext += SharingFailure.NETWORK
        // The reservation did commit; only the reload can tell.
        serverHas(lamp.copy(reservation = ReservationState.RESERVED_BY_ME))

        viewModel.onReserve(lamp.id)

        assertEquals(listOf("reserve:${lamp.id}", "sharedWishlist"), sharing.calls)
        assertEquals(ReservationMessage.NETWORK, viewModel.reservations.value.message)
        assertEquals(ReservationState.RESERVED_BY_ME, viewModel.stateOf(lamp))
    }

    @Test
    fun `a lost connection never shows a state of its own`() = runTest {
        val viewModel = reservationViewModel(lamp)
        sharing.failNext += SharingFailure.NETWORK
        sharing.failNext += SharingFailure.NETWORK

        viewModel.onReserve(lamp.id)

        // The reload failed too: what was last read from the server stays.
        assertEquals(ReservationState.AVAILABLE, viewModel.stateOf(lamp))
        assertEquals(ReservationMessage.NETWORK, viewModel.reservations.value.message)
    }

    @Test
    fun `a server error assumes nothing and reads nothing`() = runTest {
        val viewModel = reservationViewModel(lamp)
        sharing.calls.clear()
        sharing.failNext += SharingFailure.SERVER

        viewModel.onReserve(lamp.id)

        assertEquals(listOf("reserve:${lamp.id}"), sharing.calls)
        assertEquals(ReservationState.AVAILABLE, viewModel.stateOf(lamp))
        assertEquals(ReservationMessage.SERVER, viewModel.reservations.value.message)
        assertTrue("it can be tried again", viewModel.reservations.value.inFlight.isEmpty())
    }

    @Test
    fun `an unavailable wish is reported uniformly and the list read again`() = runTest {
        val mug = lamp.copy(id = UUID.randomUUID(), name = "Mug")
        val viewModel = reservationViewModel(lamp, mug)
        sharing.failNext += SharingFailure.UNAVAILABLE
        serverHas(mug)

        viewModel.onReserve(lamp.id)

        assertEquals(ReservationMessage.WISH_UNAVAILABLE, viewModel.reservations.value.message)
        assertEquals(listOf(mug), (viewModel.state.value.content as OnlineContent.Loaded).value.wishes)
    }

    @Test
    fun `when access is gone the reload turns the whole list unavailable`() = runTest {
        val viewModel = reservationViewModel(lamp)
        sharing.failNext += SharingFailure.UNAVAILABLE
        sharing.failNext += SharingFailure.UNAVAILABLE

        viewModel.onReserve(lamp.id)

        assertEquals(ReservationMessage.WISH_UNAVAILABLE, viewModel.reservations.value.message)
        assertEquals(OnlineContent.Failed(SharingFailure.UNAVAILABLE), viewModel.state.value.content)
    }

    @Test
    fun `a reload asked for before the action cannot overwrite its answer`() = runTest {
        val viewModel = reservationViewModel(lamp)
        val staleRead = CompletableDeferred<Unit>()
        sharing.gate = staleRead
        // A refresh reads the server before the reservation...
        viewModel.refresh()
        sharing.gate = null
        serverHas(lamp.copy(reservation = ReservationState.RESERVED_BY_ME))
        sharing.reserveAnswer = ReservationState.RESERVED_BY_ME

        viewModel.onReserve(lamp.id)
        // ...and answers only now, with the old state.
        staleRead.complete(Unit)

        assertEquals(ReservationState.RESERVED_BY_ME, viewModel.stateOf(lamp))
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
    fun `a link opened while the join screen is open is joined there`() = runTest {
        val viewModel = JoinWishlistViewModel(sharing, pending)
        assertEquals(JoinPhase.Idle, viewModel.state.value.phase)

        pending.offer(PendingJoin.Invite(token))

        assertEquals(JoinPhase.Joined(sharing.redeemedWishlistId), viewModel.state.value.phase)
        assertEquals(listOf(token), sharing.redeemedTokens)
        assertNull("the link is consumed", pending.take())
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
            // Each its own app run: a screen still open would take the next link itself.
            val pending = PendingWishlistJoin()
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
