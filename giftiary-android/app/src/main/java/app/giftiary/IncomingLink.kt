package app.giftiary

import android.content.Intent
import app.giftiary.data.auth.RecoveryLink
import app.giftiary.data.auth.recoveryLinkOf
import app.giftiary.presentation.sharing.PendingJoin
import app.giftiary.presentation.sharing.pendingJoinOf

/** What an intent that opens the app asks for. */
sealed interface IncomingLink {
    data class Join(val join: PendingJoin) : IncomingLink
    data class Recovery(val link: RecoveryLink) : IncomingLink

    /** Anything else, including sign-up confirmations and provider sign-ins, which the auth callback completes. */
    data object Other : IncomingLink
}

// Recovery is decided before the generic auth callback, so a recovery code always leads to choosing
// a new password and is never exchanged like a sign-up confirmation.
fun incomingLinkOf(intent: Intent): IncomingLink =
    pendingJoinOf(intent)?.let(IncomingLink::Join)
        ?: recoveryLinkOf(intent)?.let(IncomingLink::Recovery)
        ?: IncomingLink.Other
