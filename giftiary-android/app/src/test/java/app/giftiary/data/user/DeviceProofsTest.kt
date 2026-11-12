package app.giftiary.data.user

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class DeviceProofsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val bob = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")

    @Test
    fun `a secret is 32 random bytes in the shape the server accepts`() {
        val secrets = List(50) { DeviceProofs.newSecret() }

        assertTrue(secrets.all { it.matches(Regex("[0-9a-f]{64}")) })
        assertEquals(50, secrets.toSet().size)
    }

    @Test
    fun `the hash is the SHA-256 of the secret's bytes, as the server computes it`() {
        // sha256 of 32 zero bytes, as `encode(sha256(decode(repeat('00', 32), 'hex')), 'hex')`.
        assertEquals("66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925", DeviceProofs.hashOf("00".repeat(32)))
    }

    @Test
    fun `hex does not follow the device's locale`() {
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertTrue(DeviceProofs.newSecret().matches(Regex("[0-9a-f]{64}")))
            assertTrue(DeviceProofs.hashOf("ab".repeat(32)).matches(Regex("[0-9a-f]{64}")))
        } finally {
            Locale.setDefault(default)
        }
    }

    @Test
    fun `what is kept survives a new process, and forgetting one account leaves the other`() {
        DeviceProofs(context).apply {
            saveSecret(alice, "a".repeat(64))
            saveSecret(bob, "b".repeat(64))
            setLastOwner(alice)
            markUnverified(alice)
            markUnverified(bob)
        }

        val next = DeviceProofs(context)
        assertEquals("a".repeat(64), next.secret(alice))
        assertEquals(alice, next.lastOwner())
        assertEquals(setOf(alice, bob), next.unverified())

        next.forget(alice)
        assertNull(next.secret(alice))
        assertNull(next.lastOwner())
        assertEquals(setOf(bob), next.unverified())
        assertEquals("b".repeat(64), next.secret(bob))

        next.markVerified(bob)
        assertEquals(emptySet<UUID>(), DeviceProofs(context).unverified())
    }
}
