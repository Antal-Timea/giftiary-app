package app.giftiary.presentation.profile

import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.domain.repository.PhotoKind
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.testing.FakeGiftPhotoRepository
import app.giftiary.testing.FakeProfileRepository
import app.giftiary.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** The name others see, moved here from Settings unchanged, and the avatar only the user sees. */
class EditProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeProfileRepository()
    private val photos = FakeGiftPhotoRepository()
    private val session = object : AccountSession {
        override val state = MutableStateFlow<OwnerSessionState>(OwnerSessionState.Ready(AuthUser(UUID.randomUUID(), "anna@example.com")))
        override fun signOut() = Unit
    }

    private fun viewModel() = EditProfileViewModel(profiles, photos, session)

    @Test
    fun `the saved name is shown for editing`() = runTest {
        profiles.name = "Anna"

        val viewModel = viewModel()

        assertEquals(ProfileContent.Loaded(saved = "Anna"), viewModel.state.value.profile)
        assertEquals("Anna", viewModel.state.value.nameInput)
    }

    @Test
    fun `without a name the field is empty, never the email`() = runTest {
        val viewModel = viewModel()

        assertEquals(ProfileContent.Loaded(saved = null), viewModel.state.value.profile)
        assertEquals("", viewModel.state.value.nameInput)
    }

    @Test
    fun `a name is saved trimmed, and an emptied one as no name`() = runTest {
        val viewModel = viewModel()

        viewModel.onNameChange("  Anna  ")
        viewModel.onSave()
        assertEquals(listOf<String?>("Anna"), profiles.saved)
        assertEquals("Anna", viewModel.state.value.nameInput)
        assertTrue(viewModel.state.value.saved)

        viewModel.onMessageShown()
        viewModel.onNameChange("   ")
        viewModel.onSave()
        assertEquals(listOf("Anna", null), profiles.saved)
        assertEquals(ProfileContent.Loaded(saved = null), viewModel.state.value.profile)
    }

    @Test
    fun `a name with a line break is refused locally`() = runTest {
        val viewModel = viewModel()

        viewModel.onNameChange("Anna\nKovács")
        viewModel.onSave()

        assertTrue(viewModel.state.value.inputInvalid)
        assertTrue(profiles.saved.isEmpty())
        viewModel.onNameChange("Anna")
        assertFalse(viewModel.state.value.inputInvalid)
    }

    @Test
    fun `an unchanged name is not sent again`() = runTest {
        profiles.name = "Anna"
        val viewModel = viewModel()

        viewModel.onNameChange(" Anna ")
        viewModel.onSave()

        assertTrue(profiles.saved.isEmpty())
    }

    @Test
    fun `a failed save keeps what was typed and can be tried again`() = runTest {
        val viewModel = viewModel()
        profiles.failNext += ProfileFailure.NETWORK

        viewModel.onNameChange("Anna")
        viewModel.onSave()
        assertEquals(ProfileFailure.NETWORK, viewModel.state.value.saveFailure)
        assertEquals("Anna", viewModel.state.value.nameInput)
        assertFalse(viewModel.state.value.isSaving)

        viewModel.onMessageShown()
        viewModel.onSave()
        assertEquals(listOf<String?>("Anna"), profiles.saved)
    }

    // ---- avatar ----

    @Test
    fun `the saved avatar is downloaded and shown, and without one the monogram has the email`() = runTest {
        assertNull(viewModel().state.value.avatar)
        assertEquals("anna@example.com", viewModel().state.value.email)

        profiles.avatarPath = OLD
        profiles.avatars[OLD] = byteArrayOf(1, 2)
        val state = viewModel().state.value

        assertEquals(OLD, state.avatarPath)
        assertTrue(state.avatar!!.bytes.contentEquals(byteArrayOf(1, 2)))
    }

    @Test
    fun `an avatar that cannot be downloaded leaves the monogram, which can still be changed`() = runTest {
        profiles.avatarPath = OLD

        val state = viewModel().state.value

        assertEquals(ProfileContent.Loaded(saved = null), state.profile)
        assertEquals(OLD, state.avatarPath)
        assertNull(state.avatar)
    }

    @Test
    fun `a picked picture is prepared as an avatar and saved straight away`() = runTest {
        val viewModel = viewModel()

        viewModel.onAvatarPicked("content://picked")

        assertEquals(listOf(PhotoKind.PERSON), photos.importedKinds)
        val path = photos.stored.keys.single()
        assertEquals(listOf("profile", "setAvatar"), profiles.calls)
        with(viewModel.state.value) {
            assertEquals(path, avatarPath)
            assertTrue(avatar!!.bytes.contentEquals("content://picked".encodeToByteArray()))
            assertEquals(AvatarMessage.SAVED, avatarMessage)
            assertFalse(avatarBusy)
            assertNull(retry)
        }
    }

    @Test
    fun `a photo taken with the camera is an avatar too, and a cancelled one changes nothing`() = runTest {
        val viewModel = viewModel()
        val cancelled = viewModel.newCaptureTarget()!!
        viewModel.onCaptureCancelled(cancelled)
        assertEquals(listOf(cancelled), photos.discarded)
        assertEquals(listOf("profile"), profiles.calls)

        val target = viewModel.newCaptureTarget()!!
        photos.captured[target] = "camera"
        viewModel.onAvatarCaptured(target)

        assertEquals(listOf(PhotoKind.PERSON), photos.importedKinds)
        assertEquals(listOf("profile", "setAvatar"), profiles.calls)
        assertEquals(photos.stored.keys.single(), viewModel.state.value.avatarPath)
    }

    @Test
    fun `a failed save keeps the saved avatar shown, and retrying sends the same picture`() = runTest {
        profiles.avatarPath = OLD
        profiles.avatars[OLD] = byteArrayOf(1)
        val viewModel = viewModel()
        profiles.failNext += ProfileFailure.NETWORK

        viewModel.onAvatarPicked("content://picked")

        val picked = photos.stored.keys.single()
        with(viewModel.state.value) {
            assertEquals("the server still has the old one", OLD, avatarPath)
            assertTrue(avatar!!.bytes.contentEquals(byteArrayOf(1)))
            assertEquals(AvatarFailure.NETWORK, avatarFailure)
            assertEquals(AvatarChange.Set(picked), retry)
            assertNull(avatarMessage)
        }

        viewModel.onAvatarRetry()

        assertEquals(listOf("profile", "avatar", "setAvatar", "setAvatar"), profiles.calls)
        assertEquals("the same picture, not imported again", 1, photos.imported.size)
        with(viewModel.state.value) {
            assertEquals(picked, avatarPath)
            assertNull(avatarFailure)
            assertNull(retry)
        }
    }

    @Test
    fun `removing clears the avatar once the server has, and a failure keeps it and can be retried`() = runTest {
        profiles.avatarPath = OLD
        profiles.avatars[OLD] = byteArrayOf(1)
        val viewModel = viewModel()
        profiles.failNext += ProfileFailure.SERVER

        viewModel.onAvatarRemoved()
        assertEquals(OLD, viewModel.state.value.avatarPath)
        assertEquals(AvatarFailure.SERVER, viewModel.state.value.avatarFailure)
        assertEquals(AvatarChange.Remove, viewModel.state.value.retry)

        viewModel.onAvatarRetry()

        assertEquals(listOf("profile", "avatar", "removeAvatar", "removeAvatar"), profiles.calls)
        with(viewModel.state.value) {
            assertNull(avatarPath)
            assertNull(avatar)
            assertEquals(AvatarMessage.REMOVED, avatarMessage)
        }
    }

    @Test
    fun `an unreadable image or no camera is said, and nothing is sent`() = runTest {
        photos.unreadable += "content://broken"
        val viewModel = viewModel()

        viewModel.onAvatarPicked("content://broken")
        assertEquals(AvatarFailure.UNREADABLE, viewModel.state.value.avatarFailure)
        assertNull(viewModel.state.value.retry)

        viewModel.onCameraUnavailable(viewModel.newCaptureTarget()!!)
        assertEquals(AvatarFailure.CAMERA_UNAVAILABLE, viewModel.state.value.avatarFailure)
        assertEquals(listOf("profile"), profiles.calls)
    }

    @Test
    fun `a picture gone from the device is said, without a retry that could not work`() = runTest {
        val viewModel = viewModel()
        profiles.missingPhotos += "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-000000000001.jpg"

        viewModel.onAvatarPicked("content://picked")

        assertEquals(AvatarFailure.NOT_ON_DEVICE, viewModel.state.value.avatarFailure)
        assertNull(viewModel.state.value.retry)
        assertNull(viewModel.state.value.avatarPath)
    }

    @Test
    fun `while a change is on its way another one waits`() = runTest {
        val viewModel = viewModel()
        profiles.gate = CompletableDeferred()

        viewModel.onAvatarPicked("content://first")
        assertTrue(viewModel.state.value.avatarBusy)
        viewModel.onAvatarPicked("content://second")
        viewModel.onAvatarRemoved()
        profiles.gate!!.complete(Unit)

        assertEquals(listOf("content://first"), photos.imported)
        assertEquals(listOf("profile", "setAvatar"), profiles.calls)
        assertFalse(viewModel.state.value.avatarBusy)
    }

    @Test
    fun `a profile that cannot be read can be read again`() = runTest {
        profiles.failNext += ProfileFailure.UNAVAILABLE
        val viewModel = viewModel()
        assertEquals(ProfileContent.Failed(ProfileFailure.UNAVAILABLE), viewModel.state.value.profile)

        viewModel.onSave()
        assertTrue("nothing to save before it is loaded", profiles.saved.isEmpty())

        viewModel.load()
        assertEquals(ProfileContent.Loaded(saved = null), viewModel.state.value.profile)
        assertNull(viewModel.state.value.saveFailure)
    }

    private companion object {
        const val OLD = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/10000000-0000-4000-8000-000000000009.jpg"
    }
}
