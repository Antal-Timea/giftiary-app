-- Each user sees and changes only their own rows, cannot hand rows over, and cannot hard-delete.
begin;
create extension if not exists pgtap with schema extensions;
select plan(17);

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test');

-- Alice's data, one row per table.
select set_config('request.jwt.claims', '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}', true);
set local role authenticated;

select lives_ok(
    $$insert into public.people (id, owner_user_id, name)
      values ('11111111-1111-4111-8111-111111111111', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna')$$,
    'Alice can create her own person');
insert into public.occasions (id, owner_user_id, person_id, type, day, month, repeats_yearly)
values ('44444444-4444-4444-8444-444444444444', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        '11111111-1111-4111-8111-111111111111', 'BIRTHDAY', 12, 3, true);
insert into public.gift_ideas (id, owner_user_id, recipient_person_id, name, status)
values ('33333333-3333-4333-8333-333333333333', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        '11111111-1111-4111-8111-111111111111', 'Scarf', 'IDEA');
insert into public.gifts (id, owner_user_id, direction, recipient_person_id, name, given_at)
values ('55555555-5555-4555-8555-555555555555', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN',
        '11111111-1111-4111-8111-111111111111', 'Book', '2026-03-12');

select is((select count(*)::int from public.people), 1, 'Alice can read her person');

select throws_ok(
    $$insert into public.people (id, owner_user_id, name)
      values ('99999999-9999-4999-8999-999999999999', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Planted')$$,
    '42501', null, 'Alice cannot create a row owned by Bob');
select throws_ok(
    $$update public.people set owner_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
      where id = '11111111-1111-4111-8111-111111111111'$$,
    '42501', null, 'Alice cannot hand her person over to Bob');
select throws_ok(
    $$update public.gifts set owner_user_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
      where id = '55555555-5555-4555-8555-555555555555'$$,
    '42501', null, 'Alice cannot hand her gift over to Bob');

-- Hard deletes are not part of the client contract, for any table.
select throws_ok($$delete from public.people$$, '42501', null, 'clients cannot hard-delete people');
select throws_ok($$delete from public.occasions$$, '42501', null, 'clients cannot hard-delete occasions');
select throws_ok($$delete from public.gift_ideas$$, '42501', null, 'clients cannot hard-delete gift ideas');
select throws_ok($$delete from public.gifts$$, '42501', null, 'clients cannot hard-delete gifts');
select throws_ok($$delete from public.profiles$$, '42501', null, 'clients cannot delete profiles');

-- Bob sees none of it and cannot change it.
select set_config('request.jwt.claims', '{"sub": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "role": "authenticated"}', true);

select is(
    (select count(*)::int from public.people) + (select count(*)::int from public.occasions)
        + (select count(*)::int from public.gift_ideas) + (select count(*)::int from public.gifts),
    0,
    'Bob cannot read any of Alice''s rows');

update public.people set name = 'Hacked' where id = '11111111-1111-4111-8111-111111111111';
update public.occasions set title = 'Hacked' where id = '44444444-4444-4444-8444-444444444444';
update public.gift_ideas set name = 'Hacked' where id = '33333333-3333-4333-8333-333333333333';
update public.gifts set name = 'Hacked' where id = '55555555-5555-4555-8555-555555555555';
update public.people set deleted_at = now() where id = '11111111-1111-4111-8111-111111111111';

-- Unauthenticated callers cannot reach the tables at all.
reset role;
set local role anon;
select throws_ok($$select * from public.people$$, '42501', null, 'anon cannot read people');
select throws_ok($$select * from public.gifts$$, '42501', null, 'anon cannot read gifts');

-- Back as the database owner: Alice's rows are untouched.
reset role;
select is((select name from public.people where id = '11111111-1111-4111-8111-111111111111'), 'Anna', 'Bob''s update of Alice''s person changed nothing');
select is((select deleted_at from public.people where id = '11111111-1111-4111-8111-111111111111'), null, 'Bob cannot soft-delete Alice''s person');
select is(
    (select count(*)::int from public.occasions where title = 'Hacked')
        + (select count(*)::int from public.gift_ideas where name = 'Hacked')
        + (select count(*)::int from public.gifts where name = 'Hacked'),
    0,
    'Bob''s updates of Alice''s occasion, idea and gift changed nothing');
select is((select version from public.gifts where id = '55555555-5555-4555-8555-555555555555'), 1::bigint, 'blocked updates do not bump versions');

select * from finish();
rollback;
