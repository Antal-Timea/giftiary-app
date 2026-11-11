-- A wishlist's kind (the owner's own list, or a list kept for a person) is fixed at creation, so the
-- owner of their own list can never make public.get_owner_reserved_wishes answer for it, not even for
-- a moment by linking it to a person and back. Lists created for a person keep their neutral
-- reserved view, and every other update still works.

begin;
create extension if not exists pgtap with schema extensions;
select plan(25);

-- ---- fixtures ----

insert into auth.users (id, email, email_confirmed_at, raw_user_meta_data) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test', now(), '{"display_name": "Alice"}'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test', now(), '{"display_name": "Bob"}');
insert into public.people (id, owner_user_id, name, last_mutation_id) values
    ('a0000000-0000-4000-8000-0000000000f1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Peter', gen_random_uuid()),
    ('a0000000-0000-4000-8000-0000000000f2', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna', gen_random_uuid());
-- LO is Alice's own list; LP is kept for Peter.
insert into public.wishlists (id, owner_user_id, person_id, name, last_mutation_id) values
    ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', null, 'Mine', gen_random_uuid()),
    ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-0000000000f1', 'For Peter', gen_random_uuid());
insert into public.wishes (id, owner_user_id, wishlist_id, name, last_mutation_id) values
    ('20000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000001', 'Lamp', gen_random_uuid()),
    ('20000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000002', 'Kite', gen_random_uuid()),
    ('20000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '10000000-0000-4000-8000-000000000002', 'Ball', gen_random_uuid());
insert into public.wishlist_members (wishlist_id, owner_user_id, member_user_id) values
    ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
    ('10000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');

create function pg_temp.as_user(id uuid) returns void language sql as $$
    select set_config('request.jwt.claims', json_build_object('sub', id, 'role', 'authenticated')::text, true)
$$;
-- Read as the table owner, past row level security, so the checks see what is stored.
create function pg_temp.person_of(list uuid) returns uuid language sql security definer as $$
    select person_id from public.wishlists where id = list
$$;
create function pg_temp.version_of(list uuid) returns bigint language sql security definer as $$
    select version from public.wishlists where id = list
$$;
grant execute on function pg_temp.person_of(uuid), pg_temp.version_of(uuid) to authenticated;

-- ---- the guard itself ----

select ok(not has_function_privilege('authenticated', 'private.keep_wishlist_person()', 'execute')
          and not has_function_privilege('anon', 'private.keep_wishlist_person()', 'execute'),
    'the guard is not callable by clients');
select is((select proconfig from pg_proc where oid = 'private.keep_wishlist_person()'::regprocedure),
    array['search_path=""'], 'and runs with an empty search_path');

-- ---- Bob reserves on both lists ----

select pg_temp.as_user('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
set local role authenticated;
select is(public.reserve_wish('20000000-0000-4000-8000-000000000001'), 'RESERVED_BY_ME', 'Bob reserves the wish of Alice''s own list');
select is(public.reserve_wish('20000000-0000-4000-8000-000000000002'), 'RESERVED_BY_ME', 'and one of Peter''s list');

-- ---- 1–3. Alice cannot turn her own list into a person's list ----

select pg_temp.as_user('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
select throws_ok($$select public.get_owner_reserved_wishes('10000000-0000-4000-8000-000000000001')$$,
    'P0002', 'not available', 'Alice is not told about her own list');
select throws_ok(
    $$update public.wishlists set person_id = 'a0000000-0000-4000-8000-0000000000f1', last_mutation_id = gen_random_uuid()
      where id = '10000000-0000-4000-8000-000000000001'$$,
    '42501', 'person_id of wishlists 10000000-0000-4000-8000-000000000001 cannot be changed',
    'linking her own list to a person is refused');
select is(pg_temp.person_of('10000000-0000-4000-8000-000000000001'), null::uuid, 'the list is still her own');
select is(pg_temp.version_of('10000000-0000-4000-8000-000000000001'), 1::bigint, 'and was not changed at all');
select throws_ok($$select public.get_owner_reserved_wishes('10000000-0000-4000-8000-000000000001')$$,
    'P0002', 'not available', 'so she is still not told about it');
-- The same through a whole-row write, as a PostgREST upsert would send it.
select throws_ok(
    $$insert into public.wishlists (id, owner_user_id, person_id, name, last_mutation_id)
      values ('10000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
              'a0000000-0000-4000-8000-0000000000f1', 'Mine', gen_random_uuid())
      on conflict (id) do update set person_id = excluded.person_id, last_mutation_id = excluded.last_mutation_id$$,
    '42501', null, 'an upsert cannot link it either');
select is(pg_temp.person_of('10000000-0000-4000-8000-000000000001'), null::uuid, 'and it stays her own');

-- ---- 4. nor the other way round, nor to someone else ----

select throws_ok(
    $$update public.wishlists set person_id = null, last_mutation_id = gen_random_uuid()
      where id = '10000000-0000-4000-8000-000000000002'$$,
    '42501', 'person_id of wishlists 10000000-0000-4000-8000-000000000002 cannot be changed',
    'a person''s list cannot become her own');
select throws_ok(
    $$update public.wishlists set person_id = 'a0000000-0000-4000-8000-0000000000f2', last_mutation_id = gen_random_uuid()
      where id = '10000000-0000-4000-8000-000000000002'$$,
    '42501', 'person_id of wishlists 10000000-0000-4000-8000-000000000002 cannot be changed',
    'nor be moved to another person');
select is(pg_temp.person_of('10000000-0000-4000-8000-000000000002'), 'a0000000-0000-4000-8000-0000000000f1'::uuid,
    'it is still kept for Peter');
select is(pg_temp.version_of('10000000-0000-4000-8000-000000000002'), 1::bigint, 'and unchanged');

-- ---- 5. a list created for a person keeps its neutral reserved view ----

select is(public.get_owner_reserved_wishes('10000000-0000-4000-8000-000000000002'),
    '["20000000-0000-4000-8000-000000000002"]'::jsonb, 'Alice still sees which wish of Peter''s list is reserved, and nothing more');

-- ---- 6. every other update still works ----

select lives_ok(
    $$update public.wishlists set name = 'Mine, renamed', last_mutation_id = 'c0000000-0000-4000-8000-000000000001'
      where id = '10000000-0000-4000-8000-000000000001'$$,
    'renaming her own list works');
select lives_ok(
    $$update public.wishlists set name = 'For Peter, renamed', person_id = 'a0000000-0000-4000-8000-0000000000f1',
          last_mutation_id = 'c0000000-0000-4000-8000-000000000002'
      where id = '10000000-0000-4000-8000-000000000002'$$,
    'and a whole-row write that repeats the person is not a change of it');
select lives_ok(
    $$update public.wishlists set name = 'Mine, again', person_id = null, last_mutation_id = 'c0000000-0000-4000-8000-000000000003'
      where id = '10000000-0000-4000-8000-000000000001'$$,
    'nor one that repeats "no person"');
select is(pg_temp.version_of('10000000-0000-4000-8000-000000000001'), 3::bigint, 'each write is versioned as before');
select is(pg_temp.version_of('10000000-0000-4000-8000-000000000002'), 2::bigint, 'on both lists');
select is((select count(*)::int from public.sync_mutations
           where mutation_id in ('c0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000002',
                                 'c0000000-0000-4000-8000-000000000003')),
    3, 'and recorded in the ledger');
select is(pg_temp.person_of('10000000-0000-4000-8000-000000000002'), 'a0000000-0000-4000-8000-0000000000f1'::uuid,
    'the person is untouched');
select lives_ok(
    $$update public.wishlists set deleted_at = now(), last_mutation_id = gen_random_uuid()
      where id = '10000000-0000-4000-8000-000000000001'$$,
    'deleting her own list works');
select lives_ok(
    $$insert into public.wishlists (id, owner_user_id, person_id, name, last_mutation_id)
      values ('10000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
              'a0000000-0000-4000-8000-0000000000f2', 'For Anna', gen_random_uuid())$$,
    'and a new list can be created for any of her people');

select * from finish();
rollback;
