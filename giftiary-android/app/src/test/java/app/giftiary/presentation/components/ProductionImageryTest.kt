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
        resources.walkTopDown().filter { it.isFile }.forEach { file ->
            assertTrue("${file.path} is not a recovered file", sha256(file) !in RECOVERED.values)
            assertTrue("${file.path} is not named after one", file.nameWithoutExtension !in RECOVERED.keys)
        }
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        // The 20 reference images recovered with the approved designs (imagery brief, R4), by name and
        // SHA-256. They are pinned here because the images themselves are not kept in the public tree.
        val RECOVERED = mapOf(
            "3052cc239512b2d2e6caa8a4bd5c66ef" to "80e56d63043afdbc287c3da8ab69e7700d245db814bd480bc24c3fd70f9df9d9",
            "3ed40e70fa7c394b1786f59c8083f9c1" to "4c9f4cd2f91e820b5366f58a63109aa4d2e76d035310b2c78fc17d59bc1c223b",
            "420c5014712f6c132c679bd3aa83d880" to "93ae8ee37b2b60a0c3dad6327a482e855d4fd7d6aac25ea4f7def91dcad13dc8",
            "6bc91cab054f7621ae33e61b5b8d065a" to "d28c01576e0c98c1c4fd4b7c994cb34635f0a07bf364f7baeac620f0645cc249",
            "7429cd5fe9cf0c4fb9c21df7c5e6ff81" to "55c3d9413d4bf61889329cd3025f043fa3398c20c85c90d35e425e9b7ad67e04",
            "80f0ad15a7dc69b8bc2f48ac8529d74c" to "c7876d114c1b8cd8a06e0dce1cd069d276027ae5ff62a5f24270fcab565f686f",
            "8fc37a97848cb266d4557303d999573e" to "b47d4ed00b6bff78790a8b087097d4c543f0c09062c20e10eb87e36bec78829f",
            "8fe647e017b3aeabf65da66c827e37ec" to "a3cff07b1080e6a58dcfb6aa35825504c92572c7f7c4de372dc74a67104b6fb6",
            "907c567af15144b365b5ad83588ee016" to "d8f4875ead80f981d169d00ec38d46064056cb83511f3cbfd1beb9edfc90f277",
            "950fee72919c9436629e6a9f8a64ae7b" to "4dab988a50ef0e74495561ab28cac364d3649b400672c172a9b664708cd459a5",
            "9c1d16f8e573fb38f0a8778bcfef1ea6" to "ca1a7b5a63f2337751cb012e6a3dff1b7981845e07bb84cf5ccfd464a62e1551",
            "9e1ece56088af3c4d343da4d5fce29c9" to "fa6105f04d7e38fdb7ece467b5618917139f2d58be22c43d938fdf1f62fe8a01",
            "d7e715cda697c675bb7f533adcb7afaa" to "a16fe783dc591e0c16e94b3ac2594c166d021c13fb2e6525979e0a350d6e3f82",
            "e4a6b9dd3c1d10d9615f13b9910f0675" to "d646830bd156fe4b2aa29ef45a8507a5901121944f61e0c3c9d7083bad65682d",
            "e6a0fe8ce7cbf6875268b518a1fc0a57" to "77db448f4dbfb65059fdd25237d264d0a30bbba44e5f7e497affe610c5cda8ad",
            "e739a7f1f97e3868ec83f4338de0ee97" to "7624da7575927e7e30443f1d2e50779bd4299d4c72e838ed8ad10c6e6e3b4145",
            "e827698b1885bc19380b210f6422eeac" to "099d20341bdf88b3c855459f1c3032b9fcf3ed85a791e19dec055e14f90917a4",
            "e9fd1bc75df652b37c21b3618dcf52e6" to "513b41beb9ffac1cab599c267e9332940618d5009d71915f17f6e9aaad0629c3",
            "fb86ca6abed1123b5d457fb8fa8146bd" to "485d1781216d545c94bf86c04fb20275f88ef722d830e8fd7cfcf02f2bd389a3",
            "fefbf3ac449c357eedeb335454b2ca88" to "d50b0e5de5aec6a44c9b02371413cbeeca71b7930426ac578ce33f7075e00200",
        )
    }
}
