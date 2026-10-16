-- Wishlists and wishes follow the contract of every synced table: owner-only rows, references that
-- stay within one owner, server-managed metadata, a mutation id on every write recorded in the
-- ledger, compare-and-set updates, soft deletion, and no client hard-deletes. The concurrent
-- retry of a write is covered for the shared trigger functions by 07_sync_concurrency.
begin;
create extension if not exists pgtap with schema extensions;
select plan(55);

-- ---- schema ----

select has_table('public', 'wishlists', 'wishlists exist');
select has_table('public', 'wishes', 'wishes exist');
select col_is_pk('public', 'wishlists', 'id', 'wishlists.id is the primary key');
select col_is_pk('public', 'wishes', 'id', 'wishes.id is the primary key');
select col_hasnt_default('public', 'wishlists', 'id', 'wishlists.id has no server default: the client generates ids');
select col_hasnt_default('public', 'wishes', 'id', 'wishes.id has no server default: the client generates ids');
select col_not_null('public', 'wishes', 'owner_user_id', 'a wish always has an owner');
select col_type_is('public', 'wishes', 'estimated_price_amount_minor', 'bigint', 'wish prices are stored in minor units');
select fk_ok('public', 'wishlists', array['person_id', 'owner_user_id'], 'public', 'people', array['id', 'owner_user_id']);
select fk_ok('public', 'wishes', array['wishlist_id', 'owner_user_id'], 'public', 'wishlists', array['id', 'owner_user_id']);
select has_index('public', 'wishlists', 'wishlists_owner_user_id_id_idx', array['owner_user_id', 'id']);
select has_index('public', 'wishes', 'wishes_owner_user_id_id_idx', array['owner_user_id', 'id']);
select has_index('public', 'wishes', 'wishes_wishlist_id_idx', array['wishlist_id']);
select has_index('public', 'wishlists', 'wishlists_person_id_idx', array['person_id']);
select is(
    (select count(*)::int
     from pg_constraint
     where contype = 'f'
       and (conrelid in ('public.wishlists'::regclass, 'public.wishes'::regclass) or confrelid = 'public.wishlists'::regclass)
       and confdeltype <> 'a'),
    0,
    'no reference to or from wishlists and wishes cascades');
-- Reservations will live elsewhere, behind functions; a wish itself never carries their state.
select is(
    (select count(*)::int from information_schema.columns
     where table_schema = 'public' and table_name in ('wishlists', 'wishes') and column_name ilike '%reserv%'),
    0,
    'wishlists and wishes carry no reservation state');
select is(
    (select count(*)::int from pg_class where oid in ('public.wishlists'::regclass, 'public.wishes'::regclass) and relrowsecurity),
    2,
    'row level security is enabled on both tables');

-- ---- fixtures ----

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test');

-- Bob's rows, written with full privileges so the checks below do not depend on RLS hiding them.
insert into public.people (id, owner_user_id, name, last_mutation_id)
values ('b0000000-0000-4000-8000-000000000001', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Bob''s person', gen_random_uuid());
insert into public.wishlists (id, owner_user_id, name, last_mutation_id)
values ('b0000000-0000-4000-8000-000000000002', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Bob''s list', gen_random_uuid());

select set_config('request.jwt.claims', '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}', true);
set local role authenticated;

insert into public.people (id, owner_user_id, name, last_mutation_id)
values ('a0000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna', gen_random_uuid());

-- ---- create: server metadata and the ledger ----

select lives_ok(
    $$insert into public.wishlists (id, owner_user_id, name, last_mutation_id, version, created_at, updated_at)
      values ('a0000000-0000-4000-8000-0000000000a1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'My birthday',
              'c1000000-0000-4000-8000-000000000001', 999, '2000-01-01T00:00:00Z', '2000-01-01T00:00:00Z')$$,
    'Alice can create her own wishlist');
select is((select version from public.wishlists where id = 'a0000000-0000-4000-8000-0000000000a1'), 1::bigint,
    'a new wishlist starts at version 1, whatever the client sent');
select is((select created_at from public.wishlists where id = 'a0000000-0000-4000-8000-0000000000a1'), now(),
    'created_at is the server time');
select lives_ok(
    $$insert into public.wishlists (id, owner_user_id, person_id, name, last_mutation_id)
      values ('a0000000-0000-4000-8000-0000000000a2', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
              'a0000000-0000-4000-8000-000000000001', 'Anna''s list', gen_random_uuid())$$,
    'Alice can keep a list for one of her people');
select lives_ok(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name, estimated_price_amount_minor,
                                 estimated_price_currency_code, last_mutation_id)
      values ('a0000000-0000-4000-8000-0000000000b1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
              'a0000000-0000-4000-8000-0000000000a1', 'Headphones', 12990, 'HUF', 'c1000000-0000-4000-8000-000000000002')$$,
    'Alice can add a wish to her list');
select results_eq(
    $$select owner_user_id, entity_type, entity_id, resulting_version from public.sync_mutations
      where mutation_id in ('c1000000-0000-4000-8000-000000000001', 'c1000000-0000-4000-8000-000000000002')
      order by entity_type$$,
    $$values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid, 'wishes'::text, 'a0000000-0000-4000-8000-0000000000b1'::uuid, 1::bigint),
             ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'::uuid, 'wishlists'::text, 'a0000000-0000-4000-8000-0000000000a1'::uuid, 1::bigint)$$,
    'both creates are recorded with their owner, table, row and resulting version');
select throws_ok(
    $$insert into public.wishlists (id, owner_user_id, name) values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'No mutation')$$,
    '23502', null, 'a wishlist create without a mutation id is rejected');
select throws_ok(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-0000000000a1', 'No mutation')$$,
    '23502', null, 'a wish create without a mutation id is rejected');
select throws_ok(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name, last_mutation_id)
      values ('a0000000-0000-4000-8000-0000000000b1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
              'a0000000-0000-4000-8000-0000000000a1', 'Headphones', 'c1000000-0000-4000-8000-000000000002')$$,
    '23505', null, 'retrying a committed wish create is rejected as a duplicate');
select is((select count(*)::int from public.sync_mutations where mutation_id = 'c1000000-0000-4000-8000-000000000002'), 1,
    'the retried create is still recorded exactly once');

-- ---- update: one mutation, one version ----

update public.wishes set name = 'Wireless headphones', last_mutation_id = 'c1000000-0000-4000-8000-000000000003'
where id = 'a0000000-0000-4000-8000-0000000000b1';
select results_eq(
    $$select entity_type, resulting_version from public.sync_mutations where mutation_id = 'c1000000-0000-4000-8000-000000000003'$$,
    $$values ('wishes'::text, 2::bigint)$$,
    'an update is recorded with the version it produced');
update public.wishes set note = 'Black', version = 999, updated_at = '2000-01-01T00:00:00Z', last_mutation_id = gen_random_uuid()
where id = 'a0000000-0000-4000-8000-0000000000b1';
select is((select version from public.wishes where id = 'a0000000-0000-4000-8000-0000000000b1'), 3::bigint,
    'a forged version is replaced by the old version plus one');
select is((select updated_at from public.wishes where id = 'a0000000-0000-4000-8000-0000000000b1'), now(),
    'a forged updated_at is replaced by the server time');
select throws_ok(
    $$update public.wishes set note = 'Again', last_mutation_id = (select last_mutation_id from public.wishes where id = 'a0000000-0000-4000-8000-0000000000b1')
      where id = 'a0000000-0000-4000-8000-0000000000b1'$$,
    '23505', null, 'an update repeating the row''s current mutation id is rejected');
select throws_ok(
    $$update public.wishes set note = 'None' , last_mutation_id = null where id = 'a0000000-0000-4000-8000-0000000000b1'$$,
    '23502', null, 'an update without a mutation id is rejected');
select throws_ok(
    $$update public.wishlists set owner_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', last_mutation_id = gen_random_uuid()
      where id = 'a0000000-0000-4000-8000-0000000000a1'$$,
    '42501', null, 'a wishlist cannot be handed over to another user');
select throws_ok(
    $$update public.wishes set id = gen_random_uuid(), last_mutation_id = gen_random_uuid() where id = 'a0000000-0000-4000-8000-0000000000b1'$$,
    '42501', null, 'a wish id cannot be changed');

-- Compare-and-set: a write based on a stale read matches no row instead of overwriting.
select is_empty(
    $$update public.wishes set name = 'Stale', last_mutation_id = gen_random_uuid()
      where id = 'a0000000-0000-4000-8000-0000000000b1' and version = 2 returning id$$,
    'an update expecting a stale version changes nothing');
select results_eq(
    $$update public.wishes set name = 'Fresh', last_mutation_id = gen_random_uuid()
      where id = 'a0000000-0000-4000-8000-0000000000b1' and version = 3 returning version$$,
    $$values (4::bigint)$$,
    'an update expecting the current version produces the next one');

-- Soft deletion is an ordinary, ledgered update.
update public.wishes set deleted_at = now(), last_mutation_id = 'c1000000-0000-4000-8000-000000000004'
where id = 'a0000000-0000-4000-8000-0000000000b1';
select results_eq(
    $$select entity_type, resulting_version from public.sync_mutations where mutation_id = 'c1000000-0000-4000-8000-000000000004'$$,
    $$values ('wishes'::text, 5::bigint)$$,
    'a soft delete is a new version, recorded in the ledger');
update public.wishlists set deleted_at = now(), last_mutation_id = 'c1000000-0000-4000-8000-000000000005'
where id = 'a0000000-0000-4000-8000-0000000000a2';
select results_eq(
    $$select entity_type, resulting_version from public.sync_mutations where mutation_id = 'c1000000-0000-4000-8000-000000000005'$$,
    $$values ('wishlists'::text, 2::bigint)$$,
    'a wishlist soft delete is recorded too');

-- ---- constraints ----

select throws_ok(
    $$insert into public.wishlists (id, owner_user_id, name, last_mutation_id) values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '  ', gen_random_uuid())$$,
    '23514', null, 'a wishlist needs a name');
select throws_ok(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name, last_mutation_id)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-0000000000a1', '', gen_random_uuid())$$,
    '23514', null, 'a wish needs a name');
select throws_ok(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name, estimated_price_amount_minor, last_mutation_id)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-0000000000a1', 'Book', 1000, gen_random_uuid())$$,
    '23514', null, 'a price amount needs its currency');
select throws_ok(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name, estimated_price_amount_minor, estimated_price_currency_code, last_mutation_id)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-0000000000a1', 'Book', -1, 'HUF', gen_random_uuid())$$,
    '23514', null, 'a price cannot be negative');
select throws_ok(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name, estimated_price_amount_minor, estimated_price_currency_code, last_mutation_id)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-0000000000a1', 'Book', 1000, 'huf', gen_random_uuid())$$,
    '23514', null, 'a currency code is three upper-case letters');

-- ---- references stay within one owner, whatever RLS would let a client see ----

select throws_ok(
    $$insert into public.wishlists (id, owner_user_id, person_id, name, last_mutation_id)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'b0000000-0000-4000-8000-000000000001', 'Planted', gen_random_uuid())$$,
    '23503', null, 'a wishlist cannot be kept for another user''s person');
select throws_ok(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name, last_mutation_id)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'b0000000-0000-4000-8000-000000000002', 'Planted', gen_random_uuid())$$,
    '23503', null, 'a wish cannot be put on another user''s wishlist');
select throws_ok(
    $$insert into public.wishes (id, owner_user_id, wishlist_id, name, last_mutation_id)
      values (gen_random_uuid(), 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'b0000000-0000-4000-8000-000000000002', 'Planted', gen_random_uuid())$$,
    '42501', null, 'Alice cannot create a wish owned by Bob');

-- ---- no client hard-deletes ----

select throws_ok($$delete from public.wishlists$$, '42501', null, 'clients cannot hard-delete wishlists');
select throws_ok($$delete from public.wishes$$, '42501', null, 'clients cannot hard-delete wishes');

-- ---- another user and anonymous callers ----

select set_config('request.jwt.claims', '{"sub": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "role": "authenticated"}', true);

select is(
    (select count(*)::int from public.wishlists where owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
        + (select count(*)::int from public.wishes),
    0,
    'Bob cannot read any of Alice''s wishlists or wishes');
select is((select count(*)::int from public.sync_mutations where entity_type in ('wishlists', 'wishes')
           and owner_user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0,
    'Bob cannot read Alice''s wishlist ledger entries');
update public.wishlists set name = 'Hacked', last_mutation_id = gen_random_uuid() where id = 'a0000000-0000-4000-8000-0000000000a1';

reset role;
set local role anon;
select throws_ok($$select * from public.wishlists$$, '42501', null, 'anon cannot read wishlists');
select throws_ok($$select * from public.wishes$$, '42501', null, 'anon cannot read wishes');

reset role;
select is((select name from public.wishlists where id = 'a0000000-0000-4000-8000-0000000000a1'), 'My birthday',
    'Bob''s update of Alice''s wishlist changed nothing');

-- ---- the widened ledger check ----

select lives_ok(
    $$insert into public.sync_mutations (mutation_id, owner_user_id, entity_type, entity_id, resulting_version)
      select gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', t, gen_random_uuid(), 1
      from unnest(array['people', 'occasions', 'gift_ideas', 'gifts', 'wishlists', 'wishes']) as t$$,
    'the ledger accepts the four earlier entity types and the two new ones');
select throws_ok(
    $$insert into public.sync_mutations (mutation_id, owner_user_id, entity_type, entity_id, resulting_version)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'reservations', gen_random_uuid(), 1)$$,
    '23514', null, 'the ledger still rejects any other entity type');

select * from finish();
rollback;
