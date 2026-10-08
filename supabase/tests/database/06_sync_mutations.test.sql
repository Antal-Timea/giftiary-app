-- The sync protocol's server primitives, one write at a time: every accepted write is recorded in
-- the mutation ledger in its own transaction, a retry of a committed mutation can neither commit
-- again nor bump the version, and the ledger can be read only by its owner and written by nobody.
-- Two copies of a mutation running at the same time are covered by 07_sync_concurrency.
begin;
create extension if not exists pgtap with schema extensions;
select plan(46);

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test');

-- Bob's row, written with full privileges so the checks below do not depend on RLS hiding it.
insert into public.people (id, owner_user_id, name, last_mutation_id)
values ('b0000000-0000-4000-8000-000000000001', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Bob''s person',
        'b1000000-0000-4000-8000-000000000001');

select set_config('request.jwt.claims', '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}', true);
set local role authenticated;

-- ---- create ----

insert into public.people (id, owner_user_id, name, last_mutation_id)
values ('a0000000-0000-4000-8000-00000000000a', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna',
        'c1000000-0000-4000-8000-000000000001');

select is((select version from public.people where id = 'a0000000-0000-4000-8000-00000000000a'), 1::bigint,
    'a created row starts at version 1');
select results_eq(
    $$select owner_user_id, entity_type, entity_id, resulting_version from public.sync_mutations
      where mutation_id = 'c1000000-0000-4000-8000-000000000001'$$,
    $$values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid, 'people'::text, 'a0000000-0000-4000-8000-00000000000a'::uuid, 1::bigint)$$,
    'the create is recorded with its owner, row and resulting version');
select throws_ok(
    $$insert into public.people (id, owner_user_id, name) values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'No id')$$,
    '23502', null, 'a create without a mutation id is rejected');

-- A create whose response was lost is retried with the same id and mutation: the duplicate is
-- rejected and the ledger still proves the original.
select throws_ok(
    $$insert into public.people (id, owner_user_id, name, last_mutation_id)
      values ('a0000000-0000-4000-8000-00000000000a', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna', 'c1000000-0000-4000-8000-000000000001')$$,
    '23505', null, 'retrying a committed create is rejected as a duplicate');
select is((select count(*)::int from public.sync_mutations where mutation_id = 'c1000000-0000-4000-8000-000000000001'), 1,
    'the retried create is still recorded exactly once');
select is((select version from public.people where id = 'a0000000-0000-4000-8000-00000000000a'), 1::bigint,
    'the retried create did not touch the row');

-- ---- a write that rolls back leaves no proof ----

select throws_ok(
    $$insert into public.people (id, owner_user_id, name, last_mutation_id)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '   ', 'c2000000-0000-4000-8000-000000000002')$$,
    '23514', null, 'a create failing a check is rejected');
select is((select count(*)::int from public.sync_mutations where mutation_id = 'c2000000-0000-4000-8000-000000000002'), 0,
    'a rejected create leaves no ledger entry');

savepoint before_rolled_back_write;
insert into public.people (id, owner_user_id, name, last_mutation_id)
values ('a0000000-0000-4000-8000-0000000000aa', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Temporary',
        'c3000000-0000-4000-8000-000000000003');
rollback to savepoint before_rolled_back_write;
select is((select count(*)::int from public.sync_mutations where mutation_id = 'c3000000-0000-4000-8000-000000000003'), 0,
    'a write that is rolled back takes its ledger entry with it');
select is((select count(*)::int from public.people where id = 'a0000000-0000-4000-8000-0000000000aa'), 0,
    'and the row with it');

-- ---- compare-and-set update ----

with updated as (
    update public.people set relationship = 'Sister', last_mutation_id = 'c4000000-0000-4000-8000-000000000004'
    where id = 'a0000000-0000-4000-8000-00000000000a' and owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa' and version = 1
    returning version
)
select is((select array_agg(version) from updated), array[2::bigint],
    'an update against the current version changes exactly one row and returns the new version');
select is((select resulting_version from public.sync_mutations where mutation_id = 'c4000000-0000-4000-8000-000000000004'), 2::bigint,
    'the update is recorded with its resulting version');

-- A stale writer (it last saw version 1) changes nothing and records nothing.
with updated as (
    update public.people set relationship = 'Cousin', last_mutation_id = 'c5000000-0000-4000-8000-000000000005'
    where id = 'a0000000-0000-4000-8000-00000000000a' and version = 1
    returning version
)
select is((select count(*)::int from updated), 0, 'an update against a stale version matches no row');
select is((select relationship from public.people where id = 'a0000000-0000-4000-8000-00000000000a'), 'Sister',
    'the stale update did not change the row');
select is((select version from public.people where id = 'a0000000-0000-4000-8000-00000000000a'), 2::bigint,
    'the stale update did not bump the version');
select is((select count(*)::int from public.sync_mutations where mutation_id = 'c5000000-0000-4000-8000-000000000005'), 0,
    'the stale update left no ledger entry');

-- The update whose response was lost is retried as the same mutation against the same version.
with updated as (
    update public.people set relationship = 'Sister', last_mutation_id = 'c4000000-0000-4000-8000-000000000004'
    where id = 'a0000000-0000-4000-8000-00000000000a' and version = 1
    returning version
)
select is((select count(*)::int from updated), 0, 'retrying a committed update matches no row');
select is((select version from public.people where id = 'a0000000-0000-4000-8000-00000000000a'), 2::bigint,
    'the retry did not bump the version again');
select is((select count(*)::int from public.sync_mutations where mutation_id = 'c4000000-0000-4000-8000-000000000004'), 1,
    'the retried update is still recorded exactly once');

-- Two writers that both saw version 2, one after the other: only the first wins.
with updated as (
    update public.people set relationship = 'first', last_mutation_id = 'c6000000-0000-4000-8000-000000000006'
    where id = 'a0000000-0000-4000-8000-00000000000a' and version = 2
    returning version
)
select is((select count(*)::int from updated), 1, 'the first writer for version 2 succeeds');
with updated as (
    update public.people set relationship = 'second', last_mutation_id = 'c7000000-0000-4000-8000-000000000007'
    where id = 'a0000000-0000-4000-8000-00000000000a' and version = 2
    returning version
)
select is((select count(*)::int from updated), 0, 'the second writer for version 2 matches no row');
select is((select relationship from public.people where id = 'a0000000-0000-4000-8000-00000000000a'), 'first',
    'the first writer''s change stands');
select is((select array_agg(mutation_id order by resulting_version) from public.sync_mutations
           where mutation_id in ('c6000000-0000-4000-8000-000000000006', 'c7000000-0000-4000-8000-000000000007')),
    array['c6000000-0000-4000-8000-000000000006'::uuid], 'only the successful mutation is recorded');

-- ---- mutation ids are single-use ----

select throws_ok(
    $$update public.people set relationship = 'again' where id = 'a0000000-0000-4000-8000-00000000000a'$$,
    '23505', null, 'an update keeping the row''s current mutation id is rejected');
select throws_ok(
    $$update public.people set relationship = 'old id', last_mutation_id = 'c4000000-0000-4000-8000-000000000004'
      where id = 'a0000000-0000-4000-8000-00000000000a'$$,
    '23505', null, 'an update reusing an earlier mutation id of the same row is rejected');
select throws_ok(
    $$insert into public.people (id, owner_user_id, name, last_mutation_id)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Béla', 'c1000000-0000-4000-8000-000000000001')$$,
    '23505', null, 'a mutation id already used for another row is rejected');
select throws_ok(
    $$insert into public.occasions (id, owner_user_id, type, day, month, repeats_yearly, last_mutation_id)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'CHRISTMAS', 24, 12, true, 'c6000000-0000-4000-8000-000000000006')$$,
    '23505', null, 'a mutation id is single-use across tables');
select is((select version from public.people where id = 'a0000000-0000-4000-8000-00000000000a'), 3::bigint,
    'rejected reuses left the row''s version alone');

-- ---- tombstone ----

with updated as (
    update public.people set deleted_at = now(), last_mutation_id = 'c8000000-0000-4000-8000-000000000008'
    where id = 'a0000000-0000-4000-8000-00000000000a' and version = 3
    returning version
)
select is((select array_agg(version) from updated), array[4::bigint], 'a soft delete is a compare-and-set update to a new version');
select is((select resulting_version from public.sync_mutations where mutation_id = 'c8000000-0000-4000-8000-000000000008'), 4::bigint,
    'the tombstone is recorded');

-- ---- owners ----

select throws_ok(
    $$insert into public.people (id, owner_user_id, name, last_mutation_id)
      values (gen_random_uuid(), 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Planted', 'c9000000-0000-4000-8000-000000000009')$$,
    '42501', null, 'Alice cannot create a row for Bob');
select is((select count(*)::int from public.sync_mutations where mutation_id = 'c9000000-0000-4000-8000-000000000009'), 0,
    'and no ledger entry exists for it');
with updated as (
    update public.people set name = 'Taken', last_mutation_id = 'ca000000-0000-4000-8000-00000000000a'
    where id = 'b0000000-0000-4000-8000-000000000001' and version = 1
    returning version
)
select is((select count(*)::int from updated), 0, 'Alice cannot update Bob''s row even knowing its id and version');
select is((select count(*)::int from public.sync_mutations where mutation_id = 'ca000000-0000-4000-8000-00000000000a'), 0,
    'and no ledger entry exists for it');
select throws_ok(
    $$update public.people set owner_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', last_mutation_id = gen_random_uuid()
      where id = 'a0000000-0000-4000-8000-00000000000a'$$,
    '42501', null, 'a row cannot be handed to another owner');

-- ---- the ledger is read-only, and private ----

select is((select count(*)::int from public.sync_mutations), 4, 'Alice sees exactly her own recorded mutations: create, two updates, tombstone');
select is((select count(*)::int from public.sync_mutations where owner_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'), 0,
    'Alice sees none of Bob''s');
select throws_ok(
    $$insert into public.sync_mutations (mutation_id, owner_user_id, entity_type, entity_id, resulting_version)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'people', 'a0000000-0000-4000-8000-00000000000a', 9)$$,
    '42501', null, 'clients cannot forge a ledger entry');
select throws_ok(
    $$update public.sync_mutations set resulting_version = 9$$,
    '42501', null, 'clients cannot alter ledger entries');
select throws_ok(
    $$delete from public.sync_mutations$$,
    '42501', null, 'clients cannot delete ledger entries');
select throws_ok(
    $$select private.record_sync_mutation()$$,
    '42501', null, 'clients cannot call the recording function');

reset role;
select set_config('request.jwt.claims', '{"sub": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "role": "authenticated"}', true);
set local role authenticated;
select is((select count(*)::int from public.sync_mutations), 1, 'Bob sees only his own recorded mutation');

reset role;
set local role anon;
select throws_ok($$select count(*) from public.sync_mutations$$, '42501', null, 'anon cannot read the ledger');

-- ---- the full pull: owner_user_id = A and id > last order by id ----

reset role;
insert into public.people (id, owner_user_id, name, last_mutation_id) values
    ('a0000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'P1', gen_random_uuid()),
    ('a0000000-0000-4000-8000-000000000003', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'P3', gen_random_uuid()),
    ('a0000000-0000-4000-8000-000000000002', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'P2', gen_random_uuid());
select set_config('request.jwt.claims', '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}', true);
set local role authenticated;

select results_eq(
    $$select id from public.people where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
      and id > '00000000-0000-0000-0000-000000000000' order by id limit 2$$,
    $$values ('a0000000-0000-4000-8000-000000000001'::uuid), ('a0000000-0000-4000-8000-000000000002'::uuid)$$,
    'the first page lists the owner''s rows in id order');
select results_eq(
    $$select id from public.people where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
      and id > 'a0000000-0000-4000-8000-000000000002' order by id limit 2$$,
    $$values ('a0000000-0000-4000-8000-000000000003'::uuid), ('a0000000-0000-4000-8000-00000000000a'::uuid)$$,
    'the next page continues after the last id and includes tombstones');
select is(
    (select count(*)::int from public.people where owner_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'),
    0, 'another owner''s rows are never part of the pull');

select * from finish();
rollback;
