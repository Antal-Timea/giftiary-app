# Giftiary privacy policy: working draft

> **Status: draft, not a legal document.** It collects what the app is observed to do, from the
> repository, so that a person with the right expertise can write the real policy. Every
> `[VERIFY]` item must be confirmed or replaced by the owner (and legal review where needed)
> before anything is published. Nothing here is a statement about region, retention, contracts
> or guarantees beyond what the code shows.

## 1. Facts observed in the app (repository evidence)

### Who and what

- **Controller / contact:** `[VERIFY: legal name, address, contact email of whoever operates Giftiary]`
- **Service provider:** the backend runs on Supabase (authentication and a PostgreSQL database).
  `[VERIFY: Supabase project region, data processing agreement and subprocessors]`

### Data the app handles

| Data | Source | Where it is kept | Who else can see it |
|---|---|---|---|
| Email address and password | entered at sign-up | Supabase Auth (the app never stores the password) | nobody through the app |
| Google or Facebook sign-in (optional): the account's email if the provider shares it, name and profile picture URL, provider account id | the provider, when the user chooses "Continue with Google/Facebook" | Supabase Auth, in the account record; the app does not show or use the name or picture, and never copies them into the display name | nobody through the app |
| Display name (optional) | entered in Settings | Supabase `profiles` | owners and members of wishlists the user shares or joins |
| People: names, relationship | entered by the user | on the device and synced to Supabase | nobody else |
| Occasions: day, month, optional year, type, title (e.g. **other people's birthdays**) | entered by the user | device + Supabase | nobody else |
| Gift ideas and gifts: names, direction, prices, notes, product links, who gave or received | entered by the user | device + Supabase | nobody else |
| Wishlists and wishes: names, notes, prices, product links | entered by the user | device + Supabase | for a shared own wishlist: its members (name, wishes, owner's display name) |
| Sharing: invites, memberships, reservations | created by using sharing | Supabase only (an invite's secret is stored only as a hash) | members see only whether a wish is available, reserved by them or reserved by someone else; the list owner never sees reservations |
| Reminder preference and reminder notifications | the user's choice | on the device only | nobody |

The app is designed to work offline: data is saved on the device first and synced when online.

### Signing in with Google or Facebook

- Optional, next to email and password. The sign-in page of the provider opens in a browser tab
  (Custom Tab); Giftiary never sees the provider password.
- Only the basic sign-in scopes are requested (email and profile); Giftiary does not call any
  Google or Facebook API and adds no Google or Facebook SDK.
- The provider learns that the user signed in to Giftiary, under the provider's own privacy policy.
  `[VERIFY: links to Google's and Meta's privacy policies]`
- An account with the same verified email as an existing Giftiary account is joined to it by
  Supabase. A Facebook account without a (verified) email becomes a separate Giftiary account.
  `[VERIFY: confirmed by hosted smoke before publication]`

### What the app does not do (as of this draft)

- No analytics, advertising identifiers, crash-reporting SDK or tracking. `[VERIFY before each release]`
- No access to location, contacts, camera, photos or files.
- Data is sent over HTTPS to the backend.

### Data about other people

Users record information about other people (names, birthdays, gifts). The policy must address
this. `[VERIFY: wording and legal basis, with legal review]`

### Device backups

The app's Android backup rules leave out the sign-in session, the local database and the marker of
an unfinished account deletion. Reminder settings are backed up. Data reaches a new device by
signing in and syncing; edits not yet synced stay on the old device.

### Deleting data and accounts

- **Deleting a record** (a person, gift, list, ...) in the app hides it everywhere, but the
  backend keeps it as a deleted row ("tombstone") so the user's other devices learn of the deletion,
  together with a log of applied changes (the sync ledger). Both are kept until the account is
  deleted; no automatic purge exists. Expired and revoked wishlist invites also stay as rows (with
  their expiry and revocation time) until the account is deleted.
- **Signing out** hides the account's data on that device but keeps it there, for when the same
  account signs in again. It is not deletion.
- **Deleting the account** (Settings → Delete account, after a confirmation):
  - on the backend, in one transaction: the account's people, occasions, gift ideas, gifts,
    wishlists, wishes, invites and change log; its memberships and reservations on other users'
    lists, and every membership and reservation on its own lists; its profile; and its sign-in
    account (Supabase Auth user with its sessions and linked Google or Facebook identities). The
    rows are deleted outright, not kept as tombstones;
  - on the device used for deleting: the account's data, its reminder settings and its pending
    reminders are removed, and the device is signed out. If the app stops part way, it finishes on
    the next start.
- **Other devices** of the deleted account are not cleared: their local database keeps the
  account's data. Once such a device's session is refused it is signed out, so the data is no
  longer shown in the app, but signing out does not delete it. Removing it automatically is a
  planned follow-up. The policy must not say that deletion removes data from all devices.
- **Backups of the backend:** whether and how long hosted backups or point-in-time recovery keep
  deleted data is `[VERIFY: hosted backup/PITR retention]`. The policy must not claim immediate
  removal from backups.

## 2. Items that need a decision or verification before publication

- `[VERIFY]` Legal basis for each processing purpose.
- `[VERIFY]` Retention wording: the facts are in "Deleting data and accounts" above (tombstones and
  ledger until account deletion, no automatic purge, hosted backup retention unknown). The final
  retention statement is the owner's decision.
- `[VERIFY]` Account deletion outside the app: a public web page (required by Google Play, and by
  Meta if Facebook sign-in ships) describing the in-app path and a request route for people
  without the app, e.g. a contact address: `[VERIFY: URL and contact]`. It must describe the same
  process as this policy.
- `[VERIFY]` Hosting region and international transfers.
- `[VERIFY]` Processor terms and subprocessors.
- `[VERIFY]` Age requirements / target audience.
- `[VERIFY]` User rights (access, correction, export, deletion) and how to exercise them.
- `[VERIFY]` Email provider used for confirmation and recovery emails (custom SMTP, R0).
- `[VERIFY]` Google and Meta as identity providers: how to describe them (separate controllers for
  their own sign-in), and whether Supabase's storage of the provider name and picture URL needs
  its own retention statement.
- `[VERIFY]` Meta requires a public **data deletion instructions** URL (or callback) for Facebook
  Login; it must describe the same deletion process as this policy.
- `[VERIFY]` Languages the policy is published in (the app is English and Hungarian).
- `[VERIFY]` Publication URL, effective date and change process.
