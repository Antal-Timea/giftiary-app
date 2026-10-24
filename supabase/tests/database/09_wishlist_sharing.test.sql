-- Wishlist sharing and reservations (W3), checked call by call: the sharing tables are reachable
-- only through their functions, invites store only a hash of their token and limit themselves,
-- guests see three reservation states and never who, the owner never sees reservations at all,
-- and a reservation counts only while its wish, list and membership are what they were.
-- Two-session behaviour (locking, waiting, limits under concurrency) is in 10_wishlist_sharing_concurrency.
begin;
create extension if not exists pgtap with schema extensions;
select plan(121);

-- ---- schema, privileges and function hardening ----

select has_table('public', 'wishlist_invites', 'invites exist');
select has_table('public', 'wishlist_members', 'members exist');
select has_table('public', 'wish_reservations', 'reservations exist');
select is(
    (select count(*)::int from pg_constraint where contype = 'f' and conrelid = 'public.wish_reservations'::regclass),
    0, 'reservations have no foreign key of any kind');
select fk_ok('public', 'wishlist_members', array['wishlist_id', 'owner_user_id'], 'public', 'wishlists', array['id', 'owner_user_id']);
select fk_ok('public', 'wishlist_invites', array['wishlist_id', 'owner_user_id'], 'public', 'wishlists', array['id', 'owner_user_id']);
select col_is_unique('public', 'wishlist_members', 'membership_id', 'one membership id per membership');
select is(
    (select count(*)::int from pg_class
     where oid in ('public.wishlist_invites'::regclass, 'public.wishlist_members'::regclass, 'public.wish_reservations'::regclass)
       and relrowsecurity),
    3, 'row level security is enabled on the sharing tables');
select is(
    (select count(*)::int from pg_policies
     where schemaname = 'public' and tablename in ('wishlist_invites', 'wishlist_members', 'wish_reservations')),
    0, 'the sharing tables have no policy: no client row is ever visible');
select table_privs_are('public', 'wishlist_invites', 'authenticated', array[]::text[]);
select table_privs_are('public', 'wishlist_members', 'authenticated', array[]::text[]);
select table_privs_are('public', 'wish_reservations', 'authenticated', array[]::text[]);
select table_privs_are('public', 'wishlist_invites', 'anon', array[]::text[]);
select table_privs_are('public', 'wishlist_members', 'anon', array[]::text[]);
select table_privs_are('public', 'wish_reservations', 'anon', array[]::text[]);
select table_privs_are('public', 'wish_reservations', 'service_role', array['SELECT', 'INSERT', 'UPDATE', 'DELETE']);
select is(
    (select count(*)::int from pg_publication_tables
     where tablename in ('wishlist_invites', 'wishlist_members', 'wish_reservations')),
    0, 'no sharing table is published to realtime');
select is(
    (select count(*)::int from pg_proc where pronamespace = 'public'::regnamespace and not prosecdef),
    0, 'every API function is security definer');
select is(
    (select count(*)::int from pg_proc
     where pronamespace = 'public'::regnamespace and (proconfig is null or not ('search_path=""' = any (proconfig)))),
    0, 'every API function runs with an empty search_path');
select is(
    (select count(*)::int from pg_proc
     where pronamespace = 'public'::regnamespace and has_function_privilege('anon', oid, 'execute')),
    0, 'anon can execute no API function');
select is(
    (select count(*)::int from pg_proc p, aclexplode(p.proacl) a
     where p.pronamespace = 'public'::regnamespace and a.grantee = 0),
    0, 'no API function is executable by PUBLIC');
select is(
    (select count(*)::int from pg_proc
     where pronamespace = 'public'::regnamespace and has_function_privilege('authenticated', oid, 'execute')),
    11, 'signed-in users can execute exactly the ten sharing functions and the account deletion');
select is(
    (select count(*)::int from pg_proc
     where pronamespace = 'public'::regnamespace and has_function_privilege('service_role', oid, 'execute')),
    0, 'service_role does not call the user functions');
select is(
    (select count(*)::int from pg_proc
     where oid in ('private.wishlist_capacity_key(uuid)'::regprocedure, 'private.active_reservation_holder(uuid)'::regprocedure)
       and (has_function_privilege('authenticated', oid, 'execute') or has_function_privilege('anon', oid, 'execute'))),
    0, 'the internal helpers are not executable by clients');
select is(pg_get_function_identity_arguments('public.reserve_wish'::regproc), 'p_wish_id uuid',
    'reserving takes only the wish; its list is never taken from the caller');
select is(pg_get_function_identity_arguments('public.release_wish'::regproc), 'p_wish_id uuid',
    'releasing takes only the wish');
select is(
    (select (length(prosrc) - length(replace(lower(prosrc), 'for update', ''))) / length('for update')
     from pg_proc where oid = 'public.reserve_wish'::regproc),
    1, 'reserving takes exactly one row lock');
select matches((select prosrc from pg_proc where oid = 'public.reserve_wish'::regproc),
    'from public\.wish_reservations r where r\.wish_id = p_wish_id for update',
    'that one lock is on the reservation row');
select is(
    (select count(*)::int from pg_proc
     where oid in ('public.release_wish'::regproc, 'public.get_shared_wishlist'::regproc,
                   'public.remove_wishlist_member'::regproc, 'public.leave_wishlist'::regproc)
       and prosrc ~* 'for (update|share|no key update|key share)'),
    0, 'releasing, reading, removing and leaving lock no row explicitly');
select is(
    (select count(*)::int from pg_proc
     where oid in ('public.remove_wishlist_member'::regproc, 'public.leave_wishlist'::regproc)
       and prosrc ilike '%wish_reservations%'),
    0, 'removing a member and leaving never touch reservations');
select is(
    (select count(*)::int from pg_proc
     where oid in ('public.reserve_wish'::regproc, 'public.release_wish'::regproc, 'public.get_shared_wishlist'::regproc,
                   'public.remove_wishlist_member'::regproc, 'public.leave_wishlist'::regproc)
       and prosrc ilike '%advisory%'),
    0, 'only the invite functions take the capacity lock');
select ok(
    (select bool_and(prosrc ilike '%pg_catalog.sha256(v_raw)%' and prosrc !~* 'raise (notice|log|info|debug)')
     from pg_proc where oid in ('public.create_wishlist_invite'::regproc, 'public.redeem_wishlist_invite'::regproc)),
    'invite functions hash with pg_catalog.sha256 and never log');
select matches((select prosrc from pg_proc where oid = 'public.create_wishlist_invite'::regproc),
    'extensions\.gen_random_bytes\(32\)', 'tokens are 32 bytes from pgcrypto''s random generator');
select is((select extnamespace::regnamespace::text from pg_extension where extname = 'pgcrypto'), 'extensions',
    'pgcrypto lives in the extensions schema');
select has_function('extensions', 'gen_random_bytes', array['integer'], 'pgcrypto provides gen_random_bytes(integer)');
select has_function('pg_catalog', 'sha256', array['bytea'], 'the core provides sha256(bytea)');
select is(encode(pg_catalog.sha256('abc'::bytea), 'hex'),
    'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', 'sha256 matches the standard test vector');
select is(
    (select count(*)::int from information_schema.columns
     where table_schema = 'public' and table_name = 'wishlist_invites' and data_type in ('text', 'character varying')),
    0, 'invites have no text column: a token has nowhere to be stored');

-- ---- fixtures, written with full privileges ----

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'carol@example.test'),
    ('dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'dave@example.test'),
    ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', 'erin@example.test');

insert into public.people (id, owner_user_id, name, last_mutation_id)
values ('a0000000-0000-4000-8000-0000000000f1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna', gen_random_uuid());

-- Alice's lists: L (shared), L2 (shared, the target of a move), LP (kept for Anna), LCAP (limits),
-- LPRIV (shared, later linked to Anna).
insert into public.wishlists (id, owner_user_id, person_id, name, last_mutation_id) values
    ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Birthday', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Christmas', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-0000000000f1', 'For Anna', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000004', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Limits', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000005', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Soon private', gen_random_uuid());

insert into public.wishes (id, owner_user_id, wishlist_id, name, last_mutation_id) values
    ('20000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001', 'Lamp', gen_random_uuid()),
    ('20000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001', 'Mug', gen_random_uuid()),
    ('20000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001', 'Scarf (deleted later)', gen_random_uuid()),
    ('20000000-0000-4000-8000-000000000004', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001', 'Book (hard-deleted later)', gen_random_uuid()),
    ('20000000-0000-4000-8000-000000000005', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001', 'Socks', gen_random_uuid()),
    ('20000000-0000-4000-8000-000000000006', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000005', 'Pen', gen_random_uuid());

-- Carol on L and LPRIV, Dave on L2 only, Bob on LPRIV; Bob joins L through an invite below.
insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id) values
    ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'),
    ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'),
    ('10000000-0000-4000-8000-000000000005', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');

-- What the owner's sync sees of two wishes that only guests will act on.
create temporary table owner_view as
select id, version, updated_at, last_mutation_id from public.wishes
where id in ('20000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000005');
create temporary table ledger_before as
select count(*)::int as entries from public.sync_mutations
where entity_id in ('20000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000005');
grant select on owner_view, ledger_before to authenticated;

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
create function pg_temp.state(list uuid, wish uuid) returns text language sql as $$
    select x ->> 'reservation_state'
    from jsonb_array_elements(public.get_shared_wishlist(list) -> 'wishes') x
    where x ->> 'wish_id' = wish::text
$$;
create function pg_temp.listed(list uuid, wish uuid) returns int language sql as $$
    select count(*)::int
    from jsonb_array_elements(public.get_shared_wishlist(list) -> 'wishes') x
    where x ->> 'wish_id' = wish::text
$$;

-- ---- invites and tokens ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;

select throws_ok($$select public.create_wishlist_invite('10000000-0000-4000-8000-000000000003')$$,
    'P0002', 'not available', 'a list kept for a person cannot be shared');
select throws_ok($$select public.create_wishlist_invite('10000000-0000-4000-8000-000000000001', 0)$$,
    '22023', null, 'an invite must last at least a day');
select throws_ok($$select public.create_wishlist_invite('10000000-0000-4000-8000-000000000001', 15)$$,
    '22023', null, 'an invite lasts at most fourteen days');

select set_config('test.token1', public.create_wishlist_invite('10000000-0000-4000-8000-000000000001') ->> 'token', true);
select set_config('test.token2', public.create_wishlist_invite('10000000-0000-4000-8000-000000000001', 1) ->> 'token', true);
select matches(current_setting('test.token1'), '^[A-Za-z0-9_-]{43}$', 'a token is 43 characters of base64url');
select isnt(current_setting('test.token1'), current_setting('test.token2'), 'every invite gets its own token');

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select throws_ok($$select public.create_wishlist_invite('10000000-0000-4000-8000-000000000001')$$,
    'P0002', 'not available', 'only the owner invites');

reset role;
select ok(exists (
    select 1 from public.wishlist_invites
    where token_hash = pg_catalog.sha256(decode(translate(current_setting('test.token1'), '-_', '+/') || '=', 'base64'))),
    'the stored hash is the SHA-256 of the token''s bytes');
select is(
    (select min(octet_length(token_hash)) from public.wishlist_invites where wishlist_id = '10000000-0000-4000-8000-000000000001'),
    32, 'hashes are 32 bytes');

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select is(public.redeem_wishlist_invite(current_setting('test.token1')) ->> 'wishlist_id', '10000000-0000-4000-8000-000000000001',
    'a valid token joins its list');
reset role;
select set_config('test.bob_membership',
    (select membership_id::text from public.wishlist_members
     where wishlist_id = '10000000-0000-4000-8000-000000000001' and member_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'), true);
set local role authenticated;
select is(public.redeem_wishlist_invite(current_setting('test.token1')) ->> 'wishlist_id', '10000000-0000-4000-8000-000000000001',
    'joining again succeeds');
reset role;
select is(
    (select membership_id::text from public.wishlist_members
     where wishlist_id = '10000000-0000-4000-8000-000000000001' and member_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
    current_setting('test.bob_membership'), 'joining again keeps the same membership');

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select throws_ok(format('select public.redeem_wishlist_invite(%L)', current_setting('test.token1')),
    'P0002', 'not available', 'the owner cannot join their own list');
select pg_temp.as_user('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee');
select throws_ok($$select public.redeem_wishlist_invite(repeat('a', 42))$$, 'P0002', 'not available', 'a 42-character token is refused like any other');
select throws_ok($$select public.redeem_wishlist_invite(repeat('a', 44))$$, 'P0002', 'not available', 'a 44-character token is refused like any other');
select throws_ok($$select public.redeem_wishlist_invite(repeat('a', 42) || '+')$$, 'P0002', 'not available', 'a token outside base64url is refused like any other');
select throws_ok($$select public.redeem_wishlist_invite(repeat('A', 43))$$, 'P0002', 'not available', 'an unknown token is refused like any other');

-- Revoked and expired invites stop working.
reset role;
select set_config('test.invite2', (select id::text from public.wishlist_invites
    where token_hash = pg_catalog.sha256(decode(translate(current_setting('test.token2'), '-_', '+/') || '=', 'base64'))), true);
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select lives_ok(format('select public.revoke_wishlist_invite(%L)', current_setting('test.invite2')), 'the owner revokes an invite');
select pg_temp.as_user('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee');
select throws_ok(format('select public.redeem_wishlist_invite(%L)', current_setting('test.token2')),
    'P0002', 'not available', 'a revoked invite is refused like any other');
reset role;
update public.wishlist_invites set created_at = now() - interval '10 days', expires_at = now() - interval '1 day', revoked_at = null
where id = current_setting('test.invite2')::uuid;
set local role authenticated;
select throws_ok(format('select public.redeem_wishlist_invite(%L)', current_setting('test.token2')),
    'P0002', 'not available', 'an expired invite is refused like any other');

-- ---- limits, one call after another (the concurrent case is in the next file) ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select lives_ok($$select public.create_wishlist_invite('10000000-0000-4000-8000-000000000004') from generate_series(1, 5)$$,
    'five active invites are allowed');
select throws_ok($$select public.create_wishlist_invite('10000000-0000-4000-8000-000000000004')$$,
    'P0001', 'invite limit reached', 'a sixth active invite is not');
reset role;
select set_config('test.cap_invite', (select id::text from public.wishlist_invites
    where wishlist_id = '10000000-0000-4000-8000-000000000004' order by created_at, id limit 1), true);
set local role authenticated;
select public.revoke_wishlist_invite(current_setting('test.cap_invite')::uuid);
select set_config('test.cap_token', public.create_wishlist_invite('10000000-0000-4000-8000-000000000004') ->> 'token', true);
select ok(current_setting('test.cap_token') <> '', 'a revoked invite frees its place');

reset role;
insert into auth.users (id, email)
select gen_random_uuid(), 'filler' || n || '@example.test' from generate_series(1, 49) n;
insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id)
select '10000000-0000-4000-8000-000000000004', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', id
from auth.users where email like 'filler%@example.test';
select pg_temp.as_user('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
set local role authenticated;
select lives_ok(format('select public.redeem_wishlist_invite(%L)', current_setting('test.cap_token')), 'the fiftieth member joins');
select pg_temp.as_user('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee');
select throws_ok(format('select public.redeem_wishlist_invite(%L)', current_setting('test.cap_token')),
    'P0002', 'not available', 'the fifty-first is refused like an invalid token');
select pg_temp.as_user('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
select lives_ok(format('select public.redeem_wishlist_invite(%L)', current_setting('test.cap_token')),
    'a member of a full list can still redeem again: joining twice is never refused');
reset role;
select is((select count(*)::int from public.wishlist_members where wishlist_id = '10000000-0000-4000-8000-000000000004'), 50,
    'a list has at most fifty members');

-- ---- reservations: the owner and strangers see nothing ----

set local role authenticated;
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(public.reserve_wish('20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_ME', 'a member reserves a free wish');
select is(public.reserve_wish('20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_ME', 'reserving it again is harmless');
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select is(public.reserve_wish('20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_OTHER', 'another member learns only that it is taken');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_OTHER',
    'other members see it reserved by someone else');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002'), 'AVAILABLE',
    'free wishes are available');
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_ME',
    'the reserver sees it reserved by them');

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select throws_ok($$select public.reserve_wish('20000000-0000-4000-8000-000000000001')$$,
    'P0002', 'not available', 'the owner cannot reserve on a reserved wish');
select throws_ok($$select public.reserve_wish('20000000-0000-4000-8000-000000000002')$$,
    'P0002', 'not available', 'and gets exactly the same answer on a free one');
select throws_ok($$select public.release_wish('20000000-0000-4000-8000-000000000001')$$,
    'P0002', 'not available', 'the owner cannot release');
select throws_ok($$select public.get_shared_wishlist('10000000-0000-4000-8000-000000000001')$$,
    'P0002', 'not available', 'the owner cannot read the guest view of their own list');
select is(
    (select string_agg(k, ',' order by k) from jsonb_object_keys(public.list_wishlist_access('10000000-0000-4000-8000-000000000001')) k),
    'invites,members', 'the owner''s access view holds invites and members only');
select is(
    (select string_agg(distinct k, ',' order by k)
     from jsonb_array_elements(public.list_wishlist_access('10000000-0000-4000-8000-000000000001') -> 'members') m,
          jsonb_object_keys(m) k),
    'display_name,joined_at,member_user_id', 'members are listed with name and join time, nothing about reservations');

select pg_temp.as_user('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee');
select throws_ok($$select public.reserve_wish('20000000-0000-4000-8000-000000000001')$$,
    'P0002', 'not available', 'a non-member cannot reserve');
select throws_ok($$select public.get_shared_wishlist('10000000-0000-4000-8000-000000000001')$$,
    'P0002', 'not available', 'a non-member cannot read a shared list');
select lives_ok($$select public.leave_wishlist('10000000-0000-4000-8000-000000000001')$$,
    'leaving a list one is not on changes nothing');

-- ---- release only frees one's own reservation ----

select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select is(public.release_wish('20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_OTHER', 'releasing someone else''s reservation does nothing');
reset role;
select is((select reserver_user_id from public.wish_reservations where wish_id = '20000000-0000-4000-8000-000000000001'),
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid, 'the reservation is still Bob''s');
set local role authenticated;
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(public.release_wish('20000000-0000-4000-8000-000000000001'), 'AVAILABLE', 'the reserver releases their own');
reset role;
select is((select count(*)::int from public.wish_reservations where wish_id = '20000000-0000-4000-8000-000000000001'), 0,
    'a released reservation is gone');

-- ---- removal and leaving release logically; stale rows are reclaimed ----

set local role authenticated;
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select public.reserve_wish('20000000-0000-4000-8000-000000000001');
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select lives_ok($$select public.remove_wishlist_member('10000000-0000-4000-8000-000000000001', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')$$,
    'the owner removes a member');
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'), 'AVAILABLE',
    'a removed member''s reservation no longer counts');
reset role;
select is((select count(*)::int from public.wish_reservations where wish_id = '20000000-0000-4000-8000-000000000001'), 1,
    'the removal did not touch the reservation row: the release is logical');
set local role authenticated;
select is(public.reserve_wish('20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_ME', 'another member takes over the stale reservation');
reset role;
select is(
    (select (reserver_user_id, wishlist_id, membership_id)::text from public.wish_reservations
     where wish_id = '20000000-0000-4000-8000-000000000001'),
    (select ('cccccccc-cccc-4ccc-8ccc-cccccccccccc'::uuid, wishlist_id, membership_id)::text from public.wishlist_members
     where wishlist_id = '10000000-0000-4000-8000-000000000001' and member_user_id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'),
    'the row now carries the new reserver, the current list and their membership');

set local role authenticated;
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select lives_ok(format('select public.redeem_wishlist_invite(%L)', current_setting('test.token1')), 'the removed member joins again');
reset role;
select isnt(
    (select membership_id::text from public.wishlist_members
     where wishlist_id = '10000000-0000-4000-8000-000000000001' and member_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
    current_setting('test.bob_membership'), 'joining again after removal is a new membership');

set local role authenticated;
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select public.leave_wishlist('10000000-0000-4000-8000-000000000001');
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'), 'AVAILABLE',
    'a member who left no longer holds their reservation');
reset role;
insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id)
values ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc');
set local role authenticated;
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'), 'AVAILABLE',
    'rejoining does not bring an old reservation back');

-- ---- moved, deleted and hard-deleted wishes ----

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(public.reserve_wish('20000000-0000-4000-8000-000000000002'), 'RESERVED_BY_ME', 'Bob reserves the mug');
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
update public.wishes set wishlist_id = '10000000-0000-4000-8000-000000000002', last_mutation_id = gen_random_uuid()
where id = '20000000-0000-4000-8000-000000000002';
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(pg_temp.listed('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002'), 0,
    'a moved wish leaves its old list''s view');
select pg_temp.as_user('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
select is(pg_temp.state('10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002'), 'AVAILABLE',
    'on its new list its old reservation does not count');
select is(public.reserve_wish('20000000-0000-4000-8000-000000000002'), 'RESERVED_BY_ME', 'a member of the new list can reserve it');
reset role;
select is((select wishlist_id from public.wish_reservations where wish_id = '20000000-0000-4000-8000-000000000002'),
    '10000000-0000-4000-8000-000000000002'::uuid, 'the reservation now belongs to the new list');

set local role authenticated;
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select public.reserve_wish('20000000-0000-4000-8000-000000000003');
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
update public.wishes set deleted_at = now(), last_mutation_id = gen_random_uuid() where id = '20000000-0000-4000-8000-000000000003';
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(pg_temp.listed('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000003'), 0,
    'a deleted wish is not listed');
select throws_ok($$select public.reserve_wish('20000000-0000-4000-8000-000000000003')$$,
    'P0002', 'not available', 'a deleted wish cannot be reserved');
select throws_ok($$select public.release_wish('20000000-0000-4000-8000-000000000003')$$,
    'P0002', 'not available', 'or released');

select public.reserve_wish('20000000-0000-4000-8000-000000000004');
reset role;
select lives_ok($$delete from public.wishes where id = '20000000-0000-4000-8000-000000000004'$$,
    'an administrator can hard-delete a reserved wish: no key holds it');
select is((select count(*)::int from public.wish_reservations where wish_id = '20000000-0000-4000-8000-000000000004'), 1,
    'its reservation stays behind, inert');
set local role authenticated;
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select is(pg_temp.listed('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000004'), 0,
    'the orphan is never shown');
select throws_ok($$select public.reserve_wish('20000000-0000-4000-8000-000000000004')$$,
    'P0002', 'not available', 'and never actionable');

-- ---- cross-list attempts ----

select pg_temp.as_user('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
select throws_ok($$select public.reserve_wish('20000000-0000-4000-8000-000000000005')$$,
    'P0002', 'not available', 'a member of another list cannot reserve here');
reset role;
select is((select count(*)::int from public.wish_reservations where reserver_user_id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd'
                                                                     and wish_id = '20000000-0000-4000-8000-000000000005'), 0,
    'and no row was created for them');
-- An administrator's mistake: a row tying a wish of L to Dave's membership of L2.
insert into public.wish_reservations (wish_id, wishlist_id, reserver_user_id, membership_id)
select '20000000-0000-4000-8000-000000000005', wishlist_id, member_user_id, membership_id
from public.wishlist_members where wishlist_id = '10000000-0000-4000-8000-000000000002' and member_user_id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
set local role authenticated;
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000005'), 'AVAILABLE',
    'a cross-list row never shows as a reservation');
select is(public.reserve_wish('20000000-0000-4000-8000-000000000005'), 'RESERVED_BY_ME', 'and is taken over by an eligible member');

-- ---- a list later kept for a person is no longer shared ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
update public.wishlists set person_id = 'a0000000-0000-4000-8000-0000000000f1', last_mutation_id = gen_random_uuid()
where id = '10000000-0000-4000-8000-000000000005';
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select throws_ok($$select public.get_shared_wishlist('10000000-0000-4000-8000-000000000005')$$,
    'P0002', 'not available', 'a list linked to a person is not available to members');
select throws_ok($$select public.reserve_wish('20000000-0000-4000-8000-000000000006')$$,
    'P0002', 'not available', 'nor are its wishes');
select is((select count(*)::int from jsonb_array_elements(public.get_shared_wishlists()) x
           where x ->> 'wishlist_id' = '10000000-0000-4000-8000-000000000005'), 0,
    'and it leaves the member''s shared lists');

-- ---- the owner's sync data never moves ----

select is(
    (select count(*)::int from public.wishes w join owner_view o using (id)
     where (w.version, w.updated_at, w.last_mutation_id) is distinct from (o.version, o.updated_at, o.last_mutation_id)),
    0, 'reserving, releasing and reclaiming never change a wish''s version, time or mutation id');
reset role;
select is(
    (select count(*)::int from public.sync_mutations
     where entity_id in ('20000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000005')),
    (select entries from ledger_before), 'and never write the ledger');

-- ---- direct table access is denied ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select throws_ok($$select * from public.wish_reservations$$, '42501', null, 'the owner cannot read reservations');
select throws_ok($$insert into public.wish_reservations (wish_id, wishlist_id, reserver_user_id, membership_id)
                   values (gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid())$$,
    '42501', null, 'nor write them');
select throws_ok($$update public.wish_reservations set reserved_at = now()$$, '42501', null, 'nor change them');
select throws_ok($$delete from public.wish_reservations$$, '42501', null, 'nor delete them');
select throws_ok($$select * from public.wishlist_members$$, '42501', null, 'members are not readable directly');
select throws_ok($$select * from public.wishlist_invites$$, '42501', null, 'invites are not readable directly');
reset role;
set local role anon;
select throws_ok($$select * from public.wish_reservations$$, '42501', null, 'anon cannot read reservations');
select throws_ok($$select public.reserve_wish('20000000-0000-4000-8000-000000000001')$$, '42501', null, 'anon cannot call a sharing function');
reset role;

select * from finish();
rollback;
