-- Recipient invites (Sharing 4B): an owner invites an email address, and only a signed-in user
-- whose confirmed address is that one sees and answers it. The owner's side never looks at
-- accounts, so it answers alike for any address, and sees pending or not accepted, never which.
begin;
create extension if not exists pgtap with schema extensions;
select plan(82);

-- ---- schema and privileges ----

select has_table('public', 'wishlist_recipient_invites', 'recipient invites exist');
select fk_ok('public', 'wishlist_recipient_invites', array['wishlist_id', 'owner_user_id'], 'public', 'wishlists', array['id', 'owner_user_id']);
select ok((select relrowsecurity from pg_class where oid = 'public.wishlist_recipient_invites'::regclass), 'row level security is on');
select is((select count(*)::int from pg_policies where schemaname = 'public' and tablename = 'wishlist_recipient_invites'),
    0, 'with no policy: no client row is ever visible');
select table_privs_are('public', 'wishlist_recipient_invites', 'authenticated', array[]::text[]);
select table_privs_are('public', 'wishlist_recipient_invites', 'anon', array[]::text[]);
select is((select count(*)::int from pg_publication_tables where tablename = 'wishlist_recipient_invites'),
    0, 'not published to realtime');
select ok(not has_function_privilege('authenticated', 'private.confirmed_email(uuid)', 'execute')
      and not has_function_privilege('anon', 'private.confirmed_email(uuid)', 'execute'),
    'clients cannot ask for anyone''s confirmed address');
select is(pg_get_function_identity_arguments('public.get_my_invites'::regproc), '', 'a recipient never names an address');
select is(pg_get_function_identity_arguments('public.respond_to_invite'::regproc), 'p_invite_id uuid, p_accept boolean',
    'answering takes only the invite and the answer');
select ok(
    (select bool_and(prosrc !~* '(request\.jwt|auth\.jwt|auth\.email)') from pg_proc
     where pronamespace = 'public'::regnamespace
       and proname in ('create_recipient_invite', 'list_recipient_invites', 'revoke_recipient_invite', 'get_my_invites', 'respond_to_invite')),
    'no invite function reads token claims beyond auth.uid()');
select ok(
    (select bool_and(prosrc !~* 'auth\.users') from pg_proc
     where oid in ('public.list_recipient_invites'::regproc, 'public.revoke_recipient_invite'::regproc)),
    'listing and revoking never look at accounts');

-- ---- fixtures, written with full privileges ----

-- Carol has not confirmed her address yet.
insert into auth.users (id, email, email_confirmed_at, raw_user_meta_data) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test', now(), '{"display_name": "Alice"}'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test', now(), '{}'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'carol@example.test', null, '{}'),
    ('dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'dave@example.test', now(), '{}'),
    ('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', 'erin@example.test', now(), '{}');

insert into public.people (id, owner_user_id, name, last_mutation_id)
values ('a0000000-0000-4000-8000-0000000000f1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna', gen_random_uuid());

-- Alice: L1 (her own), LP (kept for Anna), LD (deleted later), LF (filled to 50 members later).
-- Erin: E1 and E2, for the limits.
insert into public.wishlists (id, owner_user_id, person_id, name, last_mutation_id) values
    ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Birthday', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-0000000000f1', 'For Anna', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Soon deleted', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000004', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Full', gen_random_uuid()),
    ('30000000-0000-4000-8000-000000000001', 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', null, 'Erin one', gen_random_uuid()),
    ('30000000-0000-4000-8000-000000000002', 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee', null, 'Erin two', gen_random_uuid());
insert into public.wishes (id, owner_user_id, wishlist_id, name, last_mutation_id)
values ('20000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001', 'Lamp', gen_random_uuid());

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
-- A token that also carries an email claim, as Supabase's do.
create function pg_temp.as_user_claiming(id uuid, email text) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated', 'email', email)::text, true)
$$;
create function pg_temp.keys(j jsonb) returns text[] language sql as $$
    select array_agg(k order by k) from jsonb_object_keys(j) k
$$;
create function pg_temp.mine(id text) returns boolean language sql as $$
    select exists (select 1 from jsonb_array_elements(public.get_my_invites()) x where x ->> 'invite_id' = id)
$$;
create function pg_temp.owner_state(list uuid, id text) returns text language sql as $$
    select x ->> 'state' from jsonb_array_elements(public.list_recipient_invites(list)) x where x ->> 'invite_id' = id
$$;
create function pg_temp.owner_sees(list uuid) returns text[] language sql as $$
    select array_agg((x ->> 'recipient_email') || ':' || (x ->> 'state') order by x ->> 'recipient_email')
    from jsonb_array_elements(public.list_recipient_invites(list)) x
$$;
create function pg_temp.wish_state(list uuid, wish uuid) returns text language sql as $$
    select x ->> 'reservation_state'
    from jsonb_array_elements(public.get_shared_wishlist(list) -> 'wishes') x
    where x ->> 'wish_id' = wish::text
$$;
create function pg_temp.answer_shape(j jsonb) returns jsonb language sql as $$
    select jsonb_build_object('keys', pg_temp.keys(j), 'state', j ->> 'state',
        'lasts', ((j ->> 'expires_at')::timestamptz - (j ->> 'created_at')::timestamptz)::text)
$$;

-- ---- the owner invites ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;

select throws_ok($$select public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'not-an-email')$$,
    '22023', 'invalid email', 'an address needs to look like one');
select throws_ok($$select public.create_recipient_invite('10000000-0000-4000-8000-000000000001', null)$$,
    '22023', 'invalid email', 'and must be given');
select throws_ok($$select public.create_recipient_invite('10000000-0000-4000-8000-000000000001', ' ALICE@example.test ')$$,
    '22023', 'own email', 'the owner cannot invite herself');

select set_config('test.bob_l1',
    public.create_recipient_invite('10000000-0000-4000-8000-000000000001', '  Bob@Example.TEST ') ->> 'invite_id', true);
select is(public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'bob@example.test') ->> 'invite_id',
    current_setting('test.bob_l1'), 'inviting an address again while it is pending returns the same invite');
select is(public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'bob@example.test') ->> 'recipient_email',
    'bob@example.test', 'the address is kept trimmed and lowercased');
select is(pg_temp.keys(public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'bob@example.test')),
    array['created_at', 'expires_at', 'invite_id', 'recipient_email', 'state'], 'the answer is the invite, nothing about an account');
select is(
    pg_temp.answer_shape(public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'dave@example.test')),
    pg_temp.answer_shape(public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'nobody@example.test')),
    'an address with an account and one without get the same answer');
select is(pg_temp.answer_shape(public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'dave@example.test')) ->> 'lasts',
    '14 days', 'an invite lasts fourteen days');

select set_config('test.dave_l1', public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'dave@example.test') ->> 'invite_id', true);
select set_config('test.nobody_l1', public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'nobody@example.test') ->> 'invite_id', true);
select set_config('test.carol_l1', public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'carol@example.test') ->> 'invite_id', true);
select set_config('test.bob_lp', public.create_recipient_invite('10000000-0000-4000-8000-000000000002', 'bob@example.test') ->> 'invite_id', true);
select set_config('test.bob_ld', public.create_recipient_invite('10000000-0000-4000-8000-000000000003', 'bob@example.test') ->> 'invite_id', true);
select set_config('test.bob_lf', public.create_recipient_invite('10000000-0000-4000-8000-000000000004', 'bob@example.test') ->> 'invite_id', true);

select is(pg_temp.owner_sees('10000000-0000-4000-8000-000000000001'),
    array['bob@example.test:PENDING', 'carol@example.test:PENDING', 'dave@example.test:PENDING', 'nobody@example.test:PENDING'],
    'the owner sees every address pending alike, confirmed, unconfirmed or without an account');
select is(pg_temp.keys(public.list_recipient_invites('10000000-0000-4000-8000-000000000001') -> 0),
    array['created_at', 'expires_at', 'invite_id', 'recipient_email', 'state'],
    'list entries carry nothing about accounts or reservations');

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select throws_ok($$select public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'x@example.test')$$,
    'P0002', 'not available', 'only the owner invites');
select throws_ok($$select public.list_recipient_invites('10000000-0000-4000-8000-000000000001')$$,
    'P0002', 'not available', 'only the owner lists them');
select throws_ok(format('select public.revoke_recipient_invite(%L)', current_setting('test.bob_l1')),
    'P0002', 'not available', 'only the owner revokes');

-- ---- who receives them ----

select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select is(public.get_my_invites(), '[]'::jsonb, 'an unconfirmed address receives nothing');
select throws_ok(format('select public.respond_to_invite(%L, true)', current_setting('test.carol_l1')),
    'P0002', 'not available', 'and cannot accept');

select pg_temp.as_user_claiming('dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'bob@example.test');
select is(jsonb_array_length(public.get_my_invites()), 1, 'a token claiming another address changes nothing: Dave sees only his own');
select throws_ok(format('select public.respond_to_invite(%L, true)', current_setting('test.bob_l1')),
    'P0002', 'not available', 'and cannot answer Bob''s');

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select is(public.get_my_invites(), '[]'::jsonb, 'the owner receives none of the invites she sent');

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(jsonb_array_length(public.get_my_invites()), 4, 'Bob sees his invites to four lists');
select is(
    (select x - 'invite_id' - 'created_at' - 'expires_at' from jsonb_array_elements(public.get_my_invites()) x
     where x ->> 'invite_id' = current_setting('test.bob_lp')),
    '{"wishlist_name": "For Anna", "owner_display_name": "Alice", "person_name": "Anna"}'::jsonb,
    'an invite shows the list''s name, who sent it and whom the list is for');
select is(pg_temp.keys(public.get_my_invites() -> 0),
    array['created_at', 'expires_at', 'invite_id', 'owner_display_name', 'person_name', 'wishlist_name'],
    'never the list''s id, the person''s id or a wish');
select ok(
    (select x -> 'person_name' = 'null'::jsonb from jsonb_array_elements(public.get_my_invites()) x
     where x ->> 'invite_id' = current_setting('test.bob_l1')),
    'an own list is for no one');

-- ---- declining ----

select is(public.respond_to_invite(current_setting('test.bob_lp')::uuid, false), '{}'::jsonb, 'declining answers nothing');
select ok(not pg_temp.mine(current_setting('test.bob_lp')), 'a declined invite is gone for Bob');
select throws_ok(format('select public.respond_to_invite(%L, true)', current_setting('test.bob_lp')),
    'P0002', 'not available', 'and cannot be accepted after all');

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select is(pg_temp.owner_state('10000000-0000-4000-8000-000000000002', current_setting('test.bob_lp')), 'NOT_ACCEPTED',
    'the owner sees a decline as not accepted');
select set_config('test.bob_lp2', public.create_recipient_invite('10000000-0000-4000-8000-000000000002', 'bob@example.test') ->> 'invite_id', true);
select isnt(current_setting('test.bob_lp2'), current_setting('test.bob_lp'), 'after a decline the address can be invited again');

-- ---- accepting: membership and reservations ----

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(public.respond_to_invite(current_setting('test.bob_l1')::uuid, true) ->> 'wishlist_id',
    '10000000-0000-4000-8000-000000000001', 'accepting returns the list to open');
select ok(exists (select 1 from jsonb_array_elements(public.get_shared_wishlists()) x
                  where x ->> 'wishlist_id' = '10000000-0000-4000-8000-000000000001'), 'and Bob is a member');
select throws_ok(format('select public.respond_to_invite(%L, true)', current_setting('test.bob_l1')),
    'P0002', 'not available', 'an answered invite cannot be answered again');
select is(public.reserve_wish('20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_ME', 'Bob reserves a wish');
reset role;
select set_config('test.bob_membership',
    (select membership_id::text from public.wishlist_members
     where wishlist_id = '10000000-0000-4000-8000-000000000001' and member_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'), true);

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select is(pg_temp.owner_state('10000000-0000-4000-8000-000000000001', current_setting('test.bob_l1')), null,
    'an accepted invite leaves the owner''s list: Bob is a member now');
select set_config('test.bob_l1b', public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'bob@example.test') ->> 'invite_id', true);
select isnt(current_setting('test.bob_l1b'), current_setting('test.bob_l1'), 'inviting a member again is a new invite, like any other');

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(public.respond_to_invite(current_setting('test.bob_l1b')::uuid, true) ->> 'wishlist_id',
    '10000000-0000-4000-8000-000000000001', 'accepting while already a member succeeds');
reset role;
select is(
    (select membership_id::text from public.wishlist_members
     where wishlist_id = '10000000-0000-4000-8000-000000000001' and member_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
    current_setting('test.bob_membership'), 'and keeps the membership');
set local role authenticated;
select is(pg_temp.wish_state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_ME',
    'with its reservation');

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select public.remove_wishlist_member('10000000-0000-4000-8000-000000000001', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select set_config('test.bob_l1c', public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'bob@example.test') ->> 'invite_id', true);
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select public.respond_to_invite(current_setting('test.bob_l1c')::uuid, true);
reset role;
select isnt(
    (select membership_id::text from public.wishlist_members
     where wishlist_id = '10000000-0000-4000-8000-000000000001' and member_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
    current_setting('test.bob_membership'), 'a removed member invited again joins with a new membership');
set local role authenticated;
select is(pg_temp.wish_state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'), 'AVAILABLE',
    'so the old reservation does not come back');

-- ---- revoking ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select public.revoke_recipient_invite(current_setting('test.nobody_l1')::uuid);
select is(pg_temp.owner_state('10000000-0000-4000-8000-000000000001', current_setting('test.nobody_l1')), null,
    'a revoked invite leaves the owner''s list');
select lives_ok(format('select public.revoke_recipient_invite(%L)', current_setting('test.nobody_l1')), 'revoking again is harmless');
select public.revoke_recipient_invite(current_setting('test.bob_lp')::uuid);
select is(pg_temp.owner_sees('10000000-0000-4000-8000-000000000002'), array['bob@example.test:PENDING'],
    'a not accepted invite can be cleared, leaving the new one');
select public.revoke_recipient_invite(current_setting('test.bob_lp2')::uuid);
select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select throws_ok(format('select public.respond_to_invite(%L, true)', current_setting('test.bob_lp2')),
    'P0002', 'not available', 'a withdrawn invite cannot be accepted');

-- ---- expiry ----

reset role;
update public.wishlist_recipient_invites
set created_at = now() - interval '15 days', expires_at = now() - interval '1 day'
where id = current_setting('test.dave_l1')::uuid;
set local role authenticated;

select pg_temp.as_user('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
select is(public.get_my_invites(), '[]'::jsonb, 'an expired invite is gone for the recipient');
select throws_ok(format('select public.respond_to_invite(%L, true)', current_setting('test.dave_l1')),
    'P0002', 'not available', 'and cannot be accepted');

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select is(pg_temp.owner_state('10000000-0000-4000-8000-000000000001', current_setting('test.dave_l1')), 'NOT_ACCEPTED',
    'the owner sees an expiry as not accepted, like a decline');
select set_config('test.dave_l1b', public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'dave@example.test') ->> 'invite_id', true);
select isnt(current_setting('test.dave_l1b'), current_setting('test.dave_l1'), 'an expired invite does not block a new one');
reset role;
select is((select status from public.wishlist_recipient_invites where id = current_setting('test.dave_l1')::uuid), 'expired',
    'the old one is closed as expired');
set local role authenticated;
select pg_temp.as_user('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
select is(jsonb_array_length(public.get_my_invites()), 1, 'and Dave sees the new one');

-- ---- confirming and changing the address ----

reset role;
update auth.users set email_confirmed_at = now() where id = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
update auth.users set email = 'dave.new@example.test' where id = 'dddddddd-dddd-4ddd-8ddd-dddddddddddd';
set local role authenticated;

select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select ok(pg_temp.mine(current_setting('test.carol_l1')), 'once confirmed, Carol sees her invite');
select pg_temp.as_user('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
select is(public.get_my_invites(), '[]'::jsonb, 'after an address change, invites to the old one no longer reach Dave');
select throws_ok(format('select public.respond_to_invite(%L, true)', current_setting('test.dave_l1b')),
    'P0002', 'not available', 'nor can he answer them');

-- ---- a deleted list ----

reset role;
update public.wishlists set deleted_at = now(), last_mutation_id = gen_random_uuid()
where id = '10000000-0000-4000-8000-000000000003';
set local role authenticated;

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select ok(not pg_temp.mine(current_setting('test.bob_ld')), 'an invite to a deleted list is gone');
select throws_ok(format('select public.respond_to_invite(%L, true)', current_setting('test.bob_ld')),
    'P0002', 'not available', 'and cannot be accepted');
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select throws_ok($$select public.list_recipient_invites('10000000-0000-4000-8000-000000000003')$$,
    'P0002', 'not available', 'the owner no longer lists its invites');
select throws_ok($$select public.create_recipient_invite('10000000-0000-4000-8000-000000000003', 'x@example.test')$$,
    'P0002', 'not available', 'nor invites to it');

-- ---- a full list ----

reset role;
insert into auth.users (id, email)
select gen_random_uuid(), 'member' || g || '@example.test' from generate_series(1, 50) g;
insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id)
select '10000000-0000-4000-8000-000000000004', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', id
from auth.users where email like 'member%@example.test';
set local role authenticated;

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select throws_ok(format('select public.respond_to_invite(%L, true)', current_setting('test.bob_lf')),
    'P0002', 'not available', 'a list of fifty members cannot be joined');
select ok(pg_temp.mine(current_setting('test.bob_lf')), 'and the invite stays open');

-- ---- limits ----

select pg_temp.as_user('eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee');
select lives_ok(
    $$select public.create_recipient_invite('30000000-0000-4000-8000-000000000001', 'p' || g || '@example.test') from generate_series(1, 20) g$$,
    'twenty pending invites on a list');
select throws_ok($$select public.create_recipient_invite('30000000-0000-4000-8000-000000000001', 'p21@example.test')$$,
    'P0001', 'invite limit reached', 'the twenty-first is refused');
select lives_ok(
    $$select public.create_recipient_invite('30000000-0000-4000-8000-000000000002', 'q' || g || '@example.test') from generate_series(1, 10) g$$,
    'thirty in a day across lists');
select throws_ok($$select public.create_recipient_invite('30000000-0000-4000-8000-000000000002', 'q11@example.test')$$,
    'P0001', 'daily invite limit reached', 'the thirty-first in a day is refused');
select public.revoke_recipient_invite((
    select (x ->> 'invite_id')::uuid from jsonb_array_elements(public.list_recipient_invites('30000000-0000-4000-8000-000000000001')) x
    where x ->> 'recipient_email' = 'p20@example.test'));
select throws_ok($$select public.create_recipient_invite('30000000-0000-4000-8000-000000000001', 'p21@example.test')$$,
    'P0001', 'daily invite limit reached', 'revoking does not give back the day''s quota');
select is(public.create_recipient_invite('30000000-0000-4000-8000-000000000001', 'p1@example.test') ->> 'state', 'PENDING',
    'asking again for a pending address is no new invite, even at the limit');

reset role;
update public.wishlist_recipient_invites
set created_at = created_at - interval '2 days', expires_at = expires_at - interval '2 days'
where owner_user_id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee';
set local role authenticated;
select is(public.create_recipient_invite('30000000-0000-4000-8000-000000000001', 'p21@example.test') ->> 'state', 'PENDING',
    'a day later the owner can invite again');
select throws_ok($$select public.create_recipient_invite('30000000-0000-4000-8000-000000000001', 'p22@example.test')$$,
    'P0001', 'invite limit reached', 'still within twenty pending per list');

-- ---- links are unchanged ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select is(pg_temp.keys(public.list_wishlist_access('10000000-0000-4000-8000-000000000001')), array['invites', 'members'],
    'link access still lists links and members only');
select matches(public.create_wishlist_invite('10000000-0000-4000-8000-000000000001') ->> 'token', '^[A-Za-z0-9_-]{43}$',
    'and links are still made alongside');

-- ---- account deletion ----

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select public.delete_my_account();
reset role;
select is((select status from public.wishlist_recipient_invites where id = current_setting('test.bob_lf')::uuid), 'pending',
    'a recipient''s deletion leaves the invites addressed to them as their owner wrote them');
set local role authenticated;
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select public.delete_my_account();
reset role;
select is((select count(*)::int from public.wishlist_recipient_invites where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
    0, 'the owner''s deletion takes her recipient invites with it');
select is((select count(*)::int from public.wishlist_recipient_invites where owner_user_id = 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee'),
    31, 'and nobody else''s');

select * from finish();
rollback;
