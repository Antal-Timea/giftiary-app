-- Every new account gets exactly one private profile.
begin;
create extension if not exists pgtap with schema extensions;
select plan(9);

insert into auth.users (id, email, raw_user_meta_data) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'alice@example.test', '{"display_name": "  Alice  "}'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'bob@example.test', '{}');

select is((select count(*)::int from public.profiles where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 1, 'signing up creates one profile');
select is((select display_name from public.profiles where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 'Alice', 'the display name comes from sign-up metadata, trimmed');
select is((select display_name from public.profiles where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'), null, 'a profile does not need a display name');
select hasnt_column('public', 'profiles', 'email', 'profiles do not duplicate the account email');

select set_config('request.jwt.claims', '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}', true);
set local role authenticated;

select is((select count(*)::int from public.profiles), 1, 'a user sees only their own profile');
select lives_ok($$update public.profiles set preferred_locale = 'hu-HU' where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$$,
    'a user can update their own profile');
select throws_ok($$update public.profiles set created_at = '2000-01-01T00:00:00Z' where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'$$,
    '42501', null, 'profile timestamps are not client-editable');
select throws_ok($$insert into public.profiles (id) values ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')$$,
    '42501', null, 'clients cannot create profiles');

update public.profiles set display_name = 'Hacked' where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
reset role;
select is((select display_name from public.profiles where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'), null, 'a user cannot update someone else''s profile');

select * from finish();
rollback;
