-- Gate A smoke fixes: the owner of a person's list learns which wishes are reserved (never by
-- whom) while the owner of their own list learns nothing; members are not invited again by email;
-- a new account's first name comes from its sign-in provider; the invite email has an HTML part.

begin;
create extension if not exists pgtap with schema extensions;
select plan(37);

-- ---- fixtures ----

insert into auth.users (id, email, email_confirmed_at, raw_user_meta_data) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test', now(), '{"display_name": "Alice"}'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test', now(), '{}'),
    ('cccccccc-cccc-4ccc-8ccc-cccccccccccc', 'carol@example.test', now(), '{}'),
    ('dddddddd-dddd-4ddd-8ddd-dddddddddddd', 'dave@example.test', now(), '{}');
insert into public.people (id, owner_user_id, name, last_mutation_id)
values ('a0000000-0000-4000-8000-0000000000f1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Peter', gen_random_uuid());
-- LP is kept for Peter; LO is Alice's own.
insert into public.wishlists (id, owner_user_id, person_id, name, last_mutation_id) values
    ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-0000000000f1', 'For Peter', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Mine', gen_random_uuid());
insert into public.wishes (id, owner_user_id, wishlist_id, name, last_mutation_id) values
    ('20000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001', 'Kite', gen_random_uuid()),
    ('20000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001', 'Ball', gen_random_uuid()),
    ('20000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000002', 'Lamp', gen_random_uuid());
insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id) values
    ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
    ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc'),
    ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
    ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'cccccccc-cccc-4ccc-8ccc-cccccccccccc');

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
create function pg_temp.state(list uuid, wish uuid) returns text language sql as $$
    select x ->> 'reservation_state'
    from jsonb_array_elements(public.get_shared_wishlist(list) -> 'wishes') x
    where x ->> 'wish_id' = wish::text
$$;
grant execute on function pg_temp.state(uuid, uuid) to authenticated;

-- ---- privileges ----

select ok(has_function_privilege('authenticated', 'public.get_owner_reserved_wishes(uuid)', 'execute'),
    'signed-in users can ask for their reserved wishes');
select ok(not has_function_privilege('anon', 'public.get_owner_reserved_wishes(uuid)', 'execute'),
    'anonymous callers cannot');
select is(pg_get_function_identity_arguments('public.get_owner_reserved_wishes'::regproc), 'p_wishlist_id uuid',
    'it takes only the list');

-- ---- Bob reserves on both lists ----

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select is(public.reserve_wish('20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_ME', 'Bob reserves a wish of Peter''s list');
select is(public.reserve_wish('20000000-0000-4000-8000-000000000003'), 'RESERVED_BY_ME', 'and one of Alice''s own list');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_ME',
    'Bob sees his own reservation');

select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_OTHER',
    'Carol sees only that it is reserved');
select is(pg_temp.state('10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002'), 'AVAILABLE',
    'and the other wish as available');

-- ---- the owner of a person's list, and of their own ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select is(public.get_owner_reserved_wishes('10000000-0000-4000-8000-000000000001'),
    '["20000000-0000-4000-8000-000000000001"]'::jsonb,
    'the owner of Peter''s list learns which wish is reserved');
select ok(public.get_owner_reserved_wishes('10000000-0000-4000-8000-000000000001')::text !~ '(bbbbbbbb|Bob|bob@)',
    'and nothing about who reserved it');
select throws_ok(
    format('select public.get_owner_reserved_wishes(%L)', '10000000-0000-4000-8000-000000000002'),
    'P0002', 'not available', 'the owner of their own list learns nothing: the surprise holds');
select throws_ok(
    format('select public.get_shared_wishlist(%L)', '10000000-0000-4000-8000-000000000001'),
    'P0002', 'not available', 'and the guest view stays closed to the owner');

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select throws_ok(
    format('select public.get_owner_reserved_wishes(%L)', '10000000-0000-4000-8000-000000000001'),
    'P0002', 'not available', 'a member cannot use the owner''s call');
select pg_temp.as_user('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
select throws_ok(
    format('select public.get_owner_reserved_wishes(%L)', '10000000-0000-4000-8000-000000000001'),
    'P0002', 'not available', 'nor can a stranger');

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
select is(public.release_wish('20000000-0000-4000-8000-000000000001'), 'AVAILABLE', 'Bob releases it');
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select is(public.get_owner_reserved_wishes('10000000-0000-4000-8000-000000000001'), '[]'::jsonb,
    'and the owner sees nothing reserved any more');
reset role;

-- A member who leaves takes their reservation with them, for the owner too.
select pg_temp.as_user('cccccccc-cccc-4ccc-8ccc-cccccccccccc');
set local role authenticated;
select is(public.reserve_wish('20000000-0000-4000-8000-000000000002'), 'RESERVED_BY_ME', 'Carol reserves the ball');
select public.leave_wishlist('10000000-0000-4000-8000-000000000001');
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select is(public.get_owner_reserved_wishes('10000000-0000-4000-8000-000000000001'), '[]'::jsonb,
    'and when she leaves, her reservation no longer counts');

-- ---- email invites to members ----

select throws_ok(
    format('select public.create_recipient_invite(%L, %L)', '10000000-0000-4000-8000-000000000001', ' Bob@Example.test'),
    '23505', 'already a member', 'a member''s address is refused, however it is written');
reset role;
select is((select count(*)::int from public.wishlist_recipient_invites), 0, 'and no invite was made');
select is((select count(*)::int from net.http_request_queue), 0, 'nor any email queued');

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
set local role authenticated;
select is(public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'carol@example.test') ->> 'state', 'PENDING',
    'someone who left can be invited again');
select set_config('test.dave', public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'dave@example.test') ->> 'invite_id', true);
select isnt(current_setting('test.dave'), '', 'a registered non-member can be invited');
select is(public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'new@example.test') ->> 'state', 'PENDING',
    'and so can an address with no account');
select is(
    public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'dave@example.test') ->> 'invite_id',
    current_setting('test.dave'),
    'a pending invite is still returned, not doubled');
reset role;

-- A declined invite does not stop a new one.
select pg_temp.as_user('dddddddd-dddd-4ddd-8ddd-dddddddddddd');
select public.respond_to_invite(current_setting('test.dave')::uuid, false);
select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select is(public.create_recipient_invite('10000000-0000-4000-8000-000000000001', 'dave@example.test') ->> 'state', 'PENDING',
    'someone who declined can be invited again');

-- ---- first display name from the provider ----

insert into auth.users (id, email, email_confirmed_at, raw_user_meta_data) values
    ('eeeeeeee-eeee-4eee-8eee-000000000001', 'g@example.test', now(), '{"full_name": " Gate A User ", "name": "Ignored"}'),
    ('eeeeeeee-eeee-4eee-8eee-000000000002', 'f@example.test', now(), '{"name": "Facebook Name"}'),
    ('eeeeeeee-eeee-4eee-8eee-000000000003', 'n@example.test', now(), '{"full_name": "  "}'),
    ('eeeeeeee-eeee-4eee-8eee-000000000004', 'o@example.test', now(), '{"display_name": "Own", "full_name": "Provider"}'),
    ('eeeeeeee-eeee-4eee-8eee-000000000005', 'c@example.test', now(), '{"full_name": "Line\n\tBreak"}');
select is((select display_name from public.profiles where id = 'eeeeeeee-eeee-4eee-8eee-000000000001'), 'Gate A User',
    'a new account takes the provider''s full name');
select is((select display_name from public.profiles where id = 'eeeeeeee-eeee-4eee-8eee-000000000002'), 'Facebook Name',
    'or its name');
select is((select display_name from public.profiles where id = 'eeeeeeee-eeee-4eee-8eee-000000000003'), null,
    'without one it has no name, and the app falls back as before');
select is((select display_name from public.profiles where id = 'eeeeeeee-eeee-4eee-8eee-000000000004'), 'Own',
    'a name given to Giftiary wins over the provider''s');
select is((select display_name from public.profiles where id = 'eeeeeeee-eeee-4eee-8eee-000000000005'), 'Line Break',
    'control characters in a provider name become a space');

update public.profiles set display_name = 'Edited' where id = 'eeeeeeee-eeee-4eee-8eee-000000000001';
update auth.users set raw_user_meta_data = '{"full_name": "Signed In Again"}' where id = 'eeeeeeee-eeee-4eee-8eee-000000000001';
select is((select display_name from public.profiles where id = 'eeeeeeee-eeee-4eee-8eee-000000000001'), 'Edited',
    'a later sign-in never replaces a name');

-- ---- the invite email's HTML part ----

select set_config('test.mail', private.recipient_invite_email('a&b<c>@example.test', '2026-12-01 10:00+00')::text, true);
select ok((current_setting('test.mail')::jsonb ->> 'text') ~ 'not for marketing'
      and (current_setting('test.mail')::jsonb ->> 'text') ~ 'https://giftiary\.app/privacy/#hu'
      and (current_setting('test.mail')::jsonb ->> 'text') !~ '<(p|html|table)',
    'the plain text part stays, for clients that show no HTML');
select ok((current_setting('test.mail')::jsonb ->> 'html') ~ 'a&amp;b&lt;c&gt;@example\.test'
      and (current_setting('test.mail')::jsonb ->> 'html') !~ 'a&b<c>',
    'the HTML part escapes the address');
select ok((current_setting('test.mail')::jsonb ->> 'html') ~ 'https://giftiary\.app/privacy/#hu'
      and (current_setting('test.mail')::jsonb ->> 'html') ~ 'https://giftiary\.app/privacy/#en'
      and (current_setting('test.mail')::jsonb ->> 'html') ~ 'Giftiary megnyitása'
      and (current_setting('test.mail')::jsonb ->> 'html') ~ 'Open Giftiary',
    'it links the privacy policy in both languages and opens Giftiary');
select ok((current_setting('test.mail')::jsonb ->> 'html') ~ '2026-12-01-ig'
      and (current_setting('test.mail')::jsonb ->> 'html') ~ 'by 2026-12-01'
      and (current_setting('test.mail')::jsonb ->> 'html') ~ 'not for marketing'
      and (current_setting('test.mail')::jsonb ->> 'html') ~ 'one-time message'
      and (current_setting('test.mail')::jsonb ->> 'html') ~ 'egyszeri üzenet',
    'and says until when, what the address is for, and that it is one email');
select is(
    (select count(*)::int from regexp_matches(current_setting('test.mail')::jsonb ->> 'html', '(<img|src=|url\(|https?://(?!giftiary\.app/))', 'gi')),
    0, 'it loads nothing and links nowhere but giftiary.app');

select * from finish();
rollback;
