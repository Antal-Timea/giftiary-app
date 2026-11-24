-- Feature flags are read by the app before sign-in and changed only by the project owner: every API
-- role can read them, none can add, change or remove one.

begin;
create extension if not exists pgtap with schema extensions;
select plan(14);

-- ---- what clients can reach ----

select has_table('public', 'feature_flags', 'the flags are in the API schema');
select table_privs_are('public', 'feature_flags', 'anon', array['SELECT'], 'anon may only read the flags');
select table_privs_are('public', 'feature_flags', 'authenticated', array['SELECT'], 'and so may a signed-in account');
select ok(
    (select relrowsecurity from pg_class where oid = 'public.feature_flags'::regclass),
    'row level security is on'
);
select results_eq(
    $$ select key, enabled from public.feature_flags order by key $$,
    $$ values ('facebook_login_enabled'::text, false) $$,
    'Facebook Login starts hidden'
);

-- ---- before sign-in ----

set local role anon;

select results_eq(
    $$ select enabled from public.feature_flags where key = 'facebook_login_enabled' $$,
    $$ values (false) $$,
    'anon reads the Facebook Login flag'
);
select throws_ok(
    $$ update public.feature_flags set enabled = true where key = 'facebook_login_enabled' $$,
    '42501', null, 'anon cannot turn a flag on'
);
select throws_ok(
    $$ insert into public.feature_flags (key, enabled) values ('anything', true) $$,
    '42501', null, 'nor add one'
);
select throws_ok(
    $$ delete from public.feature_flags $$,
    '42501', null, 'nor remove one'
);

-- ---- signed in ----

reset role;
select set_config('request.jwt.claims', '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}', true);
set local role authenticated;

select results_eq(
    $$ select enabled from public.feature_flags where key = 'facebook_login_enabled' $$,
    $$ values (false) $$,
    'a signed-in account reads it too'
);
select throws_ok(
    $$ update public.feature_flags set enabled = true $$,
    '42501', null, 'and cannot change it'
);
select throws_ok(
    $$ insert into public.feature_flags (key, enabled) values ('anything', true) $$,
    '42501', null, 'nor add one'
);

-- ---- the owner, in the dashboard ----

reset role;
update public.feature_flags set enabled = true where key = 'facebook_login_enabled';
set local role anon;
select results_eq(
    $$ select enabled from public.feature_flags where key = 'facebook_login_enabled' $$,
    $$ values (true) $$,
    'a change made in the dashboard is what clients read next'
);
reset role;
select throws_ok(
    $$ insert into public.feature_flags (key, enabled) values ('Not A Key', true) $$,
    '23514', null, 'keys are lower-case snake_case'
);

select * from finish();
rollback;
