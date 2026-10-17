package app.giftiary.presentation.sharing

import android.content.Intent
import android.net.Uri
import app.giftiary.domain.model.InviteToken

/** Must match the manifest filter. The token is the fragment, which browsers never send to a server. */
const val JOIN_LINK_SCHEME = "giftiary"
const val JOIN_LINK_HOST = "wishlist"
const val JOIN_LINK_PATH = "/join"

fun joinLink(token: InviteToken): String = "$JOIN_LINK_SCHEME://$JOIN_LINK_HOST$JOIN_LINK_PATH#${token.value}"

/**
 * What an opened link asks for: null when it is not a join link at all (e.g. the auth callback),
 * otherwise the join, with an [PendingJoin.InvalidLink] when its token is missing or malformed.
 */
fun pendingJoinOf(intent: Intent): PendingJoin? {
    if (intent.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    if (!isJoinLink(uri)) return null
    val token = uri.encodedFragment?.let(InviteToken::of) ?: return PendingJoin.InvalidLink
    return PendingJoin.Invite(token)
}

private fun isJoinLink(uri: Uri): Boolean =
    uri.scheme.equals(JOIN_LINK_SCHEME, ignoreCase = true) &&
        uri.host.equals(JOIN_LINK_HOST, ignoreCase = true) &&
        uri.path == JOIN_LINK_PATH &&
        uri.query == null

private val linkInText = Regex(
    "$JOIN_LINK_SCHEME://$JOIN_LINK_HOST$JOIN_LINK_PATH#(${InviteToken.FORMAT.pattern})(?![A-Za-z0-9_-])",
    RegexOption.IGNORE_CASE,
)

/**
 * The token of a pasted invite: the link itself, the whole message it was shared in, or the bare
 * token. Only the first link counts; anything that is not one of these yields null.
 */
fun inviteTokenOfPastedText(text: String): InviteToken? {
    val trimmed = text.trim()
    InviteToken.of(trimmed)?.let { return it }
    val token = linkInText.find(trimmed)?.groupValues?.get(1) ?: return null
    // The token itself is case-sensitive; only the scheme and host are matched loosely.
    return InviteToken.of(token)
}
