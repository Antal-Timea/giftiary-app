package app.giftiary.data.remote

import app.giftiary.data.remote.RemoteTestData.alice
import app.giftiary.data.remote.RemoteTestData.token
import app.giftiary.data.user.ActiveOwner
import app.giftiary.domain.model.EmailInvite
import app.giftiary.domain.model.RecipientInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.ReservationState
import app.giftiary.domain.repository.SharingException
import app.giftiary.domain.repository.SharingFailure
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
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant
import java.util.UUID

/** The sharing RPCs against a fake PostgREST: what is sent, how answers and refusals are read. */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class SupabaseWishlistSharingRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("null") }
    private val engine = MockEngine { request ->
        requests += request
        answer(request)
    }

    private val auth: Auth = createSupabaseClient(URL, PUBLISHABLE_KEY) {
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth
    private val owner = FakeActiveOwner(alice)
    private val repository = repository(owner)

    private fun repository(activeOwner: ActiveOwner) =
        SupabaseWishlistSharingRepository(AccountBoundClients(URL, PUBLISHABLE_KEY, engine), RemoteAccounts(auth), activeOwner)

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
    }

    @Test
    fun `an invite is created for the list, a week long, and its token read`() = runTest {
        answer = { respondJson("""{"invite_id":"$INVITE","token":"$TOKEN","expires_at":"2026-10-24T10:00:00.5+00:00"}""") }

        val invite = repository.createInvite(LIST)

        assertEquals(INVITE, invite.id)
        assertEquals(TOKEN, invite.token.value)
        assertEquals(Instant.parse("2026-10-24T10:00:00.5Z"), invite.expiresAt)
        assertRpc("create_wishlist_invite", """{"p_wishlist_id":"$LIST","p_valid_days":7}""")
    }

    @Test
    fun `a malformed token from the server is refused rather than shared`() = runTest {
        answer = { respondJson("""{"invite_id":"$INVITE","token":"short","expires_at":"2026-10-24T10:00:00+00:00"}""") }

        assertFailure(SharingFailure.SERVER) { repository.createInvite(LIST) }
    }

    @Test
    fun `the invite limit is told apart from other refusals`() = runTest {
        answer = { respondError("P0001") }

        assertFailure(SharingFailure.INVITE_LIMIT) { repository.createInvite(LIST) }
    }

    @Test
    fun `the access list holds invites and members only, whatever else the answer carries`() = runTest {
        answer = {
            respondJson(
                """{"invites":[{"invite_id":"$INVITE","created_at":"2026-10-17T10:00:00+00:00","expires_at":"2026-10-24T10:00:00+00:00"}],
                   "members":[{"member_user_id":"$MEMBER","display_name":"  Anna ","joined_at":"2026-10-18T10:00:00+00:00"},
                              {"member_user_id":"$OTHER","display_name":"   ","joined_at":"2026-10-18T11:00:00+00:00"},
                              {"member_user_id":"$THIRD","display_name":null,"joined_at":"2026-10-18T12:00:00+00:00","email":"x@example.com"}],
                   "reservations":[{"wish_id":"$INVITE"}]}""",
            )
        }

        val access = repository.access(LIST)

        assertEquals(listOf(INVITE), access.invites.map { it.id })
        assertEquals(listOf(MEMBER, OTHER, THIRD), access.members.map { it.userId })
        // Names are trimmed; a blank or missing one stays absent and nothing else stands in for it.
        assertEquals(listOf("Anna", null, null), access.members.map { it.displayName })
        assertRpc("list_wishlist_access", """{"p_wishlist_id":"$LIST"}""")
    }

    @Test
    fun `an email invite sends the list and the address and reads the invite back`() = runTest {
        answer = {
            respondJson(
                """{"invite_id":"$INVITE","recipient_email":"bob@example.com","state":"PENDING",
                   "created_at":"2026-10-17T10:00:00+00:00","expires_at":"2026-10-31T10:00:00+00:00"}""",
            )
        }

        val invite = repository.createEmailInvite(LIST, "bob@example.com")

        assertEquals(EmailInvite(INVITE, "bob@example.com", Instant.parse("2026-10-17T10:00:00Z"), Instant.parse("2026-10-31T10:00:00Z")), invite)
        assertRpc("create_recipient_invite", """{"p_wishlist_id":"$LIST","p_email":"bob@example.com"}""")
    }

    @Test
    fun `only pending email invites are listed, even from a server that still lists closed ones`() = runTest {
        answer = {
            respondJson(
                """[{"invite_id":"$INVITE","recipient_email":"bob@example.com","state":"PENDING","created_at":"2026-10-17T10:00:00+00:00","expires_at":"2026-10-31T10:00:00+00:00"},
                   {"invite_id":"$OTHER","recipient_email":"eve@example.com","state":"NOT_ACCEPTED","created_at":"2026-10-17T10:00:00+00:00","expires_at":"2026-10-31T10:00:00+00:00"}]""",
            )
        }

        val invites = repository.emailInvites(LIST)

        assertEquals(listOf("bob@example.com"), invites.map { it.email })
        assertRpc("list_recipient_invites", """{"p_wishlist_id":"$LIST"}""")
    }

    @Test
    fun `email invite refusals are typed by SQLSTATE`() = runTest {
        answer = { respondError("22023", message = "own email") }
        assertFailure(SharingFailure.INVALID_INPUT) { repository.createEmailInvite(LIST, "me@example.com") }

        answer = { respondError("P0001", message = "daily invite limit reached") }
        assertFailure(SharingFailure.INVITE_LIMIT) { repository.createEmailInvite(LIST, "bob@example.com") }

        answer = { respondError("P0002", status = HttpStatusCode.NotFound) }
        assertFailure(SharingFailure.UNAVAILABLE) { repository.emailInvites(LIST) }
    }

    @Test
    fun `invitations carry exactly the inbox payload, names trimmed and blank as none`() = runTest {
        answer = {
            respondJson(
                """[{"invite_id":"$INVITE","wishlist_name":"Birthday","owner_display_name":" Bea ","person_name":"Alex",
                    "created_at":"2026-11-04T10:00:00+00:00","expires_at":"2026-11-18T10:00:00+00:00","wishlist_id":"$LIST"},
                   {"invite_id":"$OTHER","wishlist_name":"Christmas","owner_display_name":"  ","person_name":null,
                    "created_at":"2026-11-04T10:00:00+00:00","expires_at":"2026-11-18T10:00:00+00:00"}]""",
            )
        }

        val invites = repository.myInvites()

        assertEquals(
            RecipientInvite(INVITE, "Birthday", "Bea", "Alex", Instant.parse("2026-11-04T10:00:00Z"), Instant.parse("2026-11-18T10:00:00Z")),
            invites.first(),
        )
        assertEquals(listOf(null, null), invites.drop(1).flatMap { listOf(it.ownerDisplayName, it.personName) })
        // Nothing beyond the payload is kept, not even a list id a server might add.
        assertEquals(
            listOf("id", "wishlistName", "ownerDisplayName", "personName", "createdAt", "expiresAt"),
            RecipientInvite::class.java.declaredFields.map { it.name }.filterNot { it.startsWith("$") },
        )
        assertRpc("get_my_invites", "{}")
    }

    @Test
    fun `accepting an invitation sends it with accept and returns the list joined`() = runTest {
        answer = { respondJson("""{"wishlist_id":"$LIST"}""") }

        assertEquals(LIST, repository.acceptInvite(INVITE))
        assertRpc("respond_to_invite", """{"p_invite_id":"$INVITE","p_accept":true}""")
    }

    @Test
    fun `declining an invitation sends it without accept`() = runTest {
        answer = { respondJson("{}") }

        repository.declineInvite(INVITE)
        assertRpc("respond_to_invite", """{"p_invite_id":"$INVITE","p_accept":false}""")
    }

    @Test
    fun `an invitation no longer open reads as unavailable`() = runTest {
        answer = { respondError("P0002", status = HttpStatusCode.NotFound) }

        assertFailure(SharingFailure.UNAVAILABLE) { repository.acceptInvite(INVITE) }
        assertFailure(SharingFailure.UNAVAILABLE) { repository.declineInvite(INVITE) }
    }

    @Test
    fun `withdrawing an email invite sends exactly its id`() = runTest {
        repository.revokeEmailInvite(INVITE)

        assertRpc("revoke_recipient_invite", """{"p_invite_id":"$INVITE"}""")
    }

    @Test
    fun `revoking and removing send exactly their ids`() = runTest {
        repository.revokeInvite(INVITE)
        repository.removeMember(LIST, MEMBER)

        assertEquals("/rest/v1/rpc/revoke_wishlist_invite", requests[0].url.encodedPath)
        assertEquals(Json.parseToJsonElement("""{"p_invite_id":"$INVITE"}"""), body(requests[0]))
        assertEquals("/rest/v1/rpc/remove_wishlist_member", requests[1].url.encodedPath)
        assertEquals(Json.parseToJsonElement("""{"p_wishlist_id":"$LIST","p_member_user_id":"$MEMBER"}"""), body(requests[1]))
    }

    @Test
    fun `leaving sends the list and only that`() = runTest {
        repository.leave(LIST)

        assertRpc("leave_wishlist", """{"p_wishlist_id":"$LIST"}""")
    }

    @Test
    fun `a failed leave is typed like every other call`() = runTest {
        answer = { throw IOException("offline") }
        assertFailure(SharingFailure.NETWORK) { repository.leave(LIST) }

        answer = { respondError("P0002", status = HttpStatusCode.NotFound) }
        assertFailure(SharingFailure.UNAVAILABLE) { repository.leave(LIST) }
    }

    @Test
    fun `reserving and releasing send the wish and read the server's plain answer`() = runTest {
        answer = { respondJson("\"RESERVED_BY_ME\"") }
        assertEquals(ReservationState.RESERVED_BY_ME, repository.reserve(WISH))
        assertRpc("reserve_wish", """{"p_wish_id":"$WISH"}""")

        requests.clear()
        answer = { respondJson("\"AVAILABLE\"") }
        assertEquals(ReservationState.AVAILABLE, repository.release(WISH))
        assertRpc("release_wish", """{"p_wish_id":"$WISH"}""")
    }

    @Test
    fun `reserving reports someone else's reservation as just that`() = runTest {
        answer = { respondJson("\"RESERVED_BY_OTHER\"") }

        assertEquals(ReservationState.RESERVED_BY_OTHER, repository.reserve(WISH))
    }

    @Test
    fun `releasing may report any of the three states`() = runTest {
        ReservationState.entries.forEach { state ->
            answer = { respondJson("\"${state.name}\"") }
            assertEquals(state, repository.release(WISH))
        }
    }

    @Test
    fun `an unexpected reservation answer is a server failure, never a guess`() = runTest {
        // Reserving can never leave a wish available.
        listOf("\"AVAILABLE\"", "\"RESERVED\"", "\"reserved_by_me\"", "null", "{}", "[]").forEach { body ->
            answer = { respondJson(body) }
            assertFailure(SharingFailure.SERVER) { repository.reserve(WISH) }
        }
        listOf("\"SOMETHING_NEW\"", "\"\"", "null", "1").forEach { body ->
            answer = { respondJson(body) }
            assertFailure(SharingFailure.SERVER) { repository.release(WISH) }
        }
    }

    @Test
    fun `reservation refusals are typed by SQLSTATE and carry nothing of the answer`() = runTest {
        answer = { respondError("P0002", status = HttpStatusCode.NotFound, message = "secret detail") }
        val unavailable = assertFailure(SharingFailure.UNAVAILABLE) { repository.reserve(WISH) }
        assertFailure(SharingFailure.UNAVAILABLE) { repository.release(WISH) }
        assertNull(unavailable.cause)
        assertFalse(unavailable.stackTraceToString().contains("secret detail"))

        answer = { respondError("40001", message = "try again") }
        assertFailure(SharingFailure.SERVER) { repository.reserve(WISH) }

        answer = { throw IOException("offline") }
        assertFailure(SharingFailure.NETWORK) { repository.reserve(WISH) }
        assertFailure(SharingFailure.NETWORK) { repository.release(WISH) }
    }

    @Test
    fun `redeeming sends the token and returns the joined list`() = runTest {
        answer = { respondJson("""{"wishlist_id":"$LIST"}""") }

        assertEquals(LIST, repository.redeem(InviteToken.of(TOKEN)!!))
        assertRpc("redeem_wishlist_invite", """{"p_token":"$TOKEN"}""")
    }

    @Test
    fun `every refusal of the server reads the same, as unavailable`() = runTest {
        answer = { respondError("P0002", status = HttpStatusCode.NotFound) }
        val token = InviteToken.of(TOKEN)!!

        assertFailure(SharingFailure.UNAVAILABLE) { repository.redeem(token) }
        assertFailure(SharingFailure.UNAVAILABLE) { repository.createInvite(LIST) }
        assertFailure(SharingFailure.UNAVAILABLE) { repository.access(LIST) }
        assertFailure(SharingFailure.UNAVAILABLE) { repository.revokeInvite(INVITE) }
        assertFailure(SharingFailure.UNAVAILABLE) { repository.removeMember(LIST, MEMBER) }
        assertFailure(SharingFailure.UNAVAILABLE) { repository.sharedWishlist(LIST) }
    }

    @Test
    fun `no answer is a network failure, anything else a server failure, both retryable`() = runTest {
        answer = { throw IOException("offline") }
        assertFailure(SharingFailure.NETWORK) { repository.sharedWishlists() }

        answer = { respondError("40001") }
        assertFailure(SharingFailure.SERVER) { repository.sharedWishlists() }

        answer = { respondJson("""[{"wishlist_id":"not-a-uuid"}]""") }
        assertFailure(SharingFailure.SERVER) { repository.sharedWishlists() }
    }

    @Test
    fun `a token that expired offline is waited for once, then the call is made again with the new one`() = runTest {
        val refreshed = token(alice, "refreshed")
        answer = { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer $refreshed") {
                respondJson("[]")
            } else {
                respondError("PGRST303", HttpStatusCode.Unauthorized, "JWT expired")
            }
        }
        launch {
            delay(3_000)
            auth.importSession(
                UserSession(refreshed, "r2", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = alice.toString())),
                autoRefresh = false,
                source = SessionSource.Storage,
            )
        }

        assertEquals(emptyList<Any>(), repository.sharedWishlists())
        assertEquals(listOf("Bearer ${token(alice)}", "Bearer $refreshed"), requests.map { it.headers[HttpHeaders.Authorization] })
    }

    @Test
    fun `without a new token in time the refusal stays a retryable failure, sent once`() = runTest {
        answer = { respondError("PGRST303", HttpStatusCode.Unauthorized, "JWT expired") }

        assertFailure(SharingFailure.SERVER) { repository.sharedWishlists() }
        assertEquals(1, requests.size)
    }

    @Test
    fun `other refusals are not waited on or sent again`() = runTest {
        answer = { respondError("P0002") }

        assertFailure(SharingFailure.UNAVAILABLE) { repository.sharedWishlist(LIST) }
        assertEquals(1, requests.size)
    }

    @Test
    fun `without an open account nothing is sent`() = runTest {
        val signedOut = repository(FakeActiveOwner(null))

        assertFailure(SharingFailure.SERVER) { signedOut.sharedWishlists() }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a failure carries neither the token nor the server's answer`() = runTest {
        answer = { respondError("P0002", message = "secret detail $TOKEN") }

        val failure = assertFailure(SharingFailure.UNAVAILABLE) { repository.redeem(InviteToken.of(TOKEN)!!) }

        assertNull(failure.cause)
        assertFalse(failure.toString().contains(TOKEN))
        assertFalse(failure.stackTraceToString().contains(TOKEN))
    }

    @Test
    fun `shared lists are read with a neutral absence for missing owner names`() = runTest {
        answer = { respondJson("""[{"wishlist_id":"$LIST","name":"Birthday","owner_display_name":null}]""") }

        val lists = repository.sharedWishlists()

        assertEquals(LIST, lists.single().id)
        assertEquals("Birthday", lists.single().name)
        assertNull(lists.single().ownerDisplayName)
        assertNull("an own list, or a server before person names, names no one", lists.single().personName)
        assertRpc("get_shared_wishlists", "{}")
    }

    @Test
    fun `a shared list summary carries its person's name only, blank as none`() = runTest {
        answer = {
            respondJson(
                """[{"wishlist_id":"$LIST","name":"Birthday","owner_display_name":"Bea","person_name":" Alex "},
                   {"wishlist_id":"$OTHER","name":"Christmas","owner_display_name":"Bea","person_name":" "}]""",
            )
        }

        assertEquals(listOf("Alex", null), repository.sharedWishlists().map { it.personName })
    }

    @Test
    fun `a shared list is read with its wishes and their reservation state`() = runTest {
        answer = {
            respondJson(
                """{"wishlist_id":"$LIST","name":"Birthday","owner_display_name":"Bea","wishes":[
                   {"wish_id":"$INVITE","name":"Lamp","note":"small","estimated_price_amount_minor":12990,
                    "estimated_price_currency_code":"HUF","product_url":"https://example.com","reservation_state":"RESERVED_BY_OTHER"},
                   {"wish_id":"$MEMBER","name":"Socks","note":null,"estimated_price_amount_minor":null,
                    "estimated_price_currency_code":null,"product_url":null,"reservation_state":"SOMETHING_NEW"}]}""",
            )
        }

        val list = repository.sharedWishlist(LIST)

        assertEquals("Bea", list.ownerDisplayName)
        assertNull("an own list, or a server before person names, names no one", list.personName)
        assertEquals(listOf(INVITE, MEMBER), list.wishes.map { it.id })
        assertEquals(Money(12990, "HUF"), list.wishes[0].estimatedPrice)
        assertEquals(ReservationState.RESERVED_BY_OTHER, list.wishes[0].reservation)
        // A state this version does not know is not guessed at.
        assertNull(list.wishes[1].reservation)
        assertRpc("get_shared_wishlist", """{"p_wishlist_id":"$LIST"}""")
    }

    @Test
    fun `a list kept for a person carries that person's name only, blank as none`() = runTest {
        answer = { respondJson("""{"wishlist_id":"$LIST","name":"x","owner_display_name":"Bea","person_name":" Alex ","wishes":[]}""") }
        assertEquals("Alex", repository.sharedWishlist(LIST).personName)

        answer = { respondJson("""{"wishlist_id":"$LIST","name":"x","owner_display_name":"Bea","person_name":"  ","wishes":[]}""") }
        assertNull(repository.sharedWishlist(LIST).personName)
    }

    @Test
    fun `an answer for another list or with half a price is refused`() = runTest {
        answer = { respondJson("""{"wishlist_id":"$OTHER","name":"x","owner_display_name":null,"wishes":[]}""") }
        assertFailure(SharingFailure.SERVER) { repository.sharedWishlist(LIST) }

        answer = {
            respondJson(
                """{"wishlist_id":"$LIST","name":"x","owner_display_name":null,"wishes":[{"wish_id":"$INVITE","name":"a",
                   "note":null,"estimated_price_amount_minor":5,"estimated_price_currency_code":null,"product_url":null,
                   "reservation_state":"AVAILABLE"}]}""",
            )
        }
        assertFailure(SharingFailure.SERVER) { repository.sharedWishlist(LIST) }
    }

    @Test
    fun `every call is made with the account's token`() = runTest {
        answer = { respondJson("[]") }

        repository.sharedWishlists()

        assertEquals("Bearer ${token(alice)}", requests.single().headers[HttpHeaders.Authorization])
        assertEquals("POST", requests.single().method.value)
    }

    @Test
    fun `the repository depends on remote access only, never on Room or sync`() {
        val dependencies = SupabaseWishlistSharingRepository::class.java.constructors.single().parameterTypes.toList()

        assertEquals(listOf(AccountBoundClients::class.java, RemoteAccounts::class.java, ActiveOwner::class.java), dependencies)
    }

    private fun assertRpc(function: String, expectedBody: String) {
        val request = requests.single()
        assertEquals("/rest/v1/rpc/$function", request.url.encodedPath)
        assertEquals("POST", request.method.value)
        assertEquals(Json.parseToJsonElement(expectedBody), body(request))
    }

    private fun body(request: HttpRequestData): JsonObject {
        val bytes = (request.body as OutgoingContent.ByteArrayContent).bytes()
        return Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    }

    private suspend fun assertFailure(expected: SharingFailure, block: suspend () -> Unit): SharingException {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("expected $expected but got $failure", failure is SharingException)
        assertEquals(expected, (failure as SharingException).failure)
        return failure
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(body, HttpStatusCode.OK, JSON_HEADERS)

    private fun MockRequestHandleScope.respondError(
        code: String,
        status: HttpStatusCode = HttpStatusCode.BadRequest,
        message: String = "not available",
    ) = respond("""{"code":"$code","message":"$message","details":null,"hint":null}""", status, JSON_HEADERS)

    private companion object {
        const val URL = "https://test.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_test-key"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
        const val TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_AbCdE"
        val LIST: UUID = UUID.fromString("10000000-0000-4000-8000-000000000001")
        val INVITE: UUID = UUID.fromString("30000000-0000-4000-8000-000000000001")
        val MEMBER: UUID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
        val OTHER: UUID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc")
        val THIRD: UUID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd")
        val WISH: UUID = UUID.fromString("20000000-0000-4000-8000-000000000001")
    }
}
