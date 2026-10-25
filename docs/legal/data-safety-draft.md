# Google Play Data safety: working draft

> **Status: draft answer sheet, not a submission.** The left column is what the repository shows
> the app doing. Play's categories and wording change; the owner must map each fact onto the
> **current** form and resolve every `[VERIFY]` before submitting anything.

## Facts from the app

| Question area | Observed in the app | Draft answer | Verify |
|---|---|---|---|
| Does the app collect or share user data? | Yes: an account (email) and user-entered content are sent to the backend | Collects: yes | `[VERIFY]` category mapping on the current form |
| Data encrypted in transit? | All backend traffic is HTTPS (Supabase client) | Yes | |
| Can users request deletion? | In the app: Settings → Delete account. The backend hard-deletes the account and all data associated with it (records, change log, profile, sharing memberships and reservations, sign-in account) in one transaction; the deleting device then removes that account's local data and signs out. Other devices keep their local copy until signed out. No external web deletion page exists yet | Yes, in-app; the web deletion resource is still needed | `[VERIFY]` web deletion URL (required before release); hosted backup retention |
| Email address | Required for an email account; received from Google or Facebook when the user signs in with them and the provider shares it | Collected; purpose: account management | `[VERIFY]` optional vs required wording |
| Name | Optional display name, shown to people the user shares wishlists with. With Google or Facebook sign-in, the provider's name (and picture URL) is also stored by the auth service in the account record; the app does not use it | Collected, optional; shared with other users at the user's request (display name only) | `[VERIFY]` whether "shared" applies to user-initiated sharing, and whether the provider profile counts as collected "Name" / "Photos" on the current form |
| Sign-in with Google or Facebook | Browser-based OAuth through Supabase; no Google or Facebook SDK in the app | Google and Meta act as identity providers at the user's choice | `[VERIFY]` how the current form treats identity providers |
| User IDs | The account's id (UUID), used for sign-in and sync, stored on the server and on the device | Collected; required; app functionality / account management | `[VERIFY]` category mapping |
| Other user-generated content | People, occasions (incl. birthdays of others), gift ideas, gifts, prices, notes, product links, wishlists, wishes; stored on the device and synced. Deleted records are kept on the server as tombstones until the account is deleted | Collected; optional (typed in by the user); purpose: app functionality | `[VERIFY]` category ("other user-generated content" or similar) |
| Sharing data | Invites (secret stored only as a hash), memberships, reservations; server only. Members see a wish only as available, reserved by them or reserved by someone else; the list owner never sees reservations | Collected; optional; app functionality. Shown only to people the user invites | `[VERIFY]` category mapping; whether user-initiated sharing counts as "shared" |
| Reminder settings | Per account, on the device only (included in Android backup); removed when the account is deleted | Not sent to the backend | |
| Contacts | Not read from the device; people are typed in by the user | Not collected (as device contacts) | `[VERIFY]` that typed-in people are not a "Contacts" category answer |
| Location, photos, files, audio, health, financial info | Not accessed | Not collected | `[VERIFY]` prices of gifts are user notes, not financial info |
| App activity, analytics, crash logs, device ids | No analytics or crash SDK; Play Console vitals only | Not collected by the app | `[VERIFY]` at each release that no SDK was added |
| Shared with third parties | Backend processor (Supabase) only; no advertising or analytics partners | Processing by a service provider; not "sharing" for advertising | `[VERIFY]` how the current form treats processors |
| Data processed ephemerally | Sharing reads (shared lists) are not stored on the device | n/a | |

## Before submitting

- [ ] Every `[VERIFY]` above resolved by the owner.
- [ ] Answers consistent with the published privacy policy (`docs/legal/privacy-policy-draft.md`
      once finalised).
- [ ] External web deletion resource published and its URL entered in Play Console.
- [ ] Account-deletion answer consistent with the current Play policy check in
      `docs/release-checklist.md`, Gate B.
