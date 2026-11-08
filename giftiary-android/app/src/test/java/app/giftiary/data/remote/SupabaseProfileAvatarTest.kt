package app.giftiary.data.remote

import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.remote.RemoteTestData.alice
import app.giftiary.data.remote.RemoteTestData.bob
import app.giftiary.data.remote.RemoteTestData.token
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.OwnProfile
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.testing.FakeActiveOwner
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The avatar against a fake PostgREST and Storage: which object and row change in which order, what
 * a failure leaves behind, and that no other account's path is ever sent.
 */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class SupabaseProfileAvatarTest {

    private val now = Instant.parse("2026-11-08T12:00:00Z")

    /** The server: the profile row's avatar path, and the bucket's objects with when each was created. */
    private var avatarPath: String? = null
    private val objects = mutableMapOf<String, Instant>()
    private val uploaded = mutableMapOf<String, ByteArray>()
    private val failing = mutableSetOf<String>()

    private val log = mutableListOf<String>()
    private val requests = mutableListOf<HttpRequestData>()
    private val engine = MockEngine { request ->
        requests += request
        val path = request.url.encodedPath
        val step = when {
            path == "/rest/v1/profiles" && request.method == HttpMethod.Get -> "read"
            path == "/rest/v1/profiles" -> "update"
            path == "/storage/v1/object/list/$BUCKET" -> "list"
            path == "/storage/v1/object/$BUCKET" && request.method == HttpMethod.Delete -> "delete"
            path.startsWith("/storage/v1/object/authenticated/$BUCKET/") -> "download"
            path.startsWith("/storage/v1/object/$BUCKET/") -> "upload"
            else -> error("unexpected $path")
        }
        log += step
        if (step in failing) throw IOException("offline")
        answer(step, request)
    }

    private suspend fun MockRequestHandleScope.answer(step: String, request: HttpRequestData): HttpResponseData {
        val objectPath = request.url.encodedPath.substringAfter("$BUCKET/")
        return when (step) {
            "read" -> json("""[{"display_name":"Anna","avatar_path":${avatarPath?.let { "\"$it\"" } ?: "null"}}]""")
            "update" -> {
                avatarPath = body(request).jsonObject["avatar_path"]!!.jsonPrimitive.contentOrNull
                json("""[{"avatar_path":${avatarPath?.let { "\"$it\"" } ?: "null"}}]""")
            }
            "upload" -> {
                objects[objectPath] = now
                uploaded[objectPath] = request.body.toByteArray()
                json("""{"Key":"$BUCKET/$objectPath","Id":"x"}""")
            }
            "list" -> json(
                objects.entries.joinToString(prefix = "[", postfix = "]") { (path, created) ->
                    """{"name":"${path.substringAfter('/')}","id":"i","updated_at":null,"created_at":"$created","last_accessed_at":null,"metadata":{}}"""
                },
            )
            "delete" -> {
                body(request).jsonObject["prefixes"]!!.jsonArray.forEach { objects.remove(it.jsonPrimitive.content) }
                json("[]")
            }
            else -> respond(byteArrayOf(9, 9), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
        }
    }

    private val auth: Auth = createSupabaseClient(URL, KEY) {
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth
    private val files = GiftPhotoFiles(ApplicationProvider.getApplicationContext())
    private val clients = AccountBoundClients(URL, KEY, engine)
    private val repository = SupabaseProfileRepository(
        clients,
        RemoteAccounts(auth),
        FakeActiveOwner(alice),
        SupabaseProfileAvatarRemote(clients),
        files,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Before
    fun signIn() = runTest {
        auth.importSession(
            UserSession(token(alice), "refresh", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = alice.toString())),
            autoRefresh = false,
            source = SessionSource.Storage,
        )
    }

    @After
    fun tearDown() {
        engine.close()
        files.deleteAll(alice)
    }

    @Test
    fun `the name and the avatar path are read together, and the avatar downloaded as the account`() = runTest {
        avatarPath = OLD

        assertEquals(OwnProfile("Anna", OLD), repository.profile())
        assertArrayEquals(byteArrayOf(9, 9), repository.avatar(OLD))

        assertEquals("display_name,avatar_path", requests[0].url.parameters["select"])
        assertEquals("eq.$alice", requests[0].url.parameters["id"])
        assertEquals("/storage/v1/object/authenticated/$BUCKET/$OLD", requests[1].url.encodedPath)
        requests.forEach { assertEquals("Bearer ${token(alice)}", it.headers[HttpHeaders.Authorization]) }
    }

    @Test
    fun `a new avatar is uploaded, then named by the profile, then the old one is removed`() = runTest {
        avatarPath = OLD
        objects[OLD] = now.minusSeconds(60)
        files.write(NEW, byteArrayOf(1, 2, 3))

        assertEquals(NEW, repository.setAvatar(NEW))

        assertEquals(listOf("read", "upload", "update", "delete", "list"), log)
        assertArrayEquals(byteArrayOf(1, 2, 3), uploaded[NEW])
        assertEquals(NEW, avatarPath)
        assertEquals(setOf(NEW), objects.keys)
        assertEquals(setOf("avatar_path"), body(requests[2]).jsonObject.keys)
        assertFalse("the picture leaves the device once saved", files.exists(NEW))
    }

    @Test
    fun `a failed upload changes nothing, keeps the picture, and the same call works again`() = runTest {
        avatarPath = OLD
        objects[OLD] = now
        files.write(NEW, byteArrayOf(1))
        failing += "upload"

        assertFailure(ProfileFailure.NETWORK) { repository.setAvatar(NEW) }
        assertEquals(listOf("read", "upload"), log)
        assertEquals(OLD, avatarPath)
        assertTrue(files.exists(NEW))

        failing.clear()
        repository.setAvatar(NEW)
        assertEquals(NEW, avatarPath)
        assertEquals(setOf(NEW), objects.keys)
    }

    @Test
    fun `when the profile cannot be updated the old avatar stays, and a retry stores the same object again`() = runTest {
        avatarPath = OLD
        objects[OLD] = now
        files.write(NEW, byteArrayOf(1))
        failing += "update"

        assertFailure(ProfileFailure.NETWORK) { repository.setAvatar(NEW) }
        assertEquals(listOf("read", "upload", "update"), log)
        assertEquals(OLD, avatarPath)
        assertTrue("the old object is never removed before the row moves on", OLD in objects)
        assertTrue(files.exists(NEW))

        failing.clear()
        log.clear()
        repository.setAvatar(NEW)
        assertEquals(listOf("read", "upload", "update", "delete", "list"), log)
        assertEquals(setOf(NEW), objects.keys)
    }

    @Test
    fun `cleaning up afterwards may fail without failing the saved change`() = runTest {
        avatarPath = OLD
        objects[OLD] = now
        files.write(NEW, byteArrayOf(1))
        failing += "delete"

        assertEquals(NEW, repository.setAvatar(NEW))
        assertEquals(NEW, avatarPath)
    }

    @Test
    fun `leftovers of earlier failures are removed once a day old, never a fresh one`() = runTest {
        objects[STALE] = now.minusSeconds(2 * 24 * 3600)
        objects[FRESH] = now.minusSeconds(3600)
        files.write(NEW, byteArrayOf(1))

        repository.setAvatar(NEW)

        assertEquals(setOf(NEW, FRESH), objects.keys)
    }

    @Test
    fun `removing clears the profile first, then deletes the old object`() = runTest {
        avatarPath = OLD
        objects[OLD] = now

        repository.removeAvatar()

        assertEquals(listOf("read", "update", "delete", "list"), log)
        assertEquals(JsonNull, body(requests[1]).jsonObject["avatar_path"])
        assertEquals(null, avatarPath)
        assertTrue(objects.isEmpty())
    }

    @Test
    fun `a failed removal keeps the avatar and its object`() = runTest {
        avatarPath = OLD
        objects[OLD] = now
        failing += "update"

        assertFailure(ProfileFailure.NETWORK) { repository.removeAvatar() }

        assertEquals(OLD, avatarPath)
        assertTrue(OLD in objects)
    }

    @Test
    fun `another account's path, or anything not shaped like an avatar, is refused before sending`() = runTest {
        val foreign = "$bob/10000000-0000-4000-8000-000000000001.jpg"
        files.write(foreign, byteArrayOf(1))

        assertFailure(ProfileFailure.SERVER) { repository.setAvatar(foreign) }
        assertFailure(ProfileFailure.SERVER) { repository.avatar(foreign) }
        assertFailure(ProfileFailure.SERVER) { repository.avatar("$alice/../$bob/x.jpg") }
        assertFailure(ProfileFailure.SERVER) { repository.avatar("https://example.com/a.jpg") }

        assertTrue(requests.isEmpty())
        files.deleteAll(bob)
    }

    @Test
    fun `a picture no longer on the device is said so, and nothing is sent`() = runTest {
        val failure = runCatching { repository.setAvatar(NEW) }.exceptionOrNull()

        assertTrue("got $failure", failure is GiftPhotoUnavailableException)
        assertTrue(requests.isEmpty())
    }

    private suspend fun body(request: HttpRequestData) = Json.parseToJsonElement(request.body.toByteArray().decodeToString())

    private fun MockRequestHandleScope.json(text: String) = respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun assertFailure(expected: ProfileFailure, block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("expected $expected but got $failure", failure is ProfileException)
        assertEquals(expected, (failure as ProfileException).failure)
    }

    private companion object {
        const val URL = "https://test.supabase.co"
        const val KEY = "sb_publishable_test-key"
        const val BUCKET = "profile-avatars"
        val OLD = "$alice/10000000-0000-4000-8000-000000000001.jpg"
        val NEW = "$alice/10000000-0000-4000-8000-000000000002.jpg"
        val STALE = "$alice/10000000-0000-4000-8000-000000000003.jpg"
        val FRESH = "$alice/10000000-0000-4000-8000-000000000004.jpg"
    }
}
