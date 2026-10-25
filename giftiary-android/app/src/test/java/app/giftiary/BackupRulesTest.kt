package app.giftiary

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * What a backup or a device transfer may carry, as the shipped rules say. Every channel (Auto
 * Backup on Android 8–11, cloud backup and device transfer on 12+) must leave out the same files.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val expected = setOf(
        // The Supabase session and PKCE verifier.
        "sharedpref" to "app.giftiary_preferences.xml",
        // The local database with its sync queue, in every file it may consist of.
        "database" to GiftiaryDatabase.NAME,
        "database" to "${GiftiaryDatabase.NAME}-wal",
        "database" to "${GiftiaryDatabase.NAME}-shm",
        "database" to "${GiftiaryDatabase.NAME}-journal",
        // An account deletion still to be finished on this device.
        "sharedpref" to "giftiary_account_deletion.xml",
    )

    @Test
    fun `auto backup leaves out the session and the database`() {
        assertEquals(expected, excludes(R.xml.backup_rules).getValue("full-backup-content"))
    }

    @Test
    fun `cloud backup and device transfer leave out exactly the same`() {
        val sections = excludes(R.xml.data_extraction_rules)

        assertEquals(expected, sections.getValue("cloud-backup"))
        assertEquals(expected, sections.getValue("device-transfer"))
    }

    @Test
    fun `reminder choices are still backed up`() {
        val reminders = "sharedpref" to "giftiary_reminders.xml"

        assertFalse(reminders in excludes(R.xml.backup_rules).getValue("full-backup-content"))
        excludes(R.xml.data_extraction_rules).values.forEach { assertFalse(reminders in it) }
    }

    @Test
    fun `backup stays allowed, so the rules are what decides`() {
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    /** The excluded (domain, path) pairs of each section of a rules file, keyed by section tag. */
    private fun excludes(resource: Int): Map<String, Set<Pair<String, String>>> {
        val parser = context.resources.getXml(resource)
        val sections = mutableMapOf<String, MutableSet<Pair<String, String>>>()
        var section: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "full-backup-content", "cloud-backup", "device-transfer" -> {
                    section = parser.name
                    sections.getOrPut(parser.name) { mutableSetOf() }
                }
                "exclude" -> sections.getValue(section!!) += parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path")
            }
        }
        return sections
    }
}
