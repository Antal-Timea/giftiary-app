# Google Play Data safety: working draft

> **Status: draft answer sheet, not a submission.** Every fact here comes from
> `release-data-inventory.md` (develop `763adc5`, F5 merged). Play's categories and wording change;
> the owner must map each fact onto the **current** form and resolve every `[VERIFY]` before
> submitting anything.

## Facts from the app

| Question area | Observed in the app | Draft answer | Verify |
|---|---|---|---|
| Does the app collect or share user data? | Yes: an account (email) and user-entered content are sent to the backend | Collects: yes | `[VERIFY]` category mapping on the current form |
| Data encrypted in transit? | All backend traffic is HTTPS (Supabase client) | Yes | |
| Can users request deletion? | **In the app:** Settings → Delete account. The app first deletes the account's gift and person photos, then its profile photos, from Storage. The backend then hard-deletes the account and all data associated with it in one transaction: records, change log, profile, own email invites, sharing memberships and reservations, device proofs and the sign-in account. The deleting device removes that account's local data and signs out. Other devices that registered a device proof remove their local copy once their session is refused and they get the server's confirmation (see Device or other IDs). **On the web:** https://giftiary.app/account-deletion/ (live; deletion by email, `docs/operations/account-deletion-by-email.md`) | Yes, in-app and by web request | `[VERIFY]` deletion URL entered in Play Console; hosted backup retention |
| Email address (the user's) | Required for an email account; received from Google or Facebook when the user signs in with them and the provider shares it | Collected; required; account management | `[VERIFY]` optional vs required wording when a provider account has no email |
| Email address (someone the user invites) | Optional email invite to one of the user's own wishlists. The address is stored only while the invite is pending (at most 14 days), then erased; Resend sends that address one invitation email | Collected; optional; app functionality. Sent to an email processor, not shared | `[VERIFY]` whether data about non-users typed in by the user is declared under Email address, and whether the email processor counts as sharing |
| Name | Optional display name, shown to people the user shares wishlists with or invites. With Google or Facebook sign-in, the provider's name (and picture URL) is also stored by the auth service in the account record; the app does not use it | Collected, optional; app functionality | `[VERIFY]` whether user-initiated sharing with other users counts as "shared" on the current form, and whether the provider profile counts as collected "Name" / "Photos" |
| Sign-in with Google or Facebook | Browser-based OAuth through Supabase; no Google or Facebook SDK in the app | Google and Meta act as identity providers at the user's choice | `[VERIFY]` how the current form treats identity providers |
| User IDs | The account's id (UUID), used for sign-in and sync, stored on the server and on the device | Collected; required; app functionality / account management | `[VERIFY]` category mapping |
| Device or other IDs | F5 device proof: each signed-in install makes a random secret, keeps it on the device (excluded from backup) and registers its SHA-256 with the account (at most 20 per account). It is used only to confirm that a deleted account's data may be removed from that device. Deleted with the account. Not an Android/advertising/hardware id | Collected; required (automatic while signed in); account management / app functionality; not shared | `[VERIFY]` whether an app-generated per-install identifier belongs here on the current form (recommended: declare it) |
| Other user-generated content | People, occasions (incl. birthdays of others), gift ideas, gifts, prices, notes, product links, wishlists, wishes; stored on the device and synced. Deleted records are kept on the server as tombstones until the account is deleted | Collected; optional (typed in by the user); app functionality | `[VERIFY]` category ("other user-generated content" or similar) |
| Sharing data | Invite links (secret stored only as a hash), email invites (above), memberships, reservations; server only. Members see a wish only as available, reserved by them or reserved by someone else; the list owner never sees reservations (for a list kept for a person, only a neutral "Reserved") | Collected; optional; app functionality. Shown only to people the user invites | `[VERIFY]` category mapping; whether user-initiated sharing counts as "shared" |
| Reminder settings | Per account, on the device only (included in Android backup); removed when the account's local data is purged | Not sent to the backend | |
| Contacts | Not read from the device; people are typed in by the user | Not collected (as device contacts) | `[VERIFY]` that typed-in people are not a "Contacts" category answer |
| Calendar | Occasions (e.g. birthdays) are typed in, not read from the device calendar | Not collected (as calendar events) | `[VERIFY]` same question as Contacts |
| Photos | Three optional kinds, all chosen with the system photo picker or taken with the device's camera app (no storage or camera permission), re-encoded as JPEG with metadata removed: a gift photo (≤1600 px), a person photo (640 px square; usually depicts a third party) and the user's own profile photo (640 px square). Gift and person photos are on the device and in the private `gift-photos` bucket (owner-only folder, no public link), deleted from Storage when replaced, removed or their gift/person deleted (after sync). The profile photo is online only, in the separate private `profile-avatars` bucket; the old one is deleted right after a change, best effort. None is shown to other users or returned by sharing/invite RPCs. All are deleted before account deletion | Collected; optional; app functionality; not shared (processor only) | Re-confirm against any declaration already in Play Console |
| Location, files, audio, health, messages | Not accessed | Not collected | |
| Financial info | No payments. Gift prices are user notes about gifts, not the user's finances | Not collected | `[VERIFY]` that user-typed prices are not "purchase history" |
| App activity, analytics, crash logs, diagnostics | No analytics or crash SDK. Logs stay in the device's logcat. Play Console vitals only | Not collected by the app | `[VERIFY]` at each release that no SDK was added |
| Shared with third parties | Processors only: Supabase (auth, database, storage), Resend (confirmation, recovery and invitation emails). No advertising or analytics partners | Processing by service providers; not "sharing" | `[VERIFY]` how the current form treats processors |
| Data processed ephemerally | Shared lists of other users are read online and not stored on the device. The device-proof check sends the secret and stores nothing | n/a | `[VERIFY]` whether the check counts as ephemeral processing |

## Proposed declaration (draft, not submitted)

- **Collected:**
  - Personal info: Email address (required), Name (optional), User IDs (required).
  - Photos (optional).
  - Device or other IDs (required, automatic).
  - Other user-generated content (optional).
  - Purposes: app functionality and account management only.
- **Shared:** none. Resend and Supabase are processors.
- **Security:** encrypted in transit; users can request deletion (in app and at https://giftiary.app/account-deletion/).
- **Not collected:** location, contacts, calendar, financial info, health, messages, audio, files, app activity, web browsing, app info and performance.

## Before submitting

- [ ] Every `[VERIFY]` above resolved by the owner.
- [ ] Answers consistent with the published privacy policy (`docs/legal/privacy-policy-en.md`,
      `-hu.md`, effective 12 November 2026, covering device proofs and the F5 other-device
      removal) and with the account-deletion page. Both must be published on the website first
      (`docs/release-checklist.md`, Gate B).
- [ ] Deletion URL https://giftiary.app/account-deletion/ entered in Play Console.
- [ ] Account-deletion answer consistent with the current Play policy check in
      `docs/release-checklist.md`, Gate B.
