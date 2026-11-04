package app.giftiary.presentation.sharing

import app.giftiary.domain.model.RecipientInvite
import app.giftiary.domain.repository.SharingFailure
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.FakeWishlistSharingRepository
import app.giftiary.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** The invitation inbox: loading per account, the badge count, and answering an invitation. */
class InvitationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sharing = FakeWishlistSharingRepository()
    private val alice = UUID.randomUUID()
    private val owner = FakeActiveOwner(alice)
    private val inbox = InvitationInbox(sharing, owner)
    private val now = Instant.parse("2026-11-04T10:00:00Z")
    private val birthday = RecipientInvite(UUID.randomUUID(), "Birthday", "Bea", "Alex", now, now.plusSeconds(14 * 86_400))
    private val christmas = RecipientInvite(UUID.randomUUID(), "Christmas", null, null, now, now.plusSeconds(14 * 86_400))

    private fun viewModel() = InvitationsViewModel(inbox, sharing).also { it.refresh() }

    private fun InvitationsViewModel.loaded() = (invites.value.content as OnlineContent.Loaded).value

    @Test
    fun `invitations load with their count`() = runTest {
        sharing.myInvites = listOf(birthday, christmas)

        val viewModel = viewModel()

        assertEquals(listOf(birthday, christmas), viewModel.loaded())
        assertEquals(2, viewModel.count.value)
        assertEquals(listOf("myInvites"), sharing.calls)
    }

    @Test
    fun `no invitations, e g for an unconfirmed address, is an empty inbox without a badge`() = runTest {
        val viewModel = viewModel()

        assertEquals(emptyList<RecipientInvite>(), viewModel.loaded())
        assertEquals(0, viewModel.count.value)
    }

    @Test
    fun `a first load that fails shows the failure, counts nothing, and can be retried`() = runTest {
        sharing.myInvites = listOf(birthday)
        sharing.failNext += SharingFailure.NETWORK

        val viewModel = viewModel()
        assertEquals(OnlineContent.Failed(SharingFailure.NETWORK), viewModel.invites.value.content)
        assertEquals(0, viewModel.count.value)

        viewModel.refresh()
        assertEquals(listOf(birthday), viewModel.loaded())
    }

    @Test
    fun `a failed refresh keeps the invitations shown`() = runTest {
        sharing.myInvites = listOf(birthday)
        val viewModel = viewModel()
        sharing.failNext += SharingFailure.SERVER

        viewModel.refresh()

        assertEquals(listOf(birthday), viewModel.loaded())
        assertEquals(false, viewModel.invites.value.isRefreshing)
    }

    @Test
    fun `the badge and the Lists inbox read the same invitations`() = runTest {
        sharing.myInvites = listOf(birthday)
        val badge = viewModel()
        val lists = InvitationsViewModel(inbox, sharing)

        assertEquals(1, lists.count.value)
        lists.onDecline(birthday.id)
        assertEquals(0, badge.count.value)
    }

    @Test
    fun `another account never sees the invitations loaded for the first`() = runTest {
        sharing.myInvites = listOf(birthday)
        val viewModel = viewModel()

        owner.ownerId.value = UUID.randomUUID()
        assertEquals(OnlineState<List<RecipientInvite>>(), viewModel.invites.value)
        assertEquals(0, viewModel.count.value)

        owner.ownerId.value = null
        viewModel.refresh()
        assertEquals("nothing is asked without an account", 1, sharing.calls.size)
    }

    @Test
    fun `accepting joins, takes the invitation off and names the list to open once`() = runTest {
        sharing.myInvites = listOf(birthday, christmas)
        val listId = sharing.acceptedWishlistId
        val viewModel = viewModel()

        viewModel.onAccept(birthday.id)

        assertEquals(listId, viewModel.responses.value.accepted)
        assertEquals(listOf(christmas), viewModel.loaded())
        assertEquals(listOf("myInvites", "accept:${birthday.id}", "myInvites"), sharing.calls)
        assertTrue(viewModel.responses.value.responding.isEmpty())
        viewModel.onAcceptedOpened()
        assertNull(viewModel.responses.value.accepted)
    }

    @Test
    fun `declining takes the invitation off and opens nothing`() = runTest {
        sharing.myInvites = listOf(birthday)
        val viewModel = viewModel()

        viewModel.onDecline(birthday.id)

        assertEquals(emptyList<RecipientInvite>(), viewModel.loaded())
        assertNull(viewModel.responses.value.accepted)
        assertNull(viewModel.responses.value.message)
        assertTrue("decline:${birthday.id}" in sharing.calls)
    }

    @Test
    fun `an invitation withdrawn, expired or for a full list is said to be gone and taken off`() = runTest {
        sharing.myInvites = listOf(birthday)
        val viewModel = viewModel()
        sharing.failNext += SharingFailure.UNAVAILABLE

        viewModel.onAccept(birthday.id)

        assertEquals(InvitationMessage.GONE, viewModel.responses.value.message)
        assertNull(viewModel.responses.value.accepted)
        viewModel.onMessageShown()
        assertNull(viewModel.responses.value.message)
    }

    @Test
    fun `a network failure keeps the invitation to answer again`() = runTest {
        sharing.myInvites = listOf(birthday)
        val viewModel = viewModel()
        sharing.failNext += SharingFailure.NETWORK

        viewModel.onAccept(birthday.id)

        assertEquals(InvitationMessage.NETWORK, viewModel.responses.value.message)
        assertEquals(listOf(birthday), viewModel.loaded())
        assertTrue(viewModel.responses.value.responding.isEmpty())
    }

    @Test
    fun `an invitation is answered once while its answer is on its way`() = runTest {
        sharing.myInvites = listOf(birthday)
        val viewModel = viewModel()
        val gate = CompletableDeferred<Unit>()
        sharing.gate = gate

        viewModel.onAccept(birthday.id)
        viewModel.onDecline(birthday.id)
        assertEquals(setOf(birthday.id), viewModel.responses.value.responding)
        gate.complete(Unit)

        assertEquals(1, sharing.calls.count { it.startsWith("accept") || it.startsWith("decline") })
    }
}
