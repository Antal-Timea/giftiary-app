-- Version and timestamps are decided by the server, never by the client.
begin;
create extension if not exists pgtap with schema extensions;
select plan(15);

insert into auth.users (id, email) values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test');

select set_config('request.jwt.claims', '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}', true);
set local role authenticated;

-- A client tries to choose its own version and timestamps on insert.
insert into public.people (id, owner_user_id, name, version, created_at, updated_at)
values ('11111111-1111-4111-8111-111111111111', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna',
        999, '2000-01-01T00:00:00Z', '2000-01-01T00:00:00Z');

select is((select version from public.people where name = 'Anna'), 1::bigint, 'a new row starts at version 1, whatever the client sent');
select is((select created_at from public.people where name = 'Anna'), now(), 'created_at is the server time');
select is((select updated_at from public.people where name = 'Anna'), now(), 'updated_at is the server time');

update public.people set relationship = 'Sister' where id = '11111111-1111-4111-8111-111111111111';
select is((select version from public.people where name = 'Anna'), 2::bigint, 'an update moves version 1 to 2');

update public.people set relationship = 'Big sister' where id = '11111111-1111-4111-8111-111111111111';
select is((select version from public.people where name = 'Anna'), 3::bigint, 'the next update moves it to 3');

-- A client tries to forge the version and timestamps on update.
update public.people
set relationship = 'Sister', version = 999, updated_at = '2000-01-01T00:00:00Z', created_at = '2000-01-01T00:00:00Z'
where id = '11111111-1111-4111-8111-111111111111';
select is((select version from public.people where name = 'Anna'), 4::bigint, 'a forged version is replaced by the old version plus one');
select is((select updated_at from public.people where name = 'Anna'), now(), 'a forged updated_at is replaced by the server time');
select isnt((select created_at from public.people where name = 'Anna'), '2000-01-01T00:00:00Z'::timestamptz, 'created_at cannot be rewritten');

-- An update that changes nothing is not a new version.
update public.people set relationship = 'Sister' where id = '11111111-1111-4111-8111-111111111111';
select is((select version from public.people where name = 'Anna'), 4::bigint, 'an update without real changes keeps the version');

-- A soft delete is an update: the tombstone gets a newer version for other devices to pull.
update public.people set deleted_at = now() where id = '11111111-1111-4111-8111-111111111111';
select is((select version from public.people where name = 'Anna'), 5::bigint, 'soft deleting increments the version');
select isnt((select deleted_at from public.people where name = 'Anna'), null, 'the soft-deleted row stays as a tombstone');

-- Archiving a gift idea (it became a gift) is an update too, and not a deletion.
insert into public.people (id, owner_user_id, name)
values ('22222222-2222-4222-8222-222222222222', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Béla');
insert into public.gift_ideas (id, owner_user_id, recipient_person_id, name, status)
values ('33333333-3333-4333-8333-333333333333', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        '22222222-2222-4222-8222-222222222222', 'Scarf', 'PURCHASED');
update public.gift_ideas set archived_at = now() where id = '33333333-3333-4333-8333-333333333333';
select is((select version from public.gift_ideas where name = 'Scarf'), 2::bigint, 'archiving increments the version');
select is((select deleted_at from public.gift_ideas where name = 'Scarf'), null, 'archiving does not delete');

-- The same trigger protects every synced table.
insert into public.occasions (id, owner_user_id, person_id, type, day, month, repeats_yearly, version)
values ('44444444-4444-4444-8444-444444444444', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        '22222222-2222-4222-8222-222222222222', 'BIRTHDAY', 12, 3, true, 42);
insert into public.gifts (id, owner_user_id, direction, recipient_person_id, name, given_at, version)
values ('55555555-5555-4555-8555-555555555555', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN',
        '22222222-2222-4222-8222-222222222222', 'Book', '2026-03-12', 42);
select is((select version from public.occasions where id = '44444444-4444-4444-8444-444444444444'), 1::bigint, 'occasions start at version 1');
select is((select version from public.gifts where id = '55555555-5555-4555-8555-555555555555'), 1::bigint, 'gifts start at version 1');

select * from finish();
rollback;
