# Release data inventory

> **Canonical evidence for privacy and Play answers.** It records what the app and its backend do
> as of develop `763adc5` (F5 merged), from the code, the manifest and `supabase/migrations`. The
> privacy policy (`privacy-policy-en.md` / `-hu.md`), the account-deletion page and
> `data-safety-draft.md` must agree with it. Update it in the same change as any feature that adds,
> moves or deletes user data.

## App surface

- **Permissions** (`AndroidManifest.xml`): `INTERNET`, `POST_NOTIFICATIONS` (reminders, asked on
  Android 13+). No camera, storage, contacts, calendar, location or account permissions. Photos come
  from the system photo picker or the camera app writing into a cache file through a non-exported
  `FileProvider`.
- **Exported components:**
  - `MainActivity` only. It handles the launcher, `giftiary://auth/callback`,
    `giftiary://auth/recovery`, `https://giftiary.app/join/` (autoVerify) and the older
    `giftiary://wishlist/join`.
  - The invite token travels in the URL fragment, which never reaches a server.
- **SDKs** (`app/build.gradle.kts`): AndroidX, Compose, Room, WorkManager, Hilt, coroutines,
  kotlinx.serialization, supabase-kt (auth, postgrest, storage) over Ktor/OkHttp, androidx.browser.
  No analytics, crash reporting, advertising, Firebase, Google or Facebook SDK.
- **Logs:**
  - `android.util.Log` warnings and debug lines (sync counts, failure kinds, class names) and supabase-kt's own error logs go to the device's logcat only.
  - Nothing is uploaded. No ids, emails or content are logged by app code.
- **Network:** HTTPS to the Supabase project only (Auth, PostgREST, Storage). Provider sign-in
  opens the provider's page in a browser Custom Tab.

## Data flows

| Data | Source | On the device | On the backend | Seen by other users | Third parties | Required? | Deleted when |
|---|---|---|---|---|---|---|---|
| Email address, password | sign-up | session only (no password) | Supabase Auth (password hashed) | no | Supabase; Resend delivers confirmation/recovery mails | required for email accounts | account deletion |
| Google/Facebook identity: email, name, picture URL, provider id | provider, at the user's choice | no | Supabase Auth identity/metadata (app never reads name or picture) | no | Google / Meta as independent controllers | optional | account deletion |
| Account id (UUID) | Auth | yes (owner of every row) | everywhere | members see the owner's display name, not the id | Supabase | required | account deletion |
| Display name | Edit profile | no | `profiles.display_name` | members and owners of shared lists; invite recipient sees it before accepting | Supabase | optional | cleared by the user or account deletion |
| Profile photo | photo picker / camera | only until uploaded | `profile-avatars/<uid>/<uuid>.jpg`, `profiles.avatar_path` | no | Supabase | optional | replaced/removed (old object deleted, best effort), account deletion |
| People, occasions, gift ideas, gifts (incl. prices, links, notes) | typed by the user | Room | rows per account; deletions kept as tombstones; `sync_mutations` ledger | no | Supabase | optional | tombstones and ledger until account deletion |
| Gift and person photos | photo picker / camera; metadata stripped | app files `gift-photos/<uid>/` | `gift-photos/<uid>/<uuid>.jpg` | no (never via sharing RPCs) | Supabase | optional | photo removed / item deleted (after sync), account deletion |
| Wishlists, wishes | typed by the user | Room | rows per account | members of a shared list see name, wishes, owner's display name, and for a person-list that person's name | Supabase | optional | tombstones until account deletion |
| Invite links | Share | token in the shared text only | hash, created/expiry/revoked times | whoever receives the link | Supabase | optional | account deletion |
| Email invites | owner types the invitee's address | no | `wishlist_recipient_invites`: address while pending (≤14 days), then erased; row kept with status/times | invitee (if they sign in with that confirmed address) sees list name, owner's display name, person name | Supabase; **Resend** sends one HU/EN email to the address (pg_net from the database) | optional | address: on close or expiry (cron, every minute); row: owner's account deletion (not the invitee's) |
| Memberships, reservations | joining, reserving | no (shared lists read online, not stored) | server-only tables | members see available / reserved by me / reserved by someone; owner never sees reservations (person-list owner: neutral "Reserved" only) | Supabase | optional | leave/remove, list deletion, either account's deletion |
| Device proof | generated per install while signed in (F5) | secret in `giftiary_device_proofs` prefs | SHA-256 hash linked to the account (`private.account_device_proofs`, ≤20 per account, one per install) | no | Supabase | automatic for signed-in installs (not optional) | hash: account deletion (cascade); secret: when the account's local data is purged |
| Device-proof check | device that lost an account's session | — | anonymous RPC with the secret over HTTPS; nothing stored; Supabase sees request metadata (IP, user agent) | no | Supabase | automatic | — |
| Reminder setting, last digest day | Settings | `giftiary_reminders` prefs, per account | no | no | none (notification built locally) | optional | account purge on the device |
| Onboarding seen | first start | `giftiary_onboarding` prefs | no | no | none | automatic | uninstall / clear data |
| Pending deletion marker | account deletion | `giftiary_account_deletion` prefs | no | no | none | automatic | when the deletion finishes |
| Technical data (IP, user agent, request times, auth/session logs) | every request | — | Supabase logs, per provider retention | no | Supabase | automatic | provider retention |
| Support / deletion emails | the user writes | — | Porkbun mailbox; private completion log | no | Porkbun | optional | 90 days after closure (correspondence), 1 year (log) |

Unused columns: `profiles.preferred_locale` is never written by the app. `people.linked_user_id`
is never set by the app (always null for new people); account deletion still unlinks it.

## Backup and device transfer

`backup_rules.xml` and `data_extraction_rules.xml` exclude the Supabase session
(`app.giftiary_preferences.xml`), the deletion marker, the device proofs, `gift-photos/` and the
Room database (with WAL/SHM/journal). Reminder and onboarding preferences are included.

## Account deletion

- **In the app** (Settings → Delete account), in this order:
  1. empty `gift-photos/<uid>/`, then `profile-avatars/<uid>/`;
  2. `delete_my_account()` deletes all rows, sharing on both sides and own email invites in one transaction, then `auth.users`, which cascades the profile, identities, sessions, refresh tokens and device proofs;
  3. the device removes the account's rows, photos, reminder state and proof, and signs out.
- **Other devices (F5):**
  - What happens: a device holding the account's data and a registered proof notices the session loss (refresh refused, up to about an hour while open, or at next start), asks with its secret, and on a definite "not live" purges only that account. Any network failure or ambiguous answer changes nothing.
  - Limitations: a device without a registered proof (an older build, never online while signed in, or past 20 installs) keeps the data until the app's data is cleared. A restore of the hosted database to before a proof's registration could make a live account's device purge its local copy; the server copy is unaffected.
- **By email:** `docs/operations/account-deletion-by-email.md`; the server result is the same, so the proofs go too.
- **Not deleted by account deletion:** email invites other users sent to the deleted user's address (their rows), hosted backups until rotated, provider logs.
