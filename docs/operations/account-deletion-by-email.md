# Runbook: account deletion requested by email

> **Internal.** How a deletion request sent to support@giftiary.app is verified, carried out and
> checked. It promises nothing beyond what the hosted backend actually does. Nothing here has been
> run against the hosted project yet: do the dry run in "Before the first real request" first.

The public promise this runbook backs (privacy policy section 12, https://giftiary.app/account-deletion/):

- deletion can be requested by email, without the app;
- the requester proves control of the account's email address by replying to our confirmation
  email; by default no password, sign-in code or identity document is asked for;
- on the server the same is deleted as by Settings → Delete account, gift, person and profile
  photos included;
- done without undue delay after the confirmation; the requester is told what was done within one
  month of **receiving the request** (the confirmation does not start a new period).

What it cannot do: the local copies on the requester's devices (their database and photo files)
cannot be deleted remotely. The reply says so and tells them to clear the app's data or uninstall.

## Why `auth.users` alone is not enough

- **Photos** are Storage objects in two private buckets, each under the folder `<user id>/`, in
  the flat shape `<user id>/<uuid>.jpg` (no subfolders):
  - `gift-photos`: gift photos **and person photos** (migrations `20261101120000_gift_photos.sql`
    and `20261109100000_person_profile_photos.sql`);
  - `profile-avatars`: profile photos, kept apart from every other photo
    (`20261109100000_person_profile_photos.sql`).

  They have no foreign key to the user: deleting the user leaves every photo behind. Storage
  objects can be removed only through the Storage API (dashboard or HTTP). Storage's own trigger
  refuses `delete from storage.objects` in SQL, and even if it did not, the files would stay.
- **Rows:** the owner foreign keys to `auth.users` have no `ON DELETE`. Dashboard → Authentication →
  Delete user fails while the account owns data, and must not be used to "force" a deletion.
- **Sharing:** the account's memberships and reservations on *other* users' lists, and those on its
  own lists, have to go too.

`private.delete_account(uuid)` (migration `20261024120000_account_deletion.sql`, current body in
`20261107100000_recipient_invites.sql`) already deletes all rows and finally the `auth.users` row
(profile, identities, sessions and refresh tokens cascade) in one transaction. It also deletes the
account's own email invites (`wishlist_recipient_invites`) and unlinks other accounts' people that
pointed to it (`people.linked_user_id`). It is not callable by `anon`, `authenticated` or
`service_role`; it is owned by `postgres`, the role the dashboard SQL editor runs as.

The app's own order (`SupabaseAccountDeletionRemote.deleteAccount`, develop `7b88fc7`):

1. `gift-photos` folder `<uid>/` emptied (gift and person photos), page by page;
2. `profile-avatars` folder `<uid>/` emptied (profile photos);
3. `public.delete_my_account()`, which runs `private.delete_account(auth.uid())`.

If step 1 or 2 fails, the account and its rows stay and the whole deletion is retried. This runbook
follows the same order with admin tools: **both buckets must be empty for `<uid>` before the
account is deleted.**

## Tools and access needed

- Owner access to the Supabase dashboard of the hosted project (SQL editor, Storage, Auth settings).
- The support@giftiary.app mailbox (Porkbun).
- A private request log **outside the repository and outside Supabase** (for example a note in the
  owner's password manager or a private spreadsheet). One line per request, using the minimum
  fields under "Completion log retention" below. No email addresses, email bodies, photos,
  attachments or user-created content.

Never put the service-role key, the user's email address or their user id into the repository,
a chat or a ticket.

## Time limits

Legal basis (GDPR, Regulation (EU) 2016/679, as adopted):

- Art. 12(3): information on action taken on a request under Articles 15–22 is given "without
  undue delay and in any event within one month of receipt of the request". The period may be
  extended by two further months where necessary, "taking into account the complexity and number
  of the requests"; the data subject must be told of the extension, with reasons, within one month
  of receipt.
- Art. 12(4): if no action is taken, the data subject is told without delay and at the latest
  within one month of receipt, with the reasons and the possibility of a complaint to a
  supervisory authority and a judicial remedy.
- Art. 12(6): where there are reasonable doubts about the requester's identity, the controller may
  ask for additional information necessary to confirm it.
- EDPB Guidelines 01/2022 on the right of access (v2.0), para. 57: the date of receipt of the
  request by the controller triggers, as a rule, the one-month period.

How Giftiary applies this:

| Date | What |
|---|---|
| **R** = the day the request reaches support@giftiary.app | Log it. **Deadline D = R + 1 month** (same day number of the next month; if that day does not exist, the last day of that month). |
| R, at the latest the next working day | Send the confirmation email (step 2). Asking for it promptly is what keeps the deadline reachable. |
| Confirmation received | Delete without undue delay (steps 3–6); do not wait for D. The confirmation **does not** start a new month. |
| Before D | Tell the requester the outcome: deleted (step 7), or not deleted with the reason (e.g. no confirmation from the account's address) and the complaint/remedy information of Art. 12(4). |
| Extension (exceptional) | Only for the Art. 12(3) reasons, decided by the owner, and announced with reasons **before D**. A missing confirmation is not a reason to extend. |

Confirmation arriving after an Art. 12(4) answer: treat it as the request continued and delete
without undue delay; log both dates.

**Open legal point (owner):** whether the time spent waiting for an identity confirmation can be
excluded from the month. The EDPB guidelines address this in para. 157 (referred to from their
footnotes 40 and 44); that paragraph has not been read for this runbook. Until it is read and a
decision recorded, plan as if the month always runs from R.

## 1. Intake (day R)

1. Record the request in the log with its receipt date R and the deadline D (see "Time limits").
2. Do **not** act on the `From:` header alone: it can be forged.
3. Look the address up (read-only), in the SQL editor:

   ```sql
   select id, email, created_at, last_sign_in_at,
          (select array_agg(provider) from auth.identities i where i.user_id = u.id) as providers
   from auth.users u
   where lower(email) = lower('<address from the request>');
   ```

   - **One row:** go on with step 2.
   - **No row:** the address may differ from the account's (e.g. Google sign-in with another
     address). Ask which address or sign-in method they used. Do not confirm or deny other addresses.
   - **The requester says the account has no email address** (e.g. Facebook sign-in): see
     "Accounts without an email address" below. Do not invent another verification method.

## 2. Verification: confirmation email to the account's address

1. From support@giftiary.app, send a **new** email (not just a reply to the request) to the
   address stored in `auth.users.email`:

   > Subject: Giftiary – fióktörlés megerősítése / Confirm your Giftiary account deletion
   >
   > Kérést kaptunk a Giftiary-fiókod törlésére. Ha valóban törölni szeretnéd, válaszolj erre a
   > levélre ezzel: „Igen, töröljétek a fiókomat”. A törlés végleges, és nem vonható vissza.
   > Ha nem te kérted, hagyd figyelmen kívül ezt a levelet. Jelszót vagy kódot soha ne küldj.
   >
   > We received a request to delete your Giftiary account. If you want it deleted, reply to this
   > email with "Yes, delete my account". Deletion is permanent and cannot be undone. If you did
   > not ask for this, ignore this email. Never send us a password or code.

2. Proceed only when the confirmation arrives **as a reply in that thread, from that address**. Keep
   the reply in the mailbox until the deletion is completed and logged.
3. Never ask for a password, sign-in code, screenshot of the app, or identity document by default.
   If something looks wrong (several conflicting requests, an address you do not recognise as a
   real mailbox), stop and decide case by case.
4. No confirmation by a few days before D: send the Art. 12(4) answer before D (to the address the
   request came from: no deletion, because control of the account's address was not confirmed;
   a later confirmation from that address still works; complaint to the supervisory authority
   (NAIH) and judicial remedy are available). Log it.

## 3. Snapshot before deleting (read-only)

Note only the numbers in the log, to compare afterwards. Replace `<uid>` everywhere.

```sql
select 'people' t, count(*) from public.people where owner_user_id = '<uid>'
union all select 'occasions', count(*) from public.occasions where owner_user_id = '<uid>'
union all select 'gift_ideas', count(*) from public.gift_ideas where owner_user_id = '<uid>'
union all select 'gifts', count(*) from public.gifts where owner_user_id = '<uid>'
union all select 'gifts_with_photo', count(*) from public.gifts where owner_user_id = '<uid>' and photo_path is not null
union all select 'people_with_photo', count(*) from public.people where owner_user_id = '<uid>' and photo_path is not null
union all select 'wishlists', count(*) from public.wishlists where owner_user_id = '<uid>'
union all select 'wishes', count(*) from public.wishes where owner_user_id = '<uid>'
union all select 'wishlist_invites', count(*) from public.wishlist_invites where owner_user_id = '<uid>'
union all select 'wishlist_recipient_invites', count(*) from public.wishlist_recipient_invites where owner_user_id = '<uid>'
union all select 'wishlist_members', count(*) from public.wishlist_members where member_user_id = '<uid>' or owner_user_id = '<uid>'
union all select 'wish_reservations', count(*) from public.wish_reservations
          where reserver_user_id = '<uid>' or wishlist_id in (select id from public.wishlists where owner_user_id = '<uid>')
union all select 'sync_mutations', count(*) from public.sync_mutations where owner_user_id = '<uid>'
union all select 'profiles', count(*) from public.profiles where id = '<uid>'
union all select 'profiles_with_avatar', count(*) from public.profiles where id = '<uid>' and avatar_path is not null
union all select 'linked_from_other_people', count(*) from public.people where linked_user_id = '<uid>'
union all select 'gift_photos_objects', count(*) from storage.objects where bucket_id = 'gift-photos' and name like '<uid>/%'
union all select 'profile_avatars_objects', count(*) from storage.objects where bucket_id = 'profile-avatars' and name like '<uid>/%';
```

`gift_photos_objects` holds gift and person photos together; it may be larger than
`gifts_with_photo + people_with_photo` (an unsaved or replaced photo still waiting for cleanup).
`profile_avatars_objects` may likewise be more than one.

## 4. Delete every photo of the account (Storage API), before the account

Same order as the app. Do **not** go on to step 5 until both checks below return 0.

1. **Gift and person photos:** Dashboard → Storage → bucket `gift-photos` → folder `<uid>` →
   select every file → Delete. The browser shows a limited page of files: repeat until the folder
   is empty.
2. **Profile photos:** Dashboard → Storage → bucket `profile-avatars` → folder `<uid>` → select
   every file → Delete; repeat until the folder is empty.
3. Confirm with SQL:

   ```sql
   select
     (select count(*) from storage.objects where bucket_id = 'gift-photos'     and name like '<uid>/%') as gift_and_person_photos,
     (select count(*) from storage.objects where bucket_id = 'profile-avatars' and name like '<uid>/%') as profile_photos;
   -- both must be 0
   ```

Only the folder named exactly `<uid>` in these two buckets belongs to this account. Never delete
another folder or bucket.

## 5. Delete the account and its rows (one transaction)

In the SQL editor (runs as `postgres`):

```sql
select private.delete_account('<uid>'::uuid);
```

- Do not call `public.delete_my_account()` here: it deletes whoever is signed in and the SQL editor
  has no signed-in user (it raises `not authenticated`).
- Do not delete the user from Authentication → Users instead.
- If it raises an error, nothing was deleted (one transaction). Record the error and stop; the
  photos from step 4 are already gone, so finish within the deadline once the cause is fixed
  (repeat step 4 first: a device may have uploaded a photo in the meantime).

## 6. Leftover window, then the final check

A device of the account that still holds a valid access token can upload a photo for up to the
JWT expiry (Auth settings, default 3600 s) after step 5: Storage checks only the token. This holds
for both buckets (a gift or person photo into `gift-photos`, a profile photo into
`profile-avatars`). Rows cannot come back (the owner foreign keys reject them), but a photo object
can.

Wait at least the JWT expiry (recommended: the next day), then run:

```sql
select
  (select count(*) from auth.users where id = '<uid>')                          as users,
  (select count(*) from auth.identities where user_id = '<uid>')                as identities,
  (select count(*) from auth.sessions where user_id = '<uid>')                  as sessions,
  (select count(*) from auth.refresh_tokens where user_id = '<uid>')            as refresh_tokens,
  (select count(*) from public.profiles where id = '<uid>')                     as profiles,
  (select count(*) from public.people where owner_user_id = '<uid>')
   + (select count(*) from public.occasions where owner_user_id = '<uid>')
   + (select count(*) from public.gift_ideas where owner_user_id = '<uid>')
   + (select count(*) from public.gifts where owner_user_id = '<uid>')
   + (select count(*) from public.wishlists where owner_user_id = '<uid>')
   + (select count(*) from public.wishes where owner_user_id = '<uid>')
   + (select count(*) from public.wishlist_invites where owner_user_id = '<uid>')
   + (select count(*) from public.wishlist_recipient_invites where owner_user_id = '<uid>')
   + (select count(*) from public.sync_mutations where owner_user_id = '<uid>') as owned_rows,
  (select count(*) from public.wishlist_members where member_user_id = '<uid>' or owner_user_id = '<uid>') as memberships,
  (select count(*) from public.wish_reservations where reserver_user_id = '<uid>') as reservations,
  (select count(*) from public.people where linked_user_id = '<uid>')           as linked_from_other_people,
  (select count(*) from storage.objects where bucket_id = 'gift-photos'     and name like '<uid>/%') as gift_and_person_photos,
  (select count(*) from storage.objects where bucket_id = 'profile-avatars' and name like '<uid>/%') as profile_photos;
```

Every column must be 0. If `gift_and_person_photos` or `profile_photos` is not 0, repeat step 4
for that bucket's `<uid>` folder and run the query again. Any other non-zero value: stop and
investigate before telling the user it is done. Record in the log only that every counter was 0
(no file names, no content).

## 7. Close the request

1. Reply to the requester (in their language): the account and its server data, gift, person
   and profile photos included, are deleted; backups made by the hosting provider may still contain them until the
   normal backup cycle overwrites them; local copies on their devices remain until they clear the
   app's data or uninstall the app.
2. Log the completed and final-check dates. Set the closure date after the final check and the
   outcome notification; record the correspondence deletion due date as closure date + 90 days.
3. Apply the correspondence retention procedure below. The separate request log is retained
   for one year after closure, as described below; the correspondence still has its 90-day limit.

## Correspondence retention (owner decision, 4 November 2026)

The owner approved manual permanent deletion of the complete correspondence for closed deletion
requests 90 days after closure. Do not apply this rule to an open request.

- Record the closure date and due date (closure + 90 days) when closing each request. Track the
  due date so deletion is not silently delayed until a later general mailbox cleanup.
- On the due date, permanently delete the original request, confirmation, replies, completion
  notice and attachments from the managed Porkbun mailbox, including Sent, Archive, custom
  folders and Trash. Check any forwarded or otherwise stored Gmail copies and permanently delete
  them there too. Moving messages to Trash or merely removing a label is not sufficient.
- Confirm the action in the private request log without copying email bodies or attachments.
  Retain that separate log only until closure + one calendar year, using the fields below.
  Do not reset its expiry when the correspondence is deleted.
- This is manual. No scheduled mailbox cleanup has been configured or tested. Porkbun's filters
  apply on message arrival and do not periodically recheck stored messages for age.

Provider evidence: in the support reply supplied by the owner on 4 November 2026, Porkbun stated
that permanently deleted messages are not kept in its internal backups, and that stored messages
are not automatically deleted based on age. Preserve that provider reply separately as provider
documentation; it is not a user's deletion-request correspondence. This statement does not cover
Google's handling of Gmail copies or change any other provider's backup policy.

## Completion log retention (owner decision, 4 November 2026)

Purpose: document how a deletion request was handled and the result of the final check, without
retaining the deleted account's content. The owner approved the following minimum fields and a
retention period of one calendar year after closure. This is Giftiary's chosen retention period,
not a claim that GDPR prescribes a one-year period.

| Field | Content |
|---|---|
| Case ID | A unique internal reference, with no email address embedded. |
| Account UUID | The affected account's UUID; treat this as personal data even after account deletion. |
| Received date | When the request reached the mailbox. |
| Confirmation dates | When confirmation was sent and received. |
| Completed date | When deletion was carried out. |
| Final check | Date and result, e.g. every examined counter was 0; no user data copied. |
| Closure date | After the final check and outcome notification. |
| Notification date | When the requester was told the outcome. |
| Correspondence deletion | Due date = closure + 90 days, and actual permanent deletion date. |
| Log deletion due date | Closure + one calendar year; if the anniversary does not exist, use that month's last day. |

Store the log privately outside Git and Supabase, with access limited to the owner. Do not include
email addresses, message text, photos, attachments, gift/list content or credentials. Track each
entry's due date and manually delete the entire entry, including the UUID, when it expires.
Delete additional copies under the owner's control too. Do not extend the expiry merely because
the correspondence was deleted later. No automated deletion job is configured or tested.

## Maintenance: leftover photo folders

The privacy policy says leftover photos of deleted accounts are deleted through manual maintenance.
After every deletion (in-app ones included) the same race exists in both buckets, so check
regularly (recommended: monthly, and before every release):

```sql
select o.bucket_id, split_part(o.name, '/', 1) as folder, count(*) as objects, max(o.created_at) as newest
from storage.objects o
where o.bucket_id in ('gift-photos', 'profile-avatars')
  and not exists (select 1 from auth.users u where u.id::text = split_part(o.name, '/', 1))
group by 1, 2;
```

Delete each listed folder in its bucket in Dashboard → Storage as in step 4 and log the date. Do
not delete objects of existing accounts here: a gift or person photo may be uploaded before its row
is pushed, and the app itself cleans up an existing account's replaced profile photos.

## Accounts without an email address

What the code shows (develop, `fc9f28e`):

- Sign-in methods are email + password, Google and Facebook, the last two only when enabled in the
  build (`AuthModule`, `BuildConfig.AUTH_*_ENABLED`). There is no phone, SMS or anonymous sign-in
  in the app.
- Facebook sign-in is `auth.getOAuthUrl(Facebook, …)` in `SupabaseAuthRepository`: the app passes
  no scopes of its own, so the scopes are Supabase Auth's defaults for Facebook.
- `supabase/config.toml` (local development only) has no `[auth.external.facebook]` section. The
  CLI template documents `email_optional = false` per provider: "If enabled, it will allow the
  user to successfully authenticate when the provider does not return an email address." Local
  config says nothing about the hosted project.

What Supabase documents:

- Login with Facebook guide: email permission must be configured in the Facebook app ("required
  for Supabase Auth"); without it Facebook does not return the email, and Facebook returns one only
  if the user has a confirmed email on their Facebook account.
- Management API, auth config: a per-provider boolean `external_facebook_email_optional` exists.

So **whether a Giftiary account without an email can exist is decided by the hosted settings, not
proven either way here.** With email-optional **off**, a Facebook sign-in that returns no email
fails and creates no account (expected Supabase behaviour; confirm with test O5). With it **on**,
such accounts can be created and this runbook has no verification channel for them.

To check on the provider side (read-only, owner):

1. Supabase dashboard → Authentication → Sign In / Providers → **Facebook**: the "allow users
   without an email" / email optional setting (Management API field
   `external_facebook_email_optional`). Expected: **off**. Same check for **Google**
   (`external_google_email_optional`).
2. Same page: **Phone** provider and **Anonymous sign-ins** disabled.
3. Meta for Developers → the Facebook app → Use cases / Permissions: `email` and `public_profile`
   present (and approved if App Review applies).
4. SQL editor, read-only:
   ```sql
   select count(*) from auth.users where email is null or email = '';
   ```
   Expected: 0.

If 1–4 are as expected, every account has an email address and the email channel covers every
account. If any account without an email exists, or the setting is on: **stop**, answer the
requester within D that we cannot verify the request by email (Art. 12(4) wording), and have the
owner decide the method before acting. It must not default to passwords, codes or ID documents.

## Before the first real request (authorised, hosted)

With the owner's explicit go-ahead for that hosted action only, run this whole runbook once on a
throwaway account created for it (with at least one gift photo, one person photo, a profile photo
and a list shared with another test account), and record that every step behaves as written, in
particular:

- `select private.delete_account(...)` is allowed in the SQL editor and removes everything;
- the Storage dashboard deletes every object in a folder with more than one page of files, in
  both `gift-photos` and `profile-avatars`;
- the `profile-avatars` bucket exists on the hosted project (migration `20261109100000`);
- the step 6 query works on the hosted schema (column names of `auth.*` can change between
  Supabase versions; adjust the query if needed, never the deletion).

## Gaps (owner decisions or provider checks)

- **Privacy follow-up, not implemented: email addresses in closed recipient invites.** Today a
  `wishlist_recipient_invites` row keeps `recipient_email` after it is accepted, declined, revoked
  or expired, and after the list is deleted; it goes only with the owner's account
  (`private.delete_account`), never with the invitee's. The privacy policy (sections 3.5, 11, 12)
  describes exactly this. Follow-up: once an invite is closed, make its email address deletable or
  anonymizable later while the invite's audit row (list, owner, status, timestamps) is kept. When
  that ships, update the privacy policy's retention text to match.
- **Accounts without an email address:** run the provider checks in "Accounts without an email
  address". Only if such accounts can exist does a verification method need to be decided.
- **EDPB para. 157** (identity confirmation and the time limit) to be read and a decision recorded.
- **No admin tooling or automation:** steps 3–6 are manual in the dashboard; there is no tested
  script with the service-role key, and no scheduled leftover sweep.
- **Not yet run on the hosted project** (see the dry run above).
- **Backups:** the hosted backup schedule and retention for the current Supabase plan, and whether
  Storage objects are included, must be confirmed with Supabase.
- **Retention implementation:** correspondence retention is closure + 90 days; the completion log
  is closure + one year. Both are manual owner procedures, with no configured cleanup automation.
  Porkbun's support answer is recorded above.
- **Other devices** keep local data; nothing on the server can remove it.
