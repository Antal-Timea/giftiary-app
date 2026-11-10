package app.giftiary.presentation.home

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.SyncStatus
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.people.PERSON_PHOTO_TAG
import app.giftiary.presentation.sync.SyncStatusContent
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.FakeProfileRepository
import app.giftiary.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** The user's own avatar on Home's profile entry, and the monogram while there is none. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1400dp")
class HomeAvatarTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private var opened = 0

    private fun showHome(avatar: LoadedPhoto?, widthClass: WidthClass = WidthClass.COMPACT) {
        compose.setContent {
            GiftiaryTheme {
                HomeScreen(
                    syncStatus = SyncStatusContent.Loaded(SyncStatus(waitingCount = 0, attention = emptyList())),
                    comingUp = ComingUpContent.Loaded(emptyList()),
                    onOpenComingUp = {},
                    reminderCard = ReminderCardState.Hidden,
                    reminderActions = ReminderCardActions({}, {}, {}, {}),
                    recentGifts = RecentGiftsContent.Loaded(emptyList()),
                    onOpenGift = {},
                    onOpenAllGifts = {},
                    onOpenOwnOccasions = {},
                    onOpenSyncAttention = {},
                    accountName = "Anna",
                    onOpenProfile = { opened++ },
                    accountAvatar = avatar,
                    widthClass = widthClass,
                )
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the saved avatar fills the profile entry, which is still named and opens the profile`() {
        showHome(jpeg())

        compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithContentDescription("Your profile").performClick()
        assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "w1280dp-h900dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `on a wide window too`() {
        showHome(jpeg(), WidthClass.EXPANDED)

        compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `without an avatar the entry keeps the monogram`() {
        showHome(null)

        compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Your profile").assertExists()
    }

    @Test
    fun `the avatar is read on every visit, downloaded once while unchanged, and kept through a failure`() = runTest {
        val profiles = FakeProfileRepository()
        val viewModel = HomeAvatarViewModel(profiles)
        viewModel.onResume()
        assertNull(viewModel.avatar.value)

        profiles.avatarPath = AVATAR
        profiles.avatars[AVATAR] = byteArrayOf(5)
        viewModel.onResume()
        viewModel.onResume()
        profiles.failNext += ProfileFailure.NETWORK
        viewModel.onResume()

        assertTrue(viewModel.avatar.value!!.bytes.contentEquals(byteArrayOf(5)))
        assertEquals(1, profiles.calls.count { it == "avatar" })
        assertTrue("never the name or anything else", profiles.calls.all { it == "profile" || it == "avatar" })

        profiles.avatarPath = null
        viewModel.onResume()
        assertNull(viewModel.avatar.value)
    }

    @Test
    fun `the name for the monogram is read on every visit and kept through a failure`() = runTest {
        val profiles = FakeProfileRepository()
        val viewModel = HomeAvatarViewModel(profiles)
        assertNull(viewModel.displayName.value)

        profiles.name = "Lisa"
        viewModel.onResume()
        assertEquals("Lisa", viewModel.displayName.value)

        profiles.name = "Gate A User"
        viewModel.onResume()
        assertEquals("Gate A User", viewModel.displayName.value)

        profiles.failNext += ProfileFailure.NETWORK
        viewModel.onResume()
        assertEquals("Gate A User", viewModel.displayName.value)
    }

    private fun jpeg(): LoadedPhoto {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF5A4032.toInt()) }
        return LoadedPhoto(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray())
    }

    private companion object {
        const val AVATAR = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/10000000-0000-4000-8000-000000000001.jpg"
    }
}
