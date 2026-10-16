package app.gifty.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.remote.AccountBoundClients
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.remote.RemoteTestData
import app.gifty.data.remote.RemoteTestData.alice
import app.gifty.data.remote.RemoteTestData.bob
import app.gifty.data.remote.RemoteTestData.token
import app.gifty.data.remote.SupabaseRemoteReader
import app.gifty.data.local.entity.RemoteState
import app.gifty.testing.FakeActiveOwner
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Pulling through the production reader against a fake PostgREST, so the rows are decoded from
 * real response JSON by the same client configuration the app uses, and the reader's own checks
 * (owner, repeated ids) are the ones that refuse bad responses.
 */
@RunWith(RobolectricTestRunner::class)
class SyncPullerReaderTest {

    private val tables = mutableMapOf<String, List<JsonObject>>()
    private val engine = MockEngine { request ->
        val table = request.url.encodedPath.substringAfterLast('/')
        // One page with everything, then the empty page that ends the read.
        val rows = if (request.url.parameters["id"] == null) tables[table].orEmpty() else emptyList()
        respond(JsonArray(rows).toString(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }
    private lateinit var database: GiftyDatabase
    private lateinit var puller: SyncPuller

    private val anna = UUID.fromString("a0000000-0000-4000-8000-00000000000a")
    private val idea = UUID.fromString("d0000000-0000-4000-8000-00000000000d")
    private val occasion = UUID.fromString("e0000000-0000-4000-8000-00000000000e")
    private val gift = UUID.fromString("f0000000-0000-4000-8000-00000000000f")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiftyDatabase::class.java).build()
        puller = SyncPuller(
            database, SupabaseRemoteReader(AccountBoundClients(URL, PUBLISHABLE_KEY, engine)), database.syncOperationDao(),
            database.personDao(), database.occasionDao(), database.giftIdeaDao(), database.giftDao(), database.wishlistDao(), database.wishDao(), FakeActiveOwner(alice),
        )
    }

    @After
    fun tearDown() {
        database.close()
        engine.close()
    }

    @Test
    fun `backend rows decoded from real responses are applied as the backend has them`() = runTest {
        tables["people"] = listOf(RemoteTestData.person(anna, alice))
        tables["occasions"] = listOf(RemoteTestData.occasion(occasion, alice))
        tables["gift_ideas"] = listOf(RemoteTestData.giftIdea(idea, alice, mapOf("occasion_id" to occasion.toString())))
        tables["gifts"] = listOf(RemoteTestData.gift(gift, alice, mapOf("price_amount_minor" to 1999L)))

        assertEquals(PullResult.Applied(4, 0, 0, 0, 0, 0), puller.pull(account()))

        val person = database.personDao().findById(anna, alice)!!
        assertEquals(3L, person.version)
        assertEquals(RemoteState.SYNCED, person.remoteState)
        // Postgres microseconds, kept to Room's milliseconds.
        assertEquals(Instant.parse("2026-10-01T10:00:00.123Z"), person.createdAt)
        val birthday = database.occasionDao().findById(occasion, alice)!!
        assertEquals(29 to 2, birthday.day to birthday.month)
        val scarf = database.giftIdeaDao().findById(idea, alice)!!
        assertEquals(1299000L to "HUF", scarf.estimatedPriceAmountMinor to scarf.estimatedPriceCurrencyCode)
        assertEquals(Instant.parse("2026-10-03T09:00:00Z"), scarf.archivedAt)
        val given = database.giftDao().findById(gift, alice)!!
        assertEquals(LocalDate.of(2024, 2, 29), given.givenAt)
        assertEquals(idea, given.sourceGiftIdeaId)
        assertEquals(Instant.parse("2026-10-04T10:00:00Z"), given.deletedAt)
    }

    @Test
    fun `a row of another account in a response fails the pull before anything is applied`() = runTest {
        tables["people"] = listOf(RemoteTestData.person(anna, alice), RemoteTestData.person(UUID.randomUUID(), bob))

        assertEquals(PullResult.Failed(RemoteFailure.SERVER), puller.pull(account()))

        assertNull(database.personDao().findById(anna, alice))
    }

    @Test
    fun `a repeated id in a response fails the pull before anything is applied`() = runTest {
        tables["people"] = listOf(RemoteTestData.person(anna, alice))
        tables["occasions"] = listOf(RemoteTestData.occasion(occasion, alice), RemoteTestData.occasion(occasion, alice))

        assertEquals(PullResult.Failed(RemoteFailure.SERVER), puller.pull(account()))

        assertNull(database.personDao().findById(anna, alice))
    }

    private fun account() = RemoteAccount.capture(alice, token(alice))

    private companion object {
        const val URL = "https://test.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_test-key"
    }
}
