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

The repository holds no key material; `.gitignore` refuses `*.jks`, `*.keystore`, `*.p12`,
`*.pem`, `keystore.properties` and `signing.properties`.

1. **Human:** create the upload key on a trusted machine and keep it and its passwords outside the
   repository (e.g. a password manager plus an offline backup). Losing it means asking Google to
   reset the upload key.
2. **Human:** put the key's details in `giftiary-android/keystore.properties` (decided: a local
   file, not environment variables). See Setup below.
3. Gradle signs `assembleRelease` and `bundleRelease` (and anything that builds them, such as
   `build` or `installRelease`) with that key. If the file is missing, a field is missing or blank,
   or `storeFile` names no file, the `checkReleaseSigning` task **fails the build** before any
   release output is produced; the message names the problem, never a password. Wrong passwords
   or alias fail at the signing step. A release build never silently comes out unsigned or
   debug-signed. Debug builds, unit tests, lint and the debug-signed `minifiedSmoke` build type
   need no key.
4. For Gate B, enrol in **Play App Signing** with this key as the upload key (human).

### Setup

`giftiary-android/keystore.properties` (git-ignored; placeholders only, never commit real values):

```properties
storeFile=/path/outside/the/repo/upload-keystore.jks
storePassword=<store password>
keyAlias=<key alias>
keyPassword=<key password>
```

A relative `storeFile` is resolved from `giftiary-android/` (the Android project root), not from
`app/`. Keep the keystore itself outside the repository; an absolute path is clearest.

### Verification

Check on every release build:
- `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` shows the upload
  certificate;
- `keytool -printcert -jarfile app/build/outputs/bundle/release/app-release.aab` shows the same
  certificate for the bundle;
- the file name has no `-unsigned` suffix.

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
- [ ] Invitation email (migration `20261110100000`): `pg_net` and `pg_cron` installed, cron job
      `recipient-invites` active with a successful last run, and the Vault secret
      `resend_api_key` present (checked by a count query that never prints it). All three were
      confirmed on hosted on 2026-11-09 (recorded in the private review log); re-check before each
      release.
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
        Builds after 0.1.0-beta.6 also show the button only while the remote flag
        `facebook_login_enabled` is on (`docs/operations/feature-flags.md`).
  - [ ] Manual identity linking stays off; "Confirm email" is **on** (needed for safe linking of
        the same email).
  - [ ] No Android OAuth client, SHA-1 fingerprint or Facebook key hash is needed: the browser
        flow does not use them.

### Build

- [ ] Clean checkout of the commit to release; `git status` clean.
- [ ] `local.properties` (never committed) holds the **hosted** `supabase.url` and
      `supabase.publishableKey`; never a service-role key. The `checkReleaseBackend` task fails
      every release APK/AAB build (the same tasks `checkReleaseSigning` guards) if the URL is
      missing or not `https://` with a host, or if the key is missing; the message names the
      setting, never its value. Debug builds, unit tests, lint and `minifiedSmoke` (mock backend,
      never shipped) only warn.
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
| S12 | Owner's access list | A opens Sharing | B and C by name, open invite links and pending email invites, nothing about reservations |
| S13 | Revoke | A revokes the invite; someone tries it | "Invite isn't available" |
| S14 | Reserve | B reserves wish W | B "Reserved by you"; C "Reserved", no name; A sees no change |
| S15 | Lost race | C reserves W from a stale screen | "Someone else reserved this first." |
| S16 | Release | B releases W, confirms | Available for B and C |
| S17 | Leave / remove | B leaves; A removes C | B no longer has the list; C's open list becomes unavailable; C's reservation no longer counts |
| S18 | Owner deletes | A deletes a reserved wish, then the list | It disappears for guests; then the list is unavailable |
| S19 | No network | Airplane mode during reserve and during join | Retry message, nothing assumed; correct after reconnecting |
| S20 | Email clients | Open confirmation and recovery mails in Gmail (with Chrome) and one other client | The link reaches the app (possibly after an "Open with Giftiary" prompt) |
| S21 | Account deletion (**authorised, destructive**) | Only with a throwaway account D created for this row, and only with the owner's explicit go-ahead for that hosted deletion. D has data and a list shared with B. D: Settings → Delete account → confirm | "Account deleted", then the sign-in screen; D's data is gone from P1; D can no longer sign in; B no longer has D's list. A second device of D that registered a device proof (any build from develop `763adc5`) shows the sign-in screen and removes D's rows, photos and proof once its session is refused (token refresh, up to about an hour while open, or its next start) and it is online; its other accounts' data stays. A device without a registered proof keeps D's rows |
| S22 | Email invite | A: Sharing → Invite by email address → B's confirmed address → Send invite | "Invite saved. We'll email …"; the address is listed under Email invites as "Pending · until" a date 14 days ahead; B receives **one** email, in HU and EN, from `no-reply@giftiary.app`, with the privacy links and the expiry date, naming neither A nor the list |
| S23 | Repeat and refusals | A invites B's address again while it is pending; A invites A's own address; an invalid address | No second email; "This address can't be invited…"; "Enter a valid email address." |
| S24 | Recipient inbox and badge | B signs in on P2 with that confirmed address; C, with another address, opens Lists | B: the Lists destination (rail: Wishlists) carries the invitation badge, and Lists shows the invitation as a card "From <A's name>" above the joined lists; C sees no invitation |
| S25 | Accept | B taps Accept | The shared list opens and then appears among B's shared lists; A sees B as a member; the invite leaves A's Email invites |
| S26 | Decline / withdraw | A invites C's address and C declines; A invites another address and withdraws it before it is answered | C's card disappears and C gets no access; the withdrawn invite leaves A's list and the recipient's inbox; Accept or Decline on a card still on screen shows "This invitation is no longer available." |
| S27 | Address without an account | A invites an address that has no Giftiary account | The same email text as S22; nothing in the app or the email tells A whether an account exists |
| S28 | List kept for a person | A shares a list kept for person P with B (by link or by email) | B's Lists row and the list header read "For P · Shared by A"; B sees only that list's wishes: none of P's occasions, gifts, ideas or other lists |

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
- An invite link opens Giftiary directly only on a build whose signing certificate is in
  `https://giftiary.app/.well-known/assetlinks.json`. That statement lists only the Play app
  signing key, so a directly installed build (upload- or debug-signed) opens the
  giftiary.app/join/ page in the browser instead. Pasting the whole message into
  Wishlists → Shared with you → Join always works.
- An email invite sends the recipient one short HU/EN email naming neither the owner nor the
  list. The invitation itself appears only inside Giftiary, under Lists, for whoever signs in
  with that confirmed address within 14 days.
- Settings → Delete account deletes the account and its data at once and cannot be undone. The
  device it is done on is cleared at once. The account's other devices clear their copy themselves
  once their session is refused and they are online (up to about an hour while the app is open, or
  at its next start); a device that never registered a device proof (an older build, or never
  online while signed in) keeps it.

## Gate B: Play-distributed closed test

All of Gate A passed on the build to upload, then:

- [ ] `versionCode` incremented if anything was uploaded before; `./gradlew :app:bundleRelease`
      signed with the upload key.
- [ ] **Account deletion policy (human):** read the **current** Google Play account-deletion
      requirements before using any Play testing track, and satisfy them. Do not rely on an
      earlier reading of the policy. In the app: Settings → Delete account calls the live backend
      function `delete_my_account()`, which hard-deletes the account and its data, including its
      sharing on other users' lists; the app then removes that account's data from the device and
      clears the session. The **external web deletion resource** is published at
      https://giftiary.app/account-deletion/ (privacy policy section 12,
      `docs/legal/privacy-policy-en.md`; runbook `docs/operations/account-deletion-by-email.md`).
      That URL is entered in Play Console as the Data safety deletion resource. Still to do: the
      Meta data deletion instructions URL if Facebook sign-in ships.
- [ ] **Privacy policy (human):** the canonical texts are `docs/legal/privacy-policy-hu.md` and
      `docs/legal/privacy-policy-en.md`, effective 12 November 2026 (F6: device proofs and the F5
      other-device removal). Published byte-identically at https://giftiary.app/privacy/ (linked
      from Settings) by website PR #9 (`Antal-Timea/giftiary-website` master `a675918`), live since
      12 November 2026. Still to do: enter the URL in Play Console.
      (`docs/legal/privacy-policy-draft.md` is superseded; it is only the evidence behind them.)
- [x] **Data safety (human):** completed in Play Console (App content), confirmed by the owner on
      13 November 2026. Collected: Name (optional), Email address and User IDs (required), each for
      app functionality and account management; Photos and Other user-generated content
      (optional), for app functionality. No analytics, marketing or personalisation purpose;
      Contacts not selected; encrypted in transit; accounts by username and password or OAuth;
      deletion resource https://giftiary.app/account-deletion/, with no separate request to delete
      data without deleting the account. The App content page shows nothing needing attention.
      On 14 November 2026 the owner added Device or other IDs (the F5 device proof: collected,
      not shared, not ephemeral, required; app functionality and account management) and
      submitted the corrected declaration for review; Google's review is not recorded yet.
      Declaration and answer sheet: `docs/legal/data-safety-draft.md`. This is the App content
      declaration only, not a submission for production review.
- [x] **Privacy texts after F5 (human):** the 12 November policy and the account-deletion page
      changes in `docs/legal/account-deletion-page-f5-update.md` were published together in website
      PR #9 (`a675918`), deployed by the owner on 12 November 2026. Evidence: the reviewer's F6
      merge approval (REVIEW at `764ee68`), and a GET on 13 November 2026: `/privacy/` shows
      "12 November 2026" / "2026. november 12." and the device proof; `/account-deletion/` has the
      new HU/EN paragraphs and none of the old "not deleted automatically" ones.
- [x] Play Console app with package `app.giftiary` (permanent once uploaded), Play App Signing. Evidence: the owner supplied
      the Play app signing certificate (CURRENT_TASK at `94389a7`), and a Play-installed build
      was tested (CURRENT_TASK at `79fec2a`).
- [x] **Invite App Link statement published:** `https://giftiary.app/.well-known/assetlinks.json`
      for package `app.giftiary`, relation `delegate_permission/common.handle_all_urls`, the Play
      app signing key's SHA-256 only (website PR #2, `site/.well-known/assetlinks.json`, with an
      exact-match test in `tests/seo.test.mjs`). Add the upload key's fingerprint only if
      directly installed release builds must open links; never a debug one.
- [ ] **Invite App Link verified (human):** on a Play-installed build, `adb shell pm get-app-links
      app.giftiary` shows `giftiary.app: verified`, and tapping an invite in Gmail joins the list,
      both cold and with the app already running. Before the token-routing fix (`81cbfb3`), a
      Play-installed build opened Giftiary from a tapped link, but on the empty join screen
      (CURRENT_TASK at `79fec2a`). Neither the verification state nor a join after that fix is
      recorded yet.
- [ ] Content rating questionnaire, target audience.
- [ ] App access: a dedicated demo account and instructions for Play review.
- [ ] Minimal store listing: name, short and full description, 512×512 icon, feature graphic,
      screenshots.
- [ ] Testing track (internal or closed) and the tester list.
- [ ] Release notes for testers, including the Gate A tester notes.

## Before a broader beta (not for the first closed test)

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
