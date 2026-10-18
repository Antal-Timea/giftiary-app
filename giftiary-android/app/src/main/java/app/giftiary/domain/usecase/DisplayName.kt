package app.giftiary.domain.usecase

sealed interface DisplayNameInput {
    /** [name] is trimmed; null means no name at all. */
    data class Valid(val name: String?) : DisplayNameInput

    /** Line breaks, tabs and other control characters have no place in a name shown to others. */
    data object HasControlCharacters : DisplayNameInput
}

/**
 * A display name as typed, made ready to store: trimmed, and blank becoming no name, which the
 * server's check (`display_name is null or btrim(display_name) <> ''`) requires. There is no
 * length limit, as the server has none. Nothing ever stands in for a missing name.
 */
fun normalizeDisplayName(raw: String): DisplayNameInput {
    val trimmed = raw.trim()
    if (trimmed.any { it.isISOControl() || it == '\u2028' || it == '\u2029' }) return DisplayNameInput.HasControlCharacters
    return DisplayNameInput.Valid(trimmed.ifEmpty { null })
}
