package app.gifty.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/** Postgres uuid, as text. */
object UuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("app.gifty.Uuid", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = parse(decoder.decodeString(), UUID::fromString)
}

/**
 * Postgres timestamptz as PostgREST writes it, e.g. 2026-10-08T18:51:23.123456+00:00. Parsed as
 * the absolute instant with its full (microsecond) precision, never as device-local time.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("app.gifty.Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant =
        parse(decoder.decodeString()) { OffsetDateTime.parse(it).toInstant() }
}

/** Postgres date, yyyy-MM-dd: a calendar date without time or zone. */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("app.gifty.LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = parse(decoder.decodeString(), LocalDate::parse)
}

private inline fun <T> parse(value: String, parse: (String) -> T): T = try {
    parse(value)
} catch (e: RuntimeException) {
    throw SerializationException("Unexpected value \"$value\"", e)
}
