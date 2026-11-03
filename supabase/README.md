# Giftiary backend (Supabase)

The PostgreSQL schema, integrity rules and row level security for the data the Android app
stores offline, the server side of its sync protocol, and wishlist sharing. The Android app signs
in with Supabase Auth and syncs its own data with these tables: it pushes queued local changes and
pulls each table in full for the signed-in owner (`where owner_user_id = A and id > last order by
id`, served by the `(owner_user_id, id)` indexes). Shared wishlists are reached only through the
sharing functions below and are never stored on the device.

## How it maps to Android

Room on the device stays the source of truth for the user's data; the UI never waits for the
network. The server is where devices exchange that data later.

| Room entity | Table | Notes |
|---|---|---|
| `person` | `people` | |
| `occasion` | `occasions` | `day`/`month`/optional `year`, never a full date |
| `gift_idea` | `gift_ideas` | statuses `IDEA`, `PLANNED`, `PURCHASED` only |
| `gift` | `gifts` | `direction` `GIVEN`/`RECEIVED`; null giver/recipient = the signed-in user |
| `wishlist` | `wishlists` | optional `person_id`: a list kept for someone else; null = the user's own |
| `wish` | `wishes` | carries `owner_user_id`, always its wishlist's (same-owner foreign key) |
| — | `profiles` | one per auth user, created on sign-up; not synced |

Columns are the Room columns in snake_case. What does **not** cross over:

- `sync_operation` (the outbox) and `remoteState` are device-local bookkeeping. The server row
  itself is the remote state, so there is no sync table or remote-state column here.
- Wishlists and wishes (`20261016084000_private_wishlists.sql`) are owner-only synced rows
  following every rule below; the Android app syncs them like the other tables.
- The sharing tables (`20261017100000_wishlist_sharing.sql`) have no Room counterpart: shared
  lists, memberships and reservations are server-owned and read online only.

**Hosted project.** At the last recorded apply (W3, 2026-10-17) all six migrations in
`supabase/migrations` were applied to the hosted project and verified. Check
`migration list --linked` (read-only, with approval) before relying on this for a release.

## Rules the schema enforces

**Client-generated ids.** Android creates UUIDs offline and they are the server ids too. The
synced tables have no id default; a row arrives with its id.

**Ownership.** Every synced row has `owner_user_id`. RLS limits each user to their own rows, and
independently of RLS every reference to another row is a composite foreign key over
`(id, owner_user_id)`, so a row can never point at someone else's person, occasion or idea, even
with a guessed UUID. Ownership and ids cannot be changed after insert.

**Sensible references.** A person's occasion (say Anna's birthday) can only be the occasion of an
idea for Anna or a gift Anna gave or received; general occasions (no person, e.g. Christmas) fit
anything. An idea can be the source of at most one gift, and stays used even if that gift is
later soft-deleted: the deleted gift still records that the idea was given.

**Version and timestamps are the server's.** On insert `version = 1` and `created_at` /
`updated_at` are `now()`; every accepted update sets `version = old + 1` and
`updated_at = now()`. Whatever a client sends for these columns is ignored. Android keeps
`version = 0` for rows the server has never seen and adopts the server's version after a push.

**Every write is an identified mutation.** Each insert and update of `people`, `occasions`,
`gift_ideas`, `gifts`, `wishlists` and `wishes` must set `last_mutation_id` to a client-generated UUID, and an update
must bring a new one (keeping the row's current id is rejected). That id is the mutation's
**idempotency identity**; `version` is the **optimistic-concurrency state**. Clients update with
a compare-and-set, `update ... where id = X and version = expected`, so a write based on a stale
read matches no row instead of overwriting a newer one.

**The mutation ledger proves commits.** In the same transaction as every accepted write, a
trigger records the mutation in `public.sync_mutations` (mutation id, owner, table, row id,
resulting version, `recorded_at`). If a client's response is lost, it retries the same mutation;
a committed original makes the retry match no row (update) or hit a duplicate key (insert), and
finding the id in the ledger proves the original committed. Equal row content is never used as
proof. Mutation ids are single-use across all rows and tables (the ledger's primary key), and the
ledger is kept indefinitely. `tests/database/07_sync_concurrency.test.sql` shows that two copies
of the same mutation running at once commit at most once.

**Soft delete, never hard delete.** Deleting is `update ... set deleted_at = now()`, which bumps
the version, so the tombstone reaches other devices. Clients have no DELETE privilege. References
to soft-deleted rows stay valid: old gifts keep pointing at a deleted person. The one hard delete
is account deletion (below).

**Archived is not deleted.** Marking an idea as given sets `gift_ideas.archived_at` and creates a
gift with `source_gift_idea_id`. Both are ordinary writes.

**What deletes what.** Nothing between people, occasions, ideas and gifts cascades. On Android,
deleting a person soft-deletes their occasions in the same transaction and keeps their gifts and
ideas; the server sees those as ordinary updates. Deleting an auth user directly is blocked while
they own data (the owner foreign keys have no `ON DELETE`); only account deletion removes the data
first. Profiles cascade with their user. `people.linked_user_id` is not a feature yet and a check
keeps it null, so no client can point a person at another account.

Value checks mirror the Android domain: money as `bigint` minor units plus a three-letter
uppercase currency, set together or not at all; non-blank names; occasion ranges
(`month` 1–12, `day` 1–31, `year` within the `java.time.Year` range Android accepts); custom
occasions need a title and only they have one; birthdays, name days and Christmas always repeat;
one-time occasions need a year; given gifts name a recipient and received ones a giver, and the
other side is optional (both set is a gift between two other people). Exact calendar validity
(29 February) is left to Android.

## Access

| Role | profiles | people, occasions, gift_ideas, gifts, wishlists, wishes | wishlist_invites, wishlist_members, wish_reservations |
|---|---|---|---|
| `anon` | nothing | nothing | nothing |
| `authenticated` | read own; update display name, avatar path, locale | read, insert, update own rows; no delete | nothing: only through the sharing functions |
| `service_role` | select, insert, update, delete (server-side only, never in the app) | same | same |

`sync_mutations` is readable by `authenticated` for their own entries only, and writable by no
client role; `anon` has no access.

Helper functions live in the `private` schema and are not executable by client roles. Two are
`security definer`, both with an empty `search_path` and a single insert: the profile creation on
sign-up, and the ledger recording, which must not be possible for clients to imitate. A third,
`private.delete_account`, is reachable only through `public.delete_my_account()`.
`config.toml` sets `auto_expose_new_tables = false`, and the migrations revoke and grant every
table explicitly anyway, because hosted projects expose new tables by default. `service_role`
bypasses RLS but still needs table privileges, and with auto-expose off it gets none by default;
it receives the four data operations, not `ALL`.

Triggers fire without the caller holding `EXECUTE` on the trigger function, so the sign-up
trigger works even though Supabase Auth (`supabase_auth_admin`) cannot call it directly.

## Wishlist sharing

An owner shares any of their wishlists by invite, one kept for a person too (since
`20261104100000_share_person_wishlists`; members never get the person). A signed-in user who redeems
the invite becomes a member and can read the list and reserve its wishes. The three sharing tables
have row level security on and **no policies and no client privileges**, so clients cannot read
or write them directly and they are not part of Realtime. Everything goes through ten
`security definer` functions in `public`, executable by `authenticated` only, each with an empty
`search_path`:

| Function | For | Returns |
|---|---|---|
| `create_wishlist_invite(p_wishlist_id, p_valid_days = 7)` | owner | `{invite_id, token, expires_at}` |
| `revoke_wishlist_invite(p_invite_id)` | owner | nothing |
| `list_wishlist_access(p_wishlist_id)` | owner | open invites and members (with display names); never reservations |
| `remove_wishlist_member(p_wishlist_id, p_member_user_id)` | owner | nothing |
| `redeem_wishlist_invite(p_token)` | guest | `{wishlist_id}`; joining again is not an error |
| `leave_wishlist(p_wishlist_id)` | guest | nothing |
| `get_shared_wishlists()` | guest | `[{wishlist_id, name, owner_display_name}]` |
| `get_shared_wishlist(p_wishlist_id)` | guest | the list, `person_name` (who a list kept for a person is for; null otherwise, never their id) and its wishes, each with `reservation_state` |
| `reserve_wish(p_wish_id)` | guest | `RESERVED_BY_ME` or `RESERVED_BY_OTHER` |
| `release_wish(p_wish_id)` | guest | `AVAILABLE`, `RESERVED_BY_ME` or `RESERVED_BY_OTHER` |

- **Invites.** A token is 32 random bytes (pgcrypto), sent once to the owner as unpadded
  base64url; only its SHA-256 is stored. The app shares it as `https://giftiary.app/join/#<token>`
  (and still opens the older `giftiary://wishlist/join#<token>`); the fragment never reaches a server.
  An invite lasts 1–14 days (7 by default); a list has at most 5 open invites and 50 members, both
  serialized per list with a transaction-scoped advisory lock taken only by create and redeem.
- **Uniform refusal.** Every access failure (no such invite, list or member, an expired or
  revoked invite, no access, a full list) is `P0002 not available`, so nothing is learnt about
  which. `P0001` is the invite limit, `22023` invalid validity days, `40001` a contended reserve,
  `42501` no session.
- **Reservations are server-authoritative and owner-blind.** The owner is refused before
  reservations are looked at, and `list_wishlist_access` carries none. Members learn only
  `AVAILABLE`, `RESERVED_BY_ME` or `RESERVED_BY_OTHER`, never who. A reservation counts only while
  its wish, its list and the reserver's membership (the same `membership_id`) are active, so
  removing a member or a member leaving releases their reservations logically without touching
  rows; a stale reservation is taken over by the next member who reserves. Reservations never
  write to a wish and never appear in the mutation ledger.

`tests/database/09_wishlist_sharing.test.sql` covers the access matrix and
`10_wishlist_sharing_concurrency.test.sql` the concurrent cases (dblink).

## Account deletion

`public.delete_my_account()` takes no argument and deletes the caller (`auth.uid()`), in one
transaction: its reservations and memberships, every membership and reservation on its lists, its
invites, wishes, lists, gifts, ideas, occasions, people and ledger entries, and last its
`auth.users` row, with which its profile, identities, sessions and refresh tokens cascade. Rows are
deleted outright, not tombstoned: the account has no device left to sync to. Any error rolls
everything back. A token issued before the deletion stays valid until it expires but reads nothing
and cannot recreate rows (the owner foreign keys reject it), and its refresh token is gone. Calling
again is a no-op. Other devices of the account keep their local copy until they notice the account
is gone.

The function runs as `postgres`, which Supabase grants `DELETE` on `auth.users`; `anon` and
`service_role` cannot execute it. `tests/database/11_account_deletion.test.sql` covers it.

## Local development

Needs the [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started)
(used so far as `npx --yes supabase@2.121.0 …`) and Docker. From the repository root:

```bash
supabase start      # local stack; prints the local URL and development keys
supabase db reset   # recreate the database from supabase/migrations
supabase test db    # run the pgTAP tests in supabase/tests/database
supabase stop
```

The keys `supabase start` prints are local-development defaults. Hosted project credentials,
service-role keys and `.env` files never go into the repository.

Migrations are append-only once shared. Tests run inside transactions and roll back; there is no
seed data.

## Not yet

- Incremental pull (cursors, change logs) and Realtime.
- Storage. Photo columns hold storage paths, not signed URLs, following
  `users/{userId}/people/{personId}/...`, `users/{userId}/gifts/{giftId}/...` and
  `users/{userId}/gift-ideas/{giftIdeaId}/...`. Buckets and policies come with photo upload.
