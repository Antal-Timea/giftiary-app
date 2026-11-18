package app.giftiary.data.photos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.remote.RemoteAccounts
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.data.remote.RemoteTestData.token
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.FakeGiftPhotoRemote
import app.giftiary.testing.MutableClock
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

/**
 * Loading a photo for display: the user's own (a wish photo synced from another device included)
 * is cached for offline use, while another account's photo on a shared wishlist is downloaded with
 * the member's own session and never stored on this device.
 */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class SharedPhotoLoadTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = GiftPhotoFiles(context)
    private val remote = FakeGiftPhotoRemote()
    private val database = Room.inMemoryDatabaseBuilder(context, GiftiaryDatabase::class.java).build()
    private val auth: Auth = createSupabaseClient("https://example.supabase.co", "publishable-key") {
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth
    private val activeOwner = FakeActiveOwner(alice)
    private val photos = GiftPhotoRepositoryImpl(
        context, files, GiftPhotoProcessor(), CameraCaptures(context, MutableClock(Instant.now())), database.photoTransferDao(),
        remote, RemoteAccounts(auth), activeOwner,
    )

    @Before
    fun signIn() = runTest {
        auth.importSession(
            UserSession(
                accessToken = token(alice),
                refreshToken = "refresh",
                expiresIn = 3600,
                tokenType = "bearer",
                user = UserInfo(aud = "authenticated", id = alice.toString()),
            ),
            autoRefresh = false,
            source = SessionSource.Storage,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a shared wish's photo is downloaded with the member's account and not written to this device`() = runTest {
        val bobs = "$bob/${UUID.randomUUID()}.jpg"
        remote.objects[bobs] = BYTES

        assertArrayEquals(BYTES, photos.loadSharedPhoto(bobs))
        assertFalse("another account's photo stays out of this account's files", files.exists(bobs))
        assertArrayEquals("each showing downloads it again", BYTES, photos.loadSharedPhoto(bobs))
        assertEquals(listOf("download $bobs", "download $bobs"), remote.calls)
    }

    @Test
    fun `a shared photo that Storage refuses, or a path that names no account, loads nothing`() = runTest {
        val refused = "$bob/${UUID.randomUUID()}.jpg"

        assertNull("Storage refuses or lacks it", photos.loadSharedPhoto(refused))
        listOf("https://example.com/a.jpg", "../$bob/x.jpg", "$bob/../${UUID.randomUUID()}.jpg").forEach { path ->
            assertNull(path, photos.loadSharedPhoto(path))
        }
        assertEquals("only the well-formed path reached Storage", listOf("download $refused"), remote.calls)

        remote.objects[refused] = BYTES
        remote.failure = RemoteFailure.NETWORK
        assertNull("offline", photos.loadSharedPhoto(refused))
    }

    @Test
    fun `signed out, nothing is loaded`() = runTest {
        activeOwner.ownerId.value = null

        assertNull(photos.loadSharedPhoto("$bob/${UUID.randomUUID()}.jpg"))
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun `the user's own wish photo, synced from another device, is downloaded once and then shown offline`() = runTest {
        val own = "$alice/${UUID.randomUUID()}.jpg"
        remote.objects[own] = BYTES

        assertArrayEquals(BYTES, photos.loadSharedPhoto(own))
        assertTrue(files.exists(own))

        remote.failure = RemoteFailure.NETWORK
        assertArrayEquals(BYTES, photos.loadPhoto(own))
        assertArrayEquals(BYTES, photos.loadSharedPhoto(own))
        assertEquals(listOf("download $own"), remote.calls)
    }

    @Test
    fun `the owner's own loader never reaches another account's photo`() = runTest {
        val bobs = "$bob/${UUID.randomUUID()}.jpg"
        remote.objects[bobs] = BYTES

        assertNull(photos.loadPhoto(bobs))
        assertTrue(remote.calls.isEmpty())
    }

    private companion object {
        val BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
    }
}
