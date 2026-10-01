# Gifty backend (Supabase)

Phase 1: the PostgreSQL schema, integrity rules and row level security for the data the Android
app already stores offline. The Android app does not talk to it yet.

## How it maps to Android

Room on the device stays the source of truth for the user's data; the UI never waits for the
network. The server is where devices exchange that data later.

| Room entity | Table | Notes |
|---|---|---|
| `person` | `people` | |
| `occasion` | `occasions` | `day`/`month`/optional `year`, never a full date |
| `gift_idea` | `gift_ideas` | statuses `IDEA`, `PLANNED`, `PURCHASED` only |
| `gift` | `gifts` | `direction` `GIVEN`/`RECEIVED`; null giver/recipient = the signed-in user |
| — | `profiles` | one per auth user, created on sign-up; not synced |

Columns are the Room columns in snake_case. What does **not** cross over:

- `sync_operation` (the outbox) and `remoteState` are device-local bookkeeping. The server row
  itself is the remote state, so there is no sync table or remote-state column here.
- Wishlists and wishes exist in Room but are not part of the first sync; they are not here yet.

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
`updated_at` are `now()`. Every update that changes something sets `version = old + 1` and
`updated_at = now()`. Whatever a client sends for these columns is ignored. Android keeps
`version = 0` for rows the server has never seen and adopts the server's version after a push.

**Stale writes are not detected yet.** Server-controlled numbering is implemented; conflict
enforcement is not. A client that updates from an outdated read still succeeds:

1. devices A and B both read a row at version 4,
2. B updates it: version 5,
3. A updates it from its stale copy: version 6, and B's change is silently overwritten.

The sync API phase must make every update carry the version the client last saw and apply it
only if the row is still at that version, checked atomically on the server, most likely through
a focused RPC per write. Until then the plain table `UPDATE` is last-writer-wins, and
`tests/database/01_versioning.test.sql` pins that no such precondition exists yet.

**Soft delete, never hard delete.** Deleting is `update ... set deleted_at = now()`, which bumps
the version, so the tombstone reaches other devices. Clients have no DELETE privilege. References
to soft-deleted rows stay valid: old gifts keep pointing at a deleted person.

**Archived is not deleted.** Marking an idea as given sets `gift_ideas.archived_at` and creates a
gift with `source_gift_idea_id`. Both are ordinary writes.

**What deletes what.** Nothing between people, occasions, ideas and gifts cascades. On Android,
deleting a person soft-deletes their occasions in the same transaction and keeps their gifts and
ideas; the server sees those as ordinary updates. Deleting an auth user is blocked while they own
data, because account deletion must be designed deliberately. Profiles cascade with their user,
and `people.linked_user_id` is cleared when the linked account goes.

Value checks mirror the Android domain: money as `bigint` minor units plus a three-letter
uppercase currency, set together or not at all; non-blank names; occasion ranges
(`month` 1–12, `day` 1–31, `year` within the `java.time.Year` range Android accepts); custom
occasions need a title and only they have one; birthdays, name days and Christmas always repeat;
one-time occasions need a year; given gifts name a recipient and received ones a giver, and the
other side is optional (both set is a gift between two other people). Exact calendar validity
(29 February) is left to Android.

## Access

| Role | profiles | people, occasions, gift_ideas, gifts |
|---|---|---|
| `anon` | nothing | nothing |
| `authenticated` | read own; update display name, avatar path, locale | read, insert, update own rows; no delete |
| `service_role` | select, insert, update, delete (server-side only, never in the app) | same |

Helper functions live in the `private` schema and are not executable by client roles. The only
`security definer` function creates the profile on sign-up, with an empty `search_path`.
`config.toml` sets `auto_expose_new_tables = false`, and the migrations revoke and grant every
table explicitly anyway, because hosted projects expose new tables by default. `service_role`
bypasses RLS but still needs table privileges, and with auto-expose off it gets none by default;
it receives the four data operations, not `ALL`.

Triggers fire without the caller holding `EXECUTE` on the trigger function, so the sign-up
trigger works even though Supabase Auth (`supabase_auth_admin`) cannot call it directly.

## Local development

Needs the [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started)
and Docker. From the repository root:

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

## Not in this phase

- Wishlists, wishes, sharing, invitations and reservations (reservations will be
  server-authoritative and hidden from the wishlist owner).
- The Android side: auth, remote data source, push/pull sync.
- Expected-version conflict detection (see above).
- Incremental pull (cursors, change logs) and Realtime.
- Storage. Photo columns hold storage paths, not signed URLs, following
  `users/{userId}/people/{personId}/...`, `users/{userId}/gifts/{giftId}/...` and
  `users/{userId}/gift-ideas/{giftIdeaId}/...`. Buckets and policies come with photo upload.
- Account deletion.
