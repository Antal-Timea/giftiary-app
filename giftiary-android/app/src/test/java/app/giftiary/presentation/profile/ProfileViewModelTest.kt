package app.giftiary.presentation.profile

import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.GiftDetails
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.testing.FakeGiftRepository
import app.giftiary.testing.FakePersonRepository
import app.giftiary.testing.FakeProfileRepository
import app.giftiary.testing.FakeWishlistRepository
import app.giftiary.testing.MainDispatcherRule
import app.giftiary.testing.giftEntry
import app.giftiary.testing.keepSubscribed
import app.giftiary.testing.person
import app.giftiary.testing.wishlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeProfileRepository()
    private val people = FakePersonRepository()
    private val gifts = FakeGiftRepository()
    private val wishlists = FakeWishlistRepository()
    private val session = object : AccountSession {
        override val state = MutableStateFlow<OwnerSessionState>(OwnerSessionState.Ready(AuthUser(UUID.randomUUID(), "anna@example.com")))
        override fun signOut() = Unit
    }

    private fun viewModel() = ProfileViewModel(profiles, session, people, gifts, wishlists)

    @Test
    fun `the email and the counts of what this device keeps are shown, and no name until the server has one`() = runTest {
        val peter = person("Peter")
        people.givenPeople(peter, person("Mom"))
        gifts.givenGifts(
            giftEntry(
                GiftDetails(GiftDirection.GIVEN, null, peter.id, null, "Scarf", LocalDate.of(2026, 3, 12), null, null, null),
                peter,
            ),
        )
        wishlists.givenWishlists(wishlist("Birthday"))
        val viewModel = viewModel()
        keepSubscribed(viewModel.state)

        assertEquals(ProfileUiState(email = "anna@example.com", displayName = null, stats = ProfileStats(2, 1, 1)), viewModel.state.value)
    }

    @Test
    fun `the name others see is read on every visit, so a change from Edit profile shows on return`() = runTest {
        val viewModel = viewModel()
        keepSubscribed(viewModel.state)
        viewModel.onResume()
        assertNull(viewModel.state.value.displayName)

        profiles.name = "Anna"
        viewModel.onResume()

        assertEquals("Anna", viewModel.state.value.displayName)
    }

    @Test
    fun `a name that cannot be read leaves the email, and keeps a name read before`() = runTest {
        profiles.name = "Anna"
        val viewModel = viewModel()
        keepSubscribed(viewModel.state)
        viewModel.onResume()

        profiles.failNext += ProfileFailure.NETWORK
        viewModel.onResume()

        assertEquals("Anna", viewModel.state.value.displayName)
        assertEquals("anna@example.com", viewModel.state.value.email)
    }

    @Test
    fun `the avatar is shown when the server has one, downloaded again only when it changed`() = runTest {
        val viewModel = viewModel()
        keepSubscribed(viewModel.state)
        viewModel.onResume()
        assertNull(viewModel.state.value.avatar)

        profiles.avatarPath = AVATAR
        profiles.avatars[AVATAR] = byteArrayOf(7)
        viewModel.onResume()
        viewModel.onResume()

        assertTrue(viewModel.state.value.avatar!!.bytes.contentEquals(byteArrayOf(7)))
        assertEquals(1, profiles.calls.count { it == "avatar" })

        profiles.avatarPath = null
        viewModel.onResume()
        assertNull("removed in Edit profile, gone on return", viewModel.state.value.avatar)
    }

    @Test
    fun `counts that cannot be read are left out rather than guessed`() = runTest {
        people.peopleFlowFailure = IllegalStateException("database")
        val viewModel = viewModel()
        keepSubscribed(viewModel.state)

        assertNull(viewModel.state.value.stats)
    }

    private companion object {
        const val AVATAR = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/10000000-0000-4000-8000-000000000001.jpg"
    }
}
