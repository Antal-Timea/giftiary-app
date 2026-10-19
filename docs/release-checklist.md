# Release checklist

How a Giftiary build gets from `develop` to testers. Two gates, in order:

- **Gate A: direct-install smoke.** A signed build installed directly on a few devices, to prove
  the real flows against the hosted backend. No Play Console involvement.
- **Gate B: Play-distributed closed test.** The same kind of build distributed through a Google
  Play testing track to invited testers.

Items marked **human** need the owner, and items marked **authorised** additionally need an
explicit go-ahead for that specific action (hosted Supabase changes, hosted test data, store
submissions). Nothing in this file is done automatically.

## Versioning

- `versionCode` goes up by one for **every** build uploaded to Play; Play rejects a code it has
  already seen. Builds only installed directly do not count, but the next upload must still be
  higher than every code uploaded before.
- `versionName` is `0.1.0-beta.N` during closed testing, N counting the builds testers receive.
- The first build is `versionCode 1`, `versionName 0.1.0-beta.1`
  (`giftiary-android/app/build.gradle.kts`).

## Signing (human)

The repository holds no signing configuration and no key material; `.gitignore` refuses
`*.jks`, `*.keystore`, `*.p12`, `*.pem`, `keystore.properties` and `signing.properties`.

1. **Human:** create the upload key on a trusted machine and keep it and its passwords outside the
   repository (e.g. a password manager plus an offline backup). Losing it means asking Google to
   reset the upload key.
2. **Human:** decide where the build reads the key from (a `keystore.properties` next to the
   project, or environment variables on the build machine).
3. Release signing in Gradle is wired only after that decision, and must **fail the build** when
   a signed release is requested and the key is missing. A signed build must never silently come
   out unsigned.
4. For Gate B, enrol in **Play App Signing** with this key as the upload key (human).

Check on every release build: `apksigner verify --print-certs` shows the upload certificate, and
the file name has no `-unsigned` suffix.

## Gate A: direct-install smoke

### Hosted prerequisites (authorised)

- [ ] Auth redirect allow-list contains `giftiary://auth/callback` **and**
      `giftiary://auth/recovery` (the recovery path added in A1).
- [ ] Custom SMTP is configured, so confirmation and recovery emails reach addresses outside the
      project team. The built-in sender is restricted and rate-limited.
- [ ] "Confirm email" setting checked and recorded (recommended: on).
- [ ] Auth email rate limit is enough for the test group.
- [ ] Recovery and confirmation templates keep `{{ .ConfirmationURL }}`.
- [ ] Hosted migrations match `supabase/migrations` (`migration list --linked`, read-only).
- [ ] Dedicated test accounts exist: A (owner), B and C (guests), each with a distinct display
      name. Creating them creates hosted data.
- [ ] Only if a sign-in provider is enabled for this build (see Build):
  - [ ] Google: the Google Cloud OAuth consent screen is in production (or the testers are listed
        test users), scopes `openid`, `email`, `profile` only; a **Web application** OAuth client
        whose authorised redirect URI is the project's Supabase callback
        `https://<project-ref>.supabase.co/auth/v1/callback`; its client ID and secret entered in
        Supabase → Auth → Providers → Google (in the dashboard only, never in Git or chat).
  - [ ] Facebook: a Meta app with Facebook Login whose valid OAuth redirect URI is the same
        Supabase callback; privacy policy URL, data deletion instructions URL, category and icon
        set; app **Live**; `email` and `public_profile` only. App ID and secret entered in
        Supabase → Auth → Providers → Facebook.
  - [ ] Manual identity linking stays off; "Confirm email" is **on** (needed for safe linking of
        the same email).
  - [ ] No Android OAuth client, SHA-1 fingerprint or Facebook key hash is needed: the browser
        flow does not use them.

### Build

- [ ] Clean checkout of the commit to release; `git status` clean.
- [ ] `local.properties` (never committed) holds the **hosted** `supabase.url` and
      `supabase.publishableKey`; never a service-role key.
- [ ] Sign-in providers are off unless enabled on purpose: `auth.google.enabled=true` and
      `auth.facebook.enabled=true` in `local.properties` (or `-P`) only once that provider's
      hosted setup above is complete. Any other value leaves its button out of the build.
- [ ] Full unit suite, run on its own: `./gradlew :app:testDebugUnitTest`.
- [ ] Minified smoke (proves serialization survives R8), on a device or emulator:
      `./gradlew :app:installMinifiedSmoke`, then
      `adb shell am instrument -w app.giftiary.minifiedsmoke/app.giftiary.smoke.MinifiedDecodingSmoke`;
      expect `smoke=PASSED`.
- [ ] `./gradlew :app:lintDebug` has 0 errors.
- [ ] `./gradlew :app:assembleRelease` (R8), signed as described above.
- [ ] Install on a device without a previous Giftiary install, and on one with the previous build.

### Device smoke (authorised, hosted data)

Real devices: P1 (A), P2 (B), P3 (C), optionally P1′ (A's second device). Include one Android 13+
device (notification permission) and one older device.

| # | Area | Steps | Expected |
|---|---|---|---|
| S1 | Sign-up + confirmation | A signs up on P1, opens the email link on P1 | Signed in on Home |
| S2 | Sign-in / sign-out | Wrong password; right password; Settings → Sign out | Error text; Home; back to sign-in with data hidden |
| S3 | Recovery | Signed out: Forgot password → email → open on P1 → new password → Continue; sign in again | "Password changed", Home; only the new password works |
| S4 | Recovery link reused / expired | Open the same link again; an expired one | "Link not valid" |
| S5 | Recovery elsewhere / signed in | Open a link on P2; open one on P1 while signed in | "Link not valid"; "You're signed in", link still unused |
| S6 | Offline-first sync | P1 offline: add a person, occasion, idea, gift, wishlist with wishes; go online | Saved at once; synced; visible on P1′ after its sync |
| S7 | Edit and delete | Edit on P1′, delete on P1 | Both devices end up the same |
| S8 | Reminders | Settings → Reminders on; an occasion today or tomorrow | Permission asked once on 13+; digest notification; off cancels it |
| S9 | Display name | A sets a name; later clears it | B sees A's name; after clearing, "a Giftiary member" |
| S10 | Join by link | A shares an own, synced list; B opens the link signed out, then signs in | Join notice, then the shared list |
| S11 | Join by paste | C pastes the whole shared message into Join | Joins |
| S12 | Owner's access list | A opens Sharing | B and C by name, open invites, nothing about reservations |
| S13 | Revoke | A revokes the invite; someone tries it | "Invite isn't available" |
| S14 | Reserve | B reserves wish W | B "Reserved by you"; C "Reserved", no name; A sees no change |
| S15 | Lost race | C reserves W from a stale screen | "Someone else reserved this first." |
| S16 | Release | B releases W, confirms | Available for B and C |
| S17 | Leave / remove | B leaves; A removes C | B no longer has the list; C's open list becomes unavailable; C's reservation no longer counts |
| S18 | Owner deletes | A deletes a reserved wish, then the list | It disappears for guests; then the list is unavailable |
| S19 | No network | Airplane mode during reserve and during join | Retry message, nothing assumed; correct after reconnecting |
| S20 | Email clients | Open confirmation and recovery mails in Gmail (with Chrome) and one other client | The link reaches the app (possibly after an "Open with Giftiary" prompt) |

#### Sign-in providers (only those enabled for this build)

Use dedicated test accounts at Google and Facebook. Run O1–O11 on the release (R8) build.

| # | Area | Steps | Expected |
|---|---|---|---|
| O1 | Google, new | Signed out: Continue with Google, choose a Google account without a Giftiary account | Account created, Home, no data |
| O2 | Google, same email | Continue with Google using the email of A (confirmed email/password account) | Signed in as A, with A's data `[VERIFY linking]` |
| O3 | Unconfirmed squatter | Sign up with email/password on a test address, do **not** confirm; then Continue with Google with that address | The Google sign-in works; the unconfirmed password never gives access `[VERIFY]` |
| O4 | Facebook, new | Continue with Facebook with an account that has an email | Account created |
| O5 | Facebook without email | A Facebook account registered by phone, if available | A separate account; nothing breaks |
| O6 | Cancel at the provider | Deny consent / cancel on the provider page | "Sign-in was cancelled." `[VERIFY the error Supabase returns]` |
| O7 | Close the tab | Open a provider, close the Custom Tab or press Back | Back on sign-in, no progress, buttons usable; tapping again works |
| O8 | Double tap | Tap a provider button twice quickly | One tab opens |
| O9 | Offline | Airplane mode after the provider page, before returning | Network error; retry works after reconnecting |
| O10 | Join + provider | B opens an invite link signed out, then Continue with Google | Join notice, then the shared list |
| O11 | Switch account | Sign out, Continue with Google | Google's account chooser appears; another account opens without the first one's data |
| O12 | Process death | "Don't keep activities" on; Continue with Google; finish on the provider page | Signed in; a pending invite is lost by design (open the link again) |

Record the device, Android version, build and result of each row.

### Tester notes for Gate A

- Open recovery links on the phone that asked for them.
- An invite link may not be clickable in some messengers: paste the whole message into
  Wishlists → Shared with you → Join.
- Account deletion is on request (see Gate B for the policy check).

## Gate B: Play-distributed closed test

All of Gate A passed on the build to upload, then:

- [ ] `versionCode` incremented if anything was uploaded before; `./gradlew :app:bundleRelease`
      signed with the upload key.
- [ ] **Account deletion policy (human):** read the **current** Google Play account-deletion
      requirements before using any Play testing track, and satisfy them if they apply. Do not
      rely on an earlier reading of the policy. Today the app has no in-app deletion (planned as
      A2).
- [ ] **Privacy policy (human / legal):** finished from `docs/legal/privacy-policy-draft.md`,
      reviewed, published at a public URL, and the URL entered in Play Console.
- [ ] **Data safety (human):** form completed from `docs/legal/data-safety-draft.md`, every
      `[VERIFY]` item resolved first.
- [ ] Play Console app with package `app.giftiary` (permanent once uploaded), Play App Signing.
- [ ] Content rating questionnaire, target audience.
- [ ] App access: a dedicated demo account and instructions for Play review.
- [ ] Minimal store listing: name, short and full description, 512×512 icon, feature graphic,
      screenshots.
- [ ] Testing track (internal or closed) and the tester list.
- [ ] Release notes for testers, including the Gate A tester notes.

## Before a broader beta (not for the first closed test)

- **Account and data deletion (A2)**, if not already required by the policy check above.
- **Human Code / Simplification Audit.** Goal: simplification, not fewer lines. Look for
  unnecessary abstraction, wrappers and indirection, state or gate machinery heavier than its
  invariant needs, defensive branches for impossible cases, duplicated layers (e.g. parallel
  failure types, near-identical loaders), over-commenting, and tests that exist mainly to hold up
  accidental complexity. Keep the real invariants protected: account capture and the recovery
  gate, atomic sync queue writes, the ledger, owner scoping, owner reservation-blindness, token
  non-persistence and uniform "not available". First an audit-only pass with evidence, then small
  reviewed simplification changes, each with the full suite and mutation checks green.
- Primary navigation and Home (N1), invite text with instructions (S1), CI (Q1).
- Hosted backup and retention level confirmed (human).
