-- Account deletion: the caller and everything it owns go in one transaction, its footprint on
-- other users' lists with it, and nothing of anyone else; nobody can delete another account, a late
-- failure keeps every row, and a token of the deleted account can neither read nor recreate data.
begin;
create extension if not exists pgtap with schema extensions;
select plan(61);

-- ---- function hardening and privileges ----

select has_function('public', 'delete_my_account', array[]::text[], 'the client entry point exists and takes no argument');
select has_function('private', 'delete_account', array['uuid'], 'the deletion itself is a private helper');
select is(
    (select count(*)::int from pg_proc
     where oid in ('public.delete_my_account()'::regprocedure, 'private.delete_account(uuid)'::regprocedure)
       and prosecdef and 'search_path=""' = any (proconfig)),
    2, 'both functions are security definer with an empty search_path');
select is(
    (select pg_catalog.pg_get_userbyid(proowner) from pg_proc where oid = 'private.delete_account(uuid)'::regprocedure),
    'postgres', 'the deletion runs as the migration role');
select ok(has_table_privilege('postgres', 'auth.users', 'DELETE'), 'which Supabase grants DELETE on auth.users');
select ok(not exists (select 1 from pg_roles where rolname = 'postgres' and rolsuper), 'without being a superuser');
select ok(has_function_privilege('authenticated', 'public.delete_my_account()', 'execute'), 'signed-in users may delete their account');
select ok(not has_function_privilege('anon', 'public.delete_my_account()', 'execute'), 'anon may not');
select ok(not has_function_privilege('service_role', 'public.delete_my_account()', 'execute'), 'nor the service role, which has no caller');
select is(
    (select count(*)::int from pg_proc p, aclexplode(p.proacl) a
     where p.oid in ('public.delete_my_account()'::regprocedure, 'private.delete_account(uuid)'::regprocedure) and a.grantee = 0),
    0, 'neither function is executable by PUBLIC');
select ok(not has_function_privilege('authenticated', 'private.delete_account(uuid)', 'execute'), 'signed-in users cannot name an account to delete');
select ok(not has_function_privilege('anon', 'private.delete_account(uuid)', 'execute'), 'nor can anon');
select is(
    (select count(*)::int from pg_proc
     where pronamespace = 'public'::regnamespace and proname = 'delete_my_account' and pronargs > 0),
    0, 'no public variant takes a target account');

-- ---- three accounts: Alice is deleted, Bob and Carol stay ----

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'carol@example.test');

-- What Supabase Auth keeps for Alice: an identity, a session and its refresh token.
insert into auth.identities (provider_id, user_id, identity_data, provider)
values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '{}', 'email');
insert into auth.sessions (id, user_id)
values ('5e550000-0000-4000-8000-00000000000a', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
insert into auth.refresh_tokens (token, user_id, session_id)
values ('alice-refresh', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '5e550000-0000-4000-8000-00000000000a');

-- Alice owns a row in every table: person, occasion, idea, gift, list, wish, invite (and the ledger
-- entries their inserts recorded).
insert into public.people (last_mutation_id, id, owner_user_id, name)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna');
insert into public.occasions (last_mutation_id, id, owner_user_id, person_id, type, day, month, repeats_yearly)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'a0000000-0000-4000-8000-000000000001', 'BIRTHDAY', 12, 5, true);
insert into public.gift_ideas (last_mutation_id, id, owner_user_id, recipient_person_id, occasion_id, name, status)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002', 'Scarf', 'IDEA');
insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, occasion_id, source_gift_idea_id, name, given_at)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000004', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN',
        'a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002',
        'a0000000-0000-4000-8000-000000000003', 'Book', '2026-05-12');
insert into public.wishlists (last_mutation_id, id, owner_user_id, person_id, name)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000005', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'a0000000-0000-4000-8000-000000000001', 'For Anna');
insert into public.wishes (last_mutation_id, id, owner_user_id, wishlist_id, name)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-000000000006', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'a0000000-0000-4000-8000-000000000005', 'Lamp');
insert into public.wishlist_invites (wishlist_id, owner_user_id, token_hash, expires_at)
values ('a0000000-0000-4000-8000-000000000005', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        decode(repeat('ab', 32), 'hex'), now() + interval '7 days');

-- Bob: a person of his own, a member of Alice's list holding a reservation on her wish.
insert into public.people (last_mutation_id, id, owner_user_id, name)
values (gen_random_uuid(), 'b0000000-0000-4000-8000-000000000001', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Bea');
insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id)
values ('a0000000-0000-4000-8000-000000000005', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
insert into public.wish_reservations (wish_id, wishlist_id, reserver_user_id, membership_id)
select 'a0000000-0000-4000-8000-000000000006', wishlist_id, member_user_id, membership_id
from public.wishlist_members
where wishlist_id = 'a0000000-0000-4000-8000-000000000005' and member_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

-- Carol: a list with two wishes; Alice and Bob are members, each holding one reservation.
insert into public.wishlists (last_mutation_id, id, owner_user_id, name)
values (gen_random_uuid(), 'c0000000-0000-4000-8000-000000000005', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'Carol''s');
insert into public.wishes (last_mutation_id, id, owner_user_id, wishlist_id, name) values
    (gen_random_uuid(), 'c0000000-0000-4000-8000-000000000006', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'c0000000-0000-4000-8000-000000000005', 'Kettle'),
    (gen_random_uuid(), 'c0000000-0000-4000-8000-000000000007', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'c0000000-0000-4000-8000-000000000005', 'Plant');
insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id) values
    ('c0000000-0000-4000-8000-000000000005', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
    ('c0000000-0000-4000-8000-000000000005', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
insert into public.wish_reservations (wish_id, wishlist_id, reserver_user_id, membership_id)
select v.wish_id::uuid, m.wishlist_id, m.member_user_id, m.membership_id
from public.wishlist_members m
join (values ('c0000000-0000-4000-8000-000000000006', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
             ('c0000000-0000-4000-8000-000000000007', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')) v (wish_id, member)
  on m.member_user_id = v.member::uuid
where m.wishlist_id = 'c0000000-0000-4000-8000-000000000005';

-- Everything that involves Alice, and everything that does not, before anything is deleted.
create function pg_temp.alices_rows() returns int language sql as $$
    select ((select count(*) from public.people where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.occasions where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.gift_ideas where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.gifts where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.wishlists where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.wishes where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.wishlist_invites where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.sync_mutations where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.wishlist_members
             where member_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' or owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.wish_reservations
             where reserver_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' or wishlist_id = 'a0000000-0000-4000-8000-000000000005')
          + (select count(*) from public.profiles where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from auth.users where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'))::int
$$;
create function pg_temp.others_rows() returns int language sql as $$
    select ((select count(*) from public.people where owner_user_id <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.wishlists where owner_user_id <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.wishes where owner_user_id <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.sync_mutations where owner_user_id <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.wishlist_members
             where member_user_id <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' and owner_user_id <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
          + (select count(*) from public.wish_reservations
             where reserver_user_id <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' and wishlist_id <> 'a0000000-0000-4000-8000-000000000005')
          + (select count(*) from public.profiles where id <> 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'))::int
$$;
create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
grant execute on function pg_temp.as_user(uuid) to authenticated, anon;

-- One row per table for Alice (11 public tables and the auth user), with her ledger entries and both
-- sharing directions: person, occasion, idea, gift, list, wish, invite, 6 ledger entries, 2 member
-- rows (Bob on hers, she on Carol's), 2 reservations (Bob's on hers, hers on Carol's), profile, user.
select is(pg_temp.alices_rows(), 19, 'Alice has a row in every table before her deletion');
select cmp_ok(pg_temp.others_rows(), '>', 0, 'Bob and Carol have rows of their own');

-- ---- linked_user_id is closed to clients ----

select col_has_check('public', 'people', 'linked_user_id', 'people.linked_user_id is guarded by a check');
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select throws_ok(
    $$update public.people set linked_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', last_mutation_id = gen_random_uuid()
      where id = 'b0000000-0000-4000-8000-000000000001'$$,
    '23514', null, 'Bob cannot link his person to Alice''s account');
select throws_ok(
    $$insert into public.people (last_mutation_id, id, owner_user_id, name, linked_user_id)
      values (gen_random_uuid(), gen_random_uuid(), 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Probe', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc')$$,
    '23514', null, 'nor create a person linked to any account');
reset role;

-- A link from before the check (only possible by dropping it here) is cleared by the deletion as a
-- versioned change, so it cannot make the deletion fail.
alter table public.people drop constraint people_linked_user_id_unused;
update public.people set linked_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', last_mutation_id = gen_random_uuid()
where id = 'b0000000-0000-4000-8000-000000000001';
select set_config('test.bea_version', (select version::text from public.people where id = 'b0000000-0000-4000-8000-000000000001'), true);
select set_config('test.others_before', pg_temp.others_rows()::text, true);

-- ---- nobody can delete someone else ----

set local role anon;
select throws_ok($$select public.delete_my_account()$$, '42501', null, 'anon cannot call the deletion');
reset role;
select set_config('request.jwt.claims', '{"role": "authenticated"}', true);
set local role authenticated;
select throws_ok($$select public.delete_my_account()$$, '42501', 'not authenticated', 'a request without a user deletes nothing');
reset role;
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select throws_ok($$select private.delete_account('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')$$,
    '42501', null, 'Bob cannot call the private deletion for Alice');
select throws_ok($$select public.delete_my_account('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')$$,
    '42883', null, 'nor pass Alice''s id to the public one');
reset role;
select is(pg_temp.alices_rows(), 19, 'after every attempt Alice still has all her rows');

-- ---- a failure at the very end keeps every row ----

-- A row pointing to Alice makes the last statement, the delete from auth.users, fail (23503) after
-- every other delete and update has run. The table goes with the test's rollback.
create table public.deletion_blocker (user_id uuid references auth.users (id));
insert into public.deletion_blocker values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select throws_ok($$select public.delete_my_account()$$, '23503', null, 'a failure deleting the auth user fails the whole call');
reset role;
select is(pg_temp.alices_rows(), 19, 'and every row of Alice is still there');
select is((select linked_user_id from public.people where id = 'b0000000-0000-4000-8000-000000000001'),
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid, 'the link of Bob''s person was rolled back too');
select is((select count(*)::int from auth.sessions where user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 1, 'as was nothing of her session');
delete from public.deletion_blocker;

-- ---- Alice deletes her account ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select lives_ok($$select public.delete_my_account()$$, 'Alice deletes her account');
reset role;

select is(pg_temp.alices_rows(), 0, 'nothing of Alice is left in any table');
select is((select count(*)::int from public.people where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her people are gone');
select is((select count(*)::int from public.gifts where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her gifts are gone');
select is((select count(*)::int from public.gift_ideas where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her ideas are gone');
select is((select count(*)::int from public.occasions where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her occasions are gone');
select is((select count(*)::int from public.wishlists where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her lists are gone');
select is((select count(*)::int from public.wishes where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her wishes are gone');
select is((select count(*)::int from public.wishlist_invites where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her invites are gone');
select is((select count(*)::int from public.sync_mutations where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her ledger is gone');
select is((select count(*)::int from public.profiles where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her profile is gone');
select is((select count(*)::int from auth.users where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her auth user is gone');
select is((select count(*)::int from auth.identities where user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her identity went with it');
select is((select count(*)::int from auth.sessions where user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'her session went with it');
select is((select count(*)::int from auth.refresh_tokens where session_id = '5e550000-0000-4000-8000-00000000000a'), 0,
    'and her refresh token, so no new access token can be issued');

-- Sharing: what involved Alice is gone in both directions; Bob and Carol's own sharing stays.
select is((select count(*)::int from public.wishlist_members where member_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
           and wishlist_id = 'a0000000-0000-4000-8000-000000000005'), 0, 'Bob is no longer a member of Alice''s deleted list');
select is((select count(*)::int from public.wish_reservations where wish_id = 'a0000000-0000-4000-8000-000000000006'), 0,
    'and his reservation on her wish is gone');
select is((select count(*)::int from public.wishlist_members where member_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0,
    'Alice is no longer a member of Carol''s list');
select is((select count(*)::int from public.wish_reservations where wish_id = 'c0000000-0000-4000-8000-000000000006'), 0,
    'and her reservation on Carol''s wish is gone, with her id');
select is((select count(*)::int from public.wishlist_members where member_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
           and wishlist_id = 'c0000000-0000-4000-8000-000000000005'), 1, 'Bob is still a member of Carol''s list');
select is((select reserver_user_id from public.wish_reservations where wish_id = 'c0000000-0000-4000-8000-000000000007'),
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'::uuid, 'with his reservation on Carol''s other wish');
select is((select count(*)::int from public.wishes where wishlist_id = 'c0000000-0000-4000-8000-000000000005'), 2, 'Carol''s list keeps both wishes');

-- Bob's person: unlinked by a versioned, ledgered change his devices pull.
select is((select linked_user_id from public.people where id = 'b0000000-0000-4000-8000-000000000001'), null::uuid,
    'Bob''s person is no longer linked to the deleted account');
select is((select version from public.people where id = 'b0000000-0000-4000-8000-000000000001'),
    current_setting('test.bea_version')::bigint + 1, 'as a new version of the row');
select ok(exists (select 1 from public.sync_mutations
                  where entity_id = 'b0000000-0000-4000-8000-000000000001'
                    and resulting_version = current_setting('test.bea_version')::bigint + 1),
    'recorded in Bob''s ledger like any other change');

-- Nothing that did not involve Alice changed, except Bob's unlinked person (one new ledger entry).
select is(pg_temp.others_rows(), current_setting('test.others_before')::int + 1,
    'every row of Bob and Carol is still there, plus the ledger entry of the unlink');
select is((select count(*)::int from auth.users where id in ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc')),
    2, 'Bob and Carol keep their accounts');

-- ---- a token of the deleted account afterwards ----

-- PostgREST accepts a signed token until it expires; the database then sees a user that no longer
-- exists. It reads nothing and can write nothing: every owned row needs its owner in auth.users.
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select is((select count(*)::int from public.people), 0, 'the old token reads no person');
select is((select count(*)::int from public.profiles), 0, 'nor a profile');
select throws_ok(
    $$insert into public.people (last_mutation_id, id, owner_user_id, name)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Back')$$,
    '23503', null, 'and cannot recreate a person');
select throws_ok(
    $$insert into public.wishlists (last_mutation_id, id, owner_user_id, name)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Back')$$,
    '23503', null, 'or a wishlist');
select is(public.get_shared_wishlists(), '[]'::jsonb, 'and sees no shared list');
-- A retry after a lost answer finds nothing left and succeeds, so the app can simply call again.
select lives_ok($$select public.delete_my_account()$$, 'deleting again is a harmless no-op');
reset role;
select is(pg_temp.others_rows(), current_setting('test.others_before')::int + 1, 'which touches nobody else');

select * from finish();
rollback;
