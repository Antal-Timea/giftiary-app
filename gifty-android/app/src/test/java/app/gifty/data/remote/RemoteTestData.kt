package app.gifty.data.remote

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64
import java.util.UUID

/** Test rows shaped exactly like PostgREST returns them: snake_case, every column present. */
object RemoteTestData {

    val alice: UUID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    val bob: UUID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")

    /** An unsigned JWT for [subject]; only the server verifies signatures. */
    fun token(subject: UUID, secret: String = "signature-$subject"): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString("""{"sub":"$subject","role":"authenticated"}""".toByteArray())
        return "$header.$payload.$secret"
    }

    fun person(id: UUID, owner: UUID, overrides: Map<String, Any?> = emptyMap()): JsonObject = row(
        mapOf(
            "id" to id.toString(),
            "owner_user_id" to owner.toString(),
            "name" to "Anna",
            "relationship" to null,
            "photo_path" to null,
            "linked_user_id" to null,
            "version" to 3L,
            "created_at" to "2026-10-01T10:00:00.123456+00:00",
            "updated_at" to "2026-10-02T11:30:00.654321+00:00",
            "deleted_at" to null,
            "last_mutation_id" to "c0000000-0000-4000-8000-000000000001",
        ) + overrides,
    )

    fun occasion(id: UUID, owner: UUID, overrides: Map<String, Any?> = emptyMap()): JsonObject = row(
        mapOf(
            "id" to id.toString(),
            "owner_user_id" to owner.toString(),
            "person_id" to "a0000000-0000-4000-8000-00000000000a",
            "type" to "BIRTHDAY",
            "title" to null,
            "day" to 29,
            "month" to 2,
            "year" to null,
            "repeats_yearly" to true,
            "version" to 1L,
            "created_at" to "2026-10-01T10:00:00+00:00",
            "updated_at" to "2026-10-01T10:00:00+00:00",
            "deleted_at" to null,
            "last_mutation_id" to null,
        ) + overrides,
    )

    fun giftIdea(id: UUID, owner: UUID, overrides: Map<String, Any?> = emptyMap()): JsonObject = row(
        mapOf(
            "id" to id.toString(),
            "owner_user_id" to owner.toString(),
            "recipient_person_id" to "a0000000-0000-4000-8000-00000000000a",
            "occasion_id" to null,
            "name" to "Scarf",
            "status" to "PURCHASED",
            "estimated_price_amount_minor" to 1299000L,
            "estimated_price_currency_code" to "HUF",
            "actual_price_amount_minor" to null,
            "actual_price_currency_code" to null,
            "photo_path" to null,
            "product_url" to "https://example.com/scarf",
            "note" to null,
            "version" to 4L,
            "created_at" to "2026-10-01T10:00:00.5+00:00",
            "updated_at" to "2026-10-03T09:00:00+00:00",
            "archived_at" to "2026-10-03T09:00:00.000999+00:00",
            "deleted_at" to null,
            "last_mutation_id" to "c0000000-0000-4000-8000-000000000004",
        ) + overrides,
    )

    fun gift(id: UUID, owner: UUID, overrides: Map<String, Any?> = emptyMap()): JsonObject = row(
        mapOf(
            "id" to id.toString(),
            "owner_user_id" to owner.toString(),
            "direction" to "GIVEN",
            "giver_person_id" to null,
            "recipient_person_id" to "a0000000-0000-4000-8000-00000000000a",
            "occasion_id" to null,
            "source_gift_idea_id" to "d0000000-0000-4000-8000-00000000000d",
            "name" to "Scarf",
            "given_at" to "2024-02-29",
            "price_amount_minor" to 9007199254740993L,
            "price_currency_code" to "EUR",
            "photo_path" to "users/$owner/gifts/$id/photo.jpg",
            "product_url" to null,
            "note" to "Green",
            "version" to 2L,
            "created_at" to "2026-10-01T10:00:00+00:00",
            "updated_at" to "2026-10-04T12:00:00+02:00",
            "deleted_at" to "2026-10-04T12:00:00+02:00",
            "last_mutation_id" to "c0000000-0000-4000-8000-000000000005",
        ) + overrides,
    )

    fun wishlist(id: UUID, owner: UUID, overrides: Map<String, Any?> = emptyMap()): JsonObject = row(
        mapOf(
            "id" to id.toString(),
            "owner_user_id" to owner.toString(),
            "person_id" to "a0000000-0000-4000-8000-00000000000a",
            "name" to "Birthday",
            "version" to 2L,
            "created_at" to "2026-10-01T10:00:00+00:00",
            "updated_at" to "2026-10-02T10:00:00.250+00:00",
            "deleted_at" to null,
            "last_mutation_id" to "c0000000-0000-4000-8000-000000000006",
        ) + overrides,
    )

    fun wish(id: UUID, owner: UUID, overrides: Map<String, Any?> = emptyMap()): JsonObject = row(
        mapOf(
            "id" to id.toString(),
            "owner_user_id" to owner.toString(),
            "wishlist_id" to "70000000-0000-4000-8000-000000000007",
            "name" to "Lamp",
            "note" to "Brass",
            "estimated_price_amount_minor" to 9007199254740993L,
            "estimated_price_currency_code" to "HUF",
            "product_url" to "https://example.com/lamp",
            "photo_path" to "users/$owner/wishes/$id/photo.jpg",
            "version" to 5L,
            "created_at" to "2026-10-01T10:00:00+00:00",
            "updated_at" to "2026-10-05T12:00:00+02:00",
            "deleted_at" to "2026-10-05T12:00:00+02:00",
            "last_mutation_id" to "c0000000-0000-4000-8000-000000000007",
        ) + overrides,
    )

    fun mutation(mutationId: UUID, owner: UUID): JsonObject = row(
        mapOf(
            "mutation_id" to mutationId.toString(),
            "owner_user_id" to owner.toString(),
            "entity_type" to "people",
            "entity_id" to "a0000000-0000-4000-8000-00000000000a",
            "resulting_version" to 7L,
            "recorded_at" to "2026-10-08T18:51:23.123456+00:00",
        ),
    )

    private fun row(values: Map<String, Any?>): JsonObject = buildJsonObject {
        values.forEach { (key, value) ->
            when (value) {
                null -> put(key, JsonPrimitive(null as String?))
                is String -> put(key, value)
                is Number -> put(key, value)
                is Boolean -> put(key, value)
                else -> error("Unsupported test value $value")
            }
        }
    }
}
