# Google Play Data safety: working draft

> **Status: draft answer sheet, not a submission.** The left column is what the repository shows
> the app doing. Play's categories and wording change; the owner must map each fact onto the
> **current** form and resolve every `[VERIFY]` before submitting anything.

## Facts from the app

| Question area | Observed in the app | Draft answer | Verify |
|---|---|---|---|
| Does the app collect or share user data? | Yes: an account (email) and user-entered content are sent to the backend | Collects: yes | `[VERIFY]` category mapping on the current form |
| Data encrypted in transit? | All backend traffic is HTTPS (Supabase client) | Yes | |
| Can users request deletion? | No in-app deletion yet; deletion only on request | `[VERIFY]` — depends on the deletion process the owner offers and on current Play policy | `[VERIFY]` |
| Email address | Required for the account | Collected; purpose: account management | `[VERIFY]` optional vs required wording |
| Name | Optional display name, shown to people the user shares wishlists with | Collected, optional; shared with other users at the user's request | `[VERIFY]` whether "shared" applies to user-initiated sharing on the current form |
| Other user-generated content | People, occasions (incl. birthdays of others), gift ideas, gifts, prices, notes, product links, wishlists, wishes | Collected; purpose: app functionality | `[VERIFY]` category ("other user-generated content" or similar) |
| Contacts | Not read from the device; people are typed in by the user | Not collected (as device contacts) | `[VERIFY]` that typed-in people are not a "Contacts" category answer |
| Location, photos, files, audio, health, financial info | Not accessed | Not collected | `[VERIFY]` prices of gifts are user notes, not financial info |
| App activity, analytics, crash logs, device ids | No analytics or crash SDK; Play Console vitals only | Not collected by the app | `[VERIFY]` at each release that no SDK was added |
| Shared with third parties | Backend processor (Supabase) only; no advertising or analytics partners | Processing by a service provider; not "sharing" for advertising | `[VERIFY]` how the current form treats processors |
| Data processed ephemerally | Sharing reads (shared lists) are not stored on the device | n/a | |

## Before submitting

- [ ] Every `[VERIFY]` above resolved by the owner.
- [ ] Answers consistent with the published privacy policy (`docs/legal/privacy-policy-draft.md`
      once finalised).
- [ ] Account-deletion answer consistent with the current Play policy check in
      `docs/release-checklist.md`, Gate B.
