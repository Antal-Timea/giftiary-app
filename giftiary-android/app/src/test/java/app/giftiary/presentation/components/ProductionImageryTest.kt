package app.giftiary.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The four owner-approved images are the only bundled photography, in the format the app needs, and
 * nothing recovered from the reference boards has found its way into the app's resources.
 */
class ProductionImageryTest {

    // Unit tests run in the app module.
    private val resources = File("src/main/res")
    private val recovered = File("../../docs/design/approved/source/assets")

    private fun webp(name: String) = File(resources, "drawable-nodpi/$name.webp").readBytes()

    private fun ByteArray.fourCc(at: Int) = String(copyOfRange(at, at + 4), Charsets.US_ASCII)

    /** Whether a WebP file carries an alpha channel: an extended header with the alpha flag, or lossless with alpha. */
    private fun ByteArray.hasAlpha(): Boolean = when (fourCc(12)) {
        "VP8X" -> this[20].toInt() and 0x10 != 0
        "VP8L" -> this[24].toInt() and 0x10 != 0
        else -> false
    }

    @Test
    fun `the photographs are opaque WebP and the cut-outs keep their transparency`() {
        mapOf(
            "giftiary_home_hero" to false,
            "giftiary_onboarding_share" to false,
            "giftiary_gift_cutout_large" to true,
            "giftiary_gift_cutout_small" to true,
        ).forEach { (name, alpha) ->
            val bytes = webp(name)
            assertEquals("$name is RIFF", "RIFF", bytes.fourCc(0))
            assertEquals("$name is WebP", "WEBP", bytes.fourCc(8))
            assertEquals("$name alpha", alpha, bytes.hasAlpha())
        }
    }

    @Test
    fun `no recovered reference image is among the app's resources`() {
        val recoveredFiles = recovered.listFiles().orEmpty().filter { it.isFile }
        assertTrue("the recovered set is where the brief says", recoveredFiles.size == 20)
        val recoveredHashes = recoveredFiles.map { sha256(it) }.toSet()
        val recoveredNames = recoveredFiles.map { it.nameWithoutExtension }.toSet()

        resources.walkTopDown().filter { it.isFile }.forEach { file ->
            assertTrue("${file.path} is not a recovered file", sha256(file) !in recoveredHashes)
            assertTrue("${file.path} is not named after one", file.nameWithoutExtension !in recoveredNames)
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
}
