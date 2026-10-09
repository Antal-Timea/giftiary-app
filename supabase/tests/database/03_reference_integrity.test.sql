-- References stay within one owner and make sense, while history survives soft deletion.
begin;
create extension if not exists pgtap with schema extensions;
select plan(18);

insert into auth.users (id, email) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test');

-- Bob's rows are created with full privileges: the protection must not rely on RLS hiding them.
insert into public.people (last_mutation_id, id, owner_user_id, name)
values (gen_random_uuid(), 'b0000000-0000-4000-8000-000000000001', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'Bob''s person');
insert into public.occasions (last_mutation_id, id, owner_user_id, person_id, type, day, month, repeats_yearly)
values (gen_random_uuid(), 'b0000000-0000-4000-8000-000000000002', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        'b0000000-0000-4000-8000-000000000001', 'BIRTHDAY', 1, 1, true);
insert into public.gift_ideas (last_mutation_id, id, owner_user_id, recipient_person_id, name, status)
values (gen_random_uuid(), 'b0000000-0000-4000-8000-000000000003', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        'b0000000-0000-4000-8000-000000000001', 'Bob''s idea', 'IDEA');

select set_config('request.jwt.claims', '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}', true);
set local role authenticated;

-- Alice's people: Anna (with a birthday) and Béla.
insert into public.people (last_mutation_id, id, owner_user_id, name) values
    (gen_random_uuid(), 'a0000000-0000-4000-8000-00000000000a', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna'),
    (gen_random_uuid(), 'a0000000-0000-4000-8000-00000000000b', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Béla');
insert into public.occasions (last_mutation_id, id, owner_user_id, person_id, type, day, month, repeats_yearly)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-0000000000b1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        'a0000000-0000-4000-8000-00000000000a', 'BIRTHDAY', 12, 3, true);
insert into public.occasions (last_mutation_id, id, owner_user_id, person_id, type, day, month, repeats_yearly)
values (gen_random_uuid(), 'a0000000-0000-4000-8000-0000000000c1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        null, 'CHRISTMAS', 24, 12, true);

-- Cross-owner references are rejected by the composite foreign keys.
select throws_ok(
    $$insert into public.occasions (last_mutation_id, id, owner_user_id, person_id, type, day, month, repeats_yearly)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'b0000000-0000-4000-8000-000000000001', 'BIRTHDAY', 1, 1, true)$$,
    '23503', null, 'Alice''s occasion cannot belong to Bob''s person');
select throws_ok(
    $$insert into public.gift_ideas (last_mutation_id, id, owner_user_id, recipient_person_id, name, status)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'b0000000-0000-4000-8000-000000000001', 'Idea', 'IDEA')$$,
    '23503', null, 'Alice''s idea cannot be for Bob''s person');
select throws_ok(
    $$insert into public.gift_ideas (last_mutation_id, id, owner_user_id, recipient_person_id, occasion_id, name, status)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000a',
              'b0000000-0000-4000-8000-000000000002', 'Idea', 'IDEA')$$,
    '23503', null, 'Alice''s idea cannot use Bob''s occasion');
select throws_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, giver_person_id, name, given_at)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'RECEIVED', 'b0000000-0000-4000-8000-000000000001', 'Gift', '2026-01-01')$$,
    '23503', null, 'Alice''s gift cannot come from Bob''s person');
select throws_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, name, given_at)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'b0000000-0000-4000-8000-000000000001', 'Gift', '2026-01-01')$$,
    '23503', null, 'Alice''s gift cannot go to Bob''s person');
select throws_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, occasion_id, name, given_at)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-00000000000a',
              'b0000000-0000-4000-8000-000000000002', 'Gift', '2026-01-01')$$,
    '23503', null, 'Alice''s gift cannot use Bob''s occasion');
select throws_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, source_gift_idea_id, name, given_at)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-00000000000a',
              'b0000000-0000-4000-8000-000000000003', 'Gift', '2026-01-01')$$,
    '23503', null, 'Alice''s gift cannot come from Bob''s idea');

-- A person's occasion only fits ideas and gifts involving that person; general ones fit all.
select throws_ok(
    $$insert into public.gift_ideas (last_mutation_id, id, owner_user_id, recipient_person_id, occasion_id, name, status)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000b',
              'a0000000-0000-4000-8000-0000000000b1', 'Idea', 'IDEA')$$,
    '23514', null, 'an idea for Béla cannot use Anna''s birthday');
select lives_ok(
    $$insert into public.gift_ideas (last_mutation_id, id, owner_user_id, recipient_person_id, occasion_id, name, status)
      values (gen_random_uuid(), 'a0000000-0000-4000-8000-0000000000d1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000a',
              'a0000000-0000-4000-8000-0000000000b1', 'Scarf', 'PURCHASED')$$,
    'an idea for Anna can use Anna''s birthday');
select lives_ok(
    $$insert into public.gift_ideas (last_mutation_id, id, owner_user_id, recipient_person_id, occasion_id, name, status)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000b',
              'a0000000-0000-4000-8000-0000000000c1', 'Ornament', 'IDEA')$$,
    'a general occasion such as Christmas fits any recipient');
select throws_ok(
    $$update public.gift_ideas set last_mutation_id = gen_random_uuid(), recipient_person_id = 'a0000000-0000-4000-8000-00000000000b'
      where id = 'a0000000-0000-4000-8000-0000000000d1'$$,
    '23514', null, 'changing the recipient away from the occasion''s person is rejected');
select throws_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, occasion_id, name, given_at)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-00000000000b',
              'a0000000-0000-4000-8000-0000000000b1', 'Gift', '2026-01-01')$$,
    '23514', null, 'a gift for Béla cannot use Anna''s birthday');

-- Marking the idea as given: archive it and record the gift that came from it.
update public.gift_ideas set last_mutation_id = gen_random_uuid(), archived_at = now() where id = 'a0000000-0000-4000-8000-0000000000d1';
select lives_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, occasion_id, source_gift_idea_id, name, given_at)
      values (gen_random_uuid(), 'a0000000-0000-4000-8000-0000000000e1', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN',
              'a0000000-0000-4000-8000-00000000000a', 'a0000000-0000-4000-8000-0000000000b1',
              'a0000000-0000-4000-8000-0000000000d1', 'Scarf', '2026-03-12')$$,
    'a gift can come from an archived idea');
select throws_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, source_gift_idea_id, name, given_at)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-00000000000a',
              'a0000000-0000-4000-8000-0000000000d1', 'Scarf again', '2026-03-13')$$,
    '23505', null, 'an idea can be given only once');

-- A soft-deleted gift is still the record that the idea was given, so the idea stays used.
update public.gifts set last_mutation_id = gen_random_uuid(), deleted_at = now() where id = 'a0000000-0000-4000-8000-0000000000e1';
select throws_ok(
    $$insert into public.gifts (last_mutation_id, id, owner_user_id, direction, recipient_person_id, source_gift_idea_id, name, given_at)
      values (gen_random_uuid(), gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-00000000000a',
              'a0000000-0000-4000-8000-0000000000d1', 'Scarf again', '2026-03-13')$$,
    '23505', null, 'deleting the gift does not free its idea for another gift');
update public.gifts set last_mutation_id = gen_random_uuid(), deleted_at = null where id = 'a0000000-0000-4000-8000-0000000000e1';

-- Soft-deleting Anna and her birthday leaves the history valid and editable.
update public.occasions set last_mutation_id = gen_random_uuid(), deleted_at = now() where id = 'a0000000-0000-4000-8000-0000000000b1';
update public.people set last_mutation_id = gen_random_uuid(), deleted_at = now() where id = 'a0000000-0000-4000-8000-00000000000a';
select lives_ok(
    $$update public.gifts set last_mutation_id = gen_random_uuid(), note = 'She loved it' where id = 'a0000000-0000-4000-8000-0000000000e1'$$,
    'a gift for a soft-deleted person and occasion can still be edited');
select lives_ok(
    $$update public.gift_ideas set last_mutation_id = gen_random_uuid(), name = 'Wool scarf' where id = 'a0000000-0000-4000-8000-0000000000d1'$$,
    'an idea for a soft-deleted person stays structurally valid');
select is(
    (select count(*)::int from public.gifts where recipient_person_id = 'a0000000-0000-4000-8000-00000000000a'),
    1,
    'the gift still refers to the soft-deleted person');

select * from finish();
rollback;
