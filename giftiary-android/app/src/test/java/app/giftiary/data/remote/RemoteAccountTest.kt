package app.giftiary.data.remote

import app.giftiary.data.remote.RemoteTestData.alice
import app.giftiary.data.remote.RemoteTestData.bob
import app.giftiary.data.remote.RemoteTestData.token
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteAccountTest {

    @Test
    fun `a token issued to the owner is captured for that owner`() {
        val account = RemoteAccount.capture(alice, token(alice))

        assertEquals(alice, account.ownerUserId)
        assertEquals(token(alice), account.accessToken)
    }

    @Test
    fun `another account's token is refused, whatever owner is asked for`() {
        val failure = assertThrows(RemoteException::class.java) { RemoteAccount.capture(alice, token(bob)) }

        assertEquals(RemoteFailure.ACCOUNT_MISMATCH, failure.failure)
    }

    @Test
    fun `without a readable token there is no account`() {
        listOf(null, "", "not-a-jwt", "a.b.c", "x.${"{\"role\":\"anon\"}".encodeUrl()}.y").forEach { candidate ->
            val failure = assertThrows("$candidate", RemoteException::class.java) {
                RemoteAccount.capture(alice, candidate)
            }
            assertEquals(RemoteFailure.UNAUTHENTICATED, failure.failure)
        }
    }

    @Test
    fun `the token never appears in the account's text`() {
        val secretToken = token(alice, secret = "top-secret-signature")

        val text = RemoteAccount.capture(alice, secretToken).toString()

        assertFalse(text.contains(secretToken))
        assertFalse(text.contains("top-secret-signature"))
    }

    private fun String.encodeUrl() = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray())
}
