package app.giftiary.smoke

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.util.Log

/**
 * Requirement G: remote rows decode in the minified app. This build is release, shrunk and
 * obfuscated by R8 the same way, with only this instrumentation and [DecodingSmoke] added:
 *
 * ```
 * ./gradlew :app:installMinifiedSmoke
 * adb shell am instrument -w app.giftiary.minifiedsmoke/app.giftiary.smoke.MinifiedDecodingSmoke
 * ```
 *
 * It instruments the app itself, so nothing outside the minified app runs the code under test,
 * and no keep rule is needed: the manifest keeps this class, and everything it reaches is shrunk
 * as R8 decides. It finishes with RESULT_OK (`INSTRUMENTATION_CODE: -1`) and `smoke=PASSED`, or
 * with RESULT_CANCELED and the reason.
 */
class MinifiedDecodingSmoke : Instrumentation() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        val passed = try {
            val report = DecodingSmoke.run()
            result.putString("report", report)
            val problem = problemIn(report)
            result.putString("smoke", problem?.let { "FAILED: $it" } ?: "PASSED")
            problem == null
        } catch (failure: Throwable) {
            // Shrinking mistakes surface as linkage errors, which must be reported, not crash the run.
            result.putString("smoke", "FAILED: ${Log.getStackTraceString(failure)}")
            false
        }
        finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }

    /** What is wrong with the decoded rows, or null if every table decoded as served. */
    private fun problemIn(report: String): String? {
        val lines = report.split("\n")
        if (lines.size != 5) return "expected 5 lines, got ${lines.size}"
        // R8 renamed the reader, so the code that decoded the rows below is the minified code.
        if (lines[0] == "app.giftiary.data.remote.SupabaseRemoteReader") return "the reader was not minified"
        return missing(
            lines[1], "RemotePerson(", "name=Anna", "relationship=Sister", "version=3",
            "createdAt=2026-10-01T10:00:00.123456Z", "deletedAt=null",
        ) ?: missing(
            lines[2], "RemoteOccasion(", "type=BIRTHDAY", "day=29", "month=2", "year=null", "repeatsYearly=true",
            "personId=a0000000-0000-4000-8000-00000000000a",
        ) ?: missing(
            lines[3], "RemoteGiftIdea(", "status=PURCHASED", "estimatedPriceAmountMinor=1299000",
            "estimatedPriceCurrencyCode=HUF", "archivedAt=2026-10-03T09:00:00.000999Z",
            "occasionId=e0000000-0000-4000-8000-00000000000e",
        ) ?: missing(
            lines[4], "RemoteGift(", "direction=GIVEN", "givenAt=2024-02-29", "priceAmountMinor=1999",
            "priceCurrencyCode=EUR", "deletedAt=2026-10-04T10:00:00Z",
            "sourceGiftIdeaId=d0000000-0000-4000-8000-00000000000d",
        )
    }

    private fun missing(rows: String, vararg expected: String): String? =
        expected.firstOrNull { it !in rows }?.let { "$it missing from $rows" }
}
