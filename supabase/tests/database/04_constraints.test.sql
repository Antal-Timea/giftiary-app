-- Value rules: money pairs, enum values and the domain rules mirrored from Android.
begin;
create extension if not exists pgtap with schema extensions;
select plan(25);

insert into auth.users (id, email) values ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test');

-- Inserts a gift for Anna with the given price columns, as SQL text for lives_ok/throws_ok.
create function pg_temp.gift_with_price(amount text, currency text) returns text language sql as $$
    select format(
        $sql$insert into public.gifts (id, owner_user_id, direction, recipient_person_id, name, given_at, price_amount_minor, price_currency_code)
             values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-00000000000a', 'Gift', '2026-01-01', %s, %s)$sql$,
        amount, currency)
$$;

select set_config('request.jwt.claims', '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}', true);
set local role authenticated;

insert into public.people (id, owner_user_id, name)
values ('a0000000-0000-4000-8000-00000000000a', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'Anna');

-- Money: amount and currency come together, the amount is never negative, the code is ISO-like.
select lives_ok(pg_temp.gift_with_price('12500', '''HUF'''), '12500 HUF is a valid price');
select lives_ok(pg_temp.gift_with_price('1250', '''EUR'''), '1250 EUR (12.50 EUR) is a valid price');
select lives_ok(pg_temp.gift_with_price('null', 'null'), 'no price at all is valid');
select throws_ok(pg_temp.gift_with_price('12500', 'null'), '23514', null, 'an amount without currency is rejected');
select throws_ok(pg_temp.gift_with_price('null', '''HUF'''), '23514', null, 'a currency without amount is rejected');
select throws_ok(pg_temp.gift_with_price('-1', '''HUF'''), '23514', null, 'a negative amount is rejected');
select throws_ok(pg_temp.gift_with_price('100', '''huf'''), '23514', null, 'a lowercase currency is rejected');
select throws_ok(pg_temp.gift_with_price('100', '''HU'''), '23514', null, 'a two-letter currency is rejected');
select throws_ok(pg_temp.gift_with_price('100', '''HUFF'''), '23514', null, 'a four-letter currency is rejected');
-- Sent as text, the way a client payload arrives; an unquoted numeric would be rounded by the cast.
select throws_ok(pg_temp.gift_with_price('''12.5''', '''EUR'''), '22P02', null, 'fractional amounts are rejected; amounts are minor units');
select throws_ok(
    $$insert into public.gift_ideas (id, owner_user_id, recipient_person_id, name, status, estimated_price_amount_minor)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000a', 'Idea', 'IDEA', 100)$$,
    '23514', null, 'an idea''s estimated price needs its currency');
select throws_ok(
    $$insert into public.gift_ideas (id, owner_user_id, recipient_person_id, name, status, actual_price_amount_minor, actual_price_currency_code)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000a', 'Idea', 'IDEA', -5, 'EUR')$$,
    '23514', null, 'an idea''s actual price cannot be negative');

-- Enums accept only the Android values.
select throws_ok(
    $$insert into public.gift_ideas (id, owner_user_id, recipient_person_id, name, status)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000a', 'Idea', 'GIVEN')$$,
    '22P02', null, 'GIVEN is not a gift idea status');
select throws_ok(
    $$insert into public.gifts (id, owner_user_id, direction, recipient_person_id, name, given_at)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'LENT', 'a0000000-0000-4000-8000-00000000000a', 'Gift', '2026-01-01')$$,
    '22P02', null, 'LENT is not a gift direction');
select throws_ok(
    $$insert into public.occasions (id, owner_user_id, type, day, month, repeats_yearly)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'EASTER', 1, 4, true)$$,
    '22P02', null, 'EASTER is not an occasion type');

-- Gifts say for whom or from whom.
select throws_ok(
    $$insert into public.gifts (id, owner_user_id, direction, giver_person_id, name, given_at)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'GIVEN', 'a0000000-0000-4000-8000-00000000000a', 'Gift', '2026-01-01')$$,
    '23514', null, 'a given gift needs a recipient');
select throws_ok(
    $$insert into public.gifts (id, owner_user_id, direction, recipient_person_id, name, given_at)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'RECEIVED', 'a0000000-0000-4000-8000-00000000000a', 'Gift', '2026-01-01')$$,
    '23514', null, 'a received gift needs a giver');

-- Names are never blank.
select throws_ok(
    $$insert into public.people (id, owner_user_id, name) values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '   ')$$,
    '23514', null, 'a person needs a name');

-- Occasion ranges and the rules the Android domain also applies.
select lives_ok(
    $$insert into public.occasions (id, owner_user_id, person_id, type, day, month, year, repeats_yearly)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000a', 'BIRTHDAY', 29, 2, null, true)$$,
    'a birthday without a year is valid');
select throws_ok(
    $$insert into public.occasions (id, owner_user_id, type, day, month, repeats_yearly)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'CHRISTMAS', 24, 13, true)$$,
    '23514', null, 'month 13 is rejected');
select throws_ok(
    $$insert into public.occasions (id, owner_user_id, type, day, month, repeats_yearly)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'CHRISTMAS', 32, 12, true)$$,
    '23514', null, 'day 32 is rejected');
select throws_ok(
    $$insert into public.occasions (id, owner_user_id, type, day, month, year, repeats_yearly)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'ANNIVERSARY', 1, 1, 0, true)$$,
    '23514', null, 'year 0 is rejected');
select throws_ok(
    $$insert into public.occasions (id, owner_user_id, type, day, month, repeats_yearly)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'CUSTOM', 1, 9, true)$$,
    '23514', null, 'a custom occasion needs a title');
select throws_ok(
    $$insert into public.occasions (id, owner_user_id, person_id, type, day, month, year, repeats_yearly)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000a', 'BIRTHDAY', 12, 3, 1990, false)$$,
    '23514', null, 'a birthday always repeats yearly');
select throws_ok(
    $$insert into public.occasions (id, owner_user_id, person_id, type, day, month, repeats_yearly)
      values (gen_random_uuid(), 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a0000000-0000-4000-8000-00000000000a', 'ANNIVERSARY', 18, 6, false)$$,
    '23514', null, 'a one-time occasion needs a year');

select * from finish();
rollback;
