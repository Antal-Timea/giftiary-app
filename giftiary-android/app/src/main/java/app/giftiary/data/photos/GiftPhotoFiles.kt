package app.giftiary.data.photos

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gift photos on this device, in app-private storage. A photo's file lives under the same path as
 * its Storage object, "<owner>/<photo id>.jpg", so one path names both. Excluded from backups.
 */
@Singleton
class GiftPhotoFiles @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val root = File(context.filesDir, DIRECTORY)

    /** A path for a new photo of [owner]. */
    fun newPath(owner: UUID): String = "$owner/${UUID.randomUUID()}.jpg"

    fun exists(path: String): Boolean = fileFor(path).isFile

    fun read(path: String): ByteArray? = fileFor(path).takeIf { it.isFile }?.readBytes()

    /** Written whole or not at all: a reader never sees half a photo. */
    fun write(path: String, bytes: ByteArray) {
        val file = fileFor(path)
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeBytes(bytes)
        check(temporary.renameTo(file) || (file.delete() && temporary.renameTo(file))) { "Could not store photo" }
    }

    fun delete(path: String) {
        fileFor(path).delete()
    }

    /** Every photo of [owner] on this device, with when it was last written. */
    fun list(owner: UUID): List<Pair<String, Long>> =
        File(root, owner.toString()).listFiles { file -> file.isFile && file.name.endsWith(".jpg") }
            .orEmpty()
            .map { "$owner/${it.name}" to it.lastModified() }

    /** For an account deleted from this device. */
    fun deleteAll(owner: UUID) {
        File(root, owner.toString()).deleteRecursively()
    }

    private fun fileFor(path: String): File {
        require(PATH.matches(path)) { "Not a gift photo path" }
        return File(root, path)
    }

    companion object {
        const val DIRECTORY = "gift-photos"

        /** The only shape this app writes; anything else (a URL, "..") is refused. */
        val PATH = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")

        fun ownerOf(path: String): UUID? = path.takeIf { PATH.matches(it) }?.substringBefore('/')?.let(UUID::fromString)
    }
}
