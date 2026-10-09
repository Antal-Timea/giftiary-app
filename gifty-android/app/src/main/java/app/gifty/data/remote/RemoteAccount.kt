package app.gifty.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import java.util.UUID

/**
 * One account's access token, captured once before remote work starts and used for all of it.
 *
 * The global Auth session can change at any moment: a refresh, a sign-out, another account signing
 * in. Requests made for a captured account always carry exactly this token, so work started for
 * account A is never sent, continued or retried as account B. If the token expires meanwhile the
 * server rejects it, and the work fails instead of quietly switching sessions.
 */
class RemoteAccount private constructor(
    val ownerUserId: UUID,
    internal val accessToken: String,
) {

    // Keeps the token out of logs and crash reports.
    override fun toString(): String = "RemoteAccount(ownerUserId=$ownerUserId)"

    companion object {

        /**
         * Binds [accessToken] to [ownerUserId], refusing a token issued to any other account.
         *
         * @throws RemoteException [RemoteFailure.UNAUTHENTICATED] without a readable token,
         *   [RemoteFailure.ACCOUNT_MISMATCH] if the token belongs to another account.
         */
        fun capture(ownerUserId: UUID, accessToken: String?): RemoteAccount {
            val subject = accessToken?.let(::tokenSubject) ?: throw RemoteException(RemoteFailure.UNAUTHENTICATED)
            if (subject != ownerUserId) throw RemoteException(RemoteFailure.ACCOUNT_MISMATCH)
            return RemoteAccount(ownerUserId, accessToken)
        }

        /**
         * The account a JWT was issued to (its "sub" claim). Only the server can verify the
         * signature; this just makes sure the token is meant for the account it is used for.
         */
        private fun tokenSubject(token: String): UUID? = runCatching {
            val payload = String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)
            UUID.fromString(Json.parseToJsonElement(payload).jsonObject.getValue("sub").jsonPrimitive.content)
        }.getOrNull()
    }
}
